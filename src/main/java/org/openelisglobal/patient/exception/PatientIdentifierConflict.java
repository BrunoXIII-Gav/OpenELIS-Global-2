package org.openelisglobal.patient.exception;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Identifies document conflicts without exposing document values or unrelated
 * errors.
 */
public final class PatientIdentifierConflict {
    public static final String CODE = "patient.identifier.duplicate";
    private static final String CONSTRAINT = "uq_patient_document_identifier";

    private PatientIdentifierConflict() {
    }

    public static boolean isDuplicate(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && CONSTRAINT.equals(violation.getConstraintName()))
                return true;
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState()) && sql.getMessage() != null
                    && sql.getMessage().contains(CONSTRAINT))
                return true;
        }
        return false;
    }
}
