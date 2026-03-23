package org.openelisglobal.sample.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.sample.dao.SampleTypeAdditionalFieldOptionDAO;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldOption;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class SampleTypeAdditionalFieldOptionDAOImpl extends BaseDAOImpl<SampleTypeAdditionalFieldOption, Integer>
        implements SampleTypeAdditionalFieldOptionDAO {

    public SampleTypeAdditionalFieldOptionDAOImpl() {
        super(SampleTypeAdditionalFieldOption.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SampleTypeAdditionalFieldOption> findByDefinitionId(Integer definitionId, boolean includeInactive) {
        String hql = "FROM SampleTypeAdditionalFieldOption o WHERE o.fieldDefinitionId = :definitionId"
                + (includeInactive ? "" : " AND o.active = true") + " ORDER BY o.sortOrder ASC, o.id ASC";
        Query<SampleTypeAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleTypeAdditionalFieldOption.class);
        query.setParameter("definitionId", definitionId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SampleTypeAdditionalFieldOption> findByDefinitionIds(List<Integer> definitionIds, boolean activeOnly) {
        if (definitionIds == null || definitionIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "FROM SampleTypeAdditionalFieldOption o WHERE o.fieldDefinitionId IN (:definitionIds)"
                + (activeOnly ? " AND o.active = true" : "")
                + " ORDER BY o.fieldDefinitionId ASC, o.sortOrder ASC, o.id ASC";

        Query<SampleTypeAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleTypeAdditionalFieldOption.class);
        query.setParameterList("definitionIds", definitionIds);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SampleTypeAdditionalFieldOption> findByDefinitionIdAndOptionKey(Integer definitionId,
            String optionKey) {
        String hql = "FROM SampleTypeAdditionalFieldOption o WHERE o.fieldDefinitionId = :definitionId "
                + "AND lower(o.optionKey) = :optionKey";
        Query<SampleTypeAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleTypeAdditionalFieldOption.class);
        query.setParameter("definitionId", definitionId);
        query.setParameter("optionKey", optionKey.toLowerCase());
        SampleTypeAdditionalFieldOption option = query.uniqueResult();
        return Optional.ofNullable(option);
    }
}
