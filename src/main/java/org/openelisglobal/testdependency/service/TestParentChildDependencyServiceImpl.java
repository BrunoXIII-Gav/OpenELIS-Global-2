package org.openelisglobal.testdependency.service;

import java.util.List;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.openelisglobal.testdependency.dao.TestParentChildDependencyDAO;
import org.openelisglobal.testdependency.valueholder.TestParentChildDependency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestParentChildDependencyServiceImpl
        extends AuditableBaseObjectServiceImpl<TestParentChildDependency, String>
        implements TestParentChildDependencyService {

    @Autowired
    private TestParentChildDependencyDAO baseObjectDAO;

    public TestParentChildDependencyServiceImpl() {
        super(TestParentChildDependency.class);
        this.auditTrailLog = true;
    }

    @Override
    protected TestParentChildDependencyDAO getBaseObjectDAO() {
        return baseObjectDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public TestParentChildDependency getActiveByChildTestId(String childTestId) {
        return baseObjectDAO.getActiveByChildTestId(childTestId);
    }

    @Override
    @Transactional(readOnly = true)
    public TestParentChildDependency getByChildTestId(String childTestId) {
        return baseObjectDAO.getByChildTestId(childTestId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestParentChildDependency> getByParentTestId(String parentTestId) {
        return baseObjectDAO.getByParentTestId(parentTestId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestParentChildDependency> getAllActive() {
        return baseObjectDAO.getAllActive();
    }
}
