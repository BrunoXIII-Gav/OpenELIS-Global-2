package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.openelisglobal.common.services.SampleAddService;
import org.openelisglobal.common.services.SampleAddService.SampleTestCollection;
import org.openelisglobal.observationhistory.valueholder.ObservationHistory;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.sample.action.util.SamplePatientUpdateData;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.sample.validator.SamplePatientEntryFormValidator;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BeanPropertyBindingResult;

public class AutomaticOrderValidationTest {
    private SamplePatientUpdateData update(boolean alternate, boolean withSample) throws Exception {
        var data = mock(SamplePatientUpdateData.class, CALLS_REAL_METHODS);
        // Construct the collection without initializing the outer service's static
        // Spring dependencies; validation only reads its test list.
        var sample = SampleTestCollection.class
                .getConstructor(SampleAddService.class, SampleItem.class, List.class, String.class, List.class,
                        Map.class, Map.class, ObservationHistory.class)
                .newInstance(null, new SampleItem(), List.of(), "", List.of(), Map.of(), Map.of(), null);
        sample.item.setSortOrder("1");
        ReflectionTestUtils.setField(data, "sampleItemsTests", withSample ? List.of(sample) : List.of());
        ReflectionTestUtils.setField(data, "patientErrors", new BeanPropertyBindingResult(new Object(), "patient"));
        data.setAlternateOrderFlow(alternate);
        return data;
    }

    @Test
    public void automaticAssignmentStillRequiresASample() throws Exception {
        var data = update(false, false);
        var errors = new BeanPropertyBindingResult(data, "order");
        data.validateSample(errors, true, true);
        assertEquals(1, errors.getErrorCount());
        assertEquals("errors.no.sample", errors.getGlobalError().getCode());
    }

    @Test
    public void normalOrdersStillRequireTests() throws Exception {
        var data = update(false, true);
        var errors = new BeanPropertyBindingResult(data, "order");
        data.validateSample(errors, true, true);
        assertEquals(1, errors.getErrorCount());
        assertEquals("errors.samples.with.no.tests", errors.getGlobalError().getCode());
    }

    @Test
    public void alternateOrdersStillAllowSamplesWithoutTests() throws Exception {
        var data = update(true, true);
        var errors = new BeanPropertyBindingResult(data, "order");
        data.validateSample(errors, true, true);
        assertFalse(errors.hasErrors());
    }

    @Test
    public void patientValidationErrorsArePreserved() throws Exception {
        var data = update(true, true);
        var patientErrors = new BeanPropertyBindingResult(new Object(), "order");
        patientErrors.reject("patient.invalid");
        data.setPatientErrors(patientErrors);
        var errors = new BeanPropertyBindingResult(data, "order");
        data.validateSample(errors, true, true);
        assertEquals("patient.invalid", errors.getGlobalError().getCode());
    }

    @Test
    public void sampleIdentifiersUseTheNumberAssignedDuringSave() throws Exception {
        var data = update(false, true);
        data.setSample(new Sample());
        var service = new SamplePatientEntryServiceImpl();
        var numbers = mock(OrderAccessionService.class);
        var cugs = mock(SampleCugService.class);
        ReflectionTestUtils.setField(service, "orderAccessionService", numbers);
        ReflectionTestUtils.setField(service, "sampleCugService", cugs);
        when(numbers.generate(null)).thenReturn("LAB0000000000008");
        doThrow(new IllegalArgumentException("sample.cug.patient.prefix.mismatch")).when(cugs)
                .lockPatientAndCheckPrefix("10", "5004");
        var patient = new PatientManagementInfo();
        patient.setPatientPK("10");
        patient.setCugPrefix("5004");
        var form = new SamplePatientEntryForm();
        form.setSampleOrderItems(new SampleOrderItem());
        assertThrows(IllegalArgumentException.class, () -> service.persistData(data, null, patient, form, null));
        assertEquals("LAB0000000000008", data.getSample().getAccessionNumber());
        assertEquals("LAB0000000000008-1", data.getSampleItemsTests().get(0).item.getExternalId());
    }

    @Test
    public void automaticOrderStillRequiresACugForEverySample() {
        var form = new SamplePatientEntryForm();
        form.setSampleXML("<samples><sample sampleID='1' tests='7' panels='' date='' time='' cug=''/></samples>");
        var errors = new BeanPropertyBindingResult(form, "order");
        new SamplePatientEntryFormValidator().validate(form, errors);
        assertEquals("sample.cug.required", errors.getGlobalError().getCode());
    }

    @Test
    public void automaticOrderStillRejectsMalformedCugs() {
        var form = new SamplePatientEntryForm();
        form.setSampleXML("<samples><sample sampleID='1' tests='7' panels='' date='' time='' cug='5001'/></samples>");
        var errors = new BeanPropertyBindingResult(form, "order");
        new SamplePatientEntryFormValidator().validate(form, errors);
        assertEquals("sample.cug.invalid.format", errors.getGlobalError().getCode());
    }
}
