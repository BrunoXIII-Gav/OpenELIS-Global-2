package org.openelisglobal.audittrail.persistence;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.StandardBasicTypes;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.springframework.beans.BeanWrapperImpl;

/**
 * Resolves server-owned catalog descriptions; never changes business entities.
 */
public final class AuditValueResolver {
    private static final String ROOT = "org.openelisglobal.";
    private static final Map<String, String> DEFINITIONS = Map.of("AnalysisAdditionalFieldValue",
            "testadditionalfield.valueholder.TestAdditionalField", "SampleItemAdditionalFieldValue",
            "sample.valueholder.SampleTypeAdditionalField", "SampleOrderAdditionalFieldValue",
            "orderadditionalfield.valueholder.OrderAdditionalField", "SampleOrderAdditionalFieldFile",
            "orderadditionalfield.valueholder.OrderAdditionalField", "PatientAdditionalFieldValue",
            "patientadditionalfield.valueholder.PatientAdditionalField");
    private static final Map<String, String> REFERENCES = Map.ofEntries(
            Map.entry("status", "statusofsample.valueholder.StatusOfSample"),
            Map.entry("statusId", "statusofsample.valueholder.StatusOfSample"),
            Map.entry("typeOfSample", "typeofsample.valueholder.TypeOfSample"),
            Map.entry("typeOfSampleId", "typeofsample.valueholder.TypeOfSample"),
            Map.entry("unitOfMeasure", "unitofmeasure.valueholder.UnitOfMeasure"),
            Map.entry("test", "test.valueholder.Test"), Map.entry("testId", "test.valueholder.Test"),
            Map.entry("testSection", "test.valueholder.TestSection"),
            Map.entry("testSectionId", "test.valueholder.TestSection"),
            Map.entry("analyte", "analyte.valueholder.Analyte"), Map.entry("panel", "panel.valueholder.Panel"),
            Map.entry("testResult", "testresult.valueholder.TestResult"),
            Map.entry("method", "method.valueholder.Method"), Map.entry("roleId", "role.valueholder.Role"),
            Map.entry("role", "role.valueholder.Role"), Map.entry("patientId", "patient.valueholder.Patient"),
            Map.entry("patient", "patient.valueholder.Patient"),
            Map.entry("providerId", "provider.valueholder.Provider"),
            Map.entry("rejectReasonId", "dictionary.valueholder.Dictionary"),
            Map.entry("resultFile", "analysis.valueholder.ResultFile"),
            Map.entry("parentSampleItem", "sampleitem.valueholder.SampleItem"),
            Map.entry("collectorUserId", "systemuser.valueholder.SystemUser"),
            Map.entry("approverUserId", "systemuser.valueholder.SystemUser"),
            Map.entry("revokedBy", "systemuser.valueholder.SystemUser"),
            Map.entry("systemUserId", "systemuser.valueholder.SystemUser"),
            Map.entry("performedByUser", "systemuser.valueholder.SystemUser"),
            Map.entry("performedByUserId", "systemuser.valueholder.SystemUser"),
            Map.entry("assignedByUserId", "systemuser.valueholder.SystemUser"),
            Map.entry("movedByUserId", "systemuser.valueholder.SystemUser"));
    private final Session session;
    private final Map<String, Object> cache = new HashMap<>();

    public AuditValueResolver(Session session) {
        this.session = session;
    }

    public String storedValue(String entity, String field, String value, String definitionId) {
        if (value == null || !"fieldValue".equals(field) || !DEFINITIONS.containsKey(entity) || definitionId == null)
            return value;
        Object definition = find(DEFINITIONS.get(entity) + "Definition", definitionId);
        if (!"DOCUMENT".equals(property(definition, "fieldType")))
            return value;
        try {
            var document = new org.json.JSONObject(value);
            Object content = document.remove("base64Content");
            if (content != null)
                document.put("contentDigest", digest(content.toString()));
            return document.toString();
        } catch (org.json.JSONException e) {
            return digest(value);
        }
    }

    private String digest(String value) {
        try {
            return "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String resolve(String entity, String field, String value, String definitionId, Map<String, String> state) {
        if (value == null || value.isEmpty() || value.equals("[REDACTED]"))
            return null;
        if ((field.equals("fileContent") || field.equals("content")) && value.startsWith("sha256:"))
            return state.get("fileName") == null ? "[DOCUMENT_CHANGED]" : state.get("fileName");
        if (field.equals("signatureImageData"))
            return "[SIGNATURE_CHANGED]";
        if (field.equals("fieldValue") && DEFINITIONS.containsKey(entity) && definitionId != null) {
            String base = DEFINITIONS.get(entity);
            Object definition = find(base + "Definition", definitionId);
            String type = property(definition, "fieldType");
            if ("DOCUMENT".equals(type)) {
                try {
                    return new org.json.JSONObject(value).optString("fileName", "[DOCUMENT_CHANGED]");
                } catch (org.json.JSONException ignored) {
                    return "[DOCUMENT_CHANGED]";
                }
            }
            if ("USER".equals(type))
                return description(find("systemuser.valueholder.SystemUser", value));
            if (List.of("SELECT", "RADIO", "MULTISELECT").contains(type == null ? "" : type)) {
                String optionEntity = base + "Option";
                if (!mapped(optionEntity))
                    return null;
                String cacheKey = optionEntity + ":" + definitionId;
                @SuppressWarnings("unchecked")
                List<Object> options = (List<Object>) cache.computeIfAbsent(cacheKey, key -> session
                        .createQuery("FROM " + ROOT + optionEntity + " o WHERE o.fieldDefinitionId = :field")
                        .setParameter("field", Integer.valueOf(definitionId), StandardBasicTypes.INTEGER).list());
                String[] keys = value.split(",");
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                for (String key : keys) {
                    String label = null;
                    for (Object option : options) {
                        if (key.trim().equals(property(option, "optionKey"))) {
                            label = property(option, "optionLabel");
                            break;
                        }
                    }
                    labels.add(label == null ? key.trim() : label);
                }
                return String.join(", ", labels);
            }
        }
        if (("roles".equals(field) || "roleIds".equals(field)) && value.startsWith("[")) {
            org.json.JSONArray ids = new org.json.JSONArray(value);
            java.util.ArrayList<String> names = new java.util.ArrayList<>();
            for (int index = 0; index < ids.length(); index++) {
                String id = ids.optString(index);
                String name = description(find("role.valueholder.Role", id));
                names.add(name == null ? id : name);
            }
            return String.join(", ", names);
        }
        if (List.of("locationId", "previousLocationId", "newLocationId").contains(field)) {
            String typeField = field.replace("Id", "Type");
            String type = state.get(typeField);
            String target = type == null ? null
                    : Map.of("room", "StorageRoom", "device", "StorageDevice", "shelf", "StorageShelf", "rack",
                            "StorageRack", "box", "StorageBox").get(type.toLowerCase(java.util.Locale.ROOT));
            if (target != null)
                return description(find("storage.valueholder." + target, value));
        }
        if ("requesterId".equals(field)) {
            Object type = find("requester.valueholder.RequesterType", state.get("requesterTypeId"));
            String typeName = property(type, "requesterType");
            if ("provider".equalsIgnoreCase(typeName))
                return description(find("provider.valueholder.Provider", value));
            if ("organization".equalsIgnoreCase(typeName))
                return description(find("organization.valueholder.Organization", value));
        }
        String reference = REFERENCES.get(field);
        if (reference != null)
            return description(find(reference, value));
        return null;
    }

    public String recordLabel(AuditEvent event) {
        String entity = event.getEntityName();
        String analysisId = "Analysis".equals(entity) ? event.getEntityId() : event.getAnalysisId();
        if (analysisId == null) {
            String resultId = "Result".equals(entity) ? event.getEntityId() : event.getResultId();
            Object result = find("result.valueholder.Result", resultId);
            if (result != null)
                analysisId = property(new BeanWrapperImpl(result).getPropertyValue("analysis"), "id");
        }
        if (analysisId != null) {
            Object analysis = find("analysis.valueholder.Analysis", analysisId);
            String test = description(analysis);
            if (test != null) {
                var parts = new java.util.LinkedHashSet<String>();
                parts.add(test);
                Object item = new BeanWrapperImpl(analysis).getPropertyValue("sampleItem");
                if (item != null) {
                    Object sample = new BeanWrapperImpl(item).getPropertyValue("sample");
                    String accession = property(sample, "accessionNumber");
                    String cug = property(item, "cugCode");
                    if (accession != null && !accession.isBlank())
                        parts.add(accession);
                    if (cug != null && !cug.isBlank())
                        parts.add(cug);
                }
                return String.join(" · ", parts);
            }
        }
        if (event.getSampleItemId() != null)
            return description(find("sampleitem.valueholder.SampleItem", event.getSampleItemId()));
        if (event.getSampleId() != null)
            return description(find("sample.valueholder.Sample", event.getSampleId()));
        String name = switch (entity) {
        case "Sample" -> "sample.valueholder.Sample";
        case "SampleItem" -> "sampleitem.valueholder.SampleItem";
        case "Analysis" -> "analysis.valueholder.Analysis";
        case "Result" -> "result.valueholder.Result";
        case "Patient" -> "patient.valueholder.Patient";
        case "Person" -> "person.valueholder.Person";
        case "ResultFile" -> "analysis.valueholder.ResultFile";
        default -> null;
        };
        if (name != null)
            return description(find(name, event.getEntityId()));
        if (event.getPatientId() != null)
            return description(find("patient.valueholder.Patient", event.getPatientId()));
        return null;
    }

    /** Stable ownership snapshot, separate from user-visible field changes. */
    public String testId(String entity, String entityId, Map<String, String> context) {
        if ("Test".equals(entity))
            return entityId;
        if ("Analysis".equals(entity))
            return context.get("test");
        if (context.get("testId") != null)
            return context.get("testId");
        String analysis = context.get("analysis") == null ? context.get("analysisId") : context.get("analysis");
        if (analysis == null)
            analysis = context.get("parentAnalysis") == null ? context.get("parentAnalysisId")
                    : context.get("parentAnalysis");
        Object owner = find("analysis.valueholder.Analysis", analysis);
        if (owner == null)
            return null;
        Object test = new BeanWrapperImpl(owner).getPropertyValue("test");
        return test == null ? null : property(test, "id");
    }

    private boolean mapped(String name) {
        return session.getSessionFactory().getMetamodel().getEntities().stream()
                .anyMatch(entity -> entity.getJavaType().getName().equals(ROOT + name));
    }

    private Object find(String name, String id) {
        if (id == null || !mapped(name))
            return null;
        String key = name + ":" + id;
        if (cache.containsKey(key))
            return cache.get(key);
        var persister = session.getSessionFactory().unwrap(SessionFactoryImplementor.class).getMetamodel()
                .entityPersister(ROOT + name);
        if (persister.getIdentifierType() instanceof org.hibernate.type.CustomType custom && custom
                .getUserType() instanceof org.openelisglobal.hibernate.resources.usertype.LIMSStringNumberUserType
                && !id.matches("[0-9]+"))
            return null;
        Class<?> type = persister.getIdentifierType().getReturnedClass();
        Serializable identifier;
        try {
            identifier = type == Integer.class ? Integer.valueOf(id) : type == Long.class ? Long.valueOf(id) : id;
        } catch (NumberFormatException e) {
            return null;
        }
        Object result = session.get(ROOT + name, identifier);
        cache.put(key, result);
        return result;
    }

    private String description(Object entity) {
        if (entity == null)
            return null;
        String first = property(entity, "firstName");
        String last = property(entity, "lastName");
        if (first != null || last != null)
            return ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        var bean = new BeanWrapperImpl(entity);
        if (bean.isReadableProperty("person")) {
            String person = description(bean.getPropertyValue("person"));
            if (person != null && !person.isBlank())
                return person;
        }
        if (bean.isReadableProperty("test")) {
            String test = description(bean.getPropertyValue("test"));
            if (test != null && !test.isBlank())
                return test;
        }
        if (bean.isReadableProperty("analysis")) {
            String analysis = description(bean.getPropertyValue("analysis"));
            if (analysis != null && !analysis.isBlank())
                return analysis;
        }
        for (String field : List.of("cugCode", "accessionNumber", "fileName", "description", "displayName", "dictEntry",
                "label", "name", "role", "optionLabel", "loginName")) {
            String value = property(entity, field);
            if (value != null && !value.isBlank())
                return value;
        }
        return null;
    }

    private String property(Object entity, String property) {
        if (entity == null)
            return null;
        var bean = new BeanWrapperImpl(entity);
        if (!bean.isReadableProperty(property))
            return null;
        Object value = bean.getPropertyValue(property);
        return value == null ? null : value.toString();
    }
}
