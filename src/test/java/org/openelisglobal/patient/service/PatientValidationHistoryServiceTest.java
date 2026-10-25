package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
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
    private AnalysisService analysisService;
    private AuditEventDAO audit;

    @Before
    public void setup() {
        service = new PatientValidationHistoryServiceImpl();
        dao = mock(PatientValidationHistoryDAO.class);
        patients = mock(PatientAuthorizationService.class);
        authorization = mock(ModuleAuthorizationService.class);
        var status = mock(IStatusService.class);
        analysisService = mock(AnalysisService.class);
        ReflectionTestUtils.setField(service, "analysisService", analysisService);
        audit = mock(AuditEventDAO.class);
        ReflectionTestUtils.setField(service, "auditEventDAO", audit);
        ReflectionTestUtils.setField(service, "dao", dao);
        ReflectionTestUtils.setField(service, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(service, "moduleAuthorizationService", authorization);
        ReflectionTestUtils.setField(service, "statusService", status);
        when(patients.canAccessPatient("7", "10", "read")).thenReturn(true);
        when(authorization.getRestrictedFieldGroupKeys("7", "patients")).thenReturn(Set.of());
        when(authorization.getRestrictedFieldTagKeys("7", "patients")).thenReturn(Set.of());
        when(authorization.getAuthorization("7", "patients", "read"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        when(authorization.getAuthorization("7", "validation", "deliver"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        when(authorization.getAuthorization("7", "results", "correct"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        when(authorization.getAuthorization("7", "administration", "manage"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        when(dao.findAmendments(anyList())).thenReturn(List.of());
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

    @Test
    public void exposesDeliveryDateAndWhetherTheUserMayRegisterIt() {
        var delivered = analysis("78", "56");
        when(delivered.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-20"));
        when(dao.findValidatedAnalyses("10", "6", null)).thenReturn(List.of(delivered));
        var row = service.getRecords("10", "7").get(0);
        assertEquals("2026-10-20", row.deliveredDate());
        assertTrue(row.canRegisterDelivery());

        when(authorization.getAuthorization("7", "validation", "deliver")).thenReturn(
                new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("57")));
        assertFalse(service.getRecords("10", "7").get(0).canRegisterDelivery());
    }

    @Test
    public void hiddenDeliveryDateIsRemovedAndCannotBeChanged() {
        var delivered = analysis("78", "56");
        when(delivered.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-20"));
        when(dao.findValidatedAnalyses("10", "6", null)).thenReturn(List.of(delivered));
        when(authorization.getRestrictedFieldTagKeys("7", "patients"))
                .thenReturn(Set.of("patient-validation-delivery-date"));
        var row = service.getRecords("10", "7").get(0);
        assertNull(row.deliveredDate());
        assertFalse(row.canRegisterDelivery());
        assertThrows(AccessDeniedException.class, () -> service.registerDelivery("10", "78", "2026-10-20", null, "7"));
    }

    @Test
    public void firstDeliveryRecordsDateAndAuthorWithoutReason() {
        var validated = deliverable("78");
        service.registerDelivery("10", "78", "2026-10-16", null, "7");
        verify(validated).setDeliveredDate(Date.valueOf("2026-10-16"));
        verify(validated).setDeliveredBy("7");
        verify(validated).setDeliveryCorrectionReason(null);
        verify(validated).setSysUserId("7");
        verify(analysisService).update(validated);
    }

    @Test
    public void correctingADeliveryRequiresAReason() {
        var validated = deliverable("78");
        when(validated.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-16"));
        var error = assertThrows(IllegalArgumentException.class,
                () -> service.registerDelivery("10", "78", "2026-10-17", "  ", "7"));
        assertEquals("patient.validation.delivery.error.reasonRequired", error.getMessage());
        verify(analysisService, never()).update(any(Analysis.class));

        service.registerDelivery("10", "78", "2026-10-17", " Fecha mal digitada ", "7");
        verify(validated).setDeliveredDate(Date.valueOf("2026-10-17"));
        verify(validated).setDeliveryCorrectionReason("Fecha mal digitada");
        verify(analysisService).update(validated);
    }

    @Test
    public void unchangedDeliveryDateIsNotRewritten() {
        var validated = deliverable("78");
        when(validated.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-16"));
        service.registerDelivery("10", "78", "2026-10-16", null, "7");
        verify(analysisService, never()).update(any(Analysis.class));
    }

    @Test
    public void rejectsMissingFutureOrPreValidationDates() {
        deliverable("78");
        for (String date : new String[] { null, "", "16/10/2026", LocalDate.now().plusDays(1).toString(),
                "2026-10-14" }) {
            assertThrows(IllegalArgumentException.class, () -> service.registerDelivery("10", "78", date, null, "7"));
        }
        verify(analysisService, never()).update(any(Analysis.class));
    }

    @Test
    public void deliveryRequiresTheDeliverPermissionForTheAnalysisUnit() {
        deliverable("78");
        when(authorization.getAuthorization("7", "validation", "deliver"))
                .thenReturn(new AuthorizationDecision(false, AuthorizationSource.MODULE_PERMISSION, false, Set.of()));
        assertThrows(AccessDeniedException.class, () -> service.registerDelivery("10", "78", "2026-10-16", null, "7"));
        when(authorization.getAuthorization("7", "validation", "deliver")).thenReturn(
                new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("57")));
        assertThrows(AccessDeniedException.class, () -> service.registerDelivery("10", "78", "2026-10-16", null, "7"));
        verify(analysisService, never()).update(any(Analysis.class));
    }

    @Test
    public void deliveredResultsOfferAmendmentAndReopeningButNotAfterBeingAmended() {
        var delivered = analysis("78", "56");
        when(delivered.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-16"));
        var amendment = analysis("80", "56");
        when(amendment.getAmendsAnalysisId()).thenReturn("78");
        when(dao.findValidatedAnalyses("10", "6", null)).thenReturn(List.of(amendment, delivered));
        var rows = service.getRecords("10", "7");
        assertEquals("78", rows.get(0).amendsAnalysisId());
        assertFalse(rows.get(0).canAmend());
        assertTrue(rows.get(1).canAmend());
        assertTrue(rows.get(1).canReopenDelivery());

        when(dao.findAmendments(anyList())).thenReturn(java.util.Collections.singletonList(new Object[] { "78", "80" }));
        var amended = service.getRecords("10", "7").get(1);
        assertEquals("80", amended.amendedByAnalysisId());
        assertFalse(amended.canAmend());
        assertFalse(amended.canReopenDelivery());
    }

    @Test
    public void amendmentPermissionsFollowResultCorrectionAndAdministration() {
        var delivered = analysis("78", "56");
        when(delivered.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-16"));
        when(authorization.getAuthorization("7", "results", "correct")).thenReturn(
                new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("57")));
        when(authorization.getAuthorization("7", "administration", "manage"))
                .thenReturn(new AuthorizationDecision(false, AuthorizationSource.MODULE_PERMISSION, false, Set.of()));
        when(dao.findValidatedAnalyses("10", "6", null)).thenReturn(List.of(delivered));
        var row = service.getRecords("10", "7").get(0);
        assertFalse(row.canAmend());
        assertFalse(row.canReopenDelivery());
    }

    @Test
    public void amendingCreatesALinkedAnalysisForTheSameTestAndSample() {
        var original = deliverable("78");
        when(original.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-16"));
        var test = original.getTest();
        var item = original.getSampleItem();
        var created = new Analysis();
        when(analysisService.buildAnalysis(test, item)).thenReturn(created);
        when(analysisService.insert(created)).thenReturn("81");

        assertEquals("81", service.createAmendment("10", "78", " Valor mal transcrito ", "7"));
        assertEquals("78", created.getAmendsAnalysisId());
        assertEquals("Valor mal transcrito", created.getAmendmentReason());
        assertEquals("7", created.getSysUserId());
        verify(audit).insert(argThat(event -> "78".equals(event.getEntityId())
                && "amendedBy".equals(event.getField()) && "81".equals(event.getNewValue())
                && "78".equals(event.getAnalysisId())));
    }

    @Test
    public void amendmentRequiresDeliveryReasonPermissionAndNoPreviousAmendment() {
        var original = deliverable("78");
        assertEquals("patient.validation.amendment.error.notDelivered", assertThrows(IllegalArgumentException.class,
                () -> service.createAmendment("10", "78", "x", "7")).getMessage());
        when(original.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-16"));
        assertEquals("patient.validation.amendment.error.reasonRequired", assertThrows(
                IllegalArgumentException.class, () -> service.createAmendment("10", "78", " ", "7")).getMessage());
        when(dao.findAmendments(List.of("78"))).thenReturn(java.util.Collections.singletonList(new Object[] { "78", "80" }));
        assertEquals("patient.validation.amendment.error.alreadyAmended", assertThrows(
                IllegalArgumentException.class, () -> service.createAmendment("10", "78", "x", "7")).getMessage());
        when(dao.findAmendments(List.of("78"))).thenReturn(List.of());
        when(authorization.getAuthorization("7", "results", "correct"))
                .thenReturn(new AuthorizationDecision(false, AuthorizationSource.MODULE_PERMISSION, false, Set.of()));
        assertThrows(AccessDeniedException.class, () -> service.createAmendment("10", "78", "x", "7"));
        verify(analysisService, never()).insert(any(Analysis.class));
    }

    @Test
    public void administratorReopensADeliveryWithAReason() {
        var original = deliverable("78");
        when(original.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-16"));
        assertEquals("patient.validation.delivery.error.reasonRequired", assertThrows(
                IllegalArgumentException.class, () -> service.reopenDelivery("10", "78", "", "7")).getMessage());
        service.reopenDelivery("10", "78", " Entregado al paciente equivocado ", "7");
        verify(original).setDeliveredDate(null);
        verify(original).setDeliveredBy(null);
        verify(original).setDeliveryCorrectionReason("Entregado al paciente equivocado");
        verify(analysisService).update(original);
    }

    @Test
    public void reopeningIsForAdministratorsAndNotForAmendedOrUndeliveredResults() {
        var original = deliverable("78");
        assertEquals("patient.validation.amendment.error.notDelivered", assertThrows(IllegalArgumentException.class,
                () -> service.reopenDelivery("10", "78", "x", "7")).getMessage());
        when(original.getDeliveredDate()).thenReturn(Date.valueOf("2026-10-16"));
        when(dao.findAmendments(List.of("78"))).thenReturn(java.util.Collections.singletonList(new Object[] { "78", "80" }));
        assertEquals("patient.validation.amendment.error.alreadyAmended", assertThrows(
                IllegalArgumentException.class, () -> service.reopenDelivery("10", "78", "x", "7")).getMessage());
        when(dao.findAmendments(List.of("78"))).thenReturn(List.of());
        when(authorization.getAuthorization("7", "administration", "manage"))
                .thenReturn(new AuthorizationDecision(false, AuthorizationSource.MODULE_PERMISSION, false, Set.of()));
        assertThrows(AccessDeniedException.class, () -> service.reopenDelivery("10", "78", "x", "7"));
        verify(analysisService, never()).update(any(Analysis.class));
    }

    private Analysis deliverable(String id) {
        var validated = analysis(id, "56");
        when(dao.findValidatedAnalyses("10", "6", id)).thenReturn(List.of(validated));
        return validated;
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
