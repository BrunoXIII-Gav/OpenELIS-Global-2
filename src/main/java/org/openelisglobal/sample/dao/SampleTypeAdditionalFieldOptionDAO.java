package org.openelisglobal.sample.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldOption;

public interface SampleTypeAdditionalFieldOptionDAO extends BaseDAO<SampleTypeAdditionalFieldOption, Integer> {

    List<SampleTypeAdditionalFieldOption> findByDefinitionId(Integer definitionId, boolean includeInactive);

    List<SampleTypeAdditionalFieldOption> findByDefinitionIds(List<Integer> definitionIds, boolean activeOnly);

    Optional<SampleTypeAdditionalFieldOption> findByDefinitionIdAndOptionKey(Integer definitionId, String optionKey);
}
