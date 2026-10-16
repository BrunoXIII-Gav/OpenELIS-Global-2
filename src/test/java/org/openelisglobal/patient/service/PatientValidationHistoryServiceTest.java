package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.patient.dao.PatientValidationHistoryDAO;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.test.valueholder.TestSection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

public class PatientValidationHistoryServiceTest {
    private PatientValidationHistoryServiceImpl service;
    private PatientValidationHistoryDAO dao;
    private PatientAuthorizationService patients;
    private ModuleAuthorizationService authorization;

    @Before
    public void setup() {
        service = new PatientValidationHistoryServiceImpl();
        dao = mock(PatientValidationHistoryDAO.class);
        patients = mock(PatientAuthorizationService.class);
        authorization = mock(ModuleAuthorizationService.class);
        var status = mock(IStatusService.class);
        ReflectionTestUtils.setField(service, "dao", dao);
        ReflectionTestUtils.setField(service, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(service, "moduleAuthorizationService", authorization);
        ReflectionTestUtils.setField(service, "statusService", status);
        when(patients.canAccessPatient("7", "10", "read")).thenReturn(true);
        when(authorization.getRestrictedFieldGroupKeys("7", "patients")).thenReturn(Set.of());
        when(authorization.getRestrictedFieldTagKeys("7", "patients")).thenReturn(Set.of());
        when(authorization.getAuthorization("7", "patients", "read"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        when(status.getStatusID(AnalysisStatus.Finalized)).thenReturn("6");
        var validated = analysis("78", "56");
        when(dao.findValidatedAnalyses("10", "6", null)).thenReturn(List.of(validated));
    }

    @Test public void listsCurrentValidatorsAndClinicalDatePerTest() {
        when(dao.findActiveApprovers(List.of("78"))).thenReturn(List.of(
                new Object[] {"78", "Carla", "Enciso", "carla", Timestamp.valueOf("2026-10-15 12:00:00")},
                new Object[] {"78", "Mario", "Cornejo", "mario", Timestamp.valueOf("2026-10-15 12:01:00")}));
        var row = service.getRecords("10", "7").get(0);
        assertEquals("78", row.analysisId());
        assertEquals("2026-10-15", row.validationDate());
        assertEquals(List.of("Carla Enciso", "Mario Cornejo"), row.validators().stream().map(v -> v.name()).toList());
        assertTrue(row.reportAvailable());
        verify(dao).findValidatedAnalyses("10", "6", null);
    }

    @Test
    public void oldValidatedTestsDoNotInventAnAuthor() {
        assertTrue(service.getRecords("10", "7").get(0).validators().isEmpty());
    }

    @Test
    public void automaticallyFinalizedTestsAreExcludedBeforeLoadingValidationAuthors() {
        var manuallyValidated = analysis("78", "56");
        var automaticallyFinalized = analysis("79", "56");
        when(automaticallyFinalized.getTest().getSkipValidationWhenParentComplete()).thenReturn(true);
        // Both have the finalized status and a date; the configured workflow
        // determines whether they belong in the validation history.
        when(dao.findValidatedAnalyses("10", "6", null)).thenReturn(List.of(manuallyValidated, automaticallyFinalized));
        assertEquals(List.of("78"), service.getRecords("10", "7").stream().map(r -> r.analysisId()).toList());
        verify(dao).findActiveApprovers(List.of("78"));
    }

    @Test
    public void directValidationPdfIsUnavailableForAutomaticallyFinalizedTests() {
        var automaticallyFinalized = analysis("79", "56");
        when(automaticallyFinalized.getTest().getSkipValidationWhenParentComplete()).thenReturn(true);
        when(dao.findValidatedAnalyses("10", "6", "79")).thenReturn(List.of(automaticallyFinalized));
        var error = assertThrows(ResponseStatusException.class, () -> service.requireReportAccess("10", "79", "7"));
        assertEquals(org.springframework.http.HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    public void validationEnabledAndLegacyNullConfigurationsRemainVisibleAndAllowPdf() {
        var validated = analysis("78", "56");
        when(dao.findValidatedAnalyses("10", "6", null)).thenReturn(List.of(validated));
        when(dao.findValidatedAnalyses("10", "6", "78")).thenReturn(List.of(validated));
        for (Boolean skipValidation : new Boolean[] { false, null }) {
            when(validated.getTest().getSkipValidationWhenParentComplete()).thenReturn(skipValidation);
            assertEquals(List.of("78"), service.getRecords("10", "7").stream().map(r -> r.analysisId()).toList());
            service.requireReportAccess("10", "78", "7");
        }
    }

    @Test
    public void restrictedGroupOrTabReturnsNoValidationDataAndDeniesPdf() {
        for (String group : List.of("patient-validations", "patient-history")) {
            when(authorization.getRestrictedFieldGroupKeys("7", "patients")).thenReturn(Set.of(group));
            assertTrue(service.getRecords("10", "7").isEmpty());
            assertThrows(AccessDeniedException.class, () -> service.requireReportAccess("10", "78", "7"));
        }
        when(authorization.getRestrictedFieldGroupKeys("7", "patients")).thenReturn(Set.of());
        when(authorization.getRestrictedFieldTagKeys("7", "patients"))
                .thenReturn(Set.of("patient-history-validations"));
        assertTrue(service.getRecords("10", "7").isEmpty());
        verify(dao, never()).findValidatedAnalyses(anyString(), anyString(), any());
    }

    @Test public void hiddenAuthorIsRemovedFromPayloadAndCannotBeRecoveredThroughPdf() {
        when(authorization.getRestrictedFieldTagKeys("7", "patients")).thenReturn(Set.of("patient-validation-validator"));
        var row = service.getRecords("10", "7").get(0);
        assertTrue(row.validators().isEmpty());
        assertFalse(row.reportAvailable());
        verify(dao, never()).findActiveApprovers(anyList());
        assertThrows(AccessDeniedException.class, () -> service.requireReportAccess("10", "78", "7"));
    }

    @Test public void hiddenDateIsRemovedAndPdfRestrictionsAreEnforced() {
        when(authorization.getRestrictedFieldTagKeys("7", "patients")).thenReturn(Set.of("patient-validation-date"));
        when(dao.findActiveApprovers(List.of("78"))).thenReturn(java.util.Collections.singletonList(
                new Object[] {"78", "Carla", "Enciso", "carla", Timestamp.valueOf("2026-10-15 12:00:00")}));
        var hiddenDate = service.getRecords("10", "7").get(0);
        assertNull(hiddenDate.validationDate());
        assertNull(hiddenDate.validators().get(0).approvedAt());
        for (String tag : List.of("patient-validation-date", "patient-validation-report", "patient-history-result-values")) {
            when(authorization.getRestrictedFieldTagKeys("7", "patients")).thenReturn(Set.of(tag));
            assertFalse(service.getRecords("10", "7").get(0).reportAvailable());
            assertThrows(AccessDeniedException.class, () -> service.requireReportAccess("10", "78", "7"));
        }
    }

    @Test public void unitScopeAppliesToEveryAnalysisAndDirectPdf() {
        when(authorization.getAuthorization("7", "patients", "read")).thenReturn(
                new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("56")));
        var ownUnit = analysis("78", "56");
        var otherUnit = analysis("79", "57");
        when(dao.findValidatedAnalyses("10", "6", null)).thenReturn(List.of(ownUnit, otherUnit));
        assertEquals(List.of("78"), service.getRecords("10", "7").stream().map(r -> r.analysisId()).toList());
        when(dao.findValidatedAnalyses("10", "6", "79")).thenReturn(List.of(otherUnit));
        assertThrows(AccessDeniedException.class, () -> service.requireReportAccess("10", "79", "7"));
    }

    @Test public void rejectsOtherPatientsAndMissingOrUnvalidatedTests() {
        when(patients.canAccessPatient("7", "10", "read")).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.getRecords("10", "7"));
        assertThrows(AccessDeniedException.class, () -> service.requireReportAccess("10", "78", "7"));
        when(patients.canAccessPatient("7", "10", "read")).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> service.requireReportAccess("10", "999", "7"));
        verify(dao).findValidatedAnalyses("10", "6", "999");
        var validated = analysis("78", "56");
        when(dao.findValidatedAnalyses("10", "6", "78")).thenReturn(List.of(validated));
        service.requireReportAccess("10", "78", "7");
    }

    private Analysis analysis(String id, String sectionId) {
        Analysis a = mock(Analysis.class);
        SampleItem item = mock(SampleItem.class);
        Sample sample = mock(Sample.class);
        var test = mock(org.openelisglobal.test.valueholder.Test.class);
        TestSection section = mock(TestSection.class);
        when(a.getId()).thenReturn(id);
        when(a.getSampleItem()).thenReturn(item);
        when(item.getSample()).thenReturn(sample);
        when(sample.getAccessionNumber()).thenReturn("LAB-1");
        when(item.getCugCode()).thenReturn("13.1");
        when(a.getTest()).thenReturn(test);
        when(test.getLocalizedName()).thenReturn("Document test");
        when(a.getValidationDate()).thenReturn(Date.valueOf("2026-10-15"));
        when(a.getTestSection()).thenReturn(section);
        when(section.getId()).thenReturn(sectionId);
        return a;
    }
}
