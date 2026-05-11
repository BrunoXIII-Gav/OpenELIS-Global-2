package org.openelisglobal.reportdefinition.form;

import java.util.Map;
import org.openelisglobal.common.form.BaseForm;

public class ConsentTemplateConfigForm extends BaseForm {

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
