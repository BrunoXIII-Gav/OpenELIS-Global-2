package org.openelisglobal.common.constants;

import java.util.List;

public enum SystemPermission {
    GLOBAL_ADMIN(List.of(Constants.ROLE_GLOBAL_ADMIN)),
    ADMINISTRATION(List.of(Constants.ROLE_ADMINISTRATION, Constants.ROLE_GLOBAL_ADMIN)),
    ANALYSER_IMPORT(List.of(Constants.ROLE_ANALYSER_IMPORT)),
    RECEPTION(List.of(Constants.ROLE_RECEPTION)),
    GENERIC_SAMPLE(List.of(Constants.ROLE_GENERIC_SAMPLE, Constants.ROLE_RECEPTION)),
    ORDER(List.of(Constants.ROLE_ORDER, Constants.ROLE_RECEPTION)),
    PATIENT(List.of(Constants.ROLE_PATIENT, Constants.ROLE_RECEPTION)),
    SAMPLE_MANAGEMENT(List.of(Constants.ROLE_RECEPTION, Constants.ROLE_RESULTS)),
    RESULTS(List.of(Constants.ROLE_RESULTS)),
    REPORTS(List.of(Constants.ROLE_REPORTS)),
    ALIQUOT(List.of(Constants.ROLE_ALIQUOT, Constants.ROLE_RECEPTION)),
    STORAGE(List.of(Constants.ROLE_STORAGE, Constants.ROLE_RECEPTION, Constants.ROLE_RESULTS)),
    VALIDATION(List.of(Constants.ROLE_VALIDATION, Constants.ROLE_PATHOLOGIST));

    private final List<String> legacyRoleNames;

    SystemPermission(List<String> legacyRoleNames) {
        this.legacyRoleNames = legacyRoleNames;
    }

    public List<String> getLegacyRoleNames() {
        return legacyRoleNames;
    }
}
