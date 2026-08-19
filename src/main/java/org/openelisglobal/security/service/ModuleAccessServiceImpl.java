package org.openelisglobal.security.service;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.login.dao.UserModuleService;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.systemmodule.service.SystemModuleUrlService;
import org.openelisglobal.systemmodule.valueholder.SystemModuleParam;
import org.openelisglobal.systemmodule.valueholder.SystemModuleUrl;
import org.openelisglobal.systemusermodule.service.PermissionModuleService;
import org.openelisglobal.systemusermodule.valueholder.PermissionModule;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ModuleAccessServiceImpl implements ModuleAccessService {

    @Autowired
    private UserModuleService userModuleService;

    @Autowired
    private SystemModuleUrlService systemModuleUrlService;

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private PermissionModuleService<PermissionModule> permissionModuleService;
    @Autowired
    private UserPermissionService userPermissionService;

    @Override
    public ModuleAccessResult canAccess(String targetUrl, HttpServletRequest request) {
        if (userModuleService.isSessionExpired(request)) {
            return ModuleAccessResult.unauthorized("Session expired");
        }

        if (userModuleService.isUserAdmin(request)) {
            return ModuleAccessResult.allowed();
        }

        String normalizedPath = normalizePath(targetUrl);
        if (GenericValidator.isBlankOrNull(normalizedPath)) {
            return ModuleAccessResult.badRequest("Invalid target URL");
        }

        if ("/".equals(normalizedPath) || "/Dashboard".equals(normalizedPath) || "/Home".equals(normalizedPath)) {
            return ModuleAccessResult.allowed();
        }

        String userId = Integer.toString(getSysUserId(request));
        Map<String, String> targetParams = parseQueryParams(targetUrl);
        if (isPatientAnalysisReportRequest(normalizedPath, targetParams)) {
            boolean reportPrintAllowed = userPermissionService.hasPermission(userId, SystemPermission.VALIDATION)
                    || userPermissionService.hasPermission(userId, SystemPermission.RESULTS)
                    || userPermissionService.hasPermission(userId, SystemPermission.REPORTS);
            if (reportPrintAllowed) {
                return ModuleAccessResult.allowed();
            }
        }

        SystemPermission semanticPermission = resolveSemanticPermission(normalizedPath, targetParams);
        List<SystemModuleUrl> systemModuleUrls = systemModuleUrlService.getByUrlPath(normalizedPath);
        systemModuleUrls = filterParamMatches(systemModuleUrls, targetParams);

        if (systemModuleUrls.isEmpty()) {
            if (semanticPermission != null && userPermissionService.hasPermission(userId, semanticPermission)) {
                return ModuleAccessResult.allowed();
            }
            return ModuleAccessResult.denied();
        }

        Set<String> permittedModuleNames = getPermittedModules(request);
        boolean allowed = systemModuleUrls.stream().anyMatch(
                moduleUrl -> permittedModuleNames.contains(moduleUrl.getSystemModule().getSystemModuleName()));

        if (!allowed && semanticPermission != null) {
            allowed = userPermissionService.hasPermission(userId, semanticPermission);
        }

        return allowed ? ModuleAccessResult.allowed() : ModuleAccessResult.denied();
    }

    private SystemPermission resolveSemanticPermission(String normalizedPath, Map<String, String> targetParams) {
        if (GenericValidator.isBlankOrNull(normalizedPath)) {
            return null;
        }

        if (normalizedPath.startsWith("/MasterListsPage") || "/admin".equals(normalizedPath)) {
            return SystemPermission.ADMINISTRATION;
        }
        if (isLegacyAdministrationEndpoint(normalizedPath)) {
            return SystemPermission.ADMINISTRATION;
        }
        if (normalizedPath.startsWith("/analyzers")) {
            return SystemPermission.ADMINISTRATION;
        }
        if (normalizedPath.startsWith("/GenericSample/")) {
            return SystemPermission.GENERIC_SAMPLE;
        }
        if ("/AnalyzerResults".equals(normalizedPath)) {
            return SystemPermission.ANALYSER_IMPORT;
        }
        if (List.of("/SamplePatientEntry", "/ModifyOrder", "/SampleEdit", "/SampleBatchEntrySetup",
                "/ElectronicOrders", "/PrintBarcode").contains(normalizedPath)) {
            return SystemPermission.ORDER;
        }
        if (List.of("/PatientManagement", "/PatientHistory").contains(normalizedPath)
                || normalizedPath.startsWith("/PatientResults/")) {
            return SystemPermission.PATIENT;
        }
        if ("/SampleManagement".equals(normalizedPath)) {
            return SystemPermission.SAMPLE_MANAGEMENT;
        }
        if ("/Aliquot".equals(normalizedPath)) {
            return SystemPermission.ALIQUOT;
        }
        if (List.of("/RoutineReports", "/RoutineReport", "/StudyReports", "/StudyReport", "/Report",
                "/AuditTrailReport").contains(normalizedPath)
                || normalizedPath.startsWith("/reports")) {
            return SystemPermission.REPORTS;
        }
        if (isReportPrintPath(normalizedPath)) {
            if (isPatientAnalysisReportRequest(normalizedPath, targetParams)) {
                return SystemPermission.VALIDATION;
            }
            return SystemPermission.REPORTS;
        }
        if (normalizedPath.startsWith("/Storage")) {
            return SystemPermission.STORAGE;
        }
        if (normalizedPath.startsWith("/FreezerMonitoring") || normalizedPath.startsWith("/rest/storage")
                || normalizedPath.startsWith("/rest/freezer-monitoring")) {
            return SystemPermission.STORAGE;
        }
        if (List.of("/WorkPlanByTestSection", "/WorkplanByTest", "/WorkplanByPanel", "/WorkplanByPriority",
                "/PrintWorkplanReport").contains(normalizedPath)) {
            return SystemPermission.RESULTS;
        }
        if (List.of("/validation", "/ResultValidation", "/AccessionValidation", "/AccessionValidationRange",
                "/ResultValidationByTestDate").contains(normalizedPath)) {
            return SystemPermission.VALIDATION;
        }
        if (List.of("/result", "/LogbookResults", "/PatientResults", "/AccessionResults", "/StatusResults",
                "/RangeResults", "/ReferredOutTests").contains(normalizedPath)) {
            return SystemPermission.RESULTS;
        }
        return null;
    }

    private boolean isLegacyAdministrationEndpoint(String normalizedPath) {
        return List.of("/TestAdd", "/TestModifyEntry", "/TestCatalog", "/TestActivation", "/TestOrderability",
                "/TestRenameEntry", "/TestSectionCreate", "/TestSectionRenameEntry", "/TestSectionTestAssign",
                "/TestSectionOrder", "/PanelCreate", "/PanelRenameEntry", "/PanelTestAssign", "/PanelOrder",
                "/SampleTypeCreate", "/SampleTypeRenameEntry", "/SampleTypeTestAssign", "/SampleTypeUomAssign",
                "/SampleTypeOrder", "/MethodRenameEntry", "/MethodTestAssign", "/UomCreate", "/UomRenameEntry",
                "/SelectListRenameEntry", "/ResultSelectListAdd", "/SaveResultSelectList", "/EntityNamesProvider",
                "/TestNamesProvider", "/UnifiedSystemUserMenu", "/SearchUnifiedSystemUserMenu",
                "/UnifiedSystemUser", "/DeleteUnifiedSystemUser", "/OrganizationMenu", "/SearchOrganizationMenu",
                "/Organization", "/DeleteOrganization", "/NextPreviousOrganization", "/CancelOrganization",
                "/NonConformityConfigurationMenu", "/MenuStatementConfigMenu", "/WorkplanConfigurationMenu",
                "/PrintedReportsConfigurationMenu", "/SampleEntryConfigMenu", "/ResultConfigurationMenu",
                "/PatientConfigurationMenu", "/ValidationConfigurationMenu", "/SiteInformationMenu",
                "/BarcodeConfiguration", "/ResultReportingConfiguration")
                .contains(normalizedPath);
    }

    private boolean isPatientAnalysisReportRequest(String normalizedPath, Map<String, String> targetParams) {
        if (!isReportPrintPath(normalizedPath)) {
            return false;
        }
        if (!"patient".equalsIgnoreCase(targetParams.getOrDefault("type", ""))) {
            return false;
        }
        return !GenericValidator.isBlankOrNull(targetParams.get("analysisIds"))
                || !GenericValidator.isBlankOrNull(targetParams.get("previewAnalysisIds"));
    }

    private boolean isReportPrintPath(String normalizedPath) {
        if (GenericValidator.isBlankOrNull(normalizedPath)) {
            return false;
        }
        return "/ReportPrint".equals(normalizedPath) || "/ReportPrintNamed".equals(normalizedPath)
                || normalizedPath.startsWith("/ReportPrintFile/");
    }

    @SuppressWarnings("unchecked")
    private Set<String> getPermittedModules(HttpServletRequest request) {
        Set<String> accessMap = (Set<String>) request.getSession().getAttribute(IActionConstants.PERMITTED_ACTIONS_MAP);
        if (accessMap == null) {
            accessMap = (Set<String>) request.getAttribute(IActionConstants.PERMITTED_ACTIONS_MAP);
        }
        if (accessMap != null && !accessMap.isEmpty()) {
            return accessMap;
        }

        int systemUserId = getSysUserId(request);
        Set<String> permittedPages = new HashSet<>();
        List<String> roleIds = userRoleService.getRoleIdsForUser(Integer.toString(systemUserId));
        for (String roleId : roleIds) {
            Set<String> permittedForRole = permissionModuleService
                    .getAllPermittedPagesFromAgentId(Integer.parseInt(roleId));
            permittedPages.addAll(permittedForRole);
        }
        return permittedPages;
    }

    private int getSysUserId(HttpServletRequest request) {
        UserSessionData usd = (UserSessionData) request.getSession().getAttribute(IActionConstants.USER_SESSION_DATA);
        if (usd == null) {
            usd = (UserSessionData) request.getAttribute(IActionConstants.USER_SESSION_DATA);
            if (usd == null) {
                return 0;
            }
        }
        return usd.getSystemUserId();
    }

    private List<SystemModuleUrl> filterParamMatches(List<SystemModuleUrl> systemModuleUrls,
            Map<String, String> params) {
        return systemModuleUrls.stream().filter(moduleUrl -> {
            SystemModuleParam requiredParam = moduleUrl.getParam();
            if (requiredParam == null) {
                return true;
            }
            String actualValue = params.get(requiredParam.getName());
            return requiredParam.getValue().equals(actualValue);
        }).toList();
    }

    private String normalizePath(String targetUrl) {
        String raw = targetUrl;
        if (GenericValidator.isBlankOrNull(raw)) {
            return null;
        }

        raw = raw.trim();
        if (raw.startsWith("http://") || raw.startsWith("https://")) {
            try {
                URI uri = URI.create(raw);
                raw = uri.getPath();
                if (GenericValidator.isBlankOrNull(raw)) {
                    raw = "/";
                }
            } catch (IllegalArgumentException e) {
                return null;
            }
        } else {
            int queryIdx = raw.indexOf('?');
            if (queryIdx >= 0) {
                raw = raw.substring(0, queryIdx);
            }
        }

        if (!raw.startsWith("/")) {
            raw = "/" + raw;
        }
        raw = stripContextPrefix(raw);
        if (raw.contains(".do") || raw.contains(".html")) {
            raw = raw.substring(0, raw.lastIndexOf('.'));
        }
        if (raw.startsWith("/rest")) {
            raw = raw.substring("/rest".length());
            if (GenericValidator.isBlankOrNull(raw)) {
                raw = "/";
            }
        }
        return raw;
    }

    private String stripContextPrefix(String raw) {
        if (GenericValidator.isBlankOrNull(raw)) {
            return raw;
        }

        for (String prefix : List.of("/api/OpenELIS-Global", "/OpenELIS-Global")) {
            if (raw.startsWith(prefix + "/")) {
                return raw.substring(prefix.length());
            }
            if (raw.equals(prefix)) {
                return "/";
            }
        }

        return raw;
    }

    private Map<String, String> parseQueryParams(String targetUrl) {
        Map<String, String> params = new HashMap<>();
        if (GenericValidator.isBlankOrNull(targetUrl) || !targetUrl.contains("?")) {
            return params;
        }
        String query = targetUrl.substring(targetUrl.indexOf('?') + 1);
        if (GenericValidator.isBlankOrNull(query)) {
            return params;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            if (GenericValidator.isBlankOrNull(pair)) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }
            String key = pair.substring(0, idx);
            String value = pair.substring(idx + 1);
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }

        return params;
    }
}
