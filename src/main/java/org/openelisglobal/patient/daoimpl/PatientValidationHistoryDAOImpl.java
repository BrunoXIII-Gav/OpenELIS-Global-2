package org.openelisglobal.patient.daoimpl;

import java.util.List;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.common.util.NumericStringIdQuery;
import org.openelisglobal.patient.dao.PatientValidationHistoryDAO;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class PatientValidationHistoryDAOImpl extends BaseDAOImpl<Analysis, String>
        implements PatientValidationHistoryDAO {
    public PatientValidationHistoryDAOImpl() {
        super(Analysis.class);
    }

    @Override
    public List<Analysis> findValidatedAnalyses(String patientId, String finalizedStatusId, String analysisId) {
        String hql = "SELECT a FROM Analysis a JOIN FETCH a.sampleItem i JOIN FETCH i.sample s "
                + "JOIN FETCH a.test t LEFT JOIN FETCH t.localizedTestName LEFT JOIN FETCH a.testSection "
                + "WHERE a.statusId = :status AND EXISTS (SELECT h.id FROM SampleHuman h "
                + "WHERE h.sampleId = s.id AND h.patientId = :patientId) "
                + (analysisId == null ? "" : "AND a.id = :analysisId ")
                + "ORDER BY a.validationDate DESC NULLS LAST, a.id DESC";
        var query = entityManager.createQuery(hql, Analysis.class);
        NumericStringIdQuery.bind(query, "patientId", patientId);
        NumericStringIdQuery.bind(query, "status", finalizedStatusId);
        if (analysisId != null)
            NumericStringIdQuery.bind(query, "analysisId", analysisId);
        return query.getResultList();
    }

    @Override
    public List<Object[]> findActiveApprovers(List<String> analysisIds) {
        if (analysisIds.isEmpty())
            return List.of();
        var query = entityManager.createQuery(
                "SELECT a.analysisId, u.firstName, u.lastName, u.loginName, a.approvedAt "
                        + "FROM AnalysisValidationApproval a, SystemUser u WHERE u.id = a.approverUserId "
                        + "AND a.revokedAt IS NULL AND a.analysisId IN (:ids) ORDER BY a.approvedAt, a.id",
                Object[].class);
        return NumericStringIdQuery.bindMany(query, "ids", analysisIds).getResultList();
    }
}
