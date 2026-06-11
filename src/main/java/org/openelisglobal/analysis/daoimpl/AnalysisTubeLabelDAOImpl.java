package org.openelisglobal.analysis.daoimpl;

import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.analysis.dao.AnalysisTubeLabelDAO;
import org.openelisglobal.analysis.valueholder.AnalysisTubeLabel;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AnalysisTubeLabelDAOImpl extends BaseDAOImpl<AnalysisTubeLabel, Long> implements AnalysisTubeLabelDAO {

    public AnalysisTubeLabelDAOImpl() {
        super(AnalysisTubeLabel.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisTubeLabel> getByAnalysisId(String analysisId) throws LIMSRuntimeException {
        try {
            String hql = "FROM AnalysisTubeLabel l WHERE l.analysis.id = :analysisId ORDER BY l.tubeBlockName";
            Query<AnalysisTubeLabel> query = entityManager.unwrap(Session.class).createQuery(hql, AnalysisTubeLabel.class);
            query.setParameter("analysisId", toNumericAnalysisId(analysisId));
            return query.list();
        } catch (Exception e) {
            throw new LIMSRuntimeException("Error getting analysis tube labels by analysis ID", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AnalysisTubeLabel> getByAnalysisIdAndBlockName(String analysisId, String blockName)
            throws LIMSRuntimeException {
        try {
            String hql = "FROM AnalysisTubeLabel l WHERE l.analysis.id = :analysisId AND lower(l.tubeBlockName) = :blockName";
            Query<AnalysisTubeLabel> query = entityManager.unwrap(Session.class).createQuery(hql, AnalysisTubeLabel.class);
            query.setParameter("analysisId", toNumericAnalysisId(analysisId));
            query.setParameter("blockName", StringUtils.trimToEmpty(blockName).toLowerCase());
            return query.list().stream().findFirst();
        } catch (Exception e) {
            throw new LIMSRuntimeException("Error getting analysis tube label by analysis ID and block", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByLabelCode(String labelCode, Long excludeId) throws LIMSRuntimeException {
        try {
            String hql = "SELECT count(l.id) FROM AnalysisTubeLabel l WHERE lower(l.labelCode) = :labelCode"
                    + (excludeId == null ? "" : " AND l.id <> :excludeId");
            Query<Long> query = entityManager.unwrap(Session.class).createQuery(hql, Long.class);
            query.setParameter("labelCode", StringUtils.trimToEmpty(labelCode).toLowerCase());
            if (excludeId != null) {
                query.setParameter("excludeId", excludeId);
            }
            Long count = query.uniqueResult();
            return count != null && count.longValue() > 0L;
        } catch (Exception e) {
            throw new LIMSRuntimeException("Error checking existing analysis tube label code", e);
        }
    }

    private Long toNumericAnalysisId(String analysisId) {
        if (analysisId == null) {
            return null;
        }
        return Long.valueOf(analysisId);
    }
}
