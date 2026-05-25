package org.openelisglobal.testadditionalfield.bean;

import java.util.ArrayList;
import java.util.List;

public class TestAdditionalFieldPayload {

    private Integer id;
    private String testId;
    private String fieldKey;
    private String displayName;
    private String fieldType;
    private Boolean required;
    private Boolean active;
    private Integer sortOrder;
    private String defaultValue;
    private Integer maxLength;
    private String metadataJson;
    private String blockName;
    private String entryScope;
    private Boolean includeInValidation;
    private List<TestAdditionalFieldOptionPayload> options = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
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

    public String getBlockName() {
        return blockName;
    }

    public void setBlockName(String blockName) {
        this.blockName = blockName;
    }

    public String getEntryScope() {
        return entryScope;
    }

    public void setEntryScope(String entryScope) {
        this.entryScope = entryScope;
    }

    public Boolean getIncludeInValidation() {
        return includeInValidation;
    }

    public void setIncludeInValidation(Boolean includeInValidation) {
        this.includeInValidation = includeInValidation;
    }

    public List<TestAdditionalFieldOptionPayload> getOptions() {
        return options;
    }

    public void setOptions(List<TestAdditionalFieldOptionPayload> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }
}
