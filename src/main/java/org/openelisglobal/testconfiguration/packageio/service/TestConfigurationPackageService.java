package org.openelisglobal.testconfiguration.packageio.service;

import java.util.List;
import java.util.Map;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage.*;

public interface TestConfigurationPackageService {
    List<Map<String, String>> tests();

    TestConfigurationPackage exportTests(List<String> ids);

    TestConfigurationPackage prepareImport(TestConfigurationPackage bundle, String testKey);

    Preview preview(ImportRequest request);

    ImportResult importPackage(ImportRequest request, String actorId);

    List<Map<String, Object>> history();
}
