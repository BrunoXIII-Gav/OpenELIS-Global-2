package org.openelisglobal.orderadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldFile;

public interface SampleOrderAdditionalFieldFileDAO extends BaseDAO<SampleOrderAdditionalFieldFile, Integer> {

    Optional<SampleOrderAdditionalFieldFile> findBySampleIdAndFieldDefinitionId(Integer sampleId,
            Integer fieldDefinitionId);

    List<SampleOrderAdditionalFieldFile> findBySampleIdAndFieldDefinitionIds(Integer sampleId,
            List<Integer> fieldDefinitionIds);
}
