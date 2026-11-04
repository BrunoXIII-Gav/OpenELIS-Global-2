package org.openelisglobal.authorization.service;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.dao.RoleFieldRestrictionDAO;
import org.openelisglobal.authorization.valueholder.RoleFieldRestriction;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleFieldRestrictionServiceImpl extends AuditableBaseObjectServiceImpl<RoleFieldRestriction, Integer>
        implements RoleFieldRestrictionService {

    @Autowired
    private RoleFieldRestrictionDAO roleFieldRestrictionDAO;

    public RoleFieldRestrictionServiceImpl() {
        super(RoleFieldRestriction.class);
    }

    @Override
    protected RoleFieldRestrictionDAO getBaseObjectDAO() {
        return roleFieldRestrictionDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleFieldRestriction> getByRoleId(Integer roleId) {
        return roleFieldRestrictionDAO.getByRoleId(roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleFieldRestriction> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey) {
        return roleFieldRestrictionDAO.getByRoleIdsAndModuleKey(roleIds, moduleKey);
    }
}
