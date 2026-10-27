package org.openelisglobal.patient.form;

import java.util.List;

public record PatientValidationRecord(String analysisId, String accessionNumber, String clinicalOrderId, String cugCode,
        String testName, String validationDate, List<Validator> validators, boolean reportAvailable,
        String deliveredDate, boolean canRegisterDelivery, String amendsAnalysisId, String amendedByAnalysisId,
        boolean canAmend, boolean canReopenDelivery) {
    public record Validator(String name, String approvedAt) {
    }
}
