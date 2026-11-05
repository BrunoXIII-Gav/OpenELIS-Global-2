package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.dataexchange.orderresult.OrderResponseWorker.Event;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.sample.exception.OrderTestHasResultsException;
import org.openelisglobal.testadditionalfield.dao.AnalysisAdditionalFieldValueDAO;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderTestRemovalServiceTest {
    private OrderTestRemovalServiceImpl service;
    private ResultService results;
    private AnalysisAdditionalFieldValueDAO fields;
    private Analysis analysis;

    @Before
    public void setup() {
        service = new OrderTestRemovalServiceImpl();
        results = mock(ResultService.class);
        fields = mock(AnalysisAdditionalFieldValueDAO.class);
        ReflectionTestUtils.setField(service, "resultService", results);
        ReflectionTestUtils.setField(service, "additionalValues", fields);
        analysis = new Analysis();
        analysis.setId("80");
    }

    @Test
    public void noSavedValuesAllowsRemoval() {
        assertFalse(service.hasResults(analysis));
        service.validateRemoval(List.of(analysis));
        verify(fields).lockAnalyses(List.of("80"));
    }

    @Test
    public void numericZeroAndValuesInAnyMainResultPreventRemoval() {
        var empty = result("N", "  ");
        when(results.getResultsByAnalysis(analysis)).thenReturn(List.of(empty, result("N", "0")));
        assertTrue(service.hasResults(analysis));
        assertThrows(OrderTestHasResultsException.class, () -> service.validateRemoval(List.of(analysis)));
    }

    @Test
    public void emptyDictionaryAndMultiSelectPlaceholdersAreNotResults() {
        when(results.getResultsByAnalysis(analysis))
                .thenReturn(List.of(result("D", "0"), result("M", "{}"), result("M", "[]"), result("N", "")));
        assertFalse(service.hasResults(analysis));
        when(results.getResultsByAnalysis(analysis)).thenReturn(List.of(result("M", "{7}")));
        assertTrue(service.hasResults(analysis));
    }

    @Test
    public void additionalValuesIncludingZeroFalseAndDocumentsPreventRemoval() {
        for (String value : List.of("0", "false", "saved text", "{\"fileName\":\"result.pdf\"}")) {
            var row = new AnalysisAdditionalFieldValue();
            row.setFieldValue(value);
            when(fields.findByAnalysisId(80)).thenReturn(List.of(row));
            assertTrue(service.hasResults(analysis));
            assertThrows(OrderTestHasResultsException.class, () -> service.validateRemoval(List.of(analysis)));
        }
    }

    @Test
    public void aSavedMainResultDocumentPreventsRemovalWithoutATextResult() {
        var document = new org.openelisglobal.analysis.valueholder.ResultFile();
        document.setId(9);
        analysis.setResultFile(document);
        assertTrue(service.hasResults(analysis));
        assertThrows(OrderTestHasResultsException.class, () -> service.validateRemoval(List.of(analysis)));
    }

    @Test
    public void blankAdditionalRowsDoNotLockAnOtherwiseEmptyTest() {
        var row = new AnalysisAdditionalFieldValue();
        row.setFieldValue("  ");
        when(fields.findByAnalysisId(80)).thenReturn(List.of(row));
        assertFalse(service.hasResults(analysis));
    }

    @Test
    public void locksAllTargetsBeforeCheckingForAConcurrentlySavedResult() {
        var other = new Analysis();
        other.setId("81");
        when(results.getResultsByAnalysis(analysis)).thenReturn(List.of(result("N", "7")));
        assertThrows(OrderTestHasResultsException.class, () -> service.validateRemoval(List.of(other, analysis)));
        InOrder order = inOrder(fields, results);
        order.verify(fields).lockAnalyses(List.of("80", "81"));
        order.verify(results).getResultsByAnalysis(other);
        order.verify(fields).findByAnalysisId(81);
        order.verify(results).getResultsByAnalysis(analysis);
    }

    @Test
    public void cancellationNotificationPlaceholderIsNotARecordedResult() {
        var canceled = result("A", "cancel");
        canceled.setResultEvent(Event.TESTING_NOT_DONE);
        when(results.getResultsByAnalysis(analysis)).thenReturn(List.of(canceled));
        assertFalse(service.hasResults(analysis));
    }

    private Result result(String type, String value) {
        var result = new Result();
        result.setResultType(type);
        result.setValue(value);
        return result;
    }
}
