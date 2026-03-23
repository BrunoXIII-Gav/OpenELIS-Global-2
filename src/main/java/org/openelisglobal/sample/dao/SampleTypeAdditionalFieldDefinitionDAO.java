package org.openelisglobal.sample.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldDefinition;

public interface SampleTypeAdditionalFieldDefinitionDAO extends BaseDAO<SampleTypeAdditionalFieldDefinition, Integer> {

    List<SampleTypeAdditionalFieldDefinition> findBySampleTypeId(Integer sampleTypeId, boolean includeInactive);

    List<SampleTypeAdditionalFieldDefinition> findBySampleTypeIds(List<Integer> sampleTypeIds, boolean activeOnly);

    Optional<SampleTypeAdditionalFieldDefinition> findBySampleTypeIdAndFieldKey(Integer sampleTypeId, String fieldKey);
}
