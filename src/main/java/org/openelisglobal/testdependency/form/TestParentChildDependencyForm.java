package org.openelisglobal.testdependency.form;

public class TestParentChildDependencyForm {

    private String id;
    private String parentTestId;
    private String childTestId;
    private Boolean active = Boolean.TRUE;
    private Integer displayOrder;
    private String sampleUsageSource;
    private String parentResultFieldKey;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getParentTestId() {
        return parentTestId;
    }

    public void setParentTestId(String parentTestId) {
        this.parentTestId = parentTestId;
    }

    public String getChildTestId() {
        return childTestId;
    }

    public void setChildTestId(String childTestId) {
        this.childTestId = childTestId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getSampleUsageSource() {
        return sampleUsageSource;
    }

    public void setSampleUsageSource(String sampleUsageSource) {
        this.sampleUsageSource = sampleUsageSource;
    }

    public String getParentResultFieldKey() {
        return parentResultFieldKey;
    }

    public void setParentResultFieldKey(String parentResultFieldKey) {
        this.parentResultFieldKey = parentResultFieldKey;
    }
}
