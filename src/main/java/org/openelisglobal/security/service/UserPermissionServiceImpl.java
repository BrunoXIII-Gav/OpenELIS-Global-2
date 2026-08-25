package org.openelisglobal.security.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    private static final Map<String, List<String>> LAB_ROLE_GROUPS = Map.of(
            Constants.ROLE_GENERIC_SAMPLE, List.of(Constants.ROLE_SAMPLE_MANAGEMENT),
            Constants.ROLE_ORDER, List.of(Constants.ROLE_ORDER_ADD, Constants.ROLE_ORDER_EDIT),
            Constants.ROLE_PATIENT, List.of(Constants.ROLE_PATIENT_MANAGEMENT, Constants.ROLE_PATIENT_HISTORY),
            Constants.ROLE_STORAGE, List.of(Constants.ROLE_STORAGE_MANAGEMENT),
            Constants.ROLE_RESULTS,
            List.of(Constants.ROLE_RESULTS_BY_UNIT, Constants.ROLE_RESULTS_BY_PATIENT,
                    Constants.ROLE_RESULTS_BY_ORDER),
            Constants.ROLE_VALIDATION, List.of(Constants.ROLE_VALIDATION_ROUTINE, Constants.ROLE_VALIDATION_BY_ORDER));

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

        Set<String> customPermissionRoleNames = new LinkedHashSet<>();
        Set<String> effectiveRoleNames = new LinkedHashSet<>();
        for (String roleId : userRoleService.getRoleIdsForUser(userId)) {
            Role assignedRole = roleService.getRoleById(roleId);
            if (assignedRole == null || StringUtils.isBlank(assignedRole.getName())) {
                continue;
            }

            String assignedRoleName = StringUtils.trim(assignedRole.getName());
            effectiveRoleNames.add(assignedRoleName);
            customRoleDefinitionService.getPermissionRoleIdsForCustomRole(roleId).stream()
                    .map(roleService::getRoleById)
                    .filter(role -> role != null && StringUtils.isNotBlank(role.getName()))
                    .map(Role::getName)
                    .map(StringUtils::trim)
                    .forEach(customPermissionRoleNames::add);
        }

        normalizeGroupedRoles(customPermissionRoleNames);
        effectiveRoleNames.addAll(customPermissionRoleNames);
        normalizeGroupedRoles(effectiveRoleNames);
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
        boolean hasGenericSample = effectiveRoleNames.contains(Constants.ROLE_GENERIC_SAMPLE);
        boolean hasSampleManagement = effectiveRoleNames.contains(Constants.ROLE_SAMPLE_MANAGEMENT);
        boolean hasOrder = effectiveRoleNames.contains(Constants.ROLE_ORDER);
        boolean hasOrderAdd = effectiveRoleNames.contains(Constants.ROLE_ORDER_ADD);
        boolean hasOrderEdit = effectiveRoleNames.contains(Constants.ROLE_ORDER_EDIT);
        boolean hasPatient = effectiveRoleNames.contains(Constants.ROLE_PATIENT);
        boolean hasPatientManagement = effectiveRoleNames.contains(Constants.ROLE_PATIENT_MANAGEMENT);
        boolean hasPatientHistory = effectiveRoleNames.contains(Constants.ROLE_PATIENT_HISTORY);
        boolean hasResults = effectiveRoleNames.contains(Constants.ROLE_RESULTS);
        boolean hasResultsByUnit = effectiveRoleNames.contains(Constants.ROLE_RESULTS_BY_UNIT);
        boolean hasResultsByPatient = effectiveRoleNames.contains(Constants.ROLE_RESULTS_BY_PATIENT);
        boolean hasResultsByOrder = effectiveRoleNames.contains(Constants.ROLE_RESULTS_BY_ORDER);
        boolean hasStorage = effectiveRoleNames.contains(Constants.ROLE_STORAGE);
        boolean hasStorageManagement = effectiveRoleNames.contains(Constants.ROLE_STORAGE_MANAGEMENT);
        boolean hasValidation = effectiveRoleNames.contains(Constants.ROLE_VALIDATION);
        boolean hasValidationRoutine = effectiveRoleNames.contains(Constants.ROLE_VALIDATION_ROUTINE);
        boolean hasValidationByOrder = effectiveRoleNames.contains(Constants.ROLE_VALIDATION_BY_ORDER);
        boolean hasPathologist = effectiveRoleNames.contains(Constants.ROLE_PATHOLOGIST);

        if (hasGlobalAdmin || hasAdministration) {
            effectiveRoleNames.add(Constants.ROLE_GLOBAL_ADMIN);
            effectiveRoleNames.add(Constants.ROLE_ADMINISTRATION);
            effectiveRoleNames.add(Constants.ROLE_GENERIC_SAMPLE);
            effectiveRoleNames.add(Constants.ROLE_SAMPLE_MANAGEMENT);
            effectiveRoleNames.add(Constants.ROLE_ORDER);
            effectiveRoleNames.add(Constants.ROLE_ORDER_ADD);
            effectiveRoleNames.add(Constants.ROLE_ORDER_EDIT);
            effectiveRoleNames.add(Constants.ROLE_PATIENT);
            effectiveRoleNames.add(Constants.ROLE_PATIENT_MANAGEMENT);
            effectiveRoleNames.add(Constants.ROLE_PATIENT_HISTORY);
            effectiveRoleNames.add(Constants.ROLE_STORAGE);
            effectiveRoleNames.add(Constants.ROLE_STORAGE_MANAGEMENT);
            effectiveRoleNames.add(Constants.ROLE_RESULTS);
            effectiveRoleNames.add(Constants.ROLE_RESULTS_BY_UNIT);
            effectiveRoleNames.add(Constants.ROLE_RESULTS_BY_PATIENT);
            effectiveRoleNames.add(Constants.ROLE_RESULTS_BY_ORDER);
            effectiveRoleNames.add(Constants.ROLE_VALIDATION);
            effectiveRoleNames.add(Constants.ROLE_VALIDATION_ROUTINE);
            effectiveRoleNames.add(Constants.ROLE_VALIDATION_BY_ORDER);
        }

        if (hasReception) {
            effectiveRoleNames.add(Constants.ROLE_GENERIC_SAMPLE);
            effectiveRoleNames.add(Constants.ROLE_SAMPLE_MANAGEMENT);
            effectiveRoleNames.add(Constants.ROLE_ORDER);
            effectiveRoleNames.add(Constants.ROLE_ORDER_ADD);
            effectiveRoleNames.add(Constants.ROLE_ORDER_EDIT);
            effectiveRoleNames.add(Constants.ROLE_PATIENT);
            effectiveRoleNames.add(Constants.ROLE_PATIENT_MANAGEMENT);
            effectiveRoleNames.add(Constants.ROLE_PATIENT_HISTORY);
            effectiveRoleNames.add(Constants.ROLE_STORAGE);
            effectiveRoleNames.add(Constants.ROLE_STORAGE_MANAGEMENT);
        }

        if (hasResults) {
            effectiveRoleNames.add(Constants.ROLE_STORAGE);
            effectiveRoleNames.add(Constants.ROLE_SAMPLE_MANAGEMENT);
            effectiveRoleNames.add(Constants.ROLE_RESULTS_BY_UNIT);
            effectiveRoleNames.add(Constants.ROLE_RESULTS_BY_PATIENT);
            effectiveRoleNames.add(Constants.ROLE_RESULTS_BY_ORDER);
            effectiveRoleNames.add(Constants.ROLE_STORAGE_MANAGEMENT);
        }

        if (hasPathologist) {
            effectiveRoleNames.add(Constants.ROLE_VALIDATION);
            effectiveRoleNames.add(Constants.ROLE_VALIDATION_ROUTINE);
            effectiveRoleNames.add(Constants.ROLE_VALIDATION_BY_ORDER);
        }

        if (hasGenericSample) {
            effectiveRoleNames.add(Constants.ROLE_GENERIC_SAMPLE);
            effectiveRoleNames.add(Constants.ROLE_SAMPLE_MANAGEMENT);
        } else if (hasSampleManagement) {
            effectiveRoleNames.add(Constants.ROLE_GENERIC_SAMPLE);
        }

        if (hasOrder) {
            effectiveRoleNames.add(Constants.ROLE_ORDER);
            effectiveRoleNames.add(Constants.ROLE_ORDER_ADD);
            effectiveRoleNames.add(Constants.ROLE_ORDER_EDIT);
        } else if (hasOrderAdd || hasOrderEdit) {
            effectiveRoleNames.add(Constants.ROLE_ORDER);
        }

        if (hasPatient) {
            effectiveRoleNames.add(Constants.ROLE_PATIENT);
            effectiveRoleNames.add(Constants.ROLE_PATIENT_MANAGEMENT);
            effectiveRoleNames.add(Constants.ROLE_PATIENT_HISTORY);
        } else if (hasPatientManagement || hasPatientHistory) {
            effectiveRoleNames.add(Constants.ROLE_PATIENT);
        }

        if (hasResults) {
            effectiveRoleNames.add(Constants.ROLE_RESULTS);
            effectiveRoleNames.add(Constants.ROLE_RESULTS_BY_UNIT);
            effectiveRoleNames.add(Constants.ROLE_RESULTS_BY_PATIENT);
            effectiveRoleNames.add(Constants.ROLE_RESULTS_BY_ORDER);
        } else if (hasResultsByUnit || hasResultsByPatient || hasResultsByOrder) {
            effectiveRoleNames.add(Constants.ROLE_RESULTS);
        }

        if (hasStorage) {
            effectiveRoleNames.add(Constants.ROLE_STORAGE);
            effectiveRoleNames.add(Constants.ROLE_STORAGE_MANAGEMENT);
        } else if (hasStorageManagement) {
            effectiveRoleNames.add(Constants.ROLE_STORAGE);
        }

        if (hasValidation) {
            effectiveRoleNames.add(Constants.ROLE_VALIDATION);
            effectiveRoleNames.add(Constants.ROLE_VALIDATION_ROUTINE);
            effectiveRoleNames.add(Constants.ROLE_VALIDATION_BY_ORDER);
        } else if (hasValidationRoutine || hasValidationByOrder) {
            effectiveRoleNames.add(Constants.ROLE_VALIDATION);
        }
    }

    private void normalizeGroupedRoles(Set<String> roleNames) {
        if (roleNames.isEmpty()) {
            return;
        }

        LAB_ROLE_GROUPS.forEach((parentRoleName, childRoleNames) -> {
            boolean hasSelectedChild = childRoleNames.stream().anyMatch(roleNames::contains);
            if (hasSelectedChild) {
                roleNames.remove(parentRoleName);
            }
        });
    }
}
