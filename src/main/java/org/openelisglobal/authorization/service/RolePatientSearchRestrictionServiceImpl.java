package org.openelisglobal.authorization.service;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.dao.RolePatientSearchRestrictionDAO;
import org.openelisglobal.authorization.valueholder.RolePatientSearchRestriction;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RolePatientSearchRestrictionServiceImpl
        extends AuditableBaseObjectServiceImpl<RolePatientSearchRestriction, Integer>
        implements RolePatientSearchRestrictionService {

    @Autowired
    private RolePatientSearchRestrictionDAO rolePatientSearchRestrictionDAO;

    public RolePatientSearchRestrictionServiceImpl() {
        super(RolePatientSearchRestriction.class);
    }

    @Override
    protected RolePatientSearchRestrictionDAO getBaseObjectDAO() {
        return rolePatientSearchRestrictionDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePatientSearchRestriction> getByRoleId(Integer roleId) {
        return rolePatientSearchRestrictionDAO.getByRoleId(roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePatientSearchRestriction> getByRoleIds(Collection<Integer> roleIds) {
        return rolePatientSearchRestrictionDAO.getByRoleIds(roleIds);
    }
}
