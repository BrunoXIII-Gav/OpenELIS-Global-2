package org.openelisglobal.professionalprofile.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileDefinitionForm;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileSettingsForm;
import org.openelisglobal.professionalprofile.service.ProfessionalProfileDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/professional-profiles")
@PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).GLOBAL_ADMIN)")
public class ProfessionalProfileDefinitionRestController extends BaseRestController {

    @Autowired
    private ProfessionalProfileDefinitionService professionalProfileDefinitionService;

    @GetMapping("/catalog")
    public Map<String, Object> getCatalog() {
        Map<String, Object> response = new HashMap<>();
        response.put("profiles", professionalProfileDefinitionService.getProfiles());
        response.put("settings", professionalProfileDefinitionService.getSettings());
        return response;
    }

    @GetMapping("/{code}")
    public ResponseEntity<ProfessionalProfileDefinitionForm> getProfile(@PathVariable String code) {
        ProfessionalProfileDefinitionForm profile = professionalProfileDefinitionService.getProfile(code);
        if (profile == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(profile);
    }

    @PostMapping
    public ResponseEntity<?> createProfile(HttpServletRequest request, @RequestBody ProfessionalProfileDefinitionForm form) {
        return execute(() -> ResponseEntity.ok(
                professionalProfileDefinitionService.createProfile(form, getSysUserId(request))));
    }

    @PutMapping("/{code}")
    public ResponseEntity<?> updateProfile(HttpServletRequest request, @PathVariable String code,
            @RequestBody ProfessionalProfileDefinitionForm form) {
        return execute(() -> ResponseEntity.ok(
                professionalProfileDefinitionService.updateProfile(code, form, getSysUserId(request))));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<?> deleteProfile(HttpServletRequest request, @PathVariable String code) {
        return execute(() -> {
            professionalProfileDefinitionService.deleteProfile(code, getSysUserId(request));
            return ResponseEntity.noContent().build();
        });
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateSettings(HttpServletRequest request, @RequestBody ProfessionalProfileSettingsForm form) {
        return execute(() -> ResponseEntity.ok(
                professionalProfileDefinitionService.updateSettings(form, getSysUserId(request))));
    }

    private ResponseEntity<?> execute(Action action) {
        try {
            return action.execute();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        }
    }

    @FunctionalInterface
    private interface Action {
        ResponseEntity<?> execute();
    }
}
