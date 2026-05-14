package org.openelisglobal.orderadditionalfield.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFilePayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldOptionPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderFixedFieldConfigPayload;
import org.openelisglobal.orderadditionalfield.dao.OrderAdditionalFieldDefinitionDAO;
import org.openelisglobal.orderadditionalfield.dao.OrderAdditionalFieldOptionDAO;
import org.openelisglobal.orderadditionalfield.dao.OrderFixedFieldConfigDAO;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldFileDAO;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldValueDAO;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition.FieldType;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldOption;
import org.openelisglobal.orderadditionalfield.valueholder.OrderFixedFieldConfig;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldFile;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderAdditionalFieldServiceImpl implements OrderAdditionalFieldService {

    private static final Pattern FIELD_KEY_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_\\-]{1,79}$");
    private static final DateTimeFormatter DATE_TIME_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_WITH_SPACE_SECONDS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_RULE_LOGIC = "ALL";
    private static final String DEFAULT_OPERATOR = "equals";
    private static final String DEFAULT_DOCUMENT_MIME_TYPE = "application/pdf";
    private static final int DEFAULT_DOCUMENT_MAX_SIZE_MB = 5;

    private static final Set<FieldType> OPTION_TYPES = Set.of(FieldType.SELECT, FieldType.RADIO, FieldType.MULTISELECT);

    private static final List<FixedFieldDefault> FIXED_FIELD_DEFAULTS = List.of(new FixedFieldDefault("priority", 10),
            new FixedFieldDefault("requestDate", 20), new FixedFieldDefault("receivedDateForDisplay", 30),
            new FixedFieldDefault("receivedTime", 40), new FixedFieldDefault("nextVisitDate", 50),
            new FixedFieldDefault("referringSiteName", 60), new FixedFieldDefault("referringSiteDepartmentId", 70),
            new FixedFieldDefault("provisionalClinicalDiagnosis", 80), new FixedFieldDefault("providerFirstName", 90),
            new FixedFieldDefault("providerLastName", 100), new FixedFieldDefault("providerCmp", 110),
            new FixedFieldDefault("providerRne", 120), new FixedFieldDefault("providerDni", 130),
            new FixedFieldDefault("providerSpecialty", 140), new FixedFieldDefault("providerWorkPhone", 150),
            new FixedFieldDefault("providerFax", 160), new FixedFieldDefault("providerEmail", 170),
            new FixedFieldDefault("paymentOptionSelection", 180), new FixedFieldDefault("testLocationCode", 190),
            new FixedFieldDefault("otherLocationCode", 200), new FixedFieldDefault("rememberSiteAndRequester", 210));
    private static final Set<String> FIXED_FIELD_KEYS = FIXED_FIELD_DEFAULTS.stream().map(f -> f.fieldKey.toLowerCase())
            .collect(Collectors.toSet());

    @Autowired
    private OrderAdditionalFieldDefinitionDAO definitionDAO;

    @Autowired
    private OrderAdditionalFieldOptionDAO optionDAO;

    @Autowired
    private SampleOrderAdditionalFieldValueDAO valueDAO;

    @Autowired
    private SampleOrderAdditionalFieldFileDAO fileDAO;

    @Autowired
    private OrderFixedFieldConfigDAO fixedFieldConfigDAO;

    @Override
    @Transactional(readOnly = true)
    public List<OrderAdditionalFieldPayload> getFields(boolean includeInactive) {
        List<OrderAdditionalFieldDefinition> definitions = definitionDAO.findAll(!includeInactive);
        if (definitions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> definitionIds = definitions.stream().map(OrderAdditionalFieldDefinition::getId)
                .collect(Collectors.toList());
        List<OrderAdditionalFieldOption> options = optionDAO.findByDefinitionIds(definitionIds, !includeInactive);

        return mapDefinitionsToPayload(definitions, options);
    }

    @Override
    public OrderAdditionalFieldPayload createField(OrderAdditionalFieldPayload payload, String currentUserId) {
        validatePayloadRequiredValues(payload);

        String normalizedFieldKey = normalizeFieldKey(payload.getFieldKey(), payload.getDisplayName());
        validateFieldKey(normalizedFieldKey);

        Optional<OrderAdditionalFieldDefinition> existing = definitionDAO.findByFieldKey(normalizedFieldKey);
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Field key already exists: " + normalizedFieldKey);
        }

        FieldType fieldType = parseFieldType(payload.getFieldType());
        validateSearchConfiguration(payload, fieldType);

        OrderAdditionalFieldDefinition definition = new OrderAdditionalFieldDefinition();
        definition.setFieldKey(normalizedFieldKey);
        definition.setDisplayName(payload.getDisplayName().trim());
        definition.setFieldType(fieldType.name());
        definition.setRequired(Boolean.TRUE.equals(payload.getRequired()));
        definition.setActive(payload.getActive() == null || payload.getActive());
        definition.setSortOrder(
                payload.getSortOrder() == null ? getNextSortOrder() : Math.max(payload.getSortOrder(), 0));
        definition.setDefaultValue(StringUtils.defaultIfBlank(payload.getDefaultValue(), null));
        definition.setMaxLength(payload.getMaxLength());
        definition.setMetadataJson(normalizeMetadataJson(payload.getMetadataJson()));
        definition.setSearchable(Boolean.TRUE.equals(payload.getSearchable()));
        definition.setSearchUnique(Boolean.TRUE.equals(payload.getSearchUnique()));
        definition.setSysUserId(currentUserId);

        Integer definitionId = definitionDAO.insert(definition);
        saveOptionsForDefinition(definitionId, payload.getOptions(), fieldType, currentUserId);
        return getFieldById(definitionId, true);
    }

    @Override
    public OrderAdditionalFieldPayload updateField(Integer fieldId, OrderAdditionalFieldPayload payload,
            String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }

        OrderAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
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
            definition.setMetadataJson(normalizeMetadataJson(payload.getMetadataJson()));
        }
        if (payload.getSearchable() != null) {
            definition.setSearchable(payload.getSearchable());
            if (!payload.getSearchable()) {
                definition.setSearchUnique(false);
            }
        }
        if (payload.getSearchUnique() != null) {
            definition.setSearchUnique(payload.getSearchUnique());
        }

        validateSearchConfiguration(mapDefinitionToPayload(definition), parseFieldType(definition.getFieldType()));

        definition.setSysUserId(currentUserId);
        definitionDAO.update(definition);

        if (payload.getOptions() != null && isOptionFieldType(parseFieldType(definition.getFieldType()))) {
            upsertOptionsForDefinition(definition.getId(), payload.getOptions(), currentUserId);
        }

        return getFieldById(fieldId, true);
    }

    @Override
    public void deactivateField(Integer fieldId, String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }

        OrderAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        definition.setActive(false);
        definition.setSysUserId(currentUserId);
        definitionDAO.update(definition);

        deactivateAllOptions(fieldId, currentUserId);
    }

    @Override
    public OrderAdditionalFieldOptionPayload createOption(Integer fieldId, OrderAdditionalFieldOptionPayload payload,
            String currentUserId) {
        if (fieldId == null) {
            throw new IllegalArgumentException("fieldId is required");
        }
        if (payload == null || StringUtils.isBlank(payload.getOptionLabel())) {
            throw new IllegalArgumentException("optionLabel is required");
        }

        OrderAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));

        FieldType fieldType = parseFieldType(definition.getFieldType());
        if (!isOptionFieldType(fieldType)) {
            throw new IllegalArgumentException("Options can only be added for SELECT, RADIO, or MULTISELECT fields");
        }

        String normalizedOptionKey = normalizeOptionKey(payload.getOptionKey(), payload.getOptionLabel());
        Optional<OrderAdditionalFieldOption> duplicate = optionDAO.findByDefinitionIdAndOptionKey(fieldId,
                normalizedOptionKey);
        if (duplicate.isPresent()) {
            throw new IllegalArgumentException("Option key already exists for selected field: " + normalizedOptionKey);
        }

        OrderAdditionalFieldOption option = new OrderAdditionalFieldOption();
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
    public OrderAdditionalFieldOptionPayload updateOption(Integer optionId, OrderAdditionalFieldOptionPayload payload,
            String currentUserId) {
        if (optionId == null) {
            throw new IllegalArgumentException("optionId is required");
        }

        OrderAdditionalFieldOption option = optionDAO.get(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));

        if (payload.getOptionKey() != null) {
            String normalizedOptionKey = normalizeOptionKey(payload.getOptionKey(), payload.getOptionLabel());
            Optional<OrderAdditionalFieldOption> duplicate = optionDAO
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

        OrderAdditionalFieldOption option = optionDAO.get(optionId)
                .orElseThrow(() -> new IllegalArgumentException("Option not found: " + optionId));
        option.setActive(false);
        option.setSysUserId(currentUserId);
        optionDAO.update(option);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getSampleValues(String sampleId, List<OrderAdditionalFieldPayload> fieldDefinitions) {
        if (StringUtils.isBlank(sampleId) || fieldDefinitions == null || fieldDefinitions.isEmpty()) {
            return Collections.emptyMap();
        }

        Integer sampleNumericId = parseNumericId(sampleId, "sampleId");
        List<Integer> fieldDefIds = fieldDefinitions.stream().map(OrderAdditionalFieldPayload::getId)
                .filter(id -> id != null).collect(Collectors.toList());
        if (fieldDefIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, String> keyByDefId = fieldDefinitions.stream().filter(def -> def.getId() != null)
                .collect(Collectors.toMap(OrderAdditionalFieldPayload::getId, OrderAdditionalFieldPayload::getFieldKey,
                        (left, right) -> left));

        Map<String, String> valuesByKey = new HashMap<>();
        List<SampleOrderAdditionalFieldValue> entities = valueDAO.findBySampleIdAndFieldDefinitionIds(sampleNumericId,
                fieldDefIds);
        for (SampleOrderAdditionalFieldValue entity : entities) {
            String key = keyByDefId.get(entity.getFieldDefinitionId());
            if (StringUtils.isNotBlank(key)) {
                valuesByKey.put(key, entity.getFieldValue());
            }
        }

        return valuesByKey;
    }

    @Override
    public void validateAndPersistSampleValues(String sampleId, Map<String, String> fieldValues, String currentUserId,
            List<OrderAdditionalFieldPayload> activeFieldCache) {
        validateAndPersistSampleValues(sampleId, fieldValues, null, currentUserId, activeFieldCache);
    }

    @Override
    public void validateAndPersistSampleValues(String sampleId, Map<String, String> fieldValues,
            Map<String, OrderAdditionalFieldFilePayload> fieldFiles, String currentUserId,
            List<OrderAdditionalFieldPayload> activeFieldCache) {
        List<OrderAdditionalFieldPayload> activeFields = activeFieldCache;
        if (activeFields == null) {
            activeFields = getFields(false);
        }

        if (activeFields == null || activeFields.isEmpty()) {
            return;
        }

        Integer sampleNumericId = parseNumericId(sampleId, "sampleId");
        Map<String, String> safeFieldValues = fieldValues == null ? Collections.emptyMap() : fieldValues;
        Map<String, OrderAdditionalFieldFilePayload> safeFieldFiles = fieldFiles == null ? Collections.emptyMap()
                : fieldFiles;

        Set<String> knownFieldKeys = activeFields.stream().map(OrderAdditionalFieldPayload::getFieldKey)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());

        for (String providedKey : safeFieldValues.keySet()) {
            if (!knownFieldKeys.contains(providedKey) && StringUtils.isNotBlank(safeFieldValues.get(providedKey))) {
                throw new IllegalArgumentException("Unknown order additional field key: " + providedKey);
            }
        }

        for (String providedFileKey : safeFieldFiles.keySet()) {
            if (!knownFieldKeys.contains(providedFileKey)) {
                throw new IllegalArgumentException("Unknown order additional field key: " + providedFileKey);
            }
        }

        Map<String, String> evaluationContext = buildEvaluationContext(activeFields, safeFieldValues);
        for (OrderAdditionalFieldPayload definition : activeFields) {
            FieldType fieldType = parseFieldType(definition.getFieldType());
            FieldMetadata metadata = parseFieldMetadata(definition.getMetadataJson());
            boolean visible = shouldFieldBeVisible(definition, metadata, evaluationContext);
            boolean required = isFieldRequired(definition, metadata, evaluationContext);

            Optional<SampleOrderAdditionalFieldValue> existing = valueDAO
                    .findBySampleIdAndFieldDefinitionId(sampleNumericId, definition.getId());

            Optional<SampleOrderAdditionalFieldFile> existingFile = fileDAO
                    .findBySampleIdAndFieldDefinitionId(sampleNumericId, definition.getId());

            if (!visible) {
                existing.ifPresent(valueDAO::delete);
                existingFile.ifPresent(fileDAO::delete);
                continue;
            }

            if (fieldType == FieldType.DOCUMENT) {
                handleDocumentField(sampleNumericId, definition, metadata, required,
                        safeFieldFiles.get(definition.getFieldKey()), existingFile, currentUserId);
                existing.ifPresent(valueDAO::delete);
                continue;
            }

            existingFile.ifPresent(fileDAO::delete);
            String rawValue = safeFieldValues.get(definition.getFieldKey());
            if (StringUtils.isBlank(rawValue) && StringUtils.isNotBlank(definition.getDefaultValue())) {
                rawValue = definition.getDefaultValue();
            }

            String normalizedValue = normalizeAndValidateValue(definition, rawValue);

            if (StringUtils.isBlank(normalizedValue)) {
                if (required) {
                    throw new LIMSRuntimeException(
                            "Order field is required: " + StringUtils.defaultString(definition.getDisplayName()));
                }
                existing.ifPresent(valueDAO::delete);
                continue;
            }

            SampleOrderAdditionalFieldValue entity = existing.orElseGet(SampleOrderAdditionalFieldValue::new);
            entity.setSampleId(sampleNumericId);
            entity.setFieldDefinitionId(definition.getId());
            entity.setFieldValue(normalizedValue);
            entity.setSysUserId(currentUserId);
            enforceSearchUniqueness(definition, sampleNumericId, normalizedValue);

            if (entity.getId() == null) {
                valueDAO.insert(entity);
            } else {
                valueDAO.update(entity);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, OrderAdditionalFieldFilePayload> getSampleFileValues(String sampleId,
            List<OrderAdditionalFieldPayload> fieldDefinitions) {
        if (StringUtils.isBlank(sampleId) || fieldDefinitions == null || fieldDefinitions.isEmpty()) {
            return Collections.emptyMap();
        }

        Integer sampleNumericId = parseNumericId(sampleId, "sampleId");
        List<OrderAdditionalFieldPayload> documentFields = fieldDefinitions.stream()
                .filter(definition -> definition != null && definition.getId() != null)
                .filter(definition -> parseFieldType(definition.getFieldType()) == FieldType.DOCUMENT)
                .collect(Collectors.toList());
        if (documentFields.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Integer> fieldDefinitionIds = documentFields.stream().map(OrderAdditionalFieldPayload::getId)
                .collect(Collectors.toList());
        Map<Integer, String> fieldKeyById = documentFields.stream().collect(Collectors.toMap(
                OrderAdditionalFieldPayload::getId, OrderAdditionalFieldPayload::getFieldKey, (left, right) -> left));

        List<SampleOrderAdditionalFieldFile> files = fileDAO.findBySampleIdAndFieldDefinitionIds(sampleNumericId,
                fieldDefinitionIds);
        Map<String, OrderAdditionalFieldFilePayload> filePayloadByFieldKey = new HashMap<>();
        for (SampleOrderAdditionalFieldFile file : files) {
            String fieldKey = fieldKeyById.get(file.getFieldDefinitionId());
            if (StringUtils.isBlank(fieldKey)) {
                continue;
            }
            filePayloadByFieldKey.put(fieldKey, mapFileToPayload(file, false));
        }
        return filePayloadByFieldKey;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderAdditionalFieldFilePayload> getSampleFile(String sampleId, String fieldKey) {
        if (StringUtils.isBlank(sampleId)) {
            throw new IllegalArgumentException("sampleId is required");
        }
        if (StringUtils.isBlank(fieldKey)) {
            throw new IllegalArgumentException("fieldKey is required");
        }

        Integer sampleNumericId = parseNumericId(sampleId, "sampleId");
        OrderAdditionalFieldDefinition definition = definitionDAO.findByFieldKey(fieldKey)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found for key: " + fieldKey));
        if (parseFieldType(definition.getFieldType()) != FieldType.DOCUMENT) {
            throw new IllegalArgumentException("Field is not a DOCUMENT field: " + fieldKey);
        }

        Optional<SampleOrderAdditionalFieldFile> file = fileDAO.findBySampleIdAndFieldDefinitionId(sampleNumericId,
                definition.getId());
        return file.map(existing -> mapFileToPayload(existing, true));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> findSampleIdBySearchableFieldValue(String searchValue) {
        if (StringUtils.isBlank(searchValue)) {
            return Optional.empty();
        }

        List<Integer> uniqueCandidates = valueDAO.findDistinctSampleIdsBySearchableFieldValue(searchValue, true, 2);
        if (uniqueCandidates.size() == 1) {
            return Optional.of(uniqueCandidates.get(0));
        }

        List<Integer> candidates = valueDAO.findDistinctSampleIdsBySearchableFieldValue(searchValue, false, 2);
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderFixedFieldConfigPayload> getFixedFieldConfigs() {
        List<OrderFixedFieldConfig> configured = fixedFieldConfigDAO.findAllOrdered();
        Map<String, OrderFixedFieldConfig> byKey = configured.stream()
                .collect(Collectors.toMap(c -> c.getFieldKey().toLowerCase(), c -> c, (left, right) -> left));

        List<OrderFixedFieldConfigPayload> result = new ArrayList<>();
        for (FixedFieldDefault fixedFieldDefault : FIXED_FIELD_DEFAULTS) {
            OrderFixedFieldConfig config = byKey.get(fixedFieldDefault.fieldKey.toLowerCase());
            if (config == null) {
                OrderFixedFieldConfigPayload fallback = new OrderFixedFieldConfigPayload();
                fallback.setFieldKey(fixedFieldDefault.fieldKey);
                fallback.setVisible(true);
                fallback.setRequired(false);
                fallback.setReadonly(false);
                fallback.setSortOrder(fixedFieldDefault.sortOrder);
                result.add(fallback);
            } else {
                result.add(mapFixedFieldToPayload(config));
            }
        }

        return result;
    }

    @Override
    public void upsertFixedFieldConfigs(List<OrderFixedFieldConfigPayload> payloads, String currentUserId) {
        if (payloads == null || payloads.isEmpty()) {
            return;
        }

        for (OrderFixedFieldConfigPayload payload : payloads) {
            if (payload == null || StringUtils.isBlank(payload.getFieldKey())) {
                continue;
            }

            String normalizedKey = payload.getFieldKey().trim();
            if (!FIXED_FIELD_KEYS.contains(normalizedKey.toLowerCase())) {
                throw new IllegalArgumentException("Unsupported fixed field key: " + normalizedKey);
            }
            OrderFixedFieldConfig entity = fixedFieldConfigDAO.findByFieldKey(normalizedKey)
                    .orElseGet(OrderFixedFieldConfig::new);
            entity.setFieldKey(normalizedKey);
            entity.setVisible(payload.getVisible() == null || payload.getVisible());
            entity.setRequired(payload.getRequired() != null && payload.getRequired());
            entity.setReadonly(payload.getReadonly() != null && payload.getReadonly());
            entity.setSortOrder(
                    payload.getSortOrder() == null ? getDefaultSortOrder(normalizedKey) : payload.getSortOrder());
            entity.setSysUserId(currentUserId);

            if (entity.getId() == null) {
                fixedFieldConfigDAO.insert(entity);
            } else {
                fixedFieldConfigDAO.update(entity);
            }
        }
    }

    private List<OrderAdditionalFieldPayload> mapDefinitionsToPayload(List<OrderAdditionalFieldDefinition> definitions,
            List<OrderAdditionalFieldOption> options) {
        Map<Integer, List<OrderAdditionalFieldOptionPayload>> optionsByDefinitionId = new HashMap<>();
        for (OrderAdditionalFieldOption option : options) {
            optionsByDefinitionId.computeIfAbsent(option.getFieldDefinitionId(), ignored -> new ArrayList<>())
                    .add(mapOptionToPayload(option));
        }

        List<OrderAdditionalFieldPayload> payloads = new ArrayList<>();
        for (OrderAdditionalFieldDefinition definition : definitions) {
            OrderAdditionalFieldPayload payload = mapDefinitionToPayload(definition);
            payload.setOptions(optionsByDefinitionId.getOrDefault(definition.getId(), Collections.emptyList()));
            payloads.add(payload);
        }
        return payloads;
    }

    private OrderAdditionalFieldPayload mapDefinitionToPayload(OrderAdditionalFieldDefinition definition) {
        OrderAdditionalFieldPayload payload = new OrderAdditionalFieldPayload();
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
        payload.setSearchable(definition.getSearchable());
        payload.setSearchUnique(definition.getSearchUnique());
        return payload;
    }

    private OrderAdditionalFieldOptionPayload mapOptionToPayload(OrderAdditionalFieldOption option) {
        OrderAdditionalFieldOptionPayload payload = new OrderAdditionalFieldOptionPayload();
        payload.setId(option.getId());
        payload.setOptionKey(option.getOptionKey());
        payload.setOptionLabel(option.getOptionLabel());
        payload.setSortOrder(option.getSortOrder());
        payload.setActive(option.getActive());
        return payload;
    }

    private OrderFixedFieldConfigPayload mapFixedFieldToPayload(OrderFixedFieldConfig config) {
        OrderFixedFieldConfigPayload payload = new OrderFixedFieldConfigPayload();
        payload.setId(config.getId());
        payload.setFieldKey(config.getFieldKey());
        payload.setVisible(config.getVisible());
        payload.setRequired(config.getRequired());
        payload.setReadonly(config.getReadonly());
        payload.setSortOrder(config.getSortOrder());
        return payload;
    }

    private OrderAdditionalFieldPayload getFieldById(Integer fieldId, boolean includeInactiveOptions) {
        OrderAdditionalFieldDefinition definition = definitionDAO.get(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Field definition not found: " + fieldId));
        OrderAdditionalFieldPayload payload = mapDefinitionToPayload(definition);
        List<OrderAdditionalFieldOption> options = optionDAO.findByDefinitionId(fieldId, includeInactiveOptions);
        payload.setOptions(options.stream().map(this::mapOptionToPayload).collect(Collectors.toList()));
        return payload;
    }

    private void validatePayloadRequiredValues(OrderAdditionalFieldPayload payload) {
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

    private void validateSearchConfiguration(OrderAdditionalFieldPayload payload, FieldType fieldType) {
        boolean searchable = Boolean.TRUE.equals(payload.getSearchable());
        boolean searchUnique = Boolean.TRUE.equals(payload.getSearchUnique());

        if (searchUnique && !searchable) {
            throw new IllegalArgumentException("searchUnique requires searchable=true");
        }
        if (searchable && fieldType == FieldType.DOCUMENT) {
            throw new IllegalArgumentException("DOCUMENT fields cannot be configured as searchable");
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

    private Integer getNextSortOrder() {
        List<OrderAdditionalFieldDefinition> existing = definitionDAO.findAll(false);
        return existing.stream().map(OrderAdditionalFieldDefinition::getSortOrder).filter(value -> value != null)
                .max(Integer::compareTo).map(max -> max + 1).orElse(1);
    }

    private Integer getNextOptionSortOrder(Integer fieldId) {
        List<OrderAdditionalFieldOption> existing = optionDAO.findByDefinitionId(fieldId, true);
        return existing.stream().map(OrderAdditionalFieldOption::getSortOrder).filter(value -> value != null)
                .max(Integer::compareTo).map(max -> max + 1).orElse(1);
    }

    private void saveOptionsForDefinition(Integer definitionId, List<OrderAdditionalFieldOptionPayload> options,
            FieldType fieldType, String currentUserId) {
        if (!isOptionFieldType(fieldType) || options == null || options.isEmpty()) {
            return;
        }

        int fallbackSortOrder = 1;
        Set<String> uniqueOptionKeys = new HashSet<>();
        for (OrderAdditionalFieldOptionPayload optionPayload : options) {
            if (optionPayload == null || StringUtils.isBlank(optionPayload.getOptionLabel())) {
                continue;
            }

            String normalizedOptionKey = normalizeOptionKey(optionPayload.getOptionKey(),
                    optionPayload.getOptionLabel());
            if (!uniqueOptionKeys.add(normalizedOptionKey)) {
                throw new IllegalArgumentException("Duplicate option key in request payload: " + normalizedOptionKey);
            }

            OrderAdditionalFieldOption option = new OrderAdditionalFieldOption();
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

    private void upsertOptionsForDefinition(Integer definitionId, List<OrderAdditionalFieldOptionPayload> options,
            String currentUserId) {
        if (options == null) {
            return;
        }

        Set<Integer> updatedOptionIds = new HashSet<>();
        for (OrderAdditionalFieldOptionPayload optionPayload : options) {
            if (optionPayload == null || StringUtils.isBlank(optionPayload.getOptionLabel())) {
                continue;
            }

            if (optionPayload.getId() != null) {
                OrderAdditionalFieldOption existing = optionDAO.get(optionPayload.getId()).orElseThrow(
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

            OrderAdditionalFieldOptionPayload created = createOption(definitionId, optionPayload, currentUserId);
            updatedOptionIds.add(created.getId());
        }

        List<OrderAdditionalFieldOption> existingOptions = optionDAO.findByDefinitionId(definitionId, true);
        for (OrderAdditionalFieldOption existingOption : existingOptions) {
            if (!updatedOptionIds.contains(existingOption.getId())) {
                existingOption.setActive(false);
                existingOption.setSysUserId(currentUserId);
                optionDAO.update(existingOption);
            }
        }
    }

    private void deactivateAllOptions(Integer fieldId, String currentUserId) {
        List<OrderAdditionalFieldOption> options = optionDAO.findByDefinitionId(fieldId, true);
        for (OrderAdditionalFieldOption option : options) {
            option.setActive(false);
            option.setSysUserId(currentUserId);
            optionDAO.update(option);
        }
    }

    private OrderAdditionalFieldFilePayload mapFileToPayload(SampleOrderAdditionalFieldFile file,
            boolean includeContent) {
        OrderAdditionalFieldFilePayload payload = new OrderAdditionalFieldFilePayload();
        payload.setFileName(file.getFileName());
        payload.setFileType(file.getFileType());
        payload.setFileSize(file.getFileSize());
        payload.setUploadedAt(file.getUploadedAt());
        if (includeContent) {
            payload.setContent(file.getFileContent());
        }
        return payload;
    }

    private void handleDocumentField(Integer sampleNumericId, OrderAdditionalFieldPayload definition,
            FieldMetadata metadata, boolean required, OrderAdditionalFieldFilePayload filePayload,
            Optional<SampleOrderAdditionalFieldFile> existingFile, String currentUserId) {
        boolean requestedDelete = filePayload != null && Boolean.TRUE.equals(filePayload.getDeleteFile());
        if (requestedDelete) {
            existingFile.ifPresent(fileDAO::delete);
        }

        boolean hasNewContent = filePayload != null && filePayload.hasContent();
        if (hasNewContent) {
            validateDocumentFile(definition, metadata, filePayload);
            SampleOrderAdditionalFieldFile entity = existingFile.orElseGet(SampleOrderAdditionalFieldFile::new);
            entity.setSampleId(sampleNumericId);
            entity.setFieldDefinitionId(definition.getId());
            entity.setFileName(filePayload.getFileName());
            entity.setFileType(filePayload.getFileType());
            entity.setFileSize(filePayload.getFileSize() != null ? filePayload.getFileSize()
                    : Long.valueOf(filePayload.getContent().length));
            entity.setFileContent(filePayload.getContent());
            entity.setUploadedAt(new java.sql.Timestamp(System.currentTimeMillis()));
            entity.setSysUserId(currentUserId);

            if (entity.getId() == null) {
                fileDAO.insert(entity);
            } else {
                fileDAO.update(entity);
            }
            return;
        }

        if (required) {
            boolean hasPersistedFile = existingFile.isPresent() && !requestedDelete;
            if (!hasPersistedFile) {
                throw new LIMSRuntimeException(
                        "Order field is required: " + StringUtils.defaultString(definition.getDisplayName()));
            }
        }
    }

    private void validateDocumentFile(OrderAdditionalFieldPayload definition, FieldMetadata metadata,
            OrderAdditionalFieldFilePayload filePayload) {
        if (StringUtils.isBlank(filePayload.getFileName())) {
            throw new IllegalArgumentException("Document fileName is required for field: " + definition.getFieldKey());
        }
        if (StringUtils.isBlank(filePayload.getFileType())) {
            throw new IllegalArgumentException("Document fileType is required for field: " + definition.getFieldKey());
        }

        List<String> allowedMimeTypes = metadata.allowedMimeTypes == null || metadata.allowedMimeTypes.isEmpty()
                ? List.of(DEFAULT_DOCUMENT_MIME_TYPE)
                : metadata.allowedMimeTypes;

        if (!allowedMimeTypes.stream()
                .anyMatch(mime -> StringUtils.equalsIgnoreCase(mime, filePayload.getFileType()))) {
            throw new IllegalArgumentException(String.format("Unsupported file type '%s' for field '%s'",
                    filePayload.getFileType(), definition.getFieldKey()));
        }

        int maxSizeMb = metadata.maxSizeMb == null || metadata.maxSizeMb <= 0 ? DEFAULT_DOCUMENT_MAX_SIZE_MB
                : metadata.maxSizeMb;
        long maxBytes = maxSizeMb * 1024L * 1024L;
        if (filePayload.getContent().length > maxBytes) {
            throw new IllegalArgumentException(
                    String.format("File exceeds max size for field '%s': %d MB", definition.getFieldKey(), maxSizeMb));
        }
    }

    private String normalizeMetadataJson(String metadataJson) {
        if (StringUtils.isBlank(metadataJson)) {
            return null;
        }
        try {
            JsonNode parsed = OBJECT_MAPPER.readTree(metadataJson);
            return OBJECT_MAPPER.writeValueAsString(parsed);
        } catch (Exception e) {
            throw new IllegalArgumentException("metadataJson must be valid JSON");
        }
    }

    private FieldMetadata parseFieldMetadata(String metadataJson) {
        if (StringUtils.isBlank(metadataJson)) {
            return FieldMetadata.empty();
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(metadataJson);
            JsonNode rulesNode = root.path("rules");
            String logic = StringUtils.defaultIfBlank(rulesNode.path("logic").asText(null), DEFAULT_RULE_LOGIC);
            ArrayNode visibleWhen = rulesNode.path("visibleWhen").isArray() ? (ArrayNode) rulesNode.path("visibleWhen")
                    : null;
            ArrayNode requiredWhen = rulesNode.path("requiredWhen").isArray()
                    ? (ArrayNode) rulesNode.path("requiredWhen")
                    : null;

            JsonNode documentNode = root.path("document");
            List<String> acceptedMimeTypes = new ArrayList<>();
            if (documentNode.path("accept").isArray()) {
                for (JsonNode acceptNode : documentNode.path("accept")) {
                    String value = StringUtils.trimToNull(acceptNode.asText());
                    if (value != null) {
                        acceptedMimeTypes.add(value);
                    }
                }
            }
            Integer maxSizeMb = documentNode.path("maxSizeMb").isNumber() ? documentNode.path("maxSizeMb").asInt()
                    : null;
            return new FieldMetadata(logic, visibleWhen, requiredWhen, acceptedMimeTypes, maxSizeMb);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid metadataJson");
        }
    }

    private Map<String, String> buildEvaluationContext(List<OrderAdditionalFieldPayload> activeFields,
            Map<String, String> providedValues) {
        Map<String, String> context = new HashMap<>();
        if (providedValues != null) {
            context.putAll(providedValues);
        }

        for (OrderAdditionalFieldPayload definition : activeFields) {
            if (definition == null || StringUtils.isBlank(definition.getFieldKey())) {
                continue;
            }
            if (!context.containsKey(definition.getFieldKey())
                    && StringUtils.isNotBlank(definition.getDefaultValue())) {
                context.put(definition.getFieldKey(), definition.getDefaultValue());
            }
        }

        return context;
    }

    private boolean shouldFieldBeVisible(OrderAdditionalFieldPayload definition, FieldMetadata metadata,
            Map<String, String> evaluationContext) {
        if (metadata.visibleWhen == null || metadata.visibleWhen.isEmpty()) {
            return true;
        }
        return evaluateConditionList(metadata.visibleWhen, metadata.logic, evaluationContext);
    }

    private boolean isFieldRequired(OrderAdditionalFieldPayload definition, FieldMetadata metadata,
            Map<String, String> evaluationContext) {
        if (Boolean.TRUE.equals(definition.getRequired())) {
            return true;
        }
        if (metadata.requiredWhen == null || metadata.requiredWhen.isEmpty()) {
            return false;
        }
        return evaluateConditionList(metadata.requiredWhen, metadata.logic, evaluationContext);
    }

    private boolean evaluateConditionList(ArrayNode conditions, String logic, Map<String, String> evaluationContext) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        boolean useAny = "ANY".equalsIgnoreCase(logic);
        if (useAny) {
            for (JsonNode condition : conditions) {
                if (evaluateSingleCondition(condition, evaluationContext)) {
                    return true;
                }
            }
            return false;
        }

        for (JsonNode condition : conditions) {
            if (!evaluateSingleCondition(condition, evaluationContext)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateSingleCondition(JsonNode condition, Map<String, String> evaluationContext) {
        if (condition == null || !condition.isObject()) {
            return false;
        }
        String fieldKey = StringUtils.trimToNull(condition.path("fieldKey").asText());
        if (fieldKey == null) {
            return false;
        }

        String operator = StringUtils.defaultIfBlank(condition.path("operator").asText(null), DEFAULT_OPERATOR);
        String leftValue = StringUtils.trimToEmpty(evaluationContext.get(fieldKey));
        String rightValue = StringUtils.trimToEmpty(condition.path("value").asText());
        Set<String> rightValues = new HashSet<>();
        if (condition.path("values").isArray()) {
            for (JsonNode valueNode : condition.path("values")) {
                String value = StringUtils.trimToNull(valueNode.asText());
                if (value != null) {
                    rightValues.add(value);
                }
            }
        }
        if (rightValues.isEmpty() && StringUtils.isNotBlank(rightValue)) {
            rightValues.add(rightValue);
        }

        switch (operator.toLowerCase()) {
        case "equals":
            return StringUtils.equalsIgnoreCase(leftValue, rightValue);
        case "notequals":
            return !StringUtils.equalsIgnoreCase(leftValue, rightValue);
        case "in":
            return rightValues.stream().anyMatch(value -> StringUtils.equalsIgnoreCase(leftValue, value));
        case "notin":
            return rightValues.stream().noneMatch(value -> StringUtils.equalsIgnoreCase(leftValue, value));
        case "hasvalue":
            return StringUtils.isNotBlank(leftValue);
        case "istrue":
            return "true".equalsIgnoreCase(leftValue) || "yes".equalsIgnoreCase(leftValue)
                    || "1".equalsIgnoreCase(leftValue);
        case "isfalse":
            return "false".equalsIgnoreCase(leftValue) || "no".equalsIgnoreCase(leftValue)
                    || "0".equalsIgnoreCase(leftValue);
        default:
            return false;
        }
    }

    private String normalizeAndValidateValue(OrderAdditionalFieldPayload definition, String rawValue) {
        String trimmedValue = StringUtils.trimToNull(rawValue);
        if (trimmedValue == null) {
            return null;
        }

        FieldType fieldType = parseFieldType(definition.getFieldType());

        switch (fieldType) {
        case TEXT:
        case TEXTAREA:
            validateMaxLength(definition, trimmedValue);
            return trimmedValue;
        case NUMBER:
            validateNumber(trimmedValue);
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
            validateSingleOption(definition, trimmedValue);
            return trimmedValue;
        case MULTISELECT:
            return normalizeAndValidateMultiSelect(definition, trimmedValue);
        case DOCUMENT:
            return null;
        default:
            throw new IllegalArgumentException("Unsupported fieldType: " + definition.getFieldType());
        }
    }

    private void enforceSearchUniqueness(OrderAdditionalFieldPayload definition, Integer sampleNumericId,
            String normalizedValue) {
        if (!Boolean.TRUE.equals(definition.getSearchable()) || !Boolean.TRUE.equals(definition.getSearchUnique())
                || StringUtils.isBlank(normalizedValue)) {
            return;
        }

        boolean existsDuplicate = valueDAO.existsByFieldDefinitionIdAndFieldValueIgnoreCaseAndSampleIdNot(
                definition.getId(), normalizedValue, sampleNumericId);
        if (existsDuplicate) {
            throw new IllegalArgumentException(
                    String.format("Duplicate value '%s' is not allowed for searchable unique field '%s'",
                            normalizedValue, definition.getFieldKey()));
        }
    }

    private void validateMaxLength(OrderAdditionalFieldPayload definition, String value) {
        if (definition.getMaxLength() != null && definition.getMaxLength() > 0
                && value.length() > definition.getMaxLength()) {
            throw new IllegalArgumentException(String.format("Field '%s' exceeds maxLength=%d",
                    definition.getFieldKey(), definition.getMaxLength()));
        }
    }

    private void validateNumber(String value) {
        try {
            new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric value: " + value);
        }
    }

    private void validateDate(String value) {
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date value: " + value + ". Expected format yyyy-MM-dd");
        }
    }

    private void validateDateTime(String value) {
        List<DateTimeFormatter> formatters = List.of(DATE_TIME_MINUTES, DATE_TIME_SECONDS,
                DATE_TIME_WITH_SPACE_SECONDS);
        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalDateTime.parse(value, formatter);
                return;
            } catch (DateTimeParseException e) {
                // Continue checking other formats.
            }
        }
        throw new IllegalArgumentException(
                "Invalid datetime value: " + value + ". Expected format yyyy-MM-dd'T'HH:mm[:ss]");
    }

    private void validateBoolean(String value) {
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Invalid boolean value: " + value + ". Expected true/false");
        }
    }

    private void validateSingleOption(OrderAdditionalFieldPayload definition, String value) {
        List<OrderAdditionalFieldOptionPayload> options = definition.getOptions() == null ? Collections.emptyList()
                : definition.getOptions();
        Set<String> validOptionKeys = options.stream().filter(option -> Boolean.TRUE.equals(option.getActive()))
                .map(OrderAdditionalFieldOptionPayload::getOptionKey).collect(Collectors.toSet());

        if (!validOptionKeys.contains(value)) {
            throw new IllegalArgumentException(
                    String.format("Invalid option '%s' for field '%s'", value, definition.getFieldKey()));
        }
    }

    private String normalizeAndValidateMultiSelect(OrderAdditionalFieldPayload definition, String value) {
        List<OrderAdditionalFieldOptionPayload> options = definition.getOptions() == null ? Collections.emptyList()
                : definition.getOptions();
        Set<String> validOptionKeys = options.stream().filter(option -> Boolean.TRUE.equals(option.getActive()))
                .map(OrderAdditionalFieldOptionPayload::getOptionKey).collect(Collectors.toSet());

        Set<String> normalizedSelections = new LinkedHashSet<>();
        for (String rawSelection : value.split(",")) {
            String selection = StringUtils.trimToNull(rawSelection);
            if (selection == null) {
                continue;
            }
            if (!validOptionKeys.contains(selection)) {
                throw new IllegalArgumentException(
                        String.format("Invalid option '%s' for field '%s'", selection, definition.getFieldKey()));
            }
            normalizedSelections.add(selection);
        }

        return String.join(",", normalizedSelections);
    }

    private Integer getDefaultSortOrder(String fieldKey) {
        for (FixedFieldDefault fixedFieldDefault : FIXED_FIELD_DEFAULTS) {
            if (fixedFieldDefault.fieldKey.equalsIgnoreCase(fieldKey)) {
                return fixedFieldDefault.sortOrder;
            }
        }
        return 999;
    }

    private static class FieldMetadata {
        private final String logic;
        private final ArrayNode visibleWhen;
        private final ArrayNode requiredWhen;
        private final List<String> allowedMimeTypes;
        private final Integer maxSizeMb;

        private FieldMetadata(String logic, ArrayNode visibleWhen, ArrayNode requiredWhen,
                List<String> allowedMimeTypes, Integer maxSizeMb) {
            this.logic = logic;
            this.visibleWhen = visibleWhen;
            this.requiredWhen = requiredWhen;
            this.allowedMimeTypes = allowedMimeTypes;
            this.maxSizeMb = maxSizeMb;
        }

        private static FieldMetadata empty() {
            return new FieldMetadata(DEFAULT_RULE_LOGIC, null, null, Collections.emptyList(), null);
        }
    }

    private static class FixedFieldDefault {
        private final String fieldKey;
        private final Integer sortOrder;

        private FixedFieldDefault(String fieldKey, Integer sortOrder) {
            this.fieldKey = fieldKey;
            this.sortOrder = sortOrder;
        }
    }
}
