package org.openelisglobal.testadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;

public interface AnalysisAdditionalFieldValueDAO extends BaseDAO<AnalysisAdditionalFieldValue, Integer> {

    List<AnalysisAdditionalFieldValue> findByAnalysisId(Integer analysisId);

    List<AnalysisAdditionalFieldValue> findByAnalysisIdAndFieldDefinitionIds(Integer analysisId, List<Integer> fieldDefIds);

    Optional<AnalysisAdditionalFieldValue> findByAnalysisIdAndFieldDefinitionId(Integer analysisId, Integer fieldDefinitionId);
}
