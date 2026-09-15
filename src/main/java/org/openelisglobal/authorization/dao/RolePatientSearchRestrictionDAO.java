package org.openelisglobal.authorization.dao;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.valueholder.RolePatientSearchRestriction;
import org.openelisglobal.common.dao.BaseDAO;

public interface RolePatientSearchRestrictionDAO extends BaseDAO<RolePatientSearchRestriction, Integer> {

    List<RolePatientSearchRestriction> getByRoleId(Integer roleId);

    List<RolePatientSearchRestriction> getByRoleIds(Collection<Integer> roleIds);
}
