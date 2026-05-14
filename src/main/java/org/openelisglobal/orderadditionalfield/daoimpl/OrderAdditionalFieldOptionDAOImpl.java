package org.openelisglobal.orderadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.orderadditionalfield.dao.OrderAdditionalFieldOptionDAO;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldOption;
import org.springframework.stereotype.Component;

@Component
public class OrderAdditionalFieldOptionDAOImpl extends BaseDAOImpl<OrderAdditionalFieldOption, Integer>
        implements OrderAdditionalFieldOptionDAO {

    public OrderAdditionalFieldOptionDAOImpl() {
        super(OrderAdditionalFieldOption.class);
    }

    @Override
    public List<OrderAdditionalFieldOption> findByDefinitionId(Integer definitionId, boolean includeInactive) {
        if (definitionId == null) {
            return Collections.emptyList();
        }

        String hql = "from OrderAdditionalFieldOption o where o.fieldDefinitionId = :definitionId "
                + (includeInactive ? "" : "and o.active = true ") + "order by o.sortOrder asc, o.id asc";
        Query<OrderAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                OrderAdditionalFieldOption.class);
        query.setParameter("definitionId", definitionId);
        return query.list();
    }

    @Override
    public List<OrderAdditionalFieldOption> findByDefinitionIds(List<Integer> definitionIds, boolean activeOnly) {
        if (definitionIds == null || definitionIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "from OrderAdditionalFieldOption o where o.fieldDefinitionId in (:definitionIds) "
                + (activeOnly ? "and o.active = true " : "") + "order by o.sortOrder asc, o.id asc";
        Query<OrderAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                OrderAdditionalFieldOption.class);
        query.setParameterList("definitionIds", definitionIds);
        return query.list();
    }

    @Override
    public Optional<OrderAdditionalFieldOption> findByDefinitionIdAndOptionKey(Integer definitionId, String optionKey) {
        if (definitionId == null || optionKey == null) {
            return Optional.empty();
        }

        String hql = "from OrderAdditionalFieldOption o where o.fieldDefinitionId = :definitionId and lower(o.optionKey) = :optionKey";
        Query<OrderAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                OrderAdditionalFieldOption.class);
        query.setParameter("definitionId", definitionId);
        query.setParameter("optionKey", optionKey.trim().toLowerCase());
        return Optional.ofNullable(query.uniqueResult());
    }
}
