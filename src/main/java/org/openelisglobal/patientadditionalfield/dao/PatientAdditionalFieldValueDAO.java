package org.openelisglobal.patientadditionalfield.dao;

import java.util.List;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldValue;

public interface PatientAdditionalFieldValueDAO extends BaseDAO<PatientAdditionalFieldValue, Integer> {

    Optional<PatientAdditionalFieldValue> findByPatientIdAndFieldDefinitionId(Integer patientId,
            Integer fieldDefinitionId);

    List<PatientAdditionalFieldValue> findByPatientIdAndFieldDefinitionIds(Integer patientId,
            List<Integer> fieldDefinitionIds);

    List<PatientAdditionalFieldValue> findByPatientId(Integer patientId);
}
