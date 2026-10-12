package org.openelisglobal.common.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.systemuser.service.SystemUserService;

@RunWith(MockitoJUnitRunner.class)
public class ProfessionalProfileRoleAuthorizationTest {
    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;
    @Mock
    private SystemUserService systemUserService;
    @InjectMocks
    private ProfessionalProfilePermissionService service;

    @Test public void professionIsNotRequiredForOperationalAuthorization() {
        when(moduleAuthorizationService.hasPermission("12", "orders", "create")).thenReturn(true);
        assertTrue(service.hasOrderPermission("12"));
        verifyZeroInteractions(systemUserService);
    }

    @Test
    public void readOnlyRoleCannotValidate() {
        assertFalse(service.hasValidationPermission("12"));
        verify(moduleAuthorizationService).hasPermission("12", "validation", "validate");
    }

    @Test public void editingRoleMayParticipateWithoutCreatePermission() {
        when(moduleAuthorizationService.hasPermission("12", "sample-management", "update")).thenReturn(true);
        assertTrue(service.hasSampleCollectionPermission("12"));
    }
}
