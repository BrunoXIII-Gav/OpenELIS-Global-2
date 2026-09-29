package org.openelisglobal.authorization.daoimpl;

import java.util.Collection;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.authorization.dao.RoleFieldTagRestrictionDAO;
import org.openelisglobal.authorization.valueholder.RoleFieldTagRestriction;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class RoleFieldTagRestrictionDAOImpl extends BaseDAOImpl<RoleFieldTagRestriction, Integer>
        implements RoleFieldTagRestrictionDAO {

    public RoleFieldTagRestrictionDAOImpl() {
        super(RoleFieldTagRestriction.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleFieldTagRestriction> getByRoleId(Integer roleId) {
        if (roleId == null) {
            return List.of();
        }
        Query<RoleFieldTagRestriction> query = entityManager.unwrap(Session.class).createQuery(
                "from RoleFieldTagRestriction restriction where restriction.roleId = :roleId "
                        + "order by restriction.moduleKey, restriction.fieldTagKey",
                RoleFieldTagRestriction.class);
        query.setParameter("roleId", roleId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleFieldTagRestriction> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Query<RoleFieldTagRestriction> query = entityManager.unwrap(Session.class).createQuery(
                "from RoleFieldTagRestriction restriction where restriction.roleId in :roleIds "
                        + "and restriction.moduleKey = :moduleKey",
                RoleFieldTagRestriction.class);
        query.setParameter("roleIds", roleIds);
        query.setParameter("moduleKey", moduleKey);
        return query.list();
    }
}
