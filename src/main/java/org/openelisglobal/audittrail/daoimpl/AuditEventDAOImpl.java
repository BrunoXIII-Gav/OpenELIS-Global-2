package org.openelisglobal.audittrail.daoimpl;

import jakarta.persistence.TypedQuery;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.audittrail.form.AuditEventFilter;
import org.openelisglobal.audittrail.form.AuditEventPage;
import org.openelisglobal.audittrail.persistence.AuditValueResolver;
import org.openelisglobal.audittrail.util.AuditFieldPolicy;
import org.openelisglobal.audittrail.util.AuditModule;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class AuditEventDAOImpl extends org.openelisglobal.common.daoimpl.BaseDAOImpl<AuditEvent, String>
        implements AuditEventDAO {
    public AuditEventDAOImpl() {
        super(AuditEvent.class);
    }

    @Override
    public AuditEventPage search(AuditEventFilter filter) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        where.append(" AND NOT EXISTS (SELECT archived.id FROM ArchivedAuditEvent archived WHERE archived.id = e.id)");
        AuditFieldPolicy.restrict(where, parameters);
        AuditModule.restrict(where, parameters, filter.module());
        exact(where, parameters, "entityName", filter.entityName());
        exact(where, parameters, "entityId", filter.entityId());
        exact(where, parameters, "actorId", filter.actorId());
        if (filter.field() != null && !filter.field().isBlank()) {
            where.append(" AND (e.field = :field OR lower(e.fieldLabel) LIKE :fieldLabel ESCAPE '!')");
            parameters.put("field", filter.field().trim());
            parameters.put("fieldLabel", "%" + filter.field().trim().toLowerCase(java.util.Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        exact(where, parameters, "action", filter.action());
        if (filter.from() != null) {
            where.append(" AND e.occurredAt >= :from");
            parameters.put("from", Timestamp.from(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND e.occurredAt <= :to");
            parameters.put("to", Timestamp.from(filter.to()));
        }
        if (filter.accessionNumber() != null && !filter.accessionNumber().isBlank()) {
            String scope = accessionScope(filter.accessionNumber().trim(), parameters);
            where.append(" AND (").append(scope).append(")");
        }
        if (filter.testId() != null && !filter.testId().isBlank()) {
            restrictTest(where, parameters, filter.testId().trim());
        }
        TypedQuery<Long> count = entityManager.createQuery("SELECT COUNT(e) FROM AuditEvent e" + where, Long.class);
        bind(count, parameters);
        long total = count.getSingleResult();
        TypedQuery<AuditEvent> query = entityManager.createQuery(
                "FROM AuditEvent e" + where + " ORDER BY e.occurredAt DESC, e.operationId DESC, e.id DESC",
                AuditEvent.class);
        bind(query, parameters);
        AuditValueResolver values = new AuditValueResolver(entityManager.unwrap(org.hibernate.Session.class));
        List<AuditEventPage.Item> items = query.setFirstResult(filter.page() * filter.size())
                .setMaxResults(filter.size()).getResultList().stream().map(
                        event -> AuditEventPage.Item.from(event,
                                event.getOldDisplayValue() != null ? event.getOldDisplayValue()
                                        : values.resolve(event.getEntityName(), event.getField(), event.getOldValue(),
                                                event.getFieldDefinitionId(), Map.of()),
                                event.getNewDisplayValue() != null ? event.getNewDisplayValue()
                                        : values.resolve(event.getEntityName(), event.getField(), event.getNewValue(),
                                                event.getFieldDefinitionId(), Map.of()),
                                values.recordLabel(event)))
                .toList();
        return new AuditEventPage(items, total, filter.page(), filter.size());
    }

    private void bind(TypedQuery<?> query, Map<String, Object> parameters) {
        parameters.forEach((key, value) -> {
            if ("clinicalTestId".equals(key))
                org.openelisglobal.common.util.NumericStringIdQuery.bind(query, key, value.toString());
            else
                query.setParameter(key, value);
        });
    }

    private void restrictTest(StringBuilder where, Map<String, Object> parameters, String testId) {
        parameters.put("selectedTestId", testId);
        parameters.put("clinicalTestId", testId);
        String analyses = "SELECT concat(a.id, '') FROM Analysis a WHERE a.test.id = :clinicalTestId";
        String historical = "SELECT link.entityId FROM AuditEvent link WHERE link.entityName = 'Analysis'"
                + " AND link.field = 'test' AND (link.oldValue = :selectedTestId OR link.newValue = :selectedTestId)";
        List<String> legacy = new ArrayList<>();
        for (String relationship : List.of("analysisId", "parentAnalysisId"))
            legacy.add(
                    "e." + relationship + " IN (" + analyses + ") OR e." + relationship + " IN (" + historical + ")");
        legacy.add("(e.entityName = 'Analysis' AND (e.entityId IN (" + analyses + ") OR e.entityId IN (" + historical
                + ")))");
        String results = "SELECT concat(r.id, '') FROM Result r WHERE r.analysis.id IN ("
                + "SELECT a.id FROM Analysis a WHERE a.test.id = :clinicalTestId)";
        legacy.add("(e.entityName = 'Result' AND e.entityId IN (" + results + ")) OR e.resultId IN (" + results + ")");
        legacy.add("(e.entityName = 'ResultFile' AND e.entityId IN (SELECT concat(a.resultFile.id, '')"
                + " FROM Analysis a WHERE a.test.id = :clinicalTestId))");
        legacy.add("(e.entityName = 'Test' AND e.entityId = :selectedTestId)");
        // The database evaluates ownership and limits the page. Never materialize
        // all analyses/results or rewrite historical immutable ledger rows.
        where.append(" AND (e.testId = :selectedTestId OR (e.testId IS NULL AND (").append(String.join(" OR ", legacy))
                .append(")))");
    }

    private void exact(StringBuilder where, Map<String, Object> parameters, String field, String value) {
        if (value != null && !value.isBlank()) {
            where.append(" AND e.").append(field).append(" = :").append(field);
            parameters.put(field, value.trim());
        }
    }

    private Set<String> ids(String hql, String parameter, Object value) {
        return ids(hql, parameter, value, String.class);
    }

    private <T> Set<String> ids(String hql, String parameter, Object value, Class<T> resultType) {
        TypedQuery<T> query = entityManager.createQuery(hql, resultType);
        if (value instanceof java.util.Collection<?> ids && !hql.contains("FROM AuditEvent")) {
            org.openelisglobal.common.util.NumericStringIdQuery.bindMany(query, parameter,
                    ids.stream().map(String::valueOf).toList());
        } else {
            query.setParameter(parameter, value);
        }
        Set<String> ids = new LinkedHashSet<>();
        query.getResultList().forEach(id -> ids.add(id == null ? null : id.toString()));
        return ids;
    }

    /**
     * Include deleted descendants using the immutable links captured on their
     * events.
     */
    private String accessionScope(String accession, Map<String, Object> parameters) {
        Set<String> samples = ids("SELECT s.id FROM Sample s WHERE s.accessionNumber = :value", "value", accession);
        samples.addAll(ids("SELECT i.sample.id FROM SampleItem i WHERE i.cugCode = :value", "value", accession));
        samples.addAll(ids(
                "SELECT e.entityId FROM AuditEvent e WHERE e.entityName = 'Sample'"
                        + " AND e.field = 'accessionNumber' AND (e.oldValue = :value OR e.newValue = :value)",
                "value", accession));
        samples.addAll(ids(
                "SELECT e.sampleId FROM AuditEvent e WHERE e.entityName = 'SampleItem'"
                        + " AND e.field = 'cugCode' AND (e.oldValue = :value OR e.newValue = :value)",
                "value", accession));
        entityManager
                .createQuery(
                        "SELECT v.sampleId FROM SampleOrderAdditionalFieldValue v, OrderAdditionalFieldDefinition d"
                                + " WHERE v.fieldDefinitionId = d.id AND d.searchable = true AND v.fieldValue = :value",
                        Integer.class)
                .setParameter("value", accession).getResultList().forEach(id -> samples.add(id.toString()));
        samples.remove(null);
        if (samples.isEmpty())
            return "1=0";
        Set<String> items = ids("SELECT i.id FROM SampleItem i WHERE i.sample.id IN (:ids)", "ids", samples);
        items.addAll(
                ids("SELECT e.entityId FROM AuditEvent e WHERE e.entityName = 'SampleItem' AND e.sampleId IN (:ids)",
                        "ids", samples));
        Set<String> analyses = new LinkedHashSet<>();
        if (!items.isEmpty()) {
            analyses.addAll(ids("SELECT a.id FROM Analysis a WHERE a.sampleItem.id IN (:ids)", "ids", items));
            analyses.addAll(ids(
                    "SELECT e.entityId FROM AuditEvent e WHERE e.entityName = 'Analysis' AND e.sampleItemId IN (:ids)",
                    "ids", items));
        }
        Set<String> results = new LinkedHashSet<>();
        if (!analyses.isEmpty()) {
            results.addAll(ids("SELECT r.id FROM Result r WHERE r.analysis.id IN (:ids)", "ids", analyses));
            results.addAll(
                    ids("SELECT e.entityId FROM AuditEvent e WHERE e.entityName = 'Result' AND e.analysisId IN (:ids)",
                            "ids", analyses));
        }
        Set<String> files = new LinkedHashSet<>();
        if (!analyses.isEmpty()) {
            files.addAll(
                    ids("SELECT a.resultFile.id FROM Analysis a WHERE a.id IN (:ids)", "ids", analyses, Integer.class));
            files.addAll(ids(
                    "SELECT e.oldValue FROM AuditEvent e WHERE e.entityName = 'Analysis' AND e.entityId IN (:ids) AND e.field = 'resultFile'",
                    "ids", analyses));
            files.addAll(ids(
                    "SELECT e.newValue FROM AuditEvent e WHERE e.entityName = 'Analysis' AND e.entityId IN (:ids) AND e.field = 'resultFile'",
                    "ids", analyses));
            files.remove(null);
        }
        Set<String> patients = ids("SELECT h.patientId FROM SampleHuman h WHERE h.sampleId IN (:ids)", "ids", samples);
        patients.addAll(ids("SELECT e.patientId FROM AuditEvent e WHERE e.sampleId IN (:ids)", "ids", samples));
        patients.addAll(ids("SELECT e.oldValue FROM AuditEvent e WHERE e.entityName = 'SampleHuman'"
                + " AND e.field = 'patientId' AND e.sampleId IN (:ids)", "ids", samples));
        patients.addAll(ids("SELECT e.newValue FROM AuditEvent e WHERE e.entityName = 'SampleHuman'"
                + " AND e.field = 'patientId' AND e.sampleId IN (:ids)", "ids", samples));
        patients.remove(null);
        Set<String> persons = new LinkedHashSet<>();
        if (!patients.isEmpty()) {
            persons.addAll(ids("SELECT p.person.id FROM Patient p WHERE p.id IN (:ids)", "ids", patients));
            persons.addAll(
                    ids("SELECT e.personId FROM AuditEvent e WHERE e.entityName = 'Patient' AND e.entityId IN (:ids)",
                            "ids", patients));
            persons.remove(null);
        }
        List<String> conditions = new ArrayList<>();
        scope(conditions, parameters, "Sample", "sampleId", "samples", samples);
        scope(conditions, parameters, "SampleItem", "sampleItemId", "sampleItems", items);
        scope(conditions, parameters, "Analysis", "analysisId", "analyses", analyses);
        if (!analyses.isEmpty())
            conditions.add("e.parentAnalysisId IN (:analyses)");
        scope(conditions, parameters, "Result", "resultId", "results", results);
        if (!files.isEmpty()) {
            conditions.add("(e.entityName = 'ResultFile' AND e.entityId IN (:files))");
            parameters.put("files", files);
        }
        scope(conditions, parameters, "Patient", "patientId", "patients", patients);
        scope(conditions, parameters, "Person", "personId", "persons", persons);
        return String.join(" OR ", conditions);
    }

    private void scope(List<String> conditions, Map<String, Object> parameters, String entity, String relationship,
            String parameter, Set<String> ids) {
        if (!ids.isEmpty()) {
            conditions.add("(e.entityName = '" + entity + "' AND e.entityId IN (:" + parameter + "))");
            conditions.add("e." + relationship + " IN (:" + parameter + ")");
            parameters.put(parameter, ids);
        }
    }
}
