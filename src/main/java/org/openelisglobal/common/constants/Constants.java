package org.openelisglobal.common.constants;

public class Constants {

    private Constants() {
        // hide public constructor
    }

    public static final String SUCCESS_MSG = "successMessage";
    public static final String REQUEST_MESSAGES = "requestMessages"; // unimplemented in display layer
    public static final String REQUEST_WARNINGS = "requstWarnings";
    public static final String REQUEST_ERRORS = "requestErrors";
    public static final String LOGIN_ERRORS = "loginErrors";
    // all active roles
    public static final String ROLE_GLOBAL_ADMIN = "Global Administrator";
    public static final String ROLE_USER_ACCOUNT_ADMIN = "User Account Administrator";
    public static final String ROLE_AUDIT_TRAIL = "Audit Trail";
    public static final String ROLE_ADMINISTRATION = "Administration";
    public static final String ROLE_ANALYSER_IMPORT = "Analyser Import";
    public static final String ROLE_RECEPTION = "Reception";
    public static final String ROLE_GENERIC_SAMPLE = "Generic Sample";
    public static final String ROLE_SAMPLE_MANAGEMENT = "Sample Management";
    public static final String ROLE_ORDER = "Order";
    public static final String ROLE_ORDER_ADD = "Order Add";
    public static final String ROLE_ORDER_EDIT = "Order Edit";
    public static final String ROLE_PATIENT = "Patient";
    public static final String ROLE_PATIENT_MANAGEMENT = "Patient Management";
    public static final String ROLE_PATIENT_HISTORY = "Patient History";
    public static final String ROLE_RESULTS = "Results";
    public static final String ROLE_RESULTS_BY_UNIT = "Results By Unit";
    public static final String ROLE_RESULTS_BY_PATIENT = "Results By Patient";
    public static final String ROLE_RESULTS_BY_ORDER = "Results By Order";
    public static final String ROLE_ALIQUOT = "Aliquot";
    public static final String ROLE_VALIDATION = "Validation";
    public static final String ROLE_VALIDATION_ROUTINE = "Validation Routine";
    public static final String ROLE_VALIDATION_BY_ORDER = "Validation By Order";
    public static final String ROLE_VALIDATION_BIOLOGIST = "Validation Biologist";
    public static final String ROLE_VALIDATION_MEDICAL = "Validation Medical";
    public static final String ROLE_REPORTS = "Reports";
    public static final String ROLE_STORAGE = "Storage";
    public static final String ROLE_STORAGE_MANAGEMENT = "Storage Management";
    public static final String ROLE_PATHOLOGIST = "Pathologist";
    // roles groups
    public static final String GLOBAL_ROLES_GROUP = "Global Roles";
    public static final String LAB_ROLES_GROUP = "Lab Unit Roles";
    public static final String CUSTOM_ROLES_GROUP = "Custom Roles";
}
