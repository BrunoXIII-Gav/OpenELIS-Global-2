package org.openelisglobal.patient.service;

import java.util.Set;

/** Applies Patients module permissions before exposing patient data. */
public interface PatientAuthorizationService {

    boolean hasPermission(String userId, String actionKey);

    boolean canAccessPatient(String userId, String patientId, String actionKey);

    Set<String> getRestrictedSearchCriteria(String userId);
}
