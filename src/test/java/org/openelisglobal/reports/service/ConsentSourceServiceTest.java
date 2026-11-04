package org.openelisglobal.reports.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldService;
import org.openelisglobal.professionalprofile.service.ProfessionalProfileDefinitionService;
import org.openelisglobal.program.service.ProgramService;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.unitofmeasure.service.UnitOfMeasureService;
import org.springframework.test.util.ReflectionTestUtils;

public class ConsentSourceServiceTest {
    @Test
    public void discoversNewOrderFieldsOnEveryReadAndDoesNotExposeInternalKeysInLabels() {
        ConsentSourceService service = new ConsentSourceService();
        OrderAdditionalFieldService orders = mock(OrderAdditionalFieldService.class);
        ReflectionTestUtils.setField(service, "orderFields", orders);
        ReflectionTestUtils.setField(service, "patientFields", mock(PatientAdditionalFieldService.class));
        ReflectionTestUtils.setField(service, "profiles", mock(ProfessionalProfileDefinitionService.class));
        ReflectionTestUtils.setField(service, "programs", mock(ProgramService.class));
        ReflectionTestUtils.setField(service, "sampleFields", mock(SampleTypeAdditionalFieldService.class));
        ReflectionTestUtils.setField(service, "sampleTypes", mock(TypeOfSampleService.class));
        ReflectionTestUtils.setField(service, "units", mock(UnitOfMeasureService.class));
        when(orders.getFields(false, true)).thenReturn(List.of());
        assertFalse(service.getSources().stream().anyMatch(s -> "orderAdditional.future".equals(s.get("id"))));
        OrderAdditionalFieldPayload field = new OrderAdditionalFieldPayload();
        field.setFieldKey("future");
        field.setDisplayName("Nuevo dato visible");
        field.setFieldType("TEXT");
        field.setActive(true);
        when(orders.getFields(false, true)).thenReturn(List.of(field));
        Map<String, Object> source = service.getSources().stream()
                .filter(s -> "orderAdditional.future".equals(s.get("id"))).findFirst().orElseThrow();
        assertEquals("Nuevo dato visible", source.get("label"));
        assertEquals("sampleOrderItems.additionalFieldValues.future", source.get("path"));
        assertFalse(service.getSources().stream().anyMatch(s -> String.valueOf(s.get("id")).contains("patientPK")));
    }
}
