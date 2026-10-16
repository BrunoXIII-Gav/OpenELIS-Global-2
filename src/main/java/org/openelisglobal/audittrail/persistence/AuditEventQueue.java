package org.openelisglobal.audittrail.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import org.hibernate.event.spi.EventSource;
import org.openelisglobal.audittrail.valueholder.AuditEvent;

/**
 * One batched audit flush per transaction; shared by scalar and collection
 * capture.
 */
final class AuditEventQueue {
    private static final Map<EventSource, List<AuditEvent>> PENDING = Collections.synchronizedMap(new WeakHashMap<>());

    private AuditEventQueue() {
    }

    static void append(EventSource session, List<AuditEvent> events) {
        synchronized (PENDING) {
            List<AuditEvent> batch = PENDING.get(session);
            if (batch == null) {
                batch = new ArrayList<>();
                PENDING.put(session, batch);
                List<AuditEvent> pending = batch;
                session.getActionQueue().registerProcess(completionSession -> {
                    while (!pending.isEmpty()) {
                        List<AuditEvent> next = new ArrayList<>(pending);
                        pending.clear();
                        next.forEach(completionSession::persist);
                        completionSession.flush();
                    }
                });
                session.getActionQueue().registerProcess((success, completionSession) -> {
                    // Hibernate may retain a before-completion process after rollback
                    // when the same Session starts another transaction. Empty its
                    // captured batch as well as removing the session registration.
                    pending.clear();
                    PENDING.remove(session);
                });
            }
            batch.addAll(events);
        }
    }
}
