package org.openelisglobal.orderadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldValueDAO;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;
import org.springframework.stereotype.Component;

@Component
public class SampleOrderAdditionalFieldValueDAOImpl extends BaseDAOImpl<SampleOrderAdditionalFieldValue, Integer>
        implements SampleOrderAdditionalFieldValueDAO {

    public SampleOrderAdditionalFieldValueDAOImpl() {
        super(SampleOrderAdditionalFieldValue.class);
    }

    @Override
    public Optional<SampleOrderAdditionalFieldValue> findBySampleIdAndFieldDefinitionId(Integer sampleId,
            Integer fieldDefinitionId) {
        if (sampleId == null || fieldDefinitionId == null) {
            return Optional.empty();
        }

        String hql = "from SampleOrderAdditionalFieldValue v where v.sampleId = :sampleId and v.fieldDefinitionId = :fieldDefinitionId";
        Query<SampleOrderAdditionalFieldValue> query = entityManager.unwrap(Session.class)
                .createQuery(hql, SampleOrderAdditionalFieldValue.class);
        query.setParameter("sampleId", sampleId);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        return Optional.ofNullable(query.uniqueResult());
    }

    @Override
    public List<SampleOrderAdditionalFieldValue> findBySampleIdAndFieldDefinitionIds(Integer sampleId,
            List<Integer> fieldDefinitionIds) {
        if (sampleId == null || fieldDefinitionIds == null || fieldDefinitionIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "from SampleOrderAdditionalFieldValue v where v.sampleId = :sampleId and v.fieldDefinitionId in (:fieldDefinitionIds)";
        Query<SampleOrderAdditionalFieldValue> query = entityManager.unwrap(Session.class)
                .createQuery(hql, SampleOrderAdditionalFieldValue.class);
        query.setParameter("sampleId", sampleId);
        query.setParameterList("fieldDefinitionIds", fieldDefinitionIds);
        return query.list();
    }
}
