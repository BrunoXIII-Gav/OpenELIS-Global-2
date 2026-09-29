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
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.authorization.service.RoleModulePermissionService;
import org.openelisglobal.authorization.service.RolePatientSearchRestrictionService;
import org.openelisglobal.authorization.service.RoleFieldRestrictionService;
import org.openelisglobal.authorization.service.RoleFieldTagRestrictionService;
import org.openelisglobal.authorization.valueholder.RoleFieldRestriction;
import org.openelisglobal.authorization.valueholder.RoleFieldTagRestriction;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.authorization.valueholder.RoleModulePermissionLabUnitScope;
import org.openelisglobal.authorization.valueholder.RolePatientSearchRestriction;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.exception.LIMSDuplicateRecordException;
import org.openelisglobal.role.form.CustomRoleDefinitionForm;
import org.openelisglobal.role.form.ModulePermissionForm;
import org.openelisglobal.role.valueholder.CustomRoleLabUnitScope;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.role.valueholder.RolePermissionMapping;
import org.openelisglobal.rolemodule.service.RoleModuleService;
import org.openelisglobal.systemusermodule.valueholder.RoleModule;
import org.openelisglobal.userrole.service.UserRoleService;
import org.openelisglobal.security.service.AuthorizationCatalogService;
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

    @Autowired
    private RoleModulePermissionService roleModulePermissionService;

    @Autowired
    private RoleFieldRestrictionService roleFieldRestrictionService;

    @Autowired
    private RoleFieldTagRestrictionService roleFieldTagRestrictionService;

    @Autowired
    private RolePatientSearchRestrictionService rolePatientSearchRestrictionService;

    @Autowired
    private AuthorizationCatalogService authorizationCatalogService;

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
    @Transactional(readOnly = true)
    public List<ModulePermissionForm> getModulePermissionsForCustomRole(String roleId) {
        if (StringUtils.isBlank(roleId)) {
            return Collections.emptyList();
        }

        List<RoleModulePermission> permissions = roleModulePermissionService.getByRoleId(Integer.parseInt(roleId));
        if (permissions.isEmpty()) {
            return deriveModulePermissionsFromLegacy(roleId);
        }

        Map<String, ModulePermissionForm> permissionsByModule = new LinkedHashMap<>();
        permissions.forEach(permission -> {
            ModulePermissionForm modulePermission = permissionsByModule.computeIfAbsent(permission.getModuleKey(),
                    ignored -> newModulePermission(permission.getModuleKey(), permission.isAllLabUnits(),
                            permission.getLabUnitScopes().stream().map(RoleModulePermissionLabUnitScope::getLabUnitId).toList()));
            modulePermission.getActionKeys().add(permission.getActionKey());
        });
        roleFieldRestrictionService.getByRoleId(Integer.parseInt(roleId)).forEach(restriction -> {
            ModulePermissionForm modulePermission = permissionsByModule.get(restriction.getModuleKey());
            if (modulePermission != null) {
                modulePermission.getRestrictedFieldGroupKeys().add(restriction.getFieldGroupKey());
            }
        });
        roleFieldTagRestrictionService.getByRoleId(Integer.parseInt(roleId)).forEach(restriction -> {
            ModulePermissionForm modulePermission = permissionsByModule.get(restriction.getModuleKey());
            if (modulePermission != null) {
                modulePermission.getRestrictedFieldTagKeys().add(restriction.getFieldTagKey());
            }
        });
        return new ArrayList<>(permissionsByModule.values());
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getRestrictedPatientSearchCriteriaForCustomRole(String roleId) {
        if (StringUtils.isBlank(roleId)) {
            return Collections.emptyList();
        }
        return rolePatientSearchRestrictionService.getByRoleId(Integer.parseInt(roleId)).stream()
                .map(RolePatientSearchRestriction::getCriterionKey).flatMap(this::expandLegacyNameCriterion).distinct()
                .toList();
    }

    @Override
    @Transactional
    public Role saveCustomRole(CustomRoleDefinitionForm form, String sysUserId) {
        String trimmedName = StringUtils.trimToEmpty(form.getName());
        if (trimmedName.isEmpty()) {
            throw new IllegalArgumentException("Custom role name is required");
        }

        boolean usesModulePermissions = form.getModulePermissions() != null;
        List<ModulePermissionForm> modulePermissions = usesModulePermissions
                ? normalizeModulePermissions(form.getModulePermissions()) : Collections.emptyList();
        List<String> permissionRoleIds = usesModulePermissions
                ? legacyPermissionRoleIdsFromModulePermissions(modulePermissions)
                : normalizePermissionRoleIds(form.getPermissionRoleIds());
        List<String> applicableLabUnitIds = usesModulePermissions
                ? legacyLabUnitIdsFromModulePermissions(modulePermissions)
                : normalizeLabUnitIds(form.getApplicableLabUnitIds());
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
        if (usesModulePermissions) {
            replaceModulePermissions(customRole.getId(), modulePermissions, sysUserId);
            replaceFieldRestrictions(customRole.getId(), modulePermissions, sysUserId);
            replaceFieldTagRestrictions(customRole.getId(), modulePermissions, sysUserId);
            replacePatientSearchRestrictions(customRole.getId(),
                    normalizePatientSearchCriteria(form.getRestrictedPatientSearchCriteria()), sysUserId);
        }
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
        List<RoleModulePermission> modulePermissions = roleModulePermissionService.getByRoleId(Integer.parseInt(roleId));
        if (!modulePermissions.isEmpty()) {
            roleModulePermissionService.deleteAll(modulePermissions);
        }
        List<RoleFieldRestriction> fieldRestrictions = roleFieldRestrictionService.getByRoleId(Integer.parseInt(roleId));
        if (!fieldRestrictions.isEmpty()) {
            roleFieldRestrictionService.deleteAll(fieldRestrictions);
        }
        List<RoleFieldTagRestriction> fieldTagRestrictions = roleFieldTagRestrictionService
                .getByRoleId(Integer.parseInt(roleId));
        if (!fieldTagRestrictions.isEmpty()) {
            roleFieldTagRestrictionService.deleteAll(fieldTagRestrictions);
        }
        List<RolePatientSearchRestriction> patientSearchRestrictions = rolePatientSearchRestrictionService
                .getByRoleId(Integer.parseInt(roleId));
        if (!patientSearchRestrictions.isEmpty()) {
            rolePatientSearchRestrictionService.deleteAll(patientSearchRestrictions);
        }
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

    private void replaceModulePermissions(String roleId, List<ModulePermissionForm> modulePermissions, String sysUserId) {
        List<RoleModulePermission> existingPermissions = roleModulePermissionService.getByRoleId(Integer.parseInt(roleId));
        if (!existingPermissions.isEmpty()) {
            roleModulePermissionService.deleteAll(existingPermissions);
        }

        modulePermissions.forEach(modulePermission -> modulePermission.getActionKeys().forEach(actionKey -> {
            RoleModulePermission permission = new RoleModulePermission();
            permission.setRoleId(Integer.parseInt(roleId));
            permission.setModuleKey(modulePermission.getModuleKey());
            permission.setActionKey(actionKey);
            permission.setAllLabUnits(modulePermission.isAllLabUnits());
            permission.setSysUserId(sysUserId);
            modulePermission.getLabUnitIds().forEach(labUnitId -> {
                RoleModulePermissionLabUnitScope scope = new RoleModulePermissionLabUnitScope();
                scope.setLabUnitId(labUnitId);
                scope.setSysUserId(sysUserId);
                permission.addLabUnitScope(scope);
            });
            roleModulePermissionService.insert(permission);
        }));
    }

    private void replaceFieldRestrictions(String roleId, List<ModulePermissionForm> modulePermissions, String sysUserId) {
        List<RoleFieldRestriction> existingRestrictions = roleFieldRestrictionService.getByRoleId(Integer.parseInt(roleId));
        if (!existingRestrictions.isEmpty()) {
            roleFieldRestrictionService.deleteAll(existingRestrictions);
        }

        modulePermissions.forEach(modulePermission -> modulePermission.getRestrictedFieldGroupKeys()
                .forEach(fieldGroupKey -> {
                    RoleFieldRestriction restriction = new RoleFieldRestriction();
                    restriction.setRoleId(Integer.parseInt(roleId));
                    restriction.setModuleKey(modulePermission.getModuleKey());
                    restriction.setFieldGroupKey(fieldGroupKey);
                    restriction.setSysUserId(sysUserId);
                    roleFieldRestrictionService.insert(restriction);
                }));
    }

    private void replaceFieldTagRestrictions(String roleId, List<ModulePermissionForm> modulePermissions,
            String sysUserId) {
        List<RoleFieldTagRestriction> existingRestrictions = roleFieldTagRestrictionService
                .getByRoleId(Integer.parseInt(roleId));
        if (!existingRestrictions.isEmpty()) {
            roleFieldTagRestrictionService.deleteAll(existingRestrictions);
        }

        modulePermissions.forEach(modulePermission -> modulePermission.getRestrictedFieldTagKeys().forEach(tagKey -> {
            RoleFieldTagRestriction restriction = new RoleFieldTagRestriction();
            restriction.setRoleId(Integer.parseInt(roleId));
            restriction.setModuleKey(modulePermission.getModuleKey());
            restriction.setFieldTagKey(tagKey);
            restriction.setSysUserId(sysUserId);
            roleFieldTagRestrictionService.insert(restriction);
        }));
    }

    private void replacePatientSearchRestrictions(String roleId, List<String> criteria, String sysUserId) {
        List<RolePatientSearchRestriction> existingRestrictions = rolePatientSearchRestrictionService
                .getByRoleId(Integer.parseInt(roleId));
        if (!existingRestrictions.isEmpty()) {
            rolePatientSearchRestrictionService.deleteAll(existingRestrictions);
        }
        criteria.forEach(criterion -> {
            RolePatientSearchRestriction restriction = new RolePatientSearchRestriction();
            restriction.setRoleId(Integer.parseInt(roleId));
            restriction.setCriterionKey(criterion);
            restriction.setSysUserId(sysUserId);
            rolePatientSearchRestrictionService.insert(restriction);
        });
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

    private List<String> normalizePatientSearchCriteria(List<String> criteria) {
        List<String> normalizedCriteria = criteria == null ? Collections.emptyList()
                : criteria.stream().filter(StringUtils::isNotBlank).map(StringUtils::trim)
                        .flatMap(this::expandLegacyNameCriterion).distinct().toList();
        if (normalizedCriteria.stream().anyMatch(
                criterion -> !authorizationCatalogService.isKnownPatientSearchCriterion(criterion))) {
            throw new IllegalArgumentException("An unknown patient search criterion was selected");
        }
        Set<String> identifyingCriteria = Set.of("first-name", "last-name", "birth-date", "gender", "national-id");
        if (normalizedCriteria.containsAll(identifyingCriteria)) {
            throw new IllegalArgumentException("At least one patient result identifier must remain visible");
        }
        return normalizedCriteria;
    }

    private Stream<String> expandLegacyNameCriterion(String criterion) {
        return "name".equals(criterion) ? Stream.of("first-name", "last-name") : Stream.of(criterion);
    }

    private List<ModulePermissionForm> normalizeModulePermissions(List<ModulePermissionForm> modulePermissions) {
        if (modulePermissions == null || modulePermissions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, ModulePermissionForm> normalizedPermissions = new LinkedHashMap<>();
        for (ModulePermissionForm submittedPermission : modulePermissions) {
            if (submittedPermission == null) {
                continue;
            }
            String moduleKey = StringUtils.trimToEmpty(submittedPermission.getModuleKey());
            if (moduleKey.isEmpty()) {
                throw new IllegalArgumentException("A module key is required");
            }

            List<String> actionKeys = submittedPermission.getActionKeys() == null ? Collections.emptyList()
                    : submittedPermission.getActionKeys().stream().filter(StringUtils::isNotBlank).map(StringUtils::trim)
                            .distinct().collect(Collectors.toList());
            if (actionKeys.isEmpty()) {
                continue;
            }
            if (actionKeys.stream().anyMatch(actionKey -> !authorizationCatalogService.isKnownPermission(moduleKey, actionKey))) {
                throw new IllegalArgumentException("An unknown module action was selected");
            }

            List<String> labUnitIds = normalizeLabUnitIds(submittedPermission.getLabUnitIds());
            List<String> restrictedFieldGroupKeys = normalizeFieldGroupKeys(moduleKey,
                    submittedPermission.getRestrictedFieldGroupKeys());
            List<String> restrictedFieldTagKeys = normalizeFieldTagKeys(moduleKey,
                    submittedPermission.getRestrictedFieldTagKeys());
            boolean administration = "administration".equals(moduleKey);
            if (!administration && submittedPermission.isAllLabUnits()) {
                throw new IllegalArgumentException("Lab module permissions must use selected lab units");
            }
            if (!administration && labUnitIds.isEmpty()) {
                throw new IllegalArgumentException("At least one lab unit is required for each selected module");
            }

            ModulePermissionForm existingPermission = normalizedPermissions.get(moduleKey);
            if (existingPermission == null) {
                normalizedPermissions.put(moduleKey,
                        newModulePermission(moduleKey, administration || submittedPermission.isAllLabUnits(), labUnitIds));
                existingPermission = normalizedPermissions.get(moduleKey);
            } else if (existingPermission.isAllLabUnits() != (administration || submittedPermission.isAllLabUnits())
                    || !new LinkedHashSet<>(existingPermission.getLabUnitIds()).equals(new LinkedHashSet<>(labUnitIds))) {
                throw new IllegalArgumentException("Each module must use one consistent lab-unit scope");
            }
            existingPermission.getActionKeys().addAll(actionKeys);
            existingPermission.setActionKeys(existingPermission.getActionKeys().stream().distinct().toList());
            existingPermission.getRestrictedFieldGroupKeys().addAll(restrictedFieldGroupKeys);
            existingPermission.setRestrictedFieldGroupKeys(
                    existingPermission.getRestrictedFieldGroupKeys().stream().distinct().toList());
            existingPermission.getRestrictedFieldTagKeys().addAll(restrictedFieldTagKeys);
            existingPermission.setRestrictedFieldTagKeys(
                    existingPermission.getRestrictedFieldTagKeys().stream().distinct().toList());
        }
        return new ArrayList<>(normalizedPermissions.values());
    }

    private List<String> legacyPermissionRoleIdsFromModulePermissions(List<ModulePermissionForm> modulePermissions) {
        Set<String> roleNames = new LinkedHashSet<>();
        modulePermissions.forEach(modulePermission -> {
            Set<String> actionKeys = new LinkedHashSet<>(modulePermission.getActionKeys());
            switch (modulePermission.getModuleKey()) {
            case "sample-management":
                if (!Collections.disjoint(actionKeys, List.of("read", "receive", "update", "print", "export"))) {
                    roleNames.add(Constants.ROLE_SAMPLE_MANAGEMENT);
                }
                if (actionKeys.contains("aliquot")) {
                    roleNames.add(Constants.ROLE_ALIQUOT);
                }
                break;
            case "orders":
                if (actionKeys.contains("create")) {
                    roleNames.add(Constants.ROLE_ORDER_ADD);
                }
                if (actionKeys.contains("update") || actionKeys.contains("cancel")) {
                    roleNames.add(Constants.ROLE_ORDER_EDIT);
                }
                if ((actionKeys.contains("read") || actionKeys.contains("print"))
                        && !actionKeys.contains("create") && !actionKeys.contains("update")
                        && !actionKeys.contains("cancel")) {
                    roleNames.add(Constants.ROLE_ORDER);
                }
                break;
            case "patients":
                if (actionKeys.contains("create") || actionKeys.contains("update") || actionKeys.contains("merge")) {
                    roleNames.add(Constants.ROLE_PATIENT_MANAGEMENT);
                } else if (actionKeys.contains("read") || actionKeys.contains("export")) {
                    roleNames.add(Constants.ROLE_PATIENT);
                }
                break;
            case "results":
                roleNames.add(Constants.ROLE_RESULTS);
                break;
            case "validation":
                roleNames.add(Constants.ROLE_VALIDATION);
                break;
            case "storage":
                if (actionKeys.contains("update") || actionKeys.contains("manage")) {
                    roleNames.add(Constants.ROLE_STORAGE_MANAGEMENT);
                } else if (actionKeys.contains("read")) {
                    roleNames.add(Constants.ROLE_STORAGE);
                }
                break;
            case "administration":
                roleNames.add(Constants.ROLE_ADMINISTRATION);
                break;
            default:
                break;
            }
        });

        List<String> permissionRoleIds = roleNames.stream().map(roleService::getRoleByName).filter(Objects::nonNull)
                .map(Role::getId).collect(Collectors.toList());
        return normalizePermissionRoleIds(permissionRoleIds);
    }

    private List<String> normalizeFieldGroupKeys(String moduleKey, List<String> fieldGroupKeys) {
        if (fieldGroupKeys == null || fieldGroupKeys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalizedKeys = fieldGroupKeys.stream().filter(StringUtils::isNotBlank).map(StringUtils::trim)
                .distinct().collect(Collectors.toList());
        if (normalizedKeys.stream().anyMatch(key -> !authorizationCatalogService.isKnownFieldGroup(moduleKey, key))) {
            throw new IllegalArgumentException("An unknown field restriction was selected");
        }
        return normalizedKeys;
    }

    private List<String> normalizeFieldTagKeys(String moduleKey, List<String> fieldTagKeys) {
        if (fieldTagKeys == null || fieldTagKeys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalizedKeys = fieldTagKeys.stream().filter(StringUtils::isNotBlank).map(StringUtils::trim)
                .distinct().collect(Collectors.toList());
        if (normalizedKeys.stream().anyMatch(key -> !authorizationCatalogService.isKnownFieldTag(moduleKey, key))) {
            throw new IllegalArgumentException("An unknown field tag was selected");
        }
        return normalizedKeys;
    }

    private List<String> legacyLabUnitIdsFromModulePermissions(List<ModulePermissionForm> modulePermissions) {
        return modulePermissions.stream().filter(modulePermission -> !modulePermission.isAllLabUnits())
                .flatMap(modulePermission -> modulePermission.getLabUnitIds().stream()).filter(StringUtils::isNotBlank)
                .map(StringUtils::trim).distinct().collect(Collectors.toList());
    }

    private List<ModulePermissionForm> deriveModulePermissionsFromLegacy(String roleId) {
        Set<String> permissionRoleNames = getPermissionRoleIdsForCustomRole(roleId).stream().map(roleService::getRoleById)
                .filter(Objects::nonNull).map(Role::getName).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        List<String> labUnitIds = getApplicableLabUnitIdsForCustomRole(roleId);
        List<ModulePermissionForm> modulePermissions = new ArrayList<>();

        if (permissionRoleNames.contains(Constants.ROLE_GENERIC_SAMPLE)
                || permissionRoleNames.contains(Constants.ROLE_SAMPLE_MANAGEMENT)
                || permissionRoleNames.contains(Constants.ROLE_ALIQUOT)) {
            List<String> actions = new ArrayList<>(List.of("read", "receive", "update", "print", "export"));
            if (permissionRoleNames.contains(Constants.ROLE_ALIQUOT)) {
                actions.add("aliquot");
            }
            modulePermissions.add(newModulePermission("sample-management", false, labUnitIds, actions));
        }
        if (permissionRoleNames.contains(Constants.ROLE_ORDER) || permissionRoleNames.contains(Constants.ROLE_ORDER_ADD)
                || permissionRoleNames.contains(Constants.ROLE_ORDER_EDIT)) {
            modulePermissions.add(newModulePermission("orders", false, labUnitIds,
                    List.of("read", "create", "update", "cancel", "print")));
        }
        if (permissionRoleNames.contains(Constants.ROLE_PATIENT)
                || permissionRoleNames.contains(Constants.ROLE_PATIENT_MANAGEMENT)
                || permissionRoleNames.contains(Constants.ROLE_PATIENT_HISTORY)) {
            modulePermissions.add(newModulePermission("patients", false, labUnitIds,
                    List.of("read", "create", "update", "merge", "export")));
        }
        if (permissionRoleNames.contains(Constants.ROLE_RESULTS)
                || permissionRoleNames.contains(Constants.ROLE_RESULTS_BY_UNIT)
                || permissionRoleNames.contains(Constants.ROLE_RESULTS_BY_PATIENT)
                || permissionRoleNames.contains(Constants.ROLE_RESULTS_BY_ORDER)) {
            modulePermissions.add(newModulePermission("results", false, labUnitIds,
                    List.of("read", "enter", "update", "correct", "export")));
        }
        if (permissionRoleNames.contains(Constants.ROLE_VALIDATION)
                || permissionRoleNames.contains(Constants.ROLE_VALIDATION_ROUTINE)
                || permissionRoleNames.contains(Constants.ROLE_VALIDATION_BY_ORDER)) {
            modulePermissions.add(newModulePermission("validation", false, labUnitIds,
                    List.of("read", "validate", "revoke")));
        }
        if (permissionRoleNames.contains(Constants.ROLE_STORAGE)
                || permissionRoleNames.contains(Constants.ROLE_STORAGE_MANAGEMENT)) {
            modulePermissions.add(newModulePermission("storage", false, labUnitIds,
                    List.of("read", "update", "manage")));
        }
        if (permissionRoleNames.contains(Constants.ROLE_ADMINISTRATION)) {
            modulePermissions.add(newModulePermission("administration", true, Collections.emptyList(),
                    List.of("read", "manage")));
        }
        return modulePermissions;
    }

    private ModulePermissionForm newModulePermission(String moduleKey, boolean allLabUnits, List<String> labUnitIds) {
        return newModulePermission(moduleKey, allLabUnits, labUnitIds, new ArrayList<>());
    }

    private ModulePermissionForm newModulePermission(String moduleKey, boolean allLabUnits, List<String> labUnitIds,
            List<String> actionKeys) {
        ModulePermissionForm modulePermission = new ModulePermissionForm();
        modulePermission.setModuleKey(moduleKey);
        modulePermission.setAllLabUnits(allLabUnits);
        modulePermission.setLabUnitIds(new ArrayList<>(labUnitIds));
        modulePermission.setActionKeys(new ArrayList<>(actionKeys));
        return modulePermission;
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
