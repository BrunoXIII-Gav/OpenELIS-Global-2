package org.openelisglobal.patientadditionalfield.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldOptionPayload;
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldPayload;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/rest/")
public class PatientAdditionalFieldRestController extends BaseRestController {

    @Autowired
    private PatientAdditionalFieldService patientAdditionalFieldService;

    @GetMapping(value = "patient-additional-fields", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@accessControl.hasAnyPermission(T(org.openelisglobal.common.constants.SystemPermission).PATIENT, "
            + "T(org.openelisglobal.common.constants.SystemPermission).ORDER)")
    @ResponseBody
    public List<PatientAdditionalFieldPayload> getFields(
            @RequestParam(value = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return patientAdditionalFieldService.getFields(includeInactive);
    }

    @GetMapping(value = "patient-additional-fields/values", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@accessControl.hasAnyPermission(T(org.openelisglobal.common.constants.SystemPermission).PATIENT, "
            + "T(org.openelisglobal.common.constants.SystemPermission).ORDER)")
    @ResponseBody
    public Map<String, String> getValues(@RequestParam("patientId") String patientId) {
        return patientAdditionalFieldService.getPatientValues(patientId, null);
    }

    @PostMapping(value = "patient-additional-fields", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @ResponseBody
    public ResponseEntity<?> createField(HttpServletRequest request,
            @RequestBody PatientAdditionalFieldPayload payload) {
        try {
            PatientAdditionalFieldPayload created = patientAdditionalFieldService.createField(payload,
                    getSysUserId(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping(value = "patient-additional-fields/{fieldId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @ResponseBody
    public ResponseEntity<?> updateField(HttpServletRequest request,
            @PathVariable Integer fieldId, @RequestBody PatientAdditionalFieldPayload payload) {
        try {
            return ResponseEntity.ok(patientAdditionalFieldService.updateField(fieldId, payload, getSysUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping(value = "patient-additional-fields/{fieldId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @ResponseBody
    public ResponseEntity<?> deactivateField(HttpServletRequest request, @PathVariable Integer fieldId) {
        try {
            patientAdditionalFieldService.deactivateField(fieldId, getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "patient-additional-fields/{fieldId}/options", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @ResponseBody
    public ResponseEntity<?> createOption(HttpServletRequest request,
            @PathVariable Integer fieldId, @RequestBody PatientAdditionalFieldOptionPayload payload) {
        try {
            PatientAdditionalFieldOptionPayload created = patientAdditionalFieldService.createOption(fieldId, payload,
                    getSysUserId(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping(value = "patient-additional-fields/options/{optionId}", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @ResponseBody
    public ResponseEntity<?> updateOption(HttpServletRequest request,
            @PathVariable Integer optionId, @RequestBody PatientAdditionalFieldOptionPayload payload) {
        try {
            return ResponseEntity.ok(patientAdditionalFieldService.updateOption(optionId, payload, getSysUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping(value = "patient-additional-fields/options/{optionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @ResponseBody
    public ResponseEntity<?> deactivateOption(HttpServletRequest request, @PathVariable Integer optionId) {
        try {
            patientAdditionalFieldService.deactivateOption(optionId, getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
