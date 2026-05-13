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

        Map<String, String> targetParams = parseQueryParams(targetUrl);
        List<SystemModuleUrl> systemModuleUrls = systemModuleUrlService.getByUrlPath(normalizedPath);
        systemModuleUrls = filterParamMatches(systemModuleUrls, targetParams);

        if (systemModuleUrls.isEmpty()) {
            return ModuleAccessResult.denied();
        }

        Set<String> permittedModuleNames = getPermittedModules(request);
        boolean allowed = systemModuleUrls.stream().anyMatch(
                moduleUrl -> permittedModuleNames.contains(moduleUrl.getSystemModule().getSystemModuleName()));

        return allowed ? ModuleAccessResult.allowed() : ModuleAccessResult.denied();
    }

    @SuppressWarnings("unchecked")
    private Set<String> getPermittedModules(HttpServletRequest request) {
        Set<String> accessMap = (Set<String>) request.getSession().getAttribute(IActionConstants.PERMITTED_ACTIONS_MAP);
        if (accessMap == null) {
            accessMap = (Set<String>) request.getAttribute(IActionConstants.PERMITTED_ACTIONS_MAP);
        }
        if (accessMap != null) {
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
