package org.openelisglobal.sampleadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.sampleadditionalfield.valueholder.SampleFixedFieldConfig;

public interface SampleFixedFieldConfigDAO extends BaseDAO<SampleFixedFieldConfig, Integer> {
    List<SampleFixedFieldConfig> findAllOrdered();

    Optional<SampleFixedFieldConfig> findByFieldKey(String fieldKey);
}
