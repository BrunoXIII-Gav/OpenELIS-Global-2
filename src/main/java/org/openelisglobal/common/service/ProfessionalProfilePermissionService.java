package org.openelisglobal.common.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Compatibility adapter: role permissions determine who may perform actions in
 * different modules (order, patient, result, validation, sample collection)
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
    private org.openelisglobal.authorization.service.ModuleAuthorizationService moduleAuthorizationService;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private ProviderService providerService;
    @Autowired
    private PersonService personService;

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
        if (StringUtils.isBlank(systemUserId))
            return false;
        return switch (flow) {
        case ORDER_REQUESTER -> allowed(systemUserId, "orders", "create", "update");
        case PATIENT_MANAGER -> allowed(systemUserId, "patients", "create", "update");
        case RESULT_ENTRY -> allowed(systemUserId, "results", "enter", "update", "correct");
        case VALIDATION_INTERPRETER -> allowed(systemUserId, "validation", "validate");
        case SAMPLE_COLLECTOR -> allowed(systemUserId, "sample-management", "receive", "update");
        };
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
            configuredProfileCodesByFlow.put(flow.name(), List.of());
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
    private boolean allowed(String userId, String module, String... actions) {
        return Arrays.stream(actions)
                .anyMatch(action -> moduleAuthorizationService.hasPermission(userId, module, action));
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

}
