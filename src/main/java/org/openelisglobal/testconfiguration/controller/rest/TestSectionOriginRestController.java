package org.openelisglobal.testconfiguration.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.common.util.ControllerUtills;
import org.openelisglobal.security.service.AccessControlService;
import org.openelisglobal.test.service.TestSectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/test-section-origins")
@PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).GLOBAL_ADMIN) or @accessControl.hasModulePermission('administration', 'manage')")
public class TestSectionOriginRestController extends BaseRestController {
    @Autowired
    private TestSectionService testSectionService;
    @Autowired
    private AccessControlService accessControl;

    @GetMapping("/availability")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Boolean> availability() {
        return Map.of("available", accessControl.hasPermission(SystemPermission.GLOBAL_ADMIN)
                || accessControl.hasModulePermission("administration", "manage"));
    }

    @GetMapping
    public List<Map<String, String>> unclassified() {
        return testSectionService.getUnclassifiedTestSectionOrigins();
    }

    public record Classification(List<String> sectionIds, boolean userCreated) {
    }

    @PostMapping
    public Map<String, Integer> classify(@RequestBody Classification form, HttpServletRequest request) {
        return Map.of("classified", testSectionService.classifyTestSectionOrigins(form.sectionIds(), form.userCreated(),
                ControllerUtills.getSysUserId(request)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
