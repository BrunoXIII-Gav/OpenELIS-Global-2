package org.openelisglobal.testadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.testadditionalfield.dao.TestAdditionalFieldOptionDAO;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldOption;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class TestAdditionalFieldOptionDAOImpl extends BaseDAOImpl<TestAdditionalFieldOption, Integer>
        implements TestAdditionalFieldOptionDAO {

    public TestAdditionalFieldOptionDAOImpl() {
        super(TestAdditionalFieldOption.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestAdditionalFieldOption> findByDefinitionIds(List<Integer> definitionIds, boolean activeOnly) {
        if (definitionIds == null || definitionIds.isEmpty()) {
            return Collections.emptyList();
        }
        String hql = "FROM TestAdditionalFieldOption o WHERE o.fieldDefinitionId IN (:definitionIds)"
                + (activeOnly ? " AND o.active = true" : "")
                + " ORDER BY o.fieldDefinitionId ASC, o.sortOrder ASC, o.id ASC";
        Query<TestAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                TestAdditionalFieldOption.class);
        query.setParameterList("definitionIds", definitionIds);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TestAdditionalFieldOption> findByDefinitionId(Integer definitionId, boolean includeInactive) {
        String hql = "FROM TestAdditionalFieldOption o WHERE o.fieldDefinitionId = :definitionId"
                + (includeInactive ? "" : " AND o.active = true") + " ORDER BY o.sortOrder ASC, o.id ASC";
        Query<TestAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                TestAdditionalFieldOption.class);
        query.setParameter("definitionId", definitionId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TestAdditionalFieldOption> findByDefinitionIdAndOptionKey(Integer definitionId, String optionKey) {
        String hql = "FROM TestAdditionalFieldOption o WHERE o.fieldDefinitionId = :definitionId AND lower(o.optionKey) = :optionKey";
        Query<TestAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                TestAdditionalFieldOption.class);
        query.setParameter("definitionId", definitionId);
        query.setParameter("optionKey", optionKey.toLowerCase());
        return Optional.ofNullable(query.uniqueResult());
    }
}
