package org.openelisglobal.result.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.sampleitem.valueholder.SampleDataCompletionStatus;
import org.openelisglobal.systemuser.service.UserService;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges the new Results module permissions with existing laboratory-unit
 * filtering while legacy roles are still supported.
 */
@Service
@Transactional(readOnly = true)
public class ResultAuthorizationServiceImpl implements ResultAuthorizationService {

    private static final String RESULTS_MODULE = "results";
    private static final List<String> RESULT_ENTRY_ACTIONS = List.of("enter", "update", "correct");

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @Autowired
    private UserService userService;

    @Autowired
    private TestService testService;

    @Autowired
    private AnalysisService analysisService;

    @Override
    public List<TestResultItem> filterResults(String userId, List<TestResultItem> results, String actionKey,
            String legacyRoleName) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        List<TestResultItem> completedSampleResults = results.stream().filter(this::hasCompletedSample).toList();
        if (completedSampleResults.isEmpty()) {
            return List.of();
        }

        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId, RESULTS_MODULE, actionKey);
        if (decision.source() != AuthorizationSource.MODULE_PERMISSION) {
            return userService.filterResultsByLabUnitRoles(userId, completedSampleResults, legacyRoleName);
        }
        if (!decision.allowed()) {
            return List.of();
        }
        if (decision.allLabUnits()) {
            return List.copyOf(completedSampleResults);
        }

        Set<String> permittedTestIds = getPermittedTestIds(decision.labUnitIds());
        return completedSampleResults.stream().filter(result -> StringUtils.isNotBlank(result.getTestId()))
                .filter(result -> permittedTestIds.contains(result.getTestId())).toList();
    }

    @Override
    public List<TestResultItem> filterResultsForEntryAccess(String userId, List<TestResultItem> results,
            String legacyRoleName) {
        if (moduleAuthorizationService.hasPermission(userId, RESULTS_MODULE, "read")) {
            return filterResults(userId, results, "read", legacyRoleName);
        }

        // Build every row before this point. Filtering only the final list keeps
        // additional-field metadata and parent-child source data intact.
        LinkedHashMap<String, TestResultItem> visibleResults = new LinkedHashMap<>();
        for (String actionKey : RESULT_ENTRY_ACTIONS) {
            if (!moduleAuthorizationService.hasPermission(userId, RESULTS_MODULE, actionKey)) {
                continue;
            }
            filterResults(userId, results, actionKey, legacyRoleName).stream()
                    .filter(result -> actionKey.equals(result.getResultEntryAction()))
                    .filter(result -> StringUtils.isNotBlank(result.getAnalysisId()))
                    .forEach(result -> visibleResults.putIfAbsent(result.getAnalysisId(), result));
        }
        return List.copyOf(visibleResults.values());
    }

    @Override
    public boolean canAccessAllResults(String userId, List<TestResultItem> results, String actionKey,
            String legacyRoleName) {
        if (results == null || results.isEmpty()) {
            return true;
        }
        if (!results.stream().allMatch(this::hasCompletedSample)) {
            return false;
        }

        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId, RESULTS_MODULE, actionKey);
        if (decision.source() != AuthorizationSource.MODULE_PERMISSION) {
            return filterResults(userId, results, actionKey, legacyRoleName).size() == results.size();
        }
        if (!decision.allowed()) {
            return false;
        }
        if (decision.allLabUnits()) {
            return true;
        }

        Set<String> permittedTestIds = getPermittedTestIds(decision.labUnitIds());
        return results.stream().filter(result -> StringUtils.isNotBlank(result.getAnalysisId()))
                .allMatch(result -> isPermittedAnalysis(result.getAnalysisId(), permittedTestIds));
    }

    @Override
    public List<String> filterAccessibleAnalysisIds(String userId, List<String> analysisIds, String actionKey,
            String legacyRoleName) {
        if (analysisIds == null || analysisIds.isEmpty()) {
            return List.of();
        }

        List<TestResultItem> analysisItems = analysisIds.stream().filter(StringUtils::isNotBlank).map(analysisId -> {
            TestResultItem item = new TestResultItem();
            item.setAnalysisId(analysisId);
            Analysis analysis = analysisService.get(analysisId);
            if (analysis != null && analysis.getTest() != null) {
                item.setTestId(analysis.getTest().getId());
            }
            return item;
        }).toList();
        return filterResults(userId, analysisItems, actionKey, legacyRoleName).stream().map(TestResultItem::getAnalysisId)
                .toList();
    }

    private boolean isPermittedAnalysis(String analysisId, Set<String> permittedTestIds) {
        Analysis analysis = analysisService.get(analysisId);
        return analysis != null && analysis.getTest() != null && permittedTestIds.contains(analysis.getTest().getId());
    }

    private boolean hasCompletedSample(TestResultItem result) {
        if (StringUtils.isBlank(result.getAnalysisId())) {
            return true;
        }
        Analysis analysis = analysisService.get(result.getAnalysisId());
        return analysis != null && analysis.getSampleItem() != null
                && analysis.getSampleItem().getDataCompletionState() == SampleDataCompletionStatus.COMPLETED;
    }

    private Set<String> getPermittedTestIds(Collection<String> labUnitIds) {
        List<Integer> testSectionIds = labUnitIds.stream().filter(StringUtils::isNumeric).map(Integer::valueOf).toList();
        if (testSectionIds.isEmpty()) {
            return Set.of();
        }
        return testService.getTestsByTestSectionIds(testSectionIds).stream().map(Test::getId).filter(StringUtils::isNotBlank)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
