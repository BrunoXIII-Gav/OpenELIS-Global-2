package org.openelisglobal.testadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldOption;

public interface TestAdditionalFieldOptionDAO extends BaseDAO<TestAdditionalFieldOption, Integer> {

    List<TestAdditionalFieldOption> findByDefinitionIds(List<Integer> definitionIds, boolean activeOnly);

    List<TestAdditionalFieldOption> findByDefinitionId(Integer definitionId, boolean includeInactive);

    Optional<TestAdditionalFieldOption> findByDefinitionIdAndOptionKey(Integer definitionId, String optionKey);
}
