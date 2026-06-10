package org.openelisglobal.patientadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldDefinition;

public interface PatientAdditionalFieldDefinitionDAO extends BaseDAO<PatientAdditionalFieldDefinition, Integer> {

    List<PatientAdditionalFieldDefinition> findAll(boolean activeOnly);

    Optional<PatientAdditionalFieldDefinition> findByFieldKey(String fieldKey);

    List<PatientAdditionalFieldDefinition> findByIds(List<Integer> ids);
}
