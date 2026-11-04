package org.openelisglobal.patient.dao;

import java.util.List;
import org.openelisglobal.analysis.valueholder.Analysis;

public interface PatientValidationHistoryDAO {
    List<Analysis> findValidatedAnalyses(String patientId, String finalizedStatusId, String analysisId);

    List<Object[]> findActiveApprovers(List<String> analysisIds);

    /** Rows of {amended analysis id, amendment analysis id}. */
    List<Object[]> findAmendments(List<String> analysisIds);
}
