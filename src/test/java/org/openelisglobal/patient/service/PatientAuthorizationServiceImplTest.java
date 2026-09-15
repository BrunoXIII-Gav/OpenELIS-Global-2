package org.openelisglobal.patient.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.analysis.service.AnalysisService;

@RunWith(MockitoJUnitRunner.class)
public class PatientAuthorizationServiceImplTest {

    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;

    @Mock
    private AnalysisService analysisService;

    @InjectMocks
    private PatientAuthorizationServiceImpl patientAuthorizationService;

    @Test
    public void hasPermission_allowsAnExplicitAllLaboratoryUnitsPermission() {
        when(moduleAuthorizationService.getAuthorization("17", "patients", "read"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));

        assertTrue(patientAuthorizationService.hasPermission("17", "read"));
    }

    @Test
    public void hasPermission_allowsAnExplicitLimitedScopeForRecordLevelEvaluation() {
        when(moduleAuthorizationService.getAuthorization("17", "patients", "read"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));

        assertTrue(patientAuthorizationService.hasPermission("17", "read"));
    }

    @Test
    public void canAccessPatient_allowsARestrictedScopeWhenPatientHasAnAnalysisInAssignedUnit() {
        when(moduleAuthorizationService.getAuthorization("17", "patients", "read"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));
        when(analysisService.hasAnalysisForPatientInTestSections("42", java.util.List.of(7))).thenReturn(true);

        assertTrue(patientAuthorizationService.canAccessPatient("17", "42", "read"));
    }

    @Test
    public void canAccessPatient_rejectsARestrictedScopeWhenPatientHasNoAnalysisInAssignedUnit() {
        when(moduleAuthorizationService.getAuthorization("17", "patients", "read"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));
        when(analysisService.hasAnalysisForPatientInTestSections("42", java.util.List.of(7))).thenReturn(false);

        assertFalse(patientAuthorizationService.canAccessPatient("17", "42", "read"));
    }

    @Test
    public void hasPermission_rejectsRestrictedMergePermission() {
        when(moduleAuthorizationService.getAuthorization("17", "patients", "merge"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));

        assertFalse(patientAuthorizationService.hasPermission("17", "merge"));
    }

    @Test
    public void hasPermission_keepsLegacyPermissionsUsable() {
        when(moduleAuthorizationService.getAuthorization("17", "patients", "merge"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.LEGACY_PERMISSION, true, Set.of()));

        assertTrue(patientAuthorizationService.hasPermission("17", "merge"));
    }
}
