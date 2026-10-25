package org.openelisglobal.audittrail.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Functional fields only; relationship IDs remain private audit context, not
 * events.
 */
public final class AuditFieldPolicy {
    private static final Set<String> INTERNAL = Set.of("id", "lastupdated", "lastUpdated", "last_updated", "sysUserId",
            "sys_user_id", "persistedSysUserId", "sampleId", "sampleItemId", "analysisId", "resultId", "personId",
            "fieldDefinitionId", "referenceId", "referenceTableId", "tableId", "recordId", "parentAnalysisId",
            "parentResultId", "revision", "version", "nextItemSequence", "packageId", "barCode", "transmissionDate",
            "systemUser", "sample", "sampleItem", "analysis", "parentAnalysis", "parentResult", "nameKey", "displayKey",
            "sampleTypeName", "testSectionName", "testName", "resultCalculated", "triggeredReflex",
            "correctedSincePatientReport", "analysisType", "resultEvent");
    private static final Set<String> EXCLUDED_ENTITIES = Set.of("SampleCugReservation");
    private static final Map<String, Set<String>> CLINICAL = Map.ofEntries(
            Map.entry("ElectronicOrder",
                    Set.of("externalId", "patient", "statusId", "status", "orderTimestamp", "priority", "qaAuthorizer",
                            "rejectReasonId", "rejectComment")),
            Map.entry("Sample",
                    Set.of("accessionNumber", "enteredDate", "receivedTimestamp", "receivedDate", "collectionDate",
                            "clientReference", "priority", "status", "statusId", "releasedDate", "referringId",
                            "clinicalOrderId", "isConfirmation", "gpsLatitude", "gpsLongitude", "gpsAccuracyMeters",
                            "gpsCaptureMethod", "gpsCaptureTimestamp")),
            Map.entry("SampleItem",
                    Set.of("quantity", "remainingQuantity", "sourceOfSample", "sourceOfSampleId", "sourceOther",
                            "typeOfSample", "typeOfSampleId", "unitOfMeasure", "externalId", "cugCode",
                            "collectionDate", "statusId", "dataCompletionStatus", "collector", "collectorUserId",
                            "rejected", "rejectReasonId", "voided", "voidReason", "parentSampleItem")),
            Map.entry("Analysis",
                    Set.of("test", "panel", "testSection", "method", "status", "statusId", "startedDate",
                            "completedDate", "releasedDate", "validationDate", "isReportable", "referredOut",
                            "soClientReference", "resultFile", "sampleUsedQuantity", "parentUsageBlockName",
                            "deliveredDate", "deliveredBy", "deliveryCorrectionReason")),
            Map.entry("Result", Set.of("value", "resultType", "isReportable", "analyte", "testResult")),
            Map.entry("AnalysisAdditionalFieldValue", Set.of("fieldValue")),
            Map.entry("SampleItemAdditionalFieldValue", Set.of("fieldValue")),
            Map.entry("SampleOrderAdditionalFieldValue", Set.of("fieldValue")),
            Map.entry("PatientAdditionalFieldValue", Set.of("fieldValue")),
            Map.entry("Note", Set.of("subject", "text", "noteType")),
            Map.entry("ObservationHistory", Set.of("value", "observationHistoryTypeId")));

    private AuditFieldPolicy() {
    }

    public static boolean includes(String entity, String field) {
        if (field == null || EXCLUDED_ENTITIES.contains(entity) || INTERNAL.contains(field) || technicalName(field))
            return false;
        return !CLINICAL.containsKey(entity) || CLINICAL.get(entity).contains(field);
    }

    private static boolean technicalName(String field) {
        String name = field.toLowerCase(Locale.ROOT);
        return name.contains("fhir") || name.contains("uuid") || name.startsWith("sync") || name.endsWith("token")
                || name.endsWith("checksum") || name.endsWith("hash");
    }

    public static void restrict(StringBuilder where, Map<String, Object> parameters) {
        // Consultation only: keep the immutable events and capture unchanged.
        // These fields are hidden for every test, without affecting other entities.
        where.append(" AND NOT (e.entityName = 'Analysis' AND e.field IN (:hiddenAnalysisFields))")
                .append(" AND NOT (e.entityName = 'Result' AND e.field = 'isReportable')");
        parameters.put("hiddenAnalysisFields", Set.of("status", "statusId", "startedDate", "isReportable", "referredOut"));
        where.append(" AND e.entityName NOT IN (:internalEntities) AND e.field NOT IN (:internalFields)")
                .append(" AND lower(e.field) NOT LIKE '%fhir%' AND lower(e.field) NOT LIKE '%uuid%'")
                .append(" AND lower(e.field) NOT LIKE 'sync%' AND lower(e.field) NOT LIKE '%token'")
                .append(" AND lower(e.field) NOT LIKE '%checksum' AND lower(e.field) NOT LIKE '%hash'");
        parameters.put("internalEntities", EXCLUDED_ENTITIES);
        parameters.put("internalFields", INTERNAL);
        parameters.put("clinicalEntities", CLINICAL.keySet());
        List<String> clauses = new ArrayList<>();
        clauses.add("e.entityName NOT IN (:clinicalEntities)");
        int index = 0;
        for (var entry : CLINICAL.entrySet()) {
            String suffix = Integer.toString(index++);
            clauses.add(
                    "(e.entityName = :clinicalEntity" + suffix + " AND e.field IN (:clinicalFields" + suffix + "))");
            parameters.put("clinicalEntity" + suffix, entry.getKey());
            parameters.put("clinicalFields" + suffix, entry.getValue());
        }
        where.append(" AND (").append(String.join(" OR ", clauses)).append(")");
    }
}
