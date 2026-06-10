package org.openelisglobal.patientadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldOption;

public interface PatientAdditionalFieldOptionDAO extends BaseDAO<PatientAdditionalFieldOption, Integer> {

    List<PatientAdditionalFieldOption> findByDefinitionId(Integer definitionId, boolean includeInactive);

    List<PatientAdditionalFieldOption> findByDefinitionIds(List<Integer> definitionIds, boolean activeOnly);

    Optional<PatientAdditionalFieldOption> findByDefinitionIdAndOptionKey(Integer definitionId, String optionKey);
}
