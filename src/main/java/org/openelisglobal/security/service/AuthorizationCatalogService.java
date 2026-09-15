package org.openelisglobal.security.service;

import java.util.List;
import org.openelisglobal.common.service.ProfessionalProfilePermissionService.PermissionFlow;
import org.springframework.stereotype.Service;

/**
 * Defines the stable authorization vocabulary used by the module-based role
 * editor.
 *
 * <p>
 * This catalog does not grant or deny access. Existing role and
 * professional-profile checks remain the source of authorization until the
 * migration stages adopt these definitions.
 */
@Service
public class AuthorizationCatalogService {

    private static final List<AuthorizationModule> MODULES = List.of(
            module("sample-management", "authorization.module.sampleManagement",
                    List.of("read", "receive", "update", "aliquot", "print", "export"),
                    List.of("patient-identity", "patient-demographics", "reception", "collection", "clinical-data",
                            "tests-and-results")),
            module("orders", "authorization.module.orders", List.of("read", "create", "update", "cancel", "print"),
                    List.of()),
            module("patients", "authorization.module.patients", List.of("read", "create", "update", "merge", "export"),
                    List.of("patient-identity", "patient-contact", "patient-demographics", "patient-history")),
            resultsModule(),
            module("validation", "authorization.module.validation", List.of("read", "validate", "revoke"),
                    List.of()),
            module("storage", "authorization.module.storage", List.of("read", "update", "manage"), List.of()),
            module("administration", "authorization.module.administration", List.of("read", "manage"), List.of()));

    private static final List<ProfessionalProfileFlow> PROFESSIONAL_PROFILE_FLOWS = List.of(
            professionalProfileFlow(PermissionFlow.ORDER_REQUESTER),
            professionalProfileFlow(PermissionFlow.PATIENT_MANAGER),
            professionalProfileFlow(PermissionFlow.RESULT_ENTRY),
            professionalProfileFlow(PermissionFlow.VALIDATION_INTERPRETER),
            professionalProfileFlow(PermissionFlow.SAMPLE_COLLECTOR));

    private static final List<AuthorizationFieldTag> SAMPLE_MANAGEMENT_FIELD_TAGS = List.of(
            fieldTag("cug-code", "authorization.fieldTag.sample-management.cug-code"),
            fieldTag("quantity", "authorization.fieldTag.sample-management.quantity"),
            fieldTag("unit-of-measure", "authorization.fieldTag.sample-management.unit-of-measure"),
            fieldTag("collector", "authorization.fieldTag.sample-management.collector"),
            fieldTag("collection-date", "authorization.fieldTag.sample-management.collection-date"),
            fieldTag("order-reception", "authorization.fieldTag.sample-management.order-reception"),
            fieldTag("tests", "authorization.fieldTag.sample-management.tests"));

    private static final List<AuthorizationFieldTag> PATIENT_FIELD_TAGS = List.of(
            fieldTag("patient-photo", "patient.fixed.fields.photo"),
            fieldTag("patient-subject-number", "patient.fixed.fields.subjectNumber"),
            fieldTag("patient-national-id", "patient.fixed.fields.nationalId"),
            fieldTag("patient-optional-identifiers", "patient.fixed.fields.optionalIdentifiers"),
            fieldTag("patient-last-name", "patient.fixed.fields.lastName"),
            fieldTag("patient-first-name", "patient.fixed.fields.firstName"),
            fieldTag("patient-primary-phone", "patient.fixed.fields.primaryPhone"),
            fieldTag("patient-email", "patient.fixed.fields.email"),
            fieldTag("patient-gender", "patient.fixed.fields.gender"),
            fieldTag("patient-birth-date-age", "patient.fixed.fields.birthDateAge"),
            fieldTag("patient-emergency-contact", "patient.fixed.fields.emergencyContact"),
            fieldTag("patient-additional-information", "patient.fixed.fields.additionalInfo"),
            fieldTag("patient-history-overview", "authorization.fieldTag.patients.history.overview"),
            fieldTag("patient-history-orders", "authorization.fieldTag.patients.history.orders"),
            fieldTag("patient-history-order-details", "authorization.fieldTag.patients.history.orderDetails"),
            fieldTag("patient-history-samples", "authorization.fieldTag.patients.history.samples"),
            fieldTag("patient-history-sample-details", "authorization.fieldTag.patients.history.sampleDetails"),
            fieldTag("patient-history-results", "authorization.fieldTag.patients.history.results"),
            fieldTag("patient-history-result-values", "authorization.fieldTag.patients.history.resultValues"),
            fieldTag("patient-history-storage", "authorization.fieldTag.patients.history.storage"));

    private static final List<AuthorizationSearchCriterion> PATIENT_SEARCH_CRITERIA = List.of(
            searchCriterion("patient-id", "authorization.patientSearchCriterion.patientId"),
            searchCriterion("lab-number", "authorization.patientSearchCriterion.labNumber"),
            searchCriterion("first-name", "authorization.patientSearchCriterion.firstName"),
            searchCriterion("last-name", "authorization.patientSearchCriterion.lastName"),
            searchCriterion("birth-date", "authorization.patientSearchCriterion.birthDate"),
            searchCriterion("gender", "authorization.patientSearchCriterion.gender"),
            searchCriterion("national-id", "authorization.patientSearchCriterion.nationalId"),
            searchCriterion("external-search", "authorization.patientSearchCriterion.externalSearch"));

    public AuthorizationCatalog getCatalog() {
        return new AuthorizationCatalog(MODULES, PROFESSIONAL_PROFILE_FLOWS);
    }

    public boolean isKnownPermission(String moduleKey, String actionKey) {
        return MODULES.stream().filter(module -> module.key().equals(moduleKey)).flatMap(module -> module.actions().stream())
                .anyMatch(action -> action.key().equals(actionKey));
    }

    public boolean isKnownFieldGroup(String moduleKey, String fieldGroupKey) {
        return MODULES.stream().filter(module -> module.key().equals(moduleKey))
                .flatMap(module -> module.fieldGroups().stream())
                .anyMatch(fieldGroup -> fieldGroup.key().equals(fieldGroupKey));
    }

    public List<AuthorizationFieldTag> getFieldTags(String moduleKey) {
        return switch (moduleKey) {
        case "sample-management" -> SAMPLE_MANAGEMENT_FIELD_TAGS;
        case "patients" -> PATIENT_FIELD_TAGS;
        default -> List.of();
        };
    }

    public boolean isKnownFieldTag(String moduleKey, String fieldTagKey) {
        return getFieldTags(moduleKey).stream().anyMatch(fieldTag -> fieldTag.key().equals(fieldTagKey))
                || "sample-management".equals(moduleKey) && fieldTagKey != null
                        && fieldTagKey.matches("additional-field-[0-9]+")
                || "patients".equals(moduleKey) && fieldTagKey != null
                        && fieldTagKey.matches("patient-additional-field-[0-9]+");
    }

    public List<AuthorizationSearchCriterion> getPatientSearchCriteria() {
        return PATIENT_SEARCH_CRITERIA;
    }

    public boolean isKnownPatientSearchCriterion(String criterionKey) {
        return PATIENT_SEARCH_CRITERIA.stream().anyMatch(criterion -> criterion.key().equals(criterionKey));
    }

    private static AuthorizationModule module(String key, String labelKey, List<String> actionKeys,
            List<String> fieldGroupKeys) {
        return new AuthorizationModule(key, labelKey, actionKeys.stream()
                .map(actionKey -> new AuthorizationAction(actionKey, "authorization.action." + actionKey)).toList(),
                fieldGroupKeys.stream().map(fieldGroupKey -> new AuthorizationFieldGroup(fieldGroupKey,
                        "authorization.fieldGroup." + key + "." + fieldGroupKey)).toList());
    }

    private static AuthorizationModule resultsModule() {
        return new AuthorizationModule("results", "authorization.module.results",
                List.of(new AuthorizationAction("read", "authorization.action.read"),
                        new AuthorizationAction("enter", "authorization.action.enter"),
                        new AuthorizationAction("update", "authorization.action.update"),
                        new AuthorizationAction("correct", "authorization.action.correct"),
                        new AuthorizationAction("export", "authorization.action.printWorkplan")),
                List.of());
    }

    private static ProfessionalProfileFlow professionalProfileFlow(PermissionFlow flow) {
        return new ProfessionalProfileFlow(flow.name(), "authorization.profileFlow." + flow.name().toLowerCase());
    }

    private static AuthorizationFieldTag fieldTag(String key, String labelKey) {
        return new AuthorizationFieldTag(key, labelKey);
    }

    private static AuthorizationSearchCriterion searchCriterion(String key, String labelKey) {
        return new AuthorizationSearchCriterion(key, labelKey);
    }

    public record AuthorizationCatalog(List<AuthorizationModule> modules,
            List<ProfessionalProfileFlow> professionalProfileFlows) {
    }

    public record AuthorizationModule(String key, String labelKey, List<AuthorizationAction> actions,
            List<AuthorizationFieldGroup> fieldGroups) {
    }

    public record AuthorizationAction(String key, String labelKey) {
    }

    public record AuthorizationFieldGroup(String key, String labelKey) {
    }

    public record AuthorizationFieldTag(String key, String labelKey) {
    }

    public record AuthorizationSearchCriterion(String key, String labelKey) {
    }

    /**
     * Existing profile gates are cataloged separately so they can be reviewed
     * before migration.
     */
    public record ProfessionalProfileFlow(String key, String labelKey) {
    }
}
