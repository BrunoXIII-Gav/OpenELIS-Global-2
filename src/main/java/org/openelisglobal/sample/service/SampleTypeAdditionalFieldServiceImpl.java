package org.openelisglobal.sample.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldOptionPayload;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.dao.SampleItemAdditionalFieldValueDAO;
import org.openelisglobal.sample.dao.SampleTypeAdditionalFieldDefinitionDAO;
import org.openelisglobal.sample.dao.SampleTypeAdditionalFieldOptionDAO;
import org.openelisglobal.sample.valueholder.SampleItemAdditionalFieldValue;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldDefinition;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldDefinition.FieldType;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SampleTypeAdditionalFieldServiceImpl implements SampleTypeAdditionalFieldService {

    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_\\-]{1,79}$");
    private static final DateTimeFormatter DATE_TIME_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_WITH_SPACE_SECONDS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<FieldType> OPTION_TYPES = Set.of(FieldType.SELECT, FieldType.RADIO, FieldType.MULTISELECT);

    @Autowired
    private SampleTypeAdditionalFieldDefinitionDAO definitionDAO;

    @Autowired
    private SampleTypeAdditionalFieldOptionDAO optionDAO;

    @Autowired
    private SampleItemAdditionalFieldValueDAO valueDAO;

    @Override
    @Transactional(readOnly = true)
    public List<SampleTypeAdditionalFieldPayload> getFieldsForSampleType(String sampleTypeId, boolean includeInactive) {
        Integer numericSampleTypeId = parseNumericId(sampleTypeId, "sampleTypeId");
        List<SampleTypeAdditionalFieldDefinition> definitions = definitionDAO.findBySampleTypeId(numericSampleTypeId,
                includeInactive);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> definitionIds = definitions.stream().map(SampleTypeAdditionalFieldDefinition::getId)
                .collect(Collectors.toList());
        List<SampleTypeAdditionalFieldOption> options = optionDAO.findByDefinitionIds(definitionIds, !includeInactive);

        return mapDefinitionsToPayload(definitions, options);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<SampleTypeAdditionalFieldPayload>> getActiveFieldsForSampleTypes(
            List<String> sampleTypeIds) {
        Map<String, List<SampleTypeAdditionalFieldPayload>> result = new HashMap<>();
        if (sampleTypeIds == null || sampleTypeIds.isEmpty()) {
            return result;
        }

        List<Integer> numericIds = new ArrayList<>();
        for (String sampleTypeId : new LinkedHashSet<>(sampleTypeIds)) {
            if (StringUtils.isBlank(sampleTypeId)) {
                continue;
            }
            Integer numericId = parseNumericId(sampleTypeId, "sampleTypeId");
            numericIds.add(numericId);
            result.put(sampleTypeId, new ArrayList<>());
        }

        if (numericIds.isEmpty()) {
            return result;
        }

        List<SampleTypeAdditionalFieldDefinition> definitions = definitionDAO.findBySampleTypeIds(numericIds, true);
        if (definitions.isEmpty()) {
            return result;
        }

        List<Integer> definitionIds = definitions.stream().map(SampleTypeAdditionalFieldDefinition::getId)
                .collect(Collectors.toList());
        List<SampleTypeAdditionalFieldOption> options = optionDAO.findByDefinitionIds(definitionIds, true);

        Map<Integer, List<SampleTypeAdditionalFieldPayload>> bySampleType = mapDefinitionsToPayload(definitions,
                options).stream().collect(Collectors.groupingBy(payload -> Integer.valueOf(payload.getSampleTypeId())));

        for (Integer numericId : numericIds) {
            String key = String.valueOf(numericId);
            result.put(key, bySampleType.getOrDefault(numericId, Collections.emptyList()));
        }

        return result;
    }

    @Override
    public Map<String, String> getFieldValuesForSampleItem(String sampleTypeId, String sampleItemId) {
        Integer numericSampleTypeId = parseNumericId(sampleTypeId, "sampleTypeId");
        Integer numericSampleItemId = parseNumericId(sampleItemId, "sampleItemId");

        List<SampleTypeAdditionalFieldDefinition> definitions = definitionDAO.findBySampleTypeId(numericSampleTypeId,
                false);
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, String> fieldKeyByDefinitionId = definitions.stream().collect(Collectors
                .toMap(SampleTypeAdditionalFieldDefinition::getId, SampleTypeAdditionalFieldDefinition::getFieldKey));

        Map<String, String> valuesByFieldKey = new HashMap<>();
        List<SampleItemAdditionalFieldValue> savedValues = valueDAO.findBySampleItemId(numericSampleItemId);
        for (SampleItemAdditionalFieldValue savedValue : savedValues) {
            String fieldKey = fieldKeyByDefinitionId.get(savedValue.getFieldDefinitionId());
            if (StringUtils.isBlank(fieldKey)) {
                continue;
            }
            valuesByFieldKey.put(fieldKey, StringUtils.defaultString(savedValue.getFieldValue()));
        }

        return valuesByFieldKey;
    }

    @Override
    public SampleTypeAdditionalFieldPayload createField(SampleTypeAdditionalFieldPayload payload,
            String currentUserId) {
        validatePayloadRequiredValues(payload);
        Integer sampleTypeNumericId = parseNumericId(payload.getSampleTypeId(), "sampleTypeId");
        String normalizedFieldKey = normalizeFieldKey(payload.getFieldKey(), payload.getDisplayName());
        validateFieldKey(normalizedFieldKey);

        Optional<SampleTypeAdditionalFieldDefinition> existing = definitionDAO
                .findBySampleTypeIdAndFieldKey(sampleTypeNumericId, normalizedFieldKey);
        if (existing.isPresent()) {
            throw new IllegalArgumentException(
                    "Field key already exists for selected sample type: " + normalizedFieldKey);
        }

        FieldType fieldType = parseFieldType(payload.getFieldType());
        SampleTypeAdditionalFieldDefinition definition = new SampleTypeAdditionalFieldDefinition();
        definition.setTypeOfSampleId(sampleTypeNumericId);
        definition.setFieldKey(normalizedFieldKey);
        definition.setDisplayName(payload.getDisplayName().trim());
        definition.setFieldType(fieldType.name());
        definition.setDisplaySection(normalizeDisplaySection(payload.getDisplaySection()));
        definition.setRequired(Boolean.TRUE.equals(payload.getRequired()));
        definition.setActive(payload.getActive() == null || payload.getActive());
        definition.setSortOrder(payload.getSortOrder() == null ? getNextSortOrder(sampleTypeNumericId)
                : Math.max(payload.getSortOrder(), 0));
        definition.setDefaultValue(StringUtils.defaultIfBlank(payload.getDefaultValue(), null));
        definition.setMaxLength(payload.getMaxLength());
        definition.setMetadataJson(StringUtils.defaultIfBlank(payload.getMetadataJson(), null));
        definition.setSysUserId(currentUserId);

        Integer definitionId = definitionDAO.insert(definition);
        saveOptionsForDefinition(definitionId, payload.getOptions(), fieldType, currentUserId);

        return getFieldById(definitionId, true);
    }

    @Override
    public SampleTypeAdditionalFieldPayload updateField(Integer fieldId, SampleTypeAdditionalFieldPayload payload,
            String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }

        SampleTypeAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));

        if (StringUtils.isNotBlank(payload.getDisplayName())) {
            definition.setDisplayName(payload.getDisplayName().trim());
        }

        if (payload.getFieldType() != null) {
            FieldType fieldType = parseFieldType(payload.getFieldType());
            definition.setFieldType(fieldType.name());

            if (payload.getDisplaySection() != null) {
                    definition.setDisplaySection(normalizeDisplaySection(payload.getDisplaySection()));
            }

            if (!isOptionFieldType(fieldType)) {
                List<SampleTypeAdditionalFieldOption> existingOptions = optionDAO.findByDefinitionId(fieldId, true);
                for (SampleTypeAdditionalFieldOption option : existingOptions) {
                    option.setActive(false);
                    option.setSysUserId(currentUserId);
                    optionDAO.update(option);
                }
            }
        }

        if (payload.getRequired() != null) {
            definition.setRequired(payload.getRequired());
        }
        if (payload.getActive() != null) {
            definition.setActive(payload.getActive());
        }
        if (payload.getSortOrder() != null) {
            definition.setSortOrder(Math.max(payload.getSortOrder(), 0));
        }
        if (payload.getDefaultValue() != null) {
            definition.setDefaultValue(StringUtils.defaultIfBlank(payload.getDefaultValue(), null));
        }
        if (payload.getMaxLength() != null) {
            definition.setMaxLength(payload.getMaxLength());
        }
        if (payload.getMetadataJson() != null) {
            definition.setMetadataJson(StringUtils.defaultIfBlank(payload.getMetadataJson(), null));
        }

        definition.setSysUserId(currentUserId);
        definitionDAO.update(definition);

        if (payload.getOptions() != null && !payload.getOptions().isEmpty()
                && isOptionFieldType(parseFieldType(definition.getFieldType()))) {
            upsertOptionsForDefinition(definition.getId(), payload.getOptions(), currentUserId);
        }

        return getFieldById(fieldId, true);
    }

    @Override
    public void deactivateField(Integer fieldId, String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }

        SampleTypeAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        definition.setActive(false);
        definition.setSysUserId(currentUserId);
        definitionDAO.update(definition);

        List<SampleTypeAdditionalFieldOption> options = optionDAO.findByDefinitionId(fieldId, true);
        for (SampleTypeAdditionalFieldOption option : options) {
            option.setActive(false);
            option.setSysUserId(currentUserId);
            optionDAO.update(option);
        }
    }

    @Override
    public SampleTypeAdditionalFieldOptionPayload createOption(Integer fieldId,
            SampleTypeAdditionalFieldOptionPayload payload, String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }
        if (payload == null || StringUtils.isBlank(payload.getOptionLabel())) {
            throw new IllegalArgumentException("optionLabel is required");
        }

        SampleTypeAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));

        FieldType fieldType = parseFieldType(definition.getFieldType());
        if (!isOptionFieldType(fieldType)) {
            throw new IllegalArgumentException("Options can only be added for SELECT, RADIO, or MULTISELECT fields");
        }

        String normalizedOptionKey = normalizeOptionKey(payload.getOptionKey(), payload.getOptionLabel());

        Optional<SampleTypeAdditionalFieldOption> duplicate = optionDAO.findByDefinitionIdAndOptionKey(fieldId,
                normalizedOptionKey);
        if (duplicate.isPresent()) {
            throw new IllegalArgumentException("Option key already exists for selected field: " + normalizedOptionKey);
        }

        SampleTypeAdditionalFieldOption option = new SampleTypeAdditionalFieldOption();
        option.setFieldDefinitionId(fieldId);
        option.setOptionKey(normalizedOptionKey);
        option.setOptionLabel(payload.getOptionLabel().trim());
        option.setSortOrder(payload.getSortOrder() == null ? getNextOptionSortOrder(fieldId) : payload.getSortOrder());
        option.setActive(payload.getActive() == null || payload.getActive());
        option.setSysUserId(currentUserId);

        Integer optionId = optionDAO.insert(option);
        return mapOptionToPayload(optionDAO.get(optionId).orElseThrow());
    }

    @Override
    public SampleTypeAdditionalFieldOptionPayload updateOption(Integer optionId,
            SampleTypeAdditionalFieldOptionPayload payload, String currentUserId) {
        if (optionId == null) {
            throw new IllegalArgumentException("optionId is required");
        }

        SampleTypeAdditionalFieldOption option = optionDAO.get(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));

        if (payload.getOptionKey() != null) {
            String normalizedOptionKey = normalizeOptionKey(payload.getOptionKey(), payload.getOptionLabel());
            Optional<SampleTypeAdditionalFieldOption> duplicate = optionDAO
                    .findByDefinitionIdAndOptionKey(option.getFieldDefinitionId(), normalizedOptionKey);
            if (duplicate.isPresent() && !duplicate.get().getId().equals(optionId)) {
                throw new IllegalArgumentException(
                        "Option key already exists for selected field: " + normalizedOptionKey);
            }
            option.setOptionKey(normalizedOptionKey);
        }

        if (StringUtils.isNotBlank(payload.getOptionLabel())) {
            option.setOptionLabel(payload.getOptionLabel().trim());
        }
        if (payload.getSortOrder() != null) {
            option.setSortOrder(Math.max(payload.getSortOrder(), 0));
        }
        if (payload.getActive() != null) {
            option.setActive(payload.getActive());
        }

        option.setSysUserId(currentUserId);
        optionDAO.update(option);

        return mapOptionToPayload(option);
    }

    @Override
    public void deactivateOption(Integer optionId, String currentUserId) {
        if (optionId == null) {
            throw new IllegalArgumentException("optionId is required");
        }

        SampleTypeAdditionalFieldOption option = optionDAO.get(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
        option.setActive(false);
        option.setSysUserId(currentUserId);
        optionDAO.update(option);
    }

    @Override
    public void validateAndPersistSampleItemValues(String sampleTypeId, String sampleItemId,
            Map<String, String> fieldValues, String currentUserId,
            Map<String, List<SampleTypeAdditionalFieldPayload>> activeFieldsBySampleTypeCache) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            return;
        }

        List<SampleTypeAdditionalFieldPayload> fieldDefinitions = null;
        if (activeFieldsBySampleTypeCache != null) {
            fieldDefinitions = activeFieldsBySampleTypeCache.get(sampleTypeId);
            if (fieldDefinitions == null) {
                fieldDefinitions = activeFieldsBySampleTypeCache
                        .get(String.valueOf(parseNumericId(sampleTypeId, "sampleTypeId")));
            }
        }
        if (fieldDefinitions == null) {
            fieldDefinitions = getFieldsForSampleType(sampleTypeId, false);
        }

        if (fieldDefinitions == null || fieldDefinitions.isEmpty()) {
            return;
        }

        Integer sampleItemNumericId = parseNumericId(sampleItemId, "sampleItemId");

        for (SampleTypeAdditionalFieldPayload fieldDefinition : fieldDefinitions) {
            String rawValue = fieldValues.get(fieldDefinition.getFieldKey());
            String normalizedValue = normalizeAndValidateValue(fieldDefinition, rawValue);

            if (StringUtils.isBlank(normalizedValue)) {
                if (Boolean.TRUE.equals(fieldDefinition.getRequired())) {
                    throw new LIMSRuntimeException("Additional field is required: "
                            + StringUtils.defaultString(fieldDefinition.getDisplayName()));
                }
                continue;
            }

            SampleItemAdditionalFieldValue valueEntity = valueDAO
                    .findBySampleItemIdAndFieldDefinitionId(sampleItemNumericId, fieldDefinition.getId())
                    .orElseGet(SampleItemAdditionalFieldValue::new);
            valueEntity.setSampleItemId(sampleItemNumericId);
            valueEntity.setFieldDefinitionId(fieldDefinition.getId());
            valueEntity.setFieldValue(normalizedValue);
            valueEntity.setSysUserId(currentUserId);

            if (valueEntity.getId() == null) {
                valueDAO.insert(valueEntity);
            } else {
                valueDAO.update(valueEntity);
            }
        }
    }

    private List<SampleTypeAdditionalFieldPayload> mapDefinitionsToPayload(
            List<SampleTypeAdditionalFieldDefinition> definitions, List<SampleTypeAdditionalFieldOption> options) {

        Map<Integer, List<SampleTypeAdditionalFieldOptionPayload>> optionsByDefinitionId = new HashMap<>();
        for (SampleTypeAdditionalFieldOption option : options) {
            optionsByDefinitionId.computeIfAbsent(option.getFieldDefinitionId(), ignored -> new ArrayList<>())
                    .add(mapOptionToPayload(option));
        }

        List<SampleTypeAdditionalFieldPayload> payloads = new ArrayList<>();
        for (SampleTypeAdditionalFieldDefinition definition : definitions) {
            SampleTypeAdditionalFieldPayload payload = mapDefinitionToPayload(definition);
            payload.setOptions(optionsByDefinitionId.getOrDefault(definition.getId(), Collections.emptyList()));
            payloads.add(payload);
        }
        return payloads;
    }

    private SampleTypeAdditionalFieldPayload mapDefinitionToPayload(SampleTypeAdditionalFieldDefinition definition) {
        SampleTypeAdditionalFieldPayload payload = new SampleTypeAdditionalFieldPayload();
        payload.setId(definition.getId());
        payload.setSampleTypeId(String.valueOf(definition.getTypeOfSampleId()));
        payload.setFieldKey(definition.getFieldKey());
        payload.setDisplayName(definition.getDisplayName());
        payload.setFieldType(definition.getFieldType());
        payload.setDisplaySection(normalizeDisplaySection(definition.getDisplaySection()));
        payload.setRequired(definition.getRequired());
        payload.setActive(definition.getActive());
        payload.setSortOrder(definition.getSortOrder());
        payload.setDefaultValue(definition.getDefaultValue());
        payload.setMaxLength(definition.getMaxLength());
        payload.setMetadataJson(definition.getMetadataJson());
        return payload;
    }

    private SampleTypeAdditionalFieldOptionPayload mapOptionToPayload(SampleTypeAdditionalFieldOption option) {
        SampleTypeAdditionalFieldOptionPayload payload = new SampleTypeAdditionalFieldOptionPayload();
        payload.setId(option.getId());
        payload.setOptionKey(option.getOptionKey());
        payload.setOptionLabel(option.getOptionLabel());
        payload.setSortOrder(option.getSortOrder());
        payload.setActive(option.getActive());
        return payload;
    }

    private SampleTypeAdditionalFieldPayload getFieldById(Integer fieldId, boolean includeInactiveOptions) {
        SampleTypeAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        SampleTypeAdditionalFieldPayload payload = mapDefinitionToPayload(definition);
        List<SampleTypeAdditionalFieldOption> options = optionDAO.findByDefinitionId(fieldId, includeInactiveOptions);
        payload.setOptions(options.stream().map(this::mapOptionToPayload).collect(Collectors.toList()));
        return payload;
    }

    private void validatePayloadRequiredValues(SampleTypeAdditionalFieldPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Request payload is required");
        }
        if (StringUtils.isBlank(payload.getSampleTypeId())) {
            throw new IllegalArgumentException("sampleTypeId is required");
        }
        if (StringUtils.isBlank(payload.getDisplayName())) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (StringUtils.isBlank(payload.getFieldType())) {
            throw new IllegalArgumentException("fieldType is required");
        }
    }

    private Integer parseNumericId(String value, String fieldName) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be numeric");
        }
    }

    private String normalizeFieldKey(String fieldKey, String displayName) {
        String source = StringUtils.isNotBlank(fieldKey) ? fieldKey : displayName;
        String normalized = StringUtils.defaultString(source).trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_")
                .replaceAll("_+", "_");

        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return normalized;
    }

    private String normalizeDisplaySection(String displaySection) {
        if (displaySection == null || displaySection.trim().isEmpty()) {
            return "RECEPTION";
        }

        String normalized = displaySection.trim().toUpperCase();

        if ("COLLECTION".equals(normalized)) {
            return "COLLECTION";
        }

        return "RECEPTION";
    }

    private void validateFieldKey(String fieldKey) {
        if (StringUtils.isBlank(fieldKey)) {
            throw new IllegalArgumentException("fieldKey is required");
        }
        if (!FIELD_KEY_PATTERN.matcher(fieldKey).matches()) {
            throw new IllegalArgumentException(
                    "fieldKey must start with a letter and can contain letters, numbers, '-' and '_' only");
        }
    }

    private String normalizeOptionKey(String optionKey, String optionLabel) {
        String source = StringUtils.isNotBlank(optionKey) ? optionKey : optionLabel;
        String normalized = StringUtils.defaultString(source).trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_")
                .replaceAll("_+", "_");

        if (normalized.startsWith("_")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        if (StringUtils.isBlank(normalized)) {
            throw new IllegalArgumentException("optionKey is required");
        }
        return normalized;
    }

    private FieldType parseFieldType(String fieldType) {
        if (StringUtils.isBlank(fieldType)) {
            throw new IllegalArgumentException("fieldType is required");
        }

        try {
            return FieldType.valueOf(fieldType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported fieldType: " + fieldType);
        }
    }

    private boolean isOptionFieldType(FieldType fieldType) {
        return OPTION_TYPES.contains(fieldType);
    }

    private Integer getNextSortOrder(Integer sampleTypeId) {
        List<SampleTypeAdditionalFieldDefinition> existing = definitionDAO.findBySampleTypeId(sampleTypeId, true);
        return existing.stream().map(SampleTypeAdditionalFieldDefinition::getSortOrder).filter(value -> value != null)
                .max(Integer::compareTo).map(max -> max + 1).orElse(1);
    }

    private Integer getNextOptionSortOrder(Integer fieldId) {
        List<SampleTypeAdditionalFieldOption> existing = optionDAO.findByDefinitionId(fieldId, true);
        return existing.stream().map(SampleTypeAdditionalFieldOption::getSortOrder).filter(value -> value != null)
                .max(Integer::compareTo).map(max -> max + 1).orElse(1);
    }

    private void saveOptionsForDefinition(Integer definitionId, List<SampleTypeAdditionalFieldOptionPayload> options,
            FieldType fieldType, String currentUserId) {
        if (!isOptionFieldType(fieldType) || options == null || options.isEmpty()) {
            return;
        }

        int fallbackSortOrder = 1;
        Set<String> uniqueOptionKeys = new HashSet<>();
        for (SampleTypeAdditionalFieldOptionPayload optionPayload : options) {
            if (optionPayload == null || StringUtils.isBlank(optionPayload.getOptionLabel())) {
                continue;
            }

            String normalizedOptionKey = normalizeOptionKey(optionPayload.getOptionKey(),
                    optionPayload.getOptionLabel());
            if (!uniqueOptionKeys.add(normalizedOptionKey)) {
                throw new IllegalArgumentException("Duplicate option key in request payload: " + normalizedOptionKey);
            }

            SampleTypeAdditionalFieldOption option = new SampleTypeAdditionalFieldOption();
            option.setFieldDefinitionId(definitionId);
            option.setOptionKey(normalizedOptionKey);
            option.setOptionLabel(optionPayload.getOptionLabel().trim());
            option.setSortOrder(
                    optionPayload.getSortOrder() == null ? fallbackSortOrder : optionPayload.getSortOrder());
            option.setActive(optionPayload.getActive() == null || optionPayload.getActive());
            option.setSysUserId(currentUserId);
            optionDAO.insert(option);

            fallbackSortOrder++;
        }
    }

    private void upsertOptionsForDefinition(Integer definitionId, List<SampleTypeAdditionalFieldOptionPayload> options,
            String currentUserId) {
        if (options == null) {
            return;
        }

        Set<Integer> updatedOptionIds = new HashSet<>();
        for (SampleTypeAdditionalFieldOptionPayload optionPayload : options) {
            if (optionPayload == null || StringUtils.isBlank(optionPayload.getOptionLabel())) {
                continue;
            }

            if (optionPayload.getId() != null) {
                SampleTypeAdditionalFieldOption existing = optionDAO.get(optionPayload.getId()).orElseThrow(
                        () -> new IllegalArgumentException("Option not found for update: " + optionPayload.getId()));

                existing.setOptionKey(normalizeOptionKey(optionPayload.getOptionKey(), optionPayload.getOptionLabel()));
                existing.setOptionLabel(optionPayload.getOptionLabel().trim());
                if (optionPayload.getSortOrder() != null) {
                    existing.setSortOrder(optionPayload.getSortOrder());
                }
                if (optionPayload.getActive() != null) {
                    existing.setActive(optionPayload.getActive());
                }
                existing.setSysUserId(currentUserId);
                optionDAO.update(existing);
                updatedOptionIds.add(existing.getId());
                continue;
            }

            SampleTypeAdditionalFieldOptionPayload created = createOption(definitionId, optionPayload, currentUserId);
            updatedOptionIds.add(created.getId());
        }

        List<SampleTypeAdditionalFieldOption> existingOptions = optionDAO.findByDefinitionId(definitionId, true);
        for (SampleTypeAdditionalFieldOption existingOption : existingOptions) {
            if (!updatedOptionIds.contains(existingOption.getId())) {
                existingOption.setActive(false);
                existingOption.setSysUserId(currentUserId);
                optionDAO.update(existingOption);
            }
        }
    }

    private String normalizeAndValidateValue(SampleTypeAdditionalFieldPayload fieldDefinition, String rawValue) {
        String trimmedValue = StringUtils.trimToNull(rawValue);
        if (trimmedValue == null) {
            return null;
        }

        FieldType fieldType = parseFieldType(fieldDefinition.getFieldType());

        switch (fieldType) {
        case TEXT:
        case TEXTAREA:
            validateMaxLength(fieldDefinition, trimmedValue);
            return trimmedValue;
        case NUMBER:
            validateNumber(fieldDefinition, trimmedValue);
            return trimmedValue;
        case DATE:
            validateDate(trimmedValue);
            return trimmedValue;
        case TIME:
            validateTime(trimmedValue);
            return trimmedValue;
        case DATETIME:
            validateDateTime(trimmedValue);
            return trimmedValue;
        case BOOLEAN:
            validateBoolean(trimmedValue);
            return trimmedValue.toLowerCase();
        case SELECT:
        case RADIO:
            validateSingleOption(fieldDefinition, trimmedValue);
            return trimmedValue;
        case MULTISELECT:
            return normalizeAndValidateMultiSelect(fieldDefinition, trimmedValue);
        default:
            return trimmedValue;
        }
    }

    private void validateMaxLength(SampleTypeAdditionalFieldPayload fieldDefinition, String value) {
        Integer maxLength = fieldDefinition.getMaxLength();
        if (maxLength != null && maxLength > 0 && value.length() > maxLength) {
            throw new LIMSRuntimeException(
                    "Value exceeds maximum length for field: " + fieldDefinition.getDisplayName());
        }
    }

    private void validateNumber(SampleTypeAdditionalFieldPayload fieldDefinition, String value) {
        try {
            new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new LIMSRuntimeException("Invalid number for field: " + fieldDefinition.getDisplayName());
        }
    }

    private void validateDate(String value) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new LIMSRuntimeException("Invalid date format. Expected yyyy-MM-dd");
        }
    }

    private void validateDateTime(String value) {
        List<DateTimeFormatter> acceptedFormats = List.of(DateTimeFormatter.ISO_LOCAL_DATE_TIME, DATE_TIME_SECONDS,
                DATE_TIME_MINUTES, DATE_TIME_WITH_SPACE_SECONDS);

        for (DateTimeFormatter acceptedFormat : acceptedFormats) {
            try {
                LocalDateTime.parse(value, acceptedFormat);
                return;
            } catch (DateTimeParseException e) {
                // try next format
            }
        }

        if (value.contains(" ")) {
            String replaced = value.replace(' ', 'T');
            for (DateTimeFormatter acceptedFormat : acceptedFormats) {
                try {
                    LocalDateTime.parse(replaced, acceptedFormat);
                    return;
                } catch (DateTimeParseException e) {
                    // try next format
                }
            }
        }

        throw new LIMSRuntimeException("Invalid datetime format. Expected yyyy-MM-ddTHH:mm or yyyy-MM-ddTHH:mm:ss");
    }

    private void validateTime(String value) {
        List<DateTimeFormatter> acceptedFormats = List.of(DateTimeFormatter.ofPattern("HH:mm"),
                DateTimeFormatter.ofPattern("HH:mm:ss"));
        for (DateTimeFormatter acceptedFormat : acceptedFormats) {
            try {
                LocalTime.parse(value, acceptedFormat);
                return;
            } catch (DateTimeParseException e) {
                // try next format
            }
        }
        throw new LIMSRuntimeException("Invalid time format. Expected HH:mm or HH:mm:ss");
    }

    private void validateBoolean(String value) {
        if (!("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            throw new LIMSRuntimeException("Invalid boolean value. Expected true or false");
        }
    }

    private void validateSingleOption(SampleTypeAdditionalFieldPayload fieldDefinition, String value) {
        Set<String> validOptions = fieldDefinition.getOptions().stream()
                .filter(option -> Boolean.TRUE.equals(option.getActive()))
                .map(SampleTypeAdditionalFieldOptionPayload::getOptionKey).collect(Collectors.toSet());

        if (!validOptions.contains(value)) {
            throw new LIMSRuntimeException("Invalid option selected for field: " + fieldDefinition.getDisplayName());
        }
    }

    private String normalizeAndValidateMultiSelect(SampleTypeAdditionalFieldPayload fieldDefinition, String value) {
        Set<String> validOptions = fieldDefinition.getOptions().stream()
                .filter(option -> Boolean.TRUE.equals(option.getActive()))
                .map(SampleTypeAdditionalFieldOptionPayload::getOptionKey).collect(Collectors.toSet());

        LinkedHashSet<String> normalizedOptions = new LinkedHashSet<>();
        for (String option : value.split(",")) {
            String trimmed = StringUtils.trimToNull(option);
            if (trimmed == null) {
                continue;
            }

            if (!validOptions.contains(trimmed)) {
                throw new LIMSRuntimeException(
                        "Invalid option selected for field: " + fieldDefinition.getDisplayName());
            }
            normalizedOptions.add(trimmed);
        }

        return normalizedOptions.isEmpty() ? null : String.join(",", normalizedOptions);
    }
}
