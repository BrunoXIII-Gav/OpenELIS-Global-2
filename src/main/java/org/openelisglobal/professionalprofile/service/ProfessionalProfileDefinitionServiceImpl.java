package org.openelisglobal.professionalprofile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.notification.valueholder.NotificationConfigOption;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileDefinitionForm;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldDefinitionForm;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldOptionForm;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileSettingsForm;
import org.openelisglobal.provider.dao.ProviderProfileFieldValueDAO;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.siteinformation.service.SiteInformationService;
import org.openelisglobal.siteinformation.valueholder.SiteInformation;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProfessionalProfileDefinitionServiceImpl implements ProfessionalProfileDefinitionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<LinkedHashMap<String, List<ProfessionalProfileFieldOptionForm>>> SPECIALTY_OPTIONS_TYPE =
            new TypeReference<>() {
            };

    @Autowired
    private SiteInformationService siteInformationService;

    @Autowired
    private SystemUserService systemUserService;

    @Autowired
    private ProviderService providerService;
    @Autowired
    private ProfessionalProfileFieldConfigService professionalProfileFieldConfigService;
    @Autowired
    private ProviderProfileFieldValueDAO providerProfileFieldValueDAO;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public List<ProfessionalProfileDefinitionForm> getProfiles() {
        return parseProfiles(ConfigurationProperties.getInstance().getPropertyValue(Property.professionalProfileOptions))
                .stream()
                .sorted(Comparator.comparing(ProfessionalProfileDefinitionForm::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProfessionalProfileDefinitionForm getProfile(String code) {
        String normalizedCode = normalizeCode(code);
        ProfessionalProfileDefinitionForm profile = getProfiles().stream()
                .filter(existingProfile -> normalizedCode.equals(existingProfile.getCode()))
                .findFirst()
                .orElse(null);
        if (profile == null) {
            return null;
        }
        profile.setFields(annotateFieldUsage(normalizedCode,
                professionalProfileFieldConfigService.getFieldsForProfile(normalizedCode, true)));
        profile.setSpecialtyOptions(getSpecialtyOptions(normalizedCode));
        return profile;
    }

    @Override
    public ProfessionalProfileDefinitionForm createProfile(ProfessionalProfileDefinitionForm form, String currentUserId) {
        List<ProfessionalProfileDefinitionForm> profiles = new ArrayList<>(getProfiles());
        ProfessionalProfileDefinitionForm normalizedForm = normalizeForm(form);
        if (normalizedForm == null) {
            throw new IllegalArgumentException("Professional profile name is required.");
        }
        boolean exists = profiles.stream().anyMatch(profile -> profile.getCode().equals(normalizedForm.getCode()));
        if (exists) {
            throw new IllegalArgumentException("A professional profile with that code already exists.");
        }
        profiles.add(normalizedForm);
        saveProfiles(profiles, currentUserId);
        professionalProfileFieldConfigService.saveFieldsForProfile(normalizedForm.getCode(), form.getFields(), currentUserId);
        saveSpecialtyOptions(normalizedForm.getCode(), form.getSpecialtyOptions(), currentUserId);
        normalizedForm.setFields(professionalProfileFieldConfigService.getFieldsForProfile(normalizedForm.getCode(), true));
        normalizedForm.setSpecialtyOptions(getSpecialtyOptions(normalizedForm.getCode()));
        return normalizedForm;
    }

    @Override
    public ProfessionalProfileDefinitionForm updateProfile(String code, ProfessionalProfileDefinitionForm form,
            String currentUserId) {
        String normalizedCode = normalizeCode(code);
        ProfessionalProfileDefinitionForm normalizedForm = normalizeForm(form);
        if (normalizedForm == null) {
            throw new IllegalArgumentException("Professional profile name is required.");
        }

        List<ProfessionalProfileDefinitionForm> profiles = new ArrayList<>(getProfiles());
        boolean updated = false;
        for (ProfessionalProfileDefinitionForm profile : profiles) {
            if (normalizedCode.equals(profile.getCode())) {
                profile.setName(normalizedForm.getName());
                updated = true;
                break;
            }
        }
        if (!updated) {
            throw new IllegalArgumentException("Professional profile was not found.");
        }
        saveProfiles(profiles, currentUserId);
        validateRemovedFieldsCanBeDeleted(normalizedCode, form == null ? List.of() : form.getFields());
        professionalProfileFieldConfigService.saveFieldsForProfile(normalizedCode, form.getFields(), currentUserId);
        saveSpecialtyOptions(normalizedCode, form.getSpecialtyOptions(), currentUserId);
        return new ProfessionalProfileDefinitionForm(normalizedCode, normalizedForm.getName(),
                professionalProfileFieldConfigService.getFieldsForProfile(normalizedCode, true),
                getSpecialtyOptions(normalizedCode));
    }

    @Override
    public void deleteProfile(String code, String currentUserId) {
        String normalizedCode = normalizeCode(code);
        if (StringUtils.isBlank(normalizedCode)) {
            return;
        }
        String usageMessage = buildUsageMessage(normalizedCode);
        if (usageMessage != null) {
            throw new IllegalStateException(usageMessage);
        }

        List<ProfessionalProfileDefinitionForm> profiles = new ArrayList<>(getProfiles());
        boolean removed = profiles.removeIf(profile -> normalizedCode.equals(profile.getCode()));
        if (!removed) {
            throw new IllegalArgumentException("Professional profile was not found.");
        }
        saveProfiles(profiles, currentUserId);
        professionalProfileFieldConfigService.deleteFieldsForProfile(normalizedCode, currentUserId);
        deleteSpecialtyOptions(normalizedCode, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfessionalProfileSettingsForm getSettings() {
        ProfessionalProfileSettingsForm form = new ProfessionalProfileSettingsForm();
        form.setOrderProviderProfessionalProfileCodes(parseConfiguredCodes(
                ConfigurationProperties.getInstance().getPropertyValue(Property.orderProviderProfessionalProfileCode)));
        form.setSampleCollectorProfessionalProfileCodes(parseConfiguredCodes(
                ConfigurationProperties.getInstance().getPropertyValue(Property.sampleCollectorProfessionalProfileCode)));
        form.setPatientEntryProfessionalProfileCodes(parseConfiguredCodes(
                ConfigurationProperties.getInstance().getPropertyValue(Property.patientEntryProfessionalProfileCode)));
        form.setResultEntryProfessionalProfileCodes(parseConfiguredCodes(
                ConfigurationProperties.getInstance().getPropertyValue(Property.resultEntryProfessionalProfileCode)));
        form.setValidationInterpreterProfessionalProfileCodes(parseConfiguredCodes(
                ConfigurationProperties.getInstance().getPropertyValue(Property.validationInterpreterProfessionalProfileCode)));
        return form;
    }

    @Override
    public ProfessionalProfileSettingsForm updateSettings(ProfessionalProfileSettingsForm form, String currentUserId) {
        List<String> availableCodes = getProfiles().stream().map(ProfessionalProfileDefinitionForm::getCode).toList();
        String orderProviderCodes = serializeConfiguredCodes(normalizeOptionalConfiguredCodes(
                form.getOrderProviderProfessionalProfileCodes(), availableCodes, "Order requester profiles"));
        String sampleCollectorCodes = serializeConfiguredCodes(normalizeOptionalConfiguredCodes(
                form.getSampleCollectorProfessionalProfileCodes(), availableCodes, "Sample collector profiles"));
        String patientEntryCodes = serializeConfiguredCodes(normalizeOptionalConfiguredCodes(
                form.getPatientEntryProfessionalProfileCodes(), availableCodes, "Patient entry profiles"));
        String resultEntryCodes = serializeConfiguredCodes(normalizeOptionalConfiguredCodes(
                form.getResultEntryProfessionalProfileCodes(), availableCodes, "Result entry profiles"));
        String validationInterpreterCodes = serializeConfiguredCodes(normalizeOptionalConfiguredCodes(
                form.getValidationInterpreterProfessionalProfileCodes(), availableCodes, "Validation interpreter profiles"));

        updateSiteInformation(Property.orderProviderProfessionalProfileCode, orderProviderCodes, currentUserId);
        updateSiteInformation(Property.sampleCollectorProfessionalProfileCode, sampleCollectorCodes, currentUserId);
        updateSiteInformation(Property.patientEntryProfessionalProfileCode, patientEntryCodes, currentUserId);
        updateSiteInformation(Property.resultEntryProfessionalProfileCode, resultEntryCodes, currentUserId);
        updateSiteInformation(Property.validationInterpreterProfessionalProfileCode, validationInterpreterCodes,
                currentUserId);
        ConfigurationProperties.loadDBValuesIntoConfiguration();
        
        // Refresh DisplayListService cache to apply new professional profile filters immediately
        DisplayListService.getInstance().refreshList(DisplayListService.ListType.ORDER_PROVIDER_PERSONS);
        DisplayListService.getInstance().refreshList(DisplayListService.ListType.PRACTITIONER_PERSONS);
        
        return getSettings();
    }

    private ProfessionalProfileDefinitionForm normalizeForm(ProfessionalProfileDefinitionForm form) {
        if (form == null || StringUtils.isBlank(form.getName())) {
            return null;
        }
        String normalizedCode = normalizeCode(StringUtils.defaultIfBlank(form.getCode(), form.getName()));
        if (StringUtils.isBlank(normalizedCode)) {
            throw new IllegalArgumentException("Professional profile code could not be generated.");
        }
        ProfessionalProfileDefinitionForm normalizedForm = new ProfessionalProfileDefinitionForm(normalizedCode,
                StringUtils.trim(form.getName()));
        normalizedForm.setFields(form.getFields());
        normalizedForm.setSpecialtyOptions(normalizeSpecialtyOptions(form.getSpecialtyOptions()));
        return normalizedForm;
    }

    private List<String> normalizeOptionalConfiguredCodes(List<String> codes, List<String> availableCodes, String label) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }

        List<String> normalizedCodes = codes.stream()
                .map(this::normalizeCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();

        for (String normalizedCode : normalizedCodes) {
            if (!availableCodes.contains(normalizedCode)) {
                throw new IllegalArgumentException(label + " must reference existing professional profiles.");
            }
        }

        return normalizedCodes;
    }

    private List<String> parseConfiguredCodes(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return List.of();
        }

        return List.of(StringUtils.split(rawValue, ',')).stream()
                .map(this::normalizeCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private String serializeConfiguredCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "";
        }
        return codes.stream()
                .map(this::normalizeCode)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.joining(","));
    }

    private void saveProfiles(List<ProfessionalProfileDefinitionForm> profiles, String currentUserId) {
        Map<String, ProfessionalProfileDefinitionForm> uniqueProfiles = profiles.stream()
                .filter(Objects::nonNull)
                .filter(profile -> StringUtils.isNotBlank(profile.getCode()) && StringUtils.isNotBlank(profile.getName()))
                .collect(Collectors.toMap(ProfessionalProfileDefinitionForm::getCode, profile -> profile, (left, _right) -> left,
                        LinkedHashMap::new));

        String serializedProfiles = uniqueProfiles.values().stream()
                .sorted(Comparator.comparing(ProfessionalProfileDefinitionForm::getName, String.CASE_INSENSITIVE_ORDER))
                .map(profile -> profile.getCode() + "|" + profile.getName())
                .collect(Collectors.joining(","));

        updateSiteInformation(Property.professionalProfileOptions, serializedProfiles, currentUserId);
        ConfigurationProperties.loadDBValuesIntoConfiguration();
    }

    private void validateRemovedFieldsCanBeDeleted(String profileCode,
            List<ProfessionalProfileFieldDefinitionForm> submittedFields) {
        Map<String, ProfessionalProfileFieldDefinitionForm> currentFieldsByKey = professionalProfileFieldConfigService
                .getFieldsForProfile(profileCode, true).stream()
                .collect(Collectors.toMap(ProfessionalProfileFieldDefinitionForm::getFieldKey, field -> field,
                        (left, _right) -> left, LinkedHashMap::new));

        Set<String> submittedFieldKeys = (submittedFields == null ? List.<ProfessionalProfileFieldDefinitionForm>of() : submittedFields)
                .stream()
                .filter(Objects::nonNull)
                .map(ProfessionalProfileFieldDefinitionForm::getFieldKey)
                .map(key -> StringUtils.upperCase(StringUtils.trimToEmpty(key)).replaceAll("[^A-Z0-9_]", "_"))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (Map.Entry<String, ProfessionalProfileFieldDefinitionForm> entry : currentFieldsByKey.entrySet()) {
            String existingFieldKey = entry.getKey();
            ProfessionalProfileFieldDefinitionForm existingField = entry.getValue();
            ProfessionalProfileFieldDefinitionForm submittedField = (submittedFields == null ? List.<ProfessionalProfileFieldDefinitionForm>of() : submittedFields)
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(field -> existingFieldKey.equals(StringUtils.upperCase(StringUtils.trimToEmpty(field.getFieldKey()))
                            .replaceAll("[^A-Z0-9_]", "_")))
                    .findFirst()
                    .orElse(null);

            if (submittedField == null) {
                ensureFieldHasNoStoredValues(profileCode, existingField,
                        "cannot be deleted because professional records already have saved values. Deactivate it instead.");
                continue;
            }

            validateFieldTypeChange(profileCode, existingField, submittedField);
            validateFieldOptionChanges(profileCode, existingField, submittedField);
        }
    }

    private void validateFieldTypeChange(String profileCode, ProfessionalProfileFieldDefinitionForm existingField,
            ProfessionalProfileFieldDefinitionForm submittedField) {
        String existingFieldType = StringUtils.upperCase(StringUtils.trimToEmpty(existingField.getFieldType()));
        String submittedFieldType = StringUtils.upperCase(StringUtils.trimToEmpty(submittedField.getFieldType()));
        if (Objects.equals(existingFieldType, submittedFieldType)) {
            return;
        }

        ensureFieldHasNoStoredValues(profileCode, existingField,
                "cannot change type because professional records already have saved values.");
    }

    private void validateFieldOptionChanges(String profileCode, ProfessionalProfileFieldDefinitionForm existingField,
            ProfessionalProfileFieldDefinitionForm submittedField) {
        String existingFieldType = StringUtils.upperCase(StringUtils.trimToEmpty(existingField.getFieldType()));
        if (!"SELECT".equals(existingFieldType) && !"MULTISELECT".equals(existingFieldType)) {
            return;
        }

        List<String> existingOptions = normalizeFieldOptionsForComparison(existingField.getOptions());
        List<String> submittedOptions = normalizeFieldOptionsForComparison(submittedField.getOptions());
        if (existingOptions.equals(submittedOptions)) {
            return;
        }

        ensureFieldHasNoStoredValues(profileCode, existingField,
                "cannot change options because professional records already have saved values.");
    }

    private List<String> normalizeFieldOptionsForComparison(List<ProfessionalProfileFieldOptionForm> options) {
        return (options == null ? List.<ProfessionalProfileFieldOptionForm>of() : options).stream()
                .filter(Objects::nonNull)
                .map(option -> StringUtils.trimToEmpty(option.getOptionKey()).toUpperCase(Locale.ROOT) + "|"
                        + StringUtils.trimToEmpty(option.getOptionLabel()))
                .filter(token -> !token.equals("|"))
                .toList();
    }

    private void ensureFieldHasNoStoredValues(String profileCode, ProfessionalProfileFieldDefinitionForm field,
            String reason) {
        if (field == null) {
            return;
        }

        long storedValueCount = providerProfileFieldValueDAO.countByProfessionalProfileCodeAndFieldKey(profileCode,
                field.getFieldKey());
        if (storedValueCount <= 0) {
            return;
        }

        String fieldName = StringUtils.defaultIfBlank(field.getDisplayName(), field.getFieldKey());
        throw new IllegalStateException("The field \"" + fieldName + "\" " + reason);
    }

    private List<ProfessionalProfileFieldDefinitionForm> annotateFieldUsage(String profileCode,
            List<ProfessionalProfileFieldDefinitionForm> fields) {
        return (fields == null ? List.<ProfessionalProfileFieldDefinitionForm>of() : fields).stream().map(field -> {
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
            copy.setCurrentValue(field.getCurrentValue());
            copy.setOptions(field.getOptions());
            copy.setHasSavedValues(
                    providerProfileFieldValueDAO.countByProfessionalProfileCodeAndFieldKey(profileCode, field.getFieldKey()) > 0);
            return copy;
        }).toList();
    }

    private void updateSiteInformation(Property property, String value, String currentUserId) {
        SiteInformation siteInformation = siteInformationService.getSiteInformationByName(property.getDBName());
        if (siteInformation == null) {
            throw new IllegalStateException("Configuration entry '" + property.getDBName() + "' was not found.");
        }
        siteInformation.setValue(StringUtils.defaultString(value));
        siteInformation.setSysUserId(currentUserId);
        siteInformationService.persistData(siteInformation, false);
    }

    private List<ProfessionalProfileFieldOptionForm> getSpecialtyOptions(String profileCode) {
        return loadAllSpecialtyOptions().getOrDefault(normalizeCode(profileCode), List.of()).stream()
                .map(this::copyOption)
                .sorted(Comparator
                        .comparing(ProfessionalProfileFieldOptionForm::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ProfessionalProfileFieldOptionForm::getOptionLabel, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Map<String, List<ProfessionalProfileFieldOptionForm>> loadAllSpecialtyOptions() {
        String rawValue = ConfigurationProperties.getInstance().getPropertyValue(Property.professionalProfileSpecialtyOptions);
        Map<String, List<ProfessionalProfileFieldOptionForm>> parsed = new LinkedHashMap<>();
        if (StringUtils.isBlank(rawValue)) {
            return parsed;
        }

        try {
            Map<String, List<ProfessionalProfileFieldOptionForm>> deserialized = OBJECT_MAPPER.readValue(rawValue,
                    SPECIALTY_OPTIONS_TYPE);
            if (deserialized == null) {
                return parsed;
            }
            deserialized.forEach((profileCode, options) -> parsed.put(normalizeCode(profileCode), normalizeSpecialtyOptions(options)));
            return parsed;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse professional profile specialty options.", e);
        }
    }

    private List<ProfessionalProfileFieldOptionForm> normalizeSpecialtyOptions(List<ProfessionalProfileFieldOptionForm> options) {
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

    private ProfessionalProfileFieldOptionForm copyOption(ProfessionalProfileFieldOptionForm source) {
        ProfessionalProfileFieldOptionForm copy = new ProfessionalProfileFieldOptionForm();
        copy.setOptionKey(source.getOptionKey());
        copy.setOptionLabel(source.getOptionLabel());
        copy.setActive(source.getActive());
        copy.setSortOrder(source.getSortOrder());
        return copy;
    }

    private void saveSpecialtyOptions(String profileCode, List<ProfessionalProfileFieldOptionForm> specialtyOptions,
            String currentUserId) {
        String normalizedCode = normalizeCode(profileCode);
        Map<String, List<ProfessionalProfileFieldOptionForm>> allOptions = loadAllSpecialtyOptions();
        List<ProfessionalProfileFieldOptionForm> normalizedOptions = normalizeSpecialtyOptions(specialtyOptions);
        if (normalizedOptions.isEmpty()) {
            allOptions.remove(normalizedCode);
        } else {
            allOptions.put(normalizedCode, normalizedOptions);
        }
        persistSpecialtyOptions(allOptions, currentUserId);
    }

    private void deleteSpecialtyOptions(String profileCode, String currentUserId) {
        String normalizedCode = normalizeCode(profileCode);
        Map<String, List<ProfessionalProfileFieldOptionForm>> allOptions = loadAllSpecialtyOptions();
        if (allOptions.remove(normalizedCode) != null) {
            persistSpecialtyOptions(allOptions, currentUserId);
        }
    }

    private void persistSpecialtyOptions(Map<String, List<ProfessionalProfileFieldOptionForm>> allOptions, String currentUserId) {
        try {
            String serialized = OBJECT_MAPPER.writeValueAsString(allOptions.entrySet().stream()
                    .filter(entry -> StringUtils.isNotBlank(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey,
                            entry -> entry.getValue().stream().map(this::copyOption).collect(Collectors.toList()),
                            (left, _right) -> left, LinkedHashMap::new)));
            updateSiteInformation(Property.professionalProfileSpecialtyOptions, serialized, currentUserId);
            ConfigurationProperties.loadDBValuesIntoConfiguration();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save professional profile specialty options.", e);
        }
    }

    private List<ProfessionalProfileDefinitionForm> parseProfiles(String rawProfiles) {
        if (StringUtils.isBlank(rawProfiles)) {
            return List.of();
        }

        List<ProfessionalProfileDefinitionForm> profiles = new ArrayList<>();
        for (String token : rawProfiles.split(",")) {
            String trimmedToken = StringUtils.trimToEmpty(token);
            if (trimmedToken.isEmpty()) {
                continue;
            }
            String[] parts = trimmedToken.split("\\|", 2);
            String normalizedCode = normalizeCode(parts[0]);
            String name = StringUtils.trimToEmpty(parts.length > 1 ? parts[1] : parts[0]);
            if (StringUtils.isBlank(normalizedCode) || StringUtils.isBlank(name)) {
                continue;
            }
            profiles.add(new ProfessionalProfileDefinitionForm(normalizedCode, name));
        }
        return profiles;
    }

    private String buildUsageMessage(String normalizedCode) {
        long userCount = systemUserService.getAll().stream()
                .map(SystemUser::getProfessionalProfileCode)
                .map(this::normalizeCode)
                .filter(normalizedCode::equals)
                .count();
        if (userCount > 0) {
            return "This professional profile is assigned to one or more users.";
        }

        long providerCount = providerService.getAll().stream()
                .map(Provider::getProfessionalProfileCode)
                .map(this::normalizeCode)
                .filter(normalizedCode::equals)
                .count();
        if (providerCount > 0) {
            return "This professional profile is assigned to one or more provider records.";
        }

        Long notificationCount = entityManager
                .createQuery(
                        "select count(configOption) from NotificationConfigOption configOption where upper(coalesce(configOption.professionalProfileCode, '')) = :profileCode",
                        Long.class)
                .setParameter("profileCode", normalizedCode)
                .getSingleResult();
        if (notificationCount != null && notificationCount > 0) {
            return "This professional profile is used by notification configuration.";
        }

        ProfessionalProfileSettingsForm settings = getSettings();
        if (settings.getOrderProviderProfessionalProfileCodes().contains(normalizedCode)
                || settings.getSampleCollectorProfessionalProfileCodes().contains(normalizedCode)
                || settings.getValidationInterpreterProfessionalProfileCodes().contains(normalizedCode)) {
            return "This professional profile is still referenced by system configuration.";
        }

        return null;
    }

    private String normalizeCode(String rawCode) {
        String trimmed = StringUtils.trimToEmpty(rawCode);
        if (trimmed.isEmpty()) {
            return "";
        }
        String normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_");
        return normalized;
    }
}
