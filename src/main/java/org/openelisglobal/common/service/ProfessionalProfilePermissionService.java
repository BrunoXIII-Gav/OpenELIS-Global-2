package org.openelisglobal.common.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.login.service.LoginUserService;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.security.service.UserPermissionService;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service to validate if a user has the required professional profile to perform actions
 * in different modules (order, patient, result, validation, sample collection)
 */
@Service
public class ProfessionalProfilePermissionService {

    public enum PermissionFlow {
        ORDER_REQUESTER(Property.orderProviderProfessionalProfileCode, "hasOrderPermission", "order"),
        PATIENT_MANAGER(Property.patientEntryProfessionalProfileCode, "hasPatientEntryPermission", "patient entry"),
        RESULT_ENTRY(Property.resultEntryProfessionalProfileCode, "hasResultEntryPermission", "result entry"),
        VALIDATION_INTERPRETER(Property.validationInterpreterProfessionalProfileCode, "hasValidationPermission",
                "validation"),
        SAMPLE_COLLECTOR(Property.sampleCollectorProfessionalProfileCode, "hasSampleCollectionPermission",
                "sample collection");

        private final Property configProperty;
        private final String legacyResponseKey;
        private final String logLabel;

        PermissionFlow(Property configProperty, String legacyResponseKey, String logLabel) {
            this.configProperty = configProperty;
            this.legacyResponseKey = legacyResponseKey;
            this.logLabel = logLabel;
        }

        public Property getConfigProperty() {
            return configProperty;
        }

        public String getLegacyResponseKey() {
            return legacyResponseKey;
        }

        public String getLogLabel() {
            return logLabel;
        }
    }

    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private ProviderService providerService;
    @Autowired
    private PersonService personService;
    @Autowired
    private UserPermissionService userPermissionService;
    @Autowired
    private LoginUserService loginUserService;

    /**
     * Check if user has permission for order creation/editing
     */
    public boolean hasOrderPermission(String systemUserId) {
        return hasPermission(systemUserId, PermissionFlow.ORDER_REQUESTER);
    }

    /**
     * Check if user has permission for patient entry/editing
     */
    public boolean hasPatientEntryPermission(String systemUserId) {
        return hasPermission(systemUserId, PermissionFlow.PATIENT_MANAGER);
    }

    /**
     * Check if user has permission for result entry/editing
     */
    public boolean hasResultEntryPermission(String systemUserId) {
        return hasPermission(systemUserId, PermissionFlow.RESULT_ENTRY);
    }

    /**
     * Check if user has permission for validation
     */
    public boolean hasValidationPermission(String systemUserId) {
        return hasPermission(systemUserId, PermissionFlow.VALIDATION_INTERPRETER);
    }

    /**
     * Check if user has permission for sample collection
     */
    public boolean hasSampleCollectionPermission(String systemUserId) {
        return hasPermission(systemUserId, PermissionFlow.SAMPLE_COLLECTOR);
    }

    public boolean hasPermission(String systemUserId, PermissionFlow flow) {
        return hasPermission(systemUserId, flow.getConfigProperty(), flow.getLogLabel());
    }

    public Map<String, Boolean> getPermissionsByFlow(String systemUserId) {
        Map<String, Boolean> permissionsByFlow = new LinkedHashMap<>();

        for (PermissionFlow flow : PermissionFlow.values()) {
            permissionsByFlow.put(flow.name(), hasPermission(systemUserId, flow));
        }

        return permissionsByFlow;
    }

    public Map<String, List<String>> getConfiguredProfileCodesByFlow() {
        Map<String, List<String>> configuredProfileCodesByFlow = new LinkedHashMap<>();

        for (PermissionFlow flow : PermissionFlow.values()) {
            String rawProfileCodes = ConfigurationProperties.getInstance().getPropertyValue(flow.getConfigProperty());
            configuredProfileCodesByFlow.put(flow.name(), parseProfileCodes(rawProfileCodes));
        }

        return configuredProfileCodesByFlow;
    }

    public String getEffectiveProfessionalProfileCode(String systemUserId) {
        return getUserProfileCode(systemUserId);
    }

    public Map<String, Boolean> getLegacyPermissionResponse(String systemUserId) {
        Map<String, Boolean> legacyPermissions = new LinkedHashMap<>();

        for (PermissionFlow flow : PermissionFlow.values()) {
            legacyPermissions.put(flow.getLegacyResponseKey(), hasPermission(systemUserId, flow));
        }

        return legacyPermissions;
    }

    /**
     * Generic permission check
     */
    private boolean hasPermission(String systemUserId, Property configProperty, String moduleName) {
        if (StringUtils.isBlank(systemUserId)) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "hasPermission",
                    "Attempted " + moduleName + " permission check with blank systemUserId");
            return false;
        }

        // Admin users always have permission
        if (isAdminUser(systemUserId)) {
            return true;
        }

        String rawProfileCodes = ConfigurationProperties.getInstance().getPropertyValue(configProperty);
        
        // If no profiles configured, allow all (backward compatibility)
        if (StringUtils.isBlank(rawProfileCodes)) {
            return true;
        }

        List<String> allowedProfileCodes = parseProfileCodes(rawProfileCodes);
        
        // If empty list after parsing, allow all
        if (allowedProfileCodes.isEmpty()) {
            return true;
        }

        Set<String> userProfileCodes = getUserProfileCodes(systemUserId);

        if (userProfileCodes.isEmpty()) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "hasPermission",
                    "User " + systemUserId + " has no professional profile linked, denying " + moduleName + " permission");
            return false;
        }

        boolean hasPermission = userProfileCodes.stream().anyMatch(allowedProfileCodes::contains);

        if (!hasPermission) {
            String denialMessage = "User " + systemUserId + " with profiles " + userProfileCodes + " denied "
                    + moduleName + " permission. Allowed profiles: " + allowedProfileCodes;
            LogEvent.logInfo(this.getClass().getSimpleName(), "hasPermission", denialMessage);
        }
        
        return hasPermission;
    }

    /**
     * Get the professional profile code for a system user
     */
    private String getUserProfileCode(String systemUserId) {
        Set<String> profileCodes = getUserProfileCodes(systemUserId);
        return profileCodes.stream().findFirst().orElse(null);
    }

    private Set<String> getUserProfileCodes(String systemUserId) {
        LinkedHashSet<String> profileCodes = new LinkedHashSet<>();
        try {
            SystemUser systemUser = systemUserService.get(systemUserId);
            if (systemUser == null) {
                return profileCodes;
            }

            String userProfileCode = systemUser.getProfessionalProfileCode();
            if (!StringUtils.isBlank(userProfileCode)) {
                profileCodes.add(StringUtils.upperCase(StringUtils.trim(userProfileCode)));
            }

            String linkedProviderPersonId = systemUser.getLinkedProviderPersonId();
            if (StringUtils.isBlank(linkedProviderPersonId)) {
                return profileCodes;
            }

            Person person = personService.get(linkedProviderPersonId);
            if (person == null) {
                return profileCodes;
            }

            Provider provider = providerService.getProviderByPerson(person);
            if (provider == null) {
                return profileCodes;
            }

            String providerProfileCode = provider.getProfessionalProfileCode();
            if (!StringUtils.isBlank(providerProfileCode)) {
                profileCodes.add(StringUtils.upperCase(StringUtils.trim(providerProfileCode)));
            }

            return profileCodes;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "getUserProfileCodes",
                    "Error getting profile codes for user " + systemUserId + ": " + e.getMessage());
            return profileCodes;
        }
    }

    /**
     * Check if user is admin
     */
    private boolean isAdminUser(String systemUserId) {
        try {
            if (userPermissionService.hasPermission(systemUserId, SystemPermission.GLOBAL_ADMIN)
                    || userPermissionService.hasPermission(systemUserId, SystemPermission.ADMINISTRATION)) {
                return true;
            }

            Set<String> effectiveRoleNames = userPermissionService.getEffectiveRoleNames(systemUserId);
            if (effectiveRoleNames.contains(Constants.ROLE_USER_ACCOUNT_ADMIN)
                    || effectiveRoleNames.contains(Constants.ROLE_GLOBAL_ADMIN)
                    || effectiveRoleNames.contains(Constants.ROLE_ADMINISTRATION)
                    || effectiveRoleNames.contains("Admin")
                    || effectiveRoleNames.contains("ROLE_ADMIN")) {
                return true;
            }

            SystemUser systemUser = systemUserService.get(systemUserId);
            if (systemUser != null && StringUtils.isNotBlank(systemUser.getLoginName())) {
                LoginUser loginUser = loginUserService.getUserProfile(systemUser.getLoginName());
                return loginUser != null && IActionConstants.YES.equalsIgnoreCase(loginUser.getIsAdmin());
            }

            return false;
        } catch (Exception e) {
            LogEvent.logError(this.getClass().getSimpleName(), "isAdminUser",
                    "Error checking admin status for user " + systemUserId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Parse comma-separated profile codes
     */
    private List<String> parseProfileCodes(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return List.of();
        }

        return Arrays.stream(StringUtils.split(rawValue, ','))
                .map(code -> StringUtils.upperCase(StringUtils.trimToNull(code)))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }
}
