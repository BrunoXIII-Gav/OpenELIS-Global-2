package org.openelisglobal.sample.controller.rest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.patient.service.PatientAuthorizationService;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.openelisglobal.sample.service.OrderPatientContextService;
import org.openelisglobal.sample.service.SampleCugService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

public class PatientCugPreviewSecurityTest {
    private SampleCugRestController controller;
    private SampleCugService cugs;
    private OrderAuthorizationService orders;
    private PatientAuthorizationService patients;
    private MockHttpServletRequest request;
    private OrderPatientContextService context;

    @Before
    public void setup() {
        controller = new SampleCugRestController();
        cugs = mock(SampleCugService.class);
        orders = mock(OrderAuthorizationService.class);
        patients = mock(PatientAuthorizationService.class);
        context = mock(OrderPatientContextService.class);
        ReflectionTestUtils.setField(controller, "sampleCugService", cugs);
        ReflectionTestUtils.setField(controller, "orderAuthorizationService", orders);
        ReflectionTestUtils.setField(controller, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(controller, "orderPatientContextService", context);
        request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(42);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
    }

    @Test
    public void unauthorizedUsersCannotPreview() {
        try {
            controller.patientPrefix(null, request);
            fail();
        } catch (AccessDeniedException expected) {
        }
        try {
            controller.suggest(new SampleCugPreviewRequest(), request);
            fail();
        } catch (AccessDeniedException expected) {
        }
        verifyZeroInteractions(cugs);
    }

    @Test
    public void patientReadPermissionAllowsPrefixWithoutAnalyses() {
        when(orders.hasPermission("42", "create")).thenReturn(true);
        when(patients.hasPermission("42", "read")).thenReturn(true);
        assertEquals(200, controller.patientPrefix("10", request).getStatusCode().value());
        verify(patients, never()).canAccessPatient(any(), any(), any());
    }

    @Test
    public void newlyRegisteredOrderPatientCanPreviewBeforeAnyAnalysesExist() {
        when(orders.hasPermission("42", "create")).thenReturn(true);
        when(patients.hasPermission("42", "read")).thenReturn(true);
        when(context.canAccessRegisteredPatient(request, "42", "10")).thenReturn(true);
        when(cugs.getPatientPrefix("10")).thenReturn("5004");
        assertEquals(200, controller.patientPrefix("10", request).getStatusCode().value());
        var preview = new SampleCugPreviewRequest();
        preview.setPatientId("10");
        assertEquals(200, controller.suggest(preview, request).getStatusCode().value());
        verify(cugs).previewCugCode(preview);
        verify(cugs, never()).reserveCugCode(any(), any());
        assertEquals(200, controller.patientPrefix("11", request).getStatusCode().value());
        verify(patients, never()).canAccessPatient(any(), any(), any());
    }

    @Test
    public void explicitAuthorizedOrderLookupCanSuggestForAnExistingPatientWithoutAnalyses() {
        request.addParameter("forOrderEntry", "true");
        when(context.isOrderEntryLookup(request, "42")).thenReturn(true);
        when(cugs.getPatientPrefix("11")).thenReturn("5005");
        assertEquals(200, controller.patientPrefix("11", request).getStatusCode().value());
        var preview = new SampleCugPreviewRequest();
        preview.setPatientId("11");
        assertEquals(200, controller.suggest(preview, request).getStatusCode().value());
        verify(cugs).previewCugCode(preview);
        verify(patients, never()).canAccessPatient(any(), any(), any());
    }

    @Test
    public void patientCreationPermissionCanReadNonallocatingPrefix() {
        when(patients.hasPermission("42", "create")).thenReturn(true);
        when(cugs.previewPatientPrefix(null)).thenReturn("5000");
        assertEquals(200, controller.patientPrefix(null, request).getStatusCode().value());
        verify(cugs).previewPatientPrefix(null);
        verify(cugs, never()).ensurePatientPrefix(any(), any());
        verify(cugs, never()).reserveCugCode(any(), any());
    }
}
