package org.openelisglobal.security.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.security.service.ModuleAccessResult;
import org.openelisglobal.security.service.ModuleAccessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ModuleAccessController extends BaseRestController {

    @Autowired
    private ModuleAccessService moduleAccessService;
    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

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

    @GetMapping("/rest/module-action-access")
    public Map<String, Boolean> canAccessAction(@RequestParam String moduleKey, @RequestParam String actionKey,
            HttpServletRequest request) {
        return Map.of("allowed", moduleAuthorizationService.hasPermission(getSysUserId(request), moduleKey, actionKey));
    }
}
