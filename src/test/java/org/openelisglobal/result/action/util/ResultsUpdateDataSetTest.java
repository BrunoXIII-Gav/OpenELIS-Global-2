package org.openelisglobal.result.action.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

public class ResultsUpdateDataSetTest {

    private AutowireCapableBeanFactory originalFactory;

    @Before
    public void setUp() throws Exception {
        Field factoryField = SpringContext.class.getDeclaredField("factory");
        factoryField.setAccessible(true);
        originalFactory = (AutowireCapableBeanFactory) factoryField.get(null);
        AutowireCapableBeanFactory factory = mock(AutowireCapableBeanFactory.class);
        when(factory.getBean(eq(ResultsValidation.class))).thenReturn(new ResultsValidation());
        when(factory.getBean(any(Class.class))).thenReturn(null);
        factoryField.set(null, factory);
    }

    @After
    public void tearDown() throws Exception {
        Field factoryField = SpringContext.class.getDeclaredField("factory");
        factoryField.setAccessible(true);
        factoryField.set(null, originalFactory);
    }

    @Test
    public void filterModifiedItems_shouldIncludeLabelOnlyEdits() {
        ResultsUpdateDataSet dataSet = new ResultsUpdateDataSet("");

        TestResultItem item = new TestResultItem();
        item.setAnalysisId("analysis-1");
        item.setIsModified(true);
        Map<String, String> tubeLabels = new HashMap<>();
        tubeLabels.put("Official", "1.4.4");
        item.setTubeLabels(tubeLabels);

        dataSet.filterModifiedItems(List.of(item));

        assertEquals(1, dataSet.getModifiedItems().size());
        assertEquals("1.4.4", dataSet.getModifiedItems().get(0).getTubeLabels().get("Official"));
        assertTrue(dataSet.getAnalysisOnlyChangeResults().isEmpty());
    }
}
