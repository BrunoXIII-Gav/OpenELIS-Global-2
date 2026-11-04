package org.openelisglobal.common.service;

public interface DatabaseCleanService {

    void requireCleanupAccess(String userId);

    void cleanDatabase(String userId);
}
