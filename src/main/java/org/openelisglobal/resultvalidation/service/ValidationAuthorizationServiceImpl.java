package org.openelisglobal.resultvalidation.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.resultvalidation.bean.AnalysisItem;
import org.openelisglobal.systemuser.service.UserService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bridges explicit Validation permissions with the existing role-based scope. */
@Service
@Transactional(readOnly = true)
public class ValidationAuthorizationServiceImpl implements ValidationAuthorizationService {

    private static final String VALIDATION_MODULE = "validation";

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @Autowired
    private UserService userService;

    @Autowired
    private TestService testService;

    @Override
    public boolean hasPermission(String userId, String actionKey) {
        return moduleAuthorizationService.hasPermission(userId, VALIDATION_MODULE, actionKey);
    }

    @Override
    public List<AnalysisItem> filterResults(String userId, List<AnalysisItem> results, String actionKey,
            List<String> legacyRoleNames) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId, VALIDATION_MODULE,
                actionKey);
        if (decision.source() != AuthorizationSource.MODULE_PERMISSION) {
            return filterLegacyResults(userId, results, legacyRoleNames);
        }
        if (!decision.allowed()) {
            return List.of();
        }
        if (decision.allLabUnits()) {
            return List.copyOf(results);
        }

        Set<String> permittedTestIds = permittedTestIds(decision.labUnitIds());
        return results.stream().filter(result -> permittedTestIds.contains(result.getTestId())).toList();
    }

    @Override
    public boolean canAccessAllResults(String userId, List<AnalysisItem> results, String actionKey,
            List<String> legacyRoleNames) {
        if (results == null || results.isEmpty()) {
            return true;
        }
        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId, VALIDATION_MODULE,
                actionKey);
        if (!decision.allowed()) {
            return false;
        }
        if (decision.source() != AuthorizationSource.MODULE_PERMISSION) {
            return filterLegacyResults(userId, results, legacyRoleNames).size() == results.size();
        }
        if (decision.allLabUnits()) {
            return true;
        }
        Set<String> permittedTestIds = permittedTestIds(decision.labUnitIds());
        return results.stream().allMatch(result -> permittedTestIds.contains(result.getTestId()));
    }

    private List<AnalysisItem> filterLegacyResults(String userId, List<AnalysisItem> results,
            List<String> legacyRoleNames) {
        LinkedHashMap<String, AnalysisItem> permitted = new LinkedHashMap<>();
        for (String roleName : legacyRoleNames) {
            for (AnalysisItem result : userService.filterAnalysisResultsByLabUnitRoles(userId, results, roleName)) {
                if (result != null && StringUtils.isNotBlank(result.getAnalysisId())) {
                    permitted.putIfAbsent(result.getAnalysisId(), result);
                }
            }
        }
        return List.copyOf(permitted.values());
    }

    private Set<String> permittedTestIds(Collection<String> labUnitIds) {
        List<Integer> testSectionIds = labUnitIds.stream().filter(StringUtils::isNumeric).map(Integer::valueOf).toList();
        if (testSectionIds.isEmpty()) {
            return Set.of();
        }
        return testService.getTestsByTestSectionIds(testSectionIds).stream().map(Test::getId)
                .filter(StringUtils::isNotBlank).collect(Collectors.toUnmodifiableSet());
    }
}
