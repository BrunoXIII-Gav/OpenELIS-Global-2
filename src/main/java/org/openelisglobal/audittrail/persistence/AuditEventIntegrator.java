package org.openelisglobal.audittrail.persistence;

import org.hibernate.boot.Metadata;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

public class AuditEventIntegrator implements Integrator {
    @Override
    public void integrate(Metadata metadata, SessionFactoryImplementor factory,
            SessionFactoryServiceRegistry services) {
        AuditEventListener listener = new AuditEventListener();
        EventListenerRegistry registry = services.getService(EventListenerRegistry.class);
        registry.appendListeners(EventType.POST_INSERT, listener);
        registry.appendListeners(EventType.POST_UPDATE, listener);
        registry.appendListeners(EventType.POST_DELETE, listener);
        registry.appendListeners(EventType.PRE_DELETE, listener);
        registry.appendListeners(EventType.PRE_UPDATE, listener);
        AuditCollectionListener collections = new AuditCollectionListener();
        registry.appendListeners(EventType.PRE_COLLECTION_UPDATE, collections);
        registry.appendListeners(EventType.PRE_COLLECTION_RECREATE, collections);
        registry.appendListeners(EventType.PRE_COLLECTION_REMOVE, collections);
    }

    @Override
    public void disintegrate(SessionFactoryImplementor factory, SessionFactoryServiceRegistry services) {
    }
}
