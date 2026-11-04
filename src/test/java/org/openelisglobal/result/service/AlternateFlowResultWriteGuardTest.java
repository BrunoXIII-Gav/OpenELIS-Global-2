package org.openelisglobal.result.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Test;
import org.openelisglobal.common.services.registration.interfaces.IResultUpdate;
import org.openelisglobal.orderadditionalfield.service.AlternateOrderFlowService;
import org.openelisglobal.result.action.util.ResultsUpdateDataSet;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

public class AlternateFlowResultWriteGuardTest {
    @Test
    public void forbiddenTestCannotReachResultPreparationOrSampleUsageChanges() {
        var service = new LogbookPersistServiceImpl();
        var workflow = mock(AlternateOrderFlowService.class);
        when(workflow.isAnalysisProcessingBlocked("80")).thenReturn(true);
        ReflectionTestUtils.setField(service, "alternateOrderFlowService", workflow);
        var data = mock(ResultsUpdateDataSet.class);
        var item = new TestResultItem();
        item.setAnalysisId("80");
        when(data.getModifiedItems()).thenReturn(List.of(item));
        when(data.getAnalysisOnlyChangeResults()).thenReturn(List.of());
        var preparation = mock(java.util.function.Consumer.class);
        assertThrows(AccessDeniedException.class,
                () -> service.prepareAndPersistDataSet(data, List.<IResultUpdate>of(), "42", preparation));
        verifyZeroInteractions(preparation);
    }
}
