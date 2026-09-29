package org.openelisglobal.patient.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.patient.form.PatientHistorySummary;
import org.openelisglobal.patient.service.PatientAuthorizationService;
import org.openelisglobal.patient.service.PatientHistorySummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/rest/")
public class PatientHistorySummaryRestController extends BaseRestController {

    @Autowired
    private PatientHistorySummaryService patientHistorySummaryService;

    @Autowired
    private PatientAuthorizationService patientAuthorizationService;

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @GetMapping(value = "patient-history/{patientId}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PatientHistorySummary getPatientHistorySummary(HttpServletRequest request, @PathVariable String patientId) {
        if (!patientAuthorizationService.canAccessPatient(getSysUserId(request), patientId, "read")) {
            throw new AccessDeniedException("User does not have Patients module permission: read");
        }
        PatientHistorySummary summary = patientHistorySummaryService.getSummary(patientId);
        summary.setRestrictedFieldTagKeys(
                moduleAuthorizationService.getRestrictedFieldTagKeys(getSysUserId(request), "patients"));
        return summary;
    }
}
