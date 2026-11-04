package org.openelisglobal.testconfiguration;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.rest.DisplayListController;
import org.openelisglobal.common.util.DefaultConfigurationProperties;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.role.valueholder.Role;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.systemuser.service.UserService;
import org.openelisglobal.test.service.TestSectionService;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

public class TestSectionFilterControllerTest {
    private Object previousFactory;

    @Before
    public void configureStaticDependencies() {
        previousFactory = ReflectionTestUtils.getField(SpringContext.class, "factory");
        var factory = mock(AutowireCapableBeanFactory.class);
        when(factory.getBean(DefaultConfigurationProperties.class))
                .thenReturn(mock(DefaultConfigurationProperties.class));
        ReflectionTestUtils.setField(SpringContext.class, "factory", factory);
    }

    @After
    public void restoreStaticDependencies() {
        ReflectionTestUtils.setField(SpringContext.class, "factory", previousFactory);
    }

    @Test
    public void resultsAndValidationFilterOnlyAfterResolvingAuthorizedUnits() {
        for (String roleName : List.of(Constants.ROLE_RESULTS, Constants.ROLE_VALIDATION)) {
            var controller = new DisplayListController();
            var users = mock(UserService.class);
            var roles = mock(RoleService.class);
            var units = mock(TestSectionService.class);
            var authorization = mock(ModuleAuthorizationService.class);
            ReflectionTestUtils.setField(controller, "userService", users);
            ReflectionTestUtils.setField(controller, "roleService", roles);
            ReflectionTestUtils.setField(controller, "testSectionService", units);
            ReflectionTestUtils.setField(controller, "moduleAuthorizationService", authorization);
            var request = new MockHttpServletRequest();
            var session = new UserSessionData();
            session.setSytemUserId(117);
            request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, session);
            var role = new Role();
            role.setId("7");
            when(roles.getRoleByName(anyString())).thenReturn(role);
            when(authorization.getAuthorization("117", "results", "read")).thenReturn(
                    new AuthorizationDecision(true, AuthorizationSource.LEGACY_PERMISSION, false, Set.of()));
            var created = new IdValuePair("27", "My unit");
            var allowed = List.of(new IdValuePair("1", "System unit"), created);
            when(users.getUserTestSections("117", "7")).thenReturn(allowed);
            when(units.filterUserCreatedTestSections(allowed)).thenReturn(List.of(created));
            assertEquals(List.of(created), controller.createUserTestSectionsList(request, roleName, false));
            verify(units).filterUserCreatedTestSections(allowed);
            when(users.getUserTestSections("117", null)).thenReturn(allowed);
            assertEquals(allowed, controller.createUserTestSectionsList(request, "ALL", false));
            verifyNoMoreInteractions(units);
        }
    }

    @Test
    public void dashboardAllKeepsAllUserCreatedUnitsForAdminWithoutRequiringAssignments() {
        for (boolean admin : List.of(true, false)) {
            var controller = new DisplayListController();
            var users = mock(UserService.class);
            var units = mock(TestSectionService.class);
            ReflectionTestUtils.setField(controller, "userService", users);
            ReflectionTestUtils.setField(controller, "testSectionService", units);
            var request = new MockHttpServletRequest();
            var session = new UserSessionData();
            session.setSytemUserId(117);
            session.setAdmin(admin);
            request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, session);
            var created = new IdValuePair("27", "My unit");
            var otherCreated = new IdValuePair("28", "Another user's unit");
            var system = new IdValuePair("1", "System unit");
            var authorized = admin ? List.of(system, created, otherCreated) : List.of(system, created);
            var expected = admin ? List.of(created, otherCreated) : List.of(created);
            when(users.getUserTestSections("117", null)).thenReturn(authorized);
            when(units.filterUserCreatedTestSections(authorized)).thenReturn(expected);
            assertEquals(expected, controller.createUserTestSectionsList(request, "ALL", true));
            verify(users).getUserTestSections("117", null);
            verify(units).filterUserCreatedTestSections(authorized);
        }
    }
}
