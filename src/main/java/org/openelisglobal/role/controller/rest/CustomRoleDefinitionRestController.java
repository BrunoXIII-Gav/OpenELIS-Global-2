package org.openelisglobal.role.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Comparator;
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
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldPayload;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldService;
import org.openelisglobal.security.service.AuthorizationCatalogService;
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
@PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).GLOBAL_ADMIN)")
public class CustomRoleDefinitionRestController extends BaseRestController {

    @Autowired
    private CustomRoleDefinitionService customRoleDefinitionService;

    @Autowired
    private AuthorizationCatalogService authorizationCatalogService;

    @Autowired
    private SampleTypeAdditionalFieldService sampleTypeAdditionalFieldService;

    @Autowired
    private PatientAdditionalFieldService patientAdditionalFieldService;

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
        response.put("moduleCatalog", authorizationCatalogService.getCatalog());
        response.put("fieldTagsByModule", getFieldTagsByModule());
        response.put("patientSearchCriteria", authorizationCatalogService.getPatientSearchCriteria());
        return response;
    }

    private Map<String, List<Map<String, String>>> getFieldTagsByModule() {
        List<Map<String, String>> sampleManagementTags = new ArrayList<>(authorizationCatalogService
                .getFieldTags("sample-management").stream()
                .map(tag -> Map.of("key", tag.key(), "labelKey", tag.labelKey())).toList());
        List<String> sampleTypeIds = DisplayListService.getInstance().getList(ListType.SAMPLE_TYPE_ACTIVE).stream()
                .map(IdValuePair::getId).filter(StringUtils::isNotBlank).toList();
        sampleTypeAdditionalFieldService.getActiveFieldsForSampleTypes(sampleTypeIds).values().stream()
                .flatMap(List::stream).filter(field -> field.getId() != null).map(this::toAdditionalFieldTag)
                .forEach(sampleManagementTags::add);
        sampleManagementTags.sort(Comparator.comparing(tag -> tag.getOrDefault("label", tag.get("key"))));
        List<Map<String, String>> patientTags = new ArrayList<>(authorizationCatalogService.getFieldTags("patients").stream()
                .map(tag -> Map.of("key", tag.key(), "labelKey", tag.labelKey())).toList());
        patientAdditionalFieldService.getFields(false, false).stream().filter(field -> field.getId() != null)
                .map(this::toPatientAdditionalFieldTag).forEach(patientTags::add);
        patientTags.sort(Comparator.comparing(tag -> tag.getOrDefault("label", tag.get("key"))));
        return Map.of("sample-management", sampleManagementTags, "patients", patientTags);
    }

    private Map<String, String> toAdditionalFieldTag(SampleTypeAdditionalFieldPayload field) {
        String label = StringUtils.defaultIfBlank(field.getDisplayName(), field.getFieldKey());
        return Map.of("key", "additional-field-" + field.getId(), "label", label);
    }

    private Map<String, String> toPatientAdditionalFieldTag(PatientAdditionalFieldPayload field) {
        String label = StringUtils.defaultIfBlank(field.getDisplayName(), field.getFieldKey());
        return Map.of("key", "patient-additional-field-" + field.getId(), "label", label);
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
        response.put("modulePermissions", customRoleDefinitionService.getModulePermissionsForCustomRole(roleId));
        response.put("restrictedPatientSearchCriteria",
                customRoleDefinitionService.getRestrictedPatientSearchCriteriaForCustomRole(roleId));
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
