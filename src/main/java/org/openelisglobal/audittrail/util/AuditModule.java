package org.openelisglobal.audittrail.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.openelisglobal.audittrail.valueholder.AuditEvent;

/**
 * One business classification shared by capture, historical queries and
 * presentation.
 */
public final class AuditModule {
    private static final Map<String, String> MODULES = Map.ofEntries(Map.entry("sample", "sample"),
            Map.entry("samplehuman", "sample"), Map.entry("orderadditionalfield", "sample"),
            Map.entry("requester", "sample"), Map.entry("sampleitem", "sampleitem"),
            Map.entry("sampleadditionalfield", "sampleadditionalfield"), Map.entry("patient", "patient"),
            Map.entry("person", "patient"), Map.entry("patientidentity", "patient"),
            Map.entry("patientrelation", "patient"), Map.entry("patientadditionalfield", "patient"),
            Map.entry("analysis", "result"), Map.entry("result", "result"), Map.entry("testadditionalfield", "result"),
            Map.entry("resultvalidation", "resultvalidation"), Map.entry("storage", "storage"),
            Map.entry("provider", "provider"), Map.entry("professionalprofile", "provider"),
            Map.entry("systemuser", "systemuser"), Map.entry("systemusermodule", "systemuser"),
            Map.entry("authorization", "systemuser"), Map.entry("role", "systemuser"),
            Map.entry("rolemodule", "systemuser"), Map.entry("userrole", "systemuser"), Map.entry("reports", "reports"),
            Map.entry("reportdefinition", "reportdefinition"), Map.entry("testconfiguration", "testconfiguration"),
            Map.entry("test", "testconfiguration"), Map.entry("testresult", "testconfiguration"),
            Map.entry("testanalyte", "testconfiguration"), Map.entry("resultlimit", "testconfiguration"),
            Map.entry("testdependency", "testconfiguration"), Map.entry("panel", "testconfiguration"),
            Map.entry("panelitem", "testconfiguration"), Map.entry("siteinformation", "siteinformation"),
            Map.entry("typeofsample", "siteinformation"), Map.entry("typeoftestresult", "siteinformation"),
            Map.entry("unitofmeasure", "siteinformation"), Map.entry("dictionary", "siteinformation"),
            Map.entry("localization", "siteinformation"), Map.entry("note", "sample"),
            Map.entry("observationhistory", "sample"));
    private static final Map<String, String> ENTITIES = Map.ofEntries(
            Map.entry("SampleItemAdditionalFieldValue", "sampleitem"),
            Map.entry("SampleTypeAdditionalFieldDefinition", "sampleadditionalfield"),
            Map.entry("SampleTypeAdditionalFieldOption", "sampleadditionalfield"),
            Map.entry("SampleOrderAdditionalFieldValue", "sample"),
            Map.entry("SampleOrderAdditionalFieldFile", "sample"), Map.entry("ElectronicOrder", "sample"),
            Map.entry("OrderAdditionalFieldDefinition", "siteinformation"),
            Map.entry("OrderAdditionalFieldOption", "siteinformation"),
            Map.entry("OrderFixedFieldConfig", "siteinformation"), Map.entry("PatientAdditionalFieldValue", "patient"),
            Map.entry("PatientAdditionalFieldDefinition", "siteinformation"),
            Map.entry("PatientAdditionalFieldOption", "siteinformation"),
            Map.entry("TestAdditionalFieldDefinition", "testconfiguration"),
            Map.entry("TestAdditionalFieldOption", "testconfiguration"),
            Map.entry("AnalysisAdditionalFieldValue", "result"), Map.entry("AnalysisTubeUsage", "result"),
            Map.entry("AnalysisTubeLabel", "sampleitem"), Map.entry("ResultFile", "result"));
    private static final Set<String> SAMPLE_ANALYSIS_FIELDS = Set.of("test", "panel");
    private static final Set<String> CONTEXT_ENTITIES = Set.of("Note", "ObservationHistory");
    // Person is shared by patients and professionals. Use the recorded operation
    // origin, rather than the person's current roles, which can change later.
    private static final List<String> PROVIDER_SOURCES = List.of("/rest/provider/", "/rest/providers/",
            "/rest/deleteprovider");

    private AuditModule() {
    }

    public static String canonical(String module) {
        return module == null ? null : MODULES.getOrDefault(module, module);
    }

    public static String forEntity(String entity, String module) {
        return entity == null ? canonical(module) : ENTITIES.getOrDefault(entity, canonical(module));
    }

    public static String forEvent(AuditEvent event) {
        if ("Person".equals(event.getEntityName())) {
            String source = event.getSource() == null ? "" : event.getSource().toLowerCase(Locale.ROOT);
            return "provider".equals(event.getModule()) || PROVIDER_SOURCES.stream().anyMatch(source::contains)
                    ? "provider" : "patient";
        }
        if ("Analysis".equals(event.getEntityName()))
            return SAMPLE_ANALYSIS_FIELDS.contains(event.getField()) ? "sampleitem" : "result";
        if (CONTEXT_ENTITIES.contains(event.getEntityName())) {
            if (event.getResultId() != null || event.getAnalysisId() != null || event.getParentAnalysisId() != null)
                return "result";
            if (event.getSampleItemId() != null)
                return "sampleitem";
            if (event.getSampleId() != null)
                return "sample";
            if (event.getPatientId() != null || event.getPersonId() != null)
                return "patient";
            return "sample";
        }
        return forEntity(event.getEntityName(), event.getModule());
    }

    /**
     * Parameterized HQL equivalent of forEvent; filtering precedes counts and
     * paging.
     */
    public static void restrict(StringBuilder where, Map<String, Object> parameters, String requested) {
        if (requested == null || requested.isBlank())
            return;
        String module = canonical(requested.trim());
        Set<String> sources = MODULES.entrySet().stream().filter(e -> e.getValue().equals(module))
                .map(Map.Entry::getKey).collect(Collectors.toCollection(LinkedHashSet::new));
        sources.add(module);
        Set<String> exceptions = new LinkedHashSet<>(ENTITIES.keySet());
        exceptions.addAll(CONTEXT_ENTITIES);
        exceptions.add("Analysis");
        exceptions.add("Person");
        parameters.put("moduleSources", sources);
        parameters.put("moduleExceptions", exceptions);
        List<String> clauses = new ArrayList<>();
        clauses.add("(e.module IN (:moduleSources) AND e.entityName NOT IN (:moduleExceptions))");
        Set<String> entities = ENTITIES.entrySet().stream().filter(e -> e.getValue().equals(module))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        if (!entities.isEmpty()) {
            clauses.add("e.entityName IN (:moduleEntities)");
            parameters.put("moduleEntities", entities);
        }
        if ("provider".equals(module) || "patient".equals(module)) {
            List<String> origins = new ArrayList<>();
            origins.add("e.module = 'provider'");
            for (int i = 0; i < PROVIDER_SOURCES.size(); i++) {
                String parameter = "providerSource" + i;
                origins.add("lower(coalesce(e.source, '')) LIKE :" + parameter);
                parameters.put(parameter, "%" + PROVIDER_SOURCES.get(i) + "%");
            }
            String professional = "(" + String.join(" OR ", origins) + ")";
            clauses.add("(e.entityName = 'Person' AND " + ("patient".equals(module) ? "NOT " : "")
                    + professional + ")");
        }
        if ("sampleitem".equals(module) || "result".equals(module)) {
            clauses.add("(e.entityName = 'Analysis' AND e.field " + ("sampleitem".equals(module) ? "IN" : "NOT IN")
                    + " (:sampleAnalysisFields))");
            parameters.put("sampleAnalysisFields", SAMPLE_ANALYSIS_FIELDS);
        }
        Map<String, String> context = new LinkedHashMap<>();
        context.put("result", "(e.resultId IS NOT NULL OR e.analysisId IS NOT NULL OR e.parentAnalysisId IS NOT NULL)");
        context.put("sampleitem", "e.sampleItemId IS NOT NULL");
        context.put("sample", "e.sampleId IS NOT NULL");
        context.put("patient", "(e.patientId IS NOT NULL OR e.personId IS NOT NULL)");
        List<String> earlier = new ArrayList<>();
        for (var entry : context.entrySet()) {
            if (entry.getKey().equals(module)) {
                String condition = entry.getValue();
                for (String previous : earlier)
                    condition += " AND NOT (" + previous + ")";
                clauses.add("(e.entityName IN (:contextEntities) AND " + condition + ")");
                parameters.put("contextEntities", CONTEXT_ENTITIES);
            }
            earlier.add(entry.getValue());
        }
        if ("sample".equals(module)) {
            clauses.add("(e.entityName IN (:contextEntities) AND NOT (" + String.join(" OR ", earlier) + "))");
            parameters.put("contextEntities", CONTEXT_ENTITIES);
        }
        where.append(" AND (").append(String.join(" OR ", clauses)).append(")");
    }
}
