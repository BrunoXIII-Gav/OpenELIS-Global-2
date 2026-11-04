package org.openelisglobal.sample.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.sample.valueholder.SampleItemAdditionalFieldValue;

public interface SampleItemAdditionalFieldValueDAO extends BaseDAO<SampleItemAdditionalFieldValue, Integer> {

    List<SampleItemAdditionalFieldValue> findBySampleItemId(Integer sampleItemId);

    Optional<SampleItemAdditionalFieldValue> findBySampleItemIdAndFieldDefinitionId(Integer sampleItemId,
            Integer fieldDefinitionId);

    long countByFieldDefinitionId(Integer fieldDefinitionId);
}
