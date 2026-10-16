package org.openelisglobal.audittrail.service;

import org.openelisglobal.administration.service.AdministrationAuthorizationService;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.audittrail.form.AuditEventFilter;
import org.openelisglobal.audittrail.form.AuditEventPage;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.security.service.UserPermissionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuditEventQueryServiceImpl implements AuditEventQueryService {
    private final AuditEventDAO dao;
    private final UserPermissionService permissions;
    private final AdministrationAuthorizationService administration;
    @org.springframework.beans.factory.annotation.Autowired
    private org.openelisglobal.testconfiguration.packageio.service.TestConfigurationPackageService testCatalog;

    public AuditEventQueryServiceImpl(AuditEventDAO dao, UserPermissionService permissions,
            AdministrationAuthorizationService administration) {
        this.dao = dao;
        this.permissions = permissions;
        this.administration = administration;
    }

    @Override
    public AuditEventPage search(AuditEventFilter filter, String userId) {
        authorize(userId);
        return dao.search(filter);
    }

    @Override
    public java.util.List<java.util.Map<String, String>> tests(String userId) {
        authorize(userId);
        return testCatalog.tests();
    }

    private void authorize(String userId) {
        // Global history includes restricted fields and previous permissions.
        if (userId == null || (!permissions.hasPermission(userId, SystemPermission.GLOBAL_ADMIN)
                && !administration.hasPermission(userId, "read"))) {
            throw new AccessDeniedException("Global audit access requires administration permission");
        }
    }
}
