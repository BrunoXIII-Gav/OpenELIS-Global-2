package org.openelisglobal.authorization.service;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.common.service.BaseObjectService;

public interface RoleModulePermissionService extends BaseObjectService<RoleModulePermission, Integer> {

    List<RoleModulePermission> getByRoleId(Integer roleId);

    List<RoleModulePermission> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey);
}
