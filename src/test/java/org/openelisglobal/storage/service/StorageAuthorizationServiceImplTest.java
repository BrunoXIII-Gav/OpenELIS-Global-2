package org.openelisglobal.storage.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.dao.UserModuleService;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.storage.dao.SampleStorageAssignmentDAO;
import org.openelisglobal.storage.dao.StorageSampleScopeDAO;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@RunWith(MockitoJUnitRunner.class)
public class StorageAuthorizationServiceImplTest {
    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;
    @Mock
    private UserModuleService userModuleService;
    @Mock
    private StorageSampleScopeDAO storageSampleScopeDAO;
    @Mock
    private SampleStorageAssignmentDAO sampleStorageAssignmentDAO;
    @InjectMocks
    private StorageAuthorizationServiceImpl service;
    private MockHttpServletRequest request;

    @Before
    public void request() {
        request = new MockHttpServletRequest();
        UserSessionData user = new UserSessionData();
        user.setSytemUserId(17);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @After
    public void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void grant(String action, String... units) {
        when(moduleAuthorizationService.getAuthorization("17", "storage", action))
            .thenReturn(new AuthorizationDecision(true, AuthorizationSource.MODULE_PERMISSION, false, Set.of(units)));
    }

    @Test
    public void scopedRoleCanEnterStorage() {
        grant("read", "7");
        assertTrue(service.hasPermission("17", "read"));
    }

    @Test
    public void emptyUnitScopeCannotEnterOrReadSpecimens() {
        grant("read");
        assertFalse(service.hasPermission("17", "read"));
        assertEquals(Set.of(), service.getAccessibleSampleItemIds("17", "read"));
        verifyZeroInteractions(storageSampleScopeDAO);
    }

    @Test public void deniedActionCannotAccessSpecimens() {
        when(moduleAuthorizationService.getAuthorization("17", "storage", "update"))
            .thenReturn(new AuthorizationDecision(false, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));
        assertEquals(Set.of(), service.getAccessibleSampleItemIds("17", "update"));
        verifyZeroInteractions(storageSampleScopeDAO);
    }

    @Test
    public void unitUnionAndNestedAliquotsUseOnlyTheirOwnOrInheritedScope() {
        grant("read", "7", "8");
        when(storageSampleScopeDAO.findSampleItemIdsByLabUnits(Set.of("7", "8"))).thenReturn(Set.of("43", "44"));
        // Deliberately reverse parent order; unrelated and cyclic specimens remain
        // inaccessible.
        when(storageSampleScopeDAO.findUnanalysedSampleItemParents())
                .thenReturn(List.of(new Object[] { "46", "45" }, new Object[] { "45", "43" },
                        new Object[] { "50", "51" }, new Object[] { "51", "50" }, new Object[] { "60", "99" }));
        assertEquals(Set.of("43", "44", "45", "46"), service.getAccessibleSampleItemIds("17", "read"));
    }

    @Test
    public void eachOperationUsesItsOwnUnits() {
        grant("read", "7");
        grant("update", "8");
        when(storageSampleScopeDAO.findSampleItemIdsByLabUnits(Set.of("7"))).thenReturn(Set.of("43"));
        when(storageSampleScopeDAO.findSampleItemIdsByLabUnits(Set.of("8"))).thenReturn(Set.of("44"));
        service.requireSampleAccess("43", "read");
        assertThrows(AccessDeniedException.class, () -> service.requireSampleAccess("43", "update"));
        service.requireSampleAccess("44", "update");
    }

    @Test public void administratorRetainsGlobalAccessWithoutRequiringAnOperationalRole() {
        when(userModuleService.isUserAdmin(request)).thenReturn(true);
        assertNull(service.getAccessibleSampleItemIdsForCurrentUser("manage"));
        service.requireSampleAccess("99", "manage");
        service.requireLocationAccess("room", 1, "manage");
        verifyZeroInteractions(moduleAuthorizationService, storageSampleScopeDAO);
    }

    @Test public void legacyGlobalPermissionRetainsItsScope() {
        when(moduleAuthorizationService.getAuthorization("17", "storage", "read"))
            .thenReturn(new AuthorizationDecision(true, AuthorizationSource.LEGACY_PERMISSION, true, Set.of()));
        assertTrue(service.hasPermission("17", "read"));
        assertNull(service.getAccessibleSampleItemIds("17", "read"));
    }

    @Test
    public void occupiedForeignCoordinatesStayBlockedWithoutIdentifiers() {
        grant("read", "7");
        when(storageSampleScopeDAO.findSampleItemIdsByLabUnits(Set.of("7"))).thenReturn(Set.of("43"));
        when(sampleStorageAssignmentDAO.getOccupiedCoordinatesWithSampleInfo(1))
                .thenReturn(Map.of("A1", Map.of("sampleItemId", "43", "externalId", "OWN", "cugCode", "1.1"), "A2",
                        Map.of("sampleItemId", "99", "externalId", "OTHER", "cugCode", "9.9")));
        Map<String, Map<String, String>> result = service.getOccupiedCoordinatesForCurrentUser(1);
        assertEquals(Set.of("A1", "A2"), result.keySet());
        assertEquals("OWN", result.get("A1").get("externalId"));
        assertTrue(result.get("A2").isEmpty());
    }

    @Test
    public void sharedHierarchyWithForeignSamplesCannotBeChanged() {
        grant("manage", "7");
        when(storageSampleScopeDAO.findSampleItemIdsByLabUnits(Set.of("7"))).thenReturn(Set.of("43"));
        when(storageSampleScopeDAO.findAssignedSampleItemIdsInHierarchy("device", 1)).thenReturn(Set.of("43", "99"));
        assertThrows(AccessDeniedException.class, () -> service.requireLocationAccess("device", 1, "manage"));
    }

    @Test
    public void ownHierarchyAndEmptyLocationsCanBeManaged() {
        grant("manage", "7");
        when(storageSampleScopeDAO.findSampleItemIdsByLabUnits(Set.of("7"))).thenReturn(Set.of("43"));
        when(storageSampleScopeDAO.findAssignedSampleItemIdsInHierarchy("device", 1)).thenReturn(Set.of("43"));
        service.requireLocationAccess("device", 1, "manage");
        service.requireLocationAccess("box", 2, "manage");
    }

    @Test public void expiredOrMissingSessionDoesNotBecomeGlobalAccess() {
        when(userModuleService.isSessionExpired(request)).thenReturn(true);
        assertThrows(AccessDeniedException.class, () -> service.getAccessibleSampleItemIdsForCurrentUser("read"));
        request.getSession().removeAttribute(IActionConstants.USER_SESSION_DATA);
        assertThrows(AccessDeniedException.class, () -> service.getAccessibleSampleItemIdsForCurrentUser("read"));
    }
}
