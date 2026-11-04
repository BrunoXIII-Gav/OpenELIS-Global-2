package org.openelisglobal.analysis.service;

import java.math.BigDecimal;
import java.util.List;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.analysis.valueholder.AnalysisTubeUsage;
import org.openelisglobal.common.service.BaseObjectService;

public interface AnalysisTubeUsageService extends BaseObjectService<AnalysisTubeUsage, Long> {
    List<AnalysisTubeUsage> getByAnalysisId(String analysisId);

    List<AnalysisTubeUsage> getByParentAnalysisId(String parentAnalysisId);

    void deleteByAnalysisId(String analysisId);

    record UsageInput(String childBlockName, String parentTubeBlockName, BigDecimal usedQuantity) {
    }

    void replaceUsages(Analysis analysis, Analysis parentAnalysis, List<UsageInput> inputs, String actorId);

    AnalysisTubeUsage createUsage(Analysis analysis, Analysis parentAnalysis, String childBlockName,
            String parentTubeBlockName, BigDecimal usedQuantity, String sysUserId);
}
