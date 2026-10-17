package org.openelisglobal.sample.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.sample.form.CugPrefixSettingsForm;
import org.openelisglobal.sample.service.CugPrefixService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/rest/sample-cug/prefix-settings")
@PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
public class CugPrefixSettingsRestController extends BaseRestController {
    private final CugPrefixService prefixes;

    public CugPrefixSettingsRestController(CugPrefixService prefixes) {
        this.prefixes = prefixes;
    }

    @GetMapping
    public CugPrefixSettingsForm get() {
        return prefixes.getSettings();
    }

    @PutMapping
    public ResponseEntity<?> set(@RequestBody CugPrefixSettingsForm form, HttpServletRequest request) {
        try {
            return ResponseEntity
                    .ok(prefixes.setStart(form == null ? null : form.getStartPrefix(), getSysUserId(request)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
