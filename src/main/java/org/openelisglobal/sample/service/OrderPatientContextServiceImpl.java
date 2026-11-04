package org.openelisglobal.sample.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.patient.service.PatientAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * A new patient has no analyses yet, so lab-unit patient scopes cannot resolve
 * them. Keep a bounded, temporary registration context in the authenticated
 * session, populated only after the server has successfully registered a
 * patient.
 */
@Service
public class OrderPatientContextServiceImpl implements OrderPatientContextService {
    private static final String SESSION_KEY = OrderPatientContextServiceImpl.class.getName() + ".registrations";
    private static final long TTL = Duration.ofHours(2).toMillis();
    private static final int MAX_PATIENTS = 32;

    @Autowired
    private OrderAuthorizationService authorization;

    @Autowired
    private PatientAuthorizationService patientAuthorization;

    @Override
    public boolean isOrderEntryLookup(HttpServletRequest request, String userId) {
        if (!"true".equals(request.getParameter("forOrderEntry")))
            return false;
        if (!authorization.hasPermission(userId, "create"))
            throw new AccessDeniedException("Cannot look up order patients");
        return true;
    }

    @Override
    public boolean canUpdatePatient(HttpServletRequest request, String userId, String patientId) {
        return patientAuthorization.hasPermission(userId, "update");
    }

    protected long now() {
        return System.currentTimeMillis();
    }

    @Override
    public void rememberRegistration(HttpServletRequest request, String userId, String patientId) {
        if (!authorization.hasPermission(userId, "create"))
            throw new AccessDeniedException("Cannot register order patients");
        if (StringUtils.isBlank(userId) || !StringUtils.isNumeric(patientId))
            throw new IllegalArgumentException("Invalid registration context");
        HttpSession session = request.getSession();
        synchronized (session) {
            Map<String, Registration> registrations = registrations(session);
            registrations.put(patientId, new Registration(userId, now() + TTL));
            while (registrations.size() > MAX_PATIENTS)
                registrations.remove(registrations.keySet().iterator().next());
            session.setAttribute(SESSION_KEY, registrations);
        }
    }

    @Override
    public boolean canAccessRegisteredPatient(HttpServletRequest request, String userId, String patientId) {
        HttpSession session = request.getSession(false);
        if (session == null || StringUtils.isBlank(patientId) || StringUtils.isBlank(userId)
                || !authorization.hasPermission(userId, "create"))
            return false;
        synchronized (session) {
            Registration registration = registrations(session).get(patientId);
            return registration != null && userId.equals(registration.userId()) && registration.expiresAt() > now();
        }
    }

    @Override
    public void forgetRegistration(HttpServletRequest request, String userId, String patientId) {
        HttpSession session = request.getSession(false);
        if (session == null)
            return;
        synchronized (session) {
            Map<String, Registration> registrations = registrations(session);
            Registration registration = registrations.get(patientId);
            if (registration != null && registration.userId().equals(userId))
                registrations.remove(patientId);
            session.setAttribute(SESSION_KEY, registrations);
        }
    }

    private Map<String, Registration> registrations(HttpSession session) {
        Map<String, Registration> result = new LinkedHashMap<>();
        if (session.getAttribute(SESSION_KEY) instanceof Map<?, ?> stored)
            stored.forEach((key, value) -> {
                if (key instanceof String id && value instanceof Registration registration
                        && registration.expiresAt() > now())
                    result.put(id, registration);
            });
        return result;
    }

    private record Registration(String userId, long expiresAt) implements Serializable {
    }
}
