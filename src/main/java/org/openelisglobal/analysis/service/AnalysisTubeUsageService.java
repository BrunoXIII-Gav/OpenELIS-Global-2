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

    AnalysisTubeUsage createUsage(Analysis analysis, Analysis parentAnalysis, String childBlockName,
            String parentTubeBlockName, BigDecimal usedQuantity, String sysUserId);
}
