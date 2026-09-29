package org.openelisglobal.authorization.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.authorization.valueholder.RoleModulePermissionLabUnitScope;
import org.openelisglobal.authorization.valueholder.RoleFieldRestriction;
import org.openelisglobal.authorization.valueholder.RoleFieldTagRestriction;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.security.service.AuthorizationCatalogService;
import org.openelisglobal.security.service.UserPermissionService;
import org.openelisglobal.userrole.service.UserRoleService;

@RunWith(MockitoJUnitRunner.class)
public class ModuleAuthorizationServiceImplTest {

    @Mock
    private AuthorizationCatalogService authorizationCatalogService;

    @Mock
    private RoleModulePermissionService roleModulePermissionService;

    @Mock
    private RoleFieldRestrictionService roleFieldRestrictionService;

    @Mock
    private RoleFieldTagRestrictionService roleFieldTagRestrictionService;

    @Mock
    private UserPermissionService userPermissionService;

    @Mock
    private UserRoleService userRoleService;

    @InjectMocks
    private ModuleAuthorizationServiceImpl moduleAuthorizationService;

    @Before
    public void setUp() {
        when(authorizationCatalogService.isKnownPermission(anyString(), anyString())).thenReturn(true);
        when(userRoleService.getRoleIdsForUser("17")).thenReturn(List.of("12"));
    }

    @Test
    public void getAuthorization_usesExplicitPermissionAndLabUnitScope() {
        RoleModulePermission permission = permission("receive", false);
        RoleModulePermissionLabUnitScope scope = new RoleModulePermissionLabUnitScope();
        scope.setLabUnitId("neurogenetics");
        permission.addLabUnitScope(scope);
        when(roleModulePermissionService.getByRoleIdsAndModuleKey(List.of(12), "sample-management"))
                .thenReturn(List.of(permission));

        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization("17", "sample-management",
                "receive");

        assertTrue(decision.allowed());
        assertEquals(AuthorizationSource.MODULE_PERMISSION, decision.source());
        assertTrue(decision.appliesToLabUnit("neurogenetics"));
        assertFalse(decision.appliesToLabUnit("chemistry"));
        verify(userPermissionService, never()).hasPermission("17", SystemPermission.SAMPLE_MANAGEMENT);
    }

    @Test
    public void getAuthorization_doesNotFallBackWhenModuleHasExplicitPermissions() {
        when(roleModulePermissionService.getByRoleIdsAndModuleKey(List.of(12), "sample-management"))
                .thenReturn(List.of(permission("update", true)));

        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization("17", "sample-management",
                "receive");

        assertFalse(decision.allowed());
        assertEquals(AuthorizationSource.MODULE_PERMISSION, decision.source());
        verify(userPermissionService, never()).hasPermission("17", SystemPermission.SAMPLE_MANAGEMENT);
    }

    @Test
    public void getAuthorization_fallsBackToCurrentLegacyPermissionWithoutModulePermissions() {
        when(roleModulePermissionService.getByRoleIdsAndModuleKey(List.of(12), "sample-management"))
                .thenReturn(List.of());
        when(userPermissionService.hasPermission("17", SystemPermission.SAMPLE_MANAGEMENT)).thenReturn(true);

        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization("17", "sample-management",
                "receive");

        assertTrue(decision.allowed());
        assertEquals(AuthorizationSource.LEGACY_PERMISSION, decision.source());
        verify(userPermissionService).hasPermission("17", SystemPermission.SAMPLE_MANAGEMENT);
    }

    @Test
    public void getRestrictedFieldGroupKeys_returnsRestrictionsFromAssignedRoles() {
        RoleFieldRestriction restriction = new RoleFieldRestriction();
        restriction.setRoleId(12);
        restriction.setModuleKey("sample-management");
        restriction.setFieldGroupKey("patient-identity");
        when(userPermissionService.hasPermission("17", SystemPermission.GLOBAL_ADMIN)).thenReturn(false);
        when(roleFieldRestrictionService.getByRoleIdsAndModuleKey(List.of(12), "sample-management"))
                .thenReturn(List.of(restriction));

        assertTrue(moduleAuthorizationService.getRestrictedFieldGroupKeys("17", "sample-management")
                .contains("patient-identity"));
    }

    @Test
    public void getRestrictedFieldTagKeys_returnsRestrictionsFromAssignedRoles() {
        RoleFieldTagRestriction restriction = new RoleFieldTagRestriction();
        restriction.setRoleId(12);
        restriction.setModuleKey("sample-management");
        restriction.setFieldTagKey("quantity");
        when(userPermissionService.hasPermission("17", SystemPermission.GLOBAL_ADMIN)).thenReturn(false);
        when(roleFieldTagRestrictionService.getByRoleIdsAndModuleKey(List.of(12), "sample-management"))
                .thenReturn(List.of(restriction));

        assertTrue(moduleAuthorizationService.getRestrictedFieldTagKeys("17", "sample-management")
                .contains("quantity"));
    }

    private RoleModulePermission permission(String actionKey, boolean allLabUnits) {
        RoleModulePermission permission = new RoleModulePermission();
        permission.setRoleId(12);
        permission.setModuleKey("sample-management");
        permission.setActionKey(actionKey);
        permission.setAllLabUnits(allLabUnits);
        return permission;
    }
}
