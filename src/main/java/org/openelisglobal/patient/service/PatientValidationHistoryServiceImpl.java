package org.openelisglobal.patient.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analysis.service.AnalysisService;
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
    @Autowired
    private AnalysisService analysisService;

    @Override
    public List<PatientValidationRecord> getRecords(String patientId, String userId) {
        requirePatientAccess(patientId, userId);
        Set<String> tags = moduleAuthorizationService.getRestrictedFieldTagKeys(userId, "patients");
        if (hidden(userId, tags))
            return List.of();
        var decision = moduleAuthorizationService.getAuthorization(userId, "patients", "read");
        var deliverDecision = moduleAuthorizationService.getAuthorization(userId, "validation", "deliver");
        boolean deliveryHidden = tags.contains("patient-validation-delivery-date");
        List<Analysis> analyses = dao
                .findValidatedAnalyses(patientId, statusService.getStatusID(AnalysisStatus.Finalized), null).stream()
                .filter(this::isValidationRequired).filter(a -> allowedUnit(decision, a)).toList();
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
                    validators.getOrDefault(analysis.getId(), List.of()), canViewReport(tags),
                    deliveryHidden || analysis.getDeliveredDate() == null ? null
                            : analysis.getDeliveredDate().toString(),
                    !deliveryHidden && allowedUnit(deliverDecision, analysis)));
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
        if (analyses.isEmpty() || !isValidationRequired(analyses.get(0)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!allowedUnit(decision, analyses.get(0)))
            throw new AccessDeniedException("Analysis outside assigned units");
    }

    @Override
    @Transactional
    public void registerDelivery(String patientId, String analysisId, String deliveredDate, String reason,
            String userId) {
        requirePatientAccess(patientId, userId);
        Set<String> tags = moduleAuthorizationService.getRestrictedFieldTagKeys(userId, "patients");
        if (hidden(userId, tags) || tags.contains("patient-validation-delivery-date"))
            throw new AccessDeniedException("Result delivery date is restricted");
        var analyses = dao.findValidatedAnalyses(patientId, statusService.getStatusID(AnalysisStatus.Finalized),
                analysisId);
        if (analyses.isEmpty() || !isValidationRequired(analyses.get(0)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Analysis analysis = analyses.get(0);
        if (!allowedUnit(moduleAuthorizationService.getAuthorization(userId, "validation", "deliver"), analysis))
            throw new AccessDeniedException("User may not register result delivery for this analysis");

        Date date = parseDeliveryDate(deliveredDate, analysis);
        Date current = analysis.getDeliveredDate();
        if (date.equals(current))
            return;
        String trimmedReason = StringUtils.trimToNull(reason);
        // A correction overwrites the previous date; the audit trail keeps it and
        // the reason explains why it changed.
        if (current != null && trimmedReason == null)
            throw new IllegalArgumentException("patient.validation.delivery.error.reasonRequired");
        if (trimmedReason != null && trimmedReason.length() > 255)
            throw new IllegalArgumentException("patient.validation.delivery.error.reasonTooLong");
        analysis.setDeliveredDate(date);
        analysis.setDeliveredBy(userId);
        analysis.setDeliveryCorrectionReason(current == null ? null : trimmedReason);
        analysis.setSysUserId(userId);
        analysisService.update(analysis);
    }

    private Date parseDeliveryDate(String value, Analysis analysis) {
        LocalDate date;
        try {
            date = LocalDate.parse(StringUtils.trimToEmpty(value));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("patient.validation.delivery.error.invalidDate");
        }
        if (date.isAfter(LocalDate.now()))
            throw new IllegalArgumentException("patient.validation.delivery.error.futureDate");
        if (analysis.getValidationDate() != null && date.isBefore(analysis.getValidationDate().toLocalDate()))
            throw new IllegalArgumentException("patient.validation.delivery.error.beforeValidation");
        return Date.valueOf(date);
    }

    private void requirePatientAccess(String patientId, String userId) {
        if (!patientAuthorizationService.canAccessPatient(userId, patientId, "read")) {
            throw new AccessDeniedException("Patient history is not accessible");
        }
    }

    private boolean isValidationRequired(Analysis analysis) {
        return analysis.getTest() != null
                && !Boolean.TRUE.equals(analysis.getTest().getSkipValidationWhenParentComplete());
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
