package org.openelisglobal.reports.action.implementation;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperRunManager;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.audittrail.valueholder.History;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.history.service.HistoryService;
import org.openelisglobal.image.service.ImageService;
import org.openelisglobal.image.valueholder.Image;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.referencetables.service.ReferenceTablesService;
import org.openelisglobal.reports.action.implementation.reportBeans.ClinicalPatientData;
import org.openelisglobal.reports.form.ReportForm;
import org.openelisglobal.sample.util.AccessionNumberUtil;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.dao.AnalysisAdditionalFieldValueDAO;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;

/**
 * DMPK-specific patient report entry point.
 *
 * <p>
 * Initial implementation reuses the current validation clinical report behavior
 * and allows wiring a dedicated Jasper template file.
 */
public class PatientDmpkReport extends PatientCILNSPClinical_vreduit {

    private static final String DEFAULT_NOT_REGISTERED = "NO REGISTRADO";
    private static final String ENTRY_SCOPE_PRELIMINARY = "PRELIMINARY";
    private static final String ANALYSIS_REFERENCE_TABLE = "ANALYSIS";
    private static final String DEFAULT_PROCEDURE_TEXT = "El analisis molecular del gen DMPK consiste en la amplificacion "
            + "por PCR y la discriminacion de la presencia o ausencia del alelo mutado mayor o igual a 50 repeticiones CTG.";
    private static final String DEFAULT_INTERPRETATION_TEXT = "El diagnostico molecular se define por el numero "
            + "de repeticiones CTG del alelo mas largo.";
    private static final List<String> SECTION_PRINT_ORDER = Arrays.asList("PATIENT", "REQUESTING_PHYSICIAN", "SAMPLE",
            "MOLECULAR_RESULT", "CONCLUSION");
    private final ImageService imageService = SpringContext.getBean(ImageService.class);
    private final AnalysisAdditionalFieldValueDAO analysisAdditionalFieldValueDAO = SpringContext
            .getBean(AnalysisAdditionalFieldValueDAO.class);
    private final HistoryService historyService = SpringContext.getBean(HistoryService.class);
    private final ReferenceTablesService referenceTablesService = SpringContext.getBean(ReferenceTablesService.class);
    private final SystemUserService systemUserService = SpringContext.getBean(SystemUserService.class);
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
        List<Analysis> analyses = getScopedDmpkAnalyses();

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
        fixedFieldValues.put("dni", first == null ? "" : StringUtils.defaultString(first.getDni()));
        fixedFieldValues.put("hc", first == null ? "" : StringUtils.defaultString(first.getSubjectNumber()));
        fixedFieldValues.put("cug", first == null ? "" : StringUtils.defaultString(first.getSampleCug()));
        fixedFieldValues.put("gender", first == null ? "" : StringUtils.defaultString(first.getGender()));
        fixedFieldValues.put("birthDate", first == null ? "" : formatDateStringForReport(first.getDob()));
        fixedFieldValues.put("contact", first == null ? "" : StringUtils.defaultString(first.getPatientSiteNumber()));
        fixedFieldValues.put("requestingPhysician",
            first == null ? "" : StringUtils.defaultIfBlank(first.getPrescriber(), first.getContactInfo()));
        fixedFieldValues.put("requesterCmp", first == null ? "" : StringUtils.defaultString(first.getRequesterCmp()));
        fixedFieldValues.put("requesterRne", first == null ? "" : StringUtils.defaultString(first.getRequesterRne()));
        fixedFieldValues.put("requesterSpecialty",
                first == null ? "" : StringUtils.defaultString(first.getRequesterSpecialty()));
        String referenceCenter = resolveReferenceCenter(first);
        fixedFieldValues.put("referenceCenter",
                StringUtils.isBlank(referenceCenter) ? DEFAULT_NOT_REGISTERED : referenceCenter);
        fixedFieldValues.put("collectionDate", resolveCollectionDateDisplay(analyses, first));
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
        reportParameters.put("dmpkDeliveryDate", resolveDeliveryDate(analyses));

        String analyzedByConfigured = resolveConfiguredAnalyzedByUsers(config, analyses);
        String analyzedByComputed = StringUtils.defaultIfBlank(analyzedByConfigured, resolveAnalyzedByUsers(analyses));
        reportParameters.put("dmpkAnalyzedBy",
                StringUtils.defaultIfBlank(analyzedByComputed, resolveConstant(config, "analyzedBy", "")));

        InterpretedByInfo interpretedByInfo = resolveInterpretedByInfo();
        reportParameters.put("dmpkInterpretedBy",
                StringUtils.defaultIfBlank(interpretedByInfo.displayName, resolveConstant(config, "interpretedBy", "")));
        reportParameters.put("dmpkInterpretedBySpecialty", StringUtils.defaultString(interpretedByInfo.specialty));
        reportParameters.put("dmpkInterpretedBySignature", interpretedByInfo.signatureImageStream);

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
            String sourceKey = field.source;
            if ("cug".equalsIgnoreCase(normalizedKey) && "accessionNumber".equalsIgnoreCase(sourceKey)) {
                sourceKey = "sampleCug";
            }
            if ("dni".equalsIgnoreCase(normalizedKey) && "subjectNumber".equalsIgnoreCase(sourceKey)) {
                sourceKey = "nationalId";
            }
            if ("dni".equalsIgnoreCase(normalizedKey) && "nationalId".equalsIgnoreCase(sourceKey)) {
                sourceKey = "dni";
            }
            if ("hc".equalsIgnoreCase(normalizedKey) && "nationalId".equalsIgnoreCase(sourceKey)) {
                sourceKey = "subjectNumber";
            }
            String resolvedValue = resolveSourceValue(sourceKey, first, fixedFieldValues);
            if (fixedFieldValues.containsKey(normalizedKey)) {
                if ("collectionDate".equalsIgnoreCase(normalizedKey)) {
                    continue;
                }
                fixedFieldValues.put(normalizedKey, StringUtils.defaultString(resolvedValue));
                if (StringUtils.isNotBlank(field.label) && fixedFieldLabels.containsKey(normalizedKey)) {
                    fixedFieldLabels.put(normalizedKey, field.label);
                }
                continue;
            }
                String line = StringUtils.defaultIfBlank(field.label, field.key) + ": "
                    + StringUtils.defaultIfBlank(resolvedValue, DEFAULT_NOT_REGISTERED);
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
        if ("patientSiteNumber".equalsIgnoreCase(normalized)) {
            return first == null ? "" : StringUtils.defaultString(first.getPatientSiteNumber());
        }
        if ("prescriber".equalsIgnoreCase(normalized)) {
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
            return formatDateStringForReport(first.getOrderFinishDate());
        }
        if (StringUtils.isNotBlank(first.getTestDate())) {
            return formatDateStringForReport(first.getTestDate());
        }
        return formatSqlDateForReport(new java.sql.Date(System.currentTimeMillis()));
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

    private String resolveAnalyzedByUsers(List<Analysis> analyses) {
        if (analyses == null || analyses.isEmpty()) {
            return "";
        }

        final int MAX_ANALYSTS = 2;

        // Iterate analyses from newest to oldest.
        for (int i = analyses.size() - 1; i >= 0; i--) {
            Analysis analysis = analyses.get(i);
            if (analysis == null || analysis.getTest() == null || StringUtils.isBlank(analysis.getTest().getId())) {
                continue;
            }

            List<TestAdditionalFieldPayload> fields = testAdditionalFieldService.getFieldsForTest(analysis.getTest().getId(),
                    false);
            Set<Integer> allBlockFieldDefIds = getBlockFieldDefinitionIdsFromPayload(fields, false);
            if (allBlockFieldDefIds.isEmpty()) {
                continue;
            }
            Set<Integer> preliminaryFieldDefIds = getBlockFieldDefinitionIdsFromPayload(fields, true);
            Set<Integer> officialFieldDefIds = new LinkedHashSet<>(allBlockFieldDefIds);
            officialFieldDefIds.removeAll(preliminaryFieldDefIds);
            Map<String, Set<Integer>> biologoBlockFieldIds = getBiologistBlockFieldIds(fields);

            Integer analysisId = parseInteger(analysis.getId());
            if (analysisId == null) {
                continue;
            }

            List<AnalysisAdditionalFieldValue> values = analysisAdditionalFieldValueDAO
                    .findByAnalysisIdAndFieldDefinitionIds(analysisId, new ArrayList<>(allBlockFieldDefIds));
            if (values == null || values.isEmpty()) {
                continue;
            }

            List<String> candidateUserIds = new ArrayList<>();
            String preliminaryUserId = resolveLatestContributorByScope(values, preliminaryFieldDefIds);
            String officialUserId = resolveLatestContributorByScope(values, officialFieldDefIds);
            if (StringUtils.isNotBlank(preliminaryUserId)) {
                candidateUserIds.add(preliminaryUserId);
            }
            if (StringUtils.isNotBlank(officialUserId)) {
                candidateUserIds.add(officialUserId);
            }

            for (Set<Integer> blockIds : biologoBlockFieldIds.values()) {
                String blockUserId = resolveLatestContributorByScope(values, blockIds);
                if (StringUtils.isNotBlank(blockUserId)) {
                    candidateUserIds.add(blockUserId);
                }
            }

            // Fallback: take latest contributors from additional fields.
            for (int j = values.size() - 1; j >= 0 && candidateUserIds.size() < MAX_ANALYSTS * 3; j--) {
                AnalysisAdditionalFieldValue value = values.get(j);
                if (value == null || StringUtils.isBlank(StringUtils.trimToNull(value.getFieldValue()))) {
                    continue;
                }
                if (StringUtils.isNotBlank(value.getSysUserId())) {
                    candidateUserIds.add(value.getSysUserId());
                }
            }

            LinkedHashSet<String> uniqueUserIds = new LinkedHashSet<>();
            for (String userId : candidateUserIds) {
                if (StringUtils.isNotBlank(userId)) {
                    uniqueUserIds.add(userId);
                }
            }

            List<String> displayUsers = new ArrayList<>();
            for (String userId : uniqueUserIds) {
                String display = formatAnalyzedByUser(userId);
                if (StringUtils.isNotBlank(display)) {
                    displayUsers.add(display);
                    if (displayUsers.size() >= MAX_ANALYSTS) {
                        break;
                    }
                }
            }

            if (!displayUsers.isEmpty()) {
                return String.join("\n\n", displayUsers);
            }
        }

        return "";
    }

    private String resolveConfiguredAnalyzedByUsers(JSONObject config, List<Analysis> analyses) {
        if (analyses == null || analyses.isEmpty()) {
            return "";
        }

        // Optional explicit mapping via Validation Template slot mapping:
        // analyzedBy1 -> additional field key for first biologist selector
        // analyzedBy2 -> additional field key for second biologist selector
        List<AdditionalFieldSelection> configuredSelections = Arrays.asList(
                resolveConfiguredAdditionalFieldSelection(config, "analyzedBy1",
                        Arrays.asList("analyzedBy1", "analyzed_by_1", "analizado_por_1", "biologo_1")),
                resolveConfiguredAdditionalFieldSelection(config, "analyzedBy2",
                        Arrays.asList("analyzedBy2", "analyzed_by_2", "analizado_por_2", "biologo_2")));

        Map<String, TestAdditionalFieldPayload> fieldDefinitionsByKey = getAdditionalFieldDefinitionsByKey(analyses);
        LinkedHashSet<String> resolvedUserIds = new LinkedHashSet<>();

        for (AdditionalFieldSelection selection : configuredSelections) {
            if (selection == null || StringUtils.isBlank(selection.value)) {
                continue;
            }
            String selectedFieldKey = stripTestAdditionalPrefix(selection.sourceKey);
            TestAdditionalFieldPayload fieldDefinition = fieldDefinitionsByKey.get(selectedFieldKey);
            List<String> userIds = resolveBiologistUserIdsFromConfiguredValue(selection.value, fieldDefinition);
            resolvedUserIds.addAll(userIds);
            if (resolvedUserIds.size() >= 2) {
                break;
            }
        }

        if (resolvedUserIds.isEmpty()) {
            return "";
        }

        List<String> rendered = new ArrayList<>();
        for (String userId : resolvedUserIds) {
            String formatted = formatAnalyzedByUser(userId);
            if (StringUtils.isNotBlank(formatted)) {
                rendered.add(formatted);
            }
            if (rendered.size() >= 2) {
                break;
            }
        }
        return rendered.isEmpty() ? "" : String.join("\n\n", rendered);
    }

    private AdditionalFieldSelection resolveConfiguredAdditionalFieldSelection(JSONObject config, String slotName,
            List<String> defaultKeys) {
        List<String> mappedKeys = new ArrayList<>(defaultKeys);
        mappedKeys.addAll(getConfiguredKeys(config, slotName));

        List<String> candidateKeys = mappedKeys.stream().filter(StringUtils::isNotBlank).map(String::trim).distinct()
                .collect(Collectors.toList());
        if (candidateKeys.isEmpty()) {
            return null;
        }

        for (ClinicalPatientData data : getScopedReportItems()) {
            Map<String, String> additionalValues = data.getAdditionalFieldValues();
            if (additionalValues == null || additionalValues.isEmpty()) {
                continue;
            }
            for (String candidateKey : candidateKeys) {
                for (String lookupKey : expandAdditionalFieldLookupKeys(candidateKey)) {
                    String value = StringUtils.trimToNull(additionalValues.get(lookupKey));
                    if (value != null) {
                        return new AdditionalFieldSelection(lookupKey, value);
                    }
                }
            }
        }
        return null;
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

    private String stripTestAdditionalPrefix(String key) {
        if (StringUtils.isBlank(key)) {
            return "";
        }
        if (key.startsWith("testAdditional.")) {
            return key.substring("testAdditional.".length());
        }
        return key;
    }

    private Map<String, TestAdditionalFieldPayload> getAdditionalFieldDefinitionsByKey(List<Analysis> analyses) {
        if (analyses == null || analyses.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, TestAdditionalFieldPayload> definitionsByKey = new LinkedHashMap<>();
        for (Analysis analysis : analyses) {
            if (analysis == null || analysis.getTest() == null || StringUtils.isBlank(analysis.getTest().getId())) {
                continue;
            }
            List<TestAdditionalFieldPayload> testFields = testAdditionalFieldService
                    .getFieldsForTest(analysis.getTest().getId(), false);
            if (testFields == null || testFields.isEmpty()) {
                continue;
            }
            for (TestAdditionalFieldPayload payload : testFields) {
                if (payload == null || StringUtils.isBlank(payload.getFieldKey())) {
                    continue;
                }
                String fieldKey = payload.getFieldKey().trim();
                definitionsByKey.putIfAbsent(fieldKey, payload);
            }
        }
        return definitionsByKey;
    }

    private List<String> resolveBiologistUserIdsFromConfiguredValue(String rawValue,
            TestAdditionalFieldPayload fieldDefinition) {
        if (StringUtils.isBlank(rawValue)) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> userIds = new LinkedHashSet<>();
        for (String token : rawValue.split(",")) {
            String trimmedToken = StringUtils.trimToNull(token);
            if (trimmedToken == null) {
                continue;
            }

            List<String> candidateTokens = new ArrayList<>();
            candidateTokens.add(trimmedToken);
            candidateTokens.addAll(resolveOptionLabelsForSelectedToken(fieldDefinition, trimmedToken));
            for (String candidate : candidateTokens) {
                String resolved = resolveBiologistUserId(candidate);
                if (StringUtils.isNotBlank(resolved)) {
                    userIds.add(resolved);
                    break;
                }
            }
            if (userIds.size() >= 2) {
                break;
            }
        }
        return new ArrayList<>(userIds);
    }

    private List<String> resolveOptionLabelsForSelectedToken(TestAdditionalFieldPayload fieldDefinition, String token) {
        if (fieldDefinition == null || fieldDefinition.getOptions() == null || fieldDefinition.getOptions().isEmpty()
                || StringUtils.isBlank(token)) {
            return Collections.emptyList();
        }
        String normalizedToken = normalizeAliasToken(token);
        List<String> labels = new ArrayList<>();
        fieldDefinition.getOptions().forEach(option -> {
            if (option == null || StringUtils.isBlank(option.getOptionKey()) || StringUtils.isBlank(option.getOptionLabel())) {
                return;
            }
            if (normalizeAliasToken(option.getOptionKey()).equals(normalizedToken)) {
                labels.add(option.getOptionLabel());
            }
        });
        return labels;
    }

    private String resolveBiologistUserId(String rawSelector) {
        String selector = StringUtils.trimToNull(rawSelector);
        if (selector == null) {
            return null;
        }

        String directUserId = tryResolveAsUserId(selector);
        if (StringUtils.isNotBlank(directUserId) && isBiologistUser(directUserId)) {
            return directUserId;
        }

        String normalizedSelector = normalizeAliasToken(selector);
        if (StringUtils.isBlank(normalizedSelector)) {
            return null;
        }

        List<SystemUser> users = systemUserService.getAllSystemUsers();
        if (users == null || users.isEmpty()) {
            return null;
        }

        for (SystemUser user : users) {
            if (user == null || StringUtils.isBlank(user.getId()) || !isBiologistUser(user.getId())) {
                continue;
            }
            if (matchesBiologistSelector(user, normalizedSelector)) {
                return user.getId();
            }
        }
        return null;
    }

    private String tryResolveAsUserId(String selector) {
        if (!StringUtils.isNumeric(selector)) {
            return null;
        }
        SystemUser user = systemUserService.getUserById(selector);
        return user == null ? null : user.getId();
    }

    private boolean isBiologistUser(String userId) {
        if (StringUtils.isBlank(userId)) {
            return false;
        }
        SystemUser user = systemUserService.getUserById(userId);
        if (user == null) {
            return false;
        }
        Provider provider = resolveLinkedProvider(user);
        return provider != null && "BIOLOGIST".equals(normalizeProfessionalProfileCode(provider.getProfessionalProfileCode()));
    }

    private boolean matchesBiologistSelector(SystemUser user, String normalizedSelector) {
        if (user == null || StringUtils.isBlank(user.getId())) {
            return false;
        }
        Provider provider = resolveLinkedProvider(user);
        if (provider == null || !"BIOLOGIST".equals(normalizeProfessionalProfileCode(provider.getProfessionalProfileCode()))) {
            return false;
        }

        Set<String> candidates = new LinkedHashSet<>();
        addCandidateAlias(candidates, user.getId());
        addCandidateAlias(candidates, user.getLoginName());
        addCandidateAlias(candidates, user.getInitials());
        addCandidateAlias(candidates, getUserDisplayName(user.getId()));
        addCandidateAlias(candidates, user.getNameForDisplay());
        addCandidateAlias(candidates, provider.getProfessionalInitials());
        addCandidateAlias(candidates, provider.getDni());
        addCandidateAlias(candidates, provider.getCbpCode());
        addCandidateAlias(candidates, provider.getNpi());
        if (provider.getPerson() != null) {
            addCandidateAlias(candidates, provider.getPerson().getFirstName());
            addCandidateAlias(candidates, provider.getPerson().getLastName());
            addCandidateAlias(candidates, provider.getPerson().getFirstName() + " " + provider.getPerson().getLastName());
        }

        for (String candidate : candidates) {
            String normalizedCandidate = normalizeAliasToken(candidate);
            if (StringUtils.isBlank(normalizedCandidate)) {
                continue;
            }
            if (normalizedCandidate.equals(normalizedSelector) || normalizedSelector.contains(normalizedCandidate)
                    || normalizedCandidate.contains(normalizedSelector)) {
                return true;
            }
        }
        return false;
    }

    private void addCandidateAlias(Set<String> aliases, String value) {
        String safeValue = StringUtils.trimToNull(value);
        if (safeValue != null) {
            aliases.add(safeValue);
        }
    }

    private Set<Integer> getBlockFieldDefinitionIdsFromPayload(List<TestAdditionalFieldPayload> fields, boolean preliminaryOnly) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> fieldIds = new LinkedHashSet<>();
        for (TestAdditionalFieldPayload field : fields) {
            if (field == null || field.getId() == null) {
                continue;
            }
            String entryScope = StringUtils.defaultString(field.getEntryScope()).trim().toUpperCase(Locale.ROOT);
            boolean hasBlockMeta = StringUtils.isNotBlank(field.getBlockName()) || StringUtils.isNotBlank(entryScope);
            if (!hasBlockMeta) {
                continue;
            }
            if (preliminaryOnly && !ENTRY_SCOPE_PRELIMINARY.equals(entryScope)) {
                continue;
            }
            fieldIds.add(field.getId());
        }
        return fieldIds;
    }

    private Map<String, Set<Integer>> getBiologistBlockFieldIds(List<TestAdditionalFieldPayload> fields) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Set<Integer>> byBlock = new LinkedHashMap<>();
        for (TestAdditionalFieldPayload field : fields) {
            if (field == null || field.getId() == null || StringUtils.isBlank(field.getBlockName())) {
                continue;
            }
            String normalizedBlockName = normalizeAliasToken(field.getBlockName());
            if (!normalizedBlockName.contains("biolog")) {
                continue;
            }
            byBlock.computeIfAbsent(field.getBlockName(), key -> new LinkedHashSet<>()).add(field.getId());
        }
        return byBlock;
    }

    private String resolveLatestContributorByScope(List<AnalysisAdditionalFieldValue> values, Set<Integer> scopeFieldDefIds) {
        if (values == null || values.isEmpty() || scopeFieldDefIds == null || scopeFieldDefIds.isEmpty()) {
            return null;
        }
        for (int i = values.size() - 1; i >= 0; i--) {
            AnalysisAdditionalFieldValue value = values.get(i);
            if (value == null || value.getFieldDefinitionId() == null) {
                continue;
            }
            if (!scopeFieldDefIds.contains(value.getFieldDefinitionId())) {
                continue;
            }
            if (StringUtils.isBlank(StringUtils.trimToNull(value.getFieldValue()))) {
                continue;
            }
            if (StringUtils.isNotBlank(value.getSysUserId())) {
                return value.getSysUserId();
            }
        }
        return null;
    }

    private String resolveDeliveryDate(List<Analysis> analyses) {
        if (analyses == null || analyses.isEmpty()) {
            return "";
        }
        return analyses.stream().map(Analysis::getValidationDate).filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder()).map(this::formatSqlDateForReport).orElse("");
    }

    private String resolveCollectionDateDisplay(List<Analysis> analyses, ClinicalPatientData first) {
        if (analyses != null) {
            for (Analysis analysis : analyses) {
                if (analysis == null || analysis.getSampleItem() == null
                        || analysis.getSampleItem().getCollectionDate() == null) {
                    continue;
                }
                return formatTimestampForReport(analysis.getSampleItem().getCollectionDate());
            }
        }
        if (first == null) {
            return "";
        }
        String collectionDateTime = StringUtils.trimToEmpty(first.getCollectionDateTime());
        if (StringUtils.isBlank(collectionDateTime)) {
            return "";
        }
        return collectionDateTime;
    }

    private String formatSqlDateForReport(java.sql.Date date) {
        if (date == null) {
            return "";
        }
        try {
            return new SimpleDateFormat("dd/MM/yyyy").format(date);
        } catch (RuntimeException e) {
            return DateUtil.convertSqlDateToStringDate(date);
        }
    }

    private String formatTimestampForReport(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        try {
            return new SimpleDateFormat("dd/MM/yyyy").format(timestamp);
        } catch (RuntimeException e) {
            return DateUtil.convertTimestampToStringDate(timestamp);
        }
    }

    private String formatDateStringForReport(String dateStr) {
        if (StringUtils.isBlank(dateStr)) {
            return "";
        }
        try {
            return DateUtil.formatStringDate(dateStr, "dd/MM/yyyy");
        } catch (RuntimeException e) {
            return dateStr;
        }
    }

    private String normalizeProfessionalProfileCode(String rawValue) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(rawValue));
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        if ("BIOLOGO".equals(normalized) || "BIOLOGISTA".equals(normalized)) {
            return "BIOLOGIST";
        }
        return normalized;
    }

    private String formatAnalyzedByUser(String userId) {
        if (StringUtils.isBlank(userId)) {
            return "";
        }
        SystemUser user = systemUserService.getUserById(userId);
        if (user == null) {
            return "";
        }

        String displayName = getUserDisplayName(userId);
        Provider linkedProvider = resolveLinkedProvider(user);
        if (linkedProvider == null) {
            return displayName;
        }

        String profileCode = normalizeProfessionalProfileCode(linkedProvider.getProfessionalProfileCode());
        if (!"BIOLOGIST".equals(profileCode)) {
            return displayName;
        }

        String cbpCode = StringUtils.defaultIfBlank(linkedProvider.getCbpCode(), linkedProvider.getNpi());
        List<String> lines = new ArrayList<>();
        if (StringUtils.isNotBlank(displayName)) {
            lines.add(displayName);
        }

        StringBuilder secondLine = new StringBuilder();
        if (StringUtils.isNotBlank(cbpCode)) {
            secondLine.append("CBP: ").append(cbpCode);
        }
        if (StringUtils.isNotBlank(secondLine)) {
            secondLine.append(" - ");
        }
        secondLine.append("BIOLOGISTA");
        lines.add(secondLine.toString());

        return String.join("\n", lines);
    }

    private Provider resolveLinkedProvider(SystemUser user) {
        if (user == null || StringUtils.isBlank(user.getLinkedProviderPersonId())) {
            return null;
        }
        Person linkedPerson = personService.getPersonById(user.getLinkedProviderPersonId());
        if (linkedPerson == null) {
            return null;
        }
        return providerService.getProviderByPerson(linkedPerson);
    }

    private InterpretedByInfo resolveInterpretedByInfo() {
        // For validated-preview flow, show the currently logged-in validator.
        if (previewValidated && StringUtils.isNotBlank(systemUserId)) {
            return buildInterpretedByInfo(systemUserId);
        }

        List<Analysis> analyses = getScopedDmpkAnalyses();
        if (analyses.isEmpty()) {
            return InterpretedByInfo.empty();
        }

        String analysisTableId = Optional.ofNullable(referenceTablesService.getReferenceTableByName(ANALYSIS_REFERENCE_TABLE))
                .map(referenceTable -> referenceTable.getId()).orElse(null);
        if (StringUtils.isBlank(analysisTableId)) {
            return InterpretedByInfo.empty();
        }

        History latestFinalization = null;
        for (Analysis analysis : analyses) {
            if (analysis == null || StringUtils.isBlank(analysis.getId())) {
                continue;
            }

            List<History> historyList = historyService.getHistoryByRefIdAndRefTableId(analysis.getId(), analysisTableId);
            if (historyList == null || historyList.isEmpty()) {
                continue;
            }

            History latestStatusChange = null;
            History latestAnyUpdate = null;
            for (History history : historyList) {
                if (history == null || !"U".equals(history.getActivity())) {
                    continue;
                }
                if (latestAnyUpdate == null || isMoreRecent(history, latestAnyUpdate)) {
                    latestAnyUpdate = history;
                }
                if (hasStatusChange(history) && (latestStatusChange == null || isMoreRecent(history, latestStatusChange))) {
                    latestStatusChange = history;
                }
            }

            History selectedForAnalysis = latestStatusChange != null ? latestStatusChange : latestAnyUpdate;
            if (selectedForAnalysis != null
                    && (latestFinalization == null || isMoreRecent(selectedForAnalysis, latestFinalization))) {
                latestFinalization = selectedForAnalysis;
            }
        }

        if (latestFinalization == null || StringUtils.isBlank(latestFinalization.getSysUserId())) {
            return InterpretedByInfo.empty();
        }

        return buildInterpretedByInfo(latestFinalization.getSysUserId());
    }

    private boolean isMoreRecent(History candidate, History current) {
        if (candidate == null) {
            return false;
        }
        if (current == null || current.getTimestamp() == null) {
            return true;
        }
        if (candidate.getTimestamp() == null) {
            return false;
        }
        return candidate.getTimestamp().after(current.getTimestamp());
    }

    private boolean hasStatusChange(History history) {
        if (history == null || history.getChanges() == null || history.getChanges().length == 0) {
            return false;
        }
        String changes = new String(history.getChanges(), StandardCharsets.UTF_8);
        return StringUtils.isNotBlank(extractSimpleTag(changes, "statusId"));
    }

    private String extractSimpleTag(String xmlText, String tagName) {
        if (StringUtils.isBlank(xmlText) || StringUtils.isBlank(tagName)) {
            return null;
        }
        String startTag = "<" + tagName + ">";
        int begin = xmlText.indexOf(startTag);
        if (begin < 0) {
            return null;
        }
        begin += startTag.length();
        int end = xmlText.indexOf("</" + tagName + ">");
        if (end < 0 || end < begin) {
            return null;
        }
        return xmlText.substring(begin, end);
    }

    private InterpretedByInfo buildInterpretedByInfo(String systemUserId) {
        SystemUser user = systemUserService.getUserById(systemUserId);
        if (user == null) {
            return InterpretedByInfo.empty();
        }

        String displayName = getUserDisplayName(systemUserId);
        String specialty = "";
        if (StringUtils.isNotBlank(user.getLinkedProviderPersonId())) {
            Person linkedPerson = personService.getPersonById(user.getLinkedProviderPersonId());
            if (linkedPerson != null) {
                Provider linkedProvider = providerService.getProviderByPerson(linkedPerson);
                if (linkedProvider != null) {
                    specialty = StringUtils.defaultString(linkedProvider.getSpecialty());
                }
            }
        }

        ByteArrayInputStream signatureStream = decodeSignatureImage(user.getSignatureImageData());
        return new InterpretedByInfo(displayName, specialty, signatureStream);
    }

    private ByteArrayInputStream decodeSignatureImage(String signatureImageData) {
        String raw = StringUtils.trimToNull(signatureImageData);
        if (raw == null) {
            return null;
        }
        try {
            String base64Payload = raw;
            if (StringUtils.startsWithIgnoreCase(base64Payload, "data:")) {
                int commaIndex = base64Payload.indexOf(',');
                if (commaIndex > -1 && commaIndex + 1 < base64Payload.length()) {
                    base64Payload = base64Payload.substring(commaIndex + 1);
                }
            }
            byte[] decoded = Base64.getDecoder().decode(base64Payload);
            return decoded.length == 0 ? null : new ByteArrayInputStream(decoded);
        } catch (IllegalArgumentException decodeError) {
            return null;
        }
    }

    private List<Analysis> getScopedDmpkAnalyses() {
        Set<String> accessionNumbers = getScopedReportItems().stream()
                .map(ClinicalPatientData::getSampleId)
                .filter(StringUtils::isNotBlank)
                .map(this::extractBaseAccession)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (accessionNumbers.isEmpty()) {
            accessionNumbers = getScopedReportItems().stream()
                    .map(ClinicalPatientData::getAccessionNumber)
                    .filter(StringUtils::isNotBlank)
                    .map(this::extractBaseAccession)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        Map<String, Analysis> uniqueById = new LinkedHashMap<>();
        for (String accessionNumber : accessionNumbers) {
            Sample sample = sampleService.getSampleByAccessionNumber(accessionNumber);
            if (sample == null || StringUtils.isBlank(sample.getId())) {
                continue;
            }

            List<Analysis> analysesForSample = analysisService.getAnalysesBySampleId(sample.getId());
            if (analysesForSample == null || analysesForSample.isEmpty()) {
                continue;
            }

            for (Analysis analysis : analysesForSample) {
                if (analysis != null && StringUtils.isNotBlank(analysis.getId()) && isDmpkAnalysis(analysis)) {
                    uniqueById.put(analysis.getId(), analysis);
                }
            }
        }

        return new ArrayList<>(uniqueById.values());
    }

    private boolean isDmpkAnalysis(Analysis analysis) {
        if (analysis == null) {
            return false;
        }
        String testName = StringUtils.defaultString(analysisService.getTestDisplayName(analysis));
        if (StringUtils.isBlank(testName) && analysis.getTest() != null) {
            testName = StringUtils.defaultString(analysis.getTest().getDescription(), analysis.getTest().getName());
        }
        String normalized = normalizeAliasToken(testName);
        return normalized.contains("dmpk") || normalized.contains("quinasa") || normalized.contains("dm1");
    }

    private Set<Integer> getBlockFieldDefinitionIds(Analysis analysis, boolean preliminaryOnly) {
        if (analysis == null || analysis.getTest() == null || StringUtils.isBlank(analysis.getTest().getId())) {
            return Collections.emptySet();
        }

        List<TestAdditionalFieldPayload> fields = testAdditionalFieldService.getFieldsForTest(analysis.getTest().getId(),
                false);
        if (fields == null || fields.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> fieldIds = new LinkedHashSet<>();
        for (TestAdditionalFieldPayload field : fields) {
            if (field == null || field.getId() == null) {
                continue;
            }
            String entryScope = StringUtils.defaultString(field.getEntryScope()).trim().toUpperCase(Locale.ROOT);
            boolean hasBlockMeta = StringUtils.isNotBlank(field.getBlockName()) || StringUtils.isNotBlank(entryScope);
            if (!hasBlockMeta) {
                continue;
            }
            if (preliminaryOnly && !ENTRY_SCOPE_PRELIMINARY.equals(entryScope)) {
                continue;
            }
            fieldIds.add(field.getId());
        }
        return fieldIds;
    }

    private String getUserDisplayName(String userId) {
        if (StringUtils.isBlank(userId)) {
            return "";
        }
        SystemUser user = systemUserService.getUserById(userId);
        if (user == null) {
            return "";
        }
        String fullName = StringUtils.normalizeSpace(
                StringUtils.defaultString(user.getFirstName()) + " " + StringUtils.defaultString(user.getLastName()));
        return StringUtils.defaultIfBlank(fullName, StringUtils.defaultString(user.getDisplayName()));
    }

    private String extractBaseAccession(String accessionWithSampleSuffix) {
        if (StringUtils.isBlank(accessionWithSampleSuffix)) {
            return "";
        }
        if (accessionWithSampleSuffix.contains("-")) {
            return AccessionNumberUtil.getAccessionNumberFromSampleItemAccessionNumber(accessionWithSampleSuffix);
        }
        return accessionWithSampleSuffix;
    }

    private Integer parseInteger(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private static class InterpretedByInfo {
        private final String displayName;
        private final String specialty;
        private final ByteArrayInputStream signatureImageStream;

        private InterpretedByInfo(String displayName, String specialty, ByteArrayInputStream signatureImageStream) {
            this.displayName = StringUtils.defaultString(displayName);
            this.specialty = StringUtils.defaultString(specialty);
            this.signatureImageStream = signatureImageStream;
        }

        private static InterpretedByInfo empty() {
            return new InterpretedByInfo("", "", null);
        }
    }

    private static class AdditionalFieldSelection {
        private final String sourceKey;
        private final String value;

        private AdditionalFieldSelection(String sourceKey, String value) {
            this.sourceKey = StringUtils.defaultString(sourceKey);
            this.value = StringUtils.defaultString(value);
        }
    }
}
