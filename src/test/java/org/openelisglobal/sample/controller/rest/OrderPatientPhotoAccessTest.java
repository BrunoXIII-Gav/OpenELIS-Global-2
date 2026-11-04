package org.openelisglobal.sample.controller.rest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.patient.controller.rest.PatientManagementRestController;
import org.openelisglobal.patient.service.PatientAuthorizationService;
import org.openelisglobal.patient.service.PatientPhotoService;
import org.openelisglobal.sample.service.OrderPatientContextService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderPatientPhotoAccessTest {
    @Test
    public void justRegisteredOrderPatientPhotoCanLoadWithoutGrantingOtherPatientAccess() {
        var controller = new PatientManagementRestController();
        var patients = mock(PatientAuthorizationService.class);
        var context = mock(OrderPatientContextService.class);
        var photos = mock(PatientPhotoService.class);
        ReflectionTestUtils.setField(controller, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(controller, "orderPatientContextService", context);
        ReflectionTestUtils.setField(controller, "photoService", photos);
        var request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(42);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        when(context.canAccessRegisteredPatient(request, "42", "10")).thenReturn(true);
        assertEquals(200, controller.getPhoto(request, "10", false).getStatusCode().value());
        verify(photos).getPhotoByPatientId("10", false);
        try {
            controller.getPhoto(request, "11", false);
            fail();
        } catch (AccessDeniedException expected) {
        }
        verify(photos, never()).getPhotoByPatientId(eq("11"), anyBoolean());
        // Existing patient read permissions continue to work independently.
        when(patients.hasPermission("42", "read")).thenReturn(true);
        assertEquals(200, controller.getPhoto(request, "11", false).getStatusCode().value());
        when(patients.hasPermission("42", "read")).thenReturn(false);
        request.addParameter("forOrderEntry", "true");
        when(context.isOrderEntryLookup(request, "42")).thenReturn(true);
        assertEquals(200, controller.getPhoto(request, "11", true).getStatusCode().value());
        verify(photos).getPhotoByPatientId("11", true);
    }
}
