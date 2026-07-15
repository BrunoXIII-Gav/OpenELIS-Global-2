package org.openelisglobal.security.login;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.json.JSONObject;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.validator.GenericValidator;
import org.openelisglobal.common.validator.BaseErrors;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.security.SamlRoleMapping;
import org.openelisglobal.security.service.UserPermissionService;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.systemusermodule.service.PermissionModuleService;
import org.openelisglobal.systemusermodule.valueholder.PermissionModule;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.saml2.provider.service.authentication.DefaultSaml2AuthenticatedPrincipal;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.support.RequestContextUtils;

@Component
public class CustomSSOAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler
        implements IActionConstants {
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private PermissionModuleService<PermissionModule> permissionModuleService;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private UserPermissionService userPermissionService;

    @Value("${org.openelisglobal.timezone:}")
    private String timezone;
    @Value("${org.itech.login.saml.legacyRoleFallback:false}")
    private boolean samlLegacyRoleFallback;
    @Value("${org.itech.login.saml.redirect-to-frontend:true}")
    private boolean samlRedirectToFrontend;
    @Value("${org.itech.login.saml.portal-logout-url:}")
    private String samlPortalLogoutUrl;

    public static final int DEFAULT_SESSION_TIMEOUT_IN_MINUTES = 20;

    public CustomSSOAuthenticationSuccessHandler() {
        super();
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        // get the X-Forwarded-For header so that we know if the request is from a proxy
        final String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            // no proxy
            LogEvent.logInfo(this.getClass().getSimpleName(), "onSuccess",
                    "Successful login attempt for " + authentication.getName() + " from " + request.getRemoteAddr());
        } else {
            // from proxy
            LogEvent.logInfo(this.getClass().getSimpleName(), "onSuccess",
                    "Successful login attempt for " + authentication.getName() + " from " + xfHeader.split(",")[0]);
        }

        // String homePath = "/Dashboard";
        // LoginUser loginInfo = null;
        boolean apiLogin = "true".equals(request.getParameter("apiCall"));
        boolean samlLogin = false;
        boolean oauthLogin = false;

        SecurityContextHolder.getContext().setAuthentication(authentication);
        if (authentication != null) {
            Object principal = authentication.getPrincipal();

            if (principal instanceof DefaultSaml2AuthenticatedPrincipal) {
                DefaultSaml2AuthenticatedPrincipal samlUser = (DefaultSaml2AuthenticatedPrincipal) principal;
                // loginInfo = loginService.getUserProfile(samlUser.getName());
                request.getSession().setAttribute("samlSession", true);
                samlLogin = true;
                try {

                    setupUserSession(request, samlUser);
                } catch (IllegalStateException e) {
                    LogEvent.logError(this.getClass().getSimpleName(), "onAuthenticationSuccess",
                            "the login user doesn't exist in OE this is usually caused by login being handled by an"
                                    + " external application that contains a user that OE is missing");
                    SecurityContextHolder.getContext().setAuthentication(null);
                    if (redirectToPortalAccessDenied(request, response, "openelis")) {
                        return;
                    }
                    BaseErrors errors = new BaseErrors();
                    errors.reject("login.error.noOeUser");

                    request.getSession().setAttribute(Constants.LOGIN_ERRORS, errors);
                    getRedirectStrategy().sendRedirect(request, response, "/LoginPage");
                    return;
                } catch (RuntimeException e) {
                    LogEvent.logError(e);

                    SecurityContextHolder.getContext().setAuthentication(null);
                    BaseErrors errors = new BaseErrors();
                    errors.reject("login.error.sessionsetup");

                    request.getSession().setAttribute(Constants.LOGIN_ERRORS, errors);
                    getRedirectStrategy().sendRedirect(request, response, "/LoginPage");
                    return;
                }
            } else if (principal instanceof DefaultOAuth2User) {
                DefaultOAuth2User oauthUser = (DefaultOAuth2User) principal;
                // loginInfo =
                // loginService.getUserProfile(oauthUser.getAttribute("preferred_username"));
                request.getSession().setAttribute("oauthSession", true);
                oauthLogin = true;
                try {
                    setupUserSession(request, oauthUser);
                } catch (IllegalStateException e) {
                    LogEvent.logError(this.getClass().getSimpleName(), "onAuthenticationSuccess",
                            "the login user doesn't exist in OE this is usually caused by login being handled by an"
                                    + " external application that contains a user that OE is missing");
                    SecurityContextHolder.getContext().setAuthentication(null);
                    if (redirectToPortalAccessDenied(request, response, "openelis")) {
                        return;
                    }
                    BaseErrors errors = new BaseErrors();
                    errors.reject("login.error.noOeUser");

                    request.getSession().setAttribute(Constants.LOGIN_ERRORS, errors);
                    getRedirectStrategy().sendRedirect(request, response, "/LoginPage");
                    return;
                } catch (RuntimeException e) {
                    LogEvent.logError(e);

                    SecurityContextHolder.getContext().setAuthentication(null);
                    BaseErrors errors = new BaseErrors();
                    errors.reject("login.error.sessionsetup");

                    request.getSession().setAttribute(Constants.LOGIN_ERRORS, errors);
                    getRedirectStrategy().sendRedirect(request, response, "/LoginPage");
                    return;
                }

            }
        }

        // if (passwordExpiringSoon(loginInfo)) {
        // homePath += "?passReminder=true";
        // }

        if (apiLogin) {
            request.getSession().setAttribute("login_method", "form");
            this.handleApiLogin(request, response);
        } else if (samlLogin) {
            request.getSession().setAttribute("login_method", "samlLogin");
            getRedirectStrategy().sendRedirect(request, response, resolvePostLoginTarget(request));
        } else if (oauthLogin) {
            request.getSession().setAttribute("login_method", "oauthLogin");
            this.handleApiLogin(request, response);
        } else {
            // redirectStrategy.sendRedirect(request, response, homePath);
            super.onAuthenticationSuccess(request, response, authentication);
            clearCustomAuthenticationAttributes(request);
        }
    }

    private void handleApiLogin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PrintWriter out = response.getWriter();
        response.setContentType("application/json");

        out.print(new JSONObject().put("success", true));
    }

    private String resolvePostLoginTarget(HttpServletRequest request) {
        Object fullPageRedirect = request.getSession().getAttribute("saml_full_page_redirect");
        request.getSession().removeAttribute("saml_full_page_redirect");
        if (Boolean.TRUE.equals(fullPageRedirect) || "true".equals(String.valueOf(fullPageRedirect))
                || samlRedirectToFrontend) {
            return resolveFrontendRoot(request);
        }
        return "/Home";
    }

    private boolean redirectToPortalAccessDenied(HttpServletRequest request, HttpServletResponse response,
            String appKey) throws IOException {
        if (GenericValidator.isBlankOrNull(samlPortalLogoutUrl)) {
            return false;
        }

        String portalUrl = samlPortalLogoutUrl.trim();
        String separator = portalUrl.contains("?") ? "&" : "?";
        String redirectUrl = portalUrl + separator + "access_denied="
                + URLEncoder.encode(appKey, StandardCharsets.UTF_8);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
        return true;
    }

    private String resolveFrontendRoot(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (GenericValidator.isBlankOrNull(scheme)) {
            scheme = request.getScheme();
        } else {
            scheme = scheme.split(",")[0].trim();
        }

        String host = sanitizeHost(request.getHeader("Host"));
        if (GenericValidator.isBlankOrNull(host)) {
            host = sanitizeHost(request.getHeader("X-Forwarded-Host"));
        }

        if (GenericValidator.isBlankOrNull(host)) {
            host = request.getServerName();
            if ("_".equals(host) || "__".equals(host)) {
                return "/";
            }
            int serverPort = request.getServerPort();
            boolean includePort = serverPort > 0
                    && !((serverPort == 80 && "http".equalsIgnoreCase(scheme))
                            || (serverPort == 443 && "https".equalsIgnoreCase(scheme)));
            if (includePort) {
                host = host + ":" + serverPort;
            }
        }

        return scheme + "://" + host + "/";
    }

    private String sanitizeHost(String host) {
        if (GenericValidator.isBlankOrNull(host)) {
            return null;
        }
        String candidate = host.split(",")[0].trim();
        if ("_".equals(candidate) || "__".equals(candidate)) {
            return null;
        }
        return candidate;
    }

    private void setupUserSession(HttpServletRequest request, DefaultSaml2AuthenticatedPrincipal principal) {
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities();

        request.getSession().setMaxInactiveInterval(DEFAULT_SESSION_TIMEOUT_IN_MINUTES * 60);

        SystemUser systemUser = resolveLocalSystemUser(principal);
        boolean isAdmin = userPermissionService.hasPermission(systemUser.getId(), SystemPermission.GLOBAL_ADMIN);
        UserSessionData usd = new UserSessionData();
        String loginName = resolveLoginName(principal);
        usd.setSytemUserId(Integer.parseInt(systemUser.getId()));
        usd.setLoginName(loginName);
        usd.setElisUserName(systemUser.getNameForDisplay());
        usd.setUserTimeOut(DEFAULT_SESSION_TIMEOUT_IN_MINUTES * 60);
        usd.setAdmin(isAdmin);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, usd);
        request.getSession().setAttribute("timezone", timezone);

        if (ConfigurationProperties.getInstance().getPropertyValue("permissions.agent").equalsIgnoreCase("ROLE")) {
            Set<String> permittedPages = getPermittedForms(authorities, systemUser.getId());
            request.getSession().setAttribute(IActionConstants.PERMITTED_ACTIONS_MAP, permittedPages);
        }
    }

    private void setupUserSession(HttpServletRequest request, DefaultOAuth2User principal) {
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities();

        boolean isAdmin = false;
        for (GrantedAuthority authority : authorities) {
            String[] authorityExplode = authority.getAuthority().split("-");
            if (authorityExplode.length >= 2) {
                isAdmin = "admin".equalsIgnoreCase(authorityExplode[1]);
            }
        }
        request.getSession().setMaxInactiveInterval(DEFAULT_SESSION_TIMEOUT_IN_MINUTES * 60);

        UserSessionData usd = new UserSessionData();
        Optional<SystemUser> user = systemUserService.getMatch("loginName", principal.getName());
        SystemUser systemUser = new SystemUser();
        if (user.isEmpty()) {
            systemUser.setFirstName(principal.getName());
            systemUser.setLastName("");
            systemUser.setLoginName(principal.getName());
            systemUser.setIsActive("Y");
            systemUser.setIsEmployee("Y");
            systemUser.setExternalId("1");
            String initial = systemUser.getFirstName().substring(0, 1) + systemUser.getLastName().substring(0, 1);
            systemUser.setInitials(initial);
            systemUser.setSysUserId("1");

            systemUser = systemUserService.save(systemUser);
        } else {
            systemUser = user.get();
        }
        usd.setSytemUserId(Integer.parseInt(systemUser.getId()));
        usd.setLoginName(principal.getName());
        // usd.setElisUserName(su.getNameForDisplay());
        usd.setElisUserName(principal.getName());
        usd.setUserTimeOut(DEFAULT_SESSION_TIMEOUT_IN_MINUTES * 60);
        usd.setAdmin(isAdmin);
        request.getSession().setAttribute("authorities", usd);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, usd);
        request.getSession().setAttribute("timezone", timezone);

        // get permitted actions map (available modules for the current user)
        if (ConfigurationProperties.getInstance().getPropertyValue("permissions.agent").equalsIgnoreCase("ROLE")) {
            Set<String> permittedPages = getPermittedForms(authorities, systemUser.getId());
            request.getSession().setAttribute(IActionConstants.PERMITTED_ACTIONS_MAP, permittedPages);
            // showAdminMenu |= permittedPages.contains("MasterList");
        }
    }

    private String resolveLoginName(DefaultSaml2AuthenticatedPrincipal principal) {
        String username = resolvePrincipalAttribute(principal, "username", null, null);
        if (!GenericValidator.isBlankOrNull(username)) {
            return username;
        }
        if (!GenericValidator.isBlankOrNull(principal.getName())) {
            return principal.getName();
        }
        return null;
    }

    private String resolvePrincipalAttribute(DefaultSaml2AuthenticatedPrincipal principal, String attribute,
            String fallbackAttribute, String defaultValue) {
        Object value = attribute == null ? null : principal.getFirstAttribute(attribute);
        if (value == null && fallbackAttribute != null) {
            value = principal.getFirstAttribute(fallbackAttribute);
        }
        String resolved = value == null ? null : value.toString().trim();
        if (!GenericValidator.isBlankOrNull(resolved)) {
            return resolved;
        }
        return defaultValue;
    }

    private Set<String> getPermittedForms(Collection<? extends GrantedAuthority> authorities, String systemUserId) {
        Set<String> allPermittedPages = new HashSet<>();
        List<String> roleIds = new ArrayList<>();
        Set<String> seenRoleIds = new LinkedHashSet<>();

        if (!GenericValidator.isBlankOrNull(systemUserId)) {
            for (String roleId : userRoleService.getRoleIdsForUser(systemUserId)) {
                if (seenRoleIds.add(roleId)) {
                    roleIds.add(roleId);
                }
            }
        }

        if (roleIds.isEmpty() && samlLegacyRoleFallback) {
            roleIds.addAll(resolveRoleIds(authorities));
        }

        for (String roleId : roleIds) {
            Set<String> permittedPagesForRole = permissionModuleService
                    .getAllPermittedPagesFromAgentId(Integer.parseInt(roleId));
            allPermittedPages.addAll(permittedPagesForRole);
        }

        return allPermittedPages;
    }

    private SystemUser resolveLocalSystemUser(DefaultSaml2AuthenticatedPrincipal principal) {
        String loginName = resolveLoginName(principal);
        if (!GenericValidator.isBlankOrNull(loginName)) {
            Optional<SystemUser> user = systemUserService.getMatch("loginName", loginName);
            if (user.isPresent()) {
                return user.get();
            }
        }

        String principalName = principal.getName();
        if (!GenericValidator.isBlankOrNull(principalName)
                && (GenericValidator.isBlankOrNull(loginName) || !principalName.equalsIgnoreCase(loginName.trim()))) {
            Optional<SystemUser> user = systemUserService.getMatch("loginName", principalName.trim());
            if (user.isPresent()) {
                return user.get();
            }
        }

        String email = resolvePrincipalAttribute(principal, "email", null, null);
        if (!GenericValidator.isBlankOrNull(email)) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "resolveLocalSystemUser",
                    "SAML email attribute was provided for '" + email
                            + "' but email-based user matching is not enabled; local loginName match is required");
        }

        throw new IllegalStateException("No local OpenELIS user found for SAML principal");
    }

    private List<String> resolveRoleIds(Collection<? extends GrantedAuthority> authorities) {
        List<String> roleIds = new ArrayList<>();
        Set<String> seenRoleIds = new LinkedHashSet<>();

        for (String internalRoleName : SamlRoleMapping.toInternalRoleNames(authorities)) {
            Optional<Role> sysRole = roleService.getMatch("name", internalRoleName);
            if (sysRole.isPresent() && seenRoleIds.add(sysRole.get().getId())) {
                roleIds.add(sysRole.get().getId());
            }
        }

        return roleIds;
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

    private boolean passwordExpiringSoon(LoginUser loginInfo) {
        return loginInfo.getPasswordExpiredDayNo() <= Integer
                .parseInt(ConfigurationProperties.getInstance().getPropertyValue("login.user.expired.reminder.day"))
                && (loginInfo.getPasswordExpiredDayNo() > Integer.parseInt(
                        ConfigurationProperties.getInstance().getPropertyValue("login.user.change.allow.day")));
    }

    protected void clearCustomAuthenticationAttributes(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return;
        }
        session.removeAttribute("login_errors");
        session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
    }

    protected void addFlashMsgsToRequest(HttpServletRequest request) {
        Map<String, ?> inputFlashMap = RequestContextUtils.getInputFlashMap(request);
        if (inputFlashMap != null) {
            Boolean success = (Boolean) inputFlashMap.get(FWD_SUCCESS);
            request.setAttribute(FWD_SUCCESS, success);

            String successMessage = (String) inputFlashMap.get(Constants.SUCCESS_MSG);
            request.setAttribute(Constants.SUCCESS_MSG, successMessage);

            Errors errors = (Errors) inputFlashMap.get(Constants.REQUEST_ERRORS);
            request.setAttribute(Constants.SUCCESS_MSG, errors);

            List<String> messages = (List<String>) inputFlashMap.get(Constants.REQUEST_MESSAGES);
            request.setAttribute(Constants.REQUEST_MESSAGES, messages);

            List<String> warnings = (List<String>) inputFlashMap.get(Constants.REQUEST_WARNINGS);
            request.setAttribute(Constants.SUCCESS_MSG, warnings);
        }
    }
}
