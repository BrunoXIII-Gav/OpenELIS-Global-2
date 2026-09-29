package org.openelisglobal.authorization.daoimpl;

import java.util.Collection;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.authorization.dao.RolePatientSearchRestrictionDAO;
import org.openelisglobal.authorization.valueholder.RolePatientSearchRestriction;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class RolePatientSearchRestrictionDAOImpl extends BaseDAOImpl<RolePatientSearchRestriction, Integer>
        implements RolePatientSearchRestrictionDAO {

    public RolePatientSearchRestrictionDAOImpl() {
        super(RolePatientSearchRestriction.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePatientSearchRestriction> getByRoleId(Integer roleId) {
        if (roleId == null) {
            return List.of();
        }
        Query<RolePatientSearchRestriction> query = entityManager.unwrap(Session.class).createQuery(
                "from RolePatientSearchRestriction restriction where restriction.roleId = :roleId "
                        + "order by restriction.criterionKey",
                RolePatientSearchRestriction.class);
        query.setParameter("roleId", roleId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePatientSearchRestriction> getByRoleIds(Collection<Integer> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Query<RolePatientSearchRestriction> query = entityManager.unwrap(Session.class).createQuery(
                "from RolePatientSearchRestriction restriction where restriction.roleId in :roleIds",
                RolePatientSearchRestriction.class);
        query.setParameter("roleIds", roleIds);
        return query.list();
    }
}
