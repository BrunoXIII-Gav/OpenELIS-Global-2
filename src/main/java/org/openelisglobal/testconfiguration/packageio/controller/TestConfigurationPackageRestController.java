package org.openelisglobal.testconfiguration.packageio.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.openelisglobal.common.util.ControllerUtills;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage.*;
import org.openelisglobal.testconfiguration.packageio.service.TestConfigurationPackageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/test-configuration-packages")
@PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).GLOBAL_ADMIN) or @accessControl.hasModulePermission('administration', 'manage')")
public class TestConfigurationPackageRestController extends org.openelisglobal.common.rest.BaseRestController {
    @Autowired
    private TestConfigurationPackageService service;
    @Autowired
    private org.openelisglobal.security.service.AccessControlService accessControl;
    @Autowired
    private org.openelisglobal.test.service.TestCreationOriginService testCreationOriginService;

    @GetMapping("/unclassified-tests")
    public List<Map<String, String>> unclassifiedTests() {
        return testCreationOriginService.unclassifiedTests();
    }

    @PostMapping("/classify-origin")
    public Map<String, Integer> classifyOrigin(@RequestBody OriginClassification form, HttpServletRequest request) {
        return Map.of("classified", testCreationOriginService.classify(form.testIds(), form.userCreated(),
                ControllerUtills.getSysUserId(request)));
    }

    @GetMapping("/availability")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Boolean> availability() {
        return Map.of("available",
                accessControl.hasPermission(org.openelisglobal.common.constants.SystemPermission.GLOBAL_ADMIN)
                        || accessControl.hasModulePermission("administration", "manage"));
    }

    @GetMapping("/tests")
    public List<Map<String, String>> tests() {
        return service.tests();
    }

    @PostMapping("/export")
    public TestConfigurationPackage export(@RequestBody List<String> testIds) {
        return service.exportTests(testIds);
    }

    @PostMapping("/preview")
    public Preview preview(@RequestBody ImportRequest request) {
        return service.preview(request);
    }

    @PostMapping("/prepare")
    public TestConfigurationPackage prepare(@RequestBody SelectionRequest request) {
        return service.prepareImport(request.bundle(), request.testKey());
    }

    @PostMapping("/import")
    public ImportResult importPackage(@RequestBody ImportRequest form, HttpServletRequest request) {
        return service.importPackage(form, ControllerUtills.getSysUserId(request));
    }

    @GetMapping("/history")
    public List<Map<String, Object>> history() {
        return service.history();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalid(IllegalArgumentException exception) {
        String message = exception.getMessage();
        String key = message != null && message.startsWith("testPackage.issue.") ? message : "testPackage.issue.format";
        return ResponseEntity.badRequest().body(Map.of("error", key));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "testPackage.issue.conflicts"));
    }
}
