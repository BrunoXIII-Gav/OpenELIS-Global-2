package org.openelisglobal.authorization.daoimpl;

import java.util.Collection;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.authorization.dao.RoleModulePermissionDAO;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class RoleModulePermissionDAOImpl extends BaseDAOImpl<RoleModulePermission, Integer>
        implements RoleModulePermissionDAO {

    public RoleModulePermissionDAOImpl() {
        super(RoleModulePermission.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleModulePermission> getByRoleId(Integer roleId) {
        if (roleId == null) {
            return List.of();
        }

        String hql = "select distinct permission from RoleModulePermission permission "
                + "left join fetch permission.labUnitScopes "
                + "where permission.roleId = :roleId order by permission.moduleKey, permission.actionKey";
        Query<RoleModulePermission> query = entityManager.unwrap(Session.class).createQuery(hql,
                RoleModulePermission.class);
        query.setParameter("roleId", roleId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleModulePermission> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }

        String hql = "select distinct permission from RoleModulePermission permission "
                + "left join fetch permission.labUnitScopes "
                + "where permission.roleId in :roleIds and permission.moduleKey = :moduleKey";
        Query<RoleModulePermission> query = entityManager.unwrap(Session.class).createQuery(hql,
                RoleModulePermission.class);
        query.setParameter("roleIds", roleIds);
        query.setParameter("moduleKey", moduleKey);
        return query.list();
    }
}
