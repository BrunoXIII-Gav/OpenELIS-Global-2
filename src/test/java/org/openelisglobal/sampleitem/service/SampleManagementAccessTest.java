package org.openelisglobal.sampleitem.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.openelisglobal.sampleitem.valueholder.SampleDataCompletionStatus;

public class SampleManagementAccessTest {

    @Test
    public void completePermissionOnlyAllowsPendingSamples() {
        SampleManagementAccess access = new SampleManagementAccess(false, true, false);

        assertTrue(access.canView(SampleDataCompletionStatus.PENDING_COMPLETION));
        assertTrue(access.canEdit(SampleDataCompletionStatus.PENDING_COMPLETION));
        assertFalse(access.canView(SampleDataCompletionStatus.COMPLETED));
        assertFalse(access.canEdit(SampleDataCompletionStatus.COMPLETED));
    }

    @Test
    public void updatePermissionOnlyAllowsCompletedSamples() {
        SampleManagementAccess access = new SampleManagementAccess(false, false, true);

        assertFalse(access.canView(SampleDataCompletionStatus.PENDING_COMPLETION));
        assertFalse(access.canEdit(SampleDataCompletionStatus.PENDING_COMPLETION));
        assertTrue(access.canView(SampleDataCompletionStatus.COMPLETED));
        assertTrue(access.canEdit(SampleDataCompletionStatus.COMPLETED));
    }

    @Test
    public void readPermissionShowsAllSamplesWithoutAllowingChanges() {
        SampleManagementAccess access = new SampleManagementAccess(true, false, false);

        assertTrue(access.canView(SampleDataCompletionStatus.PENDING_COMPLETION));
        assertTrue(access.canView(SampleDataCompletionStatus.COMPLETED));
        assertFalse(access.canEdit(SampleDataCompletionStatus.PENDING_COMPLETION));
        assertFalse(access.canEdit(SampleDataCompletionStatus.COMPLETED));
    }
}
