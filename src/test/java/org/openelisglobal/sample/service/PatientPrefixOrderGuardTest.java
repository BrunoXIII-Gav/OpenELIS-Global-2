package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.sample.action.util.SamplePatientUpdateData;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.sample.valueholder.Sample;
import org.springframework.test.util.ReflectionTestUtils;

public class PatientPrefixOrderGuardTest {
    @Test
    public void oldOrderPrefixIsCheckedBeforePersistingPatientOrderOrSamples() {
        var service = new SamplePatientEntryServiceImpl();
        var cugs = mock(SampleCugService.class);
        var accessions = mock(OrderAccessionService.class);
        ReflectionTestUtils.setField(service, "sampleCugService", cugs);
        ReflectionTestUtils.setField(service, "orderAccessionService", accessions);
        var info = new PatientManagementInfo();
        info.setPatientPK("10");
        info.setCugPrefix("5004");
        var update = mock(SamplePatientUpdateData.class);
        when(update.getSample()).thenReturn(new Sample());
        doThrow(new IllegalArgumentException("sample.cug.patient.prefix.mismatch")).when(cugs)
                .lockPatientAndCheckPrefix("10", "5004");
        var form = new SamplePatientEntryForm();
        form.setSampleOrderItems(new org.openelisglobal.sample.bean.SampleOrderItem());
        assertEquals("sample.cug.patient.prefix.mismatch",
                assertThrows(IllegalArgumentException.class, () -> service.persistData(update, null, info, form, null))
                        .getMessage());
        verify(cugs).lockPatientAndCheckPrefix("10", "5004");
        verify(update, never()).isSavePatient();
    }

    @Test
    public void automaticNumberIsAssignedBeforeCugValidationAndSamplePersistence() {
        verifyNumberAssignment("", true);
    }

    @Test
    public void explicitNumbersFromExistingClientsStillUseConfirmation() {
        verifyNumberAssignment("LAB0000000000025", false);
    }

    private void verifyNumberAssignment(String proposed, boolean automatic) {
        var service = new SamplePatientEntryServiceImpl();
        var cugs = mock(SampleCugService.class);
        var accessions = mock(OrderAccessionService.class);
        ReflectionTestUtils.setField(service, "sampleCugService", cugs);
        ReflectionTestUtils.setField(service, "orderAccessionService", accessions);
        var info = new PatientManagementInfo();
        info.setPatientPK("10");
        info.setCugPrefix("5004");
        var update = mock(SamplePatientUpdateData.class);
        var sample = new Sample();
        when(update.getSample()).thenReturn(sample);
        when(accessions.generate("TEST")).thenReturn("TEST00001");
        when(accessions.confirm(proposed, false)).thenReturn(proposed);
        doThrow(new IllegalArgumentException("sample.cug.patient.prefix.mismatch")).when(cugs)
                .lockPatientAndCheckPrefix("10", "5004");
        var form = new SamplePatientEntryForm();
        var items = new org.openelisglobal.sample.bean.SampleOrderItem();
        items.setLabNo(proposed);
        items.setProgram("TEST");
        form.setSampleOrderItems(items);
        assertThrows(IllegalArgumentException.class, () -> service.persistData(update, null, info, form, null));
        var calls = inOrder(accessions, update, cugs);
        String assigned;
        if (automatic) {
            calls.verify(accessions).generate("TEST");
            verify(accessions, never()).confirm(any(), anyBoolean());
            assigned = "TEST00001";
        } else {
            calls.verify(accessions).confirm(proposed, false);
            verify(accessions, never()).generate(any());
            assigned = proposed;
        }
        calls.verify(update).setAccessionNumber(assigned);
        calls.verify(cugs).lockPatientAndCheckPrefix("10", "5004");
        assertEquals(assigned, sample.getAccessionNumber());
        verify(update, never()).isSavePatient();
    }
}
