package org.openelisglobal.sample.daoimpl;

import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.sample.dao.SampleCugPrefixDAO;
import org.openelisglobal.sample.valueholder.PatientCugPrefix;
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
    public Long findPatientPrefix(String patientId) {
        if (patientId == null || patientId.isBlank())
            return null;
        var rows = entityManager
                .createQuery("select p.prefix from PatientCugPrefix p where p.patientId = :patientId", Long.class)
                .setParameter("patientId", Integer.valueOf(patientId)).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public void bindPatientPrefix(String patientId, long prefix, String actorId) {
        PatientCugPrefix row = new PatientCugPrefix();
        row.setId(patientId);
        row.setPatientId(Integer.valueOf(patientId));
        row.setPrefix(prefix);
        row.setSysUserId(actorId);
        entityManager.persist(row);
        entityManager.flush();
    }

    @Override
    public void changePatientPrefix(String patientId, long prefix, String actorId) {
        PatientCugPrefix binding = entityManager.find(PatientCugPrefix.class, patientId);
        if (binding == null) {
            bindPatientPrefix(patientId, prefix, actorId);
        } else {
            binding.setPrefix(prefix);
            binding.setSysUserId(actorId);
            entityManager.flush();
        }
    }

    @Override
    public boolean isPrefixReferenced(long prefix, Timestamp now) {
        if (!entityManager.createQuery("select p.id from PatientCugPrefix p where p.prefix = :prefix", String.class)
                .setParameter("prefix", prefix).setMaxResults(1).getResultList().isEmpty())
            return true;
        // Leading zeroes and surrounding whitespace can occur in legacy CUGs.
        String pattern = prefix + ".%";
        if (!entityManager
                .createQuery("select i.id from SampleItem i where ltrim(trim(i.cugCode), '0') like :code", String.class)
                .setParameter("code", pattern).setMaxResults(1).getResultList().isEmpty())
            return true;
        return !entityManager.createQuery("select r.id from SampleCugReservation r "
                + "where ltrim(trim(r.reservedValue), '0') like :code and r.status = :status and r.expiresAt > :now",
                Integer.class).setParameter("code", pattern).setParameter("status", "RESERVED").setParameter("now", now)
                .setMaxResults(1).getResultList().isEmpty();
    }

    @Override
    public void releasePrefix(long prefix) {
        var rows = entityManager.createQuery("from SampleCugPrefix p where p.prefix = :prefix", SampleCugPrefix.class)
                .setParameter("prefix", prefix).getResultList();
        for (var row : rows)
            entityManager.remove(row);
        // Liquibase guards against remaining references and splits the occupied
        // interval under the same counter lock as automatic/manual allocation.
        entityManager.flush();
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
