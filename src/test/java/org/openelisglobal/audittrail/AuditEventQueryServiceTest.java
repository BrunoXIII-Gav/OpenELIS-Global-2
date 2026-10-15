package org.openelisglobal.audittrail;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.openelisglobal.administration.service.AdministrationAuthorizationService;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.audittrail.form.AuditEventFilter;
import org.openelisglobal.audittrail.service.AuditEventQueryServiceImpl;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.security.service.UserPermissionService;
import org.springframework.security.access.AccessDeniedException;

public class AuditEventQueryServiceTest {
    private final AuditEventDAO dao = mock(AuditEventDAO.class);
    private final UserPermissionService users = mock(UserPermissionService.class);
    private final AdministrationAuthorizationService administration = mock(AdministrationAuthorizationService.class);
    private final AuditEventQueryServiceImpl service = new AuditEventQueryServiceImpl(dao, users, administration);
    private final AuditEventFilter filter = new AuditEventFilter(null, null, null, null, null, null, null, null, null,
            0, 30);

    @Test
    public void deniesUnprivilegedGlobalSearchBeforeReadingData() {
        try {
            service.search(filter, "7");
            fail("Expected access denial");
        } catch (AccessDeniedException expected) {
            verifyZeroInteractions(dao);
        }
    }

    @Test
    public void permitsGlobalAdministrator() {
        when(users.hasPermission("7", SystemPermission.GLOBAL_ADMIN)).thenReturn(true);
        service.search(filter, "7");
        verify(dao).search(filter);
    }

    @Test
    public void permitsGlobalAdministrationReadPermission() {
        when(administration.hasPermission("7", "read")).thenReturn(true);
        service.search(filter, "7");
        verify(dao).search(filter);
    }

    @Test
    public void testSelectorUsesTheSameAuditReadPermissionAndExistingCatalog() {
        var catalog = mock(org.openelisglobal.testconfiguration.packageio.service.TestConfigurationPackageService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "testCatalog", catalog);
        try {
            service.tests("7");
            fail("Expected access denial");
        } catch (AccessDeniedException expected) {
            verifyZeroInteractions(catalog);
        }
        when(administration.hasPermission("7", "read")).thenReturn(true);
        var choices = java.util.List.of(java.util.Map.of("id", "9201", "name", "Manual test", "code", "MAN"));
        when(catalog.tests()).thenReturn(choices);
        assertEquals(choices, service.tests("7"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedPages() {
        new AuditEventFilter(null, null, null, null, null, null, null, null, null, 0, 101);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsReversedDates() {
        new AuditEventFilter(null, null, null, null, null, null, null, java.time.Instant.parse("2026-10-15T00:00:00Z"),
                java.time.Instant.parse("2026-10-14T00:00:00Z"), 0, 30);
    }
}
