package org.openelisglobal.sample.service;

import java.util.List;

/** Applies Orders module permissions and laboratory-unit scope to order tests. */
public interface OrderAuthorizationService {

    boolean hasPermission(String userId, String actionKey);

    boolean canAccessAllTests(String userId, List<String> testIds, String actionKey);

    boolean canAccessSampleXml(String userId, String sampleXml, String actionKey);
}
