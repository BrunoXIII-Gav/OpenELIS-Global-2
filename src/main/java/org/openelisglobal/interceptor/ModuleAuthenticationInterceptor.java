package org.openelisglobal.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.validator.BaseErrors;
import org.openelisglobal.administration.service.AdministrationAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.login.dao.UserModuleService;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.security.service.ModuleAccessService;
import org.openelisglobal.systemmodule.service.SystemModuleUrlService;
import org.openelisglobal.systemmodule.valueholder.SystemModuleParam;
import org.openelisglobal.systemmodule.valueholder.SystemModuleUrl;
import org.openelisglobal.systemusermodule.service.PermissionModuleService;
import org.openelisglobal.systemusermodule.valueholder.PermissionModule;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.method.HandlerMethod;

@Component
@Qualifier(value = "ModuleAuthenticationInterceptor")
public class ModuleAuthenticationInterceptor implements HandlerInterceptor {

    private static final boolean USE_PARAMETERS = true;

    // whether to reject access to protected pages if no modules are assigned
    public static final boolean REQUIRE_MODULE = true;

    private RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Autowired
    private UserModuleService userModuleService;
    @Autowired
    private SystemModuleUrlService systemModuleUrlService;
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private PermissionModuleService<PermissionModule> permissionModuleService;
    @Autowired
    private ModuleAccessService moduleAccessService;
    @Autowired
    private AdministrationAuthorizationService administrationAuthorizationService;
    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String resolvedPath = resolveRequestPath(request);
        if (isAdministrationRequest(handler) && !isOrderEntryReferenceDataRequest(request)
                && !hasAdministrationPermission(request)) {
            rejectAdministrationRequest(response);
            return false;
        }
        Errors errors = new BaseErrors();
        if (!hasPermission(errors, request) && !hasSemanticPermissionFallback(request, resolvedPath)) {
            LogEvent.logInfo("ModuleAuthenticationInterceptor", "preHandle()",
                    "======> NOT ALLOWED ACCESS TO THIS MODULE");
            LogEvent.logInfo(this.getClass().getSimpleName(), "preHandle", "has no permission"); //
            if (isRestFullPath(resolvedPath)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                String jsonResponse = "{ \"status\": 401, \"message\": \"Not Authorized\" }";
                response.getWriter().write(jsonResponse);
                response.getWriter().flush();
            } else {
                redirectStrategy.sendRedirect(request, response, "/Home?access=denied");
            }
            return false;
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
            ModelAndView modelAndView) {
    }

    protected boolean hasPermission(Errors errors, HttpServletRequest request) {
        if (ConfigurationProperties.getInstance().getPropertyValue("permissions.agent").equalsIgnoreCase("ROLE")) {
            return hasPermissionForUrl(request, USE_PARAMETERS) || userModuleService.isUserAdmin(request);
        } else {
            return userModuleService.isVerifyUserModule(request) || userModuleService.isUserAdmin(request);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasPermissionForUrl(HttpServletRequest request, boolean useParameters) {
        Set<String> accessMap = (Set<String>) request.getSession().getAttribute(IActionConstants.PERMITTED_ACTIONS_MAP);
        if (accessMap == null) {
            accessMap = (Set<String>) request.getAttribute(IActionConstants.PERMITTED_ACTIONS_MAP);
        }

        if (accessMap == null || accessMap.isEmpty()) {
            Set<String> permittedPages = getPermittedForms(getSysUserId(request));
            accessMap = permittedPages;
            request.getSession().setAttribute(IActionConstants.PERMITTED_ACTIONS_MAP, new HashSet<>(permittedPages));
        }
        List<SystemModuleUrl> sysModsByUrl = systemModuleUrlService.getByRequest(request);

        if (useParameters) {
            sysModsByUrl = filterParamMatches(request, sysModsByUrl);
        }
        if (sysModsByUrl.isEmpty() && REQUIRE_MODULE) {
            if (isRestFullPath(resolveRequestPath(request))) {
                return true;
            }
            LogEvent.logWarn("ModuleAuthenticationInterceptor", "hasPermissionForUrl()",
                    "This page has no modules assigned to it");
            return false;
        }
        for (SystemModuleUrl sysModUrl : sysModsByUrl) {
            if (accessMap.contains(sysModUrl.getSystemModule().getSystemModuleName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSemanticPermissionFallback(HttpServletRequest request, String resolvedPath) {
        if (moduleAccessService == null) {
            return false;
        }
        String targetUrl = resolvedPath;
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            targetUrl = targetUrl + "?" + request.getQueryString();
        }
        return moduleAccessService.canAccess(targetUrl, request).isAllowed();
    }

    private boolean isAdministrationRequest(Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return false;
        }
        String packageName = handlerMethod.getBeanType().getPackageName();
        return packageName.startsWith("org.openelisglobal.testconfiguration.controller.rest")
                || packageName.startsWith("org.openelisglobal.analyzer.controller")
                || packageName.startsWith("org.openelisglobal.analyzerimport.controller.rest")
                || packageName.startsWith("org.openelisglobal.barcode.controller.rest")
                || packageName.startsWith("org.openelisglobal.dictionary.controller.rest")
                || packageName.startsWith("org.openelisglobal.externalconnections.controller.rest")
                || packageName.startsWith("org.openelisglobal.logo.controller.rest")
                || packageName.startsWith("org.openelisglobal.notification.controller.rest")
                || packageName.startsWith("org.openelisglobal.organization.controller.rest")
                || packageName.startsWith("org.openelisglobal.professionalprofile.controller.rest")
                || packageName.startsWith("org.openelisglobal.provider.controller.rest")
                || packageName.startsWith("org.openelisglobal.resultreporting.controller.rest")
                || packageName.startsWith("org.openelisglobal.role.controller.rest")
                || packageName.startsWith("org.openelisglobal.sitebranding.controller.rest")
                || packageName.startsWith("org.openelisglobal.siteinformation.controller.rest")
                || packageName.startsWith("org.openelisglobal.systemuser.controller.rest")
                || packageName.startsWith("org.openelisglobal.testcalculated.controller.rest")
                || packageName.startsWith("org.openelisglobal.testdependency.controller.rest")
                || packageName.startsWith("org.openelisglobal.testreflex.controller.rest");
    }

    /**
     * These read-only sample/UOM and requester-profile lookups are needed to
     * build an order, but their controllers also contain administration-only
     * configuration endpoints.
     */
    private boolean isOrderEntryReferenceDataRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }

        String path = resolveRequestPath(request);
        if (!List.of("/rest/sample-type-uoms", "/rest/sample-type-uoms/assignments", "/rest/providers/form-config")
                .contains(path)) {
            return false;
        }

        UserSessionData userSessionData = getUserSessionData(request);
        if (userSessionData == null) {
            return false;
        }

        String userId = Integer.toString(userSessionData.getSystemUserId());
        return moduleAuthorizationService.hasPermission(userId, "orders", "create")
                || moduleAuthorizationService.hasPermission(userId, "orders", "update");
    }

    private boolean hasAdministrationPermission(HttpServletRequest request) {
        if (userModuleService.isSessionExpired(request)) {
            return false;
        }
        if (userModuleService.isUserAdmin(request)) {
            return true;
        }
        UserSessionData userSessionData = getUserSessionData(request);
        if (userSessionData == null) {
            return false;
        }
        String actionKey = "GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())
                ? "read"
                : "manage";
        return administrationAuthorizationService.hasPermission(Integer.toString(userSessionData.getSystemUserId()),
                actionKey);
    }

    private void rejectAdministrationRequest(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{ \"status\": 403, \"message\": \"Forbidden\" }");
        response.getWriter().flush();
    }

    private UserSessionData getUserSessionData(HttpServletRequest request) {
        UserSessionData userSessionData = (UserSessionData) request.getSession()
                .getAttribute(IActionConstants.USER_SESSION_DATA);
        if (userSessionData == null) {
            userSessionData = (UserSessionData) request.getAttribute(IActionConstants.USER_SESSION_DATA);
        }
        return userSessionData;
    }

    private String resolveRequestPath(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        String pathInfo = request.getPathInfo();
        String resolvedPath = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);

        if (resolvedPath.isBlank()) {
            resolvedPath = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (contextPath != null && !contextPath.isBlank() && resolvedPath.startsWith(contextPath)) {
                resolvedPath = resolvedPath.substring(contextPath.length());
            }
        }

        if (!resolvedPath.startsWith("/")) {
            resolvedPath = "/" + resolvedPath;
        }

        return resolvedPath;
    }

    private List<SystemModuleUrl> filterParamMatches(HttpServletRequest request, List<SystemModuleUrl> sysModsByUrl) {
        List<SystemModuleUrl> filteredSysModsByUrl = new ArrayList<>();
        for (SystemModuleUrl sysModUrl : sysModsByUrl) {
            boolean matchAll = true;
            SystemModuleParam param = sysModUrl.getParam();
            if (param != null) {
                if (!param.getValue().equals(request.getParameter(param.getName()))) {
                    matchAll = false;
                }
            }
            if (matchAll) {
                filteredSysModsByUrl.add(sysModUrl);
            }
        }
        return filteredSysModsByUrl;
    }

    private Set<String> getPermittedForms(int systemUserId) {
        Set<String> allPermittedPages = new HashSet<>();

        List<String> roleIds = userRoleService.getRoleIdsForUser(Integer.toString(systemUserId));

        for (String roleId : roleIds) {
            Set<String> permittedPagesForRole = permissionModuleService
                    .getAllPermittedPagesFromAgentId(Integer.parseInt(roleId));
            allPermittedPages.addAll(permittedPagesForRole);
        }

        return allPermittedPages;
    }

    protected int getSysUserId(HttpServletRequest request) {
        UserSessionData usd = (UserSessionData) request.getSession().getAttribute(IActionConstants.USER_SESSION_DATA);
        if (usd == null) {
            usd = (UserSessionData) request.getAttribute(IActionConstants.USER_SESSION_DATA);
            if (usd == null) {
                return 0;
            }
        }
        return usd.getSystemUserId();
    }

    private boolean isRestFullPath(String resolvedPath) {
        if (resolvedPath.startsWith("/rest") || resolvedPath.startsWith("/Provider")) {
            return true;
        }
        return false;
    }
}
