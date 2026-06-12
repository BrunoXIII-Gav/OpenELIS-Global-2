package org.openelisglobal.sample.bean;

import java.util.ArrayList;
import java.util.List;

public class SampleTypeAdditionalFieldPayload {

    private Integer id;
    private String sampleTypeId;
    private String fieldKey;
    private String displayName;
    private String fieldType;
    private String displaySection;
    private Boolean required;
    private Boolean active;
    private Integer sortOrder;
    private String defaultValue;
    private Integer maxLength;
    private String metadataJson;
    private List<SampleTypeAdditionalFieldOptionPayload> options = new ArrayList<>();

    public String getDisplaySection() {
        return displaySection;
    }

    public void setDisplaySection(String displaySection) {
        this.displaySection = displaySection;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getSampleTypeId() {
        return sampleTypeId;
    }

    public void setSampleTypeId(String sampleTypeId) {
        this.sampleTypeId = sampleTypeId;
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

    public List<SampleTypeAdditionalFieldOptionPayload> getOptions() {
        return options;
    }

    public void setOptions(List<SampleTypeAdditionalFieldOptionPayload> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }
}
