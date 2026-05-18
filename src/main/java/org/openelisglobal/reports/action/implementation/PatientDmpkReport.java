package org.openelisglobal.reports.action.implementation;

import java.io.ByteArrayInputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperRunManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.image.service.ImageService;
import org.openelisglobal.image.valueholder.Image;
import org.openelisglobal.reports.action.implementation.reportBeans.ClinicalPatientData;
import org.openelisglobal.reports.form.ReportForm;
import org.openelisglobal.spring.util.SpringContext;

/**
 * DMPK-specific patient report entry point.
 *
 * <p>
 * Initial implementation reuses the current validation clinical report behavior
 * and allows wiring a dedicated Jasper template file.
 */
public class PatientDmpkReport extends PatientCILNSPClinical_vreduit {

    private static final String DEFAULT_NOT_REGISTERED = "NO REGISTRADO";
    private static final String DEFAULT_PROCEDURE_TEXT = "El analisis molecular del gen DMPK consiste en la amplificacion "
            + "por PCR y la discriminacion de la presencia o ausencia del alelo mutado mayor o igual a 50 repeticiones CTG.";
    private static final String DEFAULT_INTERPRETATION_TEXT = "El diagnostico molecular se define por el numero "
            + "de repeticiones CTG del alelo mas largo.";
    private static final List<String> SECTION_PRINT_ORDER = Arrays.asList("PATIENT", "REQUESTING_PHYSICIAN", "SAMPLE",
            "MOLECULAR_RESULT", "CONCLUSION");
    private final ImageService imageService = SpringContext.getBean(ImageService.class);
    private List<ClinicalPatientData> scopedReportItems;
    private String dmpkJrxmlPath;
    private String dmpkJasperPath;

    @Override
    protected String reportFileName() {
        return "PatientDmpkReport";
    }

    @Override
    public void setReportPath(String path) {
        super.setReportPath(path);
        dmpkJrxmlPath = path + reportFileName() + ".jrxml";
        dmpkJasperPath = path + reportFileName() + ".jasper";
    }

    @Override
    public byte[] runReport() throws java.io.UnsupportedEncodingException, java.io.IOException, java.sql.SQLException,
            IllegalStateException, JRException, java.text.ParseException {
        if (StringUtils.isNotBlank(dmpkJrxmlPath) && StringUtils.isNotBlank(dmpkJasperPath)) {
            JasperCompileManager.compileReportToFile(dmpkJrxmlPath, dmpkJasperPath);
            return JasperRunManager.runReportToPdf(dmpkJasperPath, getReportParameters(), getReportDataSource());
        }
        return super.runReport();
    }

    @Override
    public void initializeReport(ReportForm form) {
        super.initializeReport(form);
        populateDmpkParameters(form);
    }

    private void populateDmpkParameters(ReportForm form) {
        if (reportParameters == null) {
            return;
        }
        scopedReportItems = null;
        scopedReportItems = getScopedReportItems();

        ClinicalPatientData first = scopedReportItems.isEmpty() ? null : scopedReportItems.get(0);
        JSONObject config = parseConfig(form);
        applyHeaderImageOverrides(config);

        String allele1 = resolveSlotValue(config, "alelo1",
                Arrays.asList("alelo_1", "alelo1", "allele_1", "allele1", "dmpk_alelo_1"), DEFAULT_NOT_REGISTERED);
        String allele2 = resolveSlotValue(config, "alelo2",
                Arrays.asList("alelo_2", "alelo2", "allele_2", "allele2", "dmpk_alelo_2"), DEFAULT_NOT_REGISTERED);

        // Fallback to existing result values when additional fields are not yet
        // configured.
        if (DEFAULT_NOT_REGISTERED.equals(allele1) || DEFAULT_NOT_REGISTERED.equals(allele2)) {
            List<String> resultFallback = scopedReportItems.stream().map(ClinicalPatientData::getResult)
                    .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
            if (DEFAULT_NOT_REGISTERED.equals(allele1) && resultFallback.size() > 0) {
                allele1 = resultFallback.get(0);
            }
            if (DEFAULT_NOT_REGISTERED.equals(allele2) && resultFallback.size() > 1) {
                allele2 = resultFallback.get(1);
            }
        }

        String longestAllele = getLongestAllele(allele1, allele2);
        String conclusionText = resolveConstant(config, "conclusionText",
                "La presencia de " + longestAllele + " repeticiones CTG requiere correlacion clinica especializada.");

        Map<String, String> fixedFieldValues = new HashMap<>();
        fixedFieldValues.put("patientName", first == null ? "" : StringUtils.defaultString(first.getPatientName()));
        fixedFieldValues.put("dni", first == null ? "" : StringUtils.defaultString(first.getNationalId()));
        fixedFieldValues.put("hc", first == null ? "" : StringUtils.defaultString(first.getSubjectNumber()));
        fixedFieldValues.put("cug", first == null ? "" : StringUtils.defaultString(first.getAccessionNumber()));
        fixedFieldValues.put("gender", first == null ? "" : StringUtils.defaultString(first.getGender()));
        fixedFieldValues.put("birthDate", first == null ? "" : StringUtils.defaultString(first.getDob()));
        fixedFieldValues.put("contact", first == null ? "" : StringUtils.defaultString(first.getPatientSiteNumber()));
        fixedFieldValues.put("requestingPhysician",
                first == null ? "" : StringUtils.defaultString(first.getPrescriber(), first.getContactInfo()));
        fixedFieldValues.put("requesterCmp", first == null ? "" : StringUtils.defaultString(first.getRequesterCmp()));
        fixedFieldValues.put("requesterRne", first == null ? "" : StringUtils.defaultString(first.getRequesterRne()));
        fixedFieldValues.put("requesterSpecialty",
                first == null ? "" : StringUtils.defaultString(first.getRequesterSpecialty()));
        String referenceCenter = resolveReferenceCenter(first);
        fixedFieldValues.put("referenceCenter",
                StringUtils.isBlank(referenceCenter) ? DEFAULT_NOT_REGISTERED : referenceCenter);
        fixedFieldValues.put("collectionDate",
                first == null ? "" : StringUtils.defaultString(first.getCollectionDateTime()));
        fixedFieldValues.put("sampleStatus", resolveConstant(config, "sampleStatus", "ACEPTADA"));
        fixedFieldValues.put("sampleSource", first == null ? "" : StringUtils.defaultString(first.getSampleType()));
        fixedFieldValues.put("allele1", allele1);
        fixedFieldValues.put("allele2", allele2);
        fixedFieldValues.put("resultDate", resolveResultDate(first, config));

        Map<String, String> fixedFieldLabels = new HashMap<>();
        fixedFieldLabels.put("dni", "DNI");
        fixedFieldLabels.put("hc", "HC");
        fixedFieldLabels.put("cug", "CUG");
        fixedFieldLabels.put("gender", "GENERO");
        fixedFieldLabels.put("birthDate", "FECHA DE NACIMIENTO");
        fixedFieldLabels.put("contact", "CONTACTO");
        fixedFieldLabels.put("requestingPhysician", "MEDICO SOLICITANTE");
        fixedFieldLabels.put("requesterCmp", "CMP");
        fixedFieldLabels.put("requesterRne", "RNE");
        fixedFieldLabels.put("requesterSpecialty", "ESPECIALIDAD");
        fixedFieldLabels.put("referenceCenter", "CENTRO DE REFERENCIA");
        fixedFieldLabels.put("collectionDate", "Fecha de toma de muestra:");
        fixedFieldLabels.put("sampleStatus", "Estado de la muestra:");
        fixedFieldLabels.put("sampleSource", "Fuente:");
        fixedFieldLabels.put("allele1", "ALELO 1:");
        fixedFieldLabels.put("allele2", "ALELO 2:");
        fixedFieldLabels.put("resultDate", "Fecha de emision de resultados:");

        applySectionFieldConfiguration(config, first, fixedFieldValues, fixedFieldLabels);

        reportParameters.put("dmpkPatientName", fixedFieldValues.get("patientName"));
        reportParameters.put("dmpkDni", fixedFieldValues.get("dni"));
        reportParameters.put("dmpkHc", fixedFieldValues.get("hc"));
        reportParameters.put("dmpkCug", fixedFieldValues.get("cug"));
        reportParameters.put("dmpkGender", fixedFieldValues.get("gender"));
        reportParameters.put("dmpkBirthDate", fixedFieldValues.get("birthDate"));
        reportParameters.put("dmpkContact", fixedFieldValues.get("contact"));
        reportParameters.put("dmpkRequestingPhysician", fixedFieldValues.get("requestingPhysician"));
        reportParameters.put("dmpkRequesterCmp", fixedFieldValues.get("requesterCmp"));
        reportParameters.put("dmpkRequesterRne", fixedFieldValues.get("requesterRne"));
        reportParameters.put("dmpkRequesterSpecialty", fixedFieldValues.get("requesterSpecialty"));
        reportParameters.put("dmpkReferenceCenter", fixedFieldValues.get("referenceCenter"));
        reportParameters.put("dmpkCollectionDate", fixedFieldValues.get("collectionDate"));
        reportParameters.put("dmpkSampleStatus", fixedFieldValues.get("sampleStatus"));
        reportParameters.put("dmpkSampleSource", fixedFieldValues.get("sampleSource"));
        reportParameters.put("dmpkProcedureText", resolveConstant(config, "procedureText", DEFAULT_PROCEDURE_TEXT));
        reportParameters.put("dmpkAllele1", fixedFieldValues.get("allele1"));
        reportParameters.put("dmpkAllele2", fixedFieldValues.get("allele2"));
        reportParameters.put("dmpkResultDate", fixedFieldValues.get("resultDate"));
        reportParameters.put("dmpkInterpretationText",
                resolveConstant(config, "interpretationText", DEFAULT_INTERPRETATION_TEXT));
        reportParameters.put("dmpkConclusionText", conclusionText);
        reportParameters.put("dmpkDeliveryDate", resolveConstant(config, "deliveryDate", ""));
        reportParameters.put("dmpkAnalyzedBy", resolveConstant(config, "analyzedBy", ""));
        reportParameters.put("dmpkInterpretedBy", resolveConstant(config, "interpretedBy", ""));
        reportParameters.put("dmpkFooterLeft", resolveConstant(config, "footerLeft", "Servicio de Neurogenetica"));
        reportParameters.put("dmpkFooterRight", resolveConstant(config, "footerRight", "Lima, Peru"));

        reportParameters.put("dmpkLabelDni", fixedFieldLabels.get("dni"));
        reportParameters.put("dmpkLabelHc", fixedFieldLabels.get("hc"));
        reportParameters.put("dmpkLabelCug", fixedFieldLabels.get("cug"));
        reportParameters.put("dmpkLabelGender", fixedFieldLabels.get("gender"));
        reportParameters.put("dmpkLabelBirthDate", fixedFieldLabels.get("birthDate"));
        reportParameters.put("dmpkLabelContact", fixedFieldLabels.get("contact"));
        reportParameters.put("dmpkLabelRequestingPhysician", fixedFieldLabels.get("requestingPhysician"));
        reportParameters.put("dmpkLabelRequesterCmp", fixedFieldLabels.get("requesterCmp"));
        reportParameters.put("dmpkLabelRequesterRne", fixedFieldLabels.get("requesterRne"));
        reportParameters.put("dmpkLabelRequesterSpecialty", fixedFieldLabels.get("requesterSpecialty"));
        reportParameters.put("dmpkLabelReferenceCenter", fixedFieldLabels.get("referenceCenter"));
        reportParameters.put("dmpkLabelCollectionDate", fixedFieldLabels.get("collectionDate"));
        reportParameters.put("dmpkLabelSampleStatus", fixedFieldLabels.get("sampleStatus"));
        reportParameters.put("dmpkLabelSampleSource", fixedFieldLabels.get("sampleSource"));
        reportParameters.put("dmpkLabelAllele1", fixedFieldLabels.get("allele1"));
        reportParameters.put("dmpkLabelAllele2", fixedFieldLabels.get("allele2"));
        reportParameters.put("dmpkLabelResultDate", fixedFieldLabels.get("resultDate"));
    }

    private void applySectionFieldConfiguration(JSONObject config, ClinicalPatientData first,
            Map<String, String> fixedFieldValues, Map<String, String> fixedFieldLabels) {
        List<ConfiguredSectionField> configured = parseConfiguredSectionFields(config);
        if (configured.isEmpty()) {
            reportParameters.put("dmpkConfiguredSectionFields", "");
            return;
        }

        Map<String, List<String>> sectionLines = new LinkedHashMap<>();
        for (String section : SECTION_PRINT_ORDER) {
            sectionLines.put(section, new ArrayList<>());
        }

        for (ConfiguredSectionField field : configured) {
            if (!field.enabled) {
                continue;
            }
            String normalizedKey = normalizeFixedFieldKey(field.key, field.source);
            String resolvedValue = resolveSourceValue(field.source, first, fixedFieldValues);
            if (fixedFieldValues.containsKey(normalizedKey)) {
                fixedFieldValues.put(normalizedKey, StringUtils.defaultString(resolvedValue));
                if (StringUtils.isNotBlank(field.label) && fixedFieldLabels.containsKey(normalizedKey)) {
                    fixedFieldLabels.put(normalizedKey, field.label);
                }
                continue;
            }
            String line = StringUtils.defaultString(field.label, field.key) + ": "
                    + StringUtils.defaultString(resolvedValue, DEFAULT_NOT_REGISTERED);
            sectionLines.computeIfAbsent(field.section, key -> new ArrayList<>()).add(line);
        }

        String dynamicSectionText = sectionLines.entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> entry.getKey() + ": " + String.join(" | ", entry.getValue()))
                .collect(Collectors.joining("\n"));
        reportParameters.put("dmpkConfiguredSectionFields", dynamicSectionText);
    }

    private String normalizeFixedFieldKey(String key, String source) {
        String normalizedSource = normalizeAliasToken(source);
        if ("requestercmp".equals(normalizedSource) || "providercmp".equals(normalizedSource)) {
            return "requesterCmp";
        }
        if ("requesterrne".equals(normalizedSource) || "providerrne".equals(normalizedSource)) {
            return "requesterRne";
        }
        if ("requesterspecialty".equals(normalizedSource) || "providerspecialty".equals(normalizedSource)) {
            return "requesterSpecialty";
        }
        if ("requestingphysician".equals(normalizedSource) || "prescriber".equals(normalizedSource)) {
            return "requestingPhysician";
        }

        String normalized = normalizeAliasToken(key);
        if ("cmp".equals(normalized) || normalized.endsWith("cmp") || normalized.contains("requestercmp")
                || normalized.contains("providercmp")) {
            return "requesterCmp";
        }
        if ("rne".equals(normalized) || normalized.endsWith("rne") || normalized.contains("requesterrne")
                || normalized.contains("providerrne")) {
            return "requesterRne";
        }
        if ("specialty".equals(normalized) || "especialidad".equals(normalized) || normalized.contains("specialty")
                || normalized.contains("especialidad")) {
            return "requesterSpecialty";
        }
        if ("medico".equals(normalized) || "requester".equals(normalized) || normalized.contains("requestingphysician")
                || normalized.contains("medicosolicitante")) {
            return "requestingPhysician";
        }
        return key;
    }

    private String normalizeAliasToken(String value) {
        if (StringUtils.isBlank(value)) {
            return "";
        }
        String unaccented = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return unaccented.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String resolveSourceValue(String source, ClinicalPatientData first, Map<String, String> fixedFieldValues) {
        if (StringUtils.isBlank(source)) {
            return "";
        }
        String normalized = source.trim();
        if (fixedFieldValues.containsKey(normalized)) {
            return fixedFieldValues.get(normalized);
        }
        if ("patientName".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPatientName());
        }
        if ("nationalId".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getNationalId());
        }
        if ("subjectNumber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getSubjectNumber());
        }
        if ("sampleCug".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getAccessionNumber());
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
        if ("patientSiteNumber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPatientSiteNumber());
        }
        if ("prescriber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPrescriber(), first.getContactInfo());
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
        if ("orderFinishDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getOrderFinishDate());
        }
        if ("testDate".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getTestDate());
        }
        if ("analysisResult1".equalsIgnoreCase(normalized) || "analysisResult2".equalsIgnoreCase(normalized)) {
            int position = "analysisResult2".equalsIgnoreCase(normalized) ? 1 : 0;
            List<String> values = getScopedReportItems().stream().map(ClinicalPatientData::getResult)
                    .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
            return values.size() > position ? values.get(position) : "";
        }

        if (normalized.startsWith("orderAdditional.") || normalized.startsWith("sampleAdditional.")
                || normalized.startsWith("testAdditional.")) {
            return resolveAdditionalValue(normalized);
        }

        String additionalKey = normalized.startsWith("additional.") ? normalized.substring("additional.".length())
                : normalized;
        if (StringUtils.isNotBlank(additionalKey)) {
            String fromAdditional = resolveAdditionalValue(additionalKey);
            if (StringUtils.isNotBlank(fromAdditional)) {
                return fromAdditional;
            }
            String fromTestAdditional = resolveAdditionalValue("testAdditional." + additionalKey);
            if (StringUtils.isNotBlank(fromTestAdditional)) {
                return fromTestAdditional;
            }
        }

        return "";
    }

    private String resolveReferenceCenter(ClinicalPatientData first) {
        String fromSiteInfo = sanitizeSiteInfo(first == null ? "" : first.getSiteInfo());
        if (StringUtils.isNotBlank(fromSiteInfo)) {
            return fromSiteInfo;
        }
        // Fallbacks when requester org was not captured in the order header.
        List<String> candidates = Arrays.asList("orderAdditional.reference_center", "orderAdditional.centro_referencia",
                "orderAdditional.referring_site", "orderAdditional.referringSite", "reference_center",
                "centro_referencia", "referring_site", "referringSite");
        for (String key : candidates) {
            String value = resolveAdditionalValue(key);
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String resolveAdditionalValue(String key) {
        if (StringUtils.isBlank(key)) {
            return "";
        }
        for (ClinicalPatientData data : getScopedReportItems()) {
            Map<String, String> values = data.getAdditionalFieldValues();
            if (values == null || values.isEmpty()) {
                continue;
            }
            String value = StringUtils.trimToNull(values.get(key));
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    private List<ConfiguredSectionField> parseConfiguredSectionFields(JSONObject config) {
        if (config == null) {
            return Collections.emptyList();
        }
        JSONArray fields = config.optJSONArray("sectionFields");
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }
        List<ConfiguredSectionField> parsed = new ArrayList<>();
        for (int i = 0; i < fields.length(); i++) {
            JSONObject row = fields.optJSONObject(i);
            if (row == null) {
                continue;
            }
            ConfiguredSectionField item = new ConfiguredSectionField();
            item.key = StringUtils.trimToEmpty(row.optString("key"));
            item.section = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(row.optString("section")), "SAMPLE");
            item.label = StringUtils.trimToEmpty(row.optString("label"));
            item.source = StringUtils.trimToEmpty(row.optString("source"));
            item.order = row.optInt("order", 0);
            item.enabled = row.optBoolean("enabled", true);
            if (StringUtils.isNotBlank(item.key) && StringUtils.isNotBlank(item.source)) {
                parsed.add(item);
            }
        }
        parsed.sort(Comparator.comparingInt(item -> item.order));
        return parsed;
    }

    private String resolveResultDate(ClinicalPatientData first, JSONObject config) {
        String configured = resolveSlotValue(config, "resultDate", Arrays.asList("fecha_resultado", "result_date"), "");
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        if (first == null) {
            return "";
        }
        if (StringUtils.isNotBlank(first.getOrderFinishDate())) {
            return first.getOrderFinishDate();
        }
        if (StringUtils.isNotBlank(first.getTestDate())) {
            return first.getTestDate();
        }
        return DateUtil.convertSqlDateToStringDate(new java.sql.Date(System.currentTimeMillis()));
    }

    private String resolveConstant(JSONObject config, String key, String fallback) {
        if (config == null) {
            return fallback;
        }
        JSONObject constants = config.optJSONObject("constants");
        if (constants != null) {
            String value = StringUtils.trimToNull(constants.optString(key, null));
            if (value != null) {
                return value;
            }
        }
        String rootValue = StringUtils.trimToNull(config.optString(key, null));
        return rootValue == null ? fallback : rootValue;
    }

    private void applyHeaderImageOverrides(JSONObject config) {
        if (reportParameters == null) {
            return;
        }
        applyHeaderImageOverride(config, "leftHeaderImage",
                Arrays.asList("leftHeaderImageName", "headerLeftImage", "leftLogoImageName"));
        applyHeaderImageOverride(config, "rightHeaderImage",
                Arrays.asList("rightHeaderImageName", "headerRightImage", "rightLogoImageName"));
    }

    private void applyHeaderImageOverride(JSONObject config, String parameterKey, List<String> configKeys) {
        String imageName = null;
        for (String key : configKeys) {
            String configured = resolveConstant(config, key, null);
            if (!GenericValidator.isBlankOrNull(configured)) {
                imageName = configured.trim();
                break;
            }
        }
        if (GenericValidator.isBlankOrNull(imageName)) {
            return;
        }
        Optional<Image> image = resolveImageOverride(imageName);
        if (image.isPresent() && image.get().getImage() != null) {
            reportParameters.put(parameterKey, new ByteArrayInputStream(image.get().getImage()));
        }
    }

    private Optional<Image> resolveImageOverride(String rawValue) {
        String value = StringUtils.trimToEmpty(rawValue);
        if (StringUtils.startsWithIgnoreCase(value, "imageId:")) {
            String imageId = StringUtils.trimToNull(value.substring("imageId:".length()));
            if (imageId != null) {
                try {
                    return Optional.ofNullable(imageService.get(imageId));
                } catch (Exception e) {
                    return Optional.empty();
                }
            }
        }
        return imageService.getImageBySiteInfoName(value);
    }

    private String resolveSlotValue(JSONObject config, String slotName, List<String> defaultKeys, String fallback) {
        List<String> mappedKeys = new ArrayList<>(defaultKeys);
        mappedKeys.addAll(getConfiguredKeys(config, slotName));

        List<String> candidateKeys = mappedKeys.stream().filter(StringUtils::isNotBlank).map(String::trim).distinct()
                .collect(Collectors.toList());

        for (ClinicalPatientData data : getScopedReportItems()) {
            Map<String, String> map = data.getAdditionalFieldValues();
            if (map == null || map.isEmpty()) {
                continue;
            }
            for (String key : candidateKeys) {
                String value = StringUtils.trimToNull(map.get(key));
                if (value != null) {
                    return value;
                }
            }
        }

        return fallback;
    }

    private List<ClinicalPatientData> getScopedReportItems() {
        if (scopedReportItems != null) {
            return scopedReportItems;
        }
        if (reportItems == null || reportItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<ClinicalPatientData> dmpkOnly = reportItems.stream().filter(this::isDmpkClinicalData)
                .collect(Collectors.toList());
        scopedReportItems = dmpkOnly.isEmpty() ? reportItems : dmpkOnly;
        return scopedReportItems;
    }

    private boolean isDmpkClinicalData(ClinicalPatientData data) {
        if (data == null || StringUtils.isBlank(data.getTestName())) {
            return false;
        }
        String normalized = normalizeAliasToken(data.getTestName());
        return normalized.contains("dmpk") || normalized.contains("quinasa") || normalized.contains("dm1");
    }

    private List<String> getConfiguredKeys(JSONObject config, String slotName) {
        if (config == null) {
            return Collections.emptyList();
        }

        List<String> keys = new ArrayList<>();
        List<String> slotContainers = Arrays.asList("slots", "slotMapping", "fieldSlots");
        for (String containerName : slotContainers) {
            JSONObject container = config.optJSONObject(containerName);
            if (container == null) {
                continue;
            }
            Object raw = container.opt(slotName);
            if (raw instanceof JSONArray) {
                JSONArray arr = (JSONArray) raw;
                for (int i = 0; i < arr.length(); i++) {
                    String value = StringUtils.trimToNull(arr.optString(i, null));
                    if (value != null) {
                        keys.add(value);
                    }
                }
            } else if (raw instanceof String) {
                String rawString = StringUtils.trimToNull((String) raw);
                if (rawString != null) {
                    keys.addAll(Arrays.stream(rawString.split(",")).map(String::trim).filter(StringUtils::isNotBlank)
                            .collect(Collectors.toList()));
                }
            }
        }
        return keys;
    }

    private JSONObject parseConfig(ReportForm form) {
        if (form == null || StringUtils.isBlank(form.getValidationTemplateConfigJson())) {
            return null;
        }
        try {
            return new JSONObject(form.getValidationTemplateConfigJson());
        } catch (Exception e) {
            return null;
        }
    }

    private String sanitizeSiteInfo(String rawSiteInfo) {
        if (StringUtils.isBlank(rawSiteInfo)) {
            return "";
        }
        String resolved = Arrays.stream(rawSiteInfo.split("\\|")).map(String::trim).filter(StringUtils::isNotBlank)
                .findFirst().orElse(rawSiteInfo.trim());
        // Some source values come as "|" placeholders; those should render empty.
        if ("|".equals(resolved) || StringUtils.isBlank(resolved.replace("|", "").trim())) {
            return "";
        }
        return resolved;
    }

    private String getLongestAllele(String allele1, String allele2) {
        List<String> alleles = Arrays.asList(StringUtils.defaultString(allele1), StringUtils.defaultString(allele2))
                .stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
        if (alleles.isEmpty()) {
            return DEFAULT_NOT_REGISTERED;
        }

        Optional<String> maxNumeric = alleles.stream().max(Comparator.comparingInt(this::extractComparableAlleleValue));
        return maxNumeric.orElse(alleles.get(0));
    }

    private int extractComparableAlleleValue(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return Integer.MIN_VALUE;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        String digits = normalized.replaceAll("[^0-9]", "");
        if (StringUtils.isBlank(digits)) {
            return Integer.MIN_VALUE + 1;
        }
        int number = Integer.parseInt(digits);
        if (normalized.startsWith(">") || normalized.contains(">=")) {
            return number + 100000;
        }
        return number;
    }

    private static class ConfiguredSectionField {
        private String key;
        private String section;
        private String label;
        private String source;
        private int order;
        private boolean enabled;
    }
}
