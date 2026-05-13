package org.openelisglobal.resultvalidation.dao;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    public boolean existsByAnalysisAndUser(String analysisId, String approverUserId) {
        Long analysisNumericId = toNumericId(analysisId);
        Long approverNumericId = toNumericId(approverUserId);
        if (analysisNumericId == null || approverNumericId == null) {
            return false;
        }
        Number count = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(id) FROM analysis_validation_approval "
                        + "WHERE analysis_id = :analysisId AND approver_user_id = :approverUserId")
                .setParameter("analysisId", analysisNumericId).setParameter("approverUserId", approverNumericId)
                .getSingleResult();
        return count != null && count.longValue() > 0L;
    }

    @Override
    public void deleteByAnalysisId(String analysisId) {
        Long analysisNumericId = toNumericId(analysisId);
        if (analysisNumericId == null) {
            return;
        }
        entityManager.createNativeQuery("DELETE FROM analysis_validation_approval WHERE analysis_id = :analysisId")
                .setParameter("analysisId", analysisNumericId).executeUpdate();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> countByAnalysisIds(List<String> analysisIds) {
        if (analysisIds == null || analysisIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> numericIds = analysisIds.stream().map(this::toNumericId).filter(id -> id != null).distinct()
                .collect(Collectors.toList());
        if (numericIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = buildIndexedPlaceholders(numericIds.size(), 1);
        String sql = "SELECT analysis_id, COUNT(id) FROM analysis_validation_approval WHERE analysis_id IN ("
                + placeholders + ") GROUP BY analysis_id";
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < numericIds.size(); i++) {
            query.setParameter(i + 1, numericIds.get(i));
        }
        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();
        Map<String, Integer> counts = new HashMap<>();
        for (Object[] row : rows) {
            if (row != null && row.length == 2 && row[0] != null && row[1] != null) {
                counts.put(String.valueOf(row[0]), ((Number) row[1]).intValue());
            }
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findApprovedAnalysisIdsByUser(List<String> analysisIds, String approverUserId) {
        Long approverNumericId = toNumericId(approverUserId);
        if (analysisIds == null || analysisIds.isEmpty() || approverNumericId == null) {
            return Collections.emptySet();
        }
        List<Long> numericIds = analysisIds.stream().map(this::toNumericId).filter(id -> id != null).distinct()
                .collect(Collectors.toList());
        if (numericIds.isEmpty()) {
            return Collections.emptySet();
        }
        String placeholders = buildIndexedPlaceholders(numericIds.size(), 1);
        String sql = "SELECT analysis_id FROM analysis_validation_approval WHERE analysis_id IN (" + placeholders
                + ") AND approver_user_id = ?" + (numericIds.size() + 1);
        var query = entityManager.createNativeQuery(sql);
        for (int i = 0; i < numericIds.size(); i++) {
            query.setParameter(i + 1, numericIds.get(i));
        }
        query.setParameter(numericIds.size() + 1, approverNumericId);
        @SuppressWarnings("unchecked")
        List<Number> rows = query.getResultList();
        return rows.stream().filter(n -> n != null).map(n -> String.valueOf(n.longValue())).collect(Collectors.toSet());
    }

    private Long toNumericId(String id) {
        if (id == null) {
            return null;
        }
        String cleaned = id.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildIndexedPlaceholders(int count, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("?").append(startIndex + i);
        }
        return sb.toString();
    }
}
