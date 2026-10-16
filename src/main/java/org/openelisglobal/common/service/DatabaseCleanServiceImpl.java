package org.openelisglobal.common.service;

import java.sql.Timestamp;
import java.util.UUID;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.openelisglobal.audittrail.valueholder.History;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.common.dao.DatabaseCleanDAO;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DefaultConfigurationProperties;
import org.openelisglobal.history.service.HistoryService;
import org.openelisglobal.security.service.UserPermissionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseCleanServiceImpl implements DatabaseCleanService {

    private final DatabaseCleanDAO cleanup;
    private final UserPermissionService permissions;
    private final DefaultConfigurationProperties configuration;
    private final HistoryService history;
    private final AuditEventDAO audit;

    public DatabaseCleanServiceImpl(DatabaseCleanDAO cleanup, UserPermissionService permissions,
            DefaultConfigurationProperties configuration, HistoryService history, AuditEventDAO audit) {
        this.cleanup = cleanup;
        this.permissions = permissions;
        this.configuration = configuration;
        this.history = history;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public void requireCleanupAccess(String userId) {
        if (userId == null || !permissions.hasPermission(userId, SystemPermission.GLOBAL_ADMIN)
                || !"true".equals(configuration.getPropertyValueLowerCase(Property.TrainingInstallation))) {
            throw new AccessDeniedException(
                    "Clinical data cleanup requires a training installation and global administrator");
        }
    }

    @Override
    @Transactional
    public void cleanDatabase(String userId) {
        requireCleanupAccess(userId);
        String operationId = UUID.randomUUID().toString();
        cleanup.archiveOperationalAuditEvents(operationId, userId);
        cleanup.deleteOperationalData();
        String laboratoryPrefix = null;
        if ("PREFIXNUM".equals(configuration.getPropertyValueUpperCase(Property.AccessionFormat))) {
            laboratoryPrefix = java.util.Objects
                    .toString(configuration.getPropertyValueUpperCase(Property.ACCESSION_NUMBER_PREFIX), "");
        }
        cleanup.resetTrainingCodes(laboratoryPrefix);

        // Both receipts and the archive commit with the deletions, or all roll back.
        History receipt = new History();
        receipt.setActivity("T");
        receipt.setTimestamp(new Timestamp(System.currentTimeMillis()));
        receipt.setNameKey("Database");
        receipt.setReferenceId("0");
        receipt.setReferenceTable("0");
        receipt.setSysUserId(userId);
        history.save(receipt);

        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID().toString());
        event.setOperationId(operationId);
        event.setModule("systemuser");
        event.setEntityName("DatabaseCleanup");
        event.setEntityId(operationId);
        event.setAction("D");
        event.setField("clinicalData");
        event.setNewValue(laboratoryPrefix == null ? "CLEARED_CUG_RESET" : "CLEARED_CODES_RESET");
        event.setActorId(userId);
        event.setActorSource("USER");
        event.setOccurredAt(receipt.getTimestamp());
        event.setSource("/DatabaseCleaningRequest");
        audit.insert(event);
    }
}
