package org.openelisglobal.security.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.role.service.CustomRoleDefinitionService;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserPermissionServiceImpl implements UserPermissionService {

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private CustomRoleDefinitionService customRoleDefinitionService;

    @Override
    public boolean hasPermission(String userId, SystemPermission permission) {
        if (StringUtils.isBlank(userId) || permission == null) {
            return false;
        }
        Set<String> effectiveRoleNames = getEffectiveRoleNames(userId);
        return permission.getLegacyRoleNames().stream().anyMatch(effectiveRoleNames::contains);
    }

    @Override
    public List<String> getUserIdsForPermission(SystemPermission permission) {
        if (permission == null) {
            return List.of();
        }
        return userRoleService.getAll().stream()
                .map(userRole -> userRole.getSystemUserId())
                .distinct()
                .filter(userId -> hasPermission(userId, permission))
                .toList();
    }

    @Override
    public List<String> getGrantedLegacyRoleNames(String userId, SystemPermission permission) {
        if (permission == null) {
            return List.of();
        }
        Set<String> effectiveRoleNames = getEffectiveRoleNames(userId);
        return permission.getLegacyRoleNames().stream()
                .filter(effectiveRoleNames::contains)
                .collect(Collectors.toList());
    }

    @Override
    public Set<String> getEffectiveRoleNames(String userId) {
        if (StringUtils.isBlank(userId)) {
            return Set.of();
        }

        Set<String> effectiveRoleNames = new LinkedHashSet<>();
        for (String roleId : userRoleService.getRoleIdsForUser(userId)) {
            Role assignedRole = roleService.getRoleById(roleId);
            if (assignedRole == null || StringUtils.isBlank(assignedRole.getName())) {
                continue;
            }

            effectiveRoleNames.add(StringUtils.trim(assignedRole.getName()));
            customRoleDefinitionService.getPermissionRoleIdsForCustomRole(roleId).stream()
                    .map(roleService::getRoleById)
                    .filter(role -> role != null && StringUtils.isNotBlank(role.getName()))
                    .map(Role::getName)
                    .map(StringUtils::trim)
                    .forEach(effectiveRoleNames::add);
        }

        addCompatibilityAliases(effectiveRoleNames);
        return effectiveRoleNames;
    }

    private void addCompatibilityAliases(Set<String> effectiveRoleNames) {
        if (effectiveRoleNames.isEmpty()) {
            return;
        }

        boolean hasGlobalAdmin = effectiveRoleNames.contains(Constants.ROLE_GLOBAL_ADMIN);
        boolean hasAdministration = effectiveRoleNames.contains(Constants.ROLE_ADMINISTRATION);
        boolean hasReception = effectiveRoleNames.contains(Constants.ROLE_RECEPTION);
        boolean hasResults = effectiveRoleNames.contains(Constants.ROLE_RESULTS);
        boolean hasValidation = effectiveRoleNames.contains(Constants.ROLE_VALIDATION);
        boolean hasPathologist = effectiveRoleNames.contains(Constants.ROLE_PATHOLOGIST);

        if (hasGlobalAdmin || hasAdministration) {
            effectiveRoleNames.add(Constants.ROLE_GLOBAL_ADMIN);
            effectiveRoleNames.add(Constants.ROLE_ADMINISTRATION);
            effectiveRoleNames.add(Constants.ROLE_GENERIC_SAMPLE);
            effectiveRoleNames.add(Constants.ROLE_ORDER);
            effectiveRoleNames.add(Constants.ROLE_PATIENT);
            effectiveRoleNames.add(Constants.ROLE_STORAGE);
            effectiveRoleNames.add(Constants.ROLE_RESULTS);
            effectiveRoleNames.add(Constants.ROLE_VALIDATION);
        }

        if (hasReception) {
            effectiveRoleNames.add(Constants.ROLE_GENERIC_SAMPLE);
            effectiveRoleNames.add(Constants.ROLE_ORDER);
            effectiveRoleNames.add(Constants.ROLE_PATIENT);
            effectiveRoleNames.add(Constants.ROLE_STORAGE);
        }

        if (hasResults) {
            effectiveRoleNames.add(Constants.ROLE_STORAGE);
        }

        if (hasPathologist) {
            effectiveRoleNames.add(Constants.ROLE_VALIDATION);
        }
    }
}
