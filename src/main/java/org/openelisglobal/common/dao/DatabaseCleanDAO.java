package org.openelisglobal.common.dao;

import org.openelisglobal.audittrail.valueholder.ArchivedAuditEvent;

public interface DatabaseCleanDAO extends BaseDAO<ArchivedAuditEvent, String> {
    void archiveOperationalAuditEvents(String cleanupId, String actorId);

    void deleteOperationalData();

    void resetTrainingCodes(String laboratoryPrefix);
}
