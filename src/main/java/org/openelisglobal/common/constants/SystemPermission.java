package org.openelisglobal.common.constants;

import java.util.List;

public enum SystemPermission {
    GLOBAL_ADMIN(List.of(Constants.ROLE_GLOBAL_ADMIN)),
    ADMINISTRATION(List.of(Constants.ROLE_ADMINISTRATION, Constants.ROLE_GLOBAL_ADMIN)),
    ANALYSER_IMPORT(List.of(Constants.ROLE_ANALYSER_IMPORT)),
    RECEPTION(List.of(Constants.ROLE_RECEPTION)),
    GENERIC_SAMPLE(List.of(Constants.ROLE_GENERIC_SAMPLE, Constants.ROLE_RECEPTION)),
    SAMPLE_MANAGEMENT(List.of(Constants.ROLE_SAMPLE_MANAGEMENT)),
    ORDER(List.of(Constants.ROLE_ORDER, Constants.ROLE_RECEPTION)),
    ORDER_ADD(List.of(Constants.ROLE_ORDER_ADD)),
    ORDER_EDIT(List.of(Constants.ROLE_ORDER_EDIT)),
    PATIENT(List.of(Constants.ROLE_PATIENT, Constants.ROLE_RECEPTION)),
    PATIENT_MANAGEMENT(List.of(Constants.ROLE_PATIENT_MANAGEMENT)),
    PATIENT_HISTORY(List.of(Constants.ROLE_PATIENT_HISTORY)),
    RESULTS(List.of(Constants.ROLE_RESULTS)),
    RESULTS_BY_UNIT(List.of(Constants.ROLE_RESULTS_BY_UNIT)),
    RESULTS_BY_PATIENT(List.of(Constants.ROLE_RESULTS_BY_PATIENT)),
    RESULTS_BY_ORDER(List.of(Constants.ROLE_RESULTS_BY_ORDER)),
    REPORTS(List.of(Constants.ROLE_REPORTS)),
    ALIQUOT(List.of(Constants.ROLE_ALIQUOT, Constants.ROLE_RECEPTION)),
    STORAGE(List.of(Constants.ROLE_STORAGE, Constants.ROLE_RECEPTION, Constants.ROLE_RESULTS)),
    STORAGE_MANAGEMENT(List.of(Constants.ROLE_STORAGE_MANAGEMENT)),
    VALIDATION(List.of(Constants.ROLE_VALIDATION, Constants.ROLE_PATHOLOGIST)),
    VALIDATION_ROUTINE(List.of(Constants.ROLE_VALIDATION_ROUTINE)),
    VALIDATION_BY_ORDER(List.of(Constants.ROLE_VALIDATION_BY_ORDER));

    private final List<String> legacyRoleNames;

    SystemPermission(List<String> legacyRoleNames) {
        this.legacyRoleNames = legacyRoleNames;
    }

    public List<String> getLegacyRoleNames() {
        return legacyRoleNames;
    }
}
