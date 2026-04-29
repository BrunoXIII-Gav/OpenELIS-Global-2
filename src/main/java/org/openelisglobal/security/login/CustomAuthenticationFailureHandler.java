package org.openelisglobal.security.login;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import org.apache.http.HttpStatus;
import org.apache.commons.validator.GenericValidator;
import org.json.JSONObject;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.validator.BaseErrors;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {
        // get the X-Forwarded-For header so that we know if the request is from a proxy
        final String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            // no proxy
            LogEvent.logInfo(this.getClass().getSimpleName(), "onFailure",
                    "Unsuccessful login attempt from " + request.getRemoteAddr());
        } else {
            // from proxy
            LogEvent.logInfo(this.getClass().getSimpleName(), "onFailure",
                    "Unsuccessful login attempt from " + xfHeader.split(",")[0]);
        }
        if ("true".equals(request.getParameter("apiCall"))) {
            this.handleApiLogin(request, response, exception);
            return;
        } else {
            if (exception instanceof Saml2AuthenticationException) {
                Saml2AuthenticationException samlException = (Saml2AuthenticationException) exception;
                String errorCode = samlException.getSaml2Error() == null ? "unknown"
                        : samlException.getSaml2Error().getErrorCode();
                LogEvent.logWarn(this.getClass().getSimpleName(), "onFailure",
                        "SAML authentication failure. errorCode=" + errorCode + ", message="
                                + samlException.getMessage());
            }

            if (shouldRedirectSamlFailureToPortal(request, exception)) {
                // During global IdP logout, the browser may trigger SAML endpoints without a
                // resolvable registration id. Avoid showing generic login failure to users.
                RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
                redirectStrategy.sendRedirect(request, response, resolveFrontendRoot(request) + "sso-portal.html");
                return;
            }

            BaseErrors errors = new BaseErrors();
            if (exception instanceof UsernameNotFoundException) {
                errors.reject("login.error.message");
            } else if (exception instanceof BadCredentialsException) {
                errors.reject("login.error.message");
            } else if (exception instanceof CredentialsExpiredException) {
                errors.reject("login.error.password.expired");
            } else if (exception instanceof DisabledException) {
                errors.reject("login.error.account.disable");
            } else if (exception instanceof LockedException) {
                errors.reject("login.error.account.lock");
            } else {
                exception.printStackTrace();
                errors.reject("login.error.generic");
            }

            RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
            request.getSession().setAttribute(Constants.LOGIN_ERRORS, errors);
            redirectStrategy.sendRedirect(request, response, "/LoginPage");
        }
    }

    private boolean isSamlRegistrationLookupFailure(AuthenticationException exception) {
        if (!(exception instanceof Saml2AuthenticationException)) {
            return false;
        }
        Saml2AuthenticationException samlException = (Saml2AuthenticationException) exception;
        return samlException.getSaml2Error() != null
                && "relying_party_registration_not_found"
                        .equalsIgnoreCase(samlException.getSaml2Error().getErrorCode());
    }

    private boolean isSamlLogoutResponseRoutedToLoginEndpoint(HttpServletRequest request, AuthenticationException exception) {
        if (!(exception instanceof Saml2AuthenticationException)) {
            return false;
        }
        Saml2AuthenticationException samlException = (Saml2AuthenticationException) exception;
        String errorCode = samlException.getSaml2Error() == null ? null : samlException.getSaml2Error().getErrorCode();
        String message = samlException.getMessage() == null ? "" : samlException.getMessage();
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI();
        return "malformed_response_data".equalsIgnoreCase(errorCode)
                && uri.contains("/login/saml2/sso")
                && request.getParameter("SAMLResponse") != null
                && message.contains("LogoutResponseImpl");
    }

    private boolean shouldRedirectSamlFailureToPortal(HttpServletRequest request, AuthenticationException exception) {
        return isSamlRegistrationLookupFailure(exception)
                || isSamlLogoutResponseRoutedToLoginEndpoint(request, exception);
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

    private void handleApiLogin(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        PrintWriter out = response.getWriter();
        response.setContentType("application/json");
        response.setStatus(HttpStatus.SC_UNAUTHORIZED);
        JSONObject sessionDetails = new JSONObject();

        if (exception instanceof UsernameNotFoundException) {
            sessionDetails.put("error", "error.invalidcredentials");
        } else if (exception instanceof BadCredentialsException) {
            sessionDetails.put("error", "error.invalidcredentials");
        } else if (exception instanceof CredentialsExpiredException) {
            sessionDetails.put("error", "error.expiredCredentials");
        } else if (exception instanceof DisabledException) {
            sessionDetails.put("error", "error.disabledCredentials");
        } else if (exception instanceof LockedException) {
            sessionDetails.put("error", "error.lockedCredentials");
        } else {
            sessionDetails.put("error", "error.generic");
        }

        out.print(sessionDetails);
    }
}
