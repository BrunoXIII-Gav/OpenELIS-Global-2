package org.openelisglobal.common.service;

import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.common.dao.DatabaseCleanDAO;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DefaultConfigurationProperties;
import org.openelisglobal.history.service.HistoryService;
import org.openelisglobal.security.service.UserPermissionService;
import org.springframework.security.access.AccessDeniedException;

public class DatabaseCleanServiceTest {
    private DatabaseCleanDAO cleanup;
    private UserPermissionService permissions;
    private DefaultConfigurationProperties configuration;
    private DatabaseCleanService service;

    @Before
    public void setup() {
        cleanup = mock(DatabaseCleanDAO.class);
        permissions = mock(UserPermissionService.class);
        configuration = mock(DefaultConfigurationProperties.class);
        service = new DatabaseCleanServiceImpl(cleanup, permissions, configuration, mock(HistoryService.class),
                mock(AuditEventDAO.class));
    }

    @Test
    public void productionRefusesCleanupEvenForAdministrator() {
        when(permissions.hasPermission("1", SystemPermission.GLOBAL_ADMIN)).thenReturn(true);
        when(configuration.getPropertyValueLowerCase(Property.TrainingInstallation)).thenReturn("false");
        denied("1");
    }

    @Test
    public void trainingRefusesCleanupForOrdinaryUser() {
        when(configuration.getPropertyValueLowerCase(Property.TrainingInstallation)).thenReturn("true");
        denied("2");
    }

    @Test
    public void unauthenticatedCleanupIsRefused() {
        denied(null);
    }

    @Test
    public void resetsOnlyConfiguredLaboratoryPrefixAfterClinicalDeletion() {
        when(permissions.hasPermission("1", SystemPermission.GLOBAL_ADMIN)).thenReturn(true);
        when(configuration.getPropertyValueLowerCase(Property.TrainingInstallation)).thenReturn("true");
        when(configuration.getPropertyValueUpperCase(Property.AccessionFormat)).thenReturn("PREFIXNUM");
        when(configuration.getPropertyValueUpperCase(Property.ACCESSION_NUMBER_PREFIX)).thenReturn("LNG");
        service.cleanDatabase("1");
        var order = inOrder(cleanup);
        order.verify(cleanup).archiveOperationalAuditEvents(anyString(), eq("1"));
        order.verify(cleanup).deleteOperationalData();
        order.verify(cleanup).resetTrainingCodes("LNG");
    }

    @Test
    public void otherAccessionFormatsDoNotResetUnrelatedLaboratoryCounters() {
        when(permissions.hasPermission("1", SystemPermission.GLOBAL_ADMIN)).thenReturn(true);
        when(configuration.getPropertyValueLowerCase(Property.TrainingInstallation)).thenReturn("true");
        when(configuration.getPropertyValueUpperCase(Property.AccessionFormat)).thenReturn("ALPHANUM");
        service.cleanDatabase("1");
        verify(cleanup).resetTrainingCodes(null);
    }

    private void denied(String id) {
        try {
            service.cleanDatabase(id);
            org.junit.Assert.fail("Cleanup must be refused");
        } catch (AccessDeniedException expected) {
            verifyZeroInteractions(cleanup);
        }
    }
}
