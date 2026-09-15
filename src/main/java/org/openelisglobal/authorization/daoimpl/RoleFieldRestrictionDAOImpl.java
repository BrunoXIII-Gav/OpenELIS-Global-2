package org.openelisglobal.authorization.daoimpl;

import java.util.Collection;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.authorization.dao.RoleFieldRestrictionDAO;
import org.openelisglobal.authorization.valueholder.RoleFieldRestriction;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class RoleFieldRestrictionDAOImpl extends BaseDAOImpl<RoleFieldRestriction, Integer>
        implements RoleFieldRestrictionDAO {

    public RoleFieldRestrictionDAOImpl() {
        super(RoleFieldRestriction.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleFieldRestriction> getByRoleId(Integer roleId) {
        if (roleId == null) {
            return List.of();
        }
        Query<RoleFieldRestriction> query = entityManager.unwrap(Session.class).createQuery(
                "from RoleFieldRestriction restriction where restriction.roleId = :roleId "
                        + "order by restriction.moduleKey, restriction.fieldGroupKey",
                RoleFieldRestriction.class);
        query.setParameter("roleId", roleId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleFieldRestriction> getByRoleIdsAndModuleKey(Collection<Integer> roleIds, String moduleKey) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Query<RoleFieldRestriction> query = entityManager.unwrap(Session.class).createQuery(
                "from RoleFieldRestriction restriction where restriction.roleId in :roleIds "
                        + "and restriction.moduleKey = :moduleKey",
                RoleFieldRestriction.class);
        query.setParameter("roleIds", roleIds);
        query.setParameter("moduleKey", moduleKey);
        return query.list();
    }
}
