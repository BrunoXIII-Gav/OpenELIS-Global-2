/**
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.mozilla.org/MPL/
 *
 * <p>Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * <p>The Original Code is OpenELIS code.
 *
 * <p>Copyright (C) The Minnesota Department of Health. All Rights Reserved.
 */
package org.openelisglobal.provider.valueholder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.openelisglobal.common.DesynchronousCapable;
import org.openelisglobal.common.valueholder.BaseObject;
import org.openelisglobal.common.valueholder.ValueHolder;
import org.openelisglobal.common.valueholder.ValueHolderInterface;
import org.openelisglobal.person.valueholder.Person;

public class Provider extends BaseObject<String> implements DesynchronousCapable {

    private String id;

    private String externalId;

    private UUID fhirUuid;

    private String npi;

    private String providerType;

    private String specialty;

    private String dni;

    private String professionalProfileCode;

    private String professionalInitials;

    private String cbpCode;

    private String profileFieldsJson;

    private Map<String, Object> profileFieldValues = new LinkedHashMap<>();

    private ValueHolderInterface person;

    private String selectedPersonId;

    private Boolean active;

    private boolean desynchronized;

    public Provider() {
        super();
        this.person = new ValueHolder();
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getNpi() {
        return npi;
    }

    public void setNpi(String npi) {
        this.npi = npi;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(String providerType) {
        this.providerType = providerType;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getProfessionalProfileCode() {
        return professionalProfileCode;
    }

    public void setProfessionalProfileCode(String professionalProfileCode) {
        this.professionalProfileCode = professionalProfileCode;
    }

    public String getProfessionalInitials() {
        return professionalInitials;
    }

    public void setProfessionalInitials(String professionalInitials) {
        this.professionalInitials = professionalInitials;
    }

    public String getCbpCode() {
        return cbpCode;
    }

    public void setCbpCode(String cbpCode) {
        this.cbpCode = cbpCode;
    }

    @JsonIgnore
    public String getProfileFieldsJson() {
        return profileFieldsJson;
    }

    public void setProfileFieldsJson(String profileFieldsJson) {
        this.profileFieldsJson = profileFieldsJson;
    }

    public Map<String, Object> getProfileFieldValues() {
        return profileFieldValues;
    }

    public void setProfileFieldValues(Map<String, Object> profileFieldValues) {
        this.profileFieldValues = profileFieldValues == null ? new LinkedHashMap<>() : new LinkedHashMap<>(profileFieldValues);
    }

    public Person getPerson() {
        return (Person) this.person.getValue();
    }

    protected ValueHolderInterface getPersonHolder() {
        return this.person;
    }

    public void setPerson(Person person) {
        this.person.setValue(person);
    }

    protected void setPersonHolder(ValueHolderInterface person) {
        this.person = person;
    }

    public void setSelectedPersonId(String selectedPersonId) {
        this.selectedPersonId = selectedPersonId;
    }

    public String getSelectedPersonId() {
        return this.selectedPersonId;
    }

    public UUID getFhirUuid() {
        return fhirUuid;
    }

    public String getFhirUuidAsString() {
        return fhirUuid == null ? "" : fhirUuid.toString();
    }

    public void setFhirUuid(UUID fhirUuid) {
        this.fhirUuid = fhirUuid;
    }

    public Boolean getActive() {
        return active == null ? false : active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public boolean isDesynchronized() {
        return desynchronized;
    }

    @Override
    public void setDesynchronized(boolean desynchronized) {
        this.desynchronized = desynchronized;
    }
}
