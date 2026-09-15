package org.openelisglobal.authorization.service;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.valueholder.RolePatientSearchRestriction;
import org.openelisglobal.common.service.BaseObjectService;

public interface RolePatientSearchRestrictionService extends BaseObjectService<RolePatientSearchRestriction, Integer> {

    List<RolePatientSearchRestriction> getByRoleId(Integer roleId);

    List<RolePatientSearchRestriction> getByRoleIds(Collection<Integer> roleIds);
}
