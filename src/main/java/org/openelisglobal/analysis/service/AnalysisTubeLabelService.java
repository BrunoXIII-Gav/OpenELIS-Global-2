package org.openelisglobal.analysis.service;

import java.util.List;
import java.util.Map;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.analysis.valueholder.AnalysisTubeLabel;
import org.openelisglobal.common.service.BaseObjectService;

public interface AnalysisTubeLabelService extends BaseObjectService<AnalysisTubeLabel, Long> {

    List<AnalysisTubeLabel> getByAnalysisId(String analysisId);

    Map<String, AnalysisTubeLabel> getByAnalysisIdGroupedByBlock(String analysisId);

    AnalysisTubeLabel saveOrUpdateLabel(Analysis analysis, String blockName, String submittedLabelCode, String sysUserId);
}
