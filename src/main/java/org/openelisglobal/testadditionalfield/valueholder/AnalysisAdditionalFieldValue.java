package org.openelisglobal.testadditionalfield.valueholder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.openelisglobal.common.valueholder.BaseObject;

@Entity
@Table(name = "analysis_additional_field_value")
@DynamicUpdate
public class AnalysisAdditionalFieldValue extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "analysis_additional_field_value_seq")
    @SequenceGenerator(name = "analysis_additional_field_value_seq", sequenceName = "analysis_additional_field_value_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "analysis_id", nullable = false)
    private Integer analysisId;

    @Column(name = "field_def_id", nullable = false)
    private Integer fieldDefinitionId;

    @Column(name = "field_value", nullable = false)
    private String fieldValue;

    @Column(name = "sys_user_id", nullable = false)
    private Integer sysUserId;

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(Integer analysisId) {
        this.analysisId = analysisId;
    }

    public Integer getFieldDefinitionId() {
        return fieldDefinitionId;
    }

    public void setFieldDefinitionId(Integer fieldDefinitionId) {
        this.fieldDefinitionId = fieldDefinitionId;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }

    @Override
    public String getSysUserId() {
        return sysUserId != null ? sysUserId.toString() : null;
    }

    @Override
    public void setSysUserId(String sysUserId) {
        this.sysUserId = sysUserId != null ? Integer.valueOf(sysUserId) : null;
    }
}
