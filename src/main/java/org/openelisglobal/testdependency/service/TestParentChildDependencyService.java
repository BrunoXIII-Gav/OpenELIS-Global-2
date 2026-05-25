package org.openelisglobal.testdependency.service;

import java.util.List;
import org.openelisglobal.common.service.BaseObjectService;
import org.openelisglobal.testdependency.valueholder.TestParentChildDependency;

public interface TestParentChildDependencyService extends BaseObjectService<TestParentChildDependency, String> {

    TestParentChildDependency getActiveByChildTestId(String childTestId);

    TestParentChildDependency getByChildTestId(String childTestId);

    List<TestParentChildDependency> getByParentTestId(String parentTestId);

    List<TestParentChildDependency> getAllActive();
}
