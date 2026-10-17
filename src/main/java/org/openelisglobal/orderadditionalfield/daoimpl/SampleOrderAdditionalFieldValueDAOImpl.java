package org.openelisglobal.orderadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldValueDAO;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;
import org.springframework.stereotype.Component;

@Component
public class SampleOrderAdditionalFieldValueDAOImpl extends BaseDAOImpl<SampleOrderAdditionalFieldValue, Integer>
        implements SampleOrderAdditionalFieldValueDAO {

    private static final String VALUE_ENTITY = SampleOrderAdditionalFieldValue.class.getName();
    private static final String DEF_ENTITY = OrderAdditionalFieldDefinition.class.getName();

    public SampleOrderAdditionalFieldValueDAOImpl() {
        super(SampleOrderAdditionalFieldValue.class);
    }

    @Override
    public Optional<SampleOrderAdditionalFieldValue> findBySampleIdAndFieldDefinitionId(Integer sampleId,
            Integer fieldDefinitionId) {
        if (sampleId == null || fieldDefinitionId == null) {
            return Optional.empty();
        }

        String hql = "from " + VALUE_ENTITY
                + " v where v.sampleId = :sampleId and v.fieldDefinitionId = :fieldDefinitionId";
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

        String hql = "from " + VALUE_ENTITY
                + " v where v.sampleId = :sampleId and v.fieldDefinitionId in (:fieldDefinitionIds)";
        Query<SampleOrderAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleOrderAdditionalFieldValue.class);
        query.setParameter("sampleId", sampleId);
        query.setParameterList("fieldDefinitionIds", fieldDefinitionIds);
        return query.list();
    }

    @Override
    public List<SampleOrderAdditionalFieldValue> findBySampleIdsAndFieldDefinitionIds(List<Integer> sampleIds,
            List<Integer> fieldDefinitionIds) {
        if (sampleIds == null || sampleIds.isEmpty() || fieldDefinitionIds == null || fieldDefinitionIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "from " + VALUE_ENTITY
                + " v where v.sampleId in (:sampleIds) and v.fieldDefinitionId in (:fieldDefinitionIds)";
        Query<SampleOrderAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleOrderAdditionalFieldValue.class);
        query.setParameterList("sampleIds", sampleIds);
        query.setParameterList("fieldDefinitionIds", fieldDefinitionIds);
        return query.list();
    }

    @Override
    public List<Integer> findDistinctSampleIdsBySearchableFieldValue(String searchValue, boolean uniqueOnly,
            int limit) {
        if (searchValue == null || searchValue.trim().isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }

        String sql = "select distinct v.sample_id from sample_order_additional_field_value v "
                + "join order_additional_field_def d on v.field_def_id = d.id "
                + "where d.active = true and d.searchable = true " + (uniqueOnly ? "and d.search_unique = true " : "")
                + "and lower(v.field_value) = :searchValue " + "order by v.sample_id desc";
        Query<?> query = entityManager.unwrap(Session.class).createNativeQuery(sql);
        query.setParameter("searchValue", searchValue.trim().toLowerCase());
        query.setMaxResults(limit);
        List<?> raw = query.list();
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        return raw.stream().filter(Number.class::isInstance).map(Number.class::cast).map(Number::intValue).toList();
    }

    @Override
    public boolean existsByFieldDefinitionIdAndFieldValueIgnoreCaseAndSampleIdNot(Integer fieldDefinitionId,
            String fieldValue, Integer excludedSampleId) {
        if (fieldDefinitionId == null || fieldValue == null || fieldValue.trim().isEmpty()) {
            return false;
        }

        String hql = "select count(v.id) from " + VALUE_ENTITY + " v "
                + "where v.fieldDefinitionId = :fieldDefinitionId and lower(v.fieldValue) = :fieldValue "
                + "and (:excludedSampleId is null or v.sampleId <> :excludedSampleId)";
        Query<Long> query = entityManager.unwrap(Session.class).createQuery(hql, Long.class);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        query.setParameter("fieldValue", fieldValue.trim().toLowerCase());
        query.setParameter("excludedSampleId", excludedSampleId);
        Long count = query.uniqueResult();
        return count != null && count > 0;
    }

    @Override
    public long countByFieldDefinitionId(Integer fieldDefinitionId) {
        if (fieldDefinitionId == null) {
            return 0L;
        }

        String hql = "select count(v.id) from " + VALUE_ENTITY + " v where v.fieldDefinitionId = :fieldDefinitionId";
        Query<Long> query = entityManager.unwrap(Session.class).createQuery(hql, Long.class);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        Long count = query.uniqueResult();
        return count == null ? 0L : count;
    }
}
