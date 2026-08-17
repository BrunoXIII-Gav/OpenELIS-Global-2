package org.openelisglobal.orderadditionalfield.valueholder;

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
@Table(name = "order_additional_field_def")
@DynamicUpdate
public class OrderAdditionalFieldDefinition extends BaseObject<Integer> {

    public enum FieldType {
        TEXT, NUMBER, DATE, TIME, DATETIME, BOOLEAN, SELECT, MULTISELECT, RADIO, TEXTAREA, DOCUMENT, USER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_additional_field_def_seq")
    @SequenceGenerator(name = "order_additional_field_def_seq", sequenceName = "order_additional_field_def_seq", allocationSize = 1)
    @Column(name = "id")
    private Integer id;

    @Column(name = "field_key", nullable = false, length = 80)
    private String fieldKey;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "field_type", nullable = false, length = 30)
    private String fieldType;

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

    @Column(name = "searchable", nullable = false)
    private Boolean searchable;

    @Column(name = "search_unique", nullable = false)
    private Boolean searchUnique;

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

    public Boolean getSearchable() {
        return searchable;
    }

    public void setSearchable(Boolean searchable) {
        this.searchable = searchable;
    }

    public Boolean getSearchUnique() {
        return searchUnique;
    }

    public void setSearchUnique(Boolean searchUnique) {
        this.searchUnique = searchUnique;
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
