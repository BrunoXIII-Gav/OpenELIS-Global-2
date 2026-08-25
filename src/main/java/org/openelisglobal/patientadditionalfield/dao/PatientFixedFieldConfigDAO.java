package org.openelisglobal.patientadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.patientadditionalfield.valueholder.PatientFixedFieldConfig;

public interface PatientFixedFieldConfigDAO extends BaseDAO<PatientFixedFieldConfig, Integer> {

    List<PatientFixedFieldConfig> findAllOrdered();

    Optional<PatientFixedFieldConfig> findByFieldKey(String fieldKey);
}
