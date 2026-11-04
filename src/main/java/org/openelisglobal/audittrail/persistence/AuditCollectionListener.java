package org.openelisglobal.audittrail.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.engine.spi.CollectionEntry;
import org.hibernate.event.spi.*;
import org.hibernate.persister.collection.CollectionPersister;
import org.json.JSONArray;
import org.openelisglobal.audittrail.util.AuditFieldPolicy;
import org.openelisglobal.audittrail.valueholder.AuditEvent;

/**
 * Captures join-table and element-collection changes, including lab-unit roles.
 */
public class AuditCollectionListener implements PreCollectionUpdateEventListener, PreCollectionRecreateEventListener,
        PreCollectionRemoveEventListener {
    @Override
    public void onPreUpdateCollection(PreCollectionUpdateEvent event) {
        capture(event, "U");
    }

    @Override
    public void onPreRecreateCollection(PreCollectionRecreateEvent event) {
        capture(event, "I");
    }

    @Override
    public void onPreRemoveCollection(PreCollectionRemoveEvent event) {
        capture(event, "D");
    }

    private void capture(AbstractCollectionEvent event, String action) {
        String name = event.getAffectedOwnerEntityName();
        String module = AuditEventListener.moduleOf(name);
        if (module == null)
            return;
        CollectionEntry entry = event.getSession().getPersistenceContext().getCollectionEntry(event.getCollection());
        CollectionPersister persister = "I".equals(action) ? entry.getCurrentPersister() : entry.getLoadedPersister();
        if (persister == null || persister.isInverse())
            return;
        String entity = name.substring(name.lastIndexOf('.') + 1);
        String property = persister.getRole().substring(persister.getRole().lastIndexOf('.') + 1);
        if (!AuditFieldPolicy.includes(entity, property))
            return;
        Object current = event.getCollection().wasInitialized() ? event.getCollection().getValue() : null;
        boolean ordered = current instanceof List<?>;
        String oldValue = "I".equals(action) ? null
                : serialize(event.getCollection().getStoredSnapshot(), ordered, persister, event);
        String newValue = "D".equals(action) ? null : serialize(current, ordered, persister, event);
        if (Objects.equals(oldValue, newValue))
            return;
        AuditActor actor = AuditActor.current(event.getAffectedOwnerOrNull());
        AuditEvent audit = new AuditEvent();
        audit.setId(UUID.randomUUID().toString());
        audit.setOperationId(UUID.randomUUID().toString());
        audit.setRequestId(actor.requestId());
        audit.setModule(module);
        audit.setEntityName(name.substring(name.lastIndexOf('.') + 1));
        audit.setEntityId(String.valueOf(event.getAffectedOwnerIdOrNull()));
        if ("Test".equals(entity))
            audit.setTestId(audit.getEntityId());
        String role = persister.getRole();
        audit.setField(role.substring(role.lastIndexOf('.') + 1));
        audit.setFieldLabel(audit.getField());
        audit.setAction(action);
        audit.setOldValue(oldValue);
        audit.setNewValue(newValue);
        AuditValueResolver values = new AuditValueResolver(event.getSession());
        audit.setOldDisplayValue(values.resolve(entity, property, oldValue, null, Map.of()));
        audit.setNewDisplayValue(values.resolve(entity, property, newValue, null, Map.of()));
        audit.setActorId(actor.id());
        audit.setActorName(actor.name());
        audit.setActorSource(actor.kind());
        audit.setSource(actor.source());
        audit.setOccurredAt(java.sql.Timestamp.from(java.time.Instant.now()));
        AuditEventQueue.append(event.getSession(), List.of(audit));
    }

    private String serialize(Object value, boolean ordered, CollectionPersister persister,
            AbstractCollectionEvent event) {
        if (value == null)
            return "[UNAVAILABLE]";
        // Set snapshots are maps of elements to their saved copies. Map-valued
        // associations preserve their keys as well as the element identifiers.
        if (value instanceof Map<?, ?> map && event.getCollection().getValue() instanceof Map<?, ?>) {
            Map<String, String> values = new java.util.TreeMap<>();
            map.forEach((key, element) -> values.put(String.valueOf(key),
                    new AuditEventListener().identifier(element, persister.getElementType(), event.getSession())));
            return new org.json.JSONObject(values).toString();
        }
        Collection<?> elements = value instanceof Map<?, ?> map ? map.values() : (Collection<?>) value;
        List<String> values = new ArrayList<>();
        elements.forEach(element -> values
                .add(new AuditEventListener().identifier(element, persister.getElementType(), event.getSession())));
        if (!ordered)
            values.sort(java.util.Comparator.nullsFirst(String::compareTo));
        return new JSONArray(values).toString();
    }
}
