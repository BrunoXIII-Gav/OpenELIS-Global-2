package org.openelisglobal.administration.service;

import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administration settings are global and cannot safely be partitioned by laboratory unit. */
@Service
@Transactional(readOnly = true)
public class AdministrationAuthorizationServiceImpl implements AdministrationAuthorizationService {

    private static final String ADMINISTRATION_MODULE = "administration";

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @Override
    public boolean hasPermission(String userId, String actionKey) {
        AuthorizationDecision decision = moduleAuthorizationService.getAuthorization(userId, ADMINISTRATION_MODULE,
                actionKey);
        return decision.allowed()
                && (decision.source() != AuthorizationSource.MODULE_PERMISSION || decision.allLabUnits());
    }
}
