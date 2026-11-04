package org.openelisglobal.authorization.service;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.dao.RoleFieldTagRestrictionDAO;
import org.openelisglobal.authorization.valueholder.RoleFieldTagRestriction;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleFieldTagRestrictionServiceImpl extends AuditableBaseObjectServiceImpl<RoleFieldTagRestriction, Integer>
        implements RoleFieldTagRestrictionService {

    @Autowired
    private RoleFieldTagRestrictionDAO roleFieldTagRestrictionDAO;

    public RoleFieldTagRestrictionServiceImpl() {
        super(RoleFieldTagRestriction.class);
    }

    @Override
    protected RoleFieldTagRestrictionDAO getBaseObjectDAO() {
        return roleFieldTagRestrictionDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleFieldTagRestriction> getByRoleId(Integer roleId) {
        return roleFieldTagRestrictionDAO.getByRoleId(roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleFieldTagRestriction> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey) {
        return roleFieldTagRestrictionDAO.getByRoleIdsAndModuleKey(roleIds, moduleKey);
    }
}
