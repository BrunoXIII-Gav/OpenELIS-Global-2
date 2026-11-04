package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Test;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.patientadditionalfield.bean.PatientFixedFieldConfigPayload;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldService;
import org.springframework.test.util.ReflectionTestUtils;

public class PatientRequiredFieldsTest {

    private PatientManagementInfo prefixChange() {
        var info = new PatientManagementInfo();
        info.setPatientUpdateStatus(org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus.UPDATE);
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
        var service = new PatientServiceImpl();
        ReflectionTestUtils.setField(service, "patientAdditionalFieldService", fields);
        try {
            service.persistPatientData(new PatientManagementInfo(), new Patient(), "42");
            fail("Missing required patient data must not be saved");
        } catch (IllegalArgumentException expected) {
            assertEquals("patient.fixed.fields.required", expected.getMessage());
        }
        verify(fields, never()).validateAndPersistPatientValues(any(), any(), any(), any());
    }
}
