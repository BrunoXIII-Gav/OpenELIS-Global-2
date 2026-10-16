package org.openelisglobal.patient.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.patient.dao.PatientValidationHistoryDAO;
import org.openelisglobal.patient.form.PatientValidationRecord;
import org.openelisglobal.patient.form.PatientValidationRecord.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PatientValidationHistoryServiceImpl implements PatientValidationHistoryService {
    @Autowired
    private PatientValidationHistoryDAO dao;
    @Autowired
    private PatientAuthorizationService patientAuthorizationService;
    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;
    @Autowired
    private IStatusService statusService;

    @Override
    public List<PatientValidationRecord> getRecords(String patientId, String userId) {
        requirePatientAccess(patientId, userId);
        Set<String> tags = moduleAuthorizationService.getRestrictedFieldTagKeys(userId, "patients");
        if (hidden(userId, tags))
            return List.of();
        var decision = moduleAuthorizationService.getAuthorization(userId, "patients", "read");
        List<Analysis> analyses = dao
                .findValidatedAnalyses(patientId, statusService.getStatusID(AnalysisStatus.Finalized), null).stream()
                .filter(a -> allowedUnit(decision, a)).toList();
        Map<String, List<Validator>> validators = new HashMap<>();
        if (!tags.contains("patient-validation-validator")) {
            for (Object[] row : dao.findActiveApprovers(analyses.stream().map(Analysis::getId).toList())) {
                String name = (StringUtils.defaultString((String) row[1]) + " "
                        + StringUtils.defaultString((String) row[2])).trim();
                name = StringUtils.defaultIfBlank(name, (String) row[3]);
                validators.computeIfAbsent((String) row[0], key -> new ArrayList<>())
                        .add(new Validator(name, tags.contains("patient-validation-date") || row[4] == null ? null
                                : ((Timestamp) row[4]).toInstant().toString()));
            }
        }
        List<PatientValidationRecord> records = new ArrayList<>();
        for (Analysis analysis : analyses) {
            var sampleItem = analysis.getSampleItem();
            var sample = sampleItem.getSample();
            records.add(new PatientValidationRecord(analysis.getId(), sample.getAccessionNumber(),
                    sample.getClinicalOrderId(), sampleItem.getCugCode(),
                    StringUtils.defaultIfBlank(analysis.getTest().getLocalizedName(),
                            analysis.getTest().getDescription()),
                    tags.contains("patient-validation-date") || analysis.getValidationDate() == null ? null
                            : analysis.getValidationDate().toString(),
                    validators.getOrDefault(analysis.getId(), List.of()), canViewReport(tags)));
        }
        return records;
    }

    @Override
    public void requireReportAccess(String patientId, String analysisId, String userId) {
        requirePatientAccess(patientId, userId);
        Set<String> tags = moduleAuthorizationService.getRestrictedFieldTagKeys(userId, "patients");
        if (hidden(userId, tags) || !canViewReport(tags)) {
            throw new AccessDeniedException("Patient validation report is restricted");
        }
        var decision = moduleAuthorizationService.getAuthorization(userId, "patients", "read");
        var analyses = dao.findValidatedAnalyses(patientId, statusService.getStatusID(AnalysisStatus.Finalized),
                analysisId);
        if (analyses.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!allowedUnit(decision, analyses.get(0)))
            throw new AccessDeniedException("Analysis outside assigned units");
    }

    private void requirePatientAccess(String patientId, String userId) {
        if (!patientAuthorizationService.canAccessPatient(userId, patientId, "read")) {
            throw new AccessDeniedException("Patient history is not accessible");
        }
    }

    private boolean hidden(String userId, Set<String> tags) {
        Set<String> groups = moduleAuthorizationService.getRestrictedFieldGroupKeys(userId, "patients");
        return groups.contains("patient-history") || groups.contains("patient-validations")
                || tags.contains("patient-history-validations");
    }

    private boolean canViewReport(Set<String> tags) {
        // The PDF contains authors, dates and results; it must not bypass hidden
        // fields.
        return java.util.Collections.disjoint(tags, Set.of("patient-validation-report", "patient-validation-validator",
                "patient-validation-date", "patient-history-result-values"));
    }

    private boolean allowedUnit(AuthorizationDecision decision, Analysis analysis) {
        return decision.allowed() && (decision.source() != AuthorizationSource.MODULE_PERMISSION
                || decision.allLabUnits()
                || analysis.getTestSection() != null && decision.appliesToLabUnit(analysis.getTestSection().getId()));
    }
}
