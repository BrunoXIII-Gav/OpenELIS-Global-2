package org.openelisglobal.role.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.exception.LIMSDuplicateRecordException;
import org.openelisglobal.role.form.CustomRoleDefinitionForm;
import org.openelisglobal.role.valueholder.CustomRoleLabUnitScope;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.role.valueholder.RolePermissionMapping;
import org.openelisglobal.rolemodule.service.RoleModuleService;
import org.openelisglobal.systemusermodule.valueholder.RoleModule;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomRoleDefinitionServiceImpl implements CustomRoleDefinitionService {

    private static final Map<String, List<String>> LAB_ROLE_GROUPS = Map.of(
            Constants.ROLE_GENERIC_SAMPLE, List.of(Constants.ROLE_SAMPLE_MANAGEMENT),
            Constants.ROLE_ORDER, List.of(Constants.ROLE_ORDER_ADD, Constants.ROLE_ORDER_EDIT),
            Constants.ROLE_PATIENT, List.of(Constants.ROLE_PATIENT_MANAGEMENT, Constants.ROLE_PATIENT_HISTORY),
            Constants.ROLE_STORAGE, List.of(Constants.ROLE_STORAGE_MANAGEMENT),
            Constants.ROLE_RESULTS,
            List.of(Constants.ROLE_RESULTS_BY_UNIT, Constants.ROLE_RESULTS_BY_PATIENT,
                    Constants.ROLE_RESULTS_BY_ORDER),
            Constants.ROLE_VALIDATION, List.of(Constants.ROLE_VALIDATION_ROUTINE, Constants.ROLE_VALIDATION_BY_ORDER));

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private RoleService roleService;

    @Autowired
    private RoleModuleService roleModuleService;

    @Autowired
    private UserRoleService userRoleService;

    @Override
    @Transactional(readOnly = true)
    public List<Role> getCustomRoles() {
        String customGroupId = getRoleIdByName(Constants.CUSTOM_ROLES_GROUP);
        if (customGroupId == null) {
            return Collections.emptyList();
        }
        return roleService.getAllActiveRoles().stream()
                .filter(role -> customGroupId.equals(role.getGroupingParent()))
                .sorted((left, right) -> StringUtils.compareIgnoreCase(left.getName(), right.getName())).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Role getCustomRole(String roleId) {
        Role role = roleService.getRoleById(roleId);
        if (role == null) {
            return null;
        }
        String customGroupId = getRoleIdByName(Constants.CUSTOM_ROLES_GROUP);
        return customGroupId != null && customGroupId.equals(role.getGroupingParent()) ? role : null;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<Role>> getPermissionCatalog() {
        List<Role> activeRoles = roleService.getAllActiveRoles();
        String globalGroupId = getRoleIdByName(Constants.GLOBAL_ROLES_GROUP);
        String labGroupId = getRoleIdByName(Constants.LAB_ROLES_GROUP);

        Map<String, List<Role>> catalog = new HashMap<>();
        catalog.put("globalRoles", filterRolesByParent(activeRoles, globalGroupId).stream()
                .filter(this::isVisibleCustomGlobalPermissionRole).toList());
        catalog.put("labUnitRoles", filterRolesByParent(activeRoles, labGroupId).stream()
                .filter(this::isVisibleCustomLabPermissionRole).toList());
        return catalog;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getPermissionRoleIdsForCustomRole(String roleId) {
        return entityManager.createQuery(
                "from RolePermissionMapping rpm where rpm.customRoleId = :roleId order by rpm.id",
                RolePermissionMapping.class).setParameter("roleId", Integer.parseInt(roleId)).getResultList().stream()
                .map(RolePermissionMapping::getPermissionRoleId).map(String::valueOf).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<String>> getPermissionRoleIdsForCustomRoles(Collection<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> normalizedRoleIds = roleIds.stream().filter(StringUtils::isNotBlank).map(StringUtils::trim)
                .distinct().collect(Collectors.toList());
        if (normalizedRoleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<RolePermissionMapping> mappings = entityManager.createQuery(
                "from RolePermissionMapping rpm where rpm.customRoleId in :roleIds order by rpm.id",
                RolePermissionMapping.class)
                .setParameter("roleIds",
                        normalizedRoleIds.stream().map(Integer::parseInt).collect(Collectors.toList()))
                .getResultList();

        Map<String, List<String>> result = new LinkedHashMap<>();
        normalizedRoleIds.forEach(roleId -> result.put(roleId, new ArrayList<>()));
        mappings.forEach(mapping -> result.computeIfAbsent(String.valueOf(mapping.getCustomRoleId()),
                ignored -> new ArrayList<>()).add(String.valueOf(mapping.getPermissionRoleId())));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getApplicableLabUnitIdsForCustomRole(String roleId) {
        if (StringUtils.isBlank(roleId)) {
            return Collections.emptyList();
        }

        return entityManager.createQuery(
                "from CustomRoleLabUnitScope scope where scope.customRoleId = :roleId order by scope.id",
                CustomRoleLabUnitScope.class).setParameter("roleId", Integer.parseInt(roleId)).getResultList().stream()
                .map(CustomRoleLabUnitScope::getLabUnitId).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<String>> getApplicableLabUnitIdsForCustomRoles(Collection<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> normalizedRoleIds = roleIds.stream().filter(StringUtils::isNotBlank).map(StringUtils::trim)
                .distinct().collect(Collectors.toList());
        if (normalizedRoleIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<CustomRoleLabUnitScope> scopes = entityManager.createQuery(
                "from CustomRoleLabUnitScope scope where scope.customRoleId in :roleIds order by scope.id",
                CustomRoleLabUnitScope.class)
                .setParameter("roleIds",
                        normalizedRoleIds.stream().map(Integer::parseInt).collect(Collectors.toList()))
                .getResultList();

        Map<String, List<String>> result = new LinkedHashMap<>();
        normalizedRoleIds.forEach(roleId -> result.put(roleId, new ArrayList<>()));
        scopes.forEach(scope -> result.computeIfAbsent(String.valueOf(scope.getCustomRoleId()), ignored -> new ArrayList<>())
                .add(scope.getLabUnitId()));
        return result;
    }

    @Override
    @Transactional
    public Role saveCustomRole(CustomRoleDefinitionForm form, String sysUserId) {
        String trimmedName = StringUtils.trimToEmpty(form.getName());
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Custom role name is required");
        }

        List<String> permissionRoleIds = normalizePermissionRoleIds(form.getPermissionRoleIds());
        List<String> applicableLabUnitIds = normalizeLabUnitIds(form.getApplicableLabUnitIds());
        validatePermissionRoleIds(permissionRoleIds);
        validateApplicableLabUnits(permissionRoleIds, applicableLabUnitIds);

        Role duplicate = roleService.getRoleByName(trimmedName);
        if (duplicate != null && !Objects.equals(StringUtils.trimToNull(form.getId()), duplicate.getId())) {
            throw new LIMSDuplicateRecordException("Role already exists: " + trimmedName);
        }

        Role customRole = StringUtils.isBlank(form.getId()) ? new Role() : getCustomRole(form.getId());
        if (customRole == null) {
            customRole = new Role();
        }

        customRole.setName(trimmedName);
        customRole.setDescription(StringUtils.defaultIfBlank(StringUtils.trimToNull(form.getDescription()), trimmedName));
        customRole.setGroupingRole(false);
        customRole.setGroupingParent(getRoleIdByName(Constants.CUSTOM_ROLES_GROUP));
        customRole.setActive(true);
        customRole.setEditable(true);
        customRole.setSysUserId(sysUserId);

        if (StringUtils.isBlank(customRole.getId())) {
            roleService.insert(customRole);
            customRole = roleService.getRoleByName(trimmedName);
        } else {
            roleService.update(customRole);
        }

        replacePermissionMappings(customRole.getId(), permissionRoleIds);
        replaceLabUnitScopes(customRole.getId(), applicableLabUnitIds);
        syncRoleModules(customRole, permissionRoleIds, sysUserId);
        return customRole;
    }

    @Override
    @Transactional
    public void deleteCustomRole(String roleId, String sysUserId) {
        Role role = getCustomRole(roleId);
        if (role == null) {
            return;
        }
        if (!userRoleService.getUserIdsForRole(role.getName()).isEmpty()) {
            throw new IllegalStateException("Custom role is assigned to one or more users");
        }
        if (!roleService.getReferencingRoles(role).isEmpty()) {
            throw new IllegalStateException("Custom role still has child references");
        }
        replacePermissionMappings(roleId, Collections.emptyList());
        replaceLabUnitScopes(roleId, Collections.emptyList());
        List<RoleModule> existingModules = roleModuleService.getAllPermissionModulesByAgentId(Integer.parseInt(roleId));
        if (!existingModules.isEmpty()) {
            roleModuleService.deleteAll(existingModules);
        }
        role.setSysUserId(sysUserId);
        roleService.delete(role);
    }

    private void replacePermissionMappings(String roleId, List<String> permissionRoleIds) {
        entityManager.createNativeQuery("delete from clinlims.role_permission_mapping where custom_role_id = :roleId")
                .setParameter("roleId", Integer.parseInt(roleId)).executeUpdate();

        permissionRoleIds.forEach(permissionRoleId -> {
            RolePermissionMapping mapping = new RolePermissionMapping();
            mapping.setCustomRoleId(Integer.parseInt(roleId));
            mapping.setPermissionRoleId(Integer.parseInt(permissionRoleId));
            entityManager.persist(mapping);
        });
        entityManager.flush();
    }

    private void replaceLabUnitScopes(String roleId, List<String> applicableLabUnitIds) {
        entityManager.createNativeQuery("delete from clinlims.custom_role_lab_unit_scope where custom_role_id = :roleId")
                .setParameter("roleId", Integer.parseInt(roleId)).executeUpdate();

        applicableLabUnitIds.forEach(labUnitId -> {
            CustomRoleLabUnitScope scope = new CustomRoleLabUnitScope();
            scope.setCustomRoleId(Integer.parseInt(roleId));
            scope.setLabUnitId(labUnitId);
            entityManager.persist(scope);
        });
        entityManager.flush();
    }

    private void syncRoleModules(Role customRole, List<String> permissionRoleIds, String sysUserId) {
        List<RoleModule> existingModules = roleModuleService
                .getAllPermissionModulesByAgentId(Integer.parseInt(customRole.getId()));
        if (!existingModules.isEmpty()) {
            roleModuleService.deleteAll(existingModules);
        }

        Map<String, RoleModule> mergedModules = new LinkedHashMap<>();
        for (String permissionRoleId : permissionRoleIds) {
            for (RoleModule permissionModule : roleModuleService
                    .getAllPermissionModulesByAgentId(Integer.parseInt(permissionRoleId))) {
                String moduleId = permissionModule.getSystemModule().getId();
                RoleModule mergedModule = mergedModules.computeIfAbsent(moduleId, ignored -> {
                    RoleModule roleModule = new RoleModule();
                    roleModule.setRole(customRole);
                    roleModule.setSystemModule(permissionModule.getSystemModule());
                    roleModule.setSysUserId(sysUserId);
                    roleModule.setHasAdd("N");
                    roleModule.setHasDelete("N");
                    roleModule.setHasSelect("N");
                    roleModule.setHasUpdate("N");
                    return roleModule;
                });
                mergedModule.setHasAdd(mergeFlag(mergedModule.getHasAdd(), permissionModule.getHasAdd()));
                mergedModule.setHasDelete(mergeFlag(mergedModule.getHasDelete(), permissionModule.getHasDelete()));
                mergedModule.setHasSelect(mergeFlag(mergedModule.getHasSelect(), permissionModule.getHasSelect()));
                mergedModule.setHasUpdate(mergeFlag(mergedModule.getHasUpdate(), permissionModule.getHasUpdate()));
            }
        }

        mergedModules.values().forEach(roleModuleService::insert);
    }

    private String mergeFlag(String currentValue, String newValue) {
        return "Y".equalsIgnoreCase(currentValue) || "Y".equalsIgnoreCase(newValue) ? "Y" : "N";
    }

    private void validatePermissionRoleIds(List<String> permissionRoleIds) {
        Set<String> validPermissionRoleIds = getPermissionCatalog().values().stream().flatMap(List::stream).map(Role::getId)
                .collect(Collectors.toSet());
        if (!validPermissionRoleIds.containsAll(permissionRoleIds)) {
            throw new IllegalArgumentException("Custom roles can only include existing global or lab-unit permission roles");
        }
    }

    private void validateApplicableLabUnits(List<String> permissionRoleIds, List<String> applicableLabUnitIds) {
        if (!requiresLabUnitScope(permissionRoleIds)) {
            return;
        }

        if (applicableLabUnitIds.isEmpty()) {
            throw new IllegalArgumentException("At least one lab unit is required when lab-unit permissions are selected");
        }
    }

    private List<Role> filterRolesByParent(List<Role> roles, String parentId) {
        if (parentId == null) {
            return Collections.emptyList();
        }
        return roles.stream().filter(role -> parentId.equals(role.getGroupingParent())).filter(role -> !role.getGroupingRole())
                .sorted((left, right) -> StringUtils.compareIgnoreCase(left.getName(), right.getName())).toList();
    }

    private String getRoleIdByName(String roleName) {
        Role role = roleService.getRoleByName(roleName);
        return role == null ? null : role.getId();
    }

    private List<String> normalizePermissionRoleIds(List<String> permissionRoleIds) {
        if (permissionRoleIds == null) {
            return Collections.emptyList();
        }

        List<String> normalizedIds = new ArrayList<>(new LinkedHashSet<>(permissionRoleIds.stream()
                .filter(StringUtils::isNotBlank).map(StringUtils::trim).collect(Collectors.toList())));

        Map<String, String> roleIdByName = normalizedIds.stream().map(roleService::getRoleById).filter(Objects::nonNull)
                .filter(role -> StringUtils.isNotBlank(role.getName()) && StringUtils.isNotBlank(role.getId()))
                .collect(Collectors.toMap(role -> StringUtils.trim(role.getName()), Role::getId, (left, right) -> left,
                        LinkedHashMap::new));

        Set<String> normalizedIdSet = new LinkedHashSet<>(normalizedIds);
        LAB_ROLE_GROUPS.forEach((parentRoleName, childRoleNames) -> {
            boolean hasSelectedChild = childRoleNames.stream().map(roleIdByName::get).filter(Objects::nonNull)
                    .anyMatch(normalizedIdSet::contains);
            if (hasSelectedChild) {
                String parentRoleId = roleIdByName.get(parentRoleName);
                if (StringUtils.isNotBlank(parentRoleId)) {
                    normalizedIdSet.remove(parentRoleId);
                }
            }
        });

        return new ArrayList<>(normalizedIdSet);
    }

    private List<String> normalizeLabUnitIds(List<String> applicableLabUnitIds) {
        if (applicableLabUnitIds == null) {
            return Collections.emptyList();
        }

        return new ArrayList<>(new LinkedHashSet<>(applicableLabUnitIds.stream().filter(StringUtils::isNotBlank)
                .map(StringUtils::trim).collect(Collectors.toList())));
    }

    private boolean requiresLabUnitScope(List<String> permissionRoleIds) {
        String labGroupId = getRoleIdByName(Constants.LAB_ROLES_GROUP);
        if (labGroupId == null) {
            return false;
        }

        return permissionRoleIds.stream().map(roleService::getRoleById).filter(Objects::nonNull)
                .anyMatch(role -> labGroupId.equals(role.getGroupingParent()));
    }

    private boolean isVisibleCustomGlobalPermissionRole(Role role) {
        String roleName = StringUtils.trimToEmpty(role.getName());
        return !Constants.ROLE_GLOBAL_ADMIN.equalsIgnoreCase(roleName);
    }

    private boolean isVisibleCustomLabPermissionRole(Role role) {
        String roleName = StringUtils.trimToEmpty(role.getName());
        return !Constants.ROLE_RECEPTION.equalsIgnoreCase(roleName)
                && !Constants.ROLE_VALIDATION_BIOLOGIST.equalsIgnoreCase(roleName)
                && !Constants.ROLE_VALIDATION_MEDICAL.equalsIgnoreCase(roleName);
    }
}
