package org.openelisglobal.sample.valueholder;

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
@Table(name = "sample_item_additional_field_value")
@DynamicUpdate
public class SampleItemAdditionalFieldValue extends BaseObject<Integer> {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sample_item_additional_field_value_seq")
    @SequenceGenerator(name = "sample_item_additional_field_value_seq", sequenceName = "sample_item_additional_field_value_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "sample_item_id", nullable = false)
    private Integer sampleItemId;

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

    public Integer getSampleItemId() {
        return sampleItemId;
    }

    public void setSampleItemId(Integer sampleItemId) {
        this.sampleItemId = sampleItemId;
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

    public Integer getSysUserIdAsInteger() {
        return sysUserId;
    }

    public void setSysUserIdAsInteger(Integer sysUserId) {
        this.sysUserId = sysUserId;
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
