package org.openelisglobal.orderadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldOption;

public interface OrderAdditionalFieldOptionDAO extends BaseDAO<OrderAdditionalFieldOption, Integer> {

    List<OrderAdditionalFieldOption> findByDefinitionId(Integer definitionId, boolean includeInactive);

    List<OrderAdditionalFieldOption> findByDefinitionIds(List<Integer> definitionIds, boolean activeOnly);

    Optional<OrderAdditionalFieldOption> findByDefinitionIdAndOptionKey(Integer definitionId, String optionKey);
}
