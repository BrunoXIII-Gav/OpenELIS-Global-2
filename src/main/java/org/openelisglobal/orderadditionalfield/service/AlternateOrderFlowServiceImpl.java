package org.openelisglobal.orderadditionalfield.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldValueDAO;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AlternateOrderFlowServiceImpl implements AlternateOrderFlowService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String WORKFLOW_NODE = "workflow";
    private static final String ALTERNATE_ORDER_FLOW_NODE = "alternateOrderFlow";
    private static final String TRIGGER_VALUE_NODE = "triggerValue";

    @Autowired
    private OrderAdditionalFieldService orderAdditionalFieldService;

    @Autowired
    private SampleOrderAdditionalFieldValueDAO sampleOrderAdditionalFieldValueDAO;

    @Override
    public boolean isAlternateOrderFlow(Map<String, String> orderAdditionalFieldValues) {
        List<TriggerFieldConfig> triggerFields = getTriggerFieldConfigs();
        return isAlternateOrderFlow(orderAdditionalFieldValues, triggerFields);
    }

    private boolean isAlternateOrderFlow(Map<String, String> orderAdditionalFieldValues,
            List<TriggerFieldConfig> triggerFields) {
        if (triggerFields.isEmpty()) {
            return false;
        }

        Map<String, String> values = orderAdditionalFieldValues == null ? Collections.emptyMap() : orderAdditionalFieldValues;
        for (TriggerFieldConfig triggerField : triggerFields) {
            String configuredValue = values.get(triggerField.fieldKey());
            if (matchesTriggerValue(triggerField, configuredValue)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAlternateOrderFlow(String sampleId) {
        String normalizedSampleId = StringUtils.trimToNull(sampleId);
        if (normalizedSampleId == null) {
            return false;
        }
        return getAlternateOrderFlowSampleIds(List.of(normalizedSampleId)).contains(normalizedSampleId);
    }

    @Override
    public Set<String> getAlternateOrderFlowSampleIds(List<String> sampleIds) {
        List<TriggerFieldConfig> triggerFields = getTriggerFieldConfigs();
        if (triggerFields.isEmpty() || sampleIds == null || sampleIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<Integer> numericSampleIds = sampleIds.stream().map(StringUtils::trimToNull).filter(StringUtils::isNotBlank)
                .map(this::tryParseInteger).flatMap(Optional::stream).distinct().collect(Collectors.toList());
        if (numericSampleIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<Integer> definitionIds = triggerFields.stream().map(TriggerFieldConfig::fieldDefinitionId).distinct()
                .collect(Collectors.toList());
        List<SampleOrderAdditionalFieldValue> persistedValues = sampleOrderAdditionalFieldValueDAO
                .findBySampleIdsAndFieldDefinitionIds(numericSampleIds, definitionIds);
        if (persistedValues.isEmpty()) {
            return Collections.emptySet();
        }

        Map<Integer, TriggerFieldConfig> triggerFieldsByDefinitionId = triggerFields.stream()
                .collect(Collectors.toMap(TriggerFieldConfig::fieldDefinitionId, config -> config, (left, _right) -> left));
        Map<String, Map<String, String>> valuesBySampleId = new HashMap<>();
        for (SampleOrderAdditionalFieldValue persistedValue : persistedValues) {
            TriggerFieldConfig triggerField = triggerFieldsByDefinitionId.get(persistedValue.getFieldDefinitionId());
            if (triggerField == null) {
                continue;
            }
            valuesBySampleId.computeIfAbsent(String.valueOf(persistedValue.getSampleId()), _sampleId -> new HashMap<>())
                    .put(triggerField.fieldKey(), persistedValue.getFieldValue());
        }

        Set<String> matchingSampleIds = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, String>> sampleEntry : valuesBySampleId.entrySet()) {
            if (isAlternateOrderFlow(sampleEntry.getValue(), triggerFields)) {
                matchingSampleIds.add(sampleEntry.getKey());
            }
        }
        return matchingSampleIds;
    }

    @Override
    public Optional<OrderAdditionalFieldPayload> getPrimaryTriggerField() {
        return getTriggerFieldConfigs().stream().map(TriggerFieldConfig::payload).findFirst();
    }

    private List<TriggerFieldConfig> getTriggerFieldConfigs() {
        List<OrderAdditionalFieldPayload> activeFields = orderAdditionalFieldService.getFields(false);
        if (activeFields == null || activeFields.isEmpty()) {
            return Collections.emptyList();
        }

        List<TriggerFieldConfig> triggerFields = new ArrayList<>();
        for (OrderAdditionalFieldPayload field : activeFields) {
            TriggerFieldConfig config = toTriggerFieldConfig(field);
            if (config != null) {
                triggerFields.add(config);
            }
        }
        return triggerFields;
    }

    private TriggerFieldConfig toTriggerFieldConfig(OrderAdditionalFieldPayload field) {
        if (field == null || field.getId() == null || StringUtils.isBlank(field.getFieldKey())) {
            return null;
        }

        JsonNode root = parseMetadata(field.getMetadataJson());
        if (root == null) {
            return null;
        }

        JsonNode workflowNode = root.path(WORKFLOW_NODE);
        if (!workflowNode.path(ALTERNATE_ORDER_FLOW_NODE).asBoolean(false)) {
            return null;
        }

        String triggerValue = StringUtils.trimToNull(workflowNode.path(TRIGGER_VALUE_NODE).asText(null));
        return new TriggerFieldConfig(field.getId(), field.getFieldKey(), field.getFieldType(), triggerValue, field);
    }

    private JsonNode parseMetadata(String metadataJson) {
        if (StringUtils.isBlank(metadataJson)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(metadataJson);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean matchesTriggerValue(TriggerFieldConfig triggerField, String configuredValue) {
        String normalizedValue = StringUtils.trimToNull(configuredValue);
        String configuredTriggerValue = StringUtils.trimToNull(triggerField.triggerValue());

        if (configuredTriggerValue != null) {
            return StringUtils.equalsIgnoreCase(configuredTriggerValue, normalizedValue);
        }

        if ("BOOLEAN".equalsIgnoreCase(triggerField.fieldType())) {
            return isTruthyValue(normalizedValue);
        }

        return StringUtils.isNotBlank(normalizedValue);
    }

    private boolean isTruthyValue(String value) {
        String normalized = StringUtils.trimToEmpty(value);
        return "true".equalsIgnoreCase(normalized) || "yes".equalsIgnoreCase(normalized)
                || "1".equalsIgnoreCase(normalized);
    }

    private Optional<Integer> tryParseInteger(String value) {
        try {
            return Optional.of(Integer.valueOf(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private record TriggerFieldConfig(Integer fieldDefinitionId, String fieldKey, String fieldType, String triggerValue,
            OrderAdditionalFieldPayload payload) {
    }
}
