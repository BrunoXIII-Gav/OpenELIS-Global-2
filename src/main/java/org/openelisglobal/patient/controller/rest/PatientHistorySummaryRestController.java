package org.openelisglobal.patient.controller.rest;

import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.patient.form.PatientHistorySummary;
import org.openelisglobal.patient.service.PatientHistorySummaryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/rest/")
@PreAuthorize("@accessControl.hasAnyPermission(T(org.openelisglobal.common.constants.SystemPermission).PATIENT, "
        + "T(org.openelisglobal.common.constants.SystemPermission).ORDER)")
public class PatientHistorySummaryRestController extends BaseRestController {

    @Autowired
    private PatientHistorySummaryService patientHistorySummaryService;

    @GetMapping(value = "patient-history/{patientId}/summary", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PatientHistorySummary getPatientHistorySummary(@PathVariable String patientId) {
        return patientHistorySummaryService.getSummary(patientId);
    }
}
