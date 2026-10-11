package org.openelisglobal.security.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Set;
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
import org.springframework.mock.web.MockHttpServletRequest;

@RunWith(MockitoJUnitRunner.class)
public class StorageModuleAccessTest {
    @Mock
    private ModuleAuthorizationService moduleAuthorizationService;
    @Mock
    private UserModuleService userModuleService;
    @InjectMocks
    private ModuleAccessServiceImpl service;

    private MockHttpServletRequest request(boolean allowed, Set<String> units) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UserSessionData user = new UserSessionData();
        user.setSytemUserId(17);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        when(moduleAuthorizationService.getAuthorization("17", "storage", "read"))
                .thenReturn(new AuthorizationDecision(allowed, AuthorizationSource.MODULE_PERMISSION, false, units));
        return request;
    }

    @Test
    public void scopedRoleCanEnterEveryStorageTab() {
        var request = request(true, Set.of("203"));
        for (String tab : new String[] { "", "/samples", "/rooms", "/devices", "/shelves", "/racks", "/boxes" }) {
            assertTrue(service.canAccess("/Storage" + tab, request).isAllowed());
        }
    }

    @Test
    public void selectingAUnitDoesNotReplaceReadPermission() {
        assertFalse(service.canAccess("/Storage/samples", request(false, Set.of("203"))).isAllowed());
    }

    @Test
    public void emptyScopeIsDenied() {
        assertFalse(service.canAccess("/Storage/samples", request(true, Set.of())).isAllowed());
    }

    @Test
    public void freezerMonitoringDoesNotAcquireUnscopedAccess() {
        assertFalse(service.canAccess("/FreezerMonitoring", request(true, Set.of("203"))).isAllowed());
    }
}
