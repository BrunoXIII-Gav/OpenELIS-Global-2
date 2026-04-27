package org.openelisglobal.security.service;

import jakarta.servlet.http.HttpServletRequest;

public interface ModuleAccessService {

    ModuleAccessResult canAccess(String targetUrl, HttpServletRequest request);
}
