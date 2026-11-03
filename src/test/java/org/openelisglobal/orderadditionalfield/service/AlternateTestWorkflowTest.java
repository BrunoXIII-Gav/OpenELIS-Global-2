package org.openelisglobal.orderadditionalfield.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldValueDAO;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.springframework.test.util.ReflectionTestUtils;

public class AlternateTestWorkflowTest {
    private AlternateOrderFlowServiceImpl workflow;
    private Analysis allowed;
    private Analysis blocked;
    private Analysis normal;

    @Before
    public void setup() {
        workflow = new AlternateOrderFlowServiceImpl();
        var analyses = mock(AnalysisService.class);
        var fields = mock(OrderAdditionalFieldService.class);
        var values = mock(SampleOrderAdditionalFieldValueDAO.class);
        ReflectionTestUtils.setField(workflow, "analysisService", analyses);
        ReflectionTestUtils.setField(workflow, "orderAdditionalFieldService", fields);
        ReflectionTestUtils.setField(workflow, "sampleOrderAdditionalFieldValueDAO", values);
        var definition = new OrderAdditionalFieldPayload();
        definition.setId(1);
        definition.setFieldKey("alternate");
        definition.setFieldType("BOOLEAN");
        definition.setMetadataJson("{\"workflow\":{\"alternateOrderFlow\":true}}");
        when(fields.getFields(false)).thenReturn(List.of(definition));
        var value = new SampleOrderAdditionalFieldValue();
        value.setSampleId(44);
        value.setFieldDefinitionId(1);
        value.setFieldValue("true");
        when(values.findBySampleIdsAndFieldDefinitionIds(anyList(), anyList()))
                .thenAnswer(call -> ((List<?>) call.getArgument(0)).contains(44) ? List.of(value) : List.of());
        allowed = analysis("80", "44", true);
        blocked = analysis("81", "44", false);
        normal = analysis("82", "45", true);
        when(analyses.get("80")).thenReturn(allowed);
        when(analyses.get("81")).thenReturn(blocked);
        when(analyses.get("82")).thenReturn(normal);
    }

    @Test
    public void exceptionChangesTestProcessingButNeverOrderClassificationOrValidation() {
        assertTrue(workflow.isAlternateOrderFlow("44"));
        assertFalse(workflow.isAnalysisProcessingBlocked("80"));
        assertTrue(workflow.isResultsOnlyAnalysis("80"));
        assertTrue(workflow.isAnalysisValidationBlocked("80"));
        assertTrue(workflow.isAnalysisProcessingBlocked("81"));
        assertFalse(workflow.isResultsOnlyAnalysis("81"));
        assertTrue(workflow.isAnalysisValidationBlocked("81"));
        assertTrue(workflow.isAlternateOrderFlow("44"));
        assertEquals(Set.of("81"), workflow.getProcessingBlockedAnalysisIds(List.of(allowed, blocked, normal)));
    }

    @Test
    public void normalOrdersKeepTheirValidationWorkflowDespiteTheNewFlag() {
        assertFalse(workflow.isAlternateOrderFlow("45"));
        assertFalse(workflow.isAnalysisProcessingBlocked("82"));
        assertFalse(workflow.isResultsOnlyAnalysis("82"));
        assertFalse(workflow.isAnalysisValidationBlocked("82"));
    }

    @Test
    public void missingAnalysesCannotAuthorizeProcessingOrValidation() {
        assertTrue(workflow.isAnalysisProcessingBlocked("missing"));
        assertTrue(workflow.isAnalysisValidationBlocked("missing"));
    }

    private Analysis analysis(String id, String sampleId, boolean exception) {
        var sample = new Sample();
        sample.setId(sampleId);
        var item = new SampleItem();
        item.setId(id);
        item.setSample(sample);
        var test = new org.openelisglobal.test.valueholder.Test();
        test.setAllowProcessingInAlternateFlow(exception);
        var analysis = new Analysis();
        analysis.setId(id);
        analysis.setSampleItem(item);
        analysis.setTest(test);
        return analysis;
    }
}
