package org.openelisglobal.reportdefinition.form;

import java.util.List;
import java.util.Map;
import org.openelisglobal.common.form.BaseForm;

public class ConsentTemplateConfigForm extends BaseForm {

    private String templateContent;
    private String templateFilename;
    private List<Map<String, String>> parameters;

    public String getTemplateContent() {
        return templateContent;
    }

    public void setTemplateContent(String value) {
        templateContent = value;
    }

    public String getTemplateFilename() {
        return templateFilename;
    }

    public void setTemplateFilename(String value) {
        templateFilename = value;
    }

    public List<Map<String, String>> getParameters() {
        return parameters;
    }

    public void setParameters(List<Map<String, String>> value) {
        parameters = value;
    }

    private String id;
    private String name;
    private Boolean isActive;
    private Map<String, String> fields;

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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields;
    }
}
