package org.openelisglobal.common.daoimpl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.openelisglobal.audittrail.util.AuditModule;
import org.openelisglobal.audittrail.valueholder.ArchivedAuditEvent;
import org.openelisglobal.common.dao.DatabaseCleanDAO;
import org.openelisglobal.common.util.NumericStringIdQuery;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class DatabaseCleanDAOImpl extends BaseDAOImpl<ArchivedAuditEvent, String> implements DatabaseCleanDAO {
    // Children precede parents. Never include catalog entities, professionals,
    // users, storage locations, sequences or business-number counters here.
    private static final List<String> CLINICAL_ENTITIES = List.of("AnalysisValidationApproval", "AnalysisTubeUsage",
            "AnalysisTubeLabel", "AnalysisAdditionalFieldValue", "SampleItemAdditionalFieldValue",
            "SampleOrderAdditionalFieldFile", "SampleOrderAdditionalFieldValue", "PatientAdditionalFieldValue",
            "SampleStorageMovement", "SampleStorageAssignment", "ReferralResult", "ReferringTestResult", "Referral",
            "ResultInventory", "ResultSignature", "AnalysisQaEventAction", "AnalysisQaEvent", "SampleQaEventAction",
            "SampleQaEvent", "NceSpecimen", "NceActionLog", "NcEvent", "Note", "ObservationHistory",
            "SampleCugReservation", "SampleProject", "SampleOrganization", "SampleHuman", "SampleNewborn",
            "SampleEnvironmental", "SampleTracking", "SamplePdf", "SampleItemAliquotRelationship", "AnalyzerResults",
            "SampleRequester", "DocumentTrack", "ReportExternalExport", "ReportExternalImport", "PatientIdentity",
            "PatientContact", "PatientPatientType", "PatientRelation", "PatientPhoto", "PatientMergeAudit",
            "QaObservation");

    public DatabaseCleanDAOImpl() {
        super(ArchivedAuditEvent.class);
    }

    @Override
    public void archiveOperationalAuditEvents(String cleanupId, String actorId) {
        List<String> entities = new ArrayList<>(CLINICAL_ENTITIES);
        entities.addAll(
                List.of("Analysis", "Result", "ResultFile", "SampleItem", "Sample", "ElectronicOrder", "Patient"));
        Map<String, Object> parameters = new LinkedHashMap<>();
        StringBuilder patientOrigin = new StringBuilder();
        AuditModule.restrict(patientOrigin, parameters, "patient");
        String hql = "INSERT INTO ArchivedAuditEvent (id, cleanupId, actorId, archivedAt)"
                + " SELECT e.id, :cleanupId, :cleanupActor, current_timestamp FROM AuditEvent e"
                + " WHERE (e.entityName IN (:clinicalEntities) OR (e.entityName = 'Person'" + patientOrigin + "))"
                + " AND NOT EXISTS (SELECT old.id FROM ArchivedAuditEvent old WHERE old.id = e.id)";
        parameters.put("cleanupId", cleanupId);
        parameters.put("cleanupActor", actorId);
        parameters.put("clinicalEntities", entities);
        mutate(hql, parameters);
    }

    @Override
    public void deleteOperationalData() {
        entityManager.flush();
        // Remember only people belonging to deleted patients/contacts. Shared
        // professional, user and organization identities are protected below.
        List<String> people = new ArrayList<>(
                entityManager.createQuery("SELECT DISTINCT p.person.id FROM Patient p", String.class).getResultList());
        people.addAll(entityManager.createQuery("SELECT DISTINCT c.person.id FROM PatientContact c", String.class)
                .getResultList());
        for (String entity : CLINICAL_ENTITIES)
            delete(entity);

        // Analysis/result form a cycle through reflex parent results.
        mutate("UPDATE Analysis SET parentResult = null, parentAnalysis = null", Map.of());
        delete("Result");
        delete("Analysis");
        delete("ResultFile");
        delete("SampleItem");
        delete("Sample");
        delete("ElectronicOrder");
        delete("Patient");
        delete("History");

        people = people.stream().filter(java.util.Objects::nonNull).distinct().toList();
        for (int start = 0; start < people.size(); start += 500) {
            var candidates = entityManager.createQuery(
                    "SELECT p.id FROM Person p WHERE p.id IN (:ids)"
                            + " AND NOT EXISTS (SELECT pr.id FROM Provider pr WHERE pr.person.id = p.id)"
                            + " AND NOT EXISTS (SELECT u.id FROM SystemUser u WHERE u.linkedProviderPersonId = p.id)"
                            + " AND NOT EXISTS (SELECT c.id FROM OrganizationContact c WHERE c.person.id = p.id)"
                            + " AND NOT EXISTS (SELECT c.id FROM ExternalConnectionContact c WHERE c.person.id = p.id)"
                            + " AND NOT EXISTS (SELECT pat.id FROM Patient pat WHERE pat.person.id = p.id)",
                    String.class);
            NumericStringIdQuery.bindMany(candidates, "ids",
                    people.subList(start, Math.min(start + 500, people.size())));
            List<String> disposable = candidates.getResultList();
            if (!disposable.isEmpty()) {
                mutate("DELETE FROM PersonAddress WHERE compoundId.targetId IN (:ids)", Map.of("ids", disposable));
                mutate("DELETE FROM Person WHERE id IN (:ids)", Map.of("ids", disposable));
            }
        }
        // Bulk HQL invalidates affected caches; detach any stale managed objects.
        entityManager.clear();
    }

    @Override
    public void resetTrainingCodes(String laboratoryPrefix) {
        if (laboratoryPrefix != null) {
            mutate("UPDATE accession_number_info SET curVal = 0 WHERE accessionIdentity.prefix = :prefix"
                    + " AND accessionIdentity.type = :format",
                    Map.of("prefix", laboratoryPrefix, "format",
                            org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat.PREFIXNUM));
        }
        // Liquibase installs this guarded PostgreSQL function. ALTER SEQUENCE
        // RESTART is transactional, unlike setval(), so any later failure also
        // rolls back the public-code reset. Internal ID sequences are untouched.
        var reset = entityManager.createStoredProcedureQuery("clinlims.restart_training_cug_prefix")
                .unwrap(org.hibernate.procedure.ProcedureCall.class);
        org.hibernate.procedure.ProcedureOutputs outputs = null;
        try {
            outputs = reset.getOutputs();
            outputs.getCurrent();
        } finally {
            if (outputs != null)
                outputs.release();
        }
    }

    private void delete(String entity) {
        mutate("DELETE FROM " + entity, Map.of());
    }

    /**
     * This installation uses Hibernate's classic SELECT-only HQL translator.
     * Compile only these explicit bulk HQL statements with Hibernate's AST
     * translator, leaving every existing query's translator unchanged. No native
     * SQL, JDBC or database-wide cascades are used here.
     */
    private void mutate(String hql, Map<String, ?> parameters) {
        var session = entityManager.unwrap(org.hibernate.engine.spi.SessionImplementor.class);
        Map<String, Object> expanded = new LinkedHashMap<>();
        for (var entry : parameters.entrySet()) {
            if (entry.getValue() instanceof java.util.Collection<?> values) {
                List<String> names = new ArrayList<>();
                int index = 0;
                for (Object value : values) {
                    String name = entry.getKey() + "_" + index++;
                    names.add(":" + name);
                    expanded.put(name, value);
                }
                hql = hql.replaceAll(":" + entry.getKey() + "\\b", String.join(",", names));
            } else {
                expanded.put(entry.getKey(), entry.getValue());
            }
        }
        var translator = new org.hibernate.hql.internal.ast.ASTQueryTranslatorFactory().createQueryTranslator(hql, hql,
                Map.of(), session.getFactory(), null);
        translator.compile(Map.of(), false);
        Map<String, org.hibernate.engine.spi.TypedValue> bindings = new LinkedHashMap<>();
        expanded.forEach((name, value) -> bindings.put(name, new org.hibernate.engine.spi.TypedValue(
                translator.getParameterTranslations().getNamedParameterInformation(name).getExpectedType(), value)));
        var queryParameters = new org.hibernate.engine.spi.QueryParameters();
        queryParameters.setNamedParameters(bindings);
        queryParameters.setPositionalParameterTypes(new org.hibernate.type.Type[0]);
        queryParameters.setPositionalParameterValues(new Object[0]);
        translator.executeUpdate(queryParameters, session);
    }
}
