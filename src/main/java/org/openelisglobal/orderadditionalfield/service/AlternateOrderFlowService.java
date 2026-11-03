package org.openelisglobal.orderadditionalfield.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;

public interface AlternateOrderFlowService {

    boolean isAlternateOrderFlow(Map<String, String> orderAdditionalFieldValues);

    boolean isAlternateOrderFlow(String sampleId);

    boolean isAnalysisProcessingBlocked(String analysisId);

    boolean isResultsOnlyAnalysis(String analysisId);

    boolean isAnalysisValidationBlocked(String analysisId);

    Set<String> getProcessingBlockedAnalysisIds(List<Analysis> analyses);

    Set<String> getAlternateOrderFlowSampleIds(List<String> sampleIds);

    Optional<OrderAdditionalFieldPayload> getPrimaryTriggerField();
}
