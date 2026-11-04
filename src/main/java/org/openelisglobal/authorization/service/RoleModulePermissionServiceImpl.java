package org.openelisglobal.authorization.service;

import java.util.Collection;
import java.util.List;
import org.openelisglobal.authorization.dao.RoleModulePermissionDAO;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleModulePermissionServiceImpl extends AuditableBaseObjectServiceImpl<RoleModulePermission, Integer>
        implements RoleModulePermissionService {

    @Autowired
    private RoleModulePermissionDAO roleModulePermissionDAO;

    public RoleModulePermissionServiceImpl() {
        super(RoleModulePermission.class);
    }

    @Override
    protected RoleModulePermissionDAO getBaseObjectDAO() {
        return roleModulePermissionDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleModulePermission> getByRoleId(Integer roleId) {
        return roleModulePermissionDAO.getByRoleId(roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleModulePermission> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey) {
        return roleModulePermissionDAO.getByRoleIdsAndModuleKey(roleIds, moduleKey);
    }
}
