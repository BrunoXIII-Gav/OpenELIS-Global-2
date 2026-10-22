package org.openelisglobal.sample.exception;

public class PatientCugPrefixException extends RuntimeException {
    public static final String INVALID = "patient.cug.prefix.invalid";
    public static final String OCCUPIED = "patient.cug.prefix.duplicate";
    public static final String READONLY = "patient.cug.prefix.readonly";
    public static final String STALE = "patient.cug.prefix.stale";
    public static final String INCONSISTENT = "patient.cug.prefix.inconsistent";

    public PatientCugPrefixException(String code) {
        super(code);
    }
}
