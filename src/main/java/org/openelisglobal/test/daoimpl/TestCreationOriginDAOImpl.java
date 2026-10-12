package org.openelisglobal.test.daoimpl;

import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.List;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.test.dao.TestCreationOriginDAO;
import org.openelisglobal.test.service.SystemTestCatalog;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.test.valueholder.TestCreationOrigin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class TestCreationOriginDAOImpl extends BaseDAOImpl<Test, String> implements TestCreationOriginDAO {
    @Autowired
    private SystemTestCatalog systemTestCatalog;

    public TestCreationOriginDAOImpl() {
        super(Test.class);
    }

    @Override
    public List<Test> unclassifiedTests() {
        return entityManager.createQuery("from Test t where t.creationOriginVerified = false and t.id not in "
                + "(select o.id from TestCreationOrigin o) order by t.description", Test.class).getResultList();
    }

    @Override
    @Transactional
    public int classify(Collection<String> ids, boolean userCreated, String actorId) {
        int count = 0;
        for (String id : ids.stream().sorted().toList()) {
            Test test = entityManager.find(Test.class, id, LockModeType.PESSIMISTIC_WRITE);
            if (test == null || test.isCreationOriginVerified()
                    || (userCreated && systemTestCatalog.contains(test.getGuid()))
                    || entityManager.find(TestCreationOrigin.class, id) != null)
                continue;
            TestCreationOrigin origin = new TestCreationOrigin();
            origin.id = id;
            origin.userCreated = userCreated;
            origin.actorId = actorId;
            origin.verifiedAt = new Timestamp(System.currentTimeMillis());
            entityManager.persist(origin);
            count++;
        }
        return count;
    }
}
