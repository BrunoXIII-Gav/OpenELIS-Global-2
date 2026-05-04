package org.openelisglobal.reportdefinition.form;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.openelisglobal.common.form.BaseForm;

public class ValidationTemplateOverrideForm extends BaseForm {

    private String id;
    private String name;
    private String description;
    private String report;
    private Boolean isActive = Boolean.TRUE;
    private List<String> testIds = new ArrayList<>();
    private List<String> testCodes = new ArrayList<>();
    private Map<String, Object> config;
    private String createdBy;
    private Timestamp createdDate;
    private Timestamp lastupdated;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public List<String> getTestIds() {
        return testIds;
    }

    public void setTestIds(List<String> testIds) {
        this.testIds = testIds;
    }

    public List<String> getTestCodes() {
        return testCodes;
    }

    public void setTestCodes(List<String> testCodes) {
        this.testCodes = testCodes;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Timestamp getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Timestamp createdDate) {
        this.createdDate = createdDate;
    }

    public Timestamp getLastupdated() {
        return lastupdated;
    }

    public void setLastupdated(Timestamp lastupdated) {
        this.lastupdated = lastupdated;
    }
}
