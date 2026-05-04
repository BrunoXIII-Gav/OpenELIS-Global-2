package org.openelisglobal.resultvalidation.service;

import java.util.List;
import java.util.Map;

public interface AnalysisValidationApprovalService {

    class ApprovalState {
        private final int approvedCount;
        private final int requiredApprovals;
        private final boolean approvedByCurrentUser;

        public ApprovalState(int approvedCount, int requiredApprovals, boolean approvedByCurrentUser) {
            this.approvedCount = approvedCount;
            this.requiredApprovals = requiredApprovals;
            this.approvedByCurrentUser = approvedByCurrentUser;
        }

        public int getApprovedCount() {
            return approvedCount;
        }

        public int getRequiredApprovals() {
            return requiredApprovals;
        }

        public boolean isApprovedByCurrentUser() {
            return approvedByCurrentUser;
        }
    }

    int getMinimumApproversRequired();

    int registerApprovalAndGetCount(String analysisId, String approverUserId);

    void clearApprovals(String analysisId);

    Map<String, ApprovalState> getApprovalStateByAnalysisIds(List<String> analysisIds, String approverUserId);
}
