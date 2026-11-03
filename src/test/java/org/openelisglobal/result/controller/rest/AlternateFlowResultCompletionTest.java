package org.openelisglobal.result.controller.rest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.orderadditionalfield.service.AlternateOrderFlowService;
import org.openelisglobal.referral.service.ReferralTypeService;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

public class AlternateFlowResultCompletionTest {
    private Object previousFactory;
    private LogbookResultsRestController controller;
    private TestResultItem item;
    private AlternateOrderFlowService workflow;

    @Before
    public void setup() {
        previousFactory = ReflectionTestUtils.getField(SpringContext.class, "factory");
        var factory = mock(AutowireCapableBeanFactory.class);
        var statuses = mock(IStatusService.class);
        when(factory.getBean(IStatusService.class)).thenReturn(statuses);
        when(statuses.getStatusID(any(AnalysisStatus.class))).thenAnswer(call -> call.getArgument(0).toString());
        ReflectionTestUtils.setField(SpringContext.class, "factory", factory);
        controller = new LogbookResultsRestController(mock(ReferralTypeService.class));
        workflow = mock(AlternateOrderFlowService.class);
        when(workflow.isResultsOnlyAnalysis("80")).thenReturn(true);
        ReflectionTestUtils.setField(controller, "alternateOrderFlowService", workflow);
        item = new TestResultItem();
        item.setAnalysisId("80");
        item.setResultType("N");
    }

    @After
    public void restore() {
        ReflectionTestUtils.setField(SpringContext.class, "factory", previousFactory);
    }

    @Test
    public void completeResultsFinalizeWithoutParentDependencyOrValidationEvenWhenAlwaysValidateIsEnabled() {
        item.setShadowResultValue("12");
        item.setTestId("30");
        assertEquals("Finalized", status(true));
    }

    @Test
    public void emptyAndIncompleteRequiredResultsRemainPending() {
        assertEquals("NotStarted", status(true));
        var required = new TestAdditionalFieldPayload();
        required.setFieldKey("measurement");
        required.setActive(true);
        required.setRequired(true);
        required.setIncludeInValidation(false);
        item.setAdditionalFieldDefinitions(List.of(required));
        item.setShadowResultValue("12");
        assertEquals("NotStarted", status(true));
        item.setAdditionalFieldValues(Map.of("measurement", "7"));
        assertEquals("Finalized", status(true));
    }

    @Test
    public void rejectingAnExceptionDoesNotSendItToValidation() {
        item.setShadowRejected(true);
        assertEquals("Canceled", status(true));
    }

    @Test
    public void requiredPrimaryResultMustBeFilledButDisabledPrimaryResultsDoNotBlockAdditionalOnlyTests() {
        var field = new TestAdditionalFieldPayload();
        field.setFieldKey("extra");
        field.setActive(true);
        field.setRequired(true);
        item.setAdditionalFieldDefinitions(List.of(field));
        item.setAdditionalFieldValues(Map.of("extra", "7"));
        assertEquals("NotStarted", status(true));
        item.setResultDisplayConfigJson("{\"active\":false}");
        assertEquals("Finalized", status(true));
    }

    @Test
    public void normalOrdersRetainTheExistingParentCompletionBehaviorWithEitherExceptionSetting() {
        when(workflow.isResultsOnlyAnalysis("80")).thenReturn(false);
        var test = new org.openelisglobal.test.valueholder.Test(); test.setId("30");
        var tests = mock(org.openelisglobal.test.service.TestService.class);
        when(tests.get("30")).thenReturn(test);
        var dependencies = mock(org.openelisglobal.testdependency.service.TestParentChildDependencyService.class);
        var dependency = new org.openelisglobal.testdependency.valueholder.TestParentChildDependency();
        dependency.setActive(true);
        when(dependencies.getByParentTestId("30")).thenReturn(List.of(dependency));
        ReflectionTestUtils.setField(controller, "testService", tests);
        ReflectionTestUtils.setField(controller, "testParentChildDependencyService", dependencies);
        item.setTestId("30"); item.setShadowResultValue("12");
        for (boolean exception : new boolean[] { false, true }) {
            test.setAllowProcessingInAlternateFlow(exception);
            test.setSkipValidationWhenParentComplete(false);
            assertEquals("TechnicalAcceptance", status(true));
            test.setSkipValidationWhenParentComplete(true);
            assertEquals("Finalized", status(true));
        }
    }

    private String status(boolean alwaysValidate) {
        return ReflectionTestUtils.invokeMethod(controller, "getStatusForTestResult", item, alwaysValidate);
    }
}
