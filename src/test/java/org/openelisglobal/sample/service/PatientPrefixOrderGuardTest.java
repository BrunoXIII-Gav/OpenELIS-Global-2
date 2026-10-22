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
}
