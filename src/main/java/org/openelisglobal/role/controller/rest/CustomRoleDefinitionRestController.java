package org.openelisglobal.role.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.services.DisplayListService.ListType;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.role.form.CustomRoleDefinitionForm;
import org.openelisglobal.role.service.CustomRoleDefinitionService;
import org.openelisglobal.role.valueholder.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/custom-roles")
@PreAuthorize("hasRole('GLOBAL_ADMIN')")
public class CustomRoleDefinitionRestController extends BaseRestController {

    @Autowired
    private CustomRoleDefinitionService customRoleDefinitionService;

    @GetMapping("/catalog")
    public Map<String, Object> getCatalog() {
        Map<String, List<Role>> catalog = customRoleDefinitionService.getPermissionCatalog();
        Map<String, Object> response = new HashMap<>();
        response.put("customRoles", customRoleDefinitionService.getCustomRoles().stream().map(this::toSummary).toList());
        response.put("globalPermissionRoles",
                catalog.getOrDefault("globalRoles", List.of()).stream().map(this::toSummary).toList());
        response.put("labPermissionRoles",
                catalog.getOrDefault("labUnitRoles", List.of()).stream().map(this::toSummary).toList());
        response.put("labUnits", DisplayListService.getInstance().getList(ListType.TEST_SECTION_ACTIVE).stream()
                .map(this::toLabUnitSummary).toList());
        return response;
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<Map<String, Object>> getCustomRole(@PathVariable String roleId) {
        Role customRole = customRoleDefinitionService.getCustomRole(roleId);
        if (customRole == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>(toSummary(customRole));
        response.put("permissionRoleIds", customRoleDefinitionService.getPermissionRoleIdsForCustomRole(roleId));
        response.put("applicableLabUnitIds", customRoleDefinitionService.getApplicableLabUnitIdsForCustomRole(roleId));
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> createCustomRole(HttpServletRequest request,
            @RequestBody @Valid CustomRoleDefinitionForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("message", bindingResult.getAllErrors().get(0).getDefaultMessage()));
        }
        try {
            Role savedRole = customRoleDefinitionService.saveCustomRole(form, getSysUserId(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(savedRole));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<?> updateCustomRole(HttpServletRequest request, @PathVariable String roleId,
            @RequestBody @Valid CustomRoleDefinitionForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(Map.of("message", bindingResult.getAllErrors().get(0).getDefaultMessage()));
        }
        form.setId(roleId);
        try {
            Role savedRole = customRoleDefinitionService.saveCustomRole(form, getSysUserId(request));
            return ResponseEntity.ok(toSummary(savedRole));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<?> deleteCustomRole(HttpServletRequest request, @PathVariable String roleId) {
        try {
            customRoleDefinitionService.deleteCustomRole(roleId, getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private Map<String, Object> toSummary(Role role) {
        return Map.of("id", role.getId(), "name", StringUtils.defaultString(role.getName()), "description",
                StringUtils.defaultString(role.getDescription()));
    }

    private Map<String, Object> toLabUnitSummary(IdValuePair labUnit) {
        return Map.of("id", labUnit.getId(), "name", StringUtils.defaultString(labUnit.getValue()));
    }
}
