package org.openelisglobal.orderadditionalfield.daoimpl;

import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.orderadditionalfield.dao.OrderFixedFieldConfigDAO;
import org.openelisglobal.orderadditionalfield.valueholder.OrderFixedFieldConfig;
import org.springframework.stereotype.Component;

@Component
public class OrderFixedFieldConfigDAOImpl extends BaseDAOImpl<OrderFixedFieldConfig, Integer>
        implements OrderFixedFieldConfigDAO {

    public OrderFixedFieldConfigDAOImpl() {
        super(OrderFixedFieldConfig.class);
    }

    @Override
    public List<OrderFixedFieldConfig> findAllOrdered() {
        String hql = "from OrderFixedFieldConfig c order by c.sortOrder asc, c.id asc";
        Query<OrderFixedFieldConfig> query = entityManager.unwrap(Session.class).createQuery(hql,
                OrderFixedFieldConfig.class);
        return query.list();
    }

    @Override
    public Optional<OrderFixedFieldConfig> findByFieldKey(String fieldKey) {
        String hql = "from OrderFixedFieldConfig c where lower(c.fieldKey) = :fieldKey";
        Query<OrderFixedFieldConfig> query = entityManager.unwrap(Session.class).createQuery(hql,
                OrderFixedFieldConfig.class);
        query.setParameter("fieldKey", fieldKey == null ? "" : fieldKey.trim().toLowerCase());
        return Optional.ofNullable(query.uniqueResult());
    }
}
