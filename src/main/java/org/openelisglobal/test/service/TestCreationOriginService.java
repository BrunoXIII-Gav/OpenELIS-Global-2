package org.openelisglobal.test.service;

import java.util.List;
import java.util.Map;

public interface TestCreationOriginService {
    List<Map<String, String>> unclassifiedTests();

    int classify(List<String> ids, boolean userCreated, String actorId);

    void recognizeConfigurationTest(String id);
}
