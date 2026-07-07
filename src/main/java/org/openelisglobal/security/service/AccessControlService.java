package org.openelisglobal.security.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.login.dao.UserModuleService;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service("accessControl")
public class AccessControlService {

    @Autowired
    private UserModuleService userModuleService;

    @Autowired
    private UserPermissionService userPermissionService;

    public boolean hasPermission(SystemPermission permission) {
        if (permission == null) {
            return false;
        }

        HttpServletRequest request = getCurrentRequest();
        if (request == null || userModuleService.isSessionExpired(request)) {
            return false;
        }

        if (userModuleService.isUserAdmin(request)) {
            return true;
        }

        UserSessionData userSessionData = getUserSessionData(request);
        if (userSessionData == null) {
            return false;
        }

        return userPermissionService.hasPermission(Integer.toString(userSessionData.getSystemUserId()), permission);
    }

    public boolean hasAnyPermission(SystemPermission... permissions) {
        return Arrays.stream(permissions).anyMatch(this::hasPermission);
    }

    private HttpServletRequest getCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }
        return servletRequestAttributes.getRequest();
    }

    private UserSessionData getUserSessionData(HttpServletRequest request) {
        UserSessionData userSessionData = (UserSessionData) request.getSession()
                .getAttribute(IActionConstants.USER_SESSION_DATA);
        if (userSessionData == null) {
            userSessionData = (UserSessionData) request.getAttribute(IActionConstants.USER_SESSION_DATA);
        }
        return userSessionData;
    }
}
