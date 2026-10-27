package org.openelisglobal.authorization.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.authorization.valueholder.RoleFieldRestriction;
import org.openelisglobal.authorization.valueholder.RoleFieldTagRestriction;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.security.service.AuthorizationCatalogService;
import org.openelisglobal.security.service.UserPermissionService;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves configured module permissions, retaining legacy access for built-in
 * roles.
 *
 * <p>
 * Custom roles are authoritative even when their configuration is empty. Legacy
 * aliases and old derived role assignments cannot restore omitted actions.
 */
@Service
@Transactional(readOnly = true)
public class ModuleAuthorizationServiceImpl implements ModuleAuthorizationService {

    @Autowired
    private AuthorizationCatalogService authorizationCatalogService;

    @Autowired
    private RoleModulePermissionService roleModulePermissionService;

    @Autowired
    private RoleFieldRestrictionService roleFieldRestrictionService;

    @Autowired
    private RoleFieldTagRestrictionService roleFieldTagRestrictionService;

    @Autowired
    private UserPermissionService userPermissionService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private RoleService roleService;

    @Override
    public AuthorizationDecision getAuthorization(String userId, String moduleKey, String actionKey) {
        if (StringUtils.isBlank(userId) || !authorizationCatalogService.isKnownPermission(moduleKey, actionKey)) {
            return denied(AuthorizationSource.UNKNOWN_PERMISSION);
        }

        List<Integer> roleIds = userRoleService.getRoleIdsForUser(userId).stream().filter(StringUtils::isNumeric)
                .map(Integer::valueOf).toList();
        List<RoleModulePermission> modulePermissions = roleModulePermissionService.getByRoleIdsAndModuleKey(roleIds,
                moduleKey);

        if (!modulePermissions.isEmpty()) {
            return explicitDecision(modulePermissions, actionKey);
        }

        boolean hasExplicitRoleConfiguration = roleIds.stream()
                .anyMatch(roleId -> !roleModulePermissionService.getByRoleId(roleId).isEmpty());
        if (hasExplicitRoleConfiguration || hasCustomRole(roleIds)) {
            return denied(AuthorizationSource.MODULE_PERMISSION);
        }

        SystemPermission legacyPermission = legacyPermission(moduleKey, actionKey);
        boolean allowed = legacyPermission != null && userPermissionService.hasPermission(userId, legacyPermission);
        return new AuthorizationDecision(allowed, AuthorizationSource.LEGACY_PERMISSION, true, Set.of());
    }

    private boolean hasCustomRole(List<Integer> roleIds) {
        if (roleIds.isEmpty()) {
            return false;
        }
        Role customGroup = roleService.getRoleByName(Constants.CUSTOM_ROLES_GROUP);
        if (customGroup == null || StringUtils.isBlank(customGroup.getId())) {
            return false;
        }
        return roleIds.stream().map(roleId -> roleService.getRoleById(String.valueOf(roleId)))
                .anyMatch(role -> role != null && customGroup.getId().equals(role.getGroupingParent()));
    }

    @Override
    public Set<String> getRestrictedFieldGroupKeys(String userId, String moduleKey) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(moduleKey)
                || userPermissionService.hasPermission(userId, SystemPermission.GLOBAL_ADMIN)) {
            return Set.of();
        }

        List<Integer> roleIds = userRoleService.getRoleIdsForUser(userId).stream().filter(StringUtils::isNumeric)
                .map(Integer::valueOf).toList();
        return roleFieldRestrictionService.getByRoleIdsAndModuleKey(roleIds, moduleKey).stream()
                .map(RoleFieldRestriction::getFieldGroupKey).filter(StringUtils::isNotBlank)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<String> getRestrictedFieldTagKeys(String userId, String moduleKey) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(moduleKey)
                || userPermissionService.hasPermission(userId, SystemPermission.GLOBAL_ADMIN)) {
            return Set.of();
        }

        List<Integer> roleIds = userRoleService.getRoleIdsForUser(userId).stream().filter(StringUtils::isNumeric)
                .map(Integer::valueOf).toList();
        return roleFieldTagRestrictionService.getByRoleIdsAndModuleKey(roleIds, moduleKey).stream()
                .map(RoleFieldTagRestriction::getFieldTagKey).filter(StringUtils::isNotBlank)
                .collect(Collectors.toUnmodifiableSet());
    }

    private AuthorizationDecision explicitDecision(List<RoleModulePermission> modulePermissions, String actionKey) {
        List<RoleModulePermission> matchingPermissions = modulePermissions.stream()
                .filter(permission -> actionKey.equals(permission.getActionKey())).toList();
        if (matchingPermissions.isEmpty()) {
            return denied(AuthorizationSource.MODULE_PERMISSION);
        }

        boolean allLabUnits = matchingPermissions.stream().anyMatch(RoleModulePermission::isAllLabUnits);
        Set<String> labUnitIds = matchingPermissions.stream()
                .flatMap(permission -> permission.getLabUnitScopes().stream()).map(scope -> scope.getLabUnitId())
                .filter(StringUtils::isNotBlank).collect(Collectors.toUnmodifiableSet());
        return new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, allLabUnits, labUnitIds);
    }

    private AuthorizationDecision denied(AuthorizationSource source) {
        return new AuthorizationDecision(false, source, false, Set.of());
    }

    private SystemPermission legacyPermission(String moduleKey, String actionKey) {
        return switch (moduleKey) {
        case "sample-management" -> switch (actionKey) {
        case "aliquot" -> SystemPermission.ALIQUOT;
        case "read", "receive", "update", "print", "export" -> SystemPermission.SAMPLE_MANAGEMENT;
        default -> null;
        };
        case "orders" -> switch (actionKey) {
        case "create" -> SystemPermission.ORDER_ADD;
        case "update", "cancel" -> SystemPermission.ORDER_EDIT;
        case "read", "print" -> SystemPermission.ORDER;
        default -> null;
        };
        case "patients" -> switch (actionKey) {
        case "create", "update" -> SystemPermission.PATIENT_MANAGEMENT;
        case "merge" -> SystemPermission.GLOBAL_ADMIN;
        case "read", "export" -> SystemPermission.PATIENT;
        default -> null;
        };
        case "results" -> switch (actionKey) {
        case "read", "enter", "update", "correct", "export" -> SystemPermission.RESULTS;
        default -> null;
        };
        case "validation" -> switch (actionKey) {
        case "read", "validate", "revoke", "deliver" -> SystemPermission.VALIDATION;
        // Preserve existing built-in administrators' access without giving every
        // legacy validator permission to reopen delivered results. Configured roles
        // must grant this action explicitly, with their own laboratory-unit scope.
        case "reopen-delivery" -> SystemPermission.ADMINISTRATION;
        default -> null;
        };
        case "storage" -> switch (actionKey) {
        case "read" -> SystemPermission.STORAGE;
        case "update", "manage" -> SystemPermission.STORAGE_MANAGEMENT;
        default -> null;
        };
        case "administration" -> switch (actionKey) {
        case "read", "manage" -> SystemPermission.ADMINISTRATION;
        default -> null;
        };
        default -> null;
        };
    }
}
