package org.openelisglobal.patient.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.RolePatientSearchRestrictionService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.authorization.valueholder.RolePatientSearchRestriction;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.security.service.UserPermissionService;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves access to patient records through their analyses. A restricted unit
 * scope grants access only when the patient has a test in an assigned section.
 */
@Service
@Transactional(readOnly = true)
public class PatientAuthorizationServiceImpl implements PatientAuthorizationService {

    private static final String PATIENTS_MODULE = "patients";

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private RolePatientSearchRestrictionService rolePatientSearchRestrictionService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private UserPermissionService userPermissionService;

    @Override
    public boolean hasPermission(String userId, String actionKey) {
        AuthorizationDecision decision = getDecision(userId, actionKey);
        return decision.allowed() && (!"merge".equals(actionKey) || decision.source() != AuthorizationSource.MODULE_PERMISSION
                || decision.allLabUnits());
    }

    @Override
    public boolean canAccessPatient(String userId, String patientId, String actionKey) {
        AuthorizationDecision decision = getDecision(userId, actionKey);
        if (!decision.allowed()) {
            return false;
        }
        if (decision.source() != AuthorizationSource.MODULE_PERMISSION || decision.allLabUnits()) {
            return true;
        }
        if ("merge".equals(actionKey)) {
            return false;
        }

        List<Integer> testSectionIds = decision.labUnitIds().stream().filter(this::isNumeric).map(Integer::valueOf)
                .toList();
        return isNumeric(patientId) && !testSectionIds.isEmpty()
                && analysisService.hasAnalysisForPatientInTestSections(patientId, testSectionIds);
    }

    @Override
    public Set<String> getRestrictedSearchCriteria(String userId) {
        if (StringUtils.isBlank(userId) || userPermissionService.hasPermission(userId, SystemPermission.GLOBAL_ADMIN)) {
            return Set.of();
        }
        List<Integer> roleIds = userRoleService.getRoleIdsForUser(userId).stream().filter(StringUtils::isNumeric)
                .map(Integer::valueOf).toList();
        return rolePatientSearchRestrictionService.getByRoleIds(roleIds).stream()
                .map(RolePatientSearchRestriction::getCriterionKey).filter(StringUtils::isNotBlank)
                .collect(Collectors.toUnmodifiableSet());
    }

    private AuthorizationDecision getDecision(String userId, String actionKey) {
        return moduleAuthorizationService.getAuthorization(userId, PATIENTS_MODULE, actionKey);
    }

    private boolean isNumeric(String value) {
        return value != null && !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }
}
