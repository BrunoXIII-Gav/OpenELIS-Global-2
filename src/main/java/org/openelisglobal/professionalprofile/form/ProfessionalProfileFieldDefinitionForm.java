package org.openelisglobal.professionalprofile.form;

import java.util.ArrayList;
import java.util.List;

public class ProfessionalProfileFieldDefinitionForm {

    private String fieldKey;

    private String displayName;

    private String fieldType;

    private Boolean required;

    private Boolean active;

    private Integer sortOrder;

    private List<ProfessionalProfileFieldOptionForm> options = new ArrayList<>();

    private String legacyBinding;

    private Boolean systemField;

    private Boolean showInOrderEntry;

    private Object currentValue;

    private Boolean hasSavedValues;

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

    public List<ProfessionalProfileFieldOptionForm> getOptions() {
        return options;
    }

    public void setOptions(List<ProfessionalProfileFieldOptionForm> options) {
        this.options = options == null ? new ArrayList<>() : options;
    }

    public String getLegacyBinding() {
        return legacyBinding;
    }

    public void setLegacyBinding(String legacyBinding) {
        this.legacyBinding = legacyBinding;
    }

    public Boolean getSystemField() {
        return systemField;
    }

    public void setSystemField(Boolean systemField) {
        this.systemField = systemField;
    }

    public Boolean getShowInOrderEntry() {
        return showInOrderEntry;
    }

    public void setShowInOrderEntry(Boolean showInOrderEntry) {
        this.showInOrderEntry = showInOrderEntry;
    }

    public Object getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Object currentValue) {
        this.currentValue = currentValue;
    }

    public Boolean getHasSavedValues() {
        return hasSavedValues;
    }

    public void setHasSavedValues(Boolean hasSavedValues) {
        this.hasSavedValues = hasSavedValues;
    }
}
