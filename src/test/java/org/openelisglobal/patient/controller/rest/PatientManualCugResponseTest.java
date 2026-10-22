package org.openelisglobal.patient.controller.rest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.junit.Test;
import org.openelisglobal.sample.exception.PatientCugPrefixException;
import org.springframework.http.HttpStatus;

public class PatientManualCugResponseTest {
    @Test
    public void orderEndpointCannotBeUsedToChangeAnExistingPatientPrefix() {
        var controller = new PatientManagementRestController();
        var orders = mock(org.openelisglobal.sample.service.OrderAuthorizationService.class);
        var patients = mock(org.openelisglobal.patient.service.PatientService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "orderAuthorizationService", orders);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "patientService", patients);
        when(orders.hasPermission("42", "create")).thenReturn(true);
        var request = request("/rest/orders/patient");
        var info = new org.openelisglobal.patient.action.bean.PatientManagementInfo();
        info.setPatientPK("10");
        info.setRequestedCugPrefix("8000");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.savepatient(request, info,
                        new org.springframework.validation.BeanPropertyBindingResult(info, "patient")));
        verifyZeroInteractions(patients);
    }

    @Test
    public void patientEndpointStillRequiresPatientUpdatePermission() {
        var controller = new PatientManagementRestController();
        var permission = mock(org.openelisglobal.patient.service.PatientAuthorizationService.class);
        var patients = mock(org.openelisglobal.patient.service.PatientService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "patientAuthorizationService",
                permission);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "patientService", patients);
        var info = new org.openelisglobal.patient.action.bean.PatientManagementInfo();
        info.setPatientPK("10");
        info.setRequestedCugPrefix("8000");
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.savepatient(request("/rest/PatientManagement"), info,
                        new org.springframework.validation.BeanPropertyBindingResult(info, "patient")));
        verify(permission).hasPermission("42", "update");
        verifyZeroInteractions(patients);
    }

    private org.springframework.mock.web.MockHttpServletRequest request(String path) {
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", path);
        var user = new org.openelisglobal.login.valueholder.UserSessionData();
        user.setSytemUserId(42);
        request.getSession().setAttribute(org.openelisglobal.common.action.IActionConstants.USER_SESSION_DATA, user);
        return request;
    }

    @Test
    public void occupiedManualPrefixReturnsAConflictWithoutPatientOrPrefixDetails() {
        var controller = new PatientManagementRestController();
        var response = controller
                .handleRegistrationFailure(new PatientCugPrefixException(PatientCugPrefixException.OCCUPIED));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(Map.of("code", "patient.cug.prefix.duplicate"), response.getBody());
    }

    @Test
    public void invalidManualPrefixReturnsBadRequest() {
        var response = new PatientManagementRestController()
                .handleRegistrationFailure(new PatientCugPrefixException(PatientCugPrefixException.INVALID));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
