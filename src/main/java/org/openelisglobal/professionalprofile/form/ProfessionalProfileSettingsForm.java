package org.openelisglobal.professionalprofile.form;

import java.util.ArrayList;
import java.util.List;

public class ProfessionalProfileSettingsForm {

    private List<String> orderProviderProfessionalProfileCodes = new ArrayList<>();

    private List<String> sampleCollectorProfessionalProfileCodes = new ArrayList<>();

    private List<String> patientEntryProfessionalProfileCodes = new ArrayList<>();

    private List<String> resultEntryProfessionalProfileCodes = new ArrayList<>();

    private List<String> validationInterpreterProfessionalProfileCodes = new ArrayList<>();

    public List<String> getOrderProviderProfessionalProfileCodes() {
        return orderProviderProfessionalProfileCodes;
    }

    public void setOrderProviderProfessionalProfileCodes(List<String> orderProviderProfessionalProfileCodes) {
        this.orderProviderProfessionalProfileCodes = orderProviderProfessionalProfileCodes == null ? new ArrayList<>()
                : new ArrayList<>(orderProviderProfessionalProfileCodes);
    }

    public List<String> getSampleCollectorProfessionalProfileCodes() {
        return sampleCollectorProfessionalProfileCodes;
    }

    public void setSampleCollectorProfessionalProfileCodes(List<String> sampleCollectorProfessionalProfileCodes) {
        this.sampleCollectorProfessionalProfileCodes = sampleCollectorProfessionalProfileCodes == null ? new ArrayList<>()
                : new ArrayList<>(sampleCollectorProfessionalProfileCodes);
    }

    public List<String> getPatientEntryProfessionalProfileCodes() {
        return patientEntryProfessionalProfileCodes;
    }

    public void setPatientEntryProfessionalProfileCodes(List<String> patientEntryProfessionalProfileCodes) {
        this.patientEntryProfessionalProfileCodes = patientEntryProfessionalProfileCodes == null ? new ArrayList<>()
                : new ArrayList<>(patientEntryProfessionalProfileCodes);
    }

    public List<String> getResultEntryProfessionalProfileCodes() {
        return resultEntryProfessionalProfileCodes;
    }

    public void setResultEntryProfessionalProfileCodes(List<String> resultEntryProfessionalProfileCodes) {
        this.resultEntryProfessionalProfileCodes = resultEntryProfessionalProfileCodes == null ? new ArrayList<>()
                : new ArrayList<>(resultEntryProfessionalProfileCodes);
    }

    public List<String> getValidationInterpreterProfessionalProfileCodes() {
        return validationInterpreterProfessionalProfileCodes;
    }

    public void setValidationInterpreterProfessionalProfileCodes(List<String> validationInterpreterProfessionalProfileCodes) {
        this.validationInterpreterProfessionalProfileCodes = validationInterpreterProfessionalProfileCodes == null
                ? new ArrayList<>()
                : new ArrayList<>(validationInterpreterProfessionalProfileCodes);
    }
}
