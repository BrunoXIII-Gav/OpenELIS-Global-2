package org.openelisglobal.audittrail.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import org.hibernate.annotations.Immutable;
import org.openelisglobal.common.valueholder.BaseObject;

/**
 * Append-only snapshots; deliberately no foreign keys to mutable business data.
 */
@Entity
@Table(name = "audit_event")
@Immutable
public class AuditEvent extends BaseObject<String> {
    @Id
    @Column(name = "id")
    private String id;
    @Column(name = "operation_id")
    private String operationId;
    @Column(name = "request_id")
    private String requestId;
    @Column(name = "module")
    private String module;
    @Column(name = "entity_name")
    private String entityName;
    @Column(name = "entity_id")
    private String entityId;
    @Column(name = "action")
    private String action;
    @Column(name = "field")
    private String field;
    @Column(name = "field_label")
    private String fieldLabel;
    @Column(name = "old_value", columnDefinition = "text")
    private String oldValue;
    @Column(name = "new_value", columnDefinition = "text")
    private String newValue;
    @Column(name = "old_display_value", columnDefinition = "text")
    private String oldDisplayValue;
    @Column(name = "new_display_value", columnDefinition = "text")
    private String newDisplayValue;

    public String getOldDisplayValue() {
        return oldDisplayValue;
    }

    public void setOldDisplayValue(String value) {
        oldDisplayValue = value;
    }

    public String getNewDisplayValue() {
        return newDisplayValue;
    }

    public void setNewDisplayValue(String value) {
        newDisplayValue = value;
    }

    @Column(name = "actor_id")
    private String actorId;
    @Column(name = "actor_name")
    private String actorName;
    @Column(name = "actor_source")
    private String actorSource;
    @Column(name = "occurred_at", columnDefinition = "timestamp with time zone")
    private Timestamp occurredAt;
    @Column(name = "source")
    private String source;
    @Column(name = "sample_id")
    private String sampleId;
    @Column(name = "sample_item_id")
    private String sampleItemId;
    @Column(name = "analysis_id")
    private String analysisId;
    @Column(name = "test_id")
    private String testId;

    public String getTestId() {
        return testId;
    }

    public void setTestId(String value) {
        testId = value;
    }
    @Column(name = "result_id")
    private String resultId;
    @Column(name = "parent_analysis_id")
    private String parentAnalysisId;

    public String getResultId() {
        return resultId;
    }

    public void setResultId(String value) {
        resultId = value;
    }

    public String getParentAnalysisId() {
        return parentAnalysisId;
    }

    public void setParentAnalysisId(String value) {
        parentAnalysisId = value;
    }

    @Column(name = "patient_id")
    private String patientId;
    @Column(name = "person_id")
    private String personId;
    @Column(name = "field_definition_id")
    private String fieldDefinitionId;

    public String getId() {
        return id;
    }

    public void setId(String value) {
        this.id = value;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String value) {
        this.operationId = value;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String value) {
        this.requestId = value;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String value) {
        this.module = value;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String value) {
        this.entityName = value;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String value) {
        this.entityId = value;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String value) {
        this.action = value;
    }

    public String getField() {
        return field;
    }

    public void setField(String value) {
        this.field = value;
    }

    public String getFieldLabel() {
        return fieldLabel;
    }

    public void setFieldLabel(String value) {
        this.fieldLabel = value;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String value) {
        this.oldValue = value;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String value) {
        this.newValue = value;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String value) {
        this.actorId = value;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String value) {
        this.actorName = value;
    }

    public String getActorSource() {
        return actorSource;
    }

    public void setActorSource(String value) {
        this.actorSource = value;
    }

    public Timestamp getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Timestamp value) {
        this.occurredAt = value;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String value) {
        this.source = value;
    }

    public String getSampleId() {
        return sampleId;
    }

    public void setSampleId(String value) {
        this.sampleId = value;
    }

    public String getSampleItemId() {
        return sampleItemId;
    }

    public void setSampleItemId(String value) {
        this.sampleItemId = value;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String value) {
        this.analysisId = value;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String value) {
        this.patientId = value;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String value) {
        this.personId = value;
    }

    public String getFieldDefinitionId() {
        return fieldDefinitionId;
    }

    public void setFieldDefinitionId(String value) {
        this.fieldDefinitionId = value;
    }
}
