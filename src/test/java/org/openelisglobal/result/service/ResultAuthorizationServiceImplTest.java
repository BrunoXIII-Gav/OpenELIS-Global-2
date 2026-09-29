package org.openelisglobal.result.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.systemuser.service.UserService;
import org.openelisglobal.sampleitem.valueholder.SampleDataCompletionStatus;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.openelisglobal.test.service.TestService;

@RunWith(MockitoJUnitRunner.class)
public class ResultAuthorizationServiceImplTest {

    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;

    @Mock
    private UserService userService;

    @Mock
    private TestService testService;

    @Mock
    private AnalysisService analysisService;

    @InjectMocks
    private ResultAuthorizationServiceImpl resultAuthorizationService;

    @Before
    public void setUp() {
        AuthorizationDecision decision = new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false,
                Set.of("7"));
        when(moduleAuthorizationService.getAuthorization("17", "results", "enter")).thenReturn(decision);
        when(testService.getTestsByTestSectionIds(List.of(7))).thenReturn(List.of(test("allowed-test")));
    }

    @Test
    public void canAccessAllResults_usesThePersistedAnalysisInsteadOfSubmittedTestId() {
        TestResultItem item = new TestResultItem();
        item.setAnalysisId("42");
        item.setTestId("allowed-test");
        when(analysisService.get("42")).thenReturn(analysis("different-test"));

        assertFalse(resultAuthorizationService.canAccessAllResults("17", List.of(item), "enter", "Results"));
    }

    @Test
    public void canAccessAllResults_allowsAnAnalysisFromAnAuthorizedLaboratoryUnit() {
        TestResultItem item = new TestResultItem();
        item.setAnalysisId("42");
        when(analysisService.get("42")).thenReturn(analysis("allowed-test"));

        assertTrue(resultAuthorizationService.canAccessAllResults("17", List.of(item), "enter", "Results"));
    }

    @Test
    public void canAccessAllResults_rejectsAnIncompleteSample() {
        TestResultItem item = new TestResultItem();
        item.setAnalysisId("42");
        Analysis analysis = analysis("allowed-test");
        analysis.getSampleItem().setDataCompletionState(SampleDataCompletionStatus.PENDING_COMPLETION);
        when(analysisService.get("42")).thenReturn(analysis);

        assertFalse(resultAuthorizationService.canAccessAllResults("17", List.of(item), "enter", "Results"));
    }

    @Test
    public void filterResultsForEntryAccess_withoutReadReturnsOnlyRowsForGrantedOperations() {
        when(moduleAuthorizationService.hasPermission("17", "results", "read")).thenReturn(false);
        when(moduleAuthorizationService.hasPermission("17", "results", "enter")).thenReturn(true);
        when(moduleAuthorizationService.hasPermission("17", "results", "update")).thenReturn(false);
        when(moduleAuthorizationService.hasPermission("17", "results", "correct")).thenReturn(false);
        when(moduleAuthorizationService.getAuthorization("17", "results", "enter"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));

        TestResultItem pending = item("allowed-test");
        pending.setAnalysisId("pending-analysis");
        pending.setResultEntryAction("enter");
        TestResultItem entered = item("allowed-test");
        entered.setAnalysisId("entered-analysis");
        entered.setResultEntryAction("update");
        when(analysisService.get("pending-analysis")).thenReturn(analysis("allowed-test"));
        when(analysisService.get("entered-analysis")).thenReturn(analysis("allowed-test"));

        List<TestResultItem> visibleResults = resultAuthorizationService.filterResultsForEntryAccess("17",
                List.of(pending, entered), "Results");

        assertEquals(List.of("pending-analysis"),
                visibleResults.stream().map(TestResultItem::getAnalysisId).toList());
    }

    @Test
    public void filterResultsForEntryAccess_withReadReturnsAllReadableRows() {
        when(moduleAuthorizationService.hasPermission("17", "results", "read")).thenReturn(true);
        when(moduleAuthorizationService.getAuthorization("17", "results", "read"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));

        TestResultItem pending = item("allowed-test");
        pending.setAnalysisId("pending-analysis");
        pending.setResultEntryAction("enter");
        TestResultItem entered = item("allowed-test");
        entered.setAnalysisId("entered-analysis");
        entered.setResultEntryAction("update");
        when(analysisService.get("pending-analysis")).thenReturn(analysis("allowed-test"));
        when(analysisService.get("entered-analysis")).thenReturn(analysis("allowed-test"));

        List<TestResultItem> visibleResults = resultAuthorizationService.filterResultsForEntryAccess("17",
                List.of(pending, entered), "Results");

        assertEquals(List.of("pending-analysis", "entered-analysis"),
                visibleResults.stream().map(TestResultItem::getAnalysisId).toList());
    }

    private Analysis analysis(String testId) {
        Analysis analysis = new Analysis();
        analysis.setTest(test(testId));
        SampleItem sampleItem = new SampleItem();
        sampleItem.setDataCompletionState(SampleDataCompletionStatus.COMPLETED);
        analysis.setSampleItem(sampleItem);
        return analysis;
    }

    private TestResultItem item(String testId) {
        TestResultItem item = new TestResultItem();
        item.setTestId(testId);
        return item;
    }

    private org.openelisglobal.test.valueholder.Test test(String id) {
        org.openelisglobal.test.valueholder.Test test = new org.openelisglobal.test.valueholder.Test();
        test.setId(id);
        return test;
    }
}
