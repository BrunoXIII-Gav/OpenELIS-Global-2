package org.openelisglobal.patient.service;

import java.util.Set;

/** Applies Patients module permissions before exposing patient data. */
public interface PatientAuthorizationService {

    /**
     * Module-level permission for patient registration/demographics, independent of
     * analyses.
     */
    boolean hasPermission(String userId, String actionKey);

    /** Clinical record access, including the configured laboratory-unit scope. */
    boolean canAccessPatient(String userId, String patientId, String actionKey);

    Set<String> getRestrictedSearchCriteria(String userId);
}
