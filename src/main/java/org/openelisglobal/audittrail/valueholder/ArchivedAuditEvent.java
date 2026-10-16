package org.openelisglobal.audittrail.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import org.hibernate.annotations.Immutable;
import org.openelisglobal.common.valueholder.BaseObject;

/**
 * A training cleanup receipt; the original immutable audit snapshot is
 * retained.
 */
@Entity
@Immutable
@Table(name = "archived_audit_event")
public class ArchivedAuditEvent extends BaseObject<String> {
    @Id
    private String id;
    @Column(name = "cleanup_id", nullable = false)
    private String cleanupId;
    @Column(name = "actor_id", nullable = false)
    private String actorId;
    @Column(name = "archived_at", nullable = false)
    private Timestamp archivedAt;

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String value) {
        id = value;
    }

    public String getCleanupId() {
        return cleanupId;
    }

    public void setCleanupId(String value) {
        cleanupId = value;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String value) {
        actorId = value;
    }

    public Timestamp getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Timestamp value) {
        archivedAt = value;
    }
}
