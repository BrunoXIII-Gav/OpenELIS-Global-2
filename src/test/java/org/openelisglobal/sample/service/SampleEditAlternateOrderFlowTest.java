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
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.sample.bean.SampleEditItem;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.controller.rest.SampleEditRestController;
import org.openelisglobal.sample.exception.OrderTestHasResultsException;
import org.openelisglobal.sample.form.SampleEditForm;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.samplehuman.valueholder.SampleHuman;
import org.openelisglobal.sampleitem.service.SampleItemService;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.testadditionalfield.dao.AnalysisAdditionalFieldValueDAO;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;
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
        when(bean(IStatusService.class).getStatusID(SampleStatus.Canceled)).thenReturn("7");
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
        var removal = new OrderTestRemovalServiceImpl();
        reset(bean(ResultService.class), bean(AnalysisAdditionalFieldValueDAO.class));
        ReflectionTestUtils.setField(removal, "resultService", bean(ResultService.class));
        ReflectionTestUtils.setField(removal, "additionalValues", bean(AnalysisAdditionalFieldValueDAO.class));
        BEANS.put(OrderTestRemovalService.class, removal);
        ReflectionTestUtils.setField(service, "orderTestRemovalService", removal);
        analyses = bean(AnalysisService.class);
        reset(analyses);
        reset(bean(SampleItemService.class), bean(SampleCugService.class), bean(OrderAuthorizationService.class));
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
        when(bean(SampleHumanService.class).getDataBySample(any(SampleHuman.class))).thenReturn(new SampleHuman());

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
            if (submitted != null)
                savedValues.putAll(submitted);
            return null;
        }).when(fields).validateAndPersistSampleValues(eq("44"), any(), any(), eq("42"), isNull(), anyMap());

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
    public void removingASampleCancelsAllItsAnalysesEvenWhenTheyAreNotGroupedInThePayload() {
        var first = analysis("80", item);
        var second = analysis("81", item);
        when(analyses.getAnalysesBySampleItemsExcludingByStatusIds(eq(item), anySet()))
                .thenReturn(List.of(first, second));
        var authorization = bean(OrderAuthorizationService.class);
        when(authorization.hasPermission("42", "cancel")).thenReturn(true);
        when(authorization.canAccessAllTests(eq("42"), anyList(), eq("cancel"))).thenReturn(true);
        when(form.getRemovedSampleItemIds()).thenReturn(List.of("55", "55"));
        when(form.getPossibleTests()).thenReturn(List.of());

        service.editSample(form, new MockHttpServletRequest(), sample, false, "42");

        assertEquals(ReflectionTestUtils.getField(SampleEditServiceImpl.class, "CANCELED_SAMPLE_STATUS_ID"),
                item.getStatusId());
        assertTrue(item.isVoided());
        assertEquals(ReflectionTestUtils.getField(SampleEditServiceImpl.class, "CANCELED_TEST_STATUS_ID"),
                first.getStatusId());
        assertEquals(ReflectionTestUtils.getField(SampleEditServiceImpl.class, "CANCELED_TEST_STATUS_ID"),
                second.getStatusId());
        verify(analyses).update(first);
        verify(analyses).update(second);
        verify(bean(SampleCugService.class)).releaseRemovedSampleCug(item, "10", "42");
        verify(bean(SampleItemService.class), times(1)).update(item);
    }

    @Test
    public void legacyRemoveFlagAlsoRemovesASampleWithoutAnAnalysisId() {
        var row = new SampleEditItem();
        row.setSampleItemId("55");
        row.setRemoveSample(true);
        when(form.getExistingTests()).thenReturn(List.of(row));
        when(form.getPossibleTests()).thenReturn(List.of());
        when(bean(OrderAuthorizationService.class).hasPermission("42", "cancel")).thenReturn(true);
        when(bean(OrderAuthorizationService.class).canAccessAllTests(eq("42"), anyList(), eq("cancel")))
                .thenReturn(true);
        when(analyses.getAnalysesBySampleItemsExcludingByStatusIds(eq(item), anySet())).thenReturn(List.of());

        service.editSample(form, new MockHttpServletRequest(), sample, false, "42");

        assertTrue(item.isVoided());
        verify(analyses, never()).get((String) null);
    }

    @Test
    public void removingASampleFromAnotherOrderIsRejectedBeforeChangingIt() {
        var other = new Sample();
        other.setId("999");
        item.setSample(other);
        when(form.getRemovedSampleItemIds()).thenReturn(List.of("55"));
        when(bean(OrderAuthorizationService.class).hasPermission("42", "cancel")).thenReturn(true);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.editSample(form, new MockHttpServletRequest(), sample, false, "42"));
        assertFalse(item.isVoided());
        assertEquals("5411.1", item.getCugCode());
    }

    @Test
    public void removalRequiresCancellationPermissionEvenWithAnExplicitSampleId() {
        when(form.getRemovedSampleItemIds()).thenReturn(List.of("55"));
        when(bean(OrderAuthorizationService.class).hasPermission("42", "cancel")).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.editSample(form, new MockHttpServletRequest(), sample, false, "42"));
        assertFalse(item.isVoided());
    }

    private Analysis analysis(String id, SampleItem sampleItem) {
        var analysis = new Analysis();
        analysis.setId(id);
        analysis.setSampleItem(sampleItem);
        analysis.setTest(bean(TestService.class).get("30"));
        return analysis;
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
    public void selectedExceptionCreatesOnlyThatAnalysisAndKeepsOrderInAlternateFlow() {
        var allowed = bean(TestService.class).get("30");
        allowed.setAllowProcessingInAlternateFlow(true);
        var other = new org.openelisglobal.test.valueholder.Test();
        other.setId("31");
        when(bean(TestService.class).get("31")).thenReturn(other);
        var selection = new SampleEditItem();
        selection.setTestId("31");
        selection.setSampleItemId("55");
        selection.setAdd(true);
        var firstSelection = form.getPossibleTests().get(0);
        when(form.getPossibleTests()).thenReturn(List.of(firstSelection, selection));
        service.editSample(form, new MockHttpServletRequest(), sample, false, "42");
        var created = ArgumentCaptor.forClass(Analysis.class);
        verify(analyses).insert(created.capture());
        assertEquals("30", created.getValue().getTest().getId());
        assertEquals("true", savedValues.get("alternate"));
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

    private SampleEditItem cancellation(Analysis analysis) {
        var row = new SampleEditItem();
        row.setAnalysisId(analysis.getId());
        row.setSampleItemId(item.getId());
        row.setTestId("30");
        row.setCanCancel(true);
        row.setCanceled(true);
        when(form.getExistingTests()).thenReturn(List.of(row));
        when(form.getPossibleTests()).thenReturn(List.of());
        when(analyses.get(analysis.getId())).thenReturn(analysis);
        when(bean(OrderAuthorizationService.class).hasPermission("42", "cancel")).thenReturn(true);
        when(bean(OrderAuthorizationService.class).canAccessAllTests(eq("42"), anyList(), eq("cancel")))
                .thenReturn(true);
        return row;
    }

    @Test
    public void deselectingAnEmptyStartedTestCancelsItAndKeepsItsSampleAndCug() {
        var analysis = analysis("80", item);
        analysis.setStatusId("2");
        cancellation(analysis);
        service.editSample(form, new MockHttpServletRequest(), sample, false, "42");
        assertEquals(ReflectionTestUtils.getField(SampleEditServiceImpl.class, "CANCELED_TEST_STATUS_ID"),
                analysis.getStatusId());
        verify(analyses).update(analysis);
        assertFalse(item.isVoided());
        assertEquals("5411.1", item.getCugCode());
        verify(bean(SampleCugService.class), never()).releaseRemovedSampleCug(any(), any(), any());
    }

    @Test
    public void anAdditionalResultBlocksForgedCancellationBeforeAnyChangesAreSaved() {
        var analysis = analysis("80", item);
        analysis.setStatusId("2");
        cancellation(analysis);
        var field = new AnalysisAdditionalFieldValue();
        field.setFieldValue("0");
        when(bean(AnalysisAdditionalFieldValueDAO.class).findByAnalysisId(80)).thenReturn(List.of(field));
        assertThrows(OrderTestHasResultsException.class,
                () -> service.editSample(form, new MockHttpServletRequest(), sample, false, "42"));
        assertEquals("2", analysis.getStatusId());
        assertFalse(item.isVoided());
        verify(analyses, never()).update(any());
        verify(bean(OrderAdditionalFieldService.class), never()).validateAndPersistSampleValues(any(), any(), any(),
                any(), any(), any());
    }

    @Test
    public void removingTheEntireSampleCannotBypassTheResultGuard() {
        var analysis = analysis("80", item);
        analysis.setStatusId("2");
        cancellation(analysis);
        when(form.getExistingTests()).thenReturn(List.of());
        when(form.getRemovedSampleItemIds()).thenReturn(List.of("55"));
        when(analyses.getAnalysesBySampleItemsExcludingByStatusIds(eq(item), anySet())).thenReturn(List.of(analysis));
        var result = new Result();
        result.setValue("7");
        when(bean(ResultService.class).getResultsByAnalysis(analysis)).thenReturn(List.of(result));
        assertThrows(OrderTestHasResultsException.class,
                () -> service.editSample(form, new MockHttpServletRequest(), sample, false, "42"));
        assertFalse(item.isVoided());
        assertEquals("2", analysis.getStatusId());
        verify(bean(SampleCugService.class), never()).releaseRemovedSampleCug(any(), any(), any());
    }

    @Test
    public void testsCanBeAddedWhileAnotherEmptyTestIsRemovedInTheSameSave() {
        var removed = analysis("80", item);
        cancellation(removed);
        form.getSampleOrderItems().getAdditionalFieldValues().put("alternate", "false");
        var added = new org.openelisglobal.test.valueholder.Test();
        added.setId("31");
        when(bean(TestService.class).get("31")).thenReturn(added);
        var selection = new SampleEditItem();
        selection.setSampleItemId("55");
        selection.setTestId("31");
        selection.setAdd(true);
        when(form.getPossibleTests()).thenReturn(List.of(selection));
        service.editSample(form, new MockHttpServletRequest(), sample, false, "42");
        verify(analyses).update(removed);
        var captured = ArgumentCaptor.forClass(Analysis.class);
        verify(analyses).insert(captured.capture());
        assertEquals("31", captured.getValue().getTest().getId());
        assertSame(item, captured.getValue().getSampleItem());
    }

    @Test
    public void aForgedAnalysisFromAnotherOrderCannotBeRemoved() {
        var analysis = analysis("80", item);
        cancellation(analysis);
        var other = new Sample();
        other.setId("999");
        var foreign = new SampleItem();
        foreign.setId("88");
        foreign.setSample(other);
        analysis.setSampleItem(foreign);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.editSample(form, new MockHttpServletRequest(), sample, false, "42"));
        verify(analyses, never()).update(any());
    }

    @Test
    public void removalMetadataDependsOnSavedValuesInsteadOfTheStartedStatus() {
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
        var analysis = analysis("80", item);
        analysis.setStatusId("2");
        when(analyses.getAnalysesBySampleItemsExcludingByStatusIds(eq(item), anySet())).thenReturn(List.of(analysis));
        var rows = new java.util.ArrayList<SampleEditItem>();
        ReflectionTestUtils.invokeMethod(controller, "addCurrentTestsToList", item, rows, sample.getAccessionNumber(),
                true);
        assertTrue(rows.get(0).isCanCancel());
        assertFalse(rows.get(0).isHasResults());
        var value = new AnalysisAdditionalFieldValue();
        value.setFieldValue("entered");
        when(bean(AnalysisAdditionalFieldValueDAO.class).findByAnalysisId(80)).thenReturn(List.of(value));
        rows.clear();
        ReflectionTestUtils.invokeMethod(controller, "addCurrentTestsToList", item, rows, sample.getAccessionNumber(),
                true);
        assertFalse(rows.get(0).isCanCancel());
        assertTrue(rows.get(0).isHasResults());
    }

    @Test
    public void aResultConflictIsReturnedAs409InsteadOfASuccessfulEdit() throws Exception {
        when(bean(org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.class)
                .getGenerator(org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat.MAIN))
                        .thenReturn(mock(org.openelisglobal.common.provider.validation.IAccessionNumberGenerator.class));
        var controller = new SampleEditRestController();
        var authorization = mock(OrderAuthorizationService.class);
        when(authorization.hasPermission(anyString(), anyString())).thenReturn(true);
        when(authorization.canAccessAllTests(anyString(), anyList(), anyString())).thenReturn(true);
        var edit = mock(SampleEditService.class);
        doThrow(new OrderTestHasResultsException()).when(edit).editSample(any(), any(), any(), anyBoolean(),
                anyString());
        ReflectionTestUtils.setField(controller, "orderAuthorizationService", authorization);
        ReflectionTestUtils.setField(controller, "sampleEditService", edit);
        ReflectionTestUtils.setField(controller, "formValidator",
                mock(org.openelisglobal.sample.validator.SampleEditFormValidator.class));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new org.openelisglobal.config.ControllerSetup())
                .setMessageConverters(new org.openelisglobal.config.AppConfig().jacksonMessageConverter())
                .setValidator(new org.springframework.validation.Validator() {
                    public boolean supports(Class<?> type) {
                        return true;
                    }

                    public void validate(Object target, org.springframework.validation.Errors errors) {
                    }
                }).build();
        var session = new org.springframework.mock.web.MockHttpSession();
        var user = new org.openelisglobal.login.valueholder.UserSessionData();
        user.setSytemUserId(42);
        session.setAttribute(org.openelisglobal.common.action.IActionConstants.USER_SESSION_DATA, user);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/rest/SampleEdit")
                .session(session).contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(
                        "{\"accessionNumber\":\"LAB1\",\"existingTests\":[{\"sampleItemId\":\"55\",\"testId\":\"30\",\"analysisId\":\"80\",\"canceled\":true}]}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string("{\"code\":\"order.edit.test.hasResults\"}"));
        verify(edit).editSample(any(), any(), isNull(), eq(false), eq("42"));
    }
}
