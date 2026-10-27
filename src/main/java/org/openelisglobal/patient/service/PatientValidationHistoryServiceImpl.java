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
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.audittrail.util.AuditModule;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
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
    @Autowired
    private AuditEventDAO auditEventDAO;

    @Override
    public List<PatientValidationRecord> getRecords(String patientId, String userId) {
        requirePatientAccess(patientId, userId);
        Set<String> tags = moduleAuthorizationService.getRestrictedFieldTagKeys(userId, "patients");
        if (hidden(userId, tags))
            return List.of();
        var decision = moduleAuthorizationService.getAuthorization(userId, "patients", "read");
        var deliverDecision = moduleAuthorizationService.getAuthorization(userId, "validation", "deliver");
        var correctDecision = moduleAuthorizationService.getAuthorization(userId, "results", "correct");
        var reopenDecision = moduleAuthorizationService.getAuthorization(userId, "validation", "reopen-delivery");
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
        Map<String, String> amendedBy = amendments(analyses.stream().map(Analysis::getId).toList());
        List<PatientValidationRecord> records = new ArrayList<>();
        for (Analysis analysis : analyses) {
            // Only a delivered, not yet amended result can branch or be reopened.
            boolean open = analysis.getDeliveredDate() != null && !amendedBy.containsKey(analysis.getId());
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
                    !deliveryHidden && allowedUnit(deliverDecision, analysis), analysis.getAmendsAnalysisId(),
                    amendedBy.get(analysis.getId()), open && allowedUnit(correctDecision, analysis),
                    open && !deliveryHidden && allowedUnit(reopenDecision, analysis)));
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
        Analysis analysis = validatedAnalysis(patientId, analysisId, userId, true);
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

    @Override
    @Transactional
    public String createAmendment(String patientId, String analysisId, String reason, String userId) {
        Analysis original = validatedAnalysis(patientId, analysisId, userId, false);
        if (!allowedUnit(moduleAuthorizationService.getAuthorization(userId, "results", "correct"), original))
            throw new AccessDeniedException("User may not correct results for this analysis");
        String trimmedReason = requireOpenDelivery(original, reason,
                "patient.validation.amendment.error.reasonRequired");

        Analysis amendment = analysisService.buildAnalysis(original.getTest(), original.getSampleItem());
        amendment.setTestSection(original.getTestSection());
        amendment.setPanel(original.getPanel());
        amendment.setParentAnalysis(original.getParentAnalysis());
        amendment.setParentResult(original.getParentResult());
        amendment.setAmendsAnalysisId(original.getId());
        amendment.setAmendmentReason(trimmedReason);
        amendment.setSysUserId(userId);
        String amendmentId = analysisService.insert(amendment);
        // The new analysis records its origin when inserted; this event closes the
        // original thread with a pointer to the branch that continues it.
        auditEventDAO.insert(amendmentEvent(original, amendmentId, userId, patientId));
        return amendmentId;
    }

    @Override
    @Transactional
    public void reopenDelivery(String patientId, String analysisId, String reason, String userId) {
        Analysis analysis = validatedAnalysis(patientId, analysisId, userId, true);
        if (!allowedUnit(moduleAuthorizationService.getAuthorization(userId, "validation", "reopen-delivery"),
                analysis))
            throw new AccessDeniedException("User may not reopen result delivery for this analysis");
        String trimmedReason = requireOpenDelivery(analysis, reason,
                "patient.validation.delivery.error.reasonRequired");
        analysis.setDeliveredDate(null);
        analysis.setDeliveredBy(null);
        analysis.setDeliveryCorrectionReason(trimmedReason);
        analysis.setSysUserId(userId);
        analysisService.update(analysis);
    }

    private Analysis validatedAnalysis(String patientId, String analysisId, String userId, boolean needsDeliveryDate) {
        requirePatientAccess(patientId, userId);
        Set<String> tags = moduleAuthorizationService.getRestrictedFieldTagKeys(userId, "patients");
        if (hidden(userId, tags) || needsDeliveryDate && tags.contains("patient-validation-delivery-date"))
            throw new AccessDeniedException("Result delivery date is restricted");
        var analyses = dao.findValidatedAnalyses(patientId, statusService.getStatusID(AnalysisStatus.Finalized),
                analysisId);
        if (analyses.isEmpty() || !isValidationRequired(analyses.get(0)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!allowedUnit(moduleAuthorizationService.getAuthorization(userId, "patients", "read"), analyses.get(0)))
            throw new AccessDeniedException("Analysis outside assigned units");
        return analyses.get(0);
    }

    private String requireOpenDelivery(Analysis analysis, String reason, String reasonRequiredKey) {
        if (analysis.getDeliveredDate() == null)
            throw new IllegalArgumentException("patient.validation.amendment.error.notDelivered");
        if (amendments(List.of(analysis.getId())).containsKey(analysis.getId()))
            throw new IllegalArgumentException("patient.validation.amendment.error.alreadyAmended");
        String trimmedReason = StringUtils.trimToNull(reason);
        if (trimmedReason == null)
            throw new IllegalArgumentException(reasonRequiredKey);
        if (trimmedReason.length() > 255)
            throw new IllegalArgumentException("patient.validation.delivery.error.reasonTooLong");
        return trimmedReason;
    }

    private Map<String, String> amendments(List<String> analysisIds) {
        Map<String, String> amendedBy = new HashMap<>();
        for (Object[] row : dao.findAmendments(analysisIds))
            amendedBy.put(String.valueOf(row[0]), String.valueOf(row[1]));
        return amendedBy;
    }

    private AuditEvent amendmentEvent(Analysis original, String amendmentId, String userId, String patientId) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID().toString());
        event.setOperationId(UUID.randomUUID().toString());
        event.setModule(AuditModule.forEntity("Analysis", "analysis"));
        event.setEntityName("Analysis");
        event.setEntityId(original.getId());
        event.setAction("U");
        event.setField("amendedBy");
        event.setFieldLabel("audit.events.field.amendedBy");
        event.setNewValue(amendmentId);
        event.setActorId(userId);
        event.setActorSource("USER");
        event.setOccurredAt(new Timestamp(System.currentTimeMillis()));
        event.setSource("/rest/patient-history/validations/amendment");
        event.setAnalysisId(original.getId());
        event.setPatientId(patientId);
        if (original.getSampleItem() != null) {
            event.setSampleItemId(original.getSampleItem().getId());
            if (original.getSampleItem().getSample() != null)
                event.setSampleId(original.getSampleItem().getSample().getId());
        }
        return event;
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
