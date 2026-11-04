package org.openelisglobal.common.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.login.service.LoginUserService;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.security.service.AuthorizationCatalogService;
import org.openelisglobal.security.service.UserPermissionService;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.test.service.TestService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserFieldOptionResolverTest {
    @Mock
    private SystemUserService systemUserService;
    @Mock
    private PersonService personService;
    @Mock
    private ProviderService providerService;
    @Mock
    private LoginUserService loginUserService;
    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;
    @Mock
    private UserPermissionService userPermissionService;
    @Mock
    private TestService testService;
    @InjectMocks
    private UserFieldOptionResolver resolver;

    @Before
    public void setup() {
        resolver.setAuthorizationCatalogService(new AuthorizationCatalogService());
        SystemUser user = new SystemUser();
        user.setId("17");
        user.setIsActive("Y");
        user.setFirstName("Ana");
        user.setLastName("Perez");
        user.setLoginName("ana");
        when(systemUserService.getAllSystemUsers()).thenReturn(List.of(user));
        LoginUser login = new LoginUser();
        login.setLoginName("ana");
        when(loginUserService.getUserProfile("ana")).thenReturn(login);
    }

    @Test public void candidatesUseTaskPermissionWithoutProfessionalProfile() {
        when(moduleAuthorizationService.getAuthorization("17", "results", "enter"))
            .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        assertEquals("17", resolver.resolveUserOptions("USER", null, "results", "9").get(0).optionKey());
        assertEquals("Ana Perez", resolver.resolveUserOptions("USER", null, "results", "9").get(0).label());
    }

    @Test public void readOnlyUserIsNotAnAnalyst() {
        when(moduleAuthorizationService.getAuthorization("17", "results", "enter"))
            .thenReturn(new AuthorizationDecision(false, AuthorizationSource.MODULE_PERMISSION, false, Set.of()));
        assertTrue(resolver.resolveUserOptions("USER", null, "results", "9").isEmpty());
    }

    @Test public void testScopeIsCheckedForTheCandidate() {
        when(moduleAuthorizationService.getAuthorization("17", "results", "enter"))
            .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of("2")));
        org.openelisglobal.test.valueholder.Test allowed = new org.openelisglobal.test.valueholder.Test();
        allowed.setId("9"); when(testService.getTestsByTestSectionIds(List.of(2))).thenReturn(List.of(allowed));
        assertEquals(1, resolver.resolveUserOptions("USER", null, "results", "9").size());
        assertTrue(resolver.resolveUserOptions("USER", null, "results", "10").isEmpty());
    }

    @Test
    public void lockedAccountsCannotBeAssigned() {
        loginUserService.getUserProfile("ana").setAccountLocked("Y");
        assertTrue(resolver.resolveUserOptions("USER", null, "results", "9").isEmpty());
    }

    @Test
    public void newInvalidAssignmentIsRejectedButUnchangedHistoryIsPreserved() {
        resolver.validateAssignment("USER", null, "results", "9", "17", "17");
        try {
            resolver.validateAssignment("USER", null, "results", "9", "17", null);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("eligible"));
        }
    }

    @Test
    public void disabledAccountsAreExcludedEvenWithTaskPermission() {
        loginUserService.getUserProfile("ana").setAccountDisabled("Y");
        assertTrue(resolver.resolveUserOptions("USER", null, "results", "9").isEmpty());
    }

    @Test
    public void invalidConfigurationCannotBePersisted() {
        assertThrows(IllegalArgumentException.class, () -> resolver.validateConfiguration("USER",
                "{\"userPermissionModule\":\"results\",\"userPermissionActions\":[\"read\"]}", "results"));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.validateConfiguration("USER", "not json", "results"));
    }

    @Test
    public void legacyUserFieldAliasCannotBypassTaskValidation() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.validateConfiguration("SYSTEM_USER_BIOLOGIST_SELECT",
                        "{\"userPermissionModule\":\"results\",\"userPermissionActions\":[\"read\"]}", "results"));
    }

    @Test
    public void fieldRestrictionsExcludeAnOtherwiseEligibleCollector() {
        when(moduleAuthorizationService.getAuthorization("17", "sample-management", "update"))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        when(moduleAuthorizationService.getRestrictedFieldGroupKeys("17", "sample-management")).thenReturn(Set.of("collection"));
        assertTrue(resolver.resolveCollectorOptions(null).isEmpty());
    }

    @Test
    public void readerPermissionCannotBeUsedAsAnOperationalSelector() {
        assertTrue(resolver
                .resolveUserOptions("USER",
                        "{\"userPermissionModule\":\"results\",\"userPermissionActions\":[\"read\"]}", "results", null)
                .isEmpty());
    }

    @Test
    public void patientUserFieldWithoutAGroupKeepsEligibleCandidates() {
        when(moduleAuthorizationService.getRestrictedFieldGroupKeys("17", "patients")).thenReturn(Set.of());
        when(moduleAuthorizationService.getRestrictedFieldTagKeys("17", "patients")).thenReturn(Set.of());
        List<UserFieldOptionResolver.ResolvedUserOption> candidates = List.of(
                new UserFieldOptionResolver.ResolvedUserOption("17", "Ana Perez"));
        assertEquals(candidates,
                resolver.filterForFieldAccess(candidates, null, "patients", null, "patient-additional-field-1"));
    }

    @Test
    public void patientUserFieldWithoutAGroupStillHonorsItsTagRestriction() {
        when(moduleAuthorizationService.getRestrictedFieldGroupKeys("17", "patients")).thenReturn(Set.of("contact"));
        when(moduleAuthorizationService.getRestrictedFieldTagKeys("17", "patients"))
                .thenReturn(Set.of("patient-additional-field-1"));
        List<UserFieldOptionResolver.ResolvedUserOption> candidates = List.of(
                new UserFieldOptionResolver.ResolvedUserOption("17", "Ana Perez"));
        assertTrue(resolver.filterForFieldAccess(candidates, null, "patients", null, "patient-additional-field-1")
                .isEmpty());
    }

    @Test
    public void fieldWithoutATagKeepsCandidatesWhenItsGroupIsAllowed() {
        when(moduleAuthorizationService.getRestrictedFieldGroupKeys("17", "sample-management"))
                .thenReturn(Set.of("reception"));
        when(moduleAuthorizationService.getRestrictedFieldTagKeys("17", "sample-management")).thenReturn(Set.of());
        List<UserFieldOptionResolver.ResolvedUserOption> candidates = List.of(
                new UserFieldOptionResolver.ResolvedUserOption("17", "Ana Perez"));
        assertEquals(candidates,
                resolver.filterForFieldAccess(candidates, null, "sample-management", "collection", null));
    }

    @Test
    public void legacyProfileMetadataDoesNotGrantAccess() {
        assertTrue(resolver.resolveUserOptions("USER", "{\"userProfileCodes\":[\"BIOLOGIST\"]}", "results", null)
                .isEmpty());
    }

    @Test
    public void globalAdministratorIsExcludedFromEveryUserModuleDespiteOperationalGrants() {
        when(userPermissionService.hasPermission("17", SystemPermission.GLOBAL_ADMIN)).thenReturn(true);
        when(moduleAuthorizationService.getAuthorization(eq("17"), anyString(), anyString()))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        for (String module : List.of("orders", "patients", "sample-management", "results", "validation", "storage",
                "administration")) {
            assertTrue(module, resolver.resolveUserOptions("USER", null, module, null).isEmpty());
        }
        assertTrue(resolver.resolveCollectorOptions(null).isEmpty());
        assertTrue(resolver.resolveOrderUserOptions(null, null).isEmpty());
        assertTrue(resolver.resolveUserOptions("SYSTEM_USER_BIOLOGIST_SELECT", null, "results", null).isEmpty());
    }

    @Test
    public void administratorAccountFlagAlsoExcludesCandidates() {
        loginUserService.getUserProfile("ana").setIsAdmin("Y");
        when(moduleAuthorizationService.getAuthorization(eq("17"), anyString(), anyString()))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        assertTrue(resolver.resolveCollectorOptions(null).isEmpty());
        assertTrue(resolver.resolveUserOptions("USER", null, "results", null).isEmpty());
    }

    @Test
    public void newAdministratorAssignmentIsRejectedAndHistoricalAssignmentRemainsValid() {
        when(userPermissionService.hasPermission("17", SystemPermission.GLOBAL_ADMIN)).thenReturn(true);
        when(moduleAuthorizationService.getAuthorization(eq("17"), anyString(), anyString()))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.validateAssignment("USER", null, "results", null, "17", null));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.validateSampleAssignment(null, "sample-management", null, "17", null));
        resolver.validateAssignment("USER", null, "results", null, "17", "17");
        resolver.validateSampleAssignment(null, "sample-management", null, "17", "17");
    }

    @Test
    public void linkedAdministratorIsExcludedFromRequestersWhileExternalProfessionalRemains() {
        SystemUser admin = systemUserService.getAllSystemUsers().get(0);
        admin.setLinkedProviderPersonId("30");
        when(userPermissionService.hasPermission("17", SystemPermission.GLOBAL_ADMIN)).thenReturn(true);
        when(moduleAuthorizationService.getAuthorization(eq("17"), anyString(), anyString()))
                .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, true, Set.of()));
        org.openelisglobal.person.valueholder.Person internal = new org.openelisglobal.person.valueholder.Person();
        internal.setId("30");
        org.openelisglobal.provider.valueholder.Provider linked = new org.openelisglobal.provider.valueholder.Provider();
        linked.setPerson(internal);
        org.openelisglobal.person.valueholder.Person external = new org.openelisglobal.person.valueholder.Person();
        external.setId("31");
        org.openelisglobal.provider.valueholder.Provider requester = new org.openelisglobal.provider.valueholder.Provider();
        requester.setPerson(external);
        when(providerService.getAllActiveProviders()).thenReturn(List.of(linked, requester));
        assertEquals(1, resolver.resolveOrderProviderOptions().size());
        assertEquals("31", resolver.resolveOrderProviderOptions().get(0).getId());
        assertThrows(IllegalArgumentException.class, () -> resolver.validateOrderProviderAssignment("30", null));
        resolver.validateOrderProviderAssignment("30", "30");
    }
}
