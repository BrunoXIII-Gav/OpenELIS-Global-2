package org.openelisglobal.patient.form;

import java.util.Set;

/** Effective Patients-module permissions and data restrictions for the current user. */
public record PatientModuleAccess(boolean canRead, boolean canCreate, boolean canUpdate,
        Set<String> restrictedFieldGroupKeys, Set<String> restrictedFieldTagKeys) {
}
