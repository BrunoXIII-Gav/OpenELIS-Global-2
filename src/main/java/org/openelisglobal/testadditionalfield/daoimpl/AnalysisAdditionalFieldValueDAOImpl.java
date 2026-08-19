package org.openelisglobal.testadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.testadditionalfield.dao.AnalysisAdditionalFieldValueDAO;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AnalysisAdditionalFieldValueDAOImpl extends BaseDAOImpl<AnalysisAdditionalFieldValue, Integer>
        implements AnalysisAdditionalFieldValueDAO {

    public AnalysisAdditionalFieldValueDAOImpl() {
        super(AnalysisAdditionalFieldValue.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisAdditionalFieldValue> findByAnalysisId(Integer analysisId) {
        String hql = "FROM AnalysisAdditionalFieldValue v WHERE v.analysisId = :analysisId ORDER BY v.id ASC";
        Query<AnalysisAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                AnalysisAdditionalFieldValue.class);
        query.setParameter("analysisId", analysisId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisAdditionalFieldValue> findByAnalysisIdAndFieldDefinitionIds(Integer analysisId,
            List<Integer> fieldDefIds) {
        if (fieldDefIds == null || fieldDefIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "FROM AnalysisAdditionalFieldValue v WHERE v.analysisId = :analysisId AND v.fieldDefinitionId IN (:fieldDefIds) ORDER BY v.id ASC";
        Query<AnalysisAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                AnalysisAdditionalFieldValue.class);
        query.setParameter("analysisId", analysisId);
        query.setParameterList("fieldDefIds", fieldDefIds);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisAdditionalFieldValue> findByAnalysisIdAndFieldDefinitionId(Integer analysisId,
            Integer fieldDefinitionId) {
        String hql = "FROM AnalysisAdditionalFieldValue v WHERE v.analysisId = :analysisId AND v.fieldDefinitionId = :fieldDefinitionId";
        Query<AnalysisAdditionalFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                AnalysisAdditionalFieldValue.class);
        query.setParameter("analysisId", analysisId);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        return Optional.ofNullable(query.uniqueResult());
    }

    @Override
    @Transactional(readOnly = true)
    public long countByFieldDefinitionId(Integer fieldDefinitionId) {
        if (fieldDefinitionId == null) {
            return 0L;
        }
        String hql = "select count(v.id) from AnalysisAdditionalFieldValue v where v.fieldDefinitionId = :fieldDefinitionId";
        Query<Long> query = entityManager.unwrap(Session.class).createQuery(hql, Long.class);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        Long count = query.uniqueResult();
        return count == null ? 0L : count;
    }
}
