package org.openelisglobal.resultvalidation.dao;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.resultvalidation.valueholder.AnalysisValidationApproval;

public interface AnalysisValidationApprovalDAO extends BaseDAO<AnalysisValidationApproval, String> {

    boolean existsByAnalysisAndUser(String analysisId, String approverUserId);

    void deleteByAnalysisId(String analysisId);

    Map<String, Integer> countByAnalysisIds(List<String> analysisIds);

    Set<String> findApprovedAnalysisIdsByUser(List<String> analysisIds, String approverUserId);
}
