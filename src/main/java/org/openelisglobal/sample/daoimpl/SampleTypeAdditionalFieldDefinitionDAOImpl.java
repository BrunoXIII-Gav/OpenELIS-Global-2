package org.openelisglobal.sample.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.sample.dao.SampleTypeAdditionalFieldDefinitionDAO;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldDefinition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class SampleTypeAdditionalFieldDefinitionDAOImpl extends
        BaseDAOImpl<SampleTypeAdditionalFieldDefinition, Integer> implements SampleTypeAdditionalFieldDefinitionDAO {

    public SampleTypeAdditionalFieldDefinitionDAOImpl() {
        super(SampleTypeAdditionalFieldDefinition.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SampleTypeAdditionalFieldDefinition> findBySampleTypeId(Integer sampleTypeId, boolean includeInactive) {
        String hql = "FROM SampleTypeAdditionalFieldDefinition d WHERE d.typeOfSampleId = :sampleTypeId"
                + (includeInactive ? "" : " AND d.active = true") + " ORDER BY d.sortOrder ASC, d.id ASC";
        Query<SampleTypeAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleTypeAdditionalFieldDefinition.class);
        query.setParameter("sampleTypeId", sampleTypeId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SampleTypeAdditionalFieldDefinition> findBySampleTypeIds(List<Integer> sampleTypeIds,
            boolean activeOnly) {
        if (sampleTypeIds == null || sampleTypeIds.isEmpty()) {
            return Collections.emptyList();
        }
        String hql = "FROM SampleTypeAdditionalFieldDefinition d WHERE d.typeOfSampleId IN (:sampleTypeIds)"
                + (activeOnly ? " AND d.active = true" : "")
                + " ORDER BY d.typeOfSampleId ASC, d.sortOrder ASC, d.id ASC";
        Query<SampleTypeAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleTypeAdditionalFieldDefinition.class);
        query.setParameterList("sampleTypeIds", sampleTypeIds);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SampleTypeAdditionalFieldDefinition> findBySampleTypeIdAndFieldKey(Integer sampleTypeId,
            String fieldKey) {
        String hql = "FROM SampleTypeAdditionalFieldDefinition d WHERE d.typeOfSampleId = :sampleTypeId "
                + "AND lower(d.fieldKey) = :fieldKey";
        Query<SampleTypeAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleTypeAdditionalFieldDefinition.class);
        query.setParameter("sampleTypeId", sampleTypeId);
        query.setParameter("fieldKey", fieldKey.toLowerCase());
        SampleTypeAdditionalFieldDefinition definition = query.uniqueResult();
        return Optional.ofNullable(definition);
    }
}
