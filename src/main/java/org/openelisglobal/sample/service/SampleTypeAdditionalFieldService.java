package org.openelisglobal.sample.service;

import java.util.List;
import java.util.Map;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldOptionPayload;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;

public interface SampleTypeAdditionalFieldService {

    List<SampleTypeAdditionalFieldPayload> getFieldsForSampleType(String sampleTypeId, boolean includeInactive);

    Map<String, List<SampleTypeAdditionalFieldPayload>> getActiveFieldsForSampleTypes(List<String> sampleTypeIds);

    SampleTypeAdditionalFieldPayload createField(SampleTypeAdditionalFieldPayload payload, String currentUserId);

    SampleTypeAdditionalFieldPayload updateField(Integer fieldId, SampleTypeAdditionalFieldPayload payload,
            String currentUserId);

    void deactivateField(Integer fieldId, String currentUserId);

    SampleTypeAdditionalFieldOptionPayload createOption(Integer fieldId, SampleTypeAdditionalFieldOptionPayload payload,
            String currentUserId);

    SampleTypeAdditionalFieldOptionPayload updateOption(Integer optionId, SampleTypeAdditionalFieldOptionPayload payload,
            String currentUserId);

    void deactivateOption(Integer optionId, String currentUserId);

    void validateAndPersistSampleItemValues(String sampleTypeId, String sampleItemId, Map<String, String> fieldValues,
            String currentUserId, Map<String, List<SampleTypeAdditionalFieldPayload>> activeFieldsBySampleTypeCache);
}
