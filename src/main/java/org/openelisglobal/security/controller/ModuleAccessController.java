package org.openelisglobal.security.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.openelisglobal.security.service.ModuleAccessResult;
import org.openelisglobal.security.service.ModuleAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModuleAccessController {

    @Autowired
    private ModuleAccessService moduleAccessService;

    @GetMapping("/rest/module-access")
    public ResponseEntity<Map<String, Object>> canAccess(@RequestParam("url") String targetUrl,
            HttpServletRequest request) {

        ModuleAccessResult accessResult = moduleAccessService.canAccess(targetUrl, request);
        if (accessResult.getMessage() != null) {
            return ResponseEntity.status(accessResult.getStatus())
                    .body(Map.of("allowed", accessResult.isAllowed(), "message", accessResult.getMessage()));
        }
        return ResponseEntity.status(accessResult.getStatus()).body(Map.of("allowed", accessResult.isAllowed()));
    }
}
