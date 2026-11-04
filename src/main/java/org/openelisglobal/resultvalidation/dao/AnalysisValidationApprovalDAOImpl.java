package org.openelisglobal.resultvalidation.dao;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.resultvalidation.valueholder.AnalysisValidationApproval;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class AnalysisValidationApprovalDAOImpl extends BaseDAOImpl<AnalysisValidationApproval, String>
        implements AnalysisValidationApprovalDAO {
    public AnalysisValidationApprovalDAOImpl() {
        super(AnalysisValidationApproval.class);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByAnalysisAndUser(String analysisId, String userId) {
        if (analysisId == null || userId == null)
            return false;
        var query = entityManager
                .createQuery("SELECT COUNT(a) FROM AnalysisValidationApproval a WHERE a.analysisId = :id"
                        + " AND a.approverUserId = :user AND a.revokedAt IS NULL", Long.class);
        org.openelisglobal.common.util.NumericStringIdQuery.bind(query, "id", analysisId.trim());
        org.openelisglobal.common.util.NumericStringIdQuery.bind(query, "user", userId.trim());
        return query.getSingleResult() > 0;
    }

    @Override
    public void invalidateByAnalysisId(String analysisId, String actorId, String reason) {
        if (analysisId == null)
            return;
        var query = entityManager.createQuery(
                "FROM AnalysisValidationApproval a WHERE a.analysisId = :id AND a.revokedAt IS NULL",
                AnalysisValidationApproval.class);
        List<AnalysisValidationApproval> approvals = org.openelisglobal.common.util.NumericStringIdQuery
                .bind(query, "id", analysisId.trim()).getResultList();
        for (AnalysisValidationApproval approval : approvals) {
            approval.setRevokedAt(new Timestamp(System.currentTimeMillis()));
            approval.setRevokedBy(actorId);
            approval.setRevocationReason(reason);
        }
        entityManager.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> countByAnalysisIds(List<String> ids) {
        if (ids == null || ids.isEmpty())
            return Collections.emptyMap();
        var query = entityManager.createQuery(
                "SELECT a.analysisId, COUNT(a) FROM AnalysisValidationApproval a"
                        + " WHERE a.revokedAt IS NULL AND a.analysisId IN (:ids) GROUP BY a.analysisId",
                Object[].class);
        List<Object[]> rows = org.openelisglobal.common.util.NumericStringIdQuery.bindMany(query, "ids", ids)
                .getResultList();
        Map<String, Integer> result = new HashMap<>();
        rows.forEach(row -> result.put((String) row[0], ((Number) row[1]).intValue()));
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findApprovedAnalysisIdsByUser(List<String> ids, String userId) {
        if (ids == null || ids.isEmpty() || userId == null)
            return Collections.emptySet();
        var query = entityManager.createQuery(
                "SELECT a.analysisId FROM AnalysisValidationApproval a"
                        + " WHERE a.revokedAt IS NULL AND a.analysisId IN (:ids) AND a.approverUserId = :user",
                String.class);
        org.openelisglobal.common.util.NumericStringIdQuery.bindMany(query, "ids", ids);
        org.openelisglobal.common.util.NumericStringIdQuery.bind(query, "user", userId.trim());
        return new HashSet<>(query.getResultList());
    }
}
