package org.openelisglobal.storage.service;

import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storage locations are not linked to laboratory units, so restricted explicit
 * scopes cannot be evaluated safely and must not expose the storage hierarchy.
 */
@Service
@Transactional(readOnly = true)
public class StorageAuthorizationServiceImpl implements StorageAuthorizationService {

    private static final String STORAGE_MODULE = "storage";

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @Override
    public boolean hasPermission(String userId, String actionKey) {
        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId, STORAGE_MODULE,
                actionKey);
        return decision.allowed()
                && (decision.source() != AuthorizationSource.MODULE_PERMISSION || decision.allLabUnits());
    }
}
