package org.openelisglobal.patient.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.SampleOrderService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.StatusService.OrderStatus;
import org.openelisglobal.common.services.StatusSet;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.DefaultConfigurationProperties;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.observationhistory.service.ObservationHistoryService;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFilePayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderFixedFieldConfigPayload;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.patient.form.PatientHistorySummary;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.result.action.util.ResultsLoadUtility;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.openelisglobal.sample.valueholder.OrderPriority;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.storage.service.SampleStorageService;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class PatientHistorySummaryServiceTest {

    @InjectMocks
    private PatientHistorySummaryServiceImpl service;

    @Mock
    private PatientService patientService;

    @Mock
    private SampleHumanService sampleHumanService;

    @Mock
    private SampleService sampleService;

    @Mock
    private SampleItemDAO sampleItemDAO;

    @Mock
    private SampleStorageService sampleStorageService;

    @Mock
    private AnalysisService analysisService;

    @Mock
    private ObservationHistoryService observationHistoryService;

    @Mock
    private IStatusService statusService;

    @Mock
    private PersonService personService;

    @Mock
    private ResultService resultService;
    @Mock
    private ObjectProvider<SampleOrderService> sampleOrderServiceProvider;
    @Mock
    private ObjectProvider<ResultsLoadUtility> resultsLoadUtilityProvider;
    @Mock
    private SampleTypeAdditionalFieldService sampleTypeAdditionalFieldService;

    private Object previousFactory;
    private Object previousMessages;

    @Before
    public void configureDateFormatting() {
        // Keep this unit test independent of Spring startup and execution order.
        previousFactory = ReflectionTestUtils.getField(SpringContext.class, "factory");
        previousMessages = ReflectionTestUtils.getField(MessageUtil.class, "instance");
        var factory = mock(AutowireCapableBeanFactory.class);
        var config = mock(DefaultConfigurationProperties.class);
        lenient().when(factory.getBean(DefaultConfigurationProperties.class)).thenReturn(config);
        lenient().when(config.getPropertyValue(ConfigurationProperties.Property.AmbiguousDateHolder)).thenReturn("X");
        lenient().when(config.getPropertyValue(ConfigurationProperties.Property.DEFAULT_DATE_LOCALE))
                .thenReturn("en-US");
        ReflectionTestUtils.setField(SpringContext.class, "factory", factory);
        var messages = new StaticMessageSource();
        messages.addMessage("date.format.formatKey", Locale.US, "MM/dd/yyyy");
        messages.addMessage("time.format.formatKey", Locale.US, "HH:mm");
        MessageUtil.setMessageSource(messages);
        SampleOrderService orderFields = mock(SampleOrderService.class);
        lenient().when(sampleOrderServiceProvider.getObject()).thenReturn(orderFields);
    }

    @After
    public void restoreDateFormatting() {
        ReflectionTestUtils.setField(SpringContext.class, "factory", previousFactory);
        ReflectionTestUtils.setField(MessageUtil.class, "instance", previousMessages);
    }

    @Test
    public void getSummary_compilesOrdersSamplesStorageAndMetrics() {
        Patient patient = new Patient();
        patient.setId("77");

        Sample sample = new Sample();
        sample.setId("501");
        sample.setAccessionNumber("LAB-501");
        sample.setClinicalOrderId("ORD-9");
        sample.setClientReference("REF-33");
        sample.setPriority(OrderPriority.STAT);
        sample.setReceivedTimestamp(Timestamp.valueOf("2026-07-10 10:00:00"));
        sample.setReceivedDateForDisplay("07/10/2026");
        sample.setCollectionDate(Timestamp.valueOf("2026-07-09 08:30:00"));

        SampleItem sampleItem = new SampleItem();
        sampleItem.setId("9001");
        sampleItem.setExternalId("LAB-501-1");
        sampleItem.setCugCode("123.4");
        sampleItem.setCollectionDate(Timestamp.valueOf("2026-07-09 08:30:00"));
        sampleItem.setStatusId("sample-entered");

        Person requester = new Person();
        requester.setId("88");
        requester.setFirstName("Ana");
        requester.setLastName("Garcia");

        Organization organization = new Organization();
        organization.setId("15");
        organization.setOrganizationName("Central Clinic");

        Analysis finalized = new Analysis();
        finalized.setId("1");
        finalized.setStatusId("analysis-finalized");
        org.openelisglobal.test.valueholder.Test finalizedTest = new org.openelisglobal.test.valueholder.Test();
        finalizedTest.setDescription("Hemoglobin");
        org.openelisglobal.localization.valueholder.Localization finalizedName = new org.openelisglobal.localization.valueholder.Localization();
        finalizedName.setEnglish("Hemoglobin");
        finalizedTest.setLocalizedTestName(finalizedName);
        finalized.setTest(finalizedTest);

        Analysis inProgress = new Analysis();
        inProgress.setId("2");
        inProgress.setStatusId("analysis-in-progress");
        org.openelisglobal.test.valueholder.Test inProgressTest = new org.openelisglobal.test.valueholder.Test();
        inProgressTest.setDescription("Platelets");
        org.openelisglobal.localization.valueholder.Localization inProgressName = new org.openelisglobal.localization.valueholder.Localization();
        inProgressName.setEnglish("Platelets");
        inProgressTest.setLocalizedTestName(inProgressName);
        inProgress.setTest(inProgressTest);
        finalized.setSampleItem(sampleItem);
        inProgress.setSampleItem(sampleItem);

        StatusSet statusSet = new StatusSet();
        statusSet.setSampleStatus(OrderStatus.Finished);

        when(patientService.get("77")).thenReturn(patient);
        when(sampleHumanService.getSamplesForPatient("77")).thenReturn(List.of(sample));
        when(observationHistoryService.getValueForSample(any(), eq("501"))).thenReturn("07/09/2026");
        when(statusService.getStatusSetForSampleId("501")).thenReturn(statusSet);
        when(statusService.getStatusName(OrderStatus.Finished)).thenReturn("Finished");
        when(sampleService.getOrganizationRequester(eq(sample), anyString())).thenReturn(organization);
        when(sampleService.getPersonRequester(sample)).thenReturn(requester);
        when(personService.getLastFirstName(requester)).thenReturn("Garcia, Ana");
        when(sampleItemDAO.getSampleItemsBySampleId("501")).thenReturn(List.of(sampleItem));
        when(sampleItemDAO.getSampleItemsWithHierarchy(List.of("9001"))).thenReturn(List.of(sampleItem));
        when(sampleStorageService.getSampleItemLocation("9001"))
                .thenReturn(Map.of("hierarchicalPath", "Room A > Rack 1 > Box 2", "assignedDate", "2026-07-11 09:00:00",
                        "positionCoordinate", "A2", "notes", "Frozen"));
        when(statusService.getStatusNameFromId("sample-entered")).thenReturn("Entered");
        when(analysisService.getAnalysesBySampleId(sample.getId())).thenReturn(List.of(finalized, inProgress));
        when(statusService.matches("analysis-finalized", AnalysisStatus.Finalized)).thenReturn(true);

        PatientHistorySummary summary = service.getSummary("77");

        assertEquals(1, summary.getOrders().size());
        assertEquals(1, summary.getSamples().size());
        assertEquals("LAB-501", summary.getOrders().get(0).getAccessionNumber());
        assertEquals("Central Clinic", summary.getOrders().get(0).getReferringSiteName());
        assertEquals("Garcia, Ana", summary.getOrders().get(0).getRequesterName());
        assertEquals("Room A > Rack 1 > Box 2", summary.getSamples().get(0).getStorageLocation());
        assertTrue(summary.getSamples().get(0).isStored());
        assertEquals(2, summary.getResults().size());
        assertEquals(1, summary.getMetrics().getTotalOrders());
        assertEquals(1, summary.getMetrics().getTotalSamples());
        assertEquals(1, summary.getMetrics().getStoredSamples());
        assertEquals(2, summary.getMetrics().getTotalTests());
        assertEquals(1, summary.getMetrics().getCompletedTests());
        assertEquals(1, summary.getMetrics().getPendingTests());
    }

    @Test
    public void getSummary_returnsEmptySummaryWhenPatientDoesNotExist() {
        when(patientService.get("404")).thenReturn(null);

        PatientHistorySummary summary = service.getSummary("404");

        assertEquals("404", summary.getPatientId());
        assertTrue(summary.getOrders().isEmpty());
        assertTrue(summary.getSamples().isEmpty());
        assertEquals(0, summary.getMetrics().getTotalOrders());
        assertFalse(summary.getMetrics().getStoredSamples() > 0);
    }

    @Test
    public void largeHistoryKeepsEveryOrderSampleResultAndDocumentWithoutRepeatedReads() {
        List<Analysis> analyses = prepareLargeHistory(16);
        PatientHistorySummary summary = service.getSummary("77");
        assertEquals(16, summary.getOrders().size());
        assertEquals(32, summary.getSamples().size());
        assertEquals(64, summary.getResults().size());
        assertEquals(64, summary.getMetrics().getTotalTests());
        assertEquals(32, summary.getMetrics().getCompletedTests());
        assertEquals(32, summary.getMetrics().getPendingTests());
        for (var result : summary.getResults()) {
            assertEquals("value-" + result.getId(), result.getResultValue());
            assertEquals("value-" + result.getId(), result.getResultValues().get(0).getValue());
            assertEquals(2, result.getAdditionalFieldDefinitions().size());
            assertTrue(result.getAdditionalFieldValues().get("document").contains("file-" + result.getId()));
            assertEquals(1, result.getSampleAdditionalFields().size());
            assertTrue(result.getSampleOrderFields().stream()
                    .anyMatch(f -> "attachment".equals(f.getKey()) && f.getValue().contains("order-")));
        }
        verify(sampleTypeAdditionalFieldService, times(1)).getFieldsForSampleType("1", false);
        verify(resultsLoadUtilityProvider, times(1)).getObject();
        verify(analysisService, never()).getAnalysesBySampleItem(any());
        for (Analysis analysis : analyses) {
            verify(resultService, times(1)).getResultsByAnalysis(analysis);
        }
    }

    @Test
    public void subsequentHistoryRequestReloadsDefinitionsAndSavedValues() {
        List<Analysis> analyses = prepareLargeHistory(2);
        service.getSummary("77");
        Result replacement = new Result();
        replacement.setValue("corrected-value");
        when(resultService.getResultsByAnalysis(analyses.get(0))).thenReturn(List.of(replacement));
        PatientHistorySummary updated = service.getSummary("77");
        assertEquals("corrected-value", updated.getResults().stream()
                .filter(r -> r.getId().equals(analyses.get(0).getId())).findFirst().get().getResultValue());
        verify(sampleTypeAdditionalFieldService, times(2)).getFieldsForSampleType("1", false);
        verify(resultsLoadUtilityProvider, times(2)).getObject();
    }

    @Test
    public void historySendsDocumentMetadataWithoutEmbeddingOrChangingTheSavedPdf() throws Exception {
        var analyses = prepareLargeHistory(1);
        var definition = new TestAdditionalFieldPayload();
        definition.setFieldKey("document");
        definition.setFieldType("DOCUMENT");
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        String original = mapper.writeValueAsString(Map.of("fileName", "saved.pdf", "fileType", "application/pdf",
                "fileSize", 750000, "base64Content", "A".repeat(1000000)));
        var configured = new TestResultItem();
        configured.setAnalysisId(analyses.get(0).getId());
        configured.setAdditionalFieldDefinitions(List.of(definition));
        configured.setAdditionalFieldValues(Map.of("document", original));
        var loader = mock(ResultsLoadUtility.class);
        when(loader.getGroupedTestsForHistory(any())).thenReturn(List.of(configured));
        when(resultsLoadUtilityProvider.getObject()).thenReturn(loader);
        var result = service.getSummary("77").getResults().stream()
                .filter(r -> r.getId().equals(analyses.get(0).getId())).findFirst().get();
        String metadata = result.getAdditionalFieldValues().get("document");
        assertTrue(metadata.length() < 150);
        assertEquals("saved.pdf", mapper.readTree(metadata).path("fileName").asText());
        assertEquals("application/pdf", mapper.readTree(metadata).path("fileType").asText());
        assertEquals(750000, mapper.readTree(metadata).path("fileSize").asInt());
        assertFalse(mapper.readTree(metadata).has("base64Content"));
        assertEquals(original, configured.getAdditionalFieldValues().get("document"));
    }

    @Test
    public void orderLookupFailureFallsBackToItemsWithoutLosingResults() {
        prepareLargeHistory(1);
        when(analysisService.getAnalysesBySampleId(anyString())).thenThrow(new IllegalStateException("lookup failed"));
        assertEquals(4, service.getSummary("77").getResults().size());
        verify(analysisService, times(2)).getAnalysesBySampleItem(any());
    }

    @Test
    public void concurrentHistoryRequestsKeepSeparateReadContexts() throws Exception {
        prepareLargeHistory(2);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Callable<PatientHistorySummary>> requests = new ArrayList<>();
            for (int i = 0; i < 8; i++)
                requests.add(() -> service.getSummary("77"));
            for (var response : executor.invokeAll(requests)) {
                assertEquals(8, response.get().getResults().size());
            }
            verify(resultsLoadUtilityProvider, times(8)).getObject();
            verify(sampleTypeAdditionalFieldService, times(8)).getFieldsForSampleType("1", false);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void sharedOrderCatalogsNeverCopyAnotherOrdersAnswersOrDocuments() {
        var definition = new OrderAdditionalFieldPayload();
        definition.setFieldKey("note");
        definition.setFieldType("TEXT");
        definition.setDefaultValue("default note");
        var catalog = new SampleOrderItem();
        catalog.setAdditionalFields(List.of(definition));
        catalog.setAdditionalFieldValues(Map.of("note", "another patient's answer"));
        var file = new OrderAdditionalFieldFilePayload();
        file.setFileName("another-patient.pdf");
        catalog.setAdditionalFieldFiles(Map.of("attachment", file));
        var prototype = new SampleOrderService();
        var first = prototype.getSampleOrderItem(catalog);
        var second = prototype.getSampleOrderItem(catalog);
        assertTrue(first != second);
        assertEquals("default note", first.getAdditionalFieldValues().get("note"));
        assertTrue(first.getAdditionalFieldFiles().isEmpty());
        first.getAdditionalFieldValues().put("note", "changed");
        assertEquals("default note", second.getAdditionalFieldValues().get("note"));
        assertEquals("another patient's answer", catalog.getAdditionalFieldValues().get("note"));
    }

    @Test
    public void resultDefinitionReuseIsLimitedToOneHistoryPrototype() {
        var definitions = mock(org.openelisglobal.testadditionalfield.service.TestAdditionalFieldService.class);
        when(definitions.getFieldsForTest("1", false, true)).thenReturn(List.of(new TestAdditionalFieldPayload()));
        var loader = new ResultsLoadUtility();
        ReflectionTestUtils.setField(loader, "testAdditionalFieldService", definitions);
        ReflectionTestUtils.invokeMethod(loader, "getAdditionalFieldsForTest", "1");
        loader.getGroupedTestsForHistory(List.of());
        ReflectionTestUtils.invokeMethod(loader, "getAdditionalFieldsForTest", "1");
        verify(definitions, times(1)).getFieldsForTest("1", false, true);
        // Other result-entry callers retain their existing cache-reset behavior.
        loader.getGroupedTestsForAnalysisList(List.of(), true);
        ReflectionTestUtils.invokeMethod(loader, "getAdditionalFieldsForTest", "1");
        verify(definitions, times(2)).getFieldsForTest("1", false, true);
        var nextRequest = new ResultsLoadUtility();
        ReflectionTestUtils.setField(nextRequest, "testAdditionalFieldService", definitions);
        ReflectionTestUtils.invokeMethod(nextRequest, "getAdditionalFieldsForTest", "1");
        verify(definitions, times(3)).getFieldsForTest("1", false, true);
    }

    public List<Analysis> prepareLargeHistory(int orderCount) {
        Patient patient = new Patient();
        patient.setId("77");
        when(patientService.get("77")).thenReturn(patient);
        List<Sample> samples = new ArrayList<>();
        List<Analysis> allAnalyses = new ArrayList<>();
        Map<String, TestResultItem> configured = new HashMap<>();
        Map<String, SampleOrderItem> forms = new HashMap<>();
        var numeric = new TestAdditionalFieldPayload();
        numeric.setId(1);
        numeric.setFieldKey("volume");
        numeric.setFieldType("NUMBER");
        numeric.setDisplayName("Volume");
        var document = new TestAdditionalFieldPayload();
        document.setId(2);
        document.setFieldKey("document");
        document.setFieldType("DOCUMENT");
        document.setDisplayName("Document");
        var sampleField = new SampleTypeAdditionalFieldPayload();
        sampleField.setFieldKey("note");
        sampleField.setDisplayName("Sample note");
        sampleField.setFieldType("TEXT");
        sampleField.setActive(true);
        when(sampleTypeAdditionalFieldService.getFieldsForSampleType("1", false)).thenReturn(List.of(sampleField));
        when(sampleTypeAdditionalFieldService.getFieldValuesForSampleItem(eq("1"), anyString()))
                .thenAnswer(i -> Map.of("note", "note-" + i.getArgument(1)));
        when(resultService.getResultValue(any(Result.class), eq(false)))
                .thenAnswer(i -> ((Result) i.getArgument(0)).getValue());
        when(statusService.matches("finalized", AnalysisStatus.Finalized)).thenReturn(true);
        when(resultsLoadUtilityProvider.getObject()).thenAnswer(invocation -> {
            var loader = mock(ResultsLoadUtility.class);
            when(loader.getGroupedTestsForHistory(any())).thenAnswer(
                    i -> ((List<Analysis>) i.getArgument(0)).stream().map(a -> configured.get(a.getId())).toList());
            lenient().when(loader.getGroupedTestsForAnalysisList(any(), eq(true))).thenAnswer(
                    i -> ((List<Analysis>) i.getArgument(0)).stream().map(a -> configured.get(a.getId())).toList());
            return loader;
        });
        when(sampleOrderServiceProvider.getObject()).thenAnswer(invocation -> {
            var order = mock(SampleOrderService.class);
            var current = new AtomicReference<Sample>();
            org.mockito.Mockito.doAnswer(i -> {
                current.set(i.getArgument(0));
                return null;
            }).when(order).setSample(any());
            lenient().when(order.getSampleOrderItem()).thenAnswer(i -> forms.get(current.get().getId()));
            lenient().when(order.getSampleOrderItem(any())).thenAnswer(i -> forms.get(current.get().getId()));
            return order;
        });
        for (int n = 0; n < orderCount; n++) {
            Sample sample = new Sample();
            sample.setId(String.valueOf(501 + n));
            sample.setAccessionNumber("LAB-" + n);
            sample.setClinicalOrderId("ORD-" + n);
            sample.setPriority(OrderPriority.ROUTINE);
            sample.setReceivedDateForDisplay("07/10/2026");
            sample.setReceivedTimestamp(Timestamp.valueOf("2026-07-10 10:00:00"));
            samples.add(sample);
            var form = new SampleOrderItem();
            form.setSampleId(sample.getId());
            form.setPriority(OrderPriority.ROUTINE);
            var priority = new OrderFixedFieldConfigPayload();
            priority.setFieldKey("priority");
            priority.setVisible(true);
            form.setFixedFieldConfigs(List.of(priority));
            var attachment = new OrderAdditionalFieldPayload();
            attachment.setFieldKey("attachment");
            attachment.setFieldType("DOCUMENT");
            attachment.setDisplayName("Order attachment");
            attachment.setActive(true);
            form.setAdditionalFields(List.of(attachment));
            var file = new OrderAdditionalFieldFilePayload();
            file.setFileName("order-" + n + ".pdf");
            file.setFileType("application/pdf");
            form.setAdditionalFieldFiles(Map.of("attachment", file));
            forms.put(sample.getId(), form);
            List<SampleItem> items = new ArrayList<>();
            List<Analysis> orderAnalyses = new ArrayList<>();
            for (int m = 0; m < 2; m++) {
                var item = mock(SampleItem.class);
                String itemId = String.valueOf(9001 + n * 2 + m);
                when(item.getId()).thenReturn(itemId);
                when(item.getTypeOfSampleId()).thenReturn("1");
                when(item.getCugCode()).thenReturn("CUG-" + itemId);
                items.add(item);
                List<Analysis> itemAnalyses = new ArrayList<>();
                for (int t = 0; t < 2; t++) {
                    Analysis analysis = new Analysis();
                    analysis.setId(String.valueOf(10001 + n * 4 + m * 2 + t));
                    analysis.setSampleItem(item);
                    analysis.setStatusId(t == 0 ? "finalized" : "pending");
                    var test = new org.openelisglobal.test.valueholder.Test();
                    test.setId(String.valueOf(t + 1));
                    test.setDescription("Test-" + t);
                    var name = new org.openelisglobal.localization.valueholder.Localization();
                    name.setEnglish("Test-" + t);
                    test.setLocalizedTestName(name);
                    analysis.setTest(test);
                    orderAnalyses.add(analysis);
                    itemAnalyses.add(analysis);
                    allAnalyses.add(analysis);
                    var result = new Result();
                    result.setValue("value-" + analysis.getId());
                    result.setResultType("N");
                    when(resultService.getResultsByAnalysis(analysis)).thenReturn(List.of(result));
                    var config = new TestResultItem();
                    config.setAnalysisId(analysis.getId());
                    config.setResultType("N");
                    config.setResultName("Test-" + t);
                    config.setAdditionalFieldDefinitions(List.of(numeric, document));
                    config.setAdditionalFieldValues(
                            Map.of("volume", "10", "document", "{\"fileName\":\"file-" + analysis.getId() + ".pdf\"}"));
                    configured.put(analysis.getId(), config);
                }
                lenient().when(analysisService.getAnalysesBySampleItem(item)).thenReturn(itemAnalyses);
                when(sampleStorageService.getSampleItemLocation(itemId))
                        .thenReturn(Map.of("hierarchicalPath", "Rack > Box > " + itemId));
            }
            when(sampleItemDAO.getSampleItemsBySampleId(sample.getId())).thenReturn(items);
            when(sampleItemDAO.getSampleItemsWithHierarchy(items.stream().map(SampleItem::getId).toList()))
                    .thenReturn(items);
            when(analysisService.getAnalysesBySampleId(sample.getId())).thenReturn(orderAnalyses);
        }
        when(sampleHumanService.getSamplesForPatient("77")).thenReturn(samples);
        return allAnalyses;
    }
}
