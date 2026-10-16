package org.openelisglobal.audittrail;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.openelisglobal.audittrail.controller.rest.AuditEventRestController;
import org.openelisglobal.audittrail.service.AuditEventQueryService;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;

public class AuditEventRestControllerTest {
    private final AuditEventQueryService service = mock(AuditEventQueryService.class);
    private final AuditEventRestController controller = new AuditEventRestController(service);

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(7);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        return request;
    }

    @Test
    public void rejectsInvalidDatesWithoutQuerying() {
        assertEquals(400,
                controller.search(request(), null, null, null, null, null, null, null, null, "invalid date", null, 0, 30)
                        .getStatusCode().value());
        verifyZeroInteractions(service);
    }

    @Test
    public void rejectsUnauthenticatedRequests() {
        assertEquals(401, controller
                .search(new MockHttpServletRequest(), null, null, null, null, null, null, null, null, null, null, 0, 30)
                .getStatusCode().value());
        verifyZeroInteractions(service);
    }

    @Test
    public void mapsAccessDenialToForbidden() {
        when(service.search(any(), eq("7"))).thenThrow(new AccessDeniedException("denied"));
        assertEquals(403, controller.search(request(), null, null, null, null, null, null, null,
                null, null, null, 0, 30).getStatusCode().value());
    }

    @Test(expected = IllegalArgumentException.class)
    public void doesNotMisreportQueryFailuresAsInvalidFilters() {
        when(service.search(any(), eq("7"))).thenThrow(new IllegalArgumentException("Incompatible query type"));
        controller.search(request(), "LAB-1", null, null, null, null, null, null, null, null, null, 0, 30);
    }

    @Test
    public void rejectsInvalidTestIdentifiersBeforeReadingHistory() {
        assertEquals(400, controller.search(request(), null, null, null, null, "not-a-test", null, null, null,
                null, null, 0, 30).getStatusCode().value());
        verifyZeroInteractions(service);
    }

    @Test
    public void authenticatesAndAuthorizesTestSelector() {
        assertEquals(401, controller.tests(new MockHttpServletRequest()).getStatusCode().value());
        verifyZeroInteractions(service);
        when(service.tests("7")).thenThrow(new AccessDeniedException("denied"));
        assertEquals(403, controller.tests(request()).getStatusCode().value());
    }
}
