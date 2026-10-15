package org.openelisglobal.audittrail.persistence;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.hibernate.event.spi.*;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.type.ComponentType;
import org.hibernate.type.Type;
import org.openelisglobal.audittrail.util.AuditFieldPolicy;
import org.openelisglobal.audittrail.util.AuditModule;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldDefinition;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldDefinition;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition;

/**
 * Captures Hibernate's persistent state (including managed changes and
 * cascades). Inserts are queued until before transaction completion, outside
 * entity flushing, and are flushed in the original transaction. An audit
 * failure aborts that commit.
 */
public class AuditEventListener implements PostInsertEventListener, PostUpdateEventListener, PostDeleteEventListener,
        PreDeleteEventListener, PreUpdateEventListener {
    private static final Set<String> MODULES = Set.of("sample", "sampleitem", "samplehuman", "sampleadditionalfield",
            "orderadditionalfield", "testadditionalfield", "patientidentity", "patientrelation", "patient",
            "patientadditionalfield", "person", "analysis", "result", "resultvalidation", "test", "testresult",
            "testanalyte", "resultlimit", "testdependency", "authorization", "role", "rolemodule", "userrole",
            "systemuser", "systemusermodule", "reportdefinition", "reports", "provider", "siteinformation",
            "professionalprofile", "storage", "testconfiguration", "typeofsample", "typeoftestresult", "unitofmeasure",
            "panel", "panelitem", "dictionary", "localization", "note", "requester", "observationhistory");
    private static final Set<String> TECHNICAL = Set.of("lastupdated", "lastUpdated", "sysUserId", "sys_user_id",
            "persistedSysUserId");

    @Override
    public void onPostInsert(PostInsertEvent event) {
        capture(event.getEntity(), event.getId(), null, event.getState(), event.getPersister(), event.getSession(),
                "I");
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        capture(event.getEntity(), event.getId(), event.getOldState(), event.getState(), event.getPersister(),
                event.getSession(), "U");
    }

    @Override
    public void onPostDelete(PostDeleteEvent event) {
        capture(event.getEntity(), event.getId(), event.getDeletedState(), null, event.getPersister(),
                event.getSession(), "D");
    }

    @Override
    public boolean onPreDelete(PreDeleteEvent event) {
        protectLedger(event.getEntity());
        return false;
    }

    @Override
    public boolean onPreUpdate(PreUpdateEvent event) {
        protectLedger(event.getEntity());
        return false;
    }

    private void protectLedger(Object entity) {
        if (entity instanceof AuditEvent) {
            throw new IllegalStateException("Audit events cannot be changed or deleted");
        }
    }

    @Override
    public boolean requiresPostCommitHanding(EntityPersister persister) {
        return false;
    }

    static String moduleOf(String name) {
        if (name == null || !name.startsWith("org.openelisglobal."))
            return null;
        String[] parts = name.split("\\.");
        if (name.equals("org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder"))
            return "sample";
        return parts.length >= 4 && MODULES.contains(parts[2])
                ? AuditModule.forEntity(parts[parts.length - 1], parts[2])
                : null;
    }

    private void capture(Object entity, Serializable id, Object[] before, Object[] after, EntityPersister persister,
            EventSource session, String action) {
        String name = persister.getEntityName();
        String[] parts = name.split("\\.");
        if (moduleOf(name) == null) {
            return;
        }
        if ("U".equals(action) && before == null) {
            throw new IllegalStateException("Previous persistent state is required for audit: " + name);
        }
        String entityName = parts[parts.length - 1];
        Map<String, String> oldState = snapshot(before, persister, session);
        Map<String, String> newState = snapshot(after, persister, session);
        Map<String, String> context = new LinkedHashMap<>(oldState);
        context.putAll(newState);
        enrichReferenceLinks(entityName, context, session);
        FieldDescriptor descriptor = fieldDescriptor(entityName, context.get("fieldDefinitionId"), session);
        List<AuditEvent> events = new ArrayList<>();
        String operationId = UUID.randomUUID().toString();
        Timestamp timestamp = Timestamp.from(Instant.now());
        AuditActor actor = null;
        AuditValueResolver values = new AuditValueResolver(session);
        String testId = values.testId(entityName, identifier(id, persister.getIdentifierType(), session), context);
        for (String field : persister.getPropertyNames()) {
            if (!AuditFieldPolicy.includes(entityName, field) || TECHNICAL.contains(field)
                    || (!oldState.containsKey(field) && !newState.containsKey(field))) {
                continue;
            }
            String oldValue = oldState.get(field);
            String newValue = newState.get(field);
            if (Objects.equals(oldValue, newValue)) {
                continue;
            }
            if (actor == null) {
                actor = AuditActor.current(entity);
            }
            AuditEvent audit = new AuditEvent();
            audit.setId(UUID.randomUUID().toString());
            audit.setOperationId(operationId);
            audit.setRequestId(actor.requestId());
            audit.setOccurredAt(timestamp);
            audit.setModule(moduleOf(name));
            audit.setEntityName(entityName);
            audit.setEntityId(identifier(id, persister.getIdentifierType(), session));
            audit.setAction(action);
            audit.setField(field);
            audit.setFieldLabel("fieldValue".equals(field) ? descriptor.label() : field);
            boolean secret = sensitive(field) || sensitive(context.get("name"));
            audit.setOldValue(secret && oldValue != null ? "[REDACTED]"
                    : values.storedValue(entityName, field, oldValue, context.get("fieldDefinitionId")));
            audit.setNewValue(secret && newValue != null ? "[REDACTED]"
                    : values.storedValue(entityName, field, newValue, context.get("fieldDefinitionId")));
            if (!secret) {
                audit.setOldDisplayValue(
                        values.resolve(entityName, field, oldValue, context.get("fieldDefinitionId"), oldState));
                audit.setNewDisplayValue(
                        values.resolve(entityName, field, newValue, context.get("fieldDefinitionId"), newState));
            }
            audit.setActorId(actor.id());
            audit.setActorName(actor.name());
            audit.setActorSource(actor.kind());
            audit.setSource(actor.source());
            audit.setSampleId(link(context, "sample", "sampleId"));
            audit.setSampleItemId(link(context, "sampleItem", "sampleItemId"));
            audit.setAnalysisId(link(context, "analysis", "analysisId"));
            audit.setTestId(testId);
            audit.setResultId(link(context, "result", "resultId"));
            audit.setParentAnalysisId(link(context, "parentAnalysis", "parentAnalysisId"));
            audit.setPatientId(link(context, "patient", "patientId"));
            audit.setPersonId(link(context, "person", "personId"));
            audit.setFieldDefinitionId(context.get("fieldDefinitionId"));
            audit.setModule(AuditModule.forEvent(audit));
            events.add(audit);
        }
        if (!events.isEmpty()) {
            AuditEventQueue.append(session, events);
        }
    }

    private void enrichReferenceLinks(String entity, Map<String, String> state, EventSource session) {
        String tableId = "Note".equals(entity) ? state.get("referenceTableId")
                : "DocumentTrack".equals(entity) ? state.get("tableId") : null;
        String recordId = "Note".equals(entity) ? state.get("referenceId") : state.get("recordId");
        if (tableId == null || recordId == null)
            return;
        var table = session.get(org.openelisglobal.referencetables.valueholder.ReferenceTables.class, tableId);
        if (table == null || table.getTableName() == null)
            return;
        String link = switch (table.getTableName().toUpperCase(java.util.Locale.ROOT)) {
        case "SAMPLE" -> "sampleId";
        case "SAMPLE_ITEM" -> "sampleItemId";
        case "ANALYSIS" -> "analysisId";
        case "RESULT" -> "resultId";
        case "PATIENT" -> "patientId";
        case "PERSON" -> "personId";
        default -> null;
        };
        if (link != null)
            state.put(link, recordId);
    }

    private String link(Map<String, String> state, String relationship, String scalar) {
        return state.get(relationship) == null ? state.get(scalar) : state.get(relationship);
    }

    Map<String, String> snapshot(Object[] state, EntityPersister persister, EventSource session) {
        Map<String, String> result = new LinkedHashMap<>();
        if (state == null) {
            return result;
        }
        String[] names = persister.getPropertyNames();
        Type[] types = persister.getPropertyTypes();
        for (int i = 0; i < names.length; i++) {
            if (!types[i].isCollectionType() && !TECHNICAL.contains(names[i])) {
                Object value = state[i];
                if ("signatureImageData".equals(names[i]) && value instanceof String data) {
                    value = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                }
                result.put(names[i], identifier(value, types[i], session));
            }
        }
        return result;
    }

    String identifier(Object value, Type type, EventSource session) {
        if (value == null) {
            return null;
        }
        if (type.isEntityType()) {
            if (value instanceof HibernateProxy proxy) {
                return String.valueOf(proxy.getHibernateLazyInitializer().getIdentifier());
            }
            return String.valueOf(
                    session.getEntityPersister(type.getReturnedClass().getName(), value).getIdentifier(value, session));
        }
        if (type instanceof ComponentType component) {
            Map<String, String> values = new java.util.TreeMap<>();
            Object[] properties = component.getPropertyValues(value, session);
            for (int i = 0; i < properties.length; i++) {
                values.put(component.getPropertyNames()[i],
                        identifier(properties[i], component.getSubtypes()[i], session));
            }
            return new org.json.JSONObject(values).toString();
        }
        if (value instanceof byte[] bytes) {
            try {
                return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof java.util.Date date) {
            return Instant.ofEpochMilli(date.getTime()).toString();
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Enum<?> || value instanceof UUID
                || value instanceof java.time.temporal.TemporalAccessor) {
            return value.toString();
        }
        // Hibernate custom scalar types must provide a safe, stable representation.
        return type.toLoggableString(value, session.getFactory());
    }

    private boolean sensitive(String value) {
        return value != null && value.toLowerCase(java.util.Locale.ROOT)
                .matches(".*(password|passwd|secret|token|credential|private.?key).*");
    }

    private record FieldDescriptor(String label, boolean userField) {
    }

    private FieldDescriptor fieldDescriptor(String entity, String id, EventSource session) {
        if (id == null) {
            return new FieldDescriptor("fieldValue", false);
        }
        Object definition = switch (entity) {
        case "AnalysisAdditionalFieldValue" -> session.get(TestAdditionalFieldDefinition.class, Integer.valueOf(id));
        case "SampleItemAdditionalFieldValue" ->
            session.get(SampleTypeAdditionalFieldDefinition.class, Integer.valueOf(id));
        case "PatientAdditionalFieldValue" -> session.get(PatientAdditionalFieldDefinition.class, Integer.valueOf(id));
        case "SampleOrderAdditionalFieldValue", "SampleOrderAdditionalFieldFile" ->
            session.get(OrderAdditionalFieldDefinition.class, Integer.valueOf(id));
        default -> null;
        };
        if (definition instanceof TestAdditionalFieldDefinition field)
            return new FieldDescriptor(field.getDisplayName(), "USER".equals(field.getFieldType()));
        if (definition instanceof SampleTypeAdditionalFieldDefinition field)
            return new FieldDescriptor(field.getDisplayName(), "USER".equals(field.getFieldType()));
        if (definition instanceof PatientAdditionalFieldDefinition field)
            return new FieldDescriptor(field.getDisplayName(), "USER".equals(field.getFieldType()));
        if (definition instanceof OrderAdditionalFieldDefinition field)
            return new FieldDescriptor(field.getDisplayName(), "USER".equals(field.getFieldType()));
        return new FieldDescriptor("fieldValue [" + id + "]", false);
    }
}
