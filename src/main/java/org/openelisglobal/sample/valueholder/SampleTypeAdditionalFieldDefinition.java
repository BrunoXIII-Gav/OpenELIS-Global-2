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
@Table(name = "sample_type_additional_field_def")
@DynamicUpdate
public class SampleTypeAdditionalFieldDefinition extends BaseObject<Integer> {

    public enum FieldType {
        TEXT, NUMBER, DATE, TIME, DATETIME, BOOLEAN, SELECT, MULTISELECT, RADIO, TEXTAREA,
        SYSTEM_USER_BIOLOGIST_SELECT
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sample_type_additional_field_def_seq")
    @SequenceGenerator(name = "sample_type_additional_field_def_seq", sequenceName = "sample_type_additional_field_def_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "type_of_sample_id", nullable = false)
    private Integer typeOfSampleId;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "field_type", nullable = false, length = 30)
    private String fieldType;

    @Column(name = "display_section", nullable = false, length = 30)
    private String displaySection;

    @Column(name = "required", nullable = false)
    private Boolean required;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    @Column(name = "max_length")
    private Integer maxLength;

    @Column(name = "metadata_json")
    private String metadataJson;

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

    public Integer getTypeOfSampleId() {
        return typeOfSampleId;
    }

    public void setTypeOfSampleId(Integer typeOfSampleId) {
        this.typeOfSampleId = typeOfSampleId;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFieldType() {
        return fieldType;
    }

    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    public String getDisplaySection() {
        return displaySection;
    }

    public void setDisplaySection(String displaySection) {
        this.displaySection = displaySection;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
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
