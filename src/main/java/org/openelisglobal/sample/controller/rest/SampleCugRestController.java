package org.openelisglobal.sample.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.patient.service.PatientAuthorizationService;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.bean.SampleCugPreviewResponse;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.openelisglobal.sample.service.OrderPatientContextService;
import org.openelisglobal.sample.service.SampleCugService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/rest/sample-cug")
public class SampleCugRestController extends BaseRestController {

    @Autowired
    private SampleCugService sampleCugService;

    @Autowired
    private OrderAuthorizationService orderAuthorizationService;

    @Autowired
    private PatientAuthorizationService patientAuthorizationService;

    @Autowired
    private OrderPatientContextService orderPatientContextService;

    private void requirePreviewPermission(HttpServletRequest request, String patientId) {
        String userId = getSysUserId(request);
        if (orderPatientContextService.isOrderEntryLookup(request, userId))
            return;
        if (orderAuthorizationService.hasPermission(userId, "create")
                && orderPatientContextService.canAccessRegisteredPatient(request, userId, patientId)) {
            return;
        }
        boolean allowed = orderAuthorizationService.hasPermission(userId, "create")
                || (patientId == null || patientId.isBlank()
                        ? patientAuthorizationService.hasPermission(userId, "create")
                        : patientAuthorizationService.hasPermission(userId, "read")
                                || patientAuthorizationService.hasPermission(userId, "update"));
        if (!allowed)
            throw new AccessDeniedException("User cannot preview patient CUGs");
    }

    @GetMapping(value = "/patient-prefix", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> patientPrefix(@RequestParam(required = false) String patientId,
            HttpServletRequest request) {
        requirePreviewPermission(request, patientId);
        String assigned = sampleCugService.getPatientPrefix(patientId);
        String prefix = assigned == null ? sampleCugService.previewPatientPrefix(patientId) : assigned;
        return ResponseEntity.ok(Map.of("prefix", prefix == null ? "" : prefix, "assigned", assigned != null));
    }

    @PostMapping(value = "/suggest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> suggest(@RequestBody SampleCugPreviewRequest request, HttpServletRequest httpRequest) {
        requirePreviewPermission(httpRequest, request.getPatientId());
        try {
            return ResponseEntity.ok(sampleCugService.previewCugCode(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> preview(@RequestBody SampleCugPreviewRequest request, HttpServletRequest httpRequest) {
        if (!orderAuthorizationService.hasPermission(getSysUserId(httpRequest), "create")) {
            throw new AccessDeniedException("The user does not have permission to create orders");
        }
        try {
            SampleCugPreviewResponse response = sampleCugService.reserveCugCode(request, getSysUserId(httpRequest));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
