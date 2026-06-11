package org.openelisglobal.patientadditionalfield.service;

import java.util.List;
import java.util.Map;
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldOptionPayload;
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldPayload;

public interface PatientAdditionalFieldService {

    List<PatientAdditionalFieldPayload> getFields(boolean includeInactive);

    Map<String, String> getPatientValues(String patientId, List<PatientAdditionalFieldPayload> fieldDefinitions);

    PatientAdditionalFieldPayload createField(PatientAdditionalFieldPayload payload, String currentUserId);

    PatientAdditionalFieldPayload updateField(Integer fieldId, PatientAdditionalFieldPayload payload,
            String currentUserId);

    void deactivateField(Integer fieldId, String currentUserId);

    PatientAdditionalFieldOptionPayload createOption(Integer fieldId, PatientAdditionalFieldOptionPayload payload,
            String currentUserId);

    PatientAdditionalFieldOptionPayload updateOption(Integer optionId, PatientAdditionalFieldOptionPayload payload,
            String currentUserId);

    void deactivateOption(Integer optionId, String currentUserId);

    void validateAndPersistPatientValues(String patientId, Map<String, String> fieldValues, String currentUserId,
            List<PatientAdditionalFieldPayload> activeFieldCache);
}
