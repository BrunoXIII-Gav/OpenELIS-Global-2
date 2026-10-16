package org.openelisglobal.patient.form;

import java.util.List;

public record PatientValidationRecord(String analysisId, String accessionNumber, String clinicalOrderId, String cugCode,
        String testName, String validationDate, List<Validator> validators, boolean reportAvailable) {
    public record Validator(String name, String approvedAt) {
    }
}
