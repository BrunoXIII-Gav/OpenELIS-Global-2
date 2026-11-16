package org.openelisglobal.reports.form;

import java.util.List;
import java.util.Map;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFileReference;

public class ConsentPreviewForm {

    private Map<String, OrderAdditionalFieldFileReference> orderDocumentFiles;

    public Map<String, OrderAdditionalFieldFileReference> getOrderDocumentFiles() {
        return orderDocumentFiles;
    }

    public void setOrderDocumentFiles(Map<String, OrderAdditionalFieldFileReference> orderDocumentFiles) {
        this.orderDocumentFiles = orderDocumentFiles;
    }

    private String templateId;
    private String orderFieldKey;
    private Map<String, String> orderFixedFieldValues;

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String value) {
        templateId = value;
    }

    public String getOrderFieldKey() {
        return orderFieldKey;
    }

    public void setOrderFieldKey(String value) {
        orderFieldKey = value;
    }

    public Map<String, String> getOrderFixedFieldValues() {
        return orderFixedFieldValues;
    }

    public void setOrderFixedFieldValues(Map<String, String> value) {
        orderFixedFieldValues = value;
    }

    private String orderPatientId;
    private String orderAccessionNumber;

    public String getOrderPatientId() {
        return orderPatientId;
    }

    public void setOrderPatientId(String orderPatientId) {
        this.orderPatientId = orderPatientId;
    }

    public String getOrderAccessionNumber() {
        return orderAccessionNumber;
    }

    public void setOrderAccessionNumber(String orderAccessionNumber) {
        this.orderAccessionNumber = orderAccessionNumber;
    }

    private Map<String, Boolean> booleanValues;

    public Map<String, Boolean> getBooleanValues() {
        return booleanValues;
    }

    public void setBooleanValues(Map<String, Boolean> values) {
        booleanValues = values;
    }

    private Map<String, String> sourceValues;

    public Map<String, String> getSourceValues() {
        return sourceValues;
    }

    public void setSourceValues(Map<String, String> values) {
        sourceValues = values;
    }

    private PatientInfo patient;
    private ProviderInfo provider;
    private List<String> selectedTests;
    private Map<String, String> orderAdditionalFieldValues;
    private String orderDate;
    private String cug;

    public PatientInfo getPatient() {
        return patient;
    }

    public void setPatient(PatientInfo patient) {
        this.patient = patient;
    }

    public ProviderInfo getProvider() {
        return provider;
    }

    public void setProvider(ProviderInfo provider) {
        this.provider = provider;
    }

    public List<String> getSelectedTests() {
        return selectedTests;
    }

    public void setSelectedTests(List<String> selectedTests) {
        this.selectedTests = selectedTests;
    }

    public Map<String, String> getOrderAdditionalFieldValues() {
        return orderAdditionalFieldValues;
    }

    public void setOrderAdditionalFieldValues(Map<String, String> orderAdditionalFieldValues) {
        this.orderAdditionalFieldValues = orderAdditionalFieldValues;
    }

    public String getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(String orderDate) {
        this.orderDate = orderDate;
    }

    public String getCug() {
        return cug;
    }

    public void setCug(String cug) {
        this.cug = cug;
    }

    public static class PatientInfo {
        private String firstName;
        private String lastName;
        private String fullName;
        private String nationalId;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getNationalId() {
            return nationalId;
        }

        public void setNationalId(String nationalId) {
            this.nationalId = nationalId;
        }
    }

    public static class ProviderInfo {
        private String providerId;
        private String personId;

        public String getProviderId() {
            return providerId;
        }

        public void setProviderId(String providerId) {
            this.providerId = providerId;
        }

        public String getPersonId() {
            return personId;
        }

        public void setPersonId(String personId) {
            this.personId = personId;
        }

        private String firstName;
        private String lastName;
        private String fullName;
        private String dni;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getDni() {
            return dni;
        }

        public void setDni(String dni) {
            this.dni = dni;
        }
    }
}
