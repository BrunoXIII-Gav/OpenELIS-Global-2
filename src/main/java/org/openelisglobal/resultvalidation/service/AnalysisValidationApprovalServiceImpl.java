package org.openelisglobal.resultvalidation.service;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.resultvalidation.dao.AnalysisValidationApprovalDAO;
import org.openelisglobal.resultvalidation.valueholder.AnalysisValidationApproval;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AnalysisValidationApprovalServiceImpl implements AnalysisValidationApprovalService {

    private final AnalysisValidationApprovalDAO approvalDAO;

    public AnalysisValidationApprovalServiceImpl(AnalysisValidationApprovalDAO approvalDAO) {
        this.approvalDAO = approvalDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public int getMinimumApproversRequired() {
        String configured = ConfigurationProperties.getInstance().getPropertyValue(Property.VALIDATION_MIN_APPROVERS);
        if (configured == null || configured.trim().isEmpty()) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(configured.trim()));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    @Override
    public int registerApprovalAndGetCount(String analysisId, String approverUserId) {
        if (analysisId == null || analysisId.trim().isEmpty() || approverUserId == null
                || approverUserId.trim().isEmpty()) {
            return 0;
        }
        if (!approvalDAO.existsByAnalysisAndUser(analysisId, approverUserId)) {
            AnalysisValidationApproval approval = new AnalysisValidationApproval();
            approval.setAnalysisId(analysisId.trim());
            approval.setApproverUserId(approverUserId.trim());
            approval.setApprovedAt(new Timestamp(System.currentTimeMillis()));
            approvalDAO.insert(approval);
        }
        return approvalDAO.countByAnalysisIds(List.of(analysisId.trim())).getOrDefault(analysisId.trim(), 0);
    }

    @Override
    public void clearApprovals(String analysisId) {
        approvalDAO.deleteByAnalysisId(analysisId);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, ApprovalState> getApprovalStateByAnalysisIds(List<String> analysisIds, String approverUserId) {
        if (analysisIds == null || analysisIds.isEmpty()) {
            return Collections.emptyMap();
        }
        int required = getMinimumApproversRequired();
        Map<String, Integer> counts = approvalDAO.countByAnalysisIds(analysisIds);
        Set<String> approvedByUser = approvalDAO.findApprovedAnalysisIdsByUser(analysisIds, approverUserId);

        Map<String, ApprovalState> result = new HashMap<>();
        for (String analysisId : analysisIds) {
            if (analysisId == null || analysisId.trim().isEmpty()) {
                continue;
            }
            int count = counts.getOrDefault(analysisId, 0);
            boolean isApprovedByUser = approvedByUser.contains(analysisId);
            result.put(analysisId, new ApprovalState(count, required, isApprovedByUser));
        }
        return result;
    }
}
