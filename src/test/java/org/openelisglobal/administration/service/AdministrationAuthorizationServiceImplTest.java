package org.openelisglobal.administration.service;

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
public class AdministrationAuthorizationServiceImplTest {

    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;

    @InjectMocks
    private AdministrationAuthorizationServiceImpl administrationAuthorizationService;

    @Test
    public void hasPermission_allowsGlobalAdministrationPermission() {
        when(moduleAuthorizationService.getAuthorization("17", "administration", "manage"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));

        assertTrue(administrationAuthorizationService.hasPermission("17", "manage"));
    }

    @Test
    public void hasPermission_rejectsLimitedScopeForGlobalAdministrationSettings() {
        when(moduleAuthorizationService.getAuthorization("17", "administration", "read"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));

        assertFalse(administrationAuthorizationService.hasPermission("17", "read"));
    }
}
