package org.openelisglobal.patientadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldValueDAO;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class PatientAdditionalFieldValueDAOImpl extends BaseDAOImpl<PatientAdditionalFieldValue, Integer>
        implements PatientAdditionalFieldValueDAO {

    public PatientAdditionalFieldValueDAOImpl() {
        super(PatientAdditionalFieldValue.class);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientAdditionalFieldValue> findByPatientIdAndFieldDefinitionId(Integer patientId,
            Integer fieldDefinitionId) {
        if (patientId == null || fieldDefinitionId == null) {
            return Optional.empty();
        }
        String hql = "from PatientAdditionalFieldValue v where v.patientId = :patientId and v.fieldDefinitionId = :fieldDefinitionId";
        Query<PatientAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientAdditionalFieldValue.class);
        query.setParameter("patientId", patientId);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        return query.uniqueResultOptional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientAdditionalFieldValue> findByPatientIdAndFieldDefinitionIds(Integer patientId,
            List<Integer> fieldDefinitionIds) {
        if (patientId == null || fieldDefinitionIds == null || fieldDefinitionIds.isEmpty()) {
            return Collections.emptyList();
        }
        String hql = "from PatientAdditionalFieldValue v where v.patientId = :patientId and v.fieldDefinitionId in (:fieldDefinitionIds)";
        Query<PatientAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientAdditionalFieldValue.class);
        query.setParameter("patientId", patientId);
        query.setParameterList("fieldDefinitionIds", fieldDefinitionIds);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientAdditionalFieldValue> findByPatientId(Integer patientId) {
        if (patientId == null) {
            return Collections.emptyList();
        }
        String hql = "from PatientAdditionalFieldValue v where v.patientId = :patientId";
        Query<PatientAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientAdditionalFieldValue.class);
        query.setParameter("patientId", patientId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByFieldDefinitionId(Integer fieldDefinitionId) {
        if (fieldDefinitionId == null) {
            return 0L;
        }
        String hql = "select count(v.id) from PatientAdditionalFieldValue v where v.fieldDefinitionId = :fieldDefinitionId";
        Query<Long> query = entityManager.unwrap(Session.class).createQuery(hql, Long.class);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        Long count = query.uniqueResult();
        return count == null ? 0L : count;
    }
}
