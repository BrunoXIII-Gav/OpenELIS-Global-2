package org.openelisglobal.audittrail;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldFileDAO;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldValueDAO;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldServiceImpl;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldPayload;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldValueDAO;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldServiceImpl;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldValue;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.dao.SampleItemAdditionalFieldValueDAO;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldServiceImpl;
import org.openelisglobal.sample.valueholder.SampleItemAdditionalFieldValue;
import org.springframework.test.util.ReflectionTestUtils;

public class ClinicalFieldAuthorshipTest {
    @Test
    public void genericSampleUnchangedFieldRetainsItsAuthor() {
        var service = new SampleTypeAdditionalFieldServiceImpl();
        var dao = mock(SampleItemAdditionalFieldValueDAO.class);
        inject(service, dao);
        var field = new SampleTypeAdditionalFieldPayload();
        field.setId(1);
        field.setFieldKey("collection");
        field.setFieldType("TEXT");
        var previous = new SampleItemAdditionalFieldValue();
        previous.setId(7);
        previous.setFieldValue("collected");
        previous.setSysUserId("11");
        when(dao.findBySampleItemIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(previous));
        service.validateAndPersistSampleItemValues("9", "100", Map.of("collection", "collected"), "12",
                Map.of("9", List.of(field)), false);
        assertEquals("11", previous.getSysUserId());
        verify(dao, never()).update(any());
    }

    @Test
    public void patientUnchangedFieldRetainsItsAuthor() {
        var service = new PatientAdditionalFieldServiceImpl();
        var dao = mock(PatientAdditionalFieldValueDAO.class);
        inject(service, dao);
        var field = new PatientAdditionalFieldPayload();
        field.setId(1);
        field.setFieldKey("responsible");
        field.setFieldType("TEXT");
        var previous = new PatientAdditionalFieldValue();
        previous.setId(7);
        previous.setFieldValue("Ana");
        previous.setSysUserId("11");
        when(dao.findByPatientIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(previous));
        service.validateAndPersistPatientValues("100", Map.of("responsible", "Ana"), "12", List.of(field));
        assertEquals("11", previous.getSysUserId());
        verify(dao, never()).update(any());
    }

    @Test
    public void orderUnchangedFieldRetainsItsAuthor() {
        var service = new OrderAdditionalFieldServiceImpl();
        var dao = mock(SampleOrderAdditionalFieldValueDAO.class);
        inject(service, dao);
        ReflectionTestUtils.setField(service, "fileDAO", mock(SampleOrderAdditionalFieldFileDAO.class));
        var field = new OrderAdditionalFieldPayload();
        field.setId(1);
        field.setFieldKey("responsible");
        field.setFieldType("TEXT");
        var previous = new SampleOrderAdditionalFieldValue();
        previous.setId(7);
        previous.setFieldValue("Ana");
        previous.setSysUserId("11");
        when(dao.findBySampleIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(previous));
        service.validateAndPersistSampleValues("100", Map.of("responsible", "Ana"), "12", List.of(field));
        assertEquals("11", previous.getSysUserId());
        verify(dao, never()).update(any());
    }

    private void inject(Object service, Object dao) {
        ReflectionTestUtils.setField(service, "valueDAO", dao);
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", mock(UserFieldOptionResolver.class));
    }
}
