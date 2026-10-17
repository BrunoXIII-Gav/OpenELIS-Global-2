package org.openelisglobal.sample.daoimpl;

import jakarta.persistence.LockModeType;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.sample.dao.SampleCugPrefixDAO;
import org.openelisglobal.sample.valueholder.SampleCugCounter;
import org.openelisglobal.sample.valueholder.SampleCugPrefix;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class SampleCugPrefixDAOImpl extends BaseDAOImpl<SampleCugCounter, String> implements SampleCugPrefixDAO {
    public SampleCugPrefixDAOImpl() {
        super(SampleCugCounter.class);
    }

    @Override
    public SampleCugCounter lockCounter() {
        // Query, rather than find(), so the lock is acquired even if this entity
        // was read earlier in the same persistence context.
        return entityManager.createQuery("from SampleCugCounter c where c.id = :id", SampleCugCounter.class)
                .setParameter("id", "1").setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult();
    }

    @Override
    public Long occupiedRangeEnd(long candidate) {
        var rows = entityManager
                .createQuery("select r.endPrefix from SampleCugPrefixRange r "
                        + "where r.startPrefix <= :candidate order by r.startPrefix desc", Long.class)
                .setParameter("candidate", candidate).setMaxResults(1).getResultList();
        return rows.isEmpty() || rows.get(0) < candidate ? null : rows.get(0);
    }

    @Override
    public boolean isAssigned(long prefix) {
        return !entityManager.createQuery("select p.id from SampleCugPrefix p where p.prefix = :prefix", String.class)
                .setParameter("prefix", prefix).setMaxResults(1).getResultList().isEmpty();
    }

    @Override
    public void register(long prefix, String actorId) {
        if (isAssigned(prefix))
            return;
        SampleCugPrefix row = new SampleCugPrefix();
        row.setId(Long.toString(prefix));
        row.setPrefix(prefix);
        row.setSysUserId(actorId);
        entityManager.persist(row);
        // Liquibase-managed triggers merge the occupied ranges atomically.
        entityManager.flush();
    }
}
