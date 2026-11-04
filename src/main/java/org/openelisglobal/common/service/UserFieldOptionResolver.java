package org.openelisglobal.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.login.service.LoginUserService;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.security.service.AuthorizationCatalogService;
import org.openelisglobal.security.service.UserPermissionService;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.test.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserFieldOptionResolver {

    public static final String USER_FIELD_TYPE = "USER";
    public static final String USER_PROFILE_CODES_METADATA_KEY = "userProfileCodes";
    public static final String USER_DISPLAY_MODE_METADATA_KEY = "userDisplayMode";
    public static final String USER_DISPLAY_MODE_INITIALS = "INITIALS";
    public static final String USER_DISPLAY_MODE_NAME = "NAME";
    public static final String USER_DISPLAY_MODE_BOTH = "BOTH";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private SystemUserService systemUserService;

    @Autowired
    private PersonService personService;

    @Autowired
    private ProviderService providerService;

    public boolean isUserFieldType(String fieldType) {
        String normalized = StringUtils.trimToEmpty(fieldType);
        return USER_FIELD_TYPE.equalsIgnoreCase(normalized)
                || "SYSTEM_USER_BIOLOGIST_SELECT".equalsIgnoreCase(normalized);
    }

    public Set<String> resolveProfileCodes(String fieldType, String metadataJson) {
        if (!USER_FIELD_TYPE.equalsIgnoreCase(StringUtils.trimToEmpty(fieldType))) {
            return Collections.emptySet();
        }

        LinkedHashSet<String> profileCodes = new LinkedHashSet<>();
        if (StringUtils.isBlank(metadataJson)) {
            return profileCodes;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(metadataJson);
            JsonNode codesNode = root.get(USER_PROFILE_CODES_METADATA_KEY);
            if (codesNode != null && codesNode.isArray()) {
                for (JsonNode codeNode : codesNode) {
                    String code = StringUtils
                            .upperCase(StringUtils.trimToNull(codeNode == null ? null : codeNode.asText()));
                    if (code != null) {
                        profileCodes.add(code);
                    }
                }
            }
        } catch (Exception ignored) {
            return Collections.emptySet();
        }

        return profileCodes;
    }

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;
    @Autowired
    private LoginUserService loginUserService;
    @Autowired
    private UserPermissionService userPermissionService;
    @Autowired
    private AuthorizationCatalogService authorizationCatalogService;
    @Autowired
    private TestService testService;
    @Autowired
    private AnalysisService analysisService;

    public static final String USER_PERMISSION_MODULE = "userPermissionModule";
    public static final String USER_PERMISSION_ACTIONS = "userPermissionActions";
    private static final Set<String> OPERATIONAL_ACTIONS = Set.of("create", "update", "receive", "enter", "correct",
            "validate", "manage");
    private static final Map<String, List<String>> DEFAULT_ACTIONS = Map.of("orders", List.of("create", "update"),
            "patients", List.of("create", "update"), "results", List.of("enter", "update", "correct"), "validation",
            List.of("validate"), "sample-management", List.of("receive", "update"), "storage",
            List.of("update", "manage"), "administration", List.of("manage"));

    void setAuthorizationCatalogService(AuthorizationCatalogService catalog) {
        authorizationCatalogService = catalog;
    }

    public void validateConfiguration(String fieldType, String metadataJson, String defaultModule) {
        if (isUserFieldType(fieldType)) {
            task(metadataJson, defaultModule);
        }
    }

    /**
     * A candidate needs any selected task action. Administrators retain their
     * permissions but cannot be assigned to operational tasks. Profession is
     * descriptive, never an authorization.
     */
    private Task task(String metadataJson, String defaultModule) {
        try {
            JsonNode root = StringUtils.isBlank(metadataJson) ? OBJECT_MAPPER.createObjectNode()
                    : OBJECT_MAPPER.readTree(metadataJson);
            String module = root.has(USER_PERMISSION_MODULE) ? root.get(USER_PERMISSION_MODULE).asText()
                    : defaultModule;
            List<String> actions = new ArrayList<>();
            if (root.has(USER_PERMISSION_ACTIONS)) {
                if (!root.get(USER_PERMISSION_ACTIONS).isArray())
                    throw new IllegalArgumentException("Invalid USER task actions");
                root.get(USER_PERMISSION_ACTIONS).forEach(action -> actions.add(action.asText()));
            } else
                actions.addAll(DEFAULT_ACTIONS.getOrDefault(module, List.of()));
            if (actions.isEmpty() || actions.stream().anyMatch(action -> !OPERATIONAL_ACTIONS.contains(action)
                    || !authorizationCatalogService.isKnownPermission(module, action))) {
                throw new IllegalArgumentException("USER fields require an operational module and action");
            }
            return new Task(module, actions);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid USER field metadata", e);
        }
    }

    public List<ResolvedUserOption> resolveUserOptions(String fieldType, String metadataJson, String defaultModule,
            String testId) {
        return resolveOptions(fieldType, metadataJson, defaultModule,
                StringUtils.isBlank(testId) ? Set.of() : Set.of(testId));
    }

    public List<ResolvedUserOption> resolveSampleUserOptions(String metadataJson, String module, String sampleItemId) {
        SampleItem item = new SampleItem();
        item.setId(sampleItemId);
        Set<String> tests = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(sampleItemId)) {
            analysisService.getAnalysesBySampleItem(item).forEach(a -> {
                if (a.getTest() != null)
                    tests.add(a.getTest().getId());
            });
        }
        return resolveOptions(USER_FIELD_TYPE, metadataJson, module, tests);
    }

    private List<ResolvedUserOption> resolveOptions(String fieldType, String metadataJson, String defaultModule,
            Set<String> testIds) {
        if (!isUserFieldType(fieldType))
            return List.of();
        Task task;
        try {
            task = task(metadataJson, defaultModule);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        List<SystemUser> users = systemUserService.getAllSystemUsers();
        if (users == null)
            return List.of();
        List<ResolvedUserOption> options = new ArrayList<>();
        for (SystemUser user : users) {
            if (user == null || StringUtils.isBlank(user.getId()) || !isActiveSystemUser(user))
                continue;
            LoginUser login = loginUserService.getUserProfile(user.getLoginName());
            if (login == null || "Y".equalsIgnoreCase(login.getAccountDisabled())
                    || "Y".equalsIgnoreCase(login.getAccountLocked()))
                continue;
            if ("Y".equalsIgnoreCase(login.getIsAdmin())
                    || userPermissionService.hasPermission(user.getId(), SystemPermission.GLOBAL_ADMIN))
                continue;
            if (login.getPasswordExpiredDate() != null
                    && login.getPasswordExpiredDate().before(new java.sql.Date(System.currentTimeMillis())))
                continue;
            if (task.actions().stream().noneMatch(action -> permits(user.getId(), task.module(), action, testIds)))
                continue;
            options.add(new ResolvedUserOption(user.getId(),
                    buildOptionLabel(user, resolveLinkedProvider(user), resolveDisplayMode(fieldType, metadataJson))));
        }
        options.sort(Comparator.comparing(ResolvedUserOption::label, String.CASE_INSENSITIVE_ORDER));
        return options;
    }

    private boolean permits(String userId, String module, String action, Set<String> testIds) {
        ModuleAuthorizationService.AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId,
                module, action);
        if (decision == null || !decision.allowed())
            return false;
        if (decision.allLabUnits())
            return true;
        if (decision.labUnitIds() == null || decision.labUnitIds().isEmpty())
            return false;
        if (testIds.isEmpty())
            return true; // Configuration/directory preview has no specimen context.
        List<Integer> units = decision.labUnitIds().stream().map(Integer::valueOf).toList();
        return testService.getTestsByTestSectionIds(units).stream().anyMatch(test -> testIds.contains(test.getId()));
    }

    public void validateAssignment(String fieldType, String metadataJson, String module, String testId, String value,
            String previousValue) {
        if (!isUserFieldType(fieldType) || StringUtils.isBlank(value) || Objects.equals(value, previousValue))
            return;
        if (resolveUserOptions(fieldType, metadataJson, module, testId).stream()
                .noneMatch(option -> option.optionKey().equals(value)))
            throw new IllegalArgumentException("Selected user is not eligible for this task");
    }

    public void validateSampleAssignment(String metadataJson, String module, String sampleItemId, String value,
            String previousValue) {
        if (StringUtils.isBlank(value) || Objects.equals(value, previousValue))
            return;
        if (resolveSampleUserOptions(metadataJson, module, sampleItemId).stream()
                .noneMatch(option -> option.optionKey().equals(value)))
            throw new IllegalArgumentException("Selected user is not eligible for this task");
    }

    public List<ResolvedUserOption> filterForFieldAccess(List<ResolvedUserOption> options, String metadataJson,
            String owningModule, String fieldGroup, String fieldTag) {
        Task requiredTask;
        try {
            requiredTask = task(metadataJson, owningModule);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        if (!owningModule.equals(requiredTask.module()))
            return options;
        return options.stream().filter(option -> {
            Set<String> groups = moduleAuthorizationService.getRestrictedFieldGroupKeys(option.optionKey(),
                    owningModule);
            Set<String> tags = moduleAuthorizationService.getRestrictedFieldTagKeys(option.optionKey(), owningModule);
            return (fieldGroup == null || groups == null || !groups.contains(fieldGroup))
                    && (fieldTag == null || tags == null || !tags.contains(fieldTag));
        }).toList();
    }

    public void validateFieldAssignment(List<ResolvedUserOption> eligible, String value, String previousValue) {
        if (StringUtils.isBlank(value) || Objects.equals(value, previousValue))
            return;
        if (eligible.stream().noneMatch(option -> value.equals(option.optionKey())))
            throw new IllegalArgumentException("Selected user is not eligible for this field");
    }

    public List<ResolvedUserOption> resolveOrderUserOptions(String metadataJson, String sampleId) {
        Set<String> testIds = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(sampleId))
            analysisService.getAnalysesBySampleId(sampleId).forEach(analysis -> {
                if (analysis.getTest() != null)
                    testIds.add(analysis.getTest().getId());
            });
        return resolveOptions(USER_FIELD_TYPE, metadataJson, "orders", testIds);
    }

    public List<ResolvedUserOption> resolveCollectorOptions(String sampleItemId) {
        return filterForFieldAccess(resolveSampleUserOptions(null, "sample-management", sampleItemId), null,
                "sample-management", "collection", "collector");
    }

    /**
     * Clinical requesters can be external professionals; linked internal accounts
     * must have an order action.
     */
    public List<org.openelisglobal.common.util.IdValuePair> resolveOrderProviderOptions() {
        Set<String> eligibleUsers = resolveUserOptions("USER", null, "orders", null).stream()
                .map(ResolvedUserOption::optionKey).collect(java.util.stream.Collectors.toSet());
        Map<String, List<SystemUser>> linkedUsers = systemUserService.getAllSystemUsers().stream()
                .filter(user -> StringUtils.isNotBlank(user.getLinkedProviderPersonId()))
                .collect(java.util.stream.Collectors.groupingBy(SystemUser::getLinkedProviderPersonId));
        return providerService.getAllActiveProviders().stream().filter(provider -> provider.getPerson() != null)
                .filter(provider -> !linkedUsers.containsKey(provider.getPerson().getId())
                        || linkedUsers.get(provider.getPerson().getId()).stream()
                                .anyMatch(user -> eligibleUsers.contains(user.getId())))
                .map(provider -> new org.openelisglobal.common.util.IdValuePair(provider.getPerson().getId(),
                        StringUtils.defaultString(provider.getPerson().getLastName()) + ", "
                                + StringUtils.defaultString(provider.getPerson().getFirstName())))
                .sorted(Comparator.comparing(org.openelisglobal.common.util.IdValuePair::getValue,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public void validateOrderProviderAssignment(String personId, String previousPersonId) {
        if (StringUtils.isBlank(personId) || Objects.equals(personId, previousPersonId))
            return;
        if (resolveOrderProviderOptions().stream().noneMatch(option -> personId.equals(option.getId())))
            throw new IllegalArgumentException("Selected requester is not eligible for an order");
    }

    private record Task(String module, List<String> actions) {
    }

    public String resolveDisplayMode(String fieldType, String metadataJson) {
        if (!isUserFieldType(fieldType)) {
            return USER_DISPLAY_MODE_BOTH;
        }

        if (StringUtils.isBlank(metadataJson)) {
            return USER_DISPLAY_MODE_BOTH;
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(metadataJson);
            JsonNode modeNode = root.get(USER_DISPLAY_MODE_METADATA_KEY);
            return normalizeDisplayMode(modeNode == null ? null : modeNode.asText());
        } catch (Exception ignored) {
            return USER_DISPLAY_MODE_BOTH;
        }
    }

    private String normalizeDisplayMode(String displayMode) {
        String normalized = StringUtils.upperCase(StringUtils.trimToNull(displayMode));
        if (USER_DISPLAY_MODE_INITIALS.equals(normalized) || USER_DISPLAY_MODE_NAME.equals(normalized)) {
            return normalized;
        }
        return USER_DISPLAY_MODE_BOTH;
    }

    private boolean isActiveSystemUser(SystemUser user) {
        String activeFlag = StringUtils.upperCase(StringUtils.trimToEmpty(user.getIsActive()));
        return "Y".equals(activeFlag) || "YES".equals(activeFlag) || "TRUE".equals(activeFlag);
    }

    private Provider resolveLinkedProvider(SystemUser user) {
        if (user == null || StringUtils.isBlank(user.getLinkedProviderPersonId())) {
            return null;
        }
        Person person = personService.getPersonById(user.getLinkedProviderPersonId());
        if (person == null) {
            return null;
        }
        Provider provider = providerService.getProviderByPerson(person);
        if (provider != null && provider.getPerson() == null) {
            provider.setPerson(person);
        }
        return provider;
    }

    private String buildOptionLabel(SystemUser user, Provider provider, String displayMode) {
        String initials = provider == null ? null : StringUtils.trimToNull(provider.getProfessionalInitials());
        String firstName = provider == null || provider.getPerson() == null ? null
                : StringUtils.trimToNull(provider.getPerson().getFirstName());
        String lastName = provider == null || provider.getPerson() == null ? null
                : StringUtils.trimToNull(provider.getPerson().getLastName());
        if (StringUtils.isBlank(firstName) && StringUtils.isBlank(lastName)) {
            firstName = user.getFirstName();
            lastName = user.getLastName();
        }
        String fullName = StringUtils
                .normalizeSpace(StringUtils.defaultString(firstName) + " " + StringUtils.defaultString(lastName));

        if (USER_DISPLAY_MODE_INITIALS.equals(displayMode)) {
            if (StringUtils.isNotBlank(initials)) {
                return initials;
            }
            if (StringUtils.isNotBlank(fullName)) {
                return fullName;
            }
            return StringUtils.defaultIfBlank(user.getLoginName(), user.getId());
        }

        if (USER_DISPLAY_MODE_NAME.equals(displayMode)) {
            if (StringUtils.isNotBlank(fullName)) {
                return fullName;
            }
            if (StringUtils.isNotBlank(initials)) {
                return initials;
            }
            return StringUtils.defaultIfBlank(user.getLoginName(), user.getId());
        }

        if (StringUtils.isNotBlank(initials) && StringUtils.isNotBlank(fullName)) {
            return initials + " - " + fullName;
        }
        if (StringUtils.isNotBlank(fullName)) {
            return fullName;
        }
        if (StringUtils.isNotBlank(initials)) {
            return initials;
        }
        return StringUtils.defaultIfBlank(user.getLoginName(), user.getId());
    }

    public record ResolvedUserOption(String optionKey, String label) {
    }
}
