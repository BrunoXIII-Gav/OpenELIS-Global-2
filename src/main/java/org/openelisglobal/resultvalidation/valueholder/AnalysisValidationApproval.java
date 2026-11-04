package org.openelisglobal.resultvalidation.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import java.util.UUID;
import org.hibernate.annotations.Type;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "analysis_validation_approval")
public class AnalysisValidationApproval extends BaseObject<String> {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "analysis_id", nullable = false)
    @Type(type = "org.openelisglobal.hibernate.resources.usertype.LIMSStringNumberUserType")
    private String analysisId;

    @Column(name = "approver_user_id", nullable = false)
    @Type(type = "org.openelisglobal.hibernate.resources.usertype.LIMSStringNumberUserType")
    private String approverUserId;

    @Column(name = "approved_at", nullable = false)
    private Timestamp approvedAt;

    @Column(name = "revoked_at")
    private Timestamp revokedAt;
    @Column(name = "revoked_by")
    private String revokedBy;
    @Column(name = "revocation_reason")
    private String revocationReason;

    public Timestamp getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Timestamp value) {
        revokedAt = value;
    }

    public String getRevokedBy() {
        return revokedBy;
    }

    public void setRevokedBy(String value) {
        revokedBy = value;
    }

    public String getRevocationReason() {
        return revocationReason;
    }

    public void setRevocationReason(String value) {
        revocationReason = value;
    }

    @PrePersist
    protected void prePersist() {
        if (id == null || id.trim().isEmpty()) {
            id = UUID.randomUUID().toString();
        }
        if (approvedAt == null) {
            approvedAt = new Timestamp(System.currentTimeMillis());
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public String getApproverUserId() {
        return approverUserId;
    }

    public void setApproverUserId(String approverUserId) {
        this.approverUserId = approverUserId;
    }

    public Timestamp getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Timestamp approvedAt) {
        this.approvedAt = approvedAt;
    }
}
