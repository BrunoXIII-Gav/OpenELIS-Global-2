package org.openelisglobal.sample.daoimpl;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.sample.dao.SampleItemAdditionalFieldValueDAO;
import org.openelisglobal.sample.valueholder.SampleItemAdditionalFieldValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class SampleItemAdditionalFieldValueDAOImpl extends BaseDAOImpl<SampleItemAdditionalFieldValue, Integer>
        implements SampleItemAdditionalFieldValueDAO {

    public SampleItemAdditionalFieldValueDAOImpl() {
        super(SampleItemAdditionalFieldValue.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SampleItemAdditionalFieldValue> findBySampleItemId(Integer sampleItemId) {
        String hql = "FROM SampleItemAdditionalFieldValue v WHERE v.sampleItemId = :sampleItemId ORDER BY v.id ASC";
        Query<SampleItemAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleItemAdditionalFieldValue.class);
        query.setParameter("sampleItemId", sampleItemId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SampleItemAdditionalFieldValue> findBySampleItemIdAndFieldDefinitionId(Integer sampleItemId,
            Integer fieldDefinitionId) {
        String hql = "FROM SampleItemAdditionalFieldValue v WHERE v.sampleItemId = :sampleItemId "
                + "AND v.fieldDefinitionId = :fieldDefinitionId";
        Query<SampleItemAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleItemAdditionalFieldValue.class);
        query.setParameter("sampleItemId", sampleItemId);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        SampleItemAdditionalFieldValue value = query.uniqueResult();
        return Optional.ofNullable(value);
    }

    @Override
    @Transactional(readOnly = true)
    public long countByFieldDefinitionId(Integer fieldDefinitionId) {
        if (fieldDefinitionId == null) {
            return 0L;
        }
        String hql = "select count(v.id) from SampleItemAdditionalFieldValue v where v.fieldDefinitionId = :fieldDefinitionId";
        Query<Long> query = entityManager.unwrap(Session.class).createQuery(hql, Long.class);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        Long count = query.uniqueResult();
        return count == null ? 0L : count;
    }
}
