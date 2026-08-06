package org.openelisglobal.patient.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.StatusService.OrderStatus;
import org.openelisglobal.common.services.StatusSet;
import org.openelisglobal.observationhistory.service.ObservationHistoryService;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.patient.form.PatientHistorySummary;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.OrderPriority;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.storage.service.SampleStorageService;
import org.openelisglobal.test.valueholder.Test;

@RunWith(MockitoJUnitRunner.class)
public class PatientHistorySummaryServiceTest {

    @InjectMocks
    private PatientHistorySummaryServiceImpl service;

    @Mock
    private PatientService patientService;

    @Mock
    private SampleHumanService sampleHumanService;

    @Mock
    private SampleService sampleService;

    @Mock
    private SampleItemDAO sampleItemDAO;

    @Mock
    private SampleStorageService sampleStorageService;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private ObservationHistoryService observationHistoryService;

    @Mock
    private IStatusService statusService;

    @Mock
    private PersonService personService;

    @Test
    public void getSummary_compilesOrdersSamplesStorageAndMetrics() {
        Patient patient = new Patient();
        patient.setId("77");

        Sample sample = new Sample();
        sample.setId("501");
        sample.setAccessionNumber("LAB-501");
        sample.setClinicalOrderId("ORD-9");
        sample.setClientReference("REF-33");
        sample.setPriority(OrderPriority.STAT);
        sample.setReceivedTimestamp(Timestamp.valueOf("2026-07-10 10:00:00"));
        sample.setReceivedDateForDisplay("07/10/2026");
        sample.setCollectionDate(Timestamp.valueOf("2026-07-09 08:30:00"));

        SampleItem sampleItem = new SampleItem();
        sampleItem.setId("9001");
        sampleItem.setExternalId("LAB-501-1");
        sampleItem.setCugCode("123.4");
        sampleItem.setCollectionDate(Timestamp.valueOf("2026-07-09 08:30:00"));
        sampleItem.setStatusId("sample-entered");

        Person requester = new Person();
        requester.setId("88");
        requester.setFirstName("Ana");
        requester.setLastName("Garcia");

        Organization organization = new Organization();
        organization.setId("15");
        organization.setOrganizationName("Central Clinic");

        Analysis finalized = new Analysis();
        finalized.setId("1");
        finalized.setStatusId("analysis-finalized");
        Test finalizedTest = new Test();
        finalizedTest.setDescription("Hemoglobin");
        finalized.setTest(finalizedTest);

        Analysis inProgress = new Analysis();
        inProgress.setId("2");
        inProgress.setStatusId("analysis-in-progress");
        Test inProgressTest = new Test();
        inProgressTest.setDescription("Platelets");
        inProgress.setTest(inProgressTest);

        StatusSet statusSet = new StatusSet();
        statusSet.setSampleStatus(OrderStatus.Finished);

        when(patientService.get("77")).thenReturn(patient);
        when(sampleHumanService.getSamplesForPatient("77")).thenReturn(List.of(sample));
        when(observationHistoryService.getValueForSample(any(), eq("501"))).thenReturn("07/09/2026");
        when(statusService.getStatusSetForSampleId("501")).thenReturn(statusSet);
        when(statusService.getStatusName(OrderStatus.Finished)).thenReturn("Finished");
        when(sampleService.getOrganizationRequester(eq(sample), anyString())).thenReturn(organization);
        when(sampleService.getPersonRequester(sample)).thenReturn(requester);
        when(personService.getLastFirstName(requester)).thenReturn("Garcia, Ana");
        when(sampleItemDAO.getSampleItemsBySampleId("501")).thenReturn(List.of(sampleItem));
        when(sampleItemDAO.getSampleItemsWithHierarchy(List.of("9001"))).thenReturn(List.of(sampleItem));
        when(sampleStorageService.getSampleItemLocation("9001")).thenReturn(Map.of(
                "hierarchicalPath", "Room A > Rack 1 > Box 2",
                "assignedDate", "2026-07-11 09:00:00",
                "positionCoordinate", "A2",
                "notes", "Frozen"));
        when(statusService.getStatusNameFromId("sample-entered")).thenReturn("Entered");
        when(analysisService.getAnalysesBySampleItem(sampleItem)).thenReturn(List.of(finalized, inProgress));
        when(statusService.matches("analysis-finalized", AnalysisStatus.Finalized)).thenReturn(true);
        when(statusService.matches("analysis-in-progress", AnalysisStatus.Finalized)).thenReturn(false);
        when(statusService.matches("analysis-finalized", AnalysisStatus.Canceled)).thenReturn(false);
        when(statusService.matches("analysis-in-progress", AnalysisStatus.Canceled)).thenReturn(false);
        when(statusService.matches("analysis-finalized", AnalysisStatus.SampleRejected)).thenReturn(false);
        when(statusService.matches("analysis-in-progress", AnalysisStatus.SampleRejected)).thenReturn(false);
        when(statusService.matches("analysis-finalized", AnalysisStatus.TechnicalRejected)).thenReturn(false);
        when(statusService.matches("analysis-in-progress", AnalysisStatus.TechnicalRejected)).thenReturn(false);
        when(statusService.matches("analysis-finalized", AnalysisStatus.BiologistRejected)).thenReturn(false);
        when(statusService.matches("analysis-in-progress", AnalysisStatus.BiologistRejected)).thenReturn(false);

        PatientHistorySummary summary = service.getSummary("77");

        assertEquals(1, summary.getOrders().size());
        assertEquals(1, summary.getSamples().size());
        assertEquals("LAB-501", summary.getOrders().get(0).getAccessionNumber());
        assertEquals("Central Clinic", summary.getOrders().get(0).getReferringSiteName());
        assertEquals("Garcia, Ana", summary.getOrders().get(0).getRequesterName());
        assertEquals("Room A > Rack 1 > Box 2", summary.getSamples().get(0).getStorageLocation());
        assertTrue(summary.getSamples().get(0).isStored());
        assertEquals(2, summary.getResults().size());
        assertEquals(1, summary.getMetrics().getTotalOrders());
        assertEquals(1, summary.getMetrics().getTotalSamples());
        assertEquals(1, summary.getMetrics().getStoredSamples());
        assertEquals(2, summary.getMetrics().getTotalTests());
        assertEquals(1, summary.getMetrics().getCompletedTests());
        assertEquals(1, summary.getMetrics().getPendingTests());
    }

    @Test
    public void getSummary_returnsEmptySummaryWhenPatientDoesNotExist() {
        when(patientService.get("404")).thenReturn(null);

        PatientHistorySummary summary = service.getSummary("404");

        assertEquals("404", summary.getPatientId());
        assertTrue(summary.getOrders().isEmpty());
        assertTrue(summary.getSamples().isEmpty());
        assertEquals(0, summary.getMetrics().getTotalOrders());
        assertFalse(summary.getMetrics().getStoredSamples() > 0);
    }
}
