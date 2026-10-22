package org.openelisglobal.patient.form;

/**
 * Values committed by patient registration; excludes persistence relationships.
 */
public record PatientRegistrationResponse(String patientPK, String cugPrefix, String guid, String patientLastUpdated,
        String personLastUpdated) {
}
