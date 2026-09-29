package org.openelisglobal.storage.service;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;

@RunWith(MockitoJUnitRunner.class)
public class StorageAuthorizationServiceImplTest {

    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;

    @InjectMocks
    private StorageAuthorizationServiceImpl storageAuthorizationService;

    @Test
    public void hasPermission_allowsAnExplicitAllLaboratoryUnitsPermission() {
        when(moduleAuthorizationService.getAuthorization("17", "storage", "update"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));

        assertTrue(storageAuthorizationService.hasPermission("17", "update"));
    }

    @Test
    public void hasPermission_rejectsAnExplicitLimitedScopeUntilStorageCanBeScopedSafely() {
        when(moduleAuthorizationService.getAuthorization("17", "storage", "read"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));

        assertFalse(storageAuthorizationService.hasPermission("17", "read"));
    }
}
