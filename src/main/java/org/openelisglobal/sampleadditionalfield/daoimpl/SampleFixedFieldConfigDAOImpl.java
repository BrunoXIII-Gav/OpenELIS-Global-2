package org.openelisglobal.sampleadditionalfield.daoimpl;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.sampleadditionalfield.dao.SampleFixedFieldConfigDAO;
import org.openelisglobal.sampleadditionalfield.valueholder.SampleFixedFieldConfig;
import org.springframework.stereotype.Component;

@Component
public class SampleFixedFieldConfigDAOImpl extends BaseDAOImpl<SampleFixedFieldConfig, Integer>
        implements SampleFixedFieldConfigDAO {

    public SampleFixedFieldConfigDAOImpl() {
        super(SampleFixedFieldConfig.class);
    }

    @Override
    public List<SampleFixedFieldConfig> findAllOrdered() {
        String hql = "from SampleFixedFieldConfig c order by c.sortOrder asc, c.id asc";
        Query<SampleFixedFieldConfig> query = entityManager.unwrap(Session.class)
                .createQuery(hql, SampleFixedFieldConfig.class);
        return query.list();
    }

    @Override
    public Optional<SampleFixedFieldConfig> findByFieldKey(String fieldKey) {
        String hql = "from SampleFixedFieldConfig c where lower(c.fieldKey) = :fieldKey";
        Query<SampleFixedFieldConfig> query = entityManager.unwrap(Session.class)
                .createQuery(hql, SampleFixedFieldConfig.class);
        query.setParameter("fieldKey", fieldKey == null ? "" : fieldKey.trim().toLowerCase());
        return Optional.ofNullable(query.uniqueResult());
    }
}
