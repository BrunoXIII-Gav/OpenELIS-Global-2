package org.openelisglobal.patient.service;

import java.util.List;
import org.openelisglobal.patient.form.PatientValidationRecord;

public interface PatientValidationHistoryService {
    List<PatientValidationRecord> getRecords(String patientId, String userId);

    void requireReportAccess(String patientId, String analysisId, String userId);
}
