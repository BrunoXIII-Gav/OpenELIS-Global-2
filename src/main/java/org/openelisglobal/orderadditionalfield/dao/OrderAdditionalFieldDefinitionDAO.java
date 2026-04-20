package org.openelisglobal.orderadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition;

public interface OrderAdditionalFieldDefinitionDAO extends BaseDAO<OrderAdditionalFieldDefinition, Integer> {

    List<OrderAdditionalFieldDefinition> findAll(boolean activeOnly);

    Optional<OrderAdditionalFieldDefinition> findByFieldKey(String fieldKey);

    List<OrderAdditionalFieldDefinition> findByIds(List<Integer> ids);
}
