package org.openelisglobal.audittrail.form;

import java.sql.Timestamp;
import java.util.List;
import org.openelisglobal.audittrail.util.AuditModule;
import org.openelisglobal.audittrail.valueholder.AuditEvent;

public record AuditEventPage(List<Item> items, long total, int page, int size) {
    public record Item(String id, String operationId, String requestId, Timestamp occurredAt, String module,
            String entityName, String entityId, String action, String field, String fieldLabel, String oldValue,
            String newValue, String oldDisplayValue, String newDisplayValue, String actorId, String actorName,
            String actorSource, String source, String fieldDefinitionId, String recordLabel,
            boolean oldDescriptionFromCurrentCatalog, boolean newDescriptionFromCurrentCatalog) {
        public static Item from(AuditEvent event) {
            return from(event, event.getOldDisplayValue(), event.getNewDisplayValue(), null);
        }

        public static Item from(AuditEvent event, String oldDisplay, String newDisplay, String recordLabel) {
            return new Item(event.getId(), event.getOperationId(), event.getRequestId(), event.getOccurredAt(),
                    AuditModule.forEvent(event), event.getEntityName(), event.getEntityId(), event.getAction(),
                    event.getField(), event.getFieldLabel(), event.getOldValue(), event.getNewValue(), oldDisplay,
                    newDisplay, event.getActorId(), event.getActorName(), event.getActorSource(), event.getSource(),
                    event.getFieldDefinitionId(), recordLabel, event.getOldDisplayValue() == null && oldDisplay != null,
                    event.getNewDisplayValue() == null && newDisplay != null);
        }
    }
}
