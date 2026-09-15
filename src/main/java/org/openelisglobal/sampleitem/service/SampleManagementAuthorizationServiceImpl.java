package org.openelisglobal.sampleitem.service;

import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SampleManagementAuthorizationServiceImpl implements SampleManagementAuthorizationService {

    private static final String SAMPLE_MANAGEMENT_MODULE = "sample-management";

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @Override
    public SampleManagementAccess getAccess(String userId) {
        return new SampleManagementAccess(hasPermission(userId, "read"), hasPermission(userId, "receive"),
                hasPermission(userId, "update"));
    }

    private boolean hasPermission(String userId, String actionKey) {
        return moduleAuthorizationService.getAuthorization(userId, SAMPLE_MANAGEMENT_MODULE, actionKey).allowed();
    }
}
