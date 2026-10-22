package org.openelisglobal.sample.exception;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;

/**
 * A displayed CUG is already used; callers must not retry with another suffix.
 */
public class SampleCugConflictException extends IllegalArgumentException {
    public static final String CODE = "sample.cug.conflict";
    private static final String INDEX = "idx_sample_item_cug_code_unique";

    public SampleCugConflictException() {
        super(CODE);
    }

    public static boolean isConflict(Throwable error) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof SampleCugConflictException)
                return true;
            if (cause instanceof ConstraintViolationException violation && INDEX.equals(violation.getConstraintName()))
                return true;
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState()) && sql.getMessage() != null
                    && sql.getMessage().contains("\"" + INDEX + "\""))
                return true;
        }
        return false;
    }
}
