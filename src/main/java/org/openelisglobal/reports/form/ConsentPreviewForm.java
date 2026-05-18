package org.openelisglobal.reports.form;

import java.util.List;
import java.util.Map;

public class ConsentPreviewForm {

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
