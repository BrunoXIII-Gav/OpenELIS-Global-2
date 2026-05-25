package org.openelisglobal.testdependency.daoimpl;

import java.util.Collections;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.testdependency.dao.TestParentChildDependencyDAO;
import org.openelisglobal.testdependency.valueholder.TestParentChildDependency;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TestParentChildDependencyDAOImpl extends BaseDAOImpl<TestParentChildDependency, String>
        implements TestParentChildDependencyDAO {

    public TestParentChildDependencyDAOImpl() {
        super(TestParentChildDependency.class);
    }

    @Override
    @Transactional(readOnly = true)
    public TestParentChildDependency getActiveByChildTestId(String childTestId) {
        if (childTestId == null) {
            return null;
        }

        try {
            String hql = "FROM TestParentChildDependency d "
                    + "WHERE d.childTest.id = :childTestId AND d.active = true";
            Query<TestParentChildDependency> query = entityManager.unwrap(Session.class).createQuery(hql,
                    TestParentChildDependency.class);
            query.setParameter("childTestId", Integer.parseInt(childTestId));
            List<TestParentChildDependency> matches = query.list();
            return matches.isEmpty() ? null : matches.get(0);
        } catch (RuntimeException e) {
            LogEvent.logError(e);
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TestParentChildDependency getByChildTestId(String childTestId) {
        if (childTestId == null) {
            return null;
        }

        try {
            String hql = "FROM TestParentChildDependency d WHERE d.childTest.id = :childTestId";
            Query<TestParentChildDependency> query = entityManager.unwrap(Session.class).createQuery(hql,
                    TestParentChildDependency.class);
            query.setParameter("childTestId", Integer.parseInt(childTestId));
            List<TestParentChildDependency> matches = query.list();
            return matches.isEmpty() ? null : matches.get(0);
        } catch (RuntimeException e) {
            LogEvent.logError(e);
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestParentChildDependency> getByParentTestId(String parentTestId) {
        if (parentTestId == null) {
            return Collections.emptyList();
        }

        try {
            String hql = "FROM TestParentChildDependency d "
                    + "WHERE d.parentTest.id = :parentTestId ORDER BY d.displayOrder, d.id";
            Query<TestParentChildDependency> query = entityManager.unwrap(Session.class).createQuery(hql,
                    TestParentChildDependency.class);
            query.setParameter("parentTestId", Integer.parseInt(parentTestId));
            return query.list();
        } catch (RuntimeException e) {
            LogEvent.logError(e);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestParentChildDependency> getAllActive() {
        try {
            String hql = "FROM TestParentChildDependency d WHERE d.active = true ORDER BY d.parentTest.id, d.displayOrder, d.id";
            Query<TestParentChildDependency> query = entityManager.unwrap(Session.class).createQuery(hql,
                    TestParentChildDependency.class);
            return query.list();
        } catch (RuntimeException e) {
            LogEvent.logError(e);
            return Collections.emptyList();
        }
    }
}
