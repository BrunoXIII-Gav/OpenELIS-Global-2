package org.openelisglobal.sample.exception;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import org.hibernate.exception.ConstraintViolationException;

/** Manual laboratory numbers are never silently replaced. */
public class OrderAccessionConflictException extends IllegalArgumentException {
    public static final String CODE = "order.labnumber.conflict";

    public OrderAccessionConflictException() {
        super(CODE);
    }

    public static boolean isConflict(Throwable error) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable cause = error; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof OrderAccessionConflictException)
                return true;
            if (cause instanceof ConstraintViolationException violation
                    && "idx_sample_order_accession_unique".equals(violation.getConstraintName()))
                return true;
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState()) && sql.getMessage() != null
                    && (sql.getMessage().contains("\"idx_sample_order_accession_unique\"")
                            || sql.getMessage().contains("Key (accession_number)=")))
                return true;
        }
        return false;
    }
}
