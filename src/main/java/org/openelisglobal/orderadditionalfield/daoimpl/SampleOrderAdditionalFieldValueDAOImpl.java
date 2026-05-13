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
        Query<SampleOrderAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleOrderAdditionalFieldValue.class);
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
        Query<SampleOrderAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleOrderAdditionalFieldValue.class);
        query.setParameter("sampleId", sampleId);
        query.setParameterList("fieldDefinitionIds", fieldDefinitionIds);
        return query.list();
    }

    @Override
    public List<Integer> findDistinctSampleIdsBySearchableFieldValue(String searchValue, boolean uniqueOnly,
            int limit) {
        if (searchValue == null || searchValue.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        String hql = "select distinct v.sampleId from SampleOrderAdditionalFieldValue v, OrderAdditionalFieldDefinition d "
                + "where v.fieldDefinitionId = d.id and d.active = true and d.searchable = true "
                + (uniqueOnly ? "and d.searchUnique = true " : "") + "and lower(v.fieldValue) = :searchValue "
                + "order by v.sampleId desc";
        Query<Integer> query = entityManager.unwrap(Session.class).createQuery(hql, Integer.class);
        query.setParameter("searchValue", searchValue.trim().toLowerCase());
        query.setMaxResults(limit);
        return query.list();
    }

    @Override
    public boolean existsByFieldDefinitionIdAndFieldValueIgnoreCaseAndSampleIdNot(Integer fieldDefinitionId,
            String fieldValue, Integer excludedSampleId) {
        if (fieldDefinitionId == null || fieldValue == null || fieldValue.trim().isEmpty()) {
            return false;
        }

        String hql = "select count(v.id) from SampleOrderAdditionalFieldValue v "
                + "where v.fieldDefinitionId = :fieldDefinitionId and lower(v.fieldValue) = :fieldValue "
                + "and (:excludedSampleId is null or v.sampleId <> :excludedSampleId)";
        Query<Long> query = entityManager.unwrap(Session.class).createQuery(hql, Long.class);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        query.setParameter("fieldValue", fieldValue.trim().toLowerCase());
        query.setParameter("excludedSampleId", excludedSampleId);
        Long count = query.uniqueResult();
        return count != null && count > 0;
    }
}
