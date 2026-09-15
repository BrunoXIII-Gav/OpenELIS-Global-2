package org.openelisglobal.sample.service;

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
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.test.service.TestService;

@RunWith(MockitoJUnitRunner.class)
public class OrderAuthorizationServiceImplTest {

    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;

    @Mock
    private TestService testService;

    @InjectMocks
    private OrderAuthorizationServiceImpl orderAuthorizationService;

    @Before
    public void setUp() {
        AuthorizationDecision decision = new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false,
                Set.of("7"));
        when(moduleAuthorizationService.getAuthorization("17", "orders", "create")).thenReturn(decision);
        when(testService.getTestsByTestSectionIds(List.of(7))).thenReturn(List.of(test("allowed-test")));
    }

    @Test
    public void canAccessAllTests_rejectsAnOrderWithATestOutsideTheAuthorizedUnit() {
        assertFalse(orderAuthorizationService.canAccessAllTests("17", List.of("allowed-test", "other-test"), "create"));
    }

    @Test
    public void canAccessSampleXml_allowsEverySelectedTestInTheAuthorizedUnit() {
        assertTrue(orderAuthorizationService.canAccessSampleXml("17", "<samples><sample tests=\"allowed-test\"/></samples>",
                "create"));
    }

    private org.openelisglobal.test.valueholder.Test test(String id) {
        org.openelisglobal.test.valueholder.Test test = new org.openelisglobal.test.valueholder.Test();
        test.setId(id);
        return test;
    }
}
