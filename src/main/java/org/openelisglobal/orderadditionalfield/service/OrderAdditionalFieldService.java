package org.openelisglobal.orderadditionalfield.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.openelisglobal.common.documentupload.TemporaryDocumentUploadPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFilePayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFileReference;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldOptionPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderFixedFieldConfigPayload;

public interface OrderAdditionalFieldService {

    default List<OrderAdditionalFieldPayload> getFields(boolean includeInactive) {
        return getFields(includeInactive, false);
    }

    List<OrderAdditionalFieldPayload> getFields(boolean includeInactive, boolean resolveUserOptions);

    Map<String, String> getVisibleConsentTemplates(String sampleId, Map<String, String> fieldValues,
            Map<String, String> fixedFieldValues, boolean requiredOnly);

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

    void validateAndPersistSampleValues(String sampleId, Map<String, String> fieldValues,
            Map<String, OrderAdditionalFieldFilePayload> fieldFiles, String currentUserId,
            List<OrderAdditionalFieldPayload> activeFieldCache, Map<String, String> fixedFieldValues);

    Map<String, OrderAdditionalFieldFilePayload> getSampleFileValues(String sampleId,
            List<OrderAdditionalFieldPayload> fieldDefinitions);

    Optional<OrderAdditionalFieldFilePayload> getSampleFile(String sampleId, String fieldKey);

    Map<String, OrderAdditionalFieldFilePayload> getReportImageFiles(String sampleId, Set<String> fieldKeys,
            Map<String, OrderAdditionalFieldFileReference> references, Map<String, String> fieldValues,
            Map<String, String> fixedFieldValues);

    TemporaryDocumentUploadPayload prepareDocumentUpload(String fieldKey, String fileName, String fileType,
            long fileSize, byte[] content);

    Optional<Integer> findSampleIdBySearchableFieldValue(String searchValue);

    Optional<String> getStorageAssignmentBlockReason(String sampleId);

    List<OrderFixedFieldConfigPayload> getFixedFieldConfigs();

    void upsertFixedFieldConfigs(List<OrderFixedFieldConfigPayload> payloads, String currentUserId);
}
