package org.openelisglobal.orderadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.orderadditionalfield.dao.OrderAdditionalFieldDefinitionDAO;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition;
import org.springframework.stereotype.Component;

@Component
public class OrderAdditionalFieldDefinitionDAOImpl extends BaseDAOImpl<OrderAdditionalFieldDefinition, Integer>
        implements OrderAdditionalFieldDefinitionDAO {

    public OrderAdditionalFieldDefinitionDAOImpl() {
        super(OrderAdditionalFieldDefinition.class);
    }

    @Override
    public List<OrderAdditionalFieldDefinition> findAll(boolean activeOnly) {
        String hql = "from OrderAdditionalFieldDefinition d "
                + (activeOnly ? "where d.active = true " : "")
                + "order by d.sortOrder asc, d.id asc";
        Query<OrderAdditionalFieldDefinition> query = entityManager.unwrap(Session.class)
                .createQuery(hql, OrderAdditionalFieldDefinition.class);
        return query.list();
    }

    @Override
    public Optional<OrderAdditionalFieldDefinition> findByFieldKey(String fieldKey) {
        String hql = "from OrderAdditionalFieldDefinition d where lower(d.fieldKey) = :fieldKey";
        Query<OrderAdditionalFieldDefinition> query = entityManager.unwrap(Session.class)
                .createQuery(hql, OrderAdditionalFieldDefinition.class);
        query.setParameter("fieldKey", fieldKey == null ? "" : fieldKey.trim().toLowerCase());
        return Optional.ofNullable(query.uniqueResult());
    }

    @Override
    public List<OrderAdditionalFieldDefinition> findByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "from OrderAdditionalFieldDefinition d where d.id in (:ids) order by d.sortOrder asc, d.id asc";
        Query<OrderAdditionalFieldDefinition> query = entityManager.unwrap(Session.class)
                .createQuery(hql, OrderAdditionalFieldDefinition.class);
        query.setParameterList("ids", ids);
        return query.list();
    }
}
