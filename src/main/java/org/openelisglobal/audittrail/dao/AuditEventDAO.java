package org.openelisglobal.audittrail.dao;

import org.openelisglobal.audittrail.form.AuditEventFilter;
import org.openelisglobal.audittrail.form.AuditEventPage;

/** Read-only access: the event listener exclusively appends audit events. */
public interface AuditEventDAO
        extends org.openelisglobal.common.dao.BaseDAO<org.openelisglobal.audittrail.valueholder.AuditEvent, String> {
    AuditEventPage search(AuditEventFilter filter);
}
