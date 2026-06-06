package org.openelisglobal.analysis.daoimpl;

import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.analysis.dao.AnalysisTubeUsageDAO;
import org.openelisglobal.analysis.valueholder.AnalysisTubeUsage;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AnalysisTubeUsageDAOImpl extends BaseDAOImpl<AnalysisTubeUsage, Long> implements AnalysisTubeUsageDAO {

    public AnalysisTubeUsageDAOImpl() {
        super(AnalysisTubeUsage.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisTubeUsage> getByAnalysisId(String analysisId) throws LIMSRuntimeException {
        try {
            String hql = "FROM AnalysisTubeUsage u WHERE u.analysis.id = :analysisId ORDER BY u.childBlockName";
            Query<AnalysisTubeUsage> query = entityManager.unwrap(Session.class).createQuery(hql,
                    AnalysisTubeUsage.class);
            query.setParameter("analysisId", toNumericAnalysisId(analysisId));
            return query.list();
        } catch (Exception e) {
            throw new LIMSRuntimeException("Error getting analysis tube usage by analysis ID", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnalysisTubeUsage> getByParentAnalysisId(String parentAnalysisId) throws LIMSRuntimeException {
        try {
            String hql = "FROM AnalysisTubeUsage u WHERE u.parentAnalysis.id = :parentAnalysisId "
                    + "ORDER BY u.parentTubeBlockName, u.childBlockName";
            Query<AnalysisTubeUsage> query = entityManager.unwrap(Session.class).createQuery(hql,
                    AnalysisTubeUsage.class);
            query.setParameter("parentAnalysisId", toNumericAnalysisId(parentAnalysisId));
            return query.list();
        } catch (Exception e) {
            throw new LIMSRuntimeException("Error getting analysis tube usage by parent analysis ID", e);
        }
    }

    @Override
    public void deleteByAnalysisId(String analysisId) throws LIMSRuntimeException {
        try {
            String sql = "DELETE FROM analysis_tube_usage WHERE analysis_id = :analysisId";
            Query<?> query = entityManager.unwrap(Session.class).createNativeQuery(sql);
            query.setParameter("analysisId", toNumericAnalysisId(analysisId));
            query.executeUpdate();
        } catch (Exception e) {
            throw new LIMSRuntimeException("Error deleting analysis tube usage by analysis ID", e);
        }
    }

    private Long toNumericAnalysisId(String analysisId) {
        if (analysisId == null) {
            return null;
        }
        return Long.valueOf(analysisId);
    }
}
