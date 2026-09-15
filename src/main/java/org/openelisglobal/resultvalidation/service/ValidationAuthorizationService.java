package org.openelisglobal.resultvalidation.service;

import java.util.List;
import org.openelisglobal.resultvalidation.bean.AnalysisItem;

/** Applies Validation module permissions and laboratory-unit scope to analyses. */
public interface ValidationAuthorizationService {

    boolean hasPermission(String userId, String actionKey);

    List<AnalysisItem> filterResults(String userId, List<AnalysisItem> results, String actionKey,
            List<String> legacyRoleNames);

    boolean canAccessAllResults(String userId, List<AnalysisItem> results, String actionKey,
            List<String> legacyRoleNames);
}
