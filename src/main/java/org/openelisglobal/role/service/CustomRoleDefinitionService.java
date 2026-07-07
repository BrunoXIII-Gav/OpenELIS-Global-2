package org.openelisglobal.role.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.openelisglobal.role.form.CustomRoleDefinitionForm;
import org.openelisglobal.role.valueholder.Role;

public interface CustomRoleDefinitionService {

    List<Role> getCustomRoles();

    Role getCustomRole(String roleId);

    Map<String, List<Role>> getPermissionCatalog();

    List<String> getPermissionRoleIdsForCustomRole(String roleId);

    Map<String, List<String>> getPermissionRoleIdsForCustomRoles(Collection<String> roleIds);

    List<String> getApplicableLabUnitIdsForCustomRole(String roleId);

    Map<String, List<String>> getApplicableLabUnitIdsForCustomRoles(Collection<String> roleIds);

    Role saveCustomRole(CustomRoleDefinitionForm form, String sysUserId);

    void deleteCustomRole(String roleId, String sysUserId);
}
