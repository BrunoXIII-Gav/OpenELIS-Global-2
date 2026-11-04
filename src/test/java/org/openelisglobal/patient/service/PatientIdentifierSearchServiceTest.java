package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class PatientIdentifierSearchServiceTest {
    @InjectMocks
    private PatientIdentifierSearchServiceImpl service;
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
    public void orderNumberLookupFindsTheLinkedPatient() {
        when(samples.getSampleByAccessionNumber("LNG0000000000078")).thenReturn(sample);
        assertEquals("21", service.findByOrderNumber("LNG0000000000078").get(0).getPatientID());
    }

    @Test
    public void numericOrderNumbersFindTheLinkedPatient() {
        when(samples.getSampleByAccessionNumber("13333")).thenReturn(sample);
        assertEquals("21", service.findByOrderNumber("13333").get(0).getPatientID());
        verify(samples).getSampleByAccessionNumber("13333");
    }

    @Test
    public void configuredSearchableOrderFieldsAndSampleItemNumbersRemainSupported() {
        when(orderFields.findSampleIdBySearchableFieldValue("EXTERNAL-78")).thenReturn(Optional.of(61));
        when(samples.get("61")).thenReturn(sample);
        assertEquals("21", service.findByOrderNumber("EXTERNAL-78").get(0).getPatientID());
        when(samples.getSampleByAccessionNumber("LNG78")).thenReturn(sample);
        assertEquals("21", service.findByOrderNumber("LNG78-1").get(0).getPatientID());
    }

    @Test
    public void blanksUnknownValuesAndOversizedNumbersDoNotReturnArbitraryPatients() {
        assertTrue(service.findByOrderNumber(null).isEmpty());
        assertTrue(service.findByOrderNumber(" ").isEmpty());
        assertTrue(service.findByOrderNumber("5416").isEmpty());
        assertTrue(service.findByOrderNumber("999999999999999999999999999999").isEmpty());
    }
}
