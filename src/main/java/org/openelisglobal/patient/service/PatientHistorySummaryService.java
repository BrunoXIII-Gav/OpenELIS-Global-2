package org.openelisglobal.patient.service;

import org.openelisglobal.patient.form.PatientHistorySummary;

public interface PatientHistorySummaryService {
    PatientHistorySummary getSummary(String patientId);
}
