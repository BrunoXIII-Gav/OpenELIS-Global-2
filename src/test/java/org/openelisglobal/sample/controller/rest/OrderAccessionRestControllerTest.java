package org.openelisglobal.sample.controller.rest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import org.junit.Test;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.sample.exception.OrderAccessionConflictException;
import org.openelisglobal.sample.service.OrderAccessionService;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderAccessionRestControllerTest {
    @Test
    public void previewsRequireOrderCreationPermissionAndDoNotSaveAnything() {
        var controller = new OrderAccessionRestController();
        var numbers = mock(OrderAccessionService.class);
        var auth = mock(OrderAuthorizationService.class);
        ReflectionTestUtils.setField(controller, "numbers", numbers);
        ReflectionTestUtils.setField(controller, "authorization", auth);
        var request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(42);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        try {
            controller.suggest(null, request);
            fail();
        } catch (AccessDeniedException expected) {
        }
        verifyZeroInteractions(numbers);
        when(auth.hasPermission("42", "create")).thenReturn(true);
        when(numbers.preview(null)).thenReturn("LAB0000000000001");
        assertEquals("LAB0000000000001", controller.suggest(null, request).get("body"));
        verify(numbers, never()).confirm(anyString(), anyBoolean());
    }

    @Test
    public void manualConflictHasARecognizable409Code() {
        var response = new SamplePatientEntryRestController().handleCugConflict(new OrderAccessionConflictException());
        assertEquals(409, response.getStatusCode().value());
        assertEquals("order.labnumber.conflict", response.getHeaders().getFirst("X-OpenELIS-Error-Code"));
        var sql = new RuntimeException(new SQLException("Key (accession_number)=(LAB1) already exists", "23505"));
        assertEquals(409, new SamplePatientEntryRestController().handleCugConflict(sql).getStatusCode().value());
        assertFalse(OrderAccessionConflictException.isConflict(new SQLException("other constraint", "23505")));
    }
}
