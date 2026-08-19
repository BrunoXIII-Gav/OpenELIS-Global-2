package org.openelisglobal.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
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
        return USER_FIELD_TYPE.equalsIgnoreCase(normalized);
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
                    String code = StringUtils.upperCase(StringUtils.trimToNull(codeNode == null ? null : codeNode.asText()));
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

    public List<ResolvedUserOption> resolveUserOptions(String fieldType, String metadataJson) {
        return resolveUserOptions(resolveProfileCodes(fieldType, metadataJson), resolveDisplayMode(fieldType, metadataJson));
    }

    public List<ResolvedUserOption> resolveUserOptions(Set<String> professionalProfileCodes) {
        return resolveUserOptions(professionalProfileCodes, USER_DISPLAY_MODE_BOTH);
    }

    public List<ResolvedUserOption> resolveUserOptions(Set<String> professionalProfileCodes, String displayMode) {
        if (professionalProfileCodes == null || professionalProfileCodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<SystemUser> allUsers = systemUserService.getAllSystemUsers();
        if (allUsers == null || allUsers.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> normalizedProfileCodes = professionalProfileCodes.stream()
                .map(code -> StringUtils.upperCase(StringUtils.trimToNull(code)))
                .filter(StringUtils::isNotBlank)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        String normalizedDisplayMode = normalizeDisplayMode(displayMode);
        if (normalizedProfileCodes.isEmpty()) {
            return Collections.emptyList();
        }

        List<ResolvedUserOption> options = new ArrayList<>();
        for (SystemUser user : allUsers) {
            if (user == null || StringUtils.isBlank(user.getId()) || !isActiveSystemUser(user)) {
                continue;
            }

            Provider linkedProvider = resolveLinkedProvider(user);
            if (linkedProvider == null || !Boolean.TRUE.equals(linkedProvider.getActive())) {
                continue;
            }

            String profileCode = StringUtils.upperCase(
                    StringUtils.trimToNull(linkedProvider.getProfessionalProfileCode()));
            if (!normalizedProfileCodes.contains(profileCode)) {
                continue;
            }

            options.add(new ResolvedUserOption(user.getId(), buildOptionLabel(user, linkedProvider, normalizedDisplayMode)));
        }

        options.sort(Comparator.comparing(option -> StringUtils.defaultString(option.label()),
                String.CASE_INSENSITIVE_ORDER));
        return options;
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
        String fullName = StringUtils.normalizeSpace(
                StringUtils.defaultString(firstName) + " " + StringUtils.defaultString(lastName));

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
