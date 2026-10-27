package org.openelisglobal.sampleitem.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
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
import org.openelisglobal.common.services.StatusService;
import org.openelisglobal.orderadditionalfield.service.AlternateOrderFlowService;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.form.AddTestsForm;
import org.openelisglobal.sampleitem.form.CancelTestForm;
import org.openelisglobal.sampleitem.form.SaveSampleManagementChangesForm;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.service.TestService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SampleManagementAlternateOrderFlowTest {
    @InjectMocks
    private SampleManagementServiceImpl service;
    @Mock
    private SampleService samples;
    @Mock
    private SampleItemDAO items;
    @Mock
    private SampleItemService sampleItems;
    @Mock
    private SampleHumanService patients;
    @Mock
    private AnalysisService analyses;
    @Mock
    private TestService tests;
    @Mock
    private AlternateOrderFlowService workflow;
    @Mock
    private ObjectProvider<SampleOrderService> orderProvider;
    @Mock
    private SampleManagementAuthorizationService authorization;
    private Object previousFactory;
    private SampleItem item;
    private Analysis analysis;

    @Before
    public void setup() {
        previousFactory = ReflectionTestUtils.getField(SpringContext.class, "factory");
        var factory = mock(AutowireCapableBeanFactory.class);
        var status = mock(IStatusService.class);
        when(factory.getBean(IStatusService.class)).thenReturn(status);
        when(status.getStatusID(StatusService.AnalysisStatus.Canceled)).thenReturn("99");
        when(status.getStatusID(StatusService.SampleStatus.Canceled)).thenReturn("88");
        ReflectionTestUtils.setField(SpringContext.class, "factory", factory);

        var sample = new Sample();
        sample.setId("81");
        sample.setAccessionNumber("LNG81");
        item = new SampleItem();
        item.setId("55");
        item.setSample(sample);
        item.setCugCode("5008.1");
        item.setExternalId("LNG81-1");
        when(samples.getSampleByAccessionNumber("LNG81")).thenReturn(sample);
        when(items.getSampleItemsBySampleId("81")).thenReturn(List.of(item));
        when(items.getSampleItemsWithHierarchy(anyList())).thenReturn(List.of(item));
        when(sampleItems.getData("55")).thenReturn(item);
        when(workflow.isAlternateOrderFlow("81")).thenReturn(true);
        var order = mock(SampleOrderService.class);
        when(orderProvider.getObject()).thenReturn(order);
        when(order.getSampleOrderItem()).thenReturn(new SampleOrderItem());
        when(authorization.getAccess("42")).thenReturn(SampleManagementAccess.fullAccess());
        analysis = new Analysis();
        analysis.setId("80");
        analysis.setSampleItem(item);
        analysis.setStatusId("1");
        var test = mock(org.openelisglobal.test.valueholder.Test.class);
        when(test.getId()).thenReturn("30");
        when(test.getName()).thenReturn("ADN");
        analysis.setTest(test);
        when(analyses.getAnalysesBySampleItem(item)).thenReturn(List.of(analysis));
        when(analyses.getAnalysisById("80")).thenReturn(analysis);
        when(tests.getTestById("30")).thenReturn(test);
    }

    @After
    public void restoreContext() {
        ReflectionTestUtils.setField(SpringContext.class, "factory", previousFactory);
    }

    @Test
    public void searchMarksConvertedOrderAsAlternateAndKeepsCugAndHistoricalAnalyses() {
        var dto = service.searchByAccessionNumber("LNG81", true).getSampleItems().get(0);
        assertTrue(dto.isAlternateOrderFlow());
        assertFalse(dto.isEditable());
        assertEquals("5008.1", dto.getCugCode());
        assertEquals(1, dto.getOrderedTests().size());
        assertEquals("80", dto.getOrderedTests().get(0).getAnalysisId());
        assertTrue(new ObjectMapper().valueToTree(dto).get("alternateOrderFlow").asBoolean());
        verify(analyses, never()).update(any(Analysis.class));
        verify(analyses, never()).delete(any(Analysis.class));
    }

    @Test
    public void reloadingAfterLeavingAlternateFlowRestoresEditingWithoutRecreatingAnalyses() {
        when(workflow.isAlternateOrderFlow("81")).thenReturn(true, false);
        assertFalse(service.searchByAccessionNumber("LNG81", true).getSampleItems().get(0).isEditable());
        var normal = service.searchByAccessionNumber("LNG81", true).getSampleItems().get(0);
        assertFalse(normal.isAlternateOrderFlow());
        assertTrue(normal.isEditable());
        assertEquals("80", normal.getOrderedTests().get(0).getAnalysisId());
        verify(analyses, never()).insert(any(Analysis.class));
    }

    @Test
    public void historicalTestsStillRespectFieldRestrictions() {
        var dto = service.searchByAccessionNumber("LNG81", true, Set.of("tests-and-results")).getSampleItems().get(0);
        assertTrue(dto.isAlternateOrderFlow());
        assertTrue(dto.getOrderedTests().isEmpty());
        assertEquals("1", analysis.getStatusId());
    }

    @Test
    public void alternateOrderWithNoAnalysesAlsoHasTheExplicitWorkflowFlag() {
        when(analyses.getAnalysesBySampleItem(item)).thenReturn(List.of());
        var dto = service.searchByAccessionNumber("LNG81", true).getSampleItems().get(0);
        assertTrue(dto.isAlternateOrderFlow());
        assertTrue(dto.getOrderedTests().isEmpty());
        assertFalse(dto.isEditable());
    }

    @Test
    public void staleScreenCannotAddTestsToAnAlternateOrder() {
        var error = assertThrows(IllegalStateException.class,
                () -> service.addTestsToSamples(new AddTestsForm(List.of("55"), List.of("30")), "42"));
        assertEquals("sample.management.alternateOrderFlow.processingBlocked", error.getMessage());
        verify(analyses, never()).insert(any(Analysis.class));
    }

    @Test
    public void staleScreenCannotCancelHistoricalAnalyses() {
        assertThrows(IllegalStateException.class, () -> service.cancelTest(new CancelTestForm("80", "55"), "42"));
        assertEquals("1", analysis.getStatusId());
        verify(analyses, never()).update(any(Analysis.class));
    }

    @Test
    public void staleScreenCannotSaveProcessingDataOrRemoveAnAlternateSample() {
        var update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setSampleItemId("55");
        update.setQuantity("9");
        update.setRemoveSample(true);
        var form = new SaveSampleManagementChangesForm();
        form.setSampleUpdates(List.of(update));
        var error = assertThrows(IllegalStateException.class, () -> service.saveSampleManagementChanges(form, "42"));
        assertEquals("sample.management.alternateOrderFlow.processingBlocked", error.getMessage());
        assertEquals("5008.1", item.getCugCode());
        assertNull(item.getQuantity());
        verify(sampleItems, never()).update(any(SampleItem.class));
        verify(analyses, never()).update(any(Analysis.class));
    }
}
