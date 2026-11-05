package org.openelisglobal.sample.service;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.dataexchange.orderresult.OrderResponseWorker.Event;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.sample.exception.OrderTestHasResultsException;
import org.openelisglobal.testadditionalfield.dao.AnalysisAdditionalFieldValueDAO;
import org.openelisglobal.typeoftestresult.service.TypeOfTestResultServiceImpl.ResultType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderTestRemovalServiceImpl implements OrderTestRemovalService {
    @Autowired
    private ResultService resultService;
    @Autowired
    private AnalysisAdditionalFieldValueDAO additionalValues;

    @Override
    public boolean hasResults(Analysis analysis) {
        if (analysis.getResultFile() != null && analysis.getResultFile().getId() != null) {
            return true;
        }
        if (resultService.getResultsByAnalysis(analysis).stream().anyMatch(this::hasResultValue)) {
            return true;
        }
        // Include saved values from inactive fields as well as current definitions.
        return additionalValues.findByAnalysisId(Integer.valueOf(analysis.getId())).stream()
                .anyMatch(value -> StringUtils.isNotBlank(value.getFieldValue()));
    }

    private boolean hasResultValue(Result result) {
        String value = StringUtils.trimToEmpty(result.getValue());
        if (value.isEmpty() || (result.getResultEvent() == Event.TESTING_NOT_DONE && "cancel".equals(value))) {
            return false;
        }
        if (ResultType.DICTIONARY.matches(result.getResultType()) && "0".equals(value)) {
            return false;
        }
        return !ResultType.isMultiSelectVariant(result.getResultType()) || (!"{}".equals(value) && !"[]".equals(value));
    }

    @Override
    @Transactional
    public void validateRemoval(List<Analysis> analyses) {
        if (analyses.isEmpty()) {
            return;
        }
        // Result entry uses the same parent-row locks, including main-result saves.
        additionalValues.lockAnalyses(analyses.stream().map(Analysis::getId).distinct().sorted().toList());
        if (analyses.stream().anyMatch(this::hasResults)) {
            throw new OrderTestHasResultsException();
        }
    }
}
