package org.openelisglobal.security.controller;

import org.openelisglobal.security.service.AuthorizationCatalogService;
import org.openelisglobal.security.service.AuthorizationCatalogService.AuthorizationCatalog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only catalog for the future module-based custom-role editor.
 *
 * <p>
 * It intentionally has no effect on the authorization decision made for
 * existing requests.
 */
@RestController
@RequestMapping("/rest/authorization")
@PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).GLOBAL_ADMIN)")
public class AuthorizationCatalogRestController {

    @Autowired
    private AuthorizationCatalogService authorizationCatalogService;

    @GetMapping("/catalog")
    public AuthorizationCatalog getCatalog() {
        return authorizationCatalogService.getCatalog();
    }
}
