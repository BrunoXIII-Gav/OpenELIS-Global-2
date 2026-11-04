package org.openelisglobal.authorization.service;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.valueholder.RoleFieldRestriction;
import org.openelisglobal.common.service.BaseObjectService;

public interface RoleFieldRestrictionService extends BaseObjectService<RoleFieldRestriction, Integer> {

    List<RoleFieldRestriction> getByRoleId(Integer roleId);

    List<RoleFieldRestriction> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey);
}
