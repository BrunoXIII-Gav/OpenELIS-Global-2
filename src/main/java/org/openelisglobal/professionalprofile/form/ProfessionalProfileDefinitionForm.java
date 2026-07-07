package org.openelisglobal.professionalprofile.form;

public class ProfessionalProfileDefinitionForm {

    private String code;

    private String name;

    public ProfessionalProfileDefinitionForm() {
    }

    public ProfessionalProfileDefinitionForm(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
