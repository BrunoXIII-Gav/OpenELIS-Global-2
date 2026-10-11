package org.openelisglobal.common.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.authorization.service.ModuleAuthorizationServiceImpl;
import org.openelisglobal.authorization.service.RoleFieldRestrictionService;
import org.openelisglobal.authorization.service.RoleFieldTagRestrictionService;
import org.openelisglobal.authorization.service.RoleModulePermissionService;
import org.openelisglobal.authorization.valueholder.RoleModulePermission;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.login.service.LoginUserService;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.security.service.AuthorizationCatalogService;
import org.openelisglobal.security.service.UserPermissionService;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Exercises the real permission resolver and the real USER selector together.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class EmptyRoleUserOptionsTest {
    @Mock
    private RoleModulePermissionService permissions;
    @Mock
    private UserRoleService userRoles;
    @Mock
    private RoleService roles;
    @Mock
    private UserPermissionService legacy;
    @Mock
    private SystemUserService users;
    @Mock
    private LoginUserService logins;
    private UserFieldOptionResolver resolver;
    private ModuleAuthorizationServiceImpl authorization;

    @Before
    public void setup() {
        AuthorizationCatalogService catalog = new AuthorizationCatalogService();
        authorization = new ModuleAuthorizationServiceImpl();
        ReflectionTestUtils.setField(authorization, "authorizationCatalogService", catalog);
        ReflectionTestUtils.setField(authorization, "roleModulePermissionService", permissions);
        ReflectionTestUtils.setField(authorization, "userRoleService", userRoles);
        ReflectionTestUtils.setField(authorization, "roleService", roles);
        ReflectionTestUtils.setField(authorization, "userPermissionService", legacy);
        ReflectionTestUtils.setField(authorization, "roleFieldRestrictionService",
                mock(RoleFieldRestrictionService.class));
        ReflectionTestUtils.setField(authorization, "roleFieldTagRestrictionService",
                mock(RoleFieldTagRestrictionService.class));
        resolver = new UserFieldOptionResolver();
        resolver.setAuthorizationCatalogService(catalog);
        ReflectionTestUtils.setField(resolver, "moduleAuthorizationService", authorization);
        ReflectionTestUtils.setField(resolver, "systemUserService", users);
        ReflectionTestUtils.setField(resolver, "loginUserService", logins);
        ReflectionTestUtils.setField(resolver, "userPermissionService", legacy);
        SystemUser user = new SystemUser();
        user.setId("114");
        user.setIsActive("Y");
        user.setLoginName("bruno");
        user.setFirstName("Bruno");
        user.setLastName("Gavidia");
        when(users.getAllSystemUsers()).thenReturn(List.of(user));
        when(logins.getUserProfile("bruno")).thenReturn(new LoginUser());
        // Reproduce the hidden direct legacy roles shown in the installation.
        when(userRoles.getRoleIdsForUser("114")).thenReturn(List.of("5", "75", "86", "104"));
        Role group = new Role();
        group.setId("84");
        Role view = new Role();
        view.setId("104");
        view.setGroupingParent("84");
        when(roles.getRoleByName(Constants.CUSTOM_ROLES_GROUP)).thenReturn(group);
        when(roles.getRoleById("104")).thenReturn(view);
        when(legacy.hasPermission("114", SystemPermission.SAMPLE_MANAGEMENT)).thenReturn(true);
        when(legacy.hasPermission("114", SystemPermission.RESULTS)).thenReturn(true);
    }

    @Test
    public void emptyRoleAndHiddenLegacyGrantsCannotPopulateCollectorOrUserFields() {
        assertTrue(resolver.resolveCollectorOptions(null).isEmpty());
        assertTrue(resolver.resolveUserOptions("USER", null, "sample-management", null).isEmpty());
        assertTrue(resolver.resolveUserOptions("USER", null, "results", null).isEmpty());
    }

    @Test
    public void emptyRoleCannotBeNewlyAssignedButItsHistoricalIdIsPreserved() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.validateAssignment("USER", null, "sample-management", null, "114", null));
        resolver.validateAssignment("USER", null, "sample-management", null, "114", "114");
    }

    @Test
    public void addingAnExplicitOperationalRoleMakesTheUserEligible() {
        when(userRoles.getRoleIdsForUser("114")).thenReturn(List.of("104", "117"));
        RoleModulePermission grant = new RoleModulePermission();
        grant.setRoleId(117); grant.setModuleKey("sample-management"); grant.setActionKey("update");
        when(permissions.getByRoleIdsAndModuleKey(List.of(104,117), "sample-management")).thenReturn(List.of(grant));
        assertEquals("114", resolver.resolveCollectorOptions(null).get(0).optionKey());
    }

    @Test
    public void emptyCustomAdministratorCannotUseBroadLegacyAliasesToParticipate() {
        SystemUser user = new SystemUser();
        user.setId("115");
        user.setIsActive("Y");
        user.setLoginName("second");
        user.setFirstName("Second");
        user.setLastName("Admin");
        when(users.getAllSystemUsers()).thenReturn(List.of(user));
        when(logins.getUserProfile("second")).thenReturn(new LoginUser());
        when(userRoles.getRoleIdsForUser("115")).thenReturn(List.of("2", "85", "105"));
        Role administrator = new Role();
        administrator.setId("105");
        administrator.setGroupingParent("84");
        when(roles.getRoleById("105")).thenReturn(administrator);
        when(legacy.hasPermission("115", SystemPermission.SAMPLE_MANAGEMENT)).thenReturn(true);
        when(legacy.hasPermission("115", SystemPermission.VALIDATION)).thenReturn(true);
        assertTrue(resolver.resolveCollectorOptions(null).isEmpty());
        assertTrue(resolver.resolveUserOptions("USER", null, "validation", null).isEmpty());
    }

    @Test
    public void nativeAdministratorCanOperateButCannotBeAssignedInFlows() {
        when(userRoles.getRoleIdsForUser("114")).thenReturn(List.of("1"));
        Role nativeAdmin = new Role();
        nativeAdmin.setId("1");
        nativeAdmin.setGroupingParent("69");
        nativeAdmin.setName(Constants.ROLE_GLOBAL_ADMIN);
        when(roles.getRoleById("1")).thenReturn(nativeAdmin);
        when(legacy.hasPermission("114", SystemPermission.GLOBAL_ADMIN)).thenReturn(true);
        when(legacy.hasPermission("114", SystemPermission.ORDER_ADD)).thenReturn(true);
        when(legacy.hasPermission("114", SystemPermission.VALIDATION)).thenReturn(true);
        assertTrue(authorization.getAuthorization("114", "sample-management", "update").allowed());
        assertTrue(authorization.getAuthorization("114", "results", "enter").allowed());
        assertTrue(authorization.getAuthorization("114", "orders", "create").allowed());
        assertTrue(authorization.getAuthorization("114", "validation", "validate").allowed());
        assertTrue(resolver.resolveCollectorOptions(null).isEmpty());
        assertTrue(resolver.resolveUserOptions("USER", null, "results", null).isEmpty());
        assertTrue(resolver.resolveOrderUserOptions(null, null).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> resolver.validateSampleAssignment(null, "sample-management", null, "114", null));
        resolver.validateSampleAssignment(null, "sample-management", null, "114", "114");
    }
}
