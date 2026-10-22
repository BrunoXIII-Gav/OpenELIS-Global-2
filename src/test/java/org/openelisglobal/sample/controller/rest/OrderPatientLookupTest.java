package org.openelisglobal.sample.controller.rest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.provider.query.PatientSearchResults;
import org.openelisglobal.common.rest.provider.PatientSearchRestController;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.patient.controller.rest.PatientHistorySummaryRestController;
import org.openelisglobal.patient.service.PatientAuthorizationService;
import org.openelisglobal.patient.service.PatientHistorySummaryService;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.openelisglobal.sample.service.OrderPatientContextServiceImpl;
import org.openelisglobal.search.service.SearchResultsService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderPatientLookupTest {
    private PatientSearchRestController controller;
    private PatientAuthorizationService patients;
    private OrderAuthorizationService orders;
    private SearchResultsService search;
    private MockHttpServletRequest request;
    private PatientSearchResults withAnalysis;
    private PatientSearchResults newPatient;

    @Before
    public void setup() {
        controller = new PatientSearchRestController();
        patients = mock(PatientAuthorizationService.class);
        orders = mock(OrderAuthorizationService.class);
        search = mock(SearchResultsService.class);
        var context = new OrderPatientContextServiceImpl();
        ReflectionTestUtils.setField(context, "authorization", orders);
        ReflectionTestUtils.setField(context, "patientAuthorization", patients);
        ReflectionTestUtils.setField(controller, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(controller, "orderAuthorizationService", orders);
        ReflectionTestUtils.setField(controller, "searchResultsService", search);
        ReflectionTestUtils.setField(controller, "orderPatientContextService", context);
        when(orders.hasPermission("42", "create")).thenReturn(true);
        when(patients.hasPermission("42", "read")).thenReturn(true);
        when(patients.getRestrictedSearchCriteria("42")).thenReturn(Set.of());
        when(patients.canAccessPatient("42", "10", "read")).thenReturn(true);
        request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(42);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        withAnalysis = patient("10");
        newPatient = patient("11");
        when(search.getSearchResults(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new ArrayList<>(List.of(withAnalysis, newPatient)));
    }

    @Test
    public void orderSearchFindsRegisteredPatientsWithoutAnalyses() {
        request.addParameter("forOrderEntry", "true");
        assertEquals(List.of(withAnalysis, newPatient), results());
        verify(patients, never()).canAccessPatient(any(), any(), any());
    }

    @Test
    public void patientModuleSearchAlsoFindsPatientsWithoutAnalyses() {
        assertEquals(List.of(withAnalysis, newPatient), results());
        verify(patients, never()).canAccessPatient(any(), any(), any());
    }

    @Test
    public void searchStillRequiresPatientOrOrderLookupPermission() {
        when(patients.hasPermission("42", "read")).thenReturn(false);
        when(orders.hasPermission("42", "create")).thenReturn(false);
        try {
            results();
            fail();
        } catch (AccessDeniedException expected) {
        }
        verifyZeroInteractions(search);
    }

    @Test
    public void orderLookupCannotBeForgedWithoutOrderPermission() {
        request.addParameter("forOrderEntry", "true");
        when(orders.hasPermission("42", "create")).thenReturn(false);
        try {
            results();
            fail();
        } catch (AccessDeniedException expected) {
        }
        verifyZeroInteractions(search);
    }

    @Test
    public void explicitSearchFieldRestrictionsStillApply() {
        when(patients.getRestrictedSearchCriteria("42")).thenReturn(Set.of("gender"));
        for (boolean orderEntry : List.of(false, true)) {
            request.setParameter("forOrderEntry", Boolean.toString(orderEntry));
            withAnalysis.setGender("F");
            newPatient.setGender("F");
            assertEquals(2, results().size());
            assertNull(newPatient.getGender());
            try {
                controller.getSearchResults(request, null, null, null, null, null, null, null, null, null, "F");
                fail();
            } catch (AccessDeniedException expected) {
            }
        }
    }

    @Test
    public void orderPagesCannotLeakThroughThePatientModuleCache() throws Exception {
        request.getSession().setAttribute(IActionConstants.PATIENT_SEARCH_RESULTS_SESSION_CACHE + ":orders:42",
                List.of(List.of(newPatient)));
        request.addParameter("page", "1");
        request.addParameter("forOrderEntry", "true");
        assertEquals(List.of(newPatient),
                controller.getPatientResults(request, null, null, null, null, null, null, null, null, null, null)
                        .getPatientSearchResults());
        request.removeParameter("forOrderEntry");
        assertNull(controller.getPatientResults(request, null, null, null, null, null, null, null, null, null, null)
                .getPatientSearchResults());
    }

    @Test
    public void findingAPatientForAnOrderDoesNotExposeTheirClinicalHistory() {
        request.addParameter("forOrderEntry", "true");
        assertEquals(2, results().size());
        var history = new PatientHistorySummaryRestController();
        var summaries = mock(PatientHistorySummaryService.class);
        ReflectionTestUtils.setField(history, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(history, "patientHistorySummaryService", summaries);
        try {
            history.getPatientHistorySummary(request, "11");
            fail();
        } catch (AccessDeniedException expected) {
        }
        verifyZeroInteractions(summaries);
    }

    private List<PatientSearchResults> results() {
        return controller.getSearchResults(request, null, null, null, null, null, null, null, null, null, null);
    }

    private PatientSearchResults patient(String id) {
        var result = new PatientSearchResults();
        result.setPatientID(id);
        result.setFirstName("Test");
        return result;
    }
}
