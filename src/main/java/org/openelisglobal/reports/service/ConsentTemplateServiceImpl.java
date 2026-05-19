package org.openelisglobal.reports.service;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import java.io.ByteArrayOutputStream;
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
    private static final String TEMPLATE_RESOURCE = "reports/consent/consent-template.pdf";

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

    private static final float SMALL_TEXT_HEIGHT = 7.617357f;
    private static final float SMALL_TEXT_FONT_SIZE = 8f;
    private static final float SMALL_TEXT_CLEAR_WIDTH = 320f;
    private static final float SMALL_TEXT_CLEAR_PADDING = 1.5f;

    private static final float CUG_X = 396.333452f;
    private static final float CUG_Y_MIN = 69.538726f;
    private static final float CUG_Y_MAX = 89.126219f;
    private static final float CUG_FONT_SIZE = 18f;
    private static final float CUG_CLEAR_WIDTH = 95f;
    private static final float CUG_CLEAR_PADDING = 2f;
    private static final BaseColor CUG_CLEAR_COLOR = new BaseColor(217, 217, 217);

    private static final float PATIENT_NAME_X = 137.090880f;
    private static final float PATIENT_NAME_Y_MAX = 538.113283f;
    private static final float PATIENT_DNI_X = 137.090880f;
    private static final float PATIENT_DNI_Y_MAX = 547.929696f;
    private static final float PROVIDER_NAME_X = 137.090880f;
    private static final float PROVIDER_NAME_Y_MAX = 580.948541f;
    private static final float PROVIDER_DNI_X = 137.090880f;
    private static final float PROVIDER_DNI_Y_MAX = 590.764954f;
    private static final float DATE_X = 79.531002f;
    private static final float DATE_Y_MAX = 600.581367f;

    private static final float TESTS_START_X = 79.531002f;
    private static final float TESTS_START_Y_MAX = 465.382585f;
    private static final float TESTS_LINE_SPACING = 9.854845f;
    private static final float TESTS_MAX_Y_MAX = 520.0f;
    private static final float TESTS_CLEAR_WIDTH = 500f;
    private static final float TESTS_TEXT_MAX_WIDTH = TESTS_CLEAR_WIDTH - (SMALL_TEXT_CLEAR_PADDING * 2);
    private static final String ELLIPSIS = "...";
    private static final int TESTS_SAMPLE_LINES = 2;

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
        return form;
    }

    @Override
    @Transactional
    public ConsentTemplateConfigForm saveConfig(String sysUserId, ConsentTemplateConfigForm form) {
        if (form == null) {
            return null;
        }
        Map<String, String> fields = normalizeFieldSources(form.getFields());
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
        definition.setDefinitionJson(buildDefinitionJson(fields));
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
        }
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateConsentPdf(ConsentPreviewForm form) {
        ConsentTemplateConfigForm config = getConfig();
        Map<String, String> fields = normalizeFieldSources(config == null ? null : config.getFields());
        ResolvedConsentValues values = resolveValues(fields, form);

        PdfReader reader = null;
        PdfStamper stamper = null;
        try (InputStream templateStream = new ClassPathResource(TEMPLATE_RESOURCE).getInputStream()) {
            reader = new PdfReader(templateStream);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            stamper = new PdfStamper(reader, outputStream);
            BaseFont baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);

            float pageHeight = reader.getPageSize(1).getHeight();

            PdfContentByte page1 = stamper.getOverContent(1);
            clearAndDrawText(page1, baseFont, CUG_FONT_SIZE, CUG_X, CUG_Y_MIN, CUG_Y_MAX, CUG_CLEAR_WIDTH,
                    CUG_CLEAR_PADDING, CUG_CLEAR_COLOR, pageHeight, values.cug);
            clearAndDrawText(page1, baseFont, SMALL_TEXT_FONT_SIZE, PATIENT_NAME_X,
                    PATIENT_NAME_Y_MAX - SMALL_TEXT_HEIGHT, PATIENT_NAME_Y_MAX, SMALL_TEXT_CLEAR_WIDTH,
                    SMALL_TEXT_CLEAR_PADDING, BaseColor.WHITE, pageHeight, values.patientName);
            clearAndDrawText(page1, baseFont, SMALL_TEXT_FONT_SIZE, PATIENT_DNI_X,
                    PATIENT_DNI_Y_MAX - SMALL_TEXT_HEIGHT, PATIENT_DNI_Y_MAX, SMALL_TEXT_CLEAR_WIDTH,
                    SMALL_TEXT_CLEAR_PADDING, BaseColor.WHITE, pageHeight, values.patientDni);
            clearAndDrawText(page1, baseFont, SMALL_TEXT_FONT_SIZE, PROVIDER_NAME_X,
                    PROVIDER_NAME_Y_MAX - SMALL_TEXT_HEIGHT, PROVIDER_NAME_Y_MAX, SMALL_TEXT_CLEAR_WIDTH,
                    SMALL_TEXT_CLEAR_PADDING, BaseColor.WHITE, pageHeight, values.providerName);
            clearAndDrawText(page1, baseFont, SMALL_TEXT_FONT_SIZE, PROVIDER_DNI_X,
                    PROVIDER_DNI_Y_MAX - SMALL_TEXT_HEIGHT, PROVIDER_DNI_Y_MAX, SMALL_TEXT_CLEAR_WIDTH,
                    SMALL_TEXT_CLEAR_PADDING, BaseColor.WHITE, pageHeight, values.providerDni);
            clearAndDrawText(page1, baseFont, SMALL_TEXT_FONT_SIZE, DATE_X, DATE_Y_MAX - SMALL_TEXT_HEIGHT, DATE_Y_MAX,
                    SMALL_TEXT_CLEAR_WIDTH, SMALL_TEXT_CLEAR_PADDING, BaseColor.WHITE, pageHeight, values.orderDate);

            clearSampleTestLines(page1, pageHeight);
            renderTests(stamper, reader, baseFont, pageHeight, values.tests);

            stamper.close();
            stamper = null;
            reader.close();
            reader = null;
            return outputStream.toByteArray();
        } catch (Exception e) {
            logger.error("Error generating consent template PDF", e);
            return new byte[0];
        } finally {
            if (stamper != null) {
                try {
                    stamper.close();
                } catch (Exception e) {
                    logger.debug("Error closing consent PDF stamper", e);
                }
            }
            if (reader != null) {
                reader.close();
            }
        }
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
            JSONObject fields = parsed.optJSONObject(FIELDS_KEY);
            if (fields != null) {
                Map<String, String> fieldSources = new LinkedHashMap<>();
                for (String key : fields.keySet()) {
                    String value = StringUtils.trimToNull(fields.optString(key));
                    if (value != null) {
                        fieldSources.put(key, value);
                    }
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
        defaults.put(FIELD_CUG, SOURCE_ORDER_ADDITIONAL_PREFIX + "cug");
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
            if (cleanedKey != null && cleanedValue != null) {
                normalized.put(cleanedKey, cleanedValue);
            }
        });
        return normalized;
    }

    private String buildDefinitionJson(Map<String, String> fields) {
        JSONObject root = new JSONObject();
        JSONObject fieldsJson = new JSONObject();
        if (fields != null) {
            fields.forEach(fieldsJson::put);
        }
        root.put(FIELDS_KEY, fieldsJson);
        return root.toString();
    }

    private ResolvedConsentValues resolveValues(Map<String, String> fields, ConsentPreviewForm form) {
        Map<String, String> sources = fields == null ? defaultFieldSources() : fields;
        ResolvedConsentValues values = new ResolvedConsentValues();
        values.cug = resolveValue(sources.get(FIELD_CUG), form);
        values.patientName = resolveValue(sources.get(FIELD_PATIENT_NAME), form);
        values.patientDni = resolveValue(sources.get(FIELD_PATIENT_DNI), form);
        values.providerName = resolveValue(sources.get(FIELD_PROVIDER_NAME), form);
        values.providerDni = resolveValue(sources.get(FIELD_PROVIDER_DNI), form);
        values.orderDate = resolveValue(sources.get(FIELD_DATE), form);
        values.tests = resolveTests(sources.get(FIELD_TESTS), form);
        return values;
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

    private void clearAndDrawText(PdfContentByte canvas, BaseFont font, float fontSize, float xMin, float yMin,
            float yMax, float clearWidth, float padding, BaseColor color, float pageHeight, String value) {
        clearRect(canvas, xMin, yMin, yMax, clearWidth, padding, color, pageHeight);
        if (!GenericValidator.isBlankOrNull(value)) {
            drawText(canvas, font, fontSize, xMin, yMax, value, pageHeight);
        }
    }

    private void drawText(PdfContentByte canvas, BaseFont font, float fontSize, float x, float yMax, String text,
            float pageHeight) {
        float y = pageHeight - yMax;
        Font pdfFont = new Font(font, fontSize);
        ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, new Phrase(text, pdfFont), x, y, 0f);
    }

    private void clearRect(PdfContentByte canvas, float xMin, float yMin, float yMax, float width, float padding,
            BaseColor color, float pageHeight) {
        float rectHeight = yMax - yMin;
        float rectX = xMin - padding;
        float rectY = pageHeight - yMax - padding;
        float rectWidth = width + (padding * 2);
        float rectHeightWithPadding = rectHeight + (padding * 2);
        canvas.saveState();
        canvas.setColorFill(color);
        canvas.rectangle(rectX, rectY, rectWidth, rectHeightWithPadding);
        canvas.fill();
        canvas.restoreState();
    }

    private void clearSampleTestLines(PdfContentByte canvas, float pageHeight) {
        for (int i = 0; i < TESTS_SAMPLE_LINES; i++) {
            float yMax = TESTS_START_Y_MAX + (TESTS_LINE_SPACING * i);
            float yMin = yMax - SMALL_TEXT_HEIGHT;
            clearRect(canvas, TESTS_START_X, yMin, yMax, TESTS_CLEAR_WIDTH, SMALL_TEXT_CLEAR_PADDING, BaseColor.WHITE,
                    pageHeight);
        }
    }

    private void renderTests(PdfStamper stamper, PdfReader reader, BaseFont font, float pageHeight,
            List<String> tests) {
        List<String> values = tests == null ? Collections.emptyList() : tests;
        if (values.isEmpty()) {
            return;
        }
        int linesPerPage = (int) Math.floor((TESTS_MAX_Y_MAX - TESTS_START_Y_MAX) / TESTS_LINE_SPACING) + 1;
        int linesToRender = Math.min(values.size(), linesPerPage);
        float yMax = TESTS_START_Y_MAX;
        PdfContentByte canvas = stamper.getOverContent(1);

        for (int i = 0; i < linesToRender; i++) {
            String testName = sanitizeValue(values.get(i));
            if (GenericValidator.isBlankOrNull(testName)) {
                yMax += TESTS_LINE_SPACING;
                continue;
            }

            String displayText = truncateToWidth(testName, font, SMALL_TEXT_FONT_SIZE, TESTS_TEXT_MAX_WIDTH);
            boolean hasMoreTests = values.size() > linesPerPage;
            if (hasMoreTests && i == linesToRender - 1) {
                displayText = truncateWithEllipsis(displayText, font, SMALL_TEXT_FONT_SIZE, TESTS_TEXT_MAX_WIDTH);
            }

            float yMin = yMax - SMALL_TEXT_HEIGHT;
            clearRect(canvas, TESTS_START_X, yMin, yMax, TESTS_CLEAR_WIDTH, SMALL_TEXT_CLEAR_PADDING, BaseColor.WHITE,
                    pageHeight);
            drawText(canvas, font, SMALL_TEXT_FONT_SIZE, TESTS_START_X, yMax, displayText, pageHeight);
            yMax += TESTS_LINE_SPACING;
        }
    }

    private String truncateToWidth(String value, BaseFont font, float fontSize, float maxWidth) {
        if (GenericValidator.isBlankOrNull(value)) {
            return "";
        }
        String text = value.trim();
        if (font.getWidthPoint(text, fontSize) <= maxWidth) {
            return text;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String candidate = builder.toString() + ch;
            if (font.getWidthPoint(candidate, fontSize) > maxWidth) {
                break;
            }
            builder.append(ch);
        }
        return builder.toString().trim();
    }

    private String truncateWithEllipsis(String value, BaseFont font, float fontSize, float maxWidth) {
        String base = GenericValidator.isBlankOrNull(value) ? "" : value.trim();
        if (GenericValidator.isBlankOrNull(base)) {
            return ELLIPSIS;
        }

        String candidate = base + " " + ELLIPSIS;
        if (font.getWidthPoint(candidate, fontSize) <= maxWidth) {
            return candidate;
        }

        StringBuilder builder = new StringBuilder(base);
        while (builder.length() > 0) {
            builder.setLength(builder.length() - 1);
            candidate = builder.toString().trim() + " " + ELLIPSIS;
            if (font.getWidthPoint(candidate, fontSize) <= maxWidth) {
                return candidate;
            }
        }
        return ELLIPSIS;
    }

    private static class ResolvedConsentValues {
        private String cug;
        private String patientName;
        private String patientDni;
        private String providerName;
        private String providerDni;
        private String orderDate;
        private List<String> tests = Collections.emptyList();
    }
}
