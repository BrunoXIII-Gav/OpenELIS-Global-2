package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.observationhistory.service.ObservationHistoryService;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.sample.dao.SampleCugPrefixDAO;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PatientIdentifierSearchServiceTest {
    @InjectMocks
    private PatientIdentifierSearchServiceImpl service;
    @Mock
    private SampleCugPrefixDAO patientPrefixes;
    @Mock
    private PatientService patients;
    @Mock
    private PersonService persons;
    @Mock
    private ObservationHistoryService observations;
    @Mock
    private OrderAdditionalFieldService orderFields;
    @Mock
    private SampleService samples;
    @Mock
    private SampleHumanService sampleHumans;
    private Patient patient;
    private Sample sample;

    @Before
    public void setup() {
        patient = new Patient();
        patient.setId("21");
        patient.setPerson(new Person());
        when(patients.getData("21")).thenReturn(patient);
        when(patients.getFirstName(patient)).thenReturn("Test");
        when(patients.getLastName(patient)).thenReturn("Patient");
        sample = new Sample();
        sample.setId("61");
        when(sampleHumans.getPatientForSample(sample)).thenReturn(patient);
        when(orderFields.findSampleIdBySearchableFieldValue(anyString())).thenReturn(Optional.empty());
    }

    @Test
    public void prefixFindsPatientWithoutAnyOrdersOrSamples() {
        when(patientPrefixes.findPatientIdsByPrefix(5415L)).thenReturn(List.of(21));
        var result = service.findByCugPrefixOrOrderNumber(" 5415 ");
        assertEquals(1, result.size());
        assertEquals("21", result.get(0).getPatientID());
        assertEquals("Test", result.get(0).getFirstName());
        verifyZeroInteractions(samples, sampleHumans, orderFields);
        verify(persons).getData(patient.getPerson());
    }

    @Test
    public void exactPrefixTakesPrecedenceOverACollidingNumericOrderNumber() {
        when(patientPrefixes.findPatientIdsByPrefix(5415L)).thenReturn(List.of(21));
        assertEquals("21", service.findByCugPrefixOrOrderNumber("5415").get(0).getPatientID());
        verify(samples, never()).getSampleByAccessionNumber(anyString());
        verify(patientPrefixes, never()).findPatientIdsByPrefix(54150L);
    }

    @Test
    public void aSharedLegacyPrefixReturnsAllBoundPatients() {
        var second = new Patient();
        second.setId("22");
        when(patients.getData("22")).thenReturn(second);
        when(patientPrefixes.findPatientIdsByPrefix(5415L)).thenReturn(List.of(21, 22));
        assertEquals(List.of("21", "22"),
                service.findByCugPrefixOrOrderNumber("5415").stream().map(row -> row.getPatientID()).toList());
    }

    @Test
    public void orderNumberLookupIsPreservedWhenNoPrefixMatches() {
        when(samples.getSampleByAccessionNumber("LNG0000000000078")).thenReturn(sample);
        assertEquals("21", service.findByCugPrefixOrOrderNumber("LNG0000000000078").get(0).getPatientID());
        verifyZeroInteractions(patientPrefixes);
    }

    @Test
    public void numericOrderNumbersStillWorkWithoutAPrefixMatch() {
        when(samples.getSampleByAccessionNumber("13333")).thenReturn(sample);
        assertEquals("21", service.findByCugPrefixOrOrderNumber("13333").get(0).getPatientID());
        verify(patientPrefixes).findPatientIdsByPrefix(13333L);
    }

    @Test
    public void configuredSearchableOrderFieldsAndSampleItemNumbersRemainSupported() {
        when(orderFields.findSampleIdBySearchableFieldValue("EXTERNAL-78")).thenReturn(Optional.of(61));
        when(samples.get("61")).thenReturn(sample);
        assertEquals("21", service.findByCugPrefixOrOrderNumber("EXTERNAL-78").get(0).getPatientID());
        when(samples.getSampleByAccessionNumber("LNG78")).thenReturn(sample);
        assertEquals("21", service.findByCugPrefixOrOrderNumber("LNG78-1").get(0).getPatientID());
    }

    @Test
    public void completeSampleCugCompatibilityDoesNotDependOnNumericPrefixParsing() {
        when(samples.getSampleByAccessionNumber("5415.1")).thenReturn(sample);
        assertEquals("21", service.findByCugPrefixOrOrderNumber("5415.1").get(0).getPatientID());
        verifyZeroInteractions(patientPrefixes);
    }

    @Test
    public void blanksUnknownValuesAndOversizedNumbersDoNotReturnArbitraryPatients() {
        assertTrue(service.findByCugPrefixOrOrderNumber(null).isEmpty());
        assertTrue(service.findByCugPrefixOrOrderNumber(" ").isEmpty());
        assertTrue(service.findByCugPrefixOrOrderNumber("5416").isEmpty());
        assertTrue(service.findByCugPrefixOrOrderNumber("999999999999999999999999999999").isEmpty());
        verify(patientPrefixes, times(1)).findPatientIdsByPrefix(anyLong());
    }
}
