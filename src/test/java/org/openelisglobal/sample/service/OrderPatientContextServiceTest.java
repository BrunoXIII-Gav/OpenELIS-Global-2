package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.patient.service.PatientAuthorizationService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderPatientContextServiceTest {
    private OrderPatientContextServiceImpl context;
    private OrderAuthorizationService orders;
    private MockHttpServletRequest request;
    private long time;
    private PatientAuthorizationService patients;

    @Before
    public void setup() {
        time = 1000;
        context = new OrderPatientContextServiceImpl() {
            @Override
            protected long now() {
                return time;
            }
        };
        orders = mock(OrderAuthorizationService.class);
        when(orders.hasPermission("42", "create")).thenReturn(true);
        ReflectionTestUtils.setField(context, "authorization", orders);
        patients = mock(PatientAuthorizationService.class);
        ReflectionTestUtils.setField(context, "patientAuthorization", patients);
        request = new MockHttpServletRequest();
    }

    @Test
    public void orderLookupFlagRequiresActualOrderCreationPermission() {
        assertFalse(context.isOrderEntryLookup(request, "42"));
        request.addParameter("forOrderEntry", "true");
        assertTrue(context.isOrderEntryLookup(request, "42"));
        try {
            context.isOrderEntryLookup(request, "43");
            fail();
        } catch (AccessDeniedException expected) {
        }
    }

    @Test
    public void lookupDoesNotGrantPermissionToModifyAnExistingPatient() {
        request.addParameter("forOrderEntry", "true");
        assertTrue(context.isOrderEntryLookup(request, "42"));
        assertFalse(context.canUpdatePatient(request, "42", "10"));
        when(patients.hasPermission("42", "update")).thenReturn(true);
        assertTrue(context.canUpdatePatient(request, "42", "10"));
        assertTrue(context.canUpdatePatient(request, "42", "11"));
        when(patients.hasPermission("42", "update")).thenReturn(false);
        context.rememberRegistration(request, "42", "11");
        assertFalse(context.canUpdatePatient(request, "42", "11"));
        verify(patients, never()).canAccessPatient(any(), any(), any());
    }

    @Test
    public void onlyServerRegisteredPatientInThisUserSessionIsAccessible() {
        assertFalse(context.canAccessRegisteredPatient(request, "42", "10"));
        assertNull(request.getSession(false));
        context.rememberRegistration(request, "42", "10");
        assertTrue(context.canAccessRegisteredPatient(request, "42", "10"));
        assertFalse(context.canAccessRegisteredPatient(request, "42", "11"));
        assertFalse(context.canAccessRegisteredPatient(new MockHttpServletRequest(), "42", "10"));
        when(orders.hasPermission("43", "create")).thenReturn(true);
        assertFalse(context.canAccessRegisteredPatient(request, "43", "10"));
    }

    @Test
    public void registrationCannotOutlivePermissionExpiryOrSuccessfulOrderSubmission() {
        context.rememberRegistration(request, "42", "10");
        when(orders.hasPermission("42", "create")).thenReturn(false);
        assertFalse(context.canAccessRegisteredPatient(request, "42", "10"));
        when(orders.hasPermission("42", "create")).thenReturn(true);
        time += Duration.ofHours(2).toMillis();
        assertFalse(context.canAccessRegisteredPatient(request, "42", "10"));
        context.rememberRegistration(request, "42", "10");
        context.forgetRegistration(request, "42", "10");
        assertFalse(context.canAccessRegisteredPatient(request, "42", "10"));
    }

    @Test
    public void contextIsBoundedAndUnauthorizedRegistrationCannotGrantAccess() {
        for (int i = 1; i <= 33; i++)
            context.rememberRegistration(request, "42", Integer.toString(i));
        assertFalse(context.canAccessRegisteredPatient(request, "42", "1"));
        assertTrue(context.canAccessRegisteredPatient(request, "42", "33"));
        try {
            context.rememberRegistration(request, "43", "10");
            fail();
        } catch (AccessDeniedException expected) {
        }
        assertFalse(context.canAccessRegisteredPatient(request, "43", "10"));
    }
}
