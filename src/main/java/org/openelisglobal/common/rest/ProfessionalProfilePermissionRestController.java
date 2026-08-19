package org.openelisglobal.common.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.openelisglobal.common.service.ProfessionalProfilePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller to check professional profile permissions for current user
 */
@RestController
@RequestMapping("/rest")
public class ProfessionalProfilePermissionRestController extends BaseRestController {

    @Autowired
    private ProfessionalProfilePermissionService profilePermissionService;

    /**
     * Get all professional profile permissions for current user
     * Returns a map with permission checks for each module
     */
    @GetMapping(value = "/professional-profile-permissions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getPermissions(HttpServletRequest request) {
        String sysUserId = getSysUserId(request);

        Map<String, Object> permissions = new LinkedHashMap<>();
        permissions.putAll(profilePermissionService.getLegacyPermissionResponse(sysUserId));
        permissions.put("effectiveProfessionalProfileCode",
                profilePermissionService.getEffectiveProfessionalProfileCode(sysUserId));
        permissions.put("permissionsByFlow", profilePermissionService.getPermissionsByFlow(sysUserId));
        permissions.put("configuredProfileCodesByFlow", profilePermissionService.getConfiguredProfileCodesByFlow());

        return ResponseEntity.ok(permissions);
    }
}
