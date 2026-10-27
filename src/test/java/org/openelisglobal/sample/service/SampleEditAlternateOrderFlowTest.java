package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.StatusService.SampleStatus;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DefaultConfigurationProperties;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldValueDAO;
import org.openelisglobal.orderadditionalfield.service.AlternateOrderFlowServiceImpl;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.sample.bean.SampleEditItem;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.form.SampleEditForm;
import org.openelisglobal.sample.controller.rest.SampleEditRestController;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.samplehuman.valueholder.SampleHuman;
import org.openelisglobal.sampleitem.service.SampleItemService;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeofsample.service.TypeOfSampleTestService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSample;
import org.openelisglobal.typeofsample.valueholder.TypeOfSampleTest;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

public class SampleEditAlternateOrderFlowTest {
    private static final Map<Class<?>, Object> BEANS = new HashMap<>();
    private static Object previousFactory;
    private static Object previousMessageUtil;
    private SampleEditServiceImpl service;
    private SampleEditForm form;
    private Sample sample;
    private SampleItem item;
    private AnalysisService analyses;
    private final Map<String, String> savedValues = new HashMap<>();

    @BeforeClass
    public static void installContext() {
        previousFactory = ReflectionTestUtils.getField(SpringContext.class, "factory");
        var factory = mock(AutowireCapableBeanFactory.class);
        when(factory.getBean(any(Class.class))).thenAnswer(call -> {
            Class<?> type = call.getArgument(0);
            return BEANS.computeIfAbsent(type, key -> mock(key));
        });
        ReflectionTestUtils.setField(SpringContext.class, "factory", factory);
        when(bean(IStatusService.class).getStatusID(AnalysisStatus.NotStarted)).thenReturn("not-started");
        when(bean(IStatusService.class).getStatusID(AnalysisStatus.Canceled)).thenReturn("6");
        when(bean(IStatusService.class).getStatusID(SampleStatus.Entered)).thenReturn("1");
        when(bean(DefaultConfigurationProperties.class).getPropertyValue(Property.DEFAULT_DATE_LOCALE))
                .thenReturn("en");
        previousMessageUtil = ReflectionTestUtils.getField(MessageUtil.class, "instance");
        var messages = new org.springframework.context.support.StaticMessageSource();
        messages.addMessage("date.format.formatKey", java.util.Locale.ENGLISH, "dd/MM/yyyy");
        MessageUtil.setMessageSource(messages);
    }

    @AfterClass
    public static void restoreContext() {
        ReflectionTestUtils.setField(SpringContext.class, "factory", previousFactory);
        ReflectionTestUtils.setField(MessageUtil.class, "instance", previousMessageUtil);
    }

    private static <T> T bean(Class<T> type) {
        return SpringContext.getBean(type);
    }

    @Before
    public void setup() {
        service = new SampleEditServiceImpl();
        for (var field : SampleEditServiceImpl.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) {
                ReflectionTestUtils.setField(service, field.getName(), bean(field.getType()));
            }
        }
        analyses = bean(AnalysisService.class);
        reset(analyses);
        when(analyses.insert(any(Analysis.class))).thenReturn("80");

        sample = new Sample();
        sample.setId("44");
        sample.setAccessionNumber("LNG0000000000047");
        var patient = new Patient();
        patient.setId("10");
        when(bean(SampleService.class).getPatient(sample)).thenReturn(patient);
        item = new SampleItem();
        item.setId("55");
        item.setSample(sample);
        item.setCugCode("5411.1");
        when(bean(SampleItemService.class).get("55")).thenReturn(item);
        var test = new org.openelisglobal.test.valueholder.Test();
        test.setId("30");
        when(bean(TestService.class).get("30")).thenReturn(test);
        when(bean(SampleHumanService.class).getDataBySample(any(SampleHuman.class)))
                .thenReturn(new SampleHuman());

        var fields = bean(OrderAdditionalFieldService.class);
        reset(fields);
        var definition = new OrderAdditionalFieldPayload();
        definition.setId(1);
        definition.setFieldKey("alternate");
        definition.setFieldType("BOOLEAN");
        definition.setMetadataJson("{\"workflow\":{\"alternateOrderFlow\":true,\"triggerValue\":\"true\"}}");
        when(fields.getFields(false)).thenReturn(List.of(definition));
        savedValues.put("alternate", "true");
        doAnswer(call -> {
            Map<String, String> submitted = call.getArgument(1);
            if (submitted != null) savedValues.putAll(submitted);
            return null;
        }).when(fields).validateAndPersistSampleValues(eq("44"), any(), any(), eq("42"), isNull());

        var values = mock(SampleOrderAdditionalFieldValueDAO.class);
        when(values.findBySampleIdsAndFieldDefinitionIds(anyList(), anyList())).thenAnswer(call -> {
            var value = new SampleOrderAdditionalFieldValue();
            value.setSampleId(44);
            value.setFieldDefinitionId(1);
            value.setFieldValue(savedValues.get("alternate"));
            return List.of(value);
        });
        var flow = new AlternateOrderFlowServiceImpl();
        ReflectionTestUtils.setField(flow, "orderAdditionalFieldService", fields);
        ReflectionTestUtils.setField(flow, "sampleOrderAdditionalFieldValueDAO", values);
        ReflectionTestUtils.setField(service, "alternateOrderFlowService", flow);

        form = mock(SampleEditForm.class);
        var order = new SampleOrderItem();
        order.setAdditionalFieldValues(new HashMap<>());
        when(form.getSampleOrderItems()).thenReturn(order);
        when(form.getSampleXML()).thenReturn("<samples/>");
        when(form.getExistingTests()).thenReturn(List.of());
        var selection = new SampleEditItem();
        selection.setSampleItemId("55");
        selection.setTestId("30");
        selection.setAdd(true);
        when(form.getPossibleTests()).thenReturn(List.of(selection));
    }

    @Test
    public void uncheckingAlternateFlowCreatesSelectedAnalysisInOneSaveAndKeepsCug() {
        form.getSampleOrderItems().getAdditionalFieldValues().put("alternate", "false");
        service.editSample(form, new MockHttpServletRequest(), sample, false, "42");

        var created = ArgumentCaptor.forClass(Analysis.class);
        verify(analyses).insert(created.capture());
        assertSame(item, created.getValue().getSampleItem());
        assertEquals("5411.1", item.getCugCode());
        assertEquals("30", created.getValue().getTest().getId());
        assertEquals("false", savedValues.get("alternate"));
    }

    @Test
    public void keepingAlternateFlowStillOmitsAnalyses() {
        form.getSampleOrderItems().getAdditionalFieldValues().put("alternate", "true");
        service.editSample(form, new MockHttpServletRequest(), sample, false, "42");
        verify(analyses, never()).insert(any(Analysis.class));
        assertEquals("5411.1", item.getCugCode());
    }

    @Test
    public void omittingTheFieldPreservesTheStoredAlternateFlow() {
        service.editSample(form, new MockHttpServletRequest(), sample, false, "42");
        verify(analyses, never()).insert(any(Analysis.class));
        assertEquals("true", savedValues.get("alternate"));
    }

    @Test
    public void checkingAlternateFlowOnANormalOrderOmitsAnalysesImmediately() {
        savedValues.put("alternate", "false");
        form.getSampleOrderItems().getAdditionalFieldValues().put("alternate", "true");
        service.editSample(form, new MockHttpServletRequest(), sample, false, "42");
        verify(analyses, never()).insert(any(Analysis.class));
        assertEquals("true", savedValues.get("alternate"));
    }

    @Test
    public void loadingAvailableTestsIncludesTheSavedCugForASampleWithNoAnalyses() {
        var controller = new SampleEditRestController();
        for (var field : SampleEditRestController.class.getDeclaredFields()) {
            if (field.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class)) {
                ReflectionTestUtils.setField(controller, field.getName(), bean(field.getType()));
            }
        }
        var type = mock(TypeOfSample.class);
        when(type.getId()).thenReturn("1");
        when(type.getLocalizedName()).thenReturn("Blood");
        item.setTypeOfSample(type);
        when(bean(TypeOfSampleService.class).get("1")).thenReturn(type);
        var sampleTest = mock(TypeOfSampleTest.class);
        when(sampleTest.getTestId()).thenReturn("30");
        when(bean(TypeOfSampleTestService.class).getTypeOfSampleTestsForSampleType("1"))
                .thenReturn(List.of(sampleTest));
        var test = bean(TestService.class).get("30");
        test.setIsActive("Y");
        test.setOrderable(true);
        var possible = new java.util.ArrayList<SampleEditItem>();

        ReflectionTestUtils.invokeMethod(controller, "addPossibleTestsToList", item, possible,
                sample.getAccessionNumber());

        assertEquals(1, possible.size());
        assertEquals("55", possible.get(0).getSampleItemId());
        assertEquals("5411.1", possible.get(0).getCugCode());
    }
}
