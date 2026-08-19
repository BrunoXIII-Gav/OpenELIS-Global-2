package org.openelisglobal.provider.form;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProviderUpsertForm {

    private String providerId;

    private String fhirUuid;

    private String professionalProfileCode;

    private Boolean active;

    private String lastName;

    private String firstName;

    private String telephone;

    private String fax;

    private String email;

    private String dni;

    private String specialty;

    private String professionalInitials;

    private Map<String, Object> profileFieldValues = new LinkedHashMap<>();

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getFhirUuid() {
        return fhirUuid;
    }

    public void setFhirUuid(String fhirUuid) {
        this.fhirUuid = fhirUuid;
    }

    public String getProfessionalProfileCode() {
        return professionalProfileCode;
    }

    public void setProfessionalProfileCode(String professionalProfileCode) {
        this.professionalProfileCode = professionalProfileCode;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getTelephone() {
        return telephone;
    }

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }

    public String getFax() {
        return fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getProfessionalInitials() {
        return professionalInitials;
    }

    public void setProfessionalInitials(String professionalInitials) {
        this.professionalInitials = professionalInitials;
    }

    public Map<String, Object> getProfileFieldValues() {
        return profileFieldValues;
    }

    public void setProfileFieldValues(Map<String, Object> profileFieldValues) {
        this.profileFieldValues = profileFieldValues == null ? new LinkedHashMap<>() : profileFieldValues;
    }
}
