package org.openelisglobal.reports.controller;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.junit.Test;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.patient.service.PatientValidationHistoryService;
import org.openelisglobal.reports.form.ReportForm;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.support.SimpleSessionStatus;

public class PatientValidationReportAccessTest {
    @Test
    public void cannotOverridePatientAnalysisTemplateOrEnableDraftPreview() {
        var controller = new CapturingReportController();
        var service = mock(PatientValidationHistoryService.class);
        ReflectionTestUtils.setField(controller, "patientValidationHistoryService", service);
        var request = request();
        request.setParameter("analysisIds", "999,1000");
        request.setParameter("report", "otherReport");
        request.setParameter("previewValidated", "true");
        var response = new MockHttpServletResponse();
        var status = new SimpleSessionStatus();
        controller.showPatientHistoryValidationReport(request, response, "10", "78", status);
        verify(service).requireReportAccess("10", "78", "7");
        assertEquals("78", controller.renderedRequest.getParameter("analysisIds"));
        assertEquals("patientCILNSP_vreduit", controller.renderedRequest.getParameter("report"));
        assertNull(controller.renderedRequest.getParameter("previewValidated"));
        assertEquals(List.of("78"), controller.form.getAnalysisIds());
        assertFalse(controller.form.isPreviewValidated());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(status.isComplete());
    }

    @Test
    public void deniedRequestNeverReachesPdfRenderer() {
        var controller = new CapturingReportController();
        var service = mock(PatientValidationHistoryService.class);
        ReflectionTestUtils.setField(controller, "patientValidationHistoryService", service);
        doThrow(new AccessDeniedException("restricted")).when(service).requireReportAccess("10", "78", "7");
        assertThrows(AccessDeniedException.class, () -> controller.showPatientHistoryValidationReport(request(),
                new MockHttpServletResponse(), "10", "78", new SimpleSessionStatus()));
        assertNull(controller.renderedRequest);
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(7);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        return request;
    }

    private static class CapturingReportController extends ReportController {
        private HttpServletRequest renderedRequest;
        private ReportForm form;

        @Override
        protected String getDefaultValidationReportName() {
            return "patientCILNSP_vreduit";
        }

        @Override
        protected void printReport(HttpServletRequest request, HttpServletResponse response, ReportForm form) {
            this.renderedRequest = request;
            this.form = form;
        }
    }
}
