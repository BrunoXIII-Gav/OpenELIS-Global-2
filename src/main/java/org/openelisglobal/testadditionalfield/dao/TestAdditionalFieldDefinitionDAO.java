package org.openelisglobal.testadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition;

public interface TestAdditionalFieldDefinitionDAO extends BaseDAO<TestAdditionalFieldDefinition, Integer> {

    List<TestAdditionalFieldDefinition> findByTestId(Integer testId, boolean includeInactive);

    List<TestAdditionalFieldDefinition> findByTestIds(List<Integer> testIds, boolean activeOnly);

    List<TestAdditionalFieldDefinition> findByIds(List<Integer> fieldDefIds);

    Optional<TestAdditionalFieldDefinition> findByTestIdAndFieldKey(Integer testId, String fieldKey);
}
