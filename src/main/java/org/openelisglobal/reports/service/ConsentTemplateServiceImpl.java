package org.openelisglobal.reports.service;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.json.JSONObject;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.openelisglobal.reportdefinition.service.ReportDefinitionService;
import org.openelisglobal.reportdefinition.valueholder.ReportDefinition;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.openelisglobal.reports.form.ConsentPreviewForm.PatientInfo;
import org.openelisglobal.reports.form.ConsentPreviewForm.ProviderInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsentTemplateServiceImpl implements ConsentTemplateService {

    private static final Logger logger = LoggerFactory.getLogger(ConsentTemplateServiceImpl.class);

    private static final String CATEGORY = "consent_template";
    private static final String REPORT_ID = "RPT-CONSENT-TEMPLATE";
    private static final String TEMPLATE_RESOURCE = "reports/consent/consent-template.jrxml";

    private static final String FIELDS_KEY = "fields";
    private static final String FIELD_CUG = "cug";
    private static final String FIELD_PATIENT_NAME = "patientName";
    private static final String FIELD_PATIENT_DNI = "patientDni";
    private static final String FIELD_PROVIDER_NAME = "providerName";
    private static final String FIELD_PROVIDER_DNI = "providerDni";
    private static final String FIELD_DATE = "date";
    private static final String FIELD_TESTS = "tests";

    private static final String SOURCE_PATIENT_NAME = "patient.fullName";
    private static final String SOURCE_PATIENT_NATIONAL_ID = "patient.nationalId";
    private static final String SOURCE_PROVIDER_NAME = "provider.fullName";
    private static final String SOURCE_PROVIDER_DNI = "provider.dni";
    private static final String SOURCE_ORDER_DATE = "order.date";
    private static final String SOURCE_TESTS_SELECTED = "tests.selectedNames";
    private static final String SOURCE_SAMPLE_CUG = "sample.cug";
    private static final String SOURCE_ORDER_ADDITIONAL_PREFIX = "orderAdditional.";

    @Autowired
    private ConsentJasperService jasper;

    @Autowired
    private ConsentSourceService sources;

    @Autowired
    private ReportDefinitionService reportDefinitionService;

    @Override
    @Transactional(readOnly = true)
    public ConsentTemplateConfigForm getConfig() {
        ReportDefinition definition = selectLatestDefinition();
        ConsentTemplateConfigForm form = toForm(definition);
        if (form == null) {
            form = new ConsentTemplateConfigForm();
            form.setId(REPORT_ID);
            form.setName("Consent Template");
            form.setIsActive(Boolean.TRUE);
            form.setFields(defaultFieldSources());
        } else {
            form.setFields(normalizeFieldSources(form.getFields()));
        }
        applyTemplate(form);
        return form;
    }

    private void applyTemplate(ConsentTemplateConfigForm form) {
        String content = form.getTemplateContent();
        String filename = form.getTemplateFilename();
        if (StringUtils.isBlank(content)) {
            try (InputStream in = new ClassPathResource(TEMPLATE_RESOURCE).getInputStream()) {
                content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                filename = "consent-template.jrxml";
            } catch (Exception e) {
                throw new IllegalStateException("Unable to load consent template", e);
            }
        }
        ConsentTemplateConfigForm parsed = jasper.parse(filename, content);
        form.setTemplateContent(parsed.getTemplateContent());
        form.setTemplateFilename(parsed.getTemplateFilename());
        form.setParameters(parsed.getParameters());
        Map<String, String> mappings = new LinkedHashMap<>();
        for (Map<String, String> parameter : parsed.getParameters()) {
            String id = parameter.get("id");
            mappings.put(id, form.getFields() == null ? "" : StringUtils.trimToEmpty(form.getFields().get(id)));
        }
        form.setFields(mappings);
    }

    @Override
    public ConsentTemplateConfigForm parseTemplate(String filename, String content) {
        return jasper.parse(filename, content);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getOptions() {
        return Map.of("sources", sources.getSources());
    }

    @Override
    @Transactional
    public ConsentTemplateConfigForm saveConfig(String sysUserId, ConsentTemplateConfigForm form) {
        if (form == null) {
            return null;
        }
        applyTemplate(form);
        Map<String, String> fields = form.getFields();
        Set<String> allowed = new LinkedHashSet<>();
        sources.getSources().forEach(source -> allowed.add((String) source.get("id")));
        if (fields.values().stream().anyMatch(value -> StringUtils.isNotBlank(value) && !allowed.contains(value))) {
            throw new IllegalArgumentException("consent.template.error.source");
        }
        Optional<ReportDefinition> existingDefinition = reportDefinitionService.getMatch("id", REPORT_ID);
        ReportDefinition definition = existingDefinition.orElse(null);
        boolean isCreate = definition == null;
        if (isCreate) {
            definition = new ReportDefinition();
            definition.setId(REPORT_ID);
            definition.setCreatedDate(Timestamp.from(Instant.now()));
            definition.setCreatedBy(sysUserId);
        }
        definition.setCategory(CATEGORY);
        definition.setName(StringUtils.defaultIfBlank(form.getName(), "Consent Template"));
        definition.setDescription("Informed consent template");
        definition.setDefinitionJson(buildDefinitionJson(form));
        definition.setIsActive(form.getIsActive() == null ? Boolean.TRUE : form.getIsActive());
        definition.setSysUserId(sysUserId);

        if (isCreate) {
            reportDefinitionService.insert(definition);
        } else {
            reportDefinitionService.update(definition);
        }

        ConsentTemplateConfigForm saved = toForm(definition);
        if (saved != null) {
            saved.setFields(fields);
            saved.setParameters(form.getParameters());
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateConsentPdf(ConsentPreviewForm form) {
        ConsentTemplateConfigForm config = getConfig();
        if (Boolean.FALSE.equals(config.getIsActive())) {
            throw new IllegalArgumentException("consent.template.error.inactive");
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        Set<String> allowed = new LinkedHashSet<>();
        Map<String, Map<String, Object>> catalog = new LinkedHashMap<>();
        sources.getSources(false).forEach(source -> {
            allowed.add((String) source.get("id"));
            catalog.put((String) source.get("id"), source);
        });
        config.getFields().values().stream().filter(v -> v.startsWith("questionnaire.")).forEach(allowed::add);
        config.getFields().forEach((target, source) -> {
            String value = "";
            if (allowed.contains(source)) {
                if (form != null && form.getSourceValues() != null && form.getSourceValues().containsKey(source)) {
                    value = sanitizeValue(form.getSourceValues().get(source));
                } else if (SOURCE_TESTS_SELECTED.equals(source)) {
                    value = String.join("\n", resolveTests(source, form));
                } else {
                    value = resolveLegacyDisplayValue(source, form, catalog.get(source));
                }
            }
            boolean booleanTarget = config.getParameters().stream()
                    .anyMatch(p -> target.equals(p.get("id")) && "java.lang.Boolean".equals(p.get("className")));
            if (booleanTarget && !value.isBlank() && form != null && form.getBooleanValues() != null
                    && form.getBooleanValues().containsKey(source)) {
                value = String.valueOf(form.getBooleanValues().get(source));
            }
            resolved.put(target, value);
        });
        return jasper.render(config, resolved);
    }

    private String resolveLegacyDisplayValue(String source, ConsentPreviewForm form, Map<String, Object> definition) {
        String raw = resolveValue(source, form);
        if (definition == null || !"additional".equals(definition.get("format"))) {
            return raw;
        }
        if ("DOCUMENT".equals(definition.get("fieldType"))) {
            return "";
        }
        Object options = definition.get("options");
        if (options instanceof List<?> && (!((List<?>) options).isEmpty()
                || Set.of("SELECT", "MULTISELECT", "RADIO", "USER", "SYSTEM_USER_BIOLOGIST_SELECT")
                        .contains(String.valueOf(definition.get("fieldType"))))) {
            List<String> labels = new ArrayList<>();
            for (String entry : raw.split(",")) {
                for (Object item : (List<?>) options) {
                    if (item instanceof Map<?, ?> && entry.trim().equals(((Map<?, ?>) item).get("optionKey"))) {
                        Object label = ((Map<?, ?>) item).get("optionLabel");
                        if (label != null)
                            labels.add(label.toString());
                    }
                }
            }
            return String.join(", ", labels);
        }
        return raw;
    }

    private ReportDefinition selectLatestDefinition() {
        List<ReportDefinition> definitions = reportDefinitionService.getDefinitionsByCategory(CATEGORY);
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        ReportDefinition selected = null;
        for (ReportDefinition candidate : definitions) {
            if (candidate == null) {
                continue;
            }
            if (selected == null) {
                selected = candidate;
                continue;
            }
            Timestamp selectedTimestamp = selected.getLastupdated() != null ? selected.getLastupdated()
                    : selected.getCreatedDate();
            Timestamp candidateTimestamp = candidate.getLastupdated() != null ? candidate.getLastupdated()
                    : candidate.getCreatedDate();
            if (selectedTimestamp == null) {
                selected = candidate;
            } else if (candidateTimestamp != null && candidateTimestamp.after(selectedTimestamp)) {
                selected = candidate;
            }
        }
        return selected;
    }

    private ConsentTemplateConfigForm toForm(ReportDefinition definition) {
        if (definition == null || GenericValidator.isBlankOrNull(definition.getDefinitionJson())) {
            return null;
        }
        try {
            JSONObject parsed = new JSONObject(definition.getDefinitionJson());
            ConsentTemplateConfigForm form = new ConsentTemplateConfigForm();
            form.setId(definition.getId());
            form.setName(definition.getName());
            form.setIsActive(definition.getIsActive());
            form.setTemplateContent(parsed.optString("templateContent", null));
            form.setTemplateFilename(parsed.optString("templateFilename", null));
            JSONObject fields = parsed.optJSONObject(FIELDS_KEY);
            if (fields != null) {
                Map<String, String> fieldSources = new LinkedHashMap<>();
                for (String key : fields.keySet()) {
                    String value = StringUtils.trimToNull(fields.optString(key));
                    fieldSources.put(key, StringUtils.defaultString(value));
                }
                form.setFields(fieldSources);
            }
            return form;
        } catch (Exception e) {
            logger.warn("Skipping invalid consent template config", e);
            return null;
        }
    }

    private Map<String, String> defaultFieldSources() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put(FIELD_CUG, SOURCE_SAMPLE_CUG);
        defaults.put(FIELD_PATIENT_NAME, SOURCE_PATIENT_NAME);
        defaults.put(FIELD_PATIENT_DNI, SOURCE_PATIENT_NATIONAL_ID);
        defaults.put(FIELD_PROVIDER_NAME, SOURCE_PROVIDER_NAME);
        defaults.put(FIELD_PROVIDER_DNI, SOURCE_PROVIDER_DNI);
        defaults.put(FIELD_DATE, SOURCE_ORDER_DATE);
        defaults.put(FIELD_TESTS, SOURCE_TESTS_SELECTED);
        return defaults;
    }

    private Map<String, String> normalizeFieldSources(Map<String, String> raw) {
        Map<String, String> normalized = new LinkedHashMap<>(defaultFieldSources());
        if (raw == null) {
            return normalized;
        }
        raw.forEach((key, value) -> {
            String cleanedKey = StringUtils.trimToNull(key);
            String cleanedValue = StringUtils.trimToNull(value);
            if (cleanedKey != null) {
                normalized.put(cleanedKey, StringUtils.defaultString(cleanedValue));
            }
        });
        return normalized;
    }

    private String buildDefinitionJson(ConsentTemplateConfigForm form) {
        JSONObject root = new JSONObject();
        root.put(FIELDS_KEY, new JSONObject(form.getFields()));
        root.put("templateContent", form.getTemplateContent());
        root.put("templateFilename", form.getTemplateFilename());
        return root.toString();
    }

    private String resolveValue(String source, ConsentPreviewForm form) {
        if (GenericValidator.isBlankOrNull(source) || form == null) {
            return "";
        }
        if (SOURCE_PATIENT_NAME.equals(source)) {
            return buildFullName(form.getPatient(), null);
        }
        if (SOURCE_PATIENT_NATIONAL_ID.equals(source)) {
            return sanitizeValue(form.getPatient() == null ? null : form.getPatient().getNationalId());
        }
        if (SOURCE_PROVIDER_NAME.equals(source)) {
            return buildFullName(null, form.getProvider());
        }
        if (SOURCE_PROVIDER_DNI.equals(source)) {
            return sanitizeValue(form.getProvider() == null ? null : form.getProvider().getDni());
        }
        if (SOURCE_ORDER_DATE.equals(source)) {
            return formatDate(form.getOrderDate());
        }
        if (SOURCE_SAMPLE_CUG.equals(source) || "accessionNumber".equalsIgnoreCase(source)) {
            return sanitizeValue(form.getCug());
        }
        if (source.startsWith(SOURCE_ORDER_ADDITIONAL_PREFIX)) {
            String key = source.substring(SOURCE_ORDER_ADDITIONAL_PREFIX.length());
            return resolveAdditionalValue(form.getOrderAdditionalFieldValues(), key);
        }
        return "";
    }

    private List<String> resolveTests(String source, ConsentPreviewForm form) {
        if (!SOURCE_TESTS_SELECTED.equals(source) || form == null) {
            return Collections.emptyList();
        }
        return sanitizeList(form.getSelectedTests());
    }

    private String resolveAdditionalValue(Map<String, String> values, String key) {
        if (values == null || GenericValidator.isBlankOrNull(key)) {
            return "";
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry == null || GenericValidator.isBlankOrNull(entry.getKey())) {
                continue;
            }
            if (entry.getKey().equalsIgnoreCase(key)) {
                return sanitizeValue(entry.getValue());
            }
        }
        return "";
    }

    private String buildFullName(PatientInfo patient, ProviderInfo provider) {
        String fullName = patient != null ? patient.getFullName() : provider == null ? null : provider.getFullName();
        if (!GenericValidator.isBlankOrNull(fullName)) {
            return fullName.trim();
        }
        String firstName = patient != null ? patient.getFirstName() : provider == null ? null : provider.getFirstName();
        String lastName = patient != null ? patient.getLastName() : provider == null ? null : provider.getLastName();
        firstName = sanitizeValue(firstName);
        lastName = sanitizeValue(lastName);
        if (!GenericValidator.isBlankOrNull(lastName) && !GenericValidator.isBlankOrNull(firstName)) {
            return lastName + ", " + firstName;
        }
        return GenericValidator.isBlankOrNull(lastName) ? StringUtils.defaultString(firstName) : lastName;
    }

    private String sanitizeValue(String value) {
        return GenericValidator.isBlankOrNull(value) ? "" : value.trim();
    }

    private List<String> sanitizeList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String entry : raw) {
            String cleaned = sanitizeValue(entry);
            if (!GenericValidator.isBlankOrNull(cleaned)) {
                unique.add(cleaned);
            }
        }
        return new ArrayList<>(unique);
    }

    private String formatDate(String raw) {
        String cleaned = sanitizeValue(raw);
        if (GenericValidator.isBlankOrNull(cleaned)) {
            return LocalDate.now().format(DateTimeFormatter.ofPattern("d/M/yyyy"));
        }
        try {
            LocalDate parsed = LocalDate.parse(cleaned);
            return parsed.format(DateTimeFormatter.ofPattern("d/M/yyyy"));
        } catch (Exception e) {
            return cleaned;
        }
    }

}
