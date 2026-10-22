package org.openelisglobal.sample.service;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Separates order registration lookup from patient editing and clinical access.
 */
public interface OrderPatientContextService {
    boolean isOrderEntryLookup(HttpServletRequest request, String userId);

    boolean canUpdatePatient(HttpServletRequest request, String userId, String patientId);

    void rememberRegistration(HttpServletRequest request, String userId, String patientId);

    boolean canAccessRegisteredPatient(HttpServletRequest request, String userId, String patientId);

    void forgetRegistration(HttpServletRequest request, String userId, String patientId);
}
