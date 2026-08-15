package org.openelisglobal.reports.action.implementation;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JasperRunManager;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openelisglobal.image.service.ImageService;
import org.openelisglobal.image.valueholder.Image;
import org.openelisglobal.reports.action.implementation.reportBeans.ClinicalPatientData;
import org.openelisglobal.spring.util.SpringContext;

public class DynamicJasperValidationReport extends PatientCILNSPClinical_vreduit {

    private static final String MODE_KEY = "mode";
    private static final String DYNAMIC_MODE = "jasper_dynamic";
    private static final String TEMPLATE_CONTENT_KEY = "templateContent";
    private static final String PARAMETER_DEFINITIONS_KEY = "parameterDefinitions";
    private static final String MAPPINGS_KEY = "mappings";

    private final ImageService imageService = SpringContext.getBean(ImageService.class);

    private JSONObject config = new JSONObject();
    private String templateContent = "";
    private List<TemplateParameterDefinition> parameterDefinitions = Collections.emptyList();

    @Override
    protected String reportFileName() {
        return "PatientClinicalReport";
    }

    @Override
    public void initializeReport(org.openelisglobal.reports.form.ReportForm form) {
        super.initializeReport(form);
        config = parseConfig(form);
        templateContent = StringUtils.defaultString(config.optString(TEMPLATE_CONTENT_KEY));
        parameterDefinitions = parseParameterDefinitions(config);
        if (!errorFound) {
            applyDynamicMappings();
        }
    }

    @Override
    public byte[] runReport() throws java.io.UnsupportedEncodingException, java.io.IOException, java.sql.SQLException,
            IllegalStateException, JRException, java.text.ParseException {
        if (errorFound || StringUtils.isBlank(templateContent)) {
            if (StringUtils.isBlank(templateContent) && !errorFound) {
                add1LineErrorMessage("report.error.message.noPrintableItems");
            }
            return super.runReport();
        }

        JasperReport compiled = JasperCompileManager
                .compileReport(new ByteArrayInputStream(templateContent.getBytes(StandardCharsets.UTF_8)));
        return JasperRunManager.runReportToPdf(compiled, getReportParameters(), new JREmptyDataSource(1));
    }

    @Override
    public net.sf.jasperreports.engine.JRDataSource getReportDataSource() throws IllegalStateException {
        if (errorFound) {
            return new JRBeanCollectionDataSource(errorMsgs);
        }
        return new JREmptyDataSource(1);
    }

    private JSONObject parseConfig(org.openelisglobal.reports.form.ReportForm form) {
        if (form == null || GenericValidator.isBlankOrNull(form.getValidationTemplateConfigJson())) {
            return new JSONObject();
        }
        try {
            JSONObject parsed = new JSONObject(form.getValidationTemplateConfigJson());
            if (!StringUtils.equals(DYNAMIC_MODE, parsed.optString(MODE_KEY))) {
                return new JSONObject();
            }
            return parsed;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private List<TemplateParameterDefinition> parseParameterDefinitions(JSONObject parsedConfig) {
        JSONArray definitions = parsedConfig.optJSONArray(PARAMETER_DEFINITIONS_KEY);
        if (definitions == null || definitions.isEmpty()) {
            return Collections.emptyList();
        }
        List<TemplateParameterDefinition> parsed = new ArrayList<>();
        for (int i = 0; i < definitions.length(); i++) {
            JSONObject row = definitions.optJSONObject(i);
            if (row == null) {
                continue;
            }
            String name = StringUtils.trimToNull(row.optString("name"));
            if (name == null) {
                continue;
            }
            String className = StringUtils.defaultIfBlank(StringUtils.trimToNull(row.optString("className")),
                    "java.lang.String");
            parsed.add(new TemplateParameterDefinition(name, className));
        }
        return parsed;
    }

    private void applyDynamicMappings() {
        JSONObject mappings = config.optJSONObject(MAPPINGS_KEY);
        ClinicalPatientData first = getFirstReportItem();

        for (TemplateParameterDefinition definition : parameterDefinitions) {
            if (definition == null || StringUtils.isBlank(definition.name)) {
                continue;
            }

            if (reportParameters.containsKey(definition.name) && mappings == null) {
                continue;
            }

            JSONObject mapping = mappings == null ? null : mappings.optJSONObject(definition.name);
            Object resolvedValue = resolveMappedValue(mapping, definition.className, first);
            if (resolvedValue != null) {
                reportParameters.put(definition.name, resolvedValue);
            } else if (!reportParameters.containsKey(definition.name)
                    && !"java.io.InputStream".equalsIgnoreCase(definition.className)) {
                reportParameters.put(definition.name, "");
            }
        }
    }

    private Object resolveMappedValue(JSONObject mapping, String className, ClinicalPatientData first) {
        if (mapping == null) {
            return null;
        }

        String type = StringUtils.defaultIfBlank(StringUtils.trimToNull(mapping.optString("type")), "source");
        String value = StringUtils.trimToNull(mapping.optString("value"));

        if ("constant".equalsIgnoreCase(type)) {
            return coerceValue(className, value);
        }
        if ("image".equalsIgnoreCase(type)) {
            if (!"java.io.InputStream".equalsIgnoreCase(className)) {
                return null;
            }
            return resolveImageInputStream(value);
        }
        if ("empty".equalsIgnoreCase(type) || value == null) {
            return null;
        }
        return coerceValue(className, resolveSourceValue(value, first));
    }

    private Object coerceValue(String className, String value) {
        if (StringUtils.isBlank(className)) {
            return value;
        }
        if ("java.lang.String".equalsIgnoreCase(className)) {
            return StringUtils.defaultString(value);
        }
        if ("java.lang.Boolean".equalsIgnoreCase(className) || "boolean".equalsIgnoreCase(className)) {
            return Boolean.valueOf(StringUtils.defaultString(value));
        }
        try {
            if ("java.lang.Integer".equalsIgnoreCase(className) || "int".equalsIgnoreCase(className)) {
                return StringUtils.isBlank(value) ? null : Integer.valueOf(value);
            }
            if ("java.lang.Long".equalsIgnoreCase(className) || "long".equalsIgnoreCase(className)) {
                return StringUtils.isBlank(value) ? null : Long.valueOf(value);
            }
            if ("java.lang.Double".equalsIgnoreCase(className) || "double".equalsIgnoreCase(className)) {
                return StringUtils.isBlank(value) ? null : Double.valueOf(value);
            }
            if ("java.lang.Float".equalsIgnoreCase(className) || "float".equalsIgnoreCase(className)) {
                return StringUtils.isBlank(value) ? null : Float.valueOf(value);
            }
        } catch (Exception e) {
            return null;
        }
        return value;
    }

    private InputStream resolveImageInputStream(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        if (StringUtils.startsWithIgnoreCase(value, "imageId:")) {
            String imageId = StringUtils.trimToNull(value.substring("imageId:".length()));
            if (imageId != null) {
                try {
                    Image image = imageService.get(imageId);
                    return image == null || image.getImage() == null ? null : new ByteArrayInputStream(image.getImage());
                } catch (Exception e) {
                    return null;
                }
            }
        }
        Optional<Image> image = imageService.getImageBySiteInfoName(value);
        return image.isPresent() && image.get().getImage() != null ? new ByteArrayInputStream(image.get().getImage())
                : null;
    }

    private ClinicalPatientData getFirstReportItem() {
        if (reportItems == null || reportItems.isEmpty()) {
            return null;
        }
        return reportItems.get(0);
    }

    private String resolveSourceValue(String source, ClinicalPatientData first) {
        if (StringUtils.isBlank(source)) {
            return "";
        }

        String normalized = source.trim();
        if ("patientName".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPatientName());
        }
        if ("dni".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getDni());
        }
        if ("passportNumber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPassportNumber());
        }
        if ("foreignId".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getForeignId());
        }
        if ("nationalId".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getNationalId());
        }
        if ("subjectNumber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getSubjectNumber());
        }
        if ("sampleCug".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getSampleCug());
        }
        if ("accessionNumber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getAccessionNumber());
        }
        if ("gender".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getGender());
        }
        if ("dob".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getDob());
        }
        if ("patientSiteNumber".equalsIgnoreCase(normalized) || "contact".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPatientSiteNumber());
        }
        if ("prescriber".equalsIgnoreCase(normalized) || "requestingPhysician".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultIfBlank(first.getPrescriber(), first.getContactInfo());
        }
        if ("requesterFirstName".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterFirstName());
        }
        if ("requesterLastName".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterLastName());
        }
        if ("requesterPhone".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterPhone());
        }
        if ("requesterEmail".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterEmail());
        }
        if ("requesterCmp".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterCmp());
        }
        if ("requesterRne".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterRne());
        }
        if ("requesterSpecialty".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getRequesterSpecialty());
        }
        if ("siteInfo".equalsIgnoreCase(normalized) || "referringSite".equalsIgnoreCase(normalized)) {
            return resolveReferenceCenter(first);
        }
        if ("collectionDateTime".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getCollectionDateTime());
        }
        if ("sampleType".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getSampleType());
        }
        if ("orderFinishDate".equalsIgnoreCase(normalized) || "resultDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getOrderFinishDate());
        }
        if ("orderDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getOrderDate());
        }
        if ("testDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getTestDate());
        }
        if ("analysisResult1".equalsIgnoreCase(normalized) || "analysisResult2".equalsIgnoreCase(normalized)) {
            int position = "analysisResult2".equalsIgnoreCase(normalized) ? 1 : 0;
            return resolveAnalysisResult(position);
        }

        if (normalized.startsWith("orderAdditional.") || normalized.startsWith("sampleAdditional.")
                || normalized.startsWith("testAdditional.") || normalized.startsWith("additional.")) {
            return resolveAdditionalValue(normalized);
        }

        String fromAdditional = resolveAdditionalValue(normalized);
        return StringUtils.defaultString(fromAdditional);
    }

    private String resolveAnalysisResult(int position) {
        if (reportItems == null || reportItems.isEmpty()) {
            return "";
        }
        List<String> values = reportItems.stream().map(ClinicalPatientData::getResult).filter(StringUtils::isNotBlank)
                .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));
        return values.size() > position ? values.get(position) : "";
    }

    private String resolveReferenceCenter(ClinicalPatientData first) {
        String siteInfo = sanitizeSiteInfo(first == null ? "" : first.getSiteInfo());
        if (StringUtils.isNotBlank(siteInfo)) {
            return siteInfo;
        }
        List<String> candidates = new ArrayList<>();
        candidates.add("orderAdditional.reference_center");
        candidates.add("orderAdditional.centro_referencia");
        candidates.add("orderAdditional.referring_site");
        candidates.add("orderAdditional.referringSite");
        candidates.add("reference_center");
        candidates.add("centro_referencia");
        candidates.add("referring_site");
        candidates.add("referringSite");
        for (String key : candidates) {
            String value = resolveAdditionalValue(key);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String sanitizeSiteInfo(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        String sanitized = raw.replace("<br/>", " ").replace("<br />", " ").replace("<br>", " ");
        return sanitized.replaceAll("\\s+", " ").trim();
    }

    private String resolveAdditionalValue(String key) {
        if (StringUtils.isBlank(key) || reportItems == null || reportItems.isEmpty()) {
            return "";
        }

        List<String> lookupKeys = expandAdditionalFieldLookupKeys(key);
        for (ClinicalPatientData data : reportItems) {
            Map<String, String> values = data.getAdditionalFieldValues();
            if (values == null || values.isEmpty()) {
                continue;
            }
            for (String lookupKey : lookupKeys) {
                String value = StringUtils.trimToNull(values.get(lookupKey));
                if (value != null) {
                    return value;
                }
            }
        }
        return "";
    }

    private List<String> expandAdditionalFieldLookupKeys(String baseKey) {
        if (StringUtils.isBlank(baseKey)) {
            return Collections.emptyList();
        }

        String trimmed = baseKey.trim();
        LinkedHashSet<String> lookupKeys = new LinkedHashSet<>();
        lookupKeys.add(trimmed);

        if (trimmed.startsWith("testAdditional.")) {
            String rawKey = trimmed.substring("testAdditional.".length());
            if (StringUtils.isNotBlank(rawKey)) {
                lookupKeys.add(rawKey);
                lookupKeys.add("additional." + rawKey);
            }
            return new ArrayList<>(lookupKeys);
        }

        if (trimmed.startsWith("additional.")) {
            String rawKey = trimmed.substring("additional.".length());
            if (StringUtils.isNotBlank(rawKey)) {
                lookupKeys.add(rawKey);
                lookupKeys.add("testAdditional." + rawKey);
            }
            return new ArrayList<>(lookupKeys);
        }

        if (trimmed.startsWith("orderAdditional.") || trimmed.startsWith("sampleAdditional.")) {
            String rawKey = trimmed.substring(trimmed.indexOf('.') + 1);
            if (StringUtils.isNotBlank(rawKey)) {
                lookupKeys.add(rawKey);
            }
            return new ArrayList<>(lookupKeys);
        }

        lookupKeys.add("testAdditional." + trimmed);
        lookupKeys.add("additional." + trimmed);
        return new ArrayList<>(lookupKeys);
    }

    @SuppressWarnings("unused")
    private String normalizeAliasToken(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String unaccented = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return unaccented.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static class TemplateParameterDefinition {
        private final String name;
        private final String className;

        private TemplateParameterDefinition(String name, String className) {
            this.name = name;
            this.className = className;
        }
    }
}
