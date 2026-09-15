package org.openelisglobal.authorization.service;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.valueholder.RoleFieldTagRestriction;
import org.openelisglobal.common.service.BaseObjectService;

public interface RoleFieldTagRestrictionService extends BaseObjectService<RoleFieldTagRestriction, Integer> {

    List<RoleFieldTagRestriction> getByRoleId(Integer roleId);

    List<RoleFieldTagRestriction> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey);
}
