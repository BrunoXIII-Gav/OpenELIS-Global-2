package org.openelisglobal.testadditionalfield.service;

import java.util.List;

/**
 * A rejected transaction; no drafts are persisted when a conflict is present.
 */
public class AdditionalFieldConflictException extends RuntimeException {
    public record Conflict(String analysisId, String fieldKey, String displayName, String currentValue,
            String currentVersion) {
    }

    private final String code;
    private final List<Conflict> conflicts;

    public AdditionalFieldConflictException(String code, List<Conflict> conflicts) {
        super(code);
        this.code = code;
        this.conflicts = List.copyOf(conflicts);
    }

    public String getCode() {
        return code;
    }

    public List<Conflict> getConflicts() {
        return conflicts;
    }
}
