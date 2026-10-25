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

    /**
     * A delivered result is closed. Correcting it creates a new analysis for the
     * same test and sample, linked to the original, with its own cycle. Returns
     * the new analysis id.
     */
    String createAmendment(String patientId, String analysisId, String reason, String userId);

    /** Emergency exit: an administrator clears the delivery so the result can be edited. */
    void reopenDelivery(String patientId, String analysisId, String reason, String userId);
}
