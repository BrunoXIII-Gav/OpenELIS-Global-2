package org.openelisglobal.testadditionalfield.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldOptionPayload;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.dao.AnalysisAdditionalFieldValueDAO;
import org.openelisglobal.testadditionalfield.dao.TestAdditionalFieldDefinitionDAO;
import org.openelisglobal.testadditionalfield.dao.TestAdditionalFieldOptionDAO;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition.FieldType;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TestAdditionalFieldServiceImpl implements TestAdditionalFieldService {

    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_\\-]{1,79}$");
    private static final DateTimeFormatter DATE_TIME_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_WITH_SPACE_SECONDS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Set<FieldType> OPTION_TYPES = Set.of(FieldType.SELECT, FieldType.RADIO, FieldType.MULTISELECT);

    @Autowired
    private TestAdditionalFieldDefinitionDAO definitionDAO;

    @Autowired
    private TestAdditionalFieldOptionDAO optionDAO;

    @Autowired
    private AnalysisAdditionalFieldValueDAO valueDAO;

    @Override
    @Transactional(readOnly = true)
    public List<TestAdditionalFieldPayload> getFieldsForTest(String testId, boolean includeInactive) {
        Integer numericTestId = parseNumericId(testId, "testId");
        List<TestAdditionalFieldDefinition> definitions = definitionDAO.findByTestId(numericTestId, includeInactive);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> definitionIds = definitions.stream().map(TestAdditionalFieldDefinition::getId)
                .collect(Collectors.toList());
        List<TestAdditionalFieldOption> options = optionDAO.findByDefinitionIds(definitionIds, !includeInactive);

        return mapDefinitionsToPayload(definitions, options);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, List<TestAdditionalFieldPayload>> getActiveFieldsForTests(List<String> testIds) {
        Map<String, List<TestAdditionalFieldPayload>> result = new HashMap<>();
        if (testIds == null || testIds.isEmpty()) {
            return result;
        }

        List<Integer> numericIds = new ArrayList<>();
        for (String testId : new LinkedHashSet<>(testIds)) {
            if (StringUtils.isBlank(testId)) {
                continue;
            }
            Integer numericId = parseNumericId(testId, "testId");
            numericIds.add(numericId);
            result.put(testId, new ArrayList<>());
        }

        if (numericIds.isEmpty()) {
            return result;
        }

        List<TestAdditionalFieldDefinition> definitions = definitionDAO.findByTestIds(numericIds, true);
        if (definitions.isEmpty()) {
            return result;
        }

        List<Integer> definitionIds = definitions.stream().map(TestAdditionalFieldDefinition::getId)
                .collect(Collectors.toList());
        List<TestAdditionalFieldOption> options = optionDAO.findByDefinitionIds(definitionIds, true);

        Map<Integer, List<TestAdditionalFieldPayload>> byTestId = mapDefinitionsToPayload(definitions, options).stream()
                .collect(Collectors.groupingBy(payload -> Integer.valueOf(payload.getTestId())));

        for (Integer numericId : numericIds) {
            String key = String.valueOf(numericId);
            result.put(key, byTestId.getOrDefault(numericId, Collections.emptyList()));
        }

        return result;
    }

    @Override
    public void replaceFieldsForTest(String testId, List<TestAdditionalFieldPayload> payloads, String currentUserId) {
        Integer numericTestId = parseNumericId(testId, "testId");
        List<TestAdditionalFieldDefinition> existingDefinitions = definitionDAO.findByTestId(numericTestId, true);

        Map<Integer, TestAdditionalFieldDefinition> existingById = existingDefinitions.stream()
                .collect(Collectors.toMap(TestAdditionalFieldDefinition::getId, d -> d));
        Map<String, TestAdditionalFieldDefinition> existingByFieldKey = existingDefinitions.stream()
                .collect(Collectors.toMap(d -> d.getFieldKey().toLowerCase(), d -> d, (left, right) -> left));

        Set<Integer> touchedDefinitionIds = new HashSet<>();
        Set<String> payloadFieldKeys = new HashSet<>();

        int fallbackSortOrder = 1;
        List<TestAdditionalFieldPayload> safePayloads = payloads == null ? Collections.emptyList() : payloads;
        for (TestAdditionalFieldPayload payload : safePayloads) {
            if (payload == null || StringUtils.isBlank(payload.getDisplayName())) {
                continue;
            }

            String normalizedFieldKey = normalizeFieldKey(payload.getFieldKey(), payload.getDisplayName());
            validateFieldKey(normalizedFieldKey);
            if (!payloadFieldKeys.add(normalizedFieldKey)) {
                throw new IllegalArgumentException("Duplicate field key in payload: " + normalizedFieldKey);
            }

            TestAdditionalFieldDefinition definition = null;
            if (payload.getId() != null) {
                definition = existingById.get(payload.getId());
            }
            if (definition == null) {
                definition = existingByFieldKey.get(normalizedFieldKey.toLowerCase());
            }
            if (definition == null) {
                definition = new TestAdditionalFieldDefinition();
                definition.setTestId(numericTestId);
            }

            FieldType fieldType = parseFieldType(
                    StringUtils.defaultIfBlank(payload.getFieldType(), FieldType.TEXT.name()));
            definition.setFieldKey(normalizedFieldKey);
            definition.setDisplayName(payload.getDisplayName().trim());
            definition.setFieldType(fieldType.name());
            definition.setRequired(Boolean.TRUE.equals(payload.getRequired()));
            definition.setActive(payload.getActive() == null || payload.getActive());
            definition.setSortOrder(
                    payload.getSortOrder() == null ? fallbackSortOrder : Math.max(payload.getSortOrder(), 0));
            definition.setDefaultValue(StringUtils.defaultIfBlank(payload.getDefaultValue(), null));
            definition.setMaxLength(payload.getMaxLength());
            definition.setMetadataJson(StringUtils.defaultIfBlank(payload.getMetadataJson(), null));
            definition.setSysUserId(currentUserId);

            if (definition.getId() == null) {
                Integer createdId = definitionDAO.insert(definition);
                definition.setId(createdId);
            } else {
                definitionDAO.update(definition);
            }

            if (isOptionFieldType(fieldType)) {
                upsertOptionsForDefinition(definition.getId(), payload.getOptions(), currentUserId);
            } else {
                deactivateAllOptions(definition.getId(), currentUserId);
            }

            touchedDefinitionIds.add(definition.getId());
            fallbackSortOrder++;
        }

        for (TestAdditionalFieldDefinition existingDefinition : existingDefinitions) {
            if (!touchedDefinitionIds.contains(existingDefinition.getId())) {
                existingDefinition.setActive(false);
                existingDefinition.setSysUserId(currentUserId);
                definitionDAO.update(existingDefinition);
                deactivateAllOptions(existingDefinition.getId(), currentUserId);
            }
        }
    }

    @Override
    public void replaceFieldsForTests(List<String> testIds, List<TestAdditionalFieldPayload> payloads,
            String currentUserId) {
        if (testIds == null || testIds.isEmpty()) {
            return;
        }

        for (String testId : new LinkedHashSet<>(testIds)) {
            if (StringUtils.isBlank(testId)) {
                continue;
            }
            replaceFieldsForTest(testId, payloads, currentUserId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getAnalysisValuesForFields(String analysisId,
            List<TestAdditionalFieldPayload> fieldDefinitions) {
        if (fieldDefinitions == null || fieldDefinitions.isEmpty() || StringUtils.isBlank(analysisId)) {
            return Collections.emptyMap();
        }

        Integer numericAnalysisId = parseNumericId(analysisId, "analysisId");
        List<Integer> fieldDefIds = fieldDefinitions.stream().map(TestAdditionalFieldPayload::getId)
                .filter(id -> id != null).collect(Collectors.toList());
        if (fieldDefIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, String> fieldKeyById = fieldDefinitions.stream().filter(def -> def.getId() != null)
                .collect(Collectors.toMap(TestAdditionalFieldPayload::getId, TestAdditionalFieldPayload::getFieldKey,
                        (left, right) -> left));

        Map<String, String> valuesByFieldKey = new HashMap<>();
        List<AnalysisAdditionalFieldValue> entities = valueDAO.findByAnalysisIdAndFieldDefinitionIds(numericAnalysisId,
                fieldDefIds);
        for (AnalysisAdditionalFieldValue valueEntity : entities) {
            String fieldKey = fieldKeyById.get(valueEntity.getFieldDefinitionId());
            if (StringUtils.isNotBlank(fieldKey)) {
                valuesByFieldKey.put(fieldKey, valueEntity.getFieldValue());
            }
        }

        return valuesByFieldKey;
    }

    @Override
    public void validateAndPersistAnalysisValues(String testId, String analysisId, Map<String, String> fieldValues,
            String currentUserId, Map<String, List<TestAdditionalFieldPayload>> activeFieldsByTestCache) {
        List<TestAdditionalFieldPayload> fieldDefinitions = null;
        if (activeFieldsByTestCache != null) {
            fieldDefinitions = activeFieldsByTestCache.get(testId);
            if (fieldDefinitions == null && StringUtils.isNotBlank(testId)) {
                fieldDefinitions = activeFieldsByTestCache.get(String.valueOf(parseNumericId(testId, "testId")));
            }
        }

        if (fieldDefinitions == null) {
            fieldDefinitions = getFieldsForTest(testId, false);
        }

        if (fieldDefinitions == null || fieldDefinitions.isEmpty()) {
            return;
        }

        Integer analysisNumericId = parseNumericId(analysisId, "analysisId");
        Set<String> knownFieldKeys = fieldDefinitions.stream().map(TestAdditionalFieldPayload::getFieldKey)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());

        if (fieldValues != null) {
            for (String providedKey : fieldValues.keySet()) {
                if (!knownFieldKeys.contains(providedKey)) {
                    throw new IllegalArgumentException(
                            "Unknown additional field key for test " + testId + ": " + providedKey);
                }
            }
        }

        for (TestAdditionalFieldPayload fieldDefinition : fieldDefinitions) {
            Optional<AnalysisAdditionalFieldValue> existingValue = valueDAO
                    .findByAnalysisIdAndFieldDefinitionId(analysisNumericId, fieldDefinition.getId());

            boolean keyProvided = fieldValues != null && fieldValues.containsKey(fieldDefinition.getFieldKey());
            String rawValue = keyProvided ? fieldValues.get(fieldDefinition.getFieldKey()) : null;
            String normalizedValue = normalizeAndValidateValue(fieldDefinition, rawValue);

            if (!keyProvided && existingValue.isPresent()) {
                normalizedValue = StringUtils.trimToNull(existingValue.get().getFieldValue());
            }

            if (normalizedValue == null && StringUtils.isNotBlank(fieldDefinition.getDefaultValue())) {
                normalizedValue = normalizeAndValidateValue(fieldDefinition, fieldDefinition.getDefaultValue());
            }

            if (normalizedValue == null) {
                if (Boolean.TRUE.equals(fieldDefinition.getRequired())) {
                    throw new LIMSRuntimeException("Additional field is required: "
                            + StringUtils.defaultString(fieldDefinition.getDisplayName()));
                }

                if (keyProvided && existingValue.isPresent()) {
                    AnalysisAdditionalFieldValue existing = existingValue.get();
                    existing.setFieldValue("");
                    existing.setSysUserId(currentUserId);
                    valueDAO.update(existing);
                }
                continue;
            }

            AnalysisAdditionalFieldValue valueEntity = existingValue.orElseGet(AnalysisAdditionalFieldValue::new);
            valueEntity.setAnalysisId(analysisNumericId);
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

    @Override
    public TestAdditionalFieldPayload createField(TestAdditionalFieldPayload payload, String currentUserId) {
        validatePayloadRequiredValues(payload);
        Integer testNumericId = parseNumericId(payload.getTestId(), "testId");
        String normalizedFieldKey = normalizeFieldKey(payload.getFieldKey(), payload.getDisplayName());
        validateFieldKey(normalizedFieldKey);

        Optional<TestAdditionalFieldDefinition> existing = definitionDAO.findByTestIdAndFieldKey(testNumericId,
                normalizedFieldKey);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Field key already exists for selected test: " + normalizedFieldKey);
        }

        FieldType fieldType = parseFieldType(payload.getFieldType());
        TestAdditionalFieldDefinition definition = new TestAdditionalFieldDefinition();
        definition.setTestId(testNumericId);
        definition.setFieldKey(normalizedFieldKey);
        definition.setDisplayName(payload.getDisplayName().trim());
        definition.setFieldType(fieldType.name());
        definition.setRequired(Boolean.TRUE.equals(payload.getRequired()));
        definition.setActive(payload.getActive() == null || payload.getActive());
        definition.setSortOrder(
                payload.getSortOrder() == null ? getNextSortOrder(testNumericId) : Math.max(payload.getSortOrder(), 0));
        definition.setDefaultValue(StringUtils.defaultIfBlank(payload.getDefaultValue(), null));
        definition.setMaxLength(payload.getMaxLength());
        definition.setMetadataJson(StringUtils.defaultIfBlank(payload.getMetadataJson(), null));
        definition.setSysUserId(currentUserId);

        Integer definitionId = definitionDAO.insert(definition);
        saveOptionsForDefinition(definitionId, payload.getOptions(), fieldType, currentUserId);

        return getFieldById(definitionId, true);
    }

    @Override
    public TestAdditionalFieldPayload updateField(Integer fieldId, TestAdditionalFieldPayload payload,
            String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }

        TestAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));

        if (StringUtils.isNotBlank(payload.getDisplayName())) {
            definition.setDisplayName(payload.getDisplayName().trim());
        }

        if (payload.getFieldType() != null) {
            FieldType fieldType = parseFieldType(payload.getFieldType());
            definition.setFieldType(fieldType.name());

            if (!isOptionFieldType(fieldType)) {
                deactivateAllOptions(fieldId, currentUserId);
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

        TestAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        definition.setActive(false);
        definition.setSysUserId(currentUserId);
        definitionDAO.update(definition);

        deactivateAllOptions(fieldId, currentUserId);
    }

    @Override
    public TestAdditionalFieldOptionPayload createOption(Integer fieldId, TestAdditionalFieldOptionPayload payload,
            String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }
        if (payload == null || StringUtils.isBlank(payload.getOptionLabel())) {
            throw new IllegalArgumentException("optionLabel is required");
        }

        TestAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));

        FieldType fieldType = parseFieldType(definition.getFieldType());
        if (!isOptionFieldType(fieldType)) {
            throw new IllegalArgumentException("Options can only be added for SELECT, RADIO, or MULTISELECT fields");
        }

        String normalizedOptionKey = normalizeOptionKey(payload.getOptionKey(), payload.getOptionLabel());

        Optional<TestAdditionalFieldOption> duplicate = optionDAO.findByDefinitionIdAndOptionKey(fieldId,
                normalizedOptionKey);
        if (duplicate.isPresent()) {
            throw new IllegalArgumentException("Option key already exists for selected field: " + normalizedOptionKey);
        }

        TestAdditionalFieldOption option = new TestAdditionalFieldOption();
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
    public TestAdditionalFieldOptionPayload updateOption(Integer optionId, TestAdditionalFieldOptionPayload payload,
            String currentUserId) {
        if (optionId == null) {
            throw new IllegalArgumentException("optionId is required");
        }

        TestAdditionalFieldOption option = optionDAO.get(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));

        if (payload.getOptionKey() != null) {
            String normalizedOptionKey = normalizeOptionKey(payload.getOptionKey(), payload.getOptionLabel());
            Optional<TestAdditionalFieldOption> duplicate = optionDAO
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

        TestAdditionalFieldOption option = optionDAO.get(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
        option.setActive(false);
        option.setSysUserId(currentUserId);
        optionDAO.update(option);
    }

    private List<TestAdditionalFieldPayload> mapDefinitionsToPayload(List<TestAdditionalFieldDefinition> definitions,
            List<TestAdditionalFieldOption> options) {

        Map<Integer, List<TestAdditionalFieldOptionPayload>> optionsByDefinitionId = new HashMap<>();
        for (TestAdditionalFieldOption option : options) {
            optionsByDefinitionId.computeIfAbsent(option.getFieldDefinitionId(), ignored -> new ArrayList<>())
                    .add(mapOptionToPayload(option));
        }

        List<TestAdditionalFieldPayload> payloads = new ArrayList<>();
        for (TestAdditionalFieldDefinition definition : definitions) {
            TestAdditionalFieldPayload payload = mapDefinitionToPayload(definition);
            payload.setOptions(optionsByDefinitionId.getOrDefault(definition.getId(), Collections.emptyList()));
            payloads.add(payload);
        }
        return payloads;
    }

    private TestAdditionalFieldPayload mapDefinitionToPayload(TestAdditionalFieldDefinition definition) {
        TestAdditionalFieldPayload payload = new TestAdditionalFieldPayload();
        payload.setId(definition.getId());
        payload.setTestId(String.valueOf(definition.getTestId()));
        payload.setFieldKey(definition.getFieldKey());
        payload.setDisplayName(definition.getDisplayName());
        payload.setFieldType(definition.getFieldType());
        payload.setRequired(definition.getRequired());
        payload.setActive(definition.getActive());
        payload.setSortOrder(definition.getSortOrder());
        payload.setDefaultValue(definition.getDefaultValue());
        payload.setMaxLength(definition.getMaxLength());
        payload.setMetadataJson(definition.getMetadataJson());
        return payload;
    }

    private TestAdditionalFieldOptionPayload mapOptionToPayload(TestAdditionalFieldOption option) {
        TestAdditionalFieldOptionPayload payload = new TestAdditionalFieldOptionPayload();
        payload.setId(option.getId());
        payload.setOptionKey(option.getOptionKey());
        payload.setOptionLabel(option.getOptionLabel());
        payload.setSortOrder(option.getSortOrder());
        payload.setActive(option.getActive());
        return payload;
    }

    private TestAdditionalFieldPayload getFieldById(Integer fieldId, boolean includeInactiveOptions) {
        TestAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        TestAdditionalFieldPayload payload = mapDefinitionToPayload(definition);
        List<TestAdditionalFieldOption> options = optionDAO.findByDefinitionId(fieldId, includeInactiveOptions);
        payload.setOptions(options.stream().map(this::mapOptionToPayload).collect(Collectors.toList()));
        return payload;
    }

    private void validatePayloadRequiredValues(TestAdditionalFieldPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Request payload is required");
        }
        if (StringUtils.isBlank(payload.getTestId())) {
            throw new IllegalArgumentException("testId is required");
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

    private Integer getNextSortOrder(Integer testId) {
        List<TestAdditionalFieldDefinition> existing = definitionDAO.findByTestId(testId, true);
        return existing.stream().map(TestAdditionalFieldDefinition::getSortOrder).filter(value -> value != null)
                .max(Integer::compareTo).map(max -> max + 1).orElse(1);
    }

    private Integer getNextOptionSortOrder(Integer fieldId) {
        List<TestAdditionalFieldOption> existing = optionDAO.findByDefinitionId(fieldId, true);
        return existing.stream().map(TestAdditionalFieldOption::getSortOrder).filter(value -> value != null)
                .max(Integer::compareTo).map(max -> max + 1).orElse(1);
    }

    private void saveOptionsForDefinition(Integer definitionId, List<TestAdditionalFieldOptionPayload> options,
            FieldType fieldType, String currentUserId) {
        if (!isOptionFieldType(fieldType) || options == null || options.isEmpty()) {
            return;
        }

        int fallbackSortOrder = 1;
        Set<String> uniqueOptionKeys = new HashSet<>();
        for (TestAdditionalFieldOptionPayload optionPayload : options) {
            if (optionPayload == null || StringUtils.isBlank(optionPayload.getOptionLabel())) {
                continue;
            }

            String normalizedOptionKey = normalizeOptionKey(optionPayload.getOptionKey(),
                    optionPayload.getOptionLabel());
            if (!uniqueOptionKeys.add(normalizedOptionKey)) {
                throw new IllegalArgumentException("Duplicate option key in request payload: " + normalizedOptionKey);
            }

            TestAdditionalFieldOption option = new TestAdditionalFieldOption();
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

    private void upsertOptionsForDefinition(Integer definitionId, List<TestAdditionalFieldOptionPayload> options,
            String currentUserId) {
        List<TestAdditionalFieldOptionPayload> safeOptions = options == null ? Collections.emptyList() : options;
        Set<Integer> updatedOptionIds = new HashSet<>();
        Set<String> optionKeysInPayload = new HashSet<>();

        for (TestAdditionalFieldOptionPayload optionPayload : safeOptions) {
            if (optionPayload == null || StringUtils.isBlank(optionPayload.getOptionLabel())) {
                continue;
            }

            String normalizedOptionKey = normalizeOptionKey(optionPayload.getOptionKey(),
                    optionPayload.getOptionLabel());
            if (!optionKeysInPayload.add(normalizedOptionKey)) {
                throw new IllegalArgumentException("Duplicate option key in request payload: " + normalizedOptionKey);
            }

            if (optionPayload.getId() != null) {
                TestAdditionalFieldOption existing = optionDAO.get(optionPayload.getId()).orElseThrow(
                        () -> new IllegalArgumentException("Option not found for update: " + optionPayload.getId()));

                existing.setOptionKey(normalizedOptionKey);
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

            TestAdditionalFieldOptionPayload created = createOption(definitionId, optionPayload, currentUserId);
            updatedOptionIds.add(created.getId());
        }

        List<TestAdditionalFieldOption> existingOptions = optionDAO.findByDefinitionId(definitionId, true);
        for (TestAdditionalFieldOption existingOption : existingOptions) {
            if (!updatedOptionIds.contains(existingOption.getId())) {
                existingOption.setActive(false);
                existingOption.setSysUserId(currentUserId);
                optionDAO.update(existingOption);
            }
        }
    }

    private void deactivateAllOptions(Integer definitionId, String currentUserId) {
        List<TestAdditionalFieldOption> existingOptions = optionDAO.findByDefinitionId(definitionId, true);
        for (TestAdditionalFieldOption option : existingOptions) {
            option.setActive(false);
            option.setSysUserId(currentUserId);
            optionDAO.update(option);
        }
    }

    private String normalizeAndValidateValue(TestAdditionalFieldPayload fieldDefinition, String rawValue) {
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

    private void validateMaxLength(TestAdditionalFieldPayload fieldDefinition, String value) {
        Integer maxLength = fieldDefinition.getMaxLength();
        if (maxLength != null && maxLength > 0 && value.length() > maxLength) {
            throw new LIMSRuntimeException(
                    "Value exceeds maximum length for field: " + fieldDefinition.getDisplayName());
        }
    }

    private void validateNumber(TestAdditionalFieldPayload fieldDefinition, String value) {
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

    private void validateBoolean(String value) {
        if (!("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            throw new LIMSRuntimeException("Invalid boolean value. Expected true or false");
        }
    }

    private void validateSingleOption(TestAdditionalFieldPayload fieldDefinition, String value) {
        Set<String> validOptions = fieldDefinition.getOptions().stream()
                .filter(option -> Boolean.TRUE.equals(option.getActive()))
                .map(TestAdditionalFieldOptionPayload::getOptionKey).collect(Collectors.toSet());

        if (!validOptions.contains(value)) {
            throw new LIMSRuntimeException("Invalid option selected for field: " + fieldDefinition.getDisplayName());
        }
    }

    private String normalizeAndValidateMultiSelect(TestAdditionalFieldPayload fieldDefinition, String value) {
        Set<String> validOptions = fieldDefinition.getOptions().stream()
                .filter(option -> Boolean.TRUE.equals(option.getActive()))
                .map(TestAdditionalFieldOptionPayload::getOptionKey).collect(Collectors.toSet());

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
