package org.openelisglobal.result.service;

import java.util.List;
import org.openelisglobal.test.beanItems.TestResultItem;

/**
 * Applies module permissions and laboratory-unit scope to result-entry items.
 */
public interface ResultAuthorizationService {

    List<TestResultItem> filterResults(String userId, List<TestResultItem> results, String actionKey,
            String legacyRoleName);

    /**
     * Returns all readable rows, or only rows matched by the user's entry actions
     * when the user does not have general read access.
     */
    List<TestResultItem> filterResultsForEntryAccess(String userId, List<TestResultItem> results,
            String legacyRoleName);

    boolean canAccessAllResults(String userId, List<TestResultItem> results, String actionKey,
            String legacyRoleName);

    List<String> filterAccessibleAnalysisIds(String userId, List<String> analysisIds, String actionKey,
            String legacyRoleName);
}
