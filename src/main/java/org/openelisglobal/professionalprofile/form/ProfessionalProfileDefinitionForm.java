package org.openelisglobal.professionalprofile.form;

import java.util.ArrayList;
import java.util.List;

public class ProfessionalProfileDefinitionForm {

    private String code;

    private String name;

    private List<ProfessionalProfileFieldDefinitionForm> fields = new ArrayList<>();

    private List<ProfessionalProfileFieldOptionForm> specialtyOptions = new ArrayList<>();

    public ProfessionalProfileDefinitionForm() {
    }

    public ProfessionalProfileDefinitionForm(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public ProfessionalProfileDefinitionForm(String code, String name,
            List<ProfessionalProfileFieldDefinitionForm> fields) {
        this.code = code;
        this.name = name;
        setFields(fields);
    }

    public ProfessionalProfileDefinitionForm(String code, String name,
            List<ProfessionalProfileFieldDefinitionForm> fields,
            List<ProfessionalProfileFieldOptionForm> specialtyOptions) {
        this.code = code;
        this.name = name;
        setFields(fields);
        setSpecialtyOptions(specialtyOptions);
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

    public List<ProfessionalProfileFieldDefinitionForm> getFields() {
        return fields;
    }

    public void setFields(List<ProfessionalProfileFieldDefinitionForm> fields) {
        this.fields = fields == null ? new ArrayList<>() : fields;
    }

    public List<ProfessionalProfileFieldOptionForm> getSpecialtyOptions() {
        return specialtyOptions;
    }

    public void setSpecialtyOptions(List<ProfessionalProfileFieldOptionForm> specialtyOptions) {
        this.specialtyOptions = specialtyOptions == null ? new ArrayList<>() : specialtyOptions;
    }
}
