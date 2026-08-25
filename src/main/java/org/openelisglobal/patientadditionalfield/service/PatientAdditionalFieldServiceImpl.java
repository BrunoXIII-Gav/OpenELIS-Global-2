package org.openelisglobal.patientadditionalfield.service;

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
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldOptionPayload;
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldPayload;
import org.openelisglobal.patientadditionalfield.bean.PatientFixedFieldConfigPayload;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldDefinitionDAO;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldOptionDAO;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldValueDAO;
import org.openelisglobal.patientadditionalfield.dao.PatientFixedFieldConfigDAO;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldDefinition;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldDefinition.FieldType;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldOption;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldValue;
import org.openelisglobal.patientadditionalfield.valueholder.PatientFixedFieldConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PatientAdditionalFieldServiceImpl implements PatientAdditionalFieldService {

    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_\\-]{1,79}$");
    private static final DateTimeFormatter DATE_TIME_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_WITH_SPACE_SECONDS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<FieldType> OPTION_TYPES = Set.of(FieldType.SELECT, FieldType.RADIO, FieldType.MULTISELECT);
    private static final List<FixedFieldDefault> FIXED_FIELD_DEFAULTS = List.of(
            new FixedFieldDefault("photo", 10, false, false),
            new FixedFieldDefault("subjectNumber", 20, false, false),
            new FixedFieldDefault("nationalId", 30, false, true),
            new FixedFieldDefault("optionalIdentifiers", 40, false, false),
            new FixedFieldDefault("lastName", 50, false, false),
            new FixedFieldDefault("firstName", 60, false, false),
            new FixedFieldDefault("primaryPhone", 70, false, false),
            new FixedFieldDefault("email", 80, false, false),
            new FixedFieldDefault("gender", 90, true, false),
            new FixedFieldDefault("birthDateAge", 100, true, false),
            new FixedFieldDefault("emergencyContact", 110, false, false),
            new FixedFieldDefault("additionalInfo", 120, false, false));
    private static final Set<String> FIXED_FIELD_KEYS = FIXED_FIELD_DEFAULTS.stream().map(f -> f.fieldKey.toLowerCase())
            .collect(Collectors.toSet());

    @Autowired
    private PatientAdditionalFieldDefinitionDAO definitionDAO;

    @Autowired
    private PatientAdditionalFieldOptionDAO optionDAO;

    @Autowired
    private PatientAdditionalFieldValueDAO valueDAO;

    @Autowired
    private PatientFixedFieldConfigDAO fixedFieldConfigDAO;

    @Autowired
    private UserFieldOptionResolver userFieldOptionResolver;

    @Override
    @Transactional(readOnly = true)
    public List<PatientAdditionalFieldPayload> getFields(boolean includeInactive, boolean resolveUserOptions) {
        List<PatientAdditionalFieldDefinition> definitions = definitionDAO.findAll(!includeInactive);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> definitionIds = definitions.stream().map(PatientAdditionalFieldDefinition::getId)
                .collect(Collectors.toList());
        List<PatientAdditionalFieldOption> options = optionDAO.findByDefinitionIds(definitionIds, !includeInactive);
        return mapDefinitionsToPayload(definitions, options, resolveUserOptions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientFixedFieldConfigPayload> getFixedFieldConfigs() {
        List<PatientFixedFieldConfig> configured = fixedFieldConfigDAO.findAllOrdered();
        Map<String, PatientFixedFieldConfig> byKey = configured.stream()
                .collect(Collectors.toMap(c -> c.getFieldKey().toLowerCase(), c -> c, (left, right) -> left));

        List<PatientFixedFieldConfigPayload> result = new ArrayList<>();
        for (FixedFieldDefault fixedFieldDefault : FIXED_FIELD_DEFAULTS) {
            PatientFixedFieldConfig config = byKey.get(fixedFieldDefault.fieldKey.toLowerCase());
            if (config == null) {
                PatientFixedFieldConfigPayload fallback = new PatientFixedFieldConfigPayload();
                fallback.setFieldKey(fixedFieldDefault.fieldKey);
                fallback.setVisible(true);
                fallback.setRequired(fixedFieldDefault.required);
                fallback.setReadonly(fixedFieldDefault.readonly);
                fallback.setSortOrder(fixedFieldDefault.sortOrder);
                result.add(fallback);
            } else {
                result.add(mapFixedFieldToPayload(config));
            }
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getPatientValues(String patientId, List<PatientAdditionalFieldPayload> fieldDefinitions) {
        Integer numericPatientId = parseNumericId(patientId, "patientId");
        List<PatientAdditionalFieldPayload> definitions = fieldDefinitions == null ? getFields(false, true)
                : fieldDefinitions;
        if (definitions.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> fieldKeyByDefinitionId = definitions.stream()
                .collect(Collectors.toMap(PatientAdditionalFieldPayload::getId, PatientAdditionalFieldPayload::getFieldKey,
                        (left, right) -> left));
        Map<String, String> valuesByFieldKey = new HashMap<>();
        for (PatientAdditionalFieldValue savedValue : valueDAO.findByPatientId(numericPatientId)) {
            String fieldKey = fieldKeyByDefinitionId.get(savedValue.getFieldDefinitionId());
            if (StringUtils.isBlank(fieldKey)) {
                continue;
            }
            valuesByFieldKey.put(fieldKey, StringUtils.defaultString(savedValue.getFieldValue()));
        }
        return valuesByFieldKey;
    }

    @Override
    public PatientAdditionalFieldPayload createField(PatientAdditionalFieldPayload payload, String currentUserId) {
        validatePayloadRequiredValues(payload);
        String normalizedFieldKey = normalizeFieldKey(payload.getFieldKey(), payload.getDisplayName());
        validateFieldKey(normalizedFieldKey);
        Optional<PatientAdditionalFieldDefinition> existing = definitionDAO.findByFieldKey(normalizedFieldKey);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Field key already exists: " + normalizedFieldKey);
        }

        FieldType fieldType = parseFieldType(payload.getFieldType());
        PatientAdditionalFieldDefinition definition = new PatientAdditionalFieldDefinition();
        definition.setFieldKey(normalizedFieldKey);
        definition.setDisplayName(payload.getDisplayName().trim());
        definition.setFieldType(fieldType.name());
        definition.setRequired(Boolean.TRUE.equals(payload.getRequired()));
        definition.setActive(payload.getActive() == null || payload.getActive());
        definition.setSortOrder(payload.getSortOrder() == null ? getNextSortOrder()
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
    public PatientAdditionalFieldPayload updateField(Integer fieldId, PatientAdditionalFieldPayload payload,
            String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }
        PatientAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        boolean hasSavedValues = hasSavedValues(fieldId);
        FieldType existingFieldType = parseFieldType(definition.getFieldType());
        FieldType requestedFieldType = payload.getFieldType() == null ? existingFieldType
                : parseFieldType(payload.getFieldType());

        if (hasSavedValues && requestedFieldType != existingFieldType) {
            throw new IllegalStateException(
                    "This field already has saved data and its type cannot be changed.");
        }
        if (hasSavedValues && payload.getOptions() != null && isOptionFieldType(requestedFieldType)
                && haveOptionsChanged(fieldId, payload.getOptions())) {
            throw new IllegalStateException(
                    "This field already has saved data and its options cannot be changed.");
        }

        if (StringUtils.isNotBlank(payload.getFieldKey())) {
            String normalizedFieldKey = normalizeFieldKey(payload.getFieldKey(), payload.getDisplayName());
            validateFieldKey(normalizedFieldKey);
            definitionDAO.findByFieldKey(normalizedFieldKey).ifPresent(existing -> {
                if (!existing.getId().equals(fieldId)) {
                    throw new IllegalArgumentException("Field key already exists: " + normalizedFieldKey);
                }
            });
            definition.setFieldKey(normalizedFieldKey);
        }

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

        FieldType currentType = parseFieldType(definition.getFieldType());
        if (payload.getOptions() != null && isOptionFieldType(currentType) && !hasSavedValues) {
            replaceOptionsForDefinition(fieldId, payload.getOptions(), currentUserId);
        }
        return getFieldById(fieldId, true);
    }

    @Override
    public void deactivateField(Integer fieldId, String currentUserId) {
        PatientAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        definition.setActive(false);
        definition.setSysUserId(currentUserId);
        definitionDAO.update(definition);
    }

    @Override
    public PatientAdditionalFieldOptionPayload createOption(Integer fieldId, PatientAdditionalFieldOptionPayload payload,
            String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }
        PatientAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        FieldType fieldType = parseFieldType(definition.getFieldType());
        if (!isOptionFieldType(fieldType)) {
            throw new IllegalArgumentException("Field type does not support options");
        }
        if (payload == null || StringUtils.isBlank(payload.getOptionLabel())) {
            throw new IllegalArgumentException("optionLabel is required");
        }
        String normalizedOptionKey = normalizeOptionKey(payload.getOptionKey(), payload.getOptionLabel());
        optionDAO.findByDefinitionIdAndOptionKey(fieldId, normalizedOptionKey)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Option key already exists: " + normalizedOptionKey);
                });

        PatientAdditionalFieldOption option = new PatientAdditionalFieldOption();
        option.setFieldDefinitionId(fieldId);
        option.setOptionKey(normalizedOptionKey);
        option.setOptionLabel(payload.getOptionLabel().trim());
        option.setSortOrder(payload.getSortOrder() == null ? getNextOptionSortOrder(fieldId)
                : Math.max(payload.getSortOrder(), 0));
        option.setActive(payload.getActive() == null || payload.getActive());
        option.setSysUserId(currentUserId);
        Integer optionId = optionDAO.insert(option);
        return mapOptionToPayload(optionDAO.get(optionId).orElse(option));
    }

    @Override
    public PatientAdditionalFieldOptionPayload updateOption(Integer optionId,
            PatientAdditionalFieldOptionPayload payload, String currentUserId) {
        if (optionId == null) {
            throw new IllegalArgumentException("optionId is required");
        }
        PatientAdditionalFieldOption option = optionDAO.get(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
        if (payload != null && StringUtils.isNotBlank(payload.getOptionKey())) {
            String normalizedOptionKey = normalizeOptionKey(payload.getOptionKey(), payload.getOptionLabel());
            optionDAO.findByDefinitionIdAndOptionKey(option.getFieldDefinitionId(), normalizedOptionKey).ifPresent(
                    existing -> {
                        if (!existing.getId().equals(optionId)) {
                            throw new IllegalArgumentException("Option key already exists: " + normalizedOptionKey);
                        }
                    });
            option.setOptionKey(normalizedOptionKey);
        }
        if (payload != null && StringUtils.isNotBlank(payload.getOptionLabel())) {
            option.setOptionLabel(payload.getOptionLabel().trim());
        }
        if (payload != null && payload.getSortOrder() != null) {
            option.setSortOrder(Math.max(payload.getSortOrder(), 0));
        }
        if (payload != null && payload.getActive() != null) {
            option.setActive(payload.getActive());
        }
        option.setSysUserId(currentUserId);
        optionDAO.update(option);
        return mapOptionToPayload(option);
    }

    @Override
    public void deactivateOption(Integer optionId, String currentUserId) {
        PatientAdditionalFieldOption option = optionDAO.get(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
        option.setActive(false);
        option.setSysUserId(currentUserId);
        optionDAO.update(option);
    }

    @Override
    public void upsertFixedFieldConfigs(List<PatientFixedFieldConfigPayload> payloads, String currentUserId) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }

        for (PatientFixedFieldConfigPayload payload : payloads) {
            if (payload == null || StringUtils.isBlank(payload.getFieldKey())) {
                continue;
            }

            String normalizedKey = payload.getFieldKey().trim();
            if (!FIXED_FIELD_KEYS.contains(normalizedKey.toLowerCase())) {
                throw new IllegalArgumentException("Unsupported patient fixed field key: " + normalizedKey);
            }

            PatientFixedFieldConfig entity = fixedFieldConfigDAO.findByFieldKey(normalizedKey)
                    .orElseGet(PatientFixedFieldConfig::new);
            entity.setFieldKey(normalizedKey);
            entity.setVisible(payload.getVisible() == null || payload.getVisible());
            entity.setRequired(payload.getRequired() != null && payload.getRequired());
            entity.setReadonly(payload.getReadonly() != null && payload.getReadonly());
            entity.setSortOrder(
                    payload.getSortOrder() == null ? getDefaultFixedFieldSortOrder(normalizedKey) : payload.getSortOrder());
            entity.setSysUserId(currentUserId);

            if (entity.getId() == null) {
                fixedFieldConfigDAO.insert(entity);
            } else {
                fixedFieldConfigDAO.update(entity);
            }
        }
    }

    @Override
    public void validateAndPersistPatientValues(String patientId, Map<String, String> fieldValues,
            String currentUserId, List<PatientAdditionalFieldPayload> activeFieldCache) {
        if (StringUtils.isBlank(patientId) || fieldValues == null) {
            return;
        }

        Integer numericPatientId = parseNumericId(patientId, "patientId");
        List<PatientAdditionalFieldPayload> fieldDefinitions = activeFieldCache == null ? getFields(false, true)
                : activeFieldCache;
        if (fieldDefinitions == null || fieldDefinitions.isEmpty()) {
            return;
        }

        for (PatientAdditionalFieldPayload fieldDefinition : fieldDefinitions) {
            String rawValue = fieldValues.get(fieldDefinition.getFieldKey());
            String normalizedValue = normalizeAndValidateValue(fieldDefinition, rawValue);
            Optional<PatientAdditionalFieldValue> existing = valueDAO.findByPatientIdAndFieldDefinitionId(numericPatientId,
                    fieldDefinition.getId());

            if (StringUtils.isBlank(normalizedValue)) {
                if (Boolean.TRUE.equals(fieldDefinition.getRequired())) {
                    throw new LIMSRuntimeException("Additional field is required: "
                            + StringUtils.defaultString(fieldDefinition.getDisplayName()));
                }
                existing.ifPresent(valueDAO::delete);
                continue;
            }

            PatientAdditionalFieldValue entity = existing.orElseGet(PatientAdditionalFieldValue::new);
            entity.setPatientId(numericPatientId);
            entity.setFieldDefinitionId(fieldDefinition.getId());
            entity.setFieldValue(normalizedValue);
            entity.setSysUserId(currentUserId);
            if (entity.getId() == null) {
                valueDAO.insert(entity);
            } else {
                valueDAO.update(entity);
            }
        }
    }

    private List<PatientAdditionalFieldPayload> mapDefinitionsToPayload(
            List<PatientAdditionalFieldDefinition> definitions, List<PatientAdditionalFieldOption> options,
            boolean resolveUserOptions) {
        Map<Integer, List<PatientAdditionalFieldOptionPayload>> optionsByDefinitionId = new HashMap<>();
        for (PatientAdditionalFieldOption option : options) {
            optionsByDefinitionId.computeIfAbsent(option.getFieldDefinitionId(), ignored -> new ArrayList<>())
                    .add(mapOptionToPayload(option));
        }

        List<PatientAdditionalFieldPayload> payloads = new ArrayList<>();
        for (PatientAdditionalFieldDefinition definition : definitions) {
            PatientAdditionalFieldPayload payload = mapDefinitionToPayload(definition);
            if (resolveUserOptions && isUserFieldType(definition.getFieldType())) {
                payload.setOptions(resolveUserFieldOptions(definition.getFieldType(), definition.getMetadataJson()));
            } else {
                payload.setOptions(optionsByDefinitionId.getOrDefault(definition.getId(), Collections.emptyList()));
            }
            payloads.add(payload);
        }
        return payloads;
    }

    private PatientAdditionalFieldPayload mapDefinitionToPayload(PatientAdditionalFieldDefinition definition) {
        PatientAdditionalFieldPayload payload = new PatientAdditionalFieldPayload();
        payload.setId(definition.getId());
        payload.setFieldKey(definition.getFieldKey());
        payload.setDisplayName(definition.getDisplayName());
        payload.setFieldType(definition.getFieldType());
        payload.setRequired(definition.getRequired());
        payload.setActive(definition.getActive());
        payload.setSortOrder(definition.getSortOrder());
        payload.setDefaultValue(definition.getDefaultValue());
        payload.setMaxLength(definition.getMaxLength());
        payload.setMetadataJson(definition.getMetadataJson());
        payload.setHasSavedValues(hasSavedValues(definition.getId()));
        return payload;
    }

    private PatientFixedFieldConfigPayload mapFixedFieldToPayload(PatientFixedFieldConfig config) {
        PatientFixedFieldConfigPayload payload = new PatientFixedFieldConfigPayload();
        payload.setId(config.getId());
        payload.setFieldKey(config.getFieldKey());
        payload.setVisible(config.getVisible());
        payload.setRequired(config.getRequired());
        payload.setReadonly(config.getReadonly());
        payload.setSortOrder(config.getSortOrder());
        return payload;
    }

    private PatientAdditionalFieldOptionPayload mapOptionToPayload(PatientAdditionalFieldOption option) {
        PatientAdditionalFieldOptionPayload payload = new PatientAdditionalFieldOptionPayload();
        payload.setId(option.getId());
        payload.setOptionKey(option.getOptionKey());
        payload.setOptionLabel(option.getOptionLabel());
        payload.setSortOrder(option.getSortOrder());
        payload.setActive(option.getActive());
        return payload;
    }

    private PatientAdditionalFieldPayload getFieldById(Integer fieldId, boolean includeInactiveOptions) {
        PatientAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        PatientAdditionalFieldPayload payload = mapDefinitionToPayload(definition);
        if (isUserFieldType(definition.getFieldType())) {
            payload.setOptions(Collections.emptyList());
        } else {
            List<PatientAdditionalFieldOption> options = optionDAO.findByDefinitionId(fieldId, includeInactiveOptions);
            payload.setOptions(options.stream().map(this::mapOptionToPayload).collect(Collectors.toList()));
        }
        return payload;
    }

    private void validatePayloadRequiredValues(PatientAdditionalFieldPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Request payload is required");
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

    private boolean hasSavedValues(Integer fieldId) {
        return fieldId != null && valueDAO.countByFieldDefinitionId(fieldId) > 0;
    }

    private boolean isUserFieldType(String fieldType) {
        return userFieldOptionResolver.isUserFieldType(fieldType);
    }

    private Integer getNextSortOrder() {
        List<PatientAdditionalFieldDefinition> existing = definitionDAO.findAll(false);
        return existing.stream().map(PatientAdditionalFieldDefinition::getSortOrder).filter(value -> value != null)
                .max(Integer::compareTo).map(max -> max + 1).orElse(1);
    }

    private Integer getNextOptionSortOrder(Integer fieldId) {
        List<PatientAdditionalFieldOption> existing = optionDAO.findByDefinitionId(fieldId, true);
        return existing.stream().map(PatientAdditionalFieldOption::getSortOrder).filter(value -> value != null)
                .max(Integer::compareTo).map(max -> max + 1).orElse(1);
    }

    private Integer getDefaultFixedFieldSortOrder(String fieldKey) {
        return FIXED_FIELD_DEFAULTS.stream().filter(f -> f.fieldKey.equalsIgnoreCase(fieldKey)).map(f -> f.sortOrder)
                .findFirst().orElse(0);
    }

    private void saveOptionsForDefinition(Integer definitionId, List<PatientAdditionalFieldOptionPayload> options,
            FieldType fieldType, String currentUserId) {
        if (!isOptionFieldType(fieldType) || options == null || options.isEmpty()) {
            return;
        }

        int fallbackSortOrder = 1;
        Set<String> uniqueOptionKeys = new HashSet<>();
        for (PatientAdditionalFieldOptionPayload optionPayload : options) {
            if (optionPayload == null || StringUtils.isBlank(optionPayload.getOptionLabel())) {
                continue;
            }
            String normalizedOptionKey = normalizeOptionKey(optionPayload.getOptionKey(), optionPayload.getOptionLabel());
            if (!uniqueOptionKeys.add(normalizedOptionKey)) {
                throw new IllegalArgumentException("Duplicate option key in request payload: " + normalizedOptionKey);
            }
            PatientAdditionalFieldOption option = new PatientAdditionalFieldOption();
            option.setFieldDefinitionId(definitionId);
            option.setOptionKey(normalizedOptionKey);
            option.setOptionLabel(optionPayload.getOptionLabel().trim());
            option.setSortOrder(optionPayload.getSortOrder() == null ? fallbackSortOrder : optionPayload.getSortOrder());
            option.setActive(optionPayload.getActive() == null || optionPayload.getActive());
            option.setSysUserId(currentUserId);
            optionDAO.insert(option);
            fallbackSortOrder++;
        }
    }

    private void replaceOptionsForDefinition(Integer fieldId, List<PatientAdditionalFieldOptionPayload> options,
            String currentUserId) {
        List<PatientAdditionalFieldOption> existingOptions = optionDAO.findByDefinitionId(fieldId, true);
        for (PatientAdditionalFieldOption existing : existingOptions) {
            optionDAO.delete(existing);
        }
        saveOptionsForDefinition(fieldId, options, parseFieldType(definitionDAO.get(fieldId).get().getFieldType()),
                currentUserId);
    }

    private boolean haveOptionsChanged(Integer fieldId, List<PatientAdditionalFieldOptionPayload> requestedOptions) {
        List<String> existing = optionDAO.findByDefinitionId(fieldId, true).stream().filter(option -> option != null)
                .filter(option -> option.getActive() == null || option.getActive()).sorted((left, right) -> Integer
                        .compare(left.getSortOrder() == null ? Integer.MAX_VALUE : left.getSortOrder(),
                                right.getSortOrder() == null ? Integer.MAX_VALUE : right.getSortOrder()))
                .map(option -> option.getOptionKey() + "|" + StringUtils.trimToEmpty(option.getOptionLabel()))
                .collect(Collectors.toList());

        List<String> requested = requestedOptions == null ? Collections.emptyList()
                : requestedOptions.stream().filter(option -> option != null)
                        .filter(option -> StringUtils.isNotBlank(option.getOptionLabel()))
                        .filter(option -> !Boolean.FALSE.equals(option.getActive()))
                        .sorted((left, right) -> Integer.compare(
                                left.getSortOrder() == null ? Integer.MAX_VALUE : left.getSortOrder(),
                                right.getSortOrder() == null ? Integer.MAX_VALUE : right.getSortOrder()))
                        .map(option -> normalizeOptionKey(option.getOptionKey(), option.getOptionLabel()) + "|"
                                + StringUtils.trimToEmpty(option.getOptionLabel()))
                        .collect(Collectors.toList());

        return !existing.equals(requested);
    }

    private void deactivateAllOptions(Integer fieldId, String currentUserId) {
        List<PatientAdditionalFieldOption> existingOptions = optionDAO.findByDefinitionId(fieldId, true);
        for (PatientAdditionalFieldOption existing : existingOptions) {
            existing.setActive(false);
            existing.setSysUserId(currentUserId);
            optionDAO.update(existing);
        }
    }

    private String normalizeAndValidateValue(PatientAdditionalFieldPayload fieldDefinition, String rawValue) {
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
        case USER:
            validateSingleOption(fieldDefinition, trimmedValue);
            return trimmedValue;
        case MULTISELECT:
            return normalizeAndValidateMultiSelect(fieldDefinition, trimmedValue);
        default:
            return trimmedValue;
        }
    }

    private void validateMaxLength(PatientAdditionalFieldPayload fieldDefinition, String value) {
        Integer maxLength = fieldDefinition.getMaxLength();
        if (maxLength != null && maxLength > 0 && value.length() > maxLength) {
            throw new LIMSRuntimeException(
                    "Value exceeds maximum length for field: " + fieldDefinition.getDisplayName());
        }
    }

    private void validateNumber(PatientAdditionalFieldPayload fieldDefinition, String value) {
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
            }
        }
        if (value.contains(" ")) {
            String replaced = value.replace(' ', 'T');
            for (DateTimeFormatter acceptedFormat : acceptedFormats) {
                try {
                    LocalDateTime.parse(replaced, acceptedFormat);
                    return;
                } catch (DateTimeParseException e) {
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
            }
        }
        throw new LIMSRuntimeException("Invalid time format. Expected HH:mm or HH:mm:ss");
    }

    private void validateBoolean(String value) {
        if (!("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
            throw new LIMSRuntimeException("Invalid boolean value. Expected true or false");
        }
    }

    private void validateSingleOption(PatientAdditionalFieldPayload fieldDefinition, String value) {
        Set<String> validOptions = fieldDefinition.getOptions().stream().filter(option -> Boolean.TRUE.equals(option.getActive()))
                .map(PatientAdditionalFieldOptionPayload::getOptionKey).collect(Collectors.toSet());
        if (!validOptions.contains(value)) {
            throw new LIMSRuntimeException("Invalid option selected for field: " + fieldDefinition.getDisplayName());
        }
    }

    private String normalizeAndValidateMultiSelect(PatientAdditionalFieldPayload fieldDefinition, String value) {
        Set<String> validOptions = fieldDefinition.getOptions().stream().filter(option -> Boolean.TRUE.equals(option.getActive()))
                .map(PatientAdditionalFieldOptionPayload::getOptionKey).collect(Collectors.toSet());
        LinkedHashSet<String> normalizedOptions = new LinkedHashSet<>();
        for (String option : value.split(",")) {
            String trimmed = StringUtils.trimToNull(option);
            if (trimmed == null) {
                continue;
            }
            if (!validOptions.contains(trimmed)) {
                throw new LIMSRuntimeException("Invalid option selected for field: " + fieldDefinition.getDisplayName());
            }
            normalizedOptions.add(trimmed);
        }
        return normalizedOptions.isEmpty() ? null : String.join(",", normalizedOptions);
    }

    private List<PatientAdditionalFieldOptionPayload> resolveUserFieldOptions(String fieldType, String metadataJson) {
        List<PatientAdditionalFieldOptionPayload> payloads = new ArrayList<>();
        List<UserFieldOptionResolver.ResolvedUserOption> resolvedOptions = userFieldOptionResolver
                .resolveUserOptions(fieldType, metadataJson);
        for (int index = 0; index < resolvedOptions.size(); index++) {
            UserFieldOptionResolver.ResolvedUserOption option = resolvedOptions.get(index);
            PatientAdditionalFieldOptionPayload payload = new PatientAdditionalFieldOptionPayload();
            payload.setOptionKey(option.optionKey());
            payload.setOptionLabel(option.label());
            payload.setSortOrder(index + 1);
            payload.setActive(true);
            payloads.add(payload);
        }
        return payloads;
    }

    private static class FixedFieldDefault {
        private final String fieldKey;
        private final Integer sortOrder;
        private final boolean required;
        private final boolean readonly;

        private FixedFieldDefault(String fieldKey, Integer sortOrder, boolean required, boolean readonly) {
            this.fieldKey = fieldKey;
            this.sortOrder = sortOrder;
            this.required = required;
            this.readonly = readonly;
        }
    }
}
