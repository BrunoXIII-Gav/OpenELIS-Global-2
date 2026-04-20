package org.openelisglobal.orderadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.orderadditionalfield.valueholder.OrderFixedFieldConfig;

public interface OrderFixedFieldConfigDAO extends BaseDAO<OrderFixedFieldConfig, Integer> {

    List<OrderFixedFieldConfig> findAllOrdered();

    Optional<OrderFixedFieldConfig> findByFieldKey(String fieldKey);
}
