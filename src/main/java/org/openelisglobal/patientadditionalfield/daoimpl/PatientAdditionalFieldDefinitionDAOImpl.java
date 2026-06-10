package org.openelisglobal.patientadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldDefinitionDAO;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldDefinition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class PatientAdditionalFieldDefinitionDAOImpl extends BaseDAOImpl<PatientAdditionalFieldDefinition, Integer>
        implements PatientAdditionalFieldDefinitionDAO {

    public PatientAdditionalFieldDefinitionDAOImpl() {
        super(PatientAdditionalFieldDefinition.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientAdditionalFieldDefinition> findAll(boolean activeOnly) {
        String hql = "from PatientAdditionalFieldDefinition d"
                + (activeOnly ? " where d.active = true" : "") + " order by d.sortOrder asc, d.id asc";
        Query<PatientAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientAdditionalFieldDefinition.class);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientAdditionalFieldDefinition> findByFieldKey(String fieldKey) {
        if (fieldKey == null || fieldKey.trim().isEmpty()) {
            return Optional.empty();
        }
        String hql = "from PatientAdditionalFieldDefinition d where lower(d.fieldKey) = :fieldKey";
        Query<PatientAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientAdditionalFieldDefinition.class);
        query.setParameter("fieldKey", fieldKey.trim().toLowerCase());
        return query.uniqueResultOptional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientAdditionalFieldDefinition> findByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        String hql = "from PatientAdditionalFieldDefinition d where d.id in (:ids)";
        Query<PatientAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientAdditionalFieldDefinition.class);
        query.setParameterList("ids", ids);
        return query.list();
    }
}
