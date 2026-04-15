package org.openelisglobal.testadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.testadditionalfield.dao.TestAdditionalFieldDefinitionDAO;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TestAdditionalFieldDefinitionDAOImpl extends BaseDAOImpl<TestAdditionalFieldDefinition, Integer>
        implements TestAdditionalFieldDefinitionDAO {

    public TestAdditionalFieldDefinitionDAOImpl() {
        super(TestAdditionalFieldDefinition.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestAdditionalFieldDefinition> findByTestId(Integer testId, boolean includeInactive) {
        String hql = "FROM TestAdditionalFieldDefinition d WHERE d.testId = :testId"
                + (includeInactive ? "" : " AND d.active = true") + " ORDER BY d.sortOrder ASC, d.id ASC";
        Query<TestAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                TestAdditionalFieldDefinition.class);
        query.setParameter("testId", testId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestAdditionalFieldDefinition> findByTestIds(List<Integer> testIds, boolean activeOnly) {
        if (testIds == null || testIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "FROM TestAdditionalFieldDefinition d WHERE d.testId IN (:testIds)"
                + (activeOnly ? " AND d.active = true" : "") + " ORDER BY d.testId ASC, d.sortOrder ASC, d.id ASC";
        Query<TestAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                TestAdditionalFieldDefinition.class);
        query.setParameterList("testIds", testIds);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestAdditionalFieldDefinition> findByIds(List<Integer> fieldDefIds) {
        if (fieldDefIds == null || fieldDefIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "FROM TestAdditionalFieldDefinition d WHERE d.id IN (:fieldDefIds)";
        Query<TestAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                TestAdditionalFieldDefinition.class);
        query.setParameterList("fieldDefIds", fieldDefIds);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TestAdditionalFieldDefinition> findByTestIdAndFieldKey(Integer testId, String fieldKey) {
        String hql = "FROM TestAdditionalFieldDefinition d WHERE d.testId = :testId AND lower(d.fieldKey) = :fieldKey";
        Query<TestAdditionalFieldDefinition> query = entityManager.unwrap(Session.class).createQuery(hql,
                TestAdditionalFieldDefinition.class);
        query.setParameter("testId", testId);
        query.setParameter("fieldKey", fieldKey.toLowerCase());
        return Optional.ofNullable(query.uniqueResult());
    }
}
