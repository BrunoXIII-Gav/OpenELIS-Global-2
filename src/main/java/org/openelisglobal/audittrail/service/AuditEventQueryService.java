package org.openelisglobal.audittrail.service;

import org.openelisglobal.audittrail.form.AuditEventFilter;
import org.openelisglobal.audittrail.form.AuditEventPage;

public interface AuditEventQueryService {
    AuditEventPage search(AuditEventFilter filter, String userId);

    java.util.List<java.util.Map<String, String>> tests(String userId);
}
