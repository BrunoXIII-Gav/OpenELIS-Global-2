package org.openelisglobal.orderadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;

public interface SampleOrderAdditionalFieldValueDAO extends BaseDAO<SampleOrderAdditionalFieldValue, Integer> {

    Optional<SampleOrderAdditionalFieldValue> findBySampleIdAndFieldDefinitionId(Integer sampleId,
            Integer fieldDefinitionId);

    List<SampleOrderAdditionalFieldValue> findBySampleIdAndFieldDefinitionIds(Integer sampleId,
            List<Integer> fieldDefinitionIds);

    List<SampleOrderAdditionalFieldValue> findBySampleIdsAndFieldDefinitionIds(List<Integer> sampleIds,
            List<Integer> fieldDefinitionIds);

    List<Integer> findDistinctSampleIdsBySearchableFieldValue(String searchValue, boolean uniqueOnly, int limit);

    boolean existsByFieldDefinitionIdAndFieldValueIgnoreCaseAndSampleIdNot(Integer fieldDefinitionId, String fieldValue,
            Integer excludedSampleId);
}
