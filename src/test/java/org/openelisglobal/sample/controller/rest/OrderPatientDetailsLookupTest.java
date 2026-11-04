package org.openelisglobal.sample.controller.rest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.rest.provider.PatientSearchPopulateRestController;
import org.openelisglobal.common.rest.provider.bean.PatientInfoBean;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.service.PatientAuthorizationService;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.openelisglobal.sample.service.OrderPatientContextService;
import org.openelisglobal.sample.service.OrderPatientContextServiceImpl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderPatientDetailsLookupTest {
    @Test
    public void orderSubmissionRejectsForgedPatientEditsButAllowsLinkingTheSelectedPatient() {
        var controller = new SamplePatientEntryRestController();
        var context = mock(OrderPatientContextService.class);
        ReflectionTestUtils.setField(controller, "orderPatientContextService", context);
        var request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(42);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        var patient = new PatientManagementInfo();
        patient.setPatientPK("11");
        patient.setReadOnly(false); // A client-controlled flag must not grant editing rights.
        patient.setPatientUpdateStatus(PatientUpdateStatus.UPDATE);
        try {
            ReflectionTestUtils.invokeMethod(controller, "requirePatientMutationPermission", request, patient);
            fail();
        } catch (AccessDeniedException expected) {
        }
        patient.setPatientUpdateStatus(PatientUpdateStatus.NO_ACTION);
        ReflectionTestUtils.invokeMethod(controller, "requirePatientMutationPermission", request, patient);
        patient.setPatientUpdateStatus(PatientUpdateStatus.UPDATE);
        when(context.canUpdatePatient(request, "42", "11")).thenReturn(true);
        ReflectionTestUtils.invokeMethod(controller, "requirePatientMutationPermission", request, patient);
    }

    @Test
    public void unscopedOrderSelectionLoadsDemographicsAsReadOnlyWithoutGrantingPatientModuleAccess() {
        var controller = new PatientSearchPopulateRestController() {
            @Override
            protected PatientInfoBean getPatientDetails(Patient patient) {
                var details = new PatientInfoBean();
                details.setPatientPK(patient.getId());
                details.setFirstName("Test");
                return details;
            }
        };
        var patients = mock(PatientAuthorizationService.class);
        var orders = mock(OrderAuthorizationService.class);
        var context = new OrderPatientContextServiceImpl();
        ReflectionTestUtils.setField(context, "authorization", orders);
        ReflectionTestUtils.setField(context, "patientAuthorization", patients);
        ReflectionTestUtils.setField(controller, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(controller, "orderAuthorizationService", orders);
        ReflectionTestUtils.setField(controller, "orderPatientContextService", context);
        ReflectionTestUtils.setField(controller, "patientService", mock(PatientService.class));
        when(orders.hasPermission("42", "create")).thenReturn(true);
        when(patients.hasPermission("42", "read")).thenReturn(true);
        var request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(42);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        request.addParameter("forOrderEntry", "true");
        var selected = controller.getPatientResults(request, "11");
        assertEquals("11", selected.getPatientPK());
        assertTrue(selected.isReadOnly());
        assertEquals(PatientUpdateStatus.NO_ACTION, selected.getPatientUpdateStatus());
        when(patients.hasPermission("42", "update")).thenReturn(true);
        assertFalse(controller.getPatientResults(request, "11").isReadOnly());
        request.removeParameter("forOrderEntry");
        assertFalse(controller.getPatientResults(request, "11").isReadOnly());
        when(patients.hasPermission("42", "update")).thenReturn(false);
        assertTrue(controller.getPatientResults(request, "11").isReadOnly());
        verify(patients, never()).canAccessPatient(any(), any(), any());
    }
}
