package org.openelisglobal.testconfiguration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Test;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.testconfiguration.service.TestModifyServiceImpl;
import org.openelisglobal.testdependency.service.TestParentChildDependencyService;
import org.openelisglobal.testdependency.valueholder.TestParentChildDependency;
import org.springframework.test.util.ReflectionTestUtils;

public class AlternateFlowTestConfigurationTest {
    @Test
    public void savingExceptionIsIndependentOfParentFlagAndCanBeDisabledAgain() {
        for (boolean parent : new boolean[] { false, true }) {
            var service = new TestModifyServiceImpl();
            var tests = mock(TestService.class);
            var dependencies = mock(TestParentChildDependencyService.class);
            ReflectionTestUtils.setField(service, "testService", tests);
            ReflectionTestUtils.setField(service, "testParentChildDependencyService", dependencies);
            var test = new org.openelisglobal.test.valueholder.Test();
            test.setId("30");
            when(tests.get("30")).thenReturn(test);
            var dependency = new TestParentChildDependency();
            dependency.setActive(true);
            when(dependencies.getByParentTestId("30")).thenReturn(parent ? List.of(dependency) : List.of());
            for (boolean exception : new boolean[] { true, false }) {
                ReflectionTestUtils.invokeMethod(service, "updateTestEntities", "30", "", "", "", "42", "0", "", false,
                        false, false, "Y", true, false, true, exception);
                assertEquals(exception, test.getAllowProcessingInAlternateFlow());
                assertEquals(parent, test.getSkipValidationWhenParentComplete());
            }
            verify(tests, times(2)).update(test);
        }
    }
}
