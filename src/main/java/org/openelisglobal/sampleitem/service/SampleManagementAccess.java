package org.openelisglobal.sampleitem.service;

import org.openelisglobal.sampleitem.valueholder.SampleDataCompletionStatus;

/** Describes which generic sample records a user can view or modify. */
public record SampleManagementAccess(boolean canRead, boolean canComplete, boolean canUpdate) {

    public boolean hasAnyAccess() {
        return canRead || canComplete || canUpdate;
    }

    public boolean canView(SampleDataCompletionStatus status) {
        return canRead || canEdit(status);
    }

    public boolean canEdit(SampleDataCompletionStatus status) {
        return status == SampleDataCompletionStatus.PENDING_COMPLETION ? canComplete : canUpdate;
    }

    public static SampleManagementAccess fullAccess() {
        return new SampleManagementAccess(true, true, true);
    }
}
