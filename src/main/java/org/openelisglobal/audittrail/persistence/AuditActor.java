package org.openelisglobal.audittrail.persistence;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.valueholder.BaseObject;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Never accepts actor IDs or correlation IDs from client input. */
public record AuditActor(String id, String name, String kind, String requestId, String source) {

    private static final String REQUEST_ID = AuditActor.class.getName();

    public static AuditActor current(Object entity) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                && attributes.getRequest().getRequestURI() != null
                && !attributes.getRequest().getRequestURI().isBlank()) {
            HttpServletRequest request = attributes.getRequest();
            Object user = request.getSession(false) == null ? null
                    : request.getSession(false).getAttribute(IActionConstants.USER_SESSION_DATA);
            if (user == null) {
                user = request.getAttribute(IActionConstants.USER_SESSION_DATA);
            }
            String requestId = (String) request.getAttribute(REQUEST_ID);
            if (requestId == null) {
                requestId = UUID.randomUUID().toString();
                request.setAttribute(REQUEST_ID, requestId);
            }
            String source = request.getMethod() + " " + request.getRequestURI();
            if (user instanceof UserSessionData session && session.getSystemUserId() > 0) {
                return new AuditActor(String.valueOf(session.getSystemUserId()), session.getLoginName(), "USER",
                        requestId, source);
            }
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication();
            if (authentication != null && authentication.isAuthenticated()
                    && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
                // SSO can provision the local account before UserSessionData exists.
                // Preserve the verified external principal instead of a guessed local ID.
                return new AuditActor(null, authentication.getName(), "IDENTITY_PROVIDER", requestId, source);
            }
            throw new IllegalStateException("Authenticated actor is required for audited request changes");
        }
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            return new AuditActor(null, authentication.getName(), "IDENTITY_PROVIDER", null, "authentication");
        }
        // Background/import tasks remain distinguishable from authenticated web
        // actions.
        String actorId = entity instanceof BaseObject<?> base ? base.getSysUserId() : null;
        return new AuditActor(actorId, null, actorId == null ? "SYSTEM" : "SERVICE", null, "background");
    }
}
