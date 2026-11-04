package org.openelisglobal.authorization.dao;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.valueholder.RoleFieldRestriction;
import org.openelisglobal.common.dao.BaseDAO;

public interface RoleFieldRestrictionDAO extends BaseDAO<RoleFieldRestriction, Integer> {

    List<RoleFieldRestriction> getByRoleId(Integer roleId);

    List<RoleFieldRestriction> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey);
}
