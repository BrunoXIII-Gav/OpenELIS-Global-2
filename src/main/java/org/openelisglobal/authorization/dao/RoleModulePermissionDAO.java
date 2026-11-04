package org.openelisglobal.authorization.dao;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.common.dao.BaseDAO;

public interface RoleModulePermissionDAO extends BaseDAO<RoleModulePermission, Integer> {

    List<RoleModulePermission> getByRoleId(Integer roleId);

    List<RoleModulePermission> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey);
}
