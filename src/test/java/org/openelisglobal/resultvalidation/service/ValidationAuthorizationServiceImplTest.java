package org.openelisglobal.resultvalidation.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.resultvalidation.bean.AnalysisItem;
import org.openelisglobal.systemuser.service.UserService;
import org.openelisglobal.test.service.TestService;

@RunWith(MockitoJUnitRunner.class)
public class ValidationAuthorizationServiceImplTest {

    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;
    @Mock
    private UserService userService;
    @Mock
    private TestService testService;
    @InjectMocks
    private ValidationAuthorizationServiceImpl validationAuthorizationService;

    @Test
    public void canAccessAllResults_rejectsValidationOutsideTheExplicitLaboratoryScope() {
        when(moduleAuthorizationService.getAuthorization("17", "validation", "validate"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));
        when(testService.getTestsByTestSectionIds(List.of(7))).thenReturn(List.of(test("allowed-test")));

        assertFalse(validationAuthorizationService.canAccessAllResults("17",
                List.of(item("allowed-test"), item("other-test")), "validate", List.of("Validation")));
    }

    @Test
    public void canAccessAllResults_allowsEveryResultInTheExplicitLaboratoryScope() {
        when(moduleAuthorizationService.getAuthorization("17", "validation", "validate"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));
        when(testService.getTestsByTestSectionIds(List.of(7))).thenReturn(List.of(test("allowed-test")));

        assertTrue(validationAuthorizationService.canAccessAllResults("17", List.of(item("allowed-test")), "validate",
                List.of("Validation")));
    }

    private AnalysisItem item(String testId) {
        AnalysisItem item = new AnalysisItem();
        item.setTestId(testId);
        return item;
    }

    private org.openelisglobal.test.valueholder.Test test(String id) {
        org.openelisglobal.test.valueholder.Test test = new org.openelisglobal.test.valueholder.Test();
        test.setId(id);
        return test;
    }
}
