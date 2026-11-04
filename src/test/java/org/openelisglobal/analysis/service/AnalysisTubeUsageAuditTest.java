package org.openelisglobal.analysis.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import org.junit.Test;
import org.openelisglobal.analysis.dao.AnalysisTubeUsageDAO;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.analysis.valueholder.AnalysisTubeUsage;
import org.springframework.test.util.ReflectionTestUtils;

public class AnalysisTubeUsageAuditTest {
    @Test
    public void unchangedTubeUseKeepsItsOriginalPerformer() {
        var dao = mock(AnalysisTubeUsageDAO.class);
        var service = new AnalysisTubeUsageServiceImpl();
        ReflectionTestUtils.setField(service, "analysisTubeUsageDAO", dao);
        Analysis child = analysis("100");
        Analysis parent = analysis("101");
        AnalysisTubeUsage previous = usage(child, parent);
        when(dao.getByAnalysisId("100")).thenReturn(List.of(previous));
        service.replaceUsages(child, parent,
                List.of(new AnalysisTubeUsageService.UsageInput("child", "parent", new BigDecimal("1.000"))), "12");
        assertEquals(Integer.valueOf(11), previous.getPerformedByUser());
        verify(dao, never()).update(any());
        verify(dao, never()).delete(any(AnalysisTubeUsage.class));
    }

    @Test
    public void changedTubeUseUpdatesTheSameRecord() {
        var dao = mock(AnalysisTubeUsageDAO.class);
        var service = new AnalysisTubeUsageServiceImpl();
        ReflectionTestUtils.setField(service, "analysisTubeUsageDAO", dao);
        Analysis child = analysis("100");
        Analysis parent = analysis("101");
        AnalysisTubeUsage previous = usage(child, parent);
        when(dao.getByAnalysisId("100")).thenReturn(List.of(previous));
        service.replaceUsages(child, parent,
                List.of(new AnalysisTubeUsageService.UsageInput("child", "parent", new BigDecimal("2"))), "12");
        assertEquals(Long.valueOf(1), previous.getId());
        assertEquals(Integer.valueOf(12), previous.getPerformedByUser());
        verify(dao).update(previous);
        verify(dao, never()).delete(any(AnalysisTubeUsage.class));
    }

    private Analysis analysis(String id) {
        Analysis result = new Analysis();
        result.setId(id);
        return result;
    }

    private AnalysisTubeUsage usage(Analysis child, Analysis parent) {
        var result = new AnalysisTubeUsage();
        result.setId(1L);
        result.setAnalysis(child);
        result.setParentAnalysis(parent);
        result.setChildBlockName("child");
        result.setParentTubeBlockName("parent");
        result.setUsedQuantity(BigDecimal.ONE);
        result.setPerformedByUser(11);
        result.setSysUserId("11");
        return result;
    }
}
