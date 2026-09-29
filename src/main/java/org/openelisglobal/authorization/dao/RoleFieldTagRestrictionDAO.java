package org.openelisglobal.authorization.dao;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.valueholder.RoleFieldTagRestriction;
import org.openelisglobal.common.dao.BaseDAO;

public interface RoleFieldTagRestrictionDAO extends BaseDAO<RoleFieldTagRestriction, Integer> {

    List<RoleFieldTagRestriction> getByRoleId(Integer roleId);

    List<RoleFieldTagRestriction> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey);
}
