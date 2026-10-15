package org.openelisglobal.audittrail.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.openelisglobal.audittrail.form.AuditEventFilter;
import org.openelisglobal.audittrail.form.AuditEventPage;
import org.openelisglobal.audittrail.service.AuditEventQueryService;
import org.openelisglobal.common.rest.BaseRestController;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditEventRestController extends BaseRestController {
    private final AuditEventQueryService service;

    public AuditEventRestController(AuditEventQueryService service) {
        this.service = service;
    }

    @GetMapping("/rest/audit-events")
    public ResponseEntity<AuditEventPage> search(HttpServletRequest request,
            @RequestParam(required = false) String accessionNumber, @RequestParam(required = false) String module,
            @RequestParam(required = false) String entityName, @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String testId,
            @RequestParam(required = false) String actorId, @RequestParam(required = false) String field,
            @RequestParam(required = false) String action, @RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        if (getSysUserId(request) == null)
            return ResponseEntity.status(401).build();
        AuditEventFilter filter;
        try {
            filter = new AuditEventFilter(accessionNumber, module, entityName, entityId, actorId, field, action,
                    parse(from), parse(to), testId, page, size);
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            return ResponseEntity.badRequest().build();
        }
        try {
            return ResponseEntity.ok(service.search(filter, getSysUserId(request)));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @GetMapping("/rest/audit-events/tests")
    public ResponseEntity<java.util.List<java.util.Map<String, String>>> tests(HttpServletRequest request) {
        String userId = getSysUserId(request);
        if (userId == null)
            return ResponseEntity.status(401).build();
        try {
            return ResponseEntity.ok(service.tests(userId));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).build();
        }
    }

    private Instant parse(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }
}
