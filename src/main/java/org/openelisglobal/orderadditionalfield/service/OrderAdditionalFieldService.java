package org.openelisglobal.orderadditionalfield.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFilePayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldOptionPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderFixedFieldConfigPayload;

public interface OrderAdditionalFieldService {

    List<OrderAdditionalFieldPayload> getFields(boolean includeInactive);

    OrderAdditionalFieldPayload createField(OrderAdditionalFieldPayload payload, String currentUserId);

    OrderAdditionalFieldPayload updateField(Integer fieldId, OrderAdditionalFieldPayload payload, String currentUserId);

    void deactivateField(Integer fieldId, String currentUserId);

    OrderAdditionalFieldOptionPayload createOption(Integer fieldId, OrderAdditionalFieldOptionPayload payload,
            String currentUserId);

    OrderAdditionalFieldOptionPayload updateOption(Integer optionId, OrderAdditionalFieldOptionPayload payload,
            String currentUserId);

    void deactivateOption(Integer optionId, String currentUserId);

    Map<String, String> getSampleValues(String sampleId, List<OrderAdditionalFieldPayload> fieldDefinitions);

    void validateAndPersistSampleValues(String sampleId, Map<String, String> fieldValues, String currentUserId,
            List<OrderAdditionalFieldPayload> activeFieldCache);

    void validateAndPersistSampleValues(String sampleId, Map<String, String> fieldValues,
            Map<String, OrderAdditionalFieldFilePayload> fieldFiles, String currentUserId,
            List<OrderAdditionalFieldPayload> activeFieldCache);

    Map<String, OrderAdditionalFieldFilePayload> getSampleFileValues(String sampleId,
            List<OrderAdditionalFieldPayload> fieldDefinitions);

    Optional<OrderAdditionalFieldFilePayload> getSampleFile(String sampleId, String fieldKey);

    List<OrderFixedFieldConfigPayload> getFixedFieldConfigs();

    void upsertFixedFieldConfigs(List<OrderFixedFieldConfigPayload> payloads, String currentUserId);
}
