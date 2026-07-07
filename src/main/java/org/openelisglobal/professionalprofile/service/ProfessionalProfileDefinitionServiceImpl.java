package org.openelisglobal.professionalprofile.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.notification.valueholder.NotificationConfigOption;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileDefinitionForm;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileSettingsForm;
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

    @Autowired
    private SiteInformationService siteInformationService;

    @Autowired
    private SystemUserService systemUserService;

    @Autowired
    private ProviderService providerService;

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
        return getProfiles().stream()
                .filter(profile -> normalizedCode.equals(profile.getCode()))
                .findFirst()
                .orElse(null);
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
        return new ProfessionalProfileDefinitionForm(normalizedCode, normalizedForm.getName());
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
    }

    @Override
    @Transactional(readOnly = true)
    public ProfessionalProfileSettingsForm getSettings() {
        ProfessionalProfileSettingsForm form = new ProfessionalProfileSettingsForm();
        form.setOrderProviderProfessionalProfileCode(
                normalizeCode(ConfigurationProperties.getInstance().getPropertyValue(Property.orderProviderProfessionalProfileCode)));
        form.setSampleCollectorProfessionalProfileCode(
                normalizeCode(ConfigurationProperties.getInstance().getPropertyValue(Property.sampleCollectorProfessionalProfileCode)));
        form.setValidationInterpreterProfessionalProfileCode(normalizeCode(
                ConfigurationProperties.getInstance().getPropertyValue(Property.validationInterpreterProfessionalProfileCode)));
        return form;
    }

    @Override
    public ProfessionalProfileSettingsForm updateSettings(ProfessionalProfileSettingsForm form, String currentUserId) {
        List<String> availableCodes = getProfiles().stream().map(ProfessionalProfileDefinitionForm::getCode).toList();
        String orderProviderCode = normalizeOptionalConfiguredCode(form.getOrderProviderProfessionalProfileCode(), availableCodes,
                "Order requester profile");
        String sampleCollectorCode = normalizeOptionalConfiguredCode(form.getSampleCollectorProfessionalProfileCode(),
                availableCodes, "Sample collector profile");
        String validationInterpreterCode = normalizeOptionalConfiguredCode(
                form.getValidationInterpreterProfessionalProfileCode(), availableCodes, "Validation interpreter profile");

        updateSiteInformation(Property.orderProviderProfessionalProfileCode, orderProviderCode, currentUserId);
        updateSiteInformation(Property.sampleCollectorProfessionalProfileCode, sampleCollectorCode, currentUserId);
        updateSiteInformation(Property.validationInterpreterProfessionalProfileCode, validationInterpreterCode, currentUserId);
        ConfigurationProperties.loadDBValuesIntoConfiguration();
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
        return new ProfessionalProfileDefinitionForm(normalizedCode, StringUtils.trim(form.getName()));
    }

    private String normalizeOptionalConfiguredCode(String code, List<String> availableCodes, String label) {
        String normalizedCode = normalizeCode(code);
        if (StringUtils.isBlank(normalizedCode)) {
            return "";
        }
        if (!availableCodes.contains(normalizedCode)) {
            throw new IllegalArgumentException(label + " must reference an existing professional profile.");
        }
        return normalizedCode;
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

    private void updateSiteInformation(Property property, String value, String currentUserId) {
        SiteInformation siteInformation = siteInformationService.getSiteInformationByName(property.getDBName());
        if (siteInformation == null) {
            throw new IllegalStateException("Configuration entry '" + property.getDBName() + "' was not found.");
        }
        siteInformation.setValue(StringUtils.defaultString(value));
        siteInformation.setSysUserId(currentUserId);
        siteInformationService.persistData(siteInformation, false);
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
        if (normalizedCode.equals(settings.getOrderProviderProfessionalProfileCode())
                || normalizedCode.equals(settings.getSampleCollectorProfessionalProfileCode())
                || normalizedCode.equals(settings.getValidationInterpreterProfessionalProfileCode())) {
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
