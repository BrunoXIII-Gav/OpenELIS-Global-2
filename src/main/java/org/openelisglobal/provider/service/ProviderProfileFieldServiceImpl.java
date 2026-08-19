package org.openelisglobal.provider.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldDefinitionForm;
import org.openelisglobal.professionalprofile.service.ProfessionalProfileFieldConfigService;
import org.openelisglobal.provider.dao.ProviderProfileFieldValueDAO;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.provider.valueholder.ProviderProfileFieldValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderProfileFieldServiceImpl implements ProviderProfileFieldService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String MULTISELECT_DELIMITER = "||";
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^\\d*([.,]\\d*)?$");

    private static final TypeReference<LinkedHashMap<String, LinkedHashMap<String, Object>>> PROFILE_VALUES_TYPE =
            new TypeReference<>() {
            };

    @Autowired
    private ProfessionalProfileFieldConfigService professionalProfileFieldConfigService;
    @Autowired
    private ProviderProfileFieldValueDAO providerProfileFieldValueDAO;

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalProfileFieldDefinitionForm> getFieldDefinitionsForProvider(String profileCode, Provider provider) {
        Map<String, Object> currentValues = getProfileValues(provider, profileCode);
        return professionalProfileFieldConfigService.getFieldsForProfile(profileCode, false).stream().map(field -> {
            ProfessionalProfileFieldDefinitionForm copy = new ProfessionalProfileFieldDefinitionForm();
            copy.setFieldKey(field.getFieldKey());
            copy.setDisplayName(field.getDisplayName());
            copy.setFieldType(field.getFieldType());
            copy.setRequired(field.getRequired());
            copy.setActive(field.getActive());
            copy.setSortOrder(field.getSortOrder());
            copy.setLegacyBinding(field.getLegacyBinding());
            copy.setSystemField(field.getSystemField());
            copy.setShowInOrderEntry(field.getShowInOrderEntry());
            copy.setOptions(new ArrayList<>(field.getOptions()));
            copy.setCurrentValue(currentValues.get(field.getFieldKey()));
            copy.setHasSavedValues(field.getHasSavedValues());
            return copy;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> normalizeAndValidateProfileFieldValues(String profileCode, Map<String, Object> submittedValues) {
        String normalizedProfileCode = normalizeProfileCode(profileCode);
        LinkedHashMap<String, Object> currentProfileValues = new LinkedHashMap<>();
        professionalProfileFieldConfigService.getFieldsForProfile(normalizedProfileCode, false).forEach(field -> {
            Object rawValue = (submittedValues == null) ? null : submittedValues.get(field.getFieldKey());
            Object normalizedValue = normalizeFieldValue(field.getFieldType(), rawValue);
            validateFieldValue(field, normalizedValue);
            validateRequiredField(field, normalizedValue);
            if (shouldPersistValue(field.getFieldType(), normalizedValue)) {
                currentProfileValues.put(field.getFieldKey(), normalizedValue);
            }
        });
        return currentProfileValues;
    }

    @Override
    @Transactional
    public void saveProfileFieldValues(Provider provider, String profileCode, Map<String, Object> submittedValues) {
        Integer providerId = normalizeProviderId(provider == null ? null : provider.getId());
        if (providerId == null) {
            return;
        }

        String normalizedProfileCode = normalizeProfileCode(profileCode);
        Map<String, Object> normalizedValues = normalizeAndValidateProfileFieldValues(normalizedProfileCode, submittedValues);
        providerProfileFieldValueDAO.deleteByProviderId(providerId);

        normalizedValues.forEach((fieldKey, normalizedValue) -> {
            ProviderProfileFieldValue value = new ProviderProfileFieldValue();
            value.setProviderId(providerId);
            value.setProfessionalProfileCode(normalizedProfileCode);
            value.setFieldKey(fieldKey);
            value.setFieldValue(serializeStoredValue(normalizedValue));
            value.setSysUserId(provider == null ? null : provider.getSysUserId());
            providerProfileFieldValueDAO.insert(value);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public void hydrateProfileFieldValues(Provider provider) {
        if (provider == null) {
            return;
        }

        provider.setProfileFieldValues(getProfileValues(provider, provider.getProfessionalProfileCode()));
    }

    @Override
    @Transactional(readOnly = true)
    public void hydrateProfileFieldValues(List<Provider> providers) {
        if (providers == null || providers.isEmpty()) {
            return;
        }

        List<Integer> providerIds = providers.stream().map(Provider::getId).map(this::normalizeProviderId)
                .filter(Objects::nonNull).distinct().toList();

        Map<Integer, List<ProviderProfileFieldValue>> rowsByProviderId = providerIds.isEmpty() ? Collections.emptyMap()
                : providerProfileFieldValueDAO.findByProviderIds(providerIds).stream()
                        .collect(Collectors.groupingBy(ProviderProfileFieldValue::getProviderId, LinkedHashMap::new,
                                Collectors.toList()));

        providers.forEach(provider -> {
            Integer providerId = normalizeProviderId(provider.getId());
            if (providerId == null) {
                provider.setProfileFieldValues(new LinkedHashMap<>());
                return;
            }

            String normalizedProfileCode = normalizeProfileCode(provider.getProfessionalProfileCode());
            List<ProfessionalProfileFieldDefinitionForm> fieldDefinitions = professionalProfileFieldConfigService
                    .getFieldsForProfile(normalizedProfileCode, true);
            Map<String, ProfessionalProfileFieldDefinitionForm> fieldDefinitionsByKey = fieldDefinitions.stream()
                    .collect(Collectors.toMap(ProfessionalProfileFieldDefinitionForm::getFieldKey, field -> field,
                            (left, _right) -> left, LinkedHashMap::new));

            LinkedHashMap<String, Object> currentValues = new LinkedHashMap<>();
            rowsByProviderId.getOrDefault(providerId, Collections.emptyList()).stream()
                    .filter(value -> normalizedProfileCode.equals(normalizeProfileCode(value.getProfessionalProfileCode())))
                    .forEach(value -> {
                        Object currentValue = deserializeStoredValue(fieldDefinitionsByKey.get(value.getFieldKey()),
                                value.getFieldValue());
                        if (currentValue != null) {
                            currentValues.put(value.getFieldKey(), currentValue);
                        }
                    });
            provider.setProfileFieldValues(currentValues);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public void hydrateProfileFieldsJson(Provider provider) {
        if (provider == null) {
            return;
        }

        provider.setProfileFieldsJson(writeProfileValues(buildCompatibilityProfileValues(provider)));
    }

    @Override
    @Transactional(readOnly = true)
    public void hydrateProfileFieldsJson(List<Provider> providers) {
        if (providers == null || providers.isEmpty()) {
            return;
        }

        List<Integer> providerIds = providers.stream().map(Provider::getId).map(this::normalizeProviderId)
                .filter(Objects::nonNull).distinct().toList();
        if (providerIds.isEmpty()) {
            providers.forEach(provider -> provider.setProfileFieldsJson("{}"));
            return;
        }

        Map<Integer, List<ProviderProfileFieldValue>> rowsByProviderId = providerProfileFieldValueDAO.findByProviderIds(providerIds).stream()
                .collect(Collectors.groupingBy(ProviderProfileFieldValue::getProviderId, LinkedHashMap::new,
                        Collectors.toList()));

        providers.forEach(provider -> {
            Integer providerId = normalizeProviderId(provider.getId());
            if (providerId == null) {
                provider.setProfileFieldsJson("{}");
                return;
            }
            provider.setProfileFieldsJson(writeProfileValues(buildCompatibilityProfileValues(provider,
                    rowsByProviderId.getOrDefault(providerId, Collections.emptyList()))));
        });
    }

    private void validateRequiredField(ProfessionalProfileFieldDefinitionForm field, Object normalizedValue) {
        if (!Boolean.TRUE.equals(field.getRequired())) {
            return;
        }

        boolean missing = switch (StringUtils.upperCase(StringUtils.trimToEmpty(field.getFieldType()))) {
        case "BOOLEAN" -> !Boolean.TRUE.equals(normalizedValue);
        case "MULTISELECT" -> !(normalizedValue instanceof List<?> listValue) || listValue.isEmpty();
        default -> normalizedValue == null || StringUtils.isBlank(String.valueOf(normalizedValue));
        };

        if (missing) {
            String fieldName = StringUtils.defaultIfBlank(field.getDisplayName(), field.getFieldKey());
            throw new IllegalArgumentException("The field " + fieldName + " is required.");
        }
    }

    private void validateFieldValue(ProfessionalProfileFieldDefinitionForm field, Object normalizedValue) {
        String normalizedFieldType = StringUtils.upperCase(StringUtils.trimToEmpty(field.getFieldType()));
        if (!"NUMBER".equals(normalizedFieldType) || normalizedValue == null) {
            return;
        }

        String stringValue = StringUtils.trimToEmpty(String.valueOf(normalizedValue));
        if (StringUtils.isBlank(stringValue) || NUMBER_PATTERN.matcher(stringValue).matches()) {
            return;
        }

        String fieldName = StringUtils.defaultIfBlank(field.getDisplayName(), field.getFieldKey());
        throw new IllegalArgumentException(
                "The field " + fieldName + " only accepts numbers and optional decimals.");
    }

    private Map<String, Object> getProfileValues(Provider provider, String profileCode) {
        String normalizedProfileCode = normalizeProfileCode(profileCode);
        Map<String, Object> currentValues = new LinkedHashMap<>();

        List<ProfessionalProfileFieldDefinitionForm> fieldDefinitions = professionalProfileFieldConfigService
                .getFieldsForProfile(normalizedProfileCode, true);
        Map<String, ProfessionalProfileFieldDefinitionForm> fieldDefinitionsByKey = fieldDefinitions.stream()
                .collect(Collectors.toMap(ProfessionalProfileFieldDefinitionForm::getFieldKey, field -> field, (left, _right) -> left,
                        LinkedHashMap::new));
        Map<String, String> storedProfileValues = getStoredProfileValues(provider, normalizedProfileCode);
        fieldDefinitions.forEach(field -> {
            String rawStoredValue = storedProfileValues.get(field.getFieldKey());
            Object currentValue = deserializeStoredValue(fieldDefinitionsByKey.get(field.getFieldKey()), rawStoredValue);
            if (currentValue != null) {
                currentValues.put(field.getFieldKey(), currentValue);
            }
        });
        return currentValues;
    }

    private Object normalizeFieldValue(String fieldType, Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        return switch (StringUtils.upperCase(StringUtils.trimToEmpty(fieldType))) {
        case "BOOLEAN" -> normalizeBoolean(rawValue);
        case "MULTISELECT" -> normalizeMultiSelect(rawValue);
        default -> normalizeStringValue(rawValue);
        };
    }

    private Object normalizeBoolean(Object rawValue) {
        if (rawValue instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String stringValue = StringUtils.trimToNull(String.valueOf(rawValue));
        if (stringValue == null) {
            return null;
        }
        return "true".equalsIgnoreCase(stringValue) || "yes".equalsIgnoreCase(stringValue) || "1".equals(stringValue);
    }

    private Object normalizeMultiSelect(Object rawValue) {
        if (rawValue instanceof List<?> rawList) {
            return rawList.stream().map(String::valueOf).map(StringUtils::trimToNull).filter(StringUtils::isNotBlank).toList();
        }
        String stringValue = StringUtils.trimToNull(String.valueOf(rawValue));
        return stringValue == null ? List.of() : List.of(stringValue);
    }

    private Object normalizeStringValue(Object rawValue) {
        String stringValue = StringUtils.trimToNull(String.valueOf(rawValue));
        return stringValue == null ? null : stringValue;
    }

    private Map<String, LinkedHashMap<String, Object>> parseProfileValues(String rawJson) {
        if (StringUtils.isBlank(rawJson)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, LinkedHashMap<String, Object>> parsed = OBJECT_MAPPER.readValue(rawJson, PROFILE_VALUES_TYPE);
            return parsed == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parsed);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private String writeProfileValues(Map<String, LinkedHashMap<String, Object>> allProfileValues) {
        Map<String, LinkedHashMap<String, Object>> cleaned = allProfileValues.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue() == null ? new LinkedHashMap<>()
                        : entry.getValue().entrySet().stream().filter(valueEntry -> valueEntry.getValue() != null)
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, _right) -> left,
                                        LinkedHashMap::new)),
                        (left, _right) -> left, LinkedHashMap::new));
        try {
            return OBJECT_MAPPER.writeValueAsString(cleaned);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize professional profile values.", e);
        }
    }

    private Map<String, String> getStoredProfileValues(Provider provider, String profileCode) {
        Integer providerId = normalizeProviderId(provider == null ? null : provider.getId());
        if (providerId != null && StringUtils.isNotBlank(profileCode)) {
            List<ProviderProfileFieldValue> storedRows = providerProfileFieldValueDAO.findByProviderIdAndProfessionalProfileCode(
                    providerId, profileCode);
            if (!storedRows.isEmpty()) {
                return storedRows.stream().collect(Collectors.toMap(ProviderProfileFieldValue::getFieldKey,
                        ProviderProfileFieldValue::getFieldValue, (left, _right) -> left, LinkedHashMap::new));
            }
        }

        Map<String, LinkedHashMap<String, Object>> parsedValues = parseProfileValues(provider == null ? null : provider.getProfileFieldsJson());
        Map<String, Object> storedProfileValues = parsedValues.getOrDefault(profileCode, new LinkedHashMap<>());
        return storedProfileValues.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> serializeStoredValue(entry.getValue()),
                        (left, _right) -> left, LinkedHashMap::new));
    }

    private Map<String, LinkedHashMap<String, Object>> buildCompatibilityProfileValues(Provider provider) {
        Integer providerId = normalizeProviderId(provider == null ? null : provider.getId());
        List<ProviderProfileFieldValue> storedRows = providerId == null ? Collections.emptyList()
                : providerProfileFieldValueDAO.findByProviderIdAndProfessionalProfileCode(providerId,
                        normalizeProfileCode(provider.getProfessionalProfileCode()));
        return buildCompatibilityProfileValues(provider, storedRows);
    }

    private Map<String, LinkedHashMap<String, Object>> buildCompatibilityProfileValues(Provider provider,
            List<ProviderProfileFieldValue> storedRows) {
        String normalizedProfileCode = normalizeProfileCode(provider == null ? null : provider.getProfessionalProfileCode());
        if (provider == null || StringUtils.isBlank(normalizedProfileCode)) {
            return new LinkedHashMap<>();
        }

        List<ProfessionalProfileFieldDefinitionForm> fieldDefinitions = professionalProfileFieldConfigService
                .getFieldsForProfile(normalizedProfileCode, true);
        Map<String, ProfessionalProfileFieldDefinitionForm> fieldDefinitionsByKey = fieldDefinitions.stream()
                .collect(Collectors.toMap(ProfessionalProfileFieldDefinitionForm::getFieldKey, field -> field, (left, _right) -> left,
                        LinkedHashMap::new));

        LinkedHashMap<String, Object> profileValues = new LinkedHashMap<>();
        if (storedRows != null && !storedRows.isEmpty()) {
            storedRows.stream()
                    .filter(value -> normalizedProfileCode.equals(normalizeProfileCode(value.getProfessionalProfileCode())))
                    .forEach(value -> {
                        ProfessionalProfileFieldDefinitionForm field = fieldDefinitionsByKey.get(value.getFieldKey());
                        Object normalizedValue = deserializeStoredValue(field, value.getFieldValue());
                        if (normalizedValue != null) {
                            profileValues.put(value.getFieldKey(), normalizedValue);
                        }
                    });
        }

        if (profileValues.isEmpty()) {
            Map<String, LinkedHashMap<String, Object>> legacyValues = parseProfileValues(provider.getProfileFieldsJson());
            LinkedHashMap<String, Object> legacyProfileValues = legacyValues.get(normalizedProfileCode);
            if (legacyProfileValues != null) {
                profileValues.putAll(legacyProfileValues);
            }
        }

        if (profileValues.isEmpty()) {
            return new LinkedHashMap<>();
        }

        LinkedHashMap<String, LinkedHashMap<String, Object>> allValues = new LinkedHashMap<>();
        allValues.put(normalizedProfileCode, profileValues);
        return allValues;
    }

    private Object deserializeStoredValue(ProfessionalProfileFieldDefinitionForm field, String storedValue) {
        if (field == null || storedValue == null) {
            return null;
        }

        return switch (StringUtils.upperCase(StringUtils.trimToEmpty(field.getFieldType()))) {
        case "BOOLEAN" -> Boolean.valueOf(storedValue);
        case "MULTISELECT" -> Arrays.stream(storedValue.split("\\Q" + MULTISELECT_DELIMITER + "\\E"))
                .map(StringUtils::trimToNull).filter(StringUtils::isNotBlank).toList();
        default -> storedValue;
        };
    }

    private boolean shouldPersistValue(String fieldType, Object normalizedValue) {
        if (normalizedValue == null) {
            return false;
        }

        return switch (StringUtils.upperCase(StringUtils.trimToEmpty(fieldType))) {
        case "MULTISELECT" -> normalizedValue instanceof List<?> listValue && !listValue.isEmpty();
        default -> true;
        };
    }

    private String serializeStoredValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream().map(String::valueOf).map(StringUtils::trimToNull).filter(StringUtils::isNotBlank)
                    .collect(Collectors.joining(MULTISELECT_DELIMITER));
        }
        return String.valueOf(value);
    }

    private Integer normalizeProviderId(String providerId) {
        String normalized = StringUtils.trimToNull(providerId);
        if (normalized == null) {
            return null;
        }
        return Integer.valueOf(normalized);
    }

    private String normalizeProfileCode(String profileCode) {
        return StringUtils.upperCase(StringUtils.trimToEmpty(profileCode));
    }
}
