package org.openelisglobal.testdependency.dao;

import java.util.List;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.testdependency.valueholder.TestParentChildDependency;

public interface TestParentChildDependencyDAO extends BaseDAO<TestParentChildDependency, String> {

    TestParentChildDependency getActiveByChildTestId(String childTestId);

    TestParentChildDependency getByChildTestId(String childTestId);

    List<TestParentChildDependency> getByParentTestId(String parentTestId);

    List<TestParentChildDependency> getAllActive();
}
