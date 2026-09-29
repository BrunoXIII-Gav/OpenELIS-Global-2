package org.openelisglobal.authorization.service;

import java.util.Set;

public interface ModuleAuthorizationService {

    AuthorizationDecision getAuthorization(String userId, String moduleKey, String actionKey);

    Set<String> getRestrictedFieldGroupKeys(String userId, String moduleKey);

    Set<String> getRestrictedFieldTagKeys(String userId, String moduleKey);

    default boolean hasPermission(String userId, String moduleKey, String actionKey) {
        return getAuthorization(userId, moduleKey, actionKey).allowed();
    }

    enum AuthorizationSource {
        MODULE_PERMISSION,
        LEGACY_PERMISSION,
        UNKNOWN_PERMISSION
    }

    record AuthorizationDecision(boolean allowed, AuthorizationSource source, boolean allLabUnits, Set<String> labUnitIds) {
        public boolean appliesToLabUnit(String labUnitId) {
            return allowed && (allLabUnits || labUnitIds.contains(labUnitId));
        }
    }
}
