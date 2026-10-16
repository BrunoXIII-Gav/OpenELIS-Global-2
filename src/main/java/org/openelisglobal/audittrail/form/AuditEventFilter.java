package org.openelisglobal.audittrail.form;

import java.time.Instant;

/** Exact-match filters; all values are bound as query parameters. */
public record AuditEventFilter(String accessionNumber, String module, String entityName, String entityId,
        String actorId, String field, String action, Instant from, Instant to, String testId, int page, int size) {
    public AuditEventFilter(String accessionNumber, String module, String entityName, String entityId,
            String actorId, String field, String action, Instant from, Instant to, int page, int size) {
        this(accessionNumber, module, entityName, entityId, actorId, field, action, from, to, null, page, size);
    }

    public AuditEventFilter {
        if (testId != null && !testId.isBlank() && !testId.trim().matches("[0-9]{1,10}"))
            throw new IllegalArgumentException("Invalid test identifier");
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Invalid audit pagination");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Invalid audit date range");
        }
        if (action != null && !action.isBlank() && !java.util.Set.of("I", "U", "D").contains(action)) {
            throw new IllegalArgumentException("Invalid audit action");
        }
    }
}
