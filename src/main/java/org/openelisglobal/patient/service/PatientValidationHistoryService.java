package org.openelisglobal.patient.service;

import java.util.List;
import org.openelisglobal.patient.form.PatientValidationRecord;

public interface PatientValidationHistoryService {
    List<PatientValidationRecord> getRecords(String patientId, String userId);

    void requireReportAccess(String patientId, String analysisId, String userId);

    /**
     * Records (or corrects, with a reason) the date the validated result was handed
     * to the patient. Dates are ISO yyyy-MM-dd.
     */
    void registerDelivery(String patientId, String analysisId, String deliveredDate, String reason, String userId);
}
