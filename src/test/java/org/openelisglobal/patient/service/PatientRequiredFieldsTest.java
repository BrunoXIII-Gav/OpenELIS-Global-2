package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Test;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.patientadditionalfield.bean.PatientFixedFieldConfigPayload;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldService;
import org.openelisglobal.sample.service.SampleCugService;
import org.springframework.test.util.ReflectionTestUtils;

public class PatientRequiredFieldsTest {
    @Test
    public void orderPersistenceCannotChangeAnAssignedPrefixEvenWithAForgedPayload() {
        var service = new PatientServiceImpl();
        var fields = mock(PatientAdditionalFieldService.class);
        var cugs = mock(SampleCugService.class);
        ReflectionTestUtils.setField(service, "patientAdditionalFieldService", fields);
        ReflectionTestUtils.setField(service, "sampleCugService", cugs);
        var info = prefixChange();
        var patient = new Patient();
        patient.setId("10");
        assertEquals(org.openelisglobal.sample.exception.PatientCugPrefixException.READONLY,
                assertThrows(org.openelisglobal.sample.exception.PatientCugPrefixException.class,
                        () -> service.persistPatientData(info, patient, "42")).getMessage());
        verifyZeroInteractions(cugs);
    }

    @Test
    public void patientModuleChecksOriginalPrefixBeforePersistingOtherPatientEdits() {
        var service = new PatientServiceImpl();
        var fields = mock(PatientAdditionalFieldService.class);
        var cugs = mock(SampleCugService.class);
        ReflectionTestUtils.setField(service, "patientAdditionalFieldService", fields);
        ReflectionTestUtils.setField(service, "sampleCugService", cugs);
        when(cugs.changePatientPrefix("10", "5004", "8000", "42"))
                .thenThrow(new org.openelisglobal.sample.exception.PatientCugPrefixException(
                        org.openelisglobal.sample.exception.PatientCugPrefixException.STALE));
        var patient = new Patient();
        patient.setId("10");
        assertThrows(org.openelisglobal.sample.exception.PatientCugPrefixException.class,
                () -> service.persistPatientDataFromPatientModule(prefixChange(), patient, "42"));
        verify(cugs).changePatientPrefix("10", "5004", "8000", "42");
        verify(fields, never()).validateAndPersistPatientValues(any(), any(), any(), any());
        assertNull(patient.getNationalId());
    }

    private PatientManagementInfo prefixChange() {
        var info = new PatientManagementInfo();
        info.setPatientUpdateStatus(org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus.UPDATE);
        info.setExpectedCugPrefix("5004");
        info.setRequestedCugPrefix("8000");
        return info;
    }

    @Test
    public void missingConfiguredRequiredFieldCannotPersistPatientOrAllocatePrefix() {
        var fields = mock(PatientAdditionalFieldService.class);
        var required = new PatientFixedFieldConfigPayload();
        required.setFieldKey("firstName");
        required.setVisible(true);
        required.setRequired(true);
        when(fields.getFixedFieldConfigs()).thenReturn(List.of(required));
        var cugs = mock(SampleCugService.class);
        var service = new PatientServiceImpl();
        ReflectionTestUtils.setField(service, "patientAdditionalFieldService", fields);
        ReflectionTestUtils.setField(service, "sampleCugService", cugs);
        try {
            service.persistPatientData(new PatientManagementInfo(), new Patient(), "42");
            fail("Missing required patient data must not be saved");
        } catch (IllegalArgumentException expected) {
            assertEquals("patient.fixed.fields.required", expected.getMessage());
        }
        verifyZeroInteractions(cugs);
        verify(fields, never()).validateAndPersistPatientValues(any(), any(), any(), any());
    }
}
