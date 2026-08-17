package org.openelisglobal.professionalprofile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldDefinitionForm;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldOptionForm;
import org.openelisglobal.siteinformation.service.SiteInformationService;
import org.openelisglobal.siteinformation.valueholder.SiteInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfessionalProfileFieldConfigServiceImpl implements ProfessionalProfileFieldConfigService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<LinkedHashMap<String, List<ProfessionalProfileFieldDefinitionForm>>> FIELD_CONFIG_TYPE =
            new TypeReference<>() {
            };

    private static final String PROFESSIONAL_INITIALS_BINDING = "PROFESSIONAL_INITIALS";

    @Autowired
    private SiteInformationService siteInformationService;

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalProfileFieldDefinitionForm> getFieldsForProfile(String profileCode, boolean includeInactive) {
        String normalizedProfileCode = normalizeProfileCode(profileCode);
        if (StringUtils.isBlank(normalizedProfileCode)) {
            return List.of();
        }

        List<ProfessionalProfileFieldDefinitionForm> definitions = loadAllFieldDefinitions().getOrDefault(normalizedProfileCode,
                List.of());

        return definitions.stream()
                .map(this::copyFieldDefinition)
                .filter(definition -> includeInactive || Boolean.TRUE.equals(definition.getActive()))
                .sorted(Comparator
                        .comparing(ProfessionalProfileFieldDefinitionForm::getSortOrder,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProfessionalProfileFieldDefinitionForm::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public void saveFieldsForProfile(String profileCode, List<ProfessionalProfileFieldDefinitionForm> fields, String currentUserId) {
        String normalizedProfileCode = normalizeProfileCode(profileCode);
        if (StringUtils.isBlank(normalizedProfileCode)) {
            throw new IllegalArgumentException("Professional profile code is required.");
        }

        Map<String, List<ProfessionalProfileFieldDefinitionForm>> allDefinitions = loadAllFieldDefinitions();
        List<ProfessionalProfileFieldDefinitionForm> normalizedFields = normalizeFields(normalizedProfileCode, fields);
        allDefinitions.put(normalizedProfileCode, normalizedFields);
        persistFieldDefinitions(allDefinitions, currentUserId);
    }

    @Override
    public void deleteFieldsForProfile(String profileCode, String currentUserId) {
        String normalizedProfileCode = normalizeProfileCode(profileCode);
        if (StringUtils.isBlank(normalizedProfileCode)) {
            return;
        }
        Map<String, List<ProfessionalProfileFieldDefinitionForm>> allDefinitions = loadAllFieldDefinitions();
        if (allDefinitions.remove(normalizedProfileCode) != null) {
            persistFieldDefinitions(allDefinitions, currentUserId);
        }
    }

    private Map<String, List<ProfessionalProfileFieldDefinitionForm>> loadAllFieldDefinitions() {
        String rawValue = ConfigurationProperties.getInstance().getPropertyValue(Property.professionalProfileFieldDefinitions);
        Map<String, List<ProfessionalProfileFieldDefinitionForm>> parsed = new LinkedHashMap<>();
        if (StringUtils.isNotBlank(rawValue)) {
            try {
                Map<String, List<ProfessionalProfileFieldDefinitionForm>> deserialized = OBJECT_MAPPER.readValue(rawValue,
                        FIELD_CONFIG_TYPE);
                if (deserialized != null) {
                    deserialized.forEach((profileCode, fields) -> parsed.put(normalizeProfileCode(profileCode),
                            normalizeFields(normalizeProfileCode(profileCode), fields)));
                }
            } catch (Exception e) {
                throw new IllegalStateException("Unable to parse professional profile field configuration.", e);
            }
        }

        return parsed;
    }

    private List<ProfessionalProfileFieldDefinitionForm> normalizeFields(String profileCode,
            List<ProfessionalProfileFieldDefinitionForm> fields) {
        Map<String, ProfessionalProfileFieldDefinitionForm> normalized = new LinkedHashMap<>();
        int sortOrder = 0;
        for (ProfessionalProfileFieldDefinitionForm field : fields == null ? List.<ProfessionalProfileFieldDefinitionForm>of() : fields) {
            ProfessionalProfileFieldDefinitionForm normalizedField = normalizeFieldDefinition(profileCode, field, sortOrder++);
            if (normalizedField == null) {
                continue;
            }
            normalized.put(normalizedField.getFieldKey(), normalizedField);
        }
        return normalized.values().stream()
                .sorted(Comparator
                        .comparing(ProfessionalProfileFieldDefinitionForm::getSortOrder,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProfessionalProfileFieldDefinitionForm::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private ProfessionalProfileFieldDefinitionForm normalizeFieldDefinition(String profileCode,
            ProfessionalProfileFieldDefinitionForm field, int fallbackSortOrder) {
        if (field == null) {
            return null;
        }

        String fieldKey = StringUtils.upperCase(StringUtils.trimToEmpty(field.getFieldKey())).replaceAll("[^A-Z0-9_]", "_");
        String displayName = StringUtils.trimToNull(field.getDisplayName());
        String fieldType = normalizeFieldType(field.getFieldType());
        String normalizedLegacyBinding = StringUtils.upperCase(StringUtils.trimToEmpty(field.getLegacyBinding()));
        if (PROFESSIONAL_INITIALS_BINDING.equals(fieldKey) || PROFESSIONAL_INITIALS_BINDING.equals(normalizedLegacyBinding)) {
            return null;
        }
        if (StringUtils.isBlank(fieldKey) || StringUtils.isBlank(displayName) || StringUtils.isBlank(fieldType)) {
            return null;
        }

        ProfessionalProfileFieldDefinitionForm normalized = new ProfessionalProfileFieldDefinitionForm();
        normalized.setFieldKey(fieldKey);
        normalized.setDisplayName(displayName);
        normalized.setFieldType(fieldType);
        normalized.setRequired(Boolean.TRUE.equals(field.getRequired()));
        normalized.setActive(!Boolean.FALSE.equals(field.getActive()));
        normalized.setSortOrder(field.getSortOrder() == null ? fallbackSortOrder : field.getSortOrder());
        normalized.setLegacyBinding(null);
        normalized.setSystemField(Boolean.FALSE);
        normalized.setOptions(normalizeFieldOptions(fieldType, field.getOptions()));
        return normalized;
    }

    private List<ProfessionalProfileFieldOptionForm> normalizeFieldOptions(String fieldType,
            List<ProfessionalProfileFieldOptionForm> options) {
        if (!"SELECT".equals(fieldType) && !"MULTISELECT".equals(fieldType)) {
            return List.of();
        }

        Map<String, ProfessionalProfileFieldOptionForm> normalized = new LinkedHashMap<>();
        int sortOrder = 0;
        for (ProfessionalProfileFieldOptionForm option : options == null ? List.<ProfessionalProfileFieldOptionForm>of() : options) {
            if (option == null) {
                continue;
            }
            String optionKey = StringUtils.trimToNull(option.getOptionKey());
            String optionLabel = StringUtils.trimToNull(option.getOptionLabel());
            if (StringUtils.isBlank(optionKey) || StringUtils.isBlank(optionLabel)) {
                continue;
            }
            ProfessionalProfileFieldOptionForm normalizedOption = new ProfessionalProfileFieldOptionForm();
            normalizedOption.setOptionKey(optionKey);
            normalizedOption.setOptionLabel(optionLabel);
            normalizedOption.setActive(!Boolean.FALSE.equals(option.getActive()));
            normalizedOption.setSortOrder(option.getSortOrder() == null ? sortOrder++ : option.getSortOrder());
            normalized.put(normalizedOption.getOptionKey(), normalizedOption);
        }
        return normalized.values().stream()
                .sorted(Comparator
                        .comparing(ProfessionalProfileFieldOptionForm::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProfessionalProfileFieldOptionForm::getOptionLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private void persistFieldDefinitions(Map<String, List<ProfessionalProfileFieldDefinitionForm>> allDefinitions,
            String currentUserId) {
        try {
            String serialized = OBJECT_MAPPER.writeValueAsString(allDefinitions.entrySet().stream()
                    .filter(entry -> StringUtils.isNotBlank(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().stream()
                            .map(this::copyFieldDefinition)
                            .collect(Collectors.toList()), (left, _right) -> left, LinkedHashMap::new)));

            SiteInformation siteInformation = siteInformationService
                    .getSiteInformationByName(Property.professionalProfileFieldDefinitions.getDBName());
            if (siteInformation == null) {
                throw new IllegalStateException("Configuration entry '"
                        + Property.professionalProfileFieldDefinitions.getDBName() + "' was not found.");
            }
            siteInformation.setValue(serialized);
            siteInformation.setSysUserId(currentUserId);
            siteInformationService.persistData(siteInformation, false);
            ConfigurationProperties.loadDBValuesIntoConfiguration();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save professional profile field configuration.", e);
        }
    }

    private ProfessionalProfileFieldDefinitionForm copyFieldDefinition(ProfessionalProfileFieldDefinitionForm source) {
        ProfessionalProfileFieldDefinitionForm copy = new ProfessionalProfileFieldDefinitionForm();
        copy.setFieldKey(source.getFieldKey());
        copy.setDisplayName(source.getDisplayName());
        copy.setFieldType(source.getFieldType());
        copy.setRequired(source.getRequired());
        copy.setActive(source.getActive());
        copy.setSortOrder(source.getSortOrder());
        copy.setLegacyBinding(source.getLegacyBinding());
        copy.setSystemField(source.getSystemField());
        copy.setCurrentValue(source.getCurrentValue());
        copy.setOptions(source.getOptions().stream().filter(Objects::nonNull).map(option -> {
            ProfessionalProfileFieldOptionForm copyOption = new ProfessionalProfileFieldOptionForm();
            copyOption.setOptionKey(option.getOptionKey());
            copyOption.setOptionLabel(option.getOptionLabel());
            copyOption.setActive(option.getActive());
            copyOption.setSortOrder(option.getSortOrder());
            return copyOption;
        }).collect(Collectors.toList()));
        return copy;
    }

    private String normalizeProfileCode(String profileCode) {
        return StringUtils.upperCase(StringUtils.trimToEmpty(profileCode));
    }

    private String normalizeFieldType(String fieldType) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(fieldType));
        return switch (normalized) {
        case "TEXT", "NUMBER", "DATE", "SELECT", "MULTISELECT", "BOOLEAN" -> normalized;
        default -> "";
        };
    }
}
