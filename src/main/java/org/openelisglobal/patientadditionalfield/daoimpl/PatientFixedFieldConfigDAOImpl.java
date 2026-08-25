package org.openelisglobal.patientadditionalfield.daoimpl;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.patientadditionalfield.dao.PatientFixedFieldConfigDAO;
import org.openelisglobal.patientadditionalfield.valueholder.PatientFixedFieldConfig;
import org.springframework.stereotype.Component;

@Component
public class PatientFixedFieldConfigDAOImpl extends BaseDAOImpl<PatientFixedFieldConfig, Integer>
        implements PatientFixedFieldConfigDAO {

    public PatientFixedFieldConfigDAOImpl() {
        super(PatientFixedFieldConfig.class);
    }

    @Override
    public List<PatientFixedFieldConfig> findAllOrdered() {
        String hql = "from PatientFixedFieldConfig c order by c.sortOrder asc, c.id asc";
        Query<PatientFixedFieldConfig> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientFixedFieldConfig.class);
        return query.list();
    }

    @Override
    public Optional<PatientFixedFieldConfig> findByFieldKey(String fieldKey) {
        String hql = "from PatientFixedFieldConfig c where lower(c.fieldKey) = :fieldKey";
        Query<PatientFixedFieldConfig> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientFixedFieldConfig.class);
        query.setParameter("fieldKey", fieldKey == null ? "" : fieldKey.trim().toLowerCase());
        return Optional.ofNullable(query.uniqueResult());
    }
}
