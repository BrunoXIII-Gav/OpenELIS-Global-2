package org.openelisglobal.testconfiguration.packageio;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.testconfiguration.packageio.service.TestConfigurationCatalogRefreshService;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.event.*;

public class TestConfigurationCatalogRefreshTest {
    @Test
    public void refreshesNamesAndDestinationCatalogsOnlyAfterCommit() throws Exception {
        TestConfigurationCatalogRefreshService listener = new TestConfigurationCatalogRefreshService();
        TestService tests = mock(TestService.class);
        TypeOfSampleService samples = mock(TypeOfSampleService.class);
        DisplayListService lists = mock(DisplayListService.class);
        ReflectionTestUtils.setField(listener, "testService", tests);
        ReflectionTestUtils.setField(listener, "sampleTypeService", samples);
        ReflectionTestUtils.setField(listener, "displayListService", lists);
        assertEquals(TransactionPhase.AFTER_COMMIT,
                listener.getClass().getMethod("imported", TestConfigurationCatalogRefreshService.Imported.class)
                        .getAnnotation(TransactionalEventListener.class).phase());
        listener.imported(new TestConfigurationCatalogRefreshService.Imported());
        verify(tests).refreshTestNames();
        verify(samples).clearCache();
        verify(lists).refreshList(DisplayListService.ListType.UNIT_OF_MEASURE);
        verify(lists).refreshList(DisplayListService.ListType.ALL_TESTS);
        verify(lists).refreshList(DisplayListService.ListType.DICTIONARY_TEST_RESULTS);
        verify(lists, never()).refreshList(DisplayListService.ListType.PATIENT_SEARCH_CRITERIA);
    }
}
