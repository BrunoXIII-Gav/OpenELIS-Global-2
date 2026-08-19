package org.openelisglobal.reportdefinition.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.image.service.ImageService;
import org.openelisglobal.image.valueholder.Image;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.reportdefinition.form.ValidationTemplateOverrideForm;
import org.openelisglobal.reportdefinition.service.ReportDefinitionService;
import org.openelisglobal.reportdefinition.valueholder.ReportDefinition;
import org.openelisglobal.reports.action.implementation.ReportImplementationFactory;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.openelisglobal.sample.valueholder.SampleAdditionalField.AdditionalFieldName;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.service.TestAdditionalFieldService;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@RestController
@RequestMapping("/rest/reports/validation-template-overrides")
public class ValidationTemplateOverrideRestController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(ValidationTemplateOverrideRestController.class);
    private static final String CATEGORY = "validation_template_override";
    private static final String REPORT_KEY = "report";
    private static final String TEST_ID_KEY = "testId";
    private static final String TEST_IDS_KEY = "testIds";
    private static final String TEST_CODE_KEY = "testCode";
    private static final String TEST_CODES_KEY = "testCodes";
    private static final String CONFIG_KEY = "config";
    private static final String DYNAMIC_REPORT_KEY = "dynamicJasperValidation";
    private static final String CONFIG_MODE_KEY = "mode";
    private static final String CONFIG_MODE_DYNAMIC_JASPER = "jasper_dynamic";
    private static final String TEMPLATE_NAME_KEY = "templateName";
    private static final String TEMPLATE_CONTENT_KEY = "templateContent";
    private static final String TEMPLATE_FILENAME_KEY = "templateOriginalFilename";
    private static final String PARAMETER_DEFINITIONS_KEY = "parameterDefinitions";
    private static final String WARNING_MESSAGES_KEY = "warningMessages";
    private static final String MAPPINGS_KEY = "mappings";
    private static final String DATA_TYPE_TEXT = "text";
    private static final String DATA_TYPE_NUMBER = "number";
    private static final String DATA_TYPE_IMAGE = "image";
    private static final String USER_FIELD_TYPE = "USER";
    private static final List<String> VALIDATION_REPORT_CANDIDATES = Arrays.asList("patientCILNSP_vreduit",
            "patientDMPK", "patientCILNSP", "patientHaitiClinical", "patientHaitiLNSP", "TBPatientReport");
    private static final List<String> DMPK_SECTIONS = Arrays.asList("PATIENT", "REQUESTING_PHYSICIAN", "SAMPLE",
            "MOLECULAR_RESULT", "CONCLUSION");

    @Autowired
    private ReportDefinitionService reportDefinitionService;
    @Autowired
    private TestAdditionalFieldService testAdditionalFieldService;
    @Autowired
    private OrderAdditionalFieldService orderAdditionalFieldService;
    @Autowired
    private SampleTypeAdditionalFieldService sampleTypeAdditionalFieldService;
    @Autowired
    private TypeOfSampleService typeOfSampleService;
    @Autowired
    private ImageService imageService;
    @Autowired
    private org.openelisglobal.professionalprofile.service.ProfessionalProfileFieldConfigService professionalProfileFieldConfigService;
    @Autowired
    private org.openelisglobal.common.util.ConfigurationProperties configurationProperties;

    @GetMapping
    public ResponseEntity<?> getOverrides() {
        try {
            List<ReportDefinition> definitions = reportDefinitionService.getDefinitionsByCategory(CATEGORY);
            if (definitions == null || definitions.isEmpty()) {
                return ResponseEntity.ok(Collections.emptyList());
            }
            List<ReportDefinition> deduped = dedupeDefinitions(definitions);
            List<ValidationTemplateOverrideForm> response = deduped.stream().map(this::toForm).filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error retrieving validation template overrides", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving validation template overrides");
        }
    }

    @GetMapping("/report-options")
    public ResponseEntity<?> getReportOptions() {
        try {
            List<IdValuePair> options = new ArrayList<>();
            options.add(new IdValuePair(DYNAMIC_REPORT_KEY, "Uploaded Jasper Template"));
            options.addAll(VALIDATION_REPORT_CANDIDATES.stream()
                    .filter(report -> ReportImplementationFactory.getReportCreator(report) != null)
                    .map(report -> new IdValuePair(report, report)).collect(Collectors.toList()));
            return ResponseEntity.ok(options);
        } catch (Exception e) {
            logger.error("Error retrieving validation template report options", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving validation template report options");
        }
    }

    @GetMapping("/field-options")
    public ResponseEntity<?> getFieldOptions(@RequestParam(value = "testIds", required = false) List<String> testIds) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("sections", new JSONArray(DMPK_SECTIONS));
            payload.put("sources", new JSONArray(buildSourceOptions(sanitizeList(testIds))));
            payload.put("defaultSectionFields", new JSONArray(buildDefaultSectionFields()));
            payload.put("imageOptions", new JSONArray(buildImageOptions()));
            return ResponseEntity.ok(payload.toMap());
        } catch (Exception e) {
            logger.error("Error retrieving validation template field options", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving validation template field options");
        }
    }

    @GetMapping("/user-field-options")
    public ResponseEntity<?> getUserFieldOptions(@RequestParam(value = "testIds", required = false) List<String> testIds) {
        try {
            List<String> sanitizedTestIds = sanitizeList(testIds);
            JSONObject payload = new JSONObject();
            payload.put("userFields", new JSONArray(buildUserFieldOptions(sanitizedTestIds)));
            payload.put("validatorProfiles", new JSONArray(getValidatorProfileCodes()));
            payload.put("profileFieldsByProfile", new JSONObject(buildProfileFieldsByProfile()));
            return ResponseEntity.ok(payload.toMap());
        } catch (Exception e) {
            logger.error("Error retrieving user field options", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving user field options");
        }
    }

    @PostMapping("/parse-template")
    public ResponseEntity<?> parseTemplate(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("JRXML template file is required");
            }

            String filename = StringUtils.defaultString(file.getOriginalFilename());
            if (!filename.toLowerCase().endsWith(".jrxml")) {
                return ResponseEntity.badRequest().body("Only JRXML templates are supported");
            }

            String templateContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok(parseTemplateMetadata(filename, templateContent).toMap());
        } catch (Exception e) {
            logger.error("Error parsing Jasper template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error parsing Jasper template");
        }
    }

    @PostMapping
    public ResponseEntity<?> createOverride(HttpServletRequest request,
            @RequestBody ValidationTemplateOverrideForm form) {
        return saveOverride(request, null, form);
    }

    @GetMapping("/{id}/download-template")
    public ResponseEntity<?> downloadTemplate(@PathVariable String id) {
        try {
            if (GenericValidator.isBlankOrNull(id)) {
                return ResponseEntity.badRequest().body("Template id is required");
            }

            ReportDefinition definition = reportDefinitionService.get(id);
            if (definition == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Template override not found");
            }

            ValidationTemplateOverrideForm form = toForm(definition);
            Map<String, Object> config = form == null ? null : form.getConfig();
            String templateContent = readString(config == null ? null : config.get(TEMPLATE_CONTENT_KEY));
            if (GenericValidator.isBlankOrNull(templateContent)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("JRXML template content not found");
            }

            String filename = sanitize(readString(config == null ? null : config.get(TEMPLATE_FILENAME_KEY)));
            if (GenericValidator.isBlankOrNull(filename)) {
                filename = sanitize(readString(config == null ? null : config.get(TEMPLATE_NAME_KEY)));
            }
            if (GenericValidator.isBlankOrNull(filename)) {
                filename = "validation-template.jrxml";
            } else if (!filename.toLowerCase().endsWith(".jrxml")) {
                filename = filename + ".jrxml";
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(templateContent.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("Error downloading Jasper template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error downloading Jasper template");
        }
    }

    @PostMapping("/upload-image")
    public ResponseEntity<?> uploadOverrideImage(HttpServletRequest request, @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("Image file is required");
            }
            if (!isSupportedImage(file)) {
                return ResponseEntity.badRequest().body("Unsupported image. Only png/jpg/jpeg/gif are allowed.");
            }

            Image image = new Image();
            image.setDescription(buildImageDescription(name, file.getOriginalFilename()));
            image.setImage(file.getBytes());
            image.setSysUserId(getSysUserId(request));
            Image saved = imageService.save(image);

            Map<String, String> response = new LinkedHashMap<>();
            response.put("id", saved.getId());
            response.put("key", "imageId:" + saved.getId());
            response.put("description", saved.getDescription());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error uploading validation template image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error uploading validation template image");
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateOverride(HttpServletRequest request, @PathVariable String id,
            @RequestBody ValidationTemplateOverrideForm form) {
        return saveOverride(request, id, form);
    }

    private ResponseEntity<?> saveOverride(HttpServletRequest request, String pathId,
            ValidationTemplateOverrideForm form) {
        try {
            if (form == null) {
                return ResponseEntity.badRequest().body("Request body is required");
            }
            Map<String, Object> normalizedConfig = normalizeConfig(form.getConfig());
            boolean dynamicJasperTemplate = isDynamicJasperConfig(normalizedConfig);
            String reportName = dynamicJasperTemplate ? DYNAMIC_REPORT_KEY : sanitize(form.getReport());
            List<String> testIds = sanitizeList(form.getTestIds());
            List<String> testCodes = sanitizeList(form.getTestCodes());

            if (!dynamicJasperTemplate && GenericValidator.isBlankOrNull(reportName)) {
                return ResponseEntity.badRequest().body("Field 'report' is required");
            }
            if (testIds.isEmpty() && testCodes.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("At least one test identifier is required (testIds or testCodes)");
            }
            if (dynamicJasperTemplate && !hasDynamicTemplateContent(normalizedConfig)) {
                return ResponseEntity.badRequest().body("Upload a JRXML template before saving");
            }
            String configValidationError = validateDynamicConfig(normalizedConfig, testIds);
            if (configValidationError != null) {
                return ResponseEntity.badRequest().body(configValidationError);
            }

            String targetId = !GenericValidator.isBlankOrNull(pathId) ? pathId : sanitize(form.getId());
            boolean isCreate = GenericValidator.isBlankOrNull(targetId);
            ReportDefinition entity;
            if (isCreate) {
                ReportDefinition existing = findExistingOverride(reportName, testIds, testCodes);
                if (existing != null) {
                    entity = existing;
                    isCreate = false;
                } else {
                    entity = new ReportDefinition();
                    entity.setId("RPT-OVR-" + UUID.randomUUID().toString().replace("-", ""));
                    entity.setCreatedDate(Timestamp.from(Instant.now()));
                    entity.setCreatedBy(getSysUserId(request));
                }
            } else {
                entity = reportDefinitionService.get(targetId);
                if (entity == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Report definition not found");
                }
            }

            entity.setCategory(CATEGORY);
            entity.setName(resolveName(form, reportName, testIds, testCodes, normalizedConfig));
            entity.setDescription(sanitize(form.getDescription()));
            entity.setDefinitionJson(buildDefinitionJson(reportName, testIds, testCodes, normalizedConfig));
            entity.setIsActive(form.getIsActive() == null ? Boolean.TRUE : form.getIsActive());
            entity.setSysUserId(getSysUserId(request));

            if (isCreate) {
                reportDefinitionService.insert(entity);
            } else {
                reportDefinitionService.update(entity);
            }

            return ResponseEntity.ok(toForm(entity));
        } catch (Exception e) {
            logger.error("Error saving validation template override", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error saving validation template override");
        }
    }

    private ValidationTemplateOverrideForm toForm(ReportDefinition definition) {
        if (definition == null || GenericValidator.isBlankOrNull(definition.getDefinitionJson())) {
            return null;
        }
        try {
            JSONObject parsed = new JSONObject(definition.getDefinitionJson());
            ValidationTemplateOverrideForm form = new ValidationTemplateOverrideForm();
            form.setId(definition.getId());
            form.setName(definition.getName());
            form.setDescription(definition.getDescription());
            form.setCreatedBy(definition.getCreatedBy());
            form.setCreatedDate(definition.getCreatedDate());
            form.setLastupdated(definition.getLastupdated());
            form.setIsActive(definition.getIsActive());
            form.setReport(sanitize(parsed.optString(REPORT_KEY)));
            form.setTestIds(readIds(parsed, TEST_ID_KEY, TEST_IDS_KEY));
            form.setTestCodes(readIds(parsed, TEST_CODE_KEY, TEST_CODES_KEY));
            JSONObject configObject = parsed.optJSONObject(CONFIG_KEY);
            if (configObject != null) {
                form.setConfig(configObject.toMap());
            }
            return form;
        } catch (Exception e) {
            logger.warn("Skipping invalid validation template override JSON for id={}", definition.getId(), e);
            return null;
        }
    }

    private List<String> readIds(JSONObject parsed, String singleKey, String listKey) {
        List<String> values = new ArrayList<>();
        String single = sanitize(parsed.optString(singleKey));
        if (!GenericValidator.isBlankOrNull(single)) {
            values.add(single);
        }
        JSONArray list = parsed.optJSONArray(listKey);
        if (list != null) {
            for (int i = 0; i < list.length(); i++) {
                String value = sanitize(list.optString(i));
                if (!GenericValidator.isBlankOrNull(value)) {
                    values.add(value);
                }
            }
        }
        return values.stream().distinct().collect(Collectors.toList());
    }

    private String resolveName(ValidationTemplateOverrideForm form, String reportName, List<String> testIds,
            List<String> testCodes, Map<String, Object> config) {
        String requestedName = sanitize(form.getName());
        if (!GenericValidator.isBlankOrNull(requestedName)) {
            return requestedName;
        }
        if (isDynamicJasperConfig(config)) {
            String templateName = sanitize(readString(config.get(TEMPLATE_NAME_KEY)));
            if (!GenericValidator.isBlankOrNull(templateName)) {
                return templateName;
            }
        }
        String scope = !testCodes.isEmpty() ? String.join(", ", testCodes) : String.join(", ", testIds);
        return "Validation Template Override - " + reportName + " - " + scope;
    }

    private String buildDefinitionJson(String reportName, List<String> testIds, List<String> testCodes,
            Map<String, Object> config) {
        JSONObject payload = new JSONObject();
        payload.put(REPORT_KEY, reportName);

        if (testIds.size() == 1) {
            payload.put(TEST_ID_KEY, testIds.get(0));
        } else if (!testIds.isEmpty()) {
            payload.put(TEST_IDS_KEY, new JSONArray(testIds));
        }

        if (testCodes.size() == 1) {
            payload.put(TEST_CODE_KEY, testCodes.get(0));
        } else if (!testCodes.isEmpty()) {
            payload.put(TEST_CODES_KEY, new JSONArray(testCodes));
        }

        if (config != null && !config.isEmpty()) {
            payload.put(CONFIG_KEY, new JSONObject(config));
        }
        return payload.toString();
    }

    private ReportDefinition findExistingOverride(String reportName, List<String> testIds, List<String> testCodes) {
        List<ReportDefinition> definitions = reportDefinitionService.getDefinitionsByCategory(CATEGORY);
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        Set<String> requestedTestIds = normalizeSet(testIds);
        Set<String> requestedTestCodes = normalizeSet(testCodes);

        for (ReportDefinition definition : definitions) {
            String raw = definition.getDefinitionJson();
            if (GenericValidator.isBlankOrNull(raw)) {
                continue;
            }
            try {
                JSONObject parsed = new JSONObject(raw);
                String existingReport = sanitize(parsed.optString(REPORT_KEY));
                if (!StringUtils.equals(existingReport, reportName)) {
                    continue;
                }
                Set<String> existingTestIds = normalizeSet(readIds(parsed, TEST_ID_KEY, TEST_IDS_KEY));
                Set<String> existingTestCodes = normalizeSet(readIds(parsed, TEST_CODE_KEY, TEST_CODES_KEY));
                if (existingTestIds.equals(requestedTestIds) && existingTestCodes.equals(requestedTestCodes)) {
                    return definition;
                }
            } catch (Exception e) {
                logger.warn("Skipping invalid override while searching duplicates: id={}", definition.getId());
            }
        }
        return null;
    }

    private Set<String> normalizeSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return values.stream().map(this::sanitize).filter(value -> !GenericValidator.isBlankOrNull(value))
                .map(String::trim).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<ReportDefinition> dedupeDefinitions(List<ReportDefinition> definitions) {
        Map<String, ReportDefinition> byIdentity = new LinkedHashMap<>();
        for (ReportDefinition definition : definitions) {
            String identity = buildIdentityKey(definition);
            if (identity == null) {
                byIdentity.put("id:" + definition.getId(), definition);
                continue;
            }
            ReportDefinition existing = byIdentity.get(identity);
            if (existing == null || isMoreRecent(definition, existing)) {
                byIdentity.put(identity, definition);
            }
        }
        return new ArrayList<>(byIdentity.values());
    }

    private String buildIdentityKey(ReportDefinition definition) {
        if (definition == null || GenericValidator.isBlankOrNull(definition.getDefinitionJson())) {
            return null;
        }
        try {
            JSONObject parsed = new JSONObject(definition.getDefinitionJson());
            String report = sanitize(parsed.optString(REPORT_KEY));
            if (GenericValidator.isBlankOrNull(report)) {
                return null;
            }
            Set<String> ids = normalizeSet(readIds(parsed, TEST_ID_KEY, TEST_IDS_KEY));
            Set<String> codes = normalizeSet(readIds(parsed, TEST_CODE_KEY, TEST_CODES_KEY));
            return report + "|ids:" + String.join(",", ids) + "|codes:" + String.join(",", codes);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isMoreRecent(ReportDefinition candidate, ReportDefinition current) {
        Timestamp candidateTs = candidate.getLastupdated() != null ? candidate.getLastupdated()
                : candidate.getCreatedDate();
        Timestamp currentTs = current.getLastupdated() != null ? current.getLastupdated() : current.getCreatedDate();
        if (candidateTs == null) {
            return false;
        }
        if (currentTs == null) {
            return true;
        }
        return candidateTs.after(currentTs);
    }

    private String sanitize(String raw) {
        return raw == null ? null : raw.trim();
    }

    private List<String> sanitizeList(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Collections.emptyList();
        }
        return rawValues.stream().map(this::sanitize).filter(value -> !GenericValidator.isBlankOrNull(value)).distinct()
                .collect(Collectors.toList());
    }

    /**
     * Build list of USER field options from test configuration.
     * Each USER field represents an analyst/technician selector in result entry.
     */
    private List<Map<String, Object>> buildUserFieldOptions(List<String> testIds) {
        if (testIds == null || testIds.isEmpty()) {
            List<Map<String, Object>> userFields = new ArrayList<>();
            addRequesterUserFieldOption(userFields);
            addValidatorUserFieldOption(userFields);
            return userFields;
        }

        List<Map<String, Object>> userFields = new ArrayList<>();
        int fieldCounter = 1;

        for (String testId : testIds) {
            if (GenericValidator.isBlankOrNull(testId)) {
                continue;
            }

            List<TestAdditionalFieldPayload> fields = testAdditionalFieldService.getFieldsForTest(testId, false);
            if (fields == null || fields.isEmpty()) {
                continue;
            }

            for (TestAdditionalFieldPayload field : fields) {
                if (!isAnalystSelectorField(field)) {
                    continue;
                }

                Map<String, Object> userField = new LinkedHashMap<>();
                userField.put("fieldId", "analyst_" + fieldCounter);
                userField.put("fieldKey", field.getFieldKey());
                userField.put("displayName", field.getDisplayName());
                userField.put("testId", testId);
                userField.put("profileCodes", extractProfileCodes(field.getMetadataJson()));
                userField.put("includeInValidation", Boolean.TRUE.equals(field.getIncludeInValidation()));
                userField.put("isValidatorField", false);

                userFields.add(userField);
                fieldCounter++;
            }
        }

        addRequesterUserFieldOption(userFields);
        addValidatorUserFieldOption(userFields);

        return userFields;
    }

    private void addRequesterUserFieldOption(List<Map<String, Object>> userFields) {
        List<String> requesterProfiles = getOrderProviderProfileCodes();
        if (requesterProfiles.isEmpty()) {
            return;
        }

        Map<String, Object> requesterField = new LinkedHashMap<>();
        requesterField.put("fieldId", "requester");
        requesterField.put("fieldKey", "requester");
        requesterField.put("displayName", "Solicitante");
        requesterField.put("testId", null);
        requesterField.put("profileCodes", requesterProfiles);
        requesterField.put("includeInValidation", true);
        requesterField.put("isValidatorField", false);
        userFields.add(requesterField);
    }

    private void addValidatorUserFieldOption(List<Map<String, Object>> userFields) {
        List<String> validatorProfiles = getValidatorProfileCodes();
        if (validatorProfiles.isEmpty()) {
            return;
        }

        Map<String, Object> validatorField = new LinkedHashMap<>();
        validatorField.put("fieldId", "validator");
        validatorField.put("fieldKey", "validator");
        validatorField.put("displayName", "Validator");
        validatorField.put("testId", null);
        validatorField.put("profileCodes", validatorProfiles);
        validatorField.put("includeInValidation", true);
        validatorField.put("isValidatorField", true);
        userFields.add(validatorField);
    }

    /**
     * Extract profile codes from metadataJson field.
     */
    private List<String> extractProfileCodes(String metadataJson) {
        if (StringUtils.isBlank(metadataJson)) {
            return Collections.emptyList();
        }

        try {
            JSONObject metadata = new JSONObject(metadataJson);
            JSONArray codes = metadata.optJSONArray("userProfileCodes");
            if (codes == null) {
                return Collections.emptyList();
            }

            List<String> profileCodes = new ArrayList<>();
            for (int i = 0; i < codes.length(); i++) {
                String code = codes.optString(i);
                if (StringUtils.isNotBlank(code)) {
                    profileCodes.add(code.trim().toUpperCase());
                }
            }
            return profileCodes;
        } catch (Exception e) {
            logger.warn("Failed to parse metadataJson for profile codes", e);
            return Collections.emptyList();
        }
    }

    /**
     * Get list of professional profile codes allowed for validators.
     */
    private List<String> getValidatorProfileCodes() {
        return getConfiguredProfessionalProfileCodes(
                org.openelisglobal.common.util.ConfigurationProperties.Property.validationInterpreterProfessionalProfileCode);
    }

    private List<String> getOrderProviderProfileCodes() {
        return getConfiguredProfessionalProfileCodes(
                org.openelisglobal.common.util.ConfigurationProperties.Property.orderProviderProfessionalProfileCode);
    }

    private List<String> getConfiguredProfessionalProfileCodes(
            org.openelisglobal.common.util.ConfigurationProperties.Property property) {
        String rawValue = org.openelisglobal.common.util.ConfigurationProperties.getInstance().getPropertyValue(property);
        if (StringUtils.isBlank(rawValue)) {
            return Collections.emptyList();
        }

        return Arrays.stream(rawValue.split(",")).map(String::trim).map(String::toUpperCase)
                .filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    }

    /**
     * Build map of profile fields grouped by professional profile code.
     */
    private Map<String, List<Map<String, Object>>> buildProfileFieldsByProfile() {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();

        // Get all unique profile codes from user fields and validators
        Set<String> allProfileCodes = new LinkedHashSet<>();
        allProfileCodes.addAll(getValidatorProfileCodes());
        allProfileCodes.addAll(getOrderProviderProfileCodes());

        // Add profile codes from configured professional profiles
        String rawProfiles = org.openelisglobal.common.util.ConfigurationProperties.getInstance()
                .getPropertyValue(org.openelisglobal.common.util.ConfigurationProperties.Property.professionalProfileOptions);
        if (StringUtils.isNotBlank(rawProfiles)) {
            Arrays.stream(rawProfiles.split(","))
                    .map(part -> part.contains("|") ? part.split("\\|")[0].trim() : part.trim())
                    .map(String::toUpperCase)
                    .filter(StringUtils::isNotBlank)
                    .forEach(allProfileCodes::add);
        }

        // Build fields for each profile
        for (String profileCode : allProfileCodes) {
            List<Map<String, Object>> fields = buildFieldsForProfile(profileCode);
            if (!fields.isEmpty()) {
                result.put(profileCode, fields);
            }
        }

        return result;
    }

    /**
     * Build list of available fields for a specific professional profile.
     */
    private List<Map<String, Object>> buildFieldsForProfile(String profileCode) {
        List<Map<String, Object>> fields = new ArrayList<>();

        // Add system fields (from Provider entity)
        fields.add(buildProfileField("name", "Name", "text", true, null));
        fields.add(buildProfileField("specialty", "Specialty", "text", true, "specialty"));
        fields.add(buildProfileField("cbpCode", "CBP Code", "text", true, "cbpCode"));
        fields.add(buildProfileField("professionalInitials", "Professional Initials", "text", true, "professionalInitials"));
        fields.add(buildProfileField("dni", "DNI", "text", true, "dni"));
        fields.add(buildProfileField("npi", "NPI", "text", true, "npi"));
        fields.add(buildProfileField("signature", "Signature", "image", true, null));

        // Add custom fields configured for this profile
        try {
            List<org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldDefinitionForm> customFields = 
                    professionalProfileFieldConfigService.getFieldsForProfile(profileCode, false);

            for (org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldDefinitionForm customField : customFields) {
                if (Boolean.TRUE.equals(customField.getActive()) && !Boolean.TRUE.equals(customField.getSystemField())) {
                    String dataType = mapFieldTypeToDataType(customField.getFieldType());
                    fields.add(buildProfileField(
                            customField.getFieldKey(),
                            customField.getDisplayName(),
                            dataType,
                            false,
                            null
                    ));
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to load custom fields for profile: " + profileCode, e);
        }

        return fields;
    }

    /**
     * Build a single profile field map.
     */
    private Map<String, Object> buildProfileField(String fieldKey, String displayName, String dataType, 
                                                    boolean isSystemField, String legacyBinding) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldKey", fieldKey);
        field.put("displayName", displayName);
        field.put("dataType", dataType);
        field.put("isSystemField", isSystemField);
        field.put("legacyBinding", legacyBinding);
        return field;
    }

    /**
     * Map field type from ProfessionalProfileFieldDefinition to simple data type.
     */
    private String mapFieldTypeToDataType(String fieldType) {
        if (StringUtils.isBlank(fieldType)) {
            return "text";
        }

        String normalized = fieldType.trim().toUpperCase();
        return switch (normalized) {
            case "NUMBER", "DECIMAL" -> "number";
            case "BOOLEAN", "CHECKBOX" -> "boolean";
            case "DATE" -> "date";
            case "DATETIME" -> "datetime";
            case "IMAGE", "FILE" -> "image";
            default -> "text";
        };
    }

    private List<Map<String, Object>> buildSourceOptions(List<String> testIds) {
        List<Map<String, Object>> options = new ArrayList<>();
        addSourceOption(options, "patientName", "Patient Name");
        addSourceOption(options, "dni", "DNI");
        addSourceOption(options, "passportNumber", "Passport");
        addSourceOption(options, "foreignId", "Foreigner Card");
        addSourceOption(options, "nationalId", "National ID");
        addSourceOption(options, "subjectNumber", "HC / Subject Number");
        addSourceOption(options, "sampleCug", "CUG");
        addSourceOption(options, "accessionNumber", "Accession Number");
        addSourceOption(options, "gender", "Gender");
        addSourceOption(options, "dob", "Birth Date");
        addSourceOption(options, "patientSiteNumber", "Contact");
        addSourceOption(options, "prescriber", "Requesting Physician");
        addSourceOption(options, "requesterFirstName", "Requester First Name");
        addSourceOption(options, "requesterLastName", "Requester Last Name");
        addSourceOption(options, "requesterPhone", "Requester Phone");
        addSourceOption(options, "requesterEmail", "Requester Email");
        addSourceOption(options, "requesterCmp", "Requester CMP");
        addSourceOption(options, "requesterRne", "Requester RNE");
        addSourceOption(options, "requesterSpecialty", "Requester Specialty");
        addSourceOption(options, "siteInfo", "Referring Site");
        addSourceOption(options, "collectionDate", "Collection Date");
        addSourceOption(options, "collectionDateTime", "Collection Date/Time");
        addSourceOption(options, "sampleType", "Sample Source");
        addSourceOption(options, "validationDate", "Validation Date");
        addSourceOption(options, "validatorName", "Validator Name");
        addSourceOption(options, "validatorSpecialty", "Validator Specialty");
        addAnalystSourceOptions(options, testIds);
        addSourceOption(options, "orderDate", "Order Date");
        addSourceOption(options, "orderFinishDate", "Order Finish Date");
        addSourceOption(options, "testDate", "Test Date");
        addSourceOption(options, "analysisResult1", "Analysis Result #1", DATA_TYPE_NUMBER);
        addSourceOption(options, "analysisResult2", "Analysis Result #2", DATA_TYPE_NUMBER);
        addAdditionalFieldSourceOptions(options, testIds);
        addOrderAdditionalFieldSourceOptions(options);
        addSampleAdditionalFieldSourceOptions(options, testIds);
        addSourceOption(options, "additional.<field_key>", "Additional Field (use key)");
        addSourceOption(options, "orderAdditional.<field_key>", "Order Additional Field (use key)");
        addSourceOption(options, "sampleAdditional.<field_key>", "Sample Additional Field (use key)");
        return options;
    }

    private void addAnalystSourceOptions(List<Map<String, Object>> options, List<String> testIds) {
        int analystSlotCount = resolveAnalystSlotCount(testIds);
        for (int slotIndex = 1; slotIndex <= analystSlotCount; slotIndex++) {
            addSourceOption(options, "biologist" + slotIndex + "Name", "Analyst #" + slotIndex + " Name");
            addSourceOption(options, "biologist" + slotIndex + "Specialty",
                    "Analyst #" + slotIndex + " Specialty");
            addSourceOption(options, "biologist" + slotIndex + "CbpCode", "Analyst #" + slotIndex + " CBP Code");
        }
    }

    private int resolveAnalystSlotCount(List<String> testIds) {
        if (testIds == null || testIds.isEmpty()) {
            return 0;
        }

        int maxSlotCount = 0;
        for (String testId : testIds) {
            if (GenericValidator.isBlankOrNull(testId)) {
                continue;
            }

            List<TestAdditionalFieldPayload> fields = testAdditionalFieldService.getFieldsForTest(testId, false);
            if (fields == null || fields.isEmpty()) {
                continue;
            }

            int slotCount = (int) fields.stream().filter(this::isAnalystSelectorField).count();
            if (slotCount > maxSlotCount) {
                maxSlotCount = slotCount;
            }
        }
        return maxSlotCount;
    }

    private boolean isAnalystSelectorField(TestAdditionalFieldPayload field) {
        return field != null && Boolean.TRUE.equals(field.getActive())
                && isUserSelectorFieldType(field.getFieldType())
                && StringUtils.isNotBlank(field.getFieldKey());
    }

    private boolean isUserSelectorFieldType(String fieldType) {
        String normalizedFieldType = StringUtils.trimToNull(fieldType);
        return StringUtils.equalsIgnoreCase(USER_FIELD_TYPE, normalizedFieldType);
    }

    private List<Map<String, Object>> buildImageOptions() {
        List<Map<String, Object>> options = new ArrayList<>();
        addSourceOption(options, "headerLeftImage", "Site Header Left Logo", DATA_TYPE_IMAGE);
        addSourceOption(options, "headerRightImage", "Site Header Right Logo", DATA_TYPE_IMAGE);
        addSourceOption(options, "labDirectorSignature", "Lab Director Signature", DATA_TYPE_IMAGE);
        addSourceOption(options, "validatorSignature", "Validator Signature", DATA_TYPE_IMAGE);
        return options;
    }

    private JSONObject parseTemplateMetadata(String filename, String templateContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(new ByteArrayInputStream(templateContent.getBytes(StandardCharsets.UTF_8)));
        Element root = document.getDocumentElement();

        JSONObject payload = new JSONObject();
        payload.put(TEMPLATE_CONTENT_KEY, templateContent);
        payload.put(TEMPLATE_NAME_KEY, resolveTemplateName(filename, root));
        payload.put(TEMPLATE_FILENAME_KEY, StringUtils.defaultString(filename));

        JSONArray parameterDefinitions = new JSONArray();
        NodeList parameters = root.getElementsByTagName("parameter");
        for (int i = 0; i < parameters.getLength(); i++) {
            Element parameter = (Element) parameters.item(i);
            String name = sanitize(parameter.getAttribute("name"));
            if (GenericValidator.isBlankOrNull(name)) {
                continue;
            }
            JSONObject definition = new JSONObject();
            definition.put("name", name);
            definition.put("className",
                    StringUtils.defaultIfBlank(sanitize(parameter.getAttribute("class")), "java.lang.String"));
            parameterDefinitions.put(definition);
        }
        payload.put(PARAMETER_DEFINITIONS_KEY, parameterDefinitions);

        NodeList fields = root.getElementsByTagName("field");
        JSONArray warnings = new JSONArray();
        if (fields.getLength() > 0) {
            warnings.put("This template declares Jasper fields. The current OpenELIS flow fills parameters only.");
        }
        if (parameterDefinitions.length() == 0) {
            warnings.put("No Jasper parameters were detected. Add parameters before uploading.");
        }
        payload.put("fieldCount", fields.getLength());
        payload.put(WARNING_MESSAGES_KEY, warnings);
        payload.put(CONFIG_MODE_KEY, CONFIG_MODE_DYNAMIC_JASPER);
        return payload;
    }

    private String resolveTemplateName(String filename, Element root) {
        String reportName = root == null ? null : sanitize(root.getAttribute("name"));
        if (!GenericValidator.isBlankOrNull(reportName)) {
            return reportName;
        }
        if (GenericValidator.isBlankOrNull(filename)) {
            return "Uploaded Jasper Template";
        }
        return filename.replaceFirst("(?i)\\.jrxml$", "");
    }

    private boolean isDynamicJasperConfig(Map<String, Object> config) {
        return config != null && StringUtils.equals(CONFIG_MODE_DYNAMIC_JASPER, readString(config.get(CONFIG_MODE_KEY)));
    }

    private boolean hasDynamicTemplateContent(Map<String, Object> config) {
        return config != null && !GenericValidator.isBlankOrNull(readString(config.get(TEMPLATE_CONTENT_KEY)));
    }

    @SuppressWarnings("unchecked")
    private String validateDynamicConfig(Map<String, Object> config, List<String> testIds) {
        if (!isDynamicJasperConfig(config)) {
            return null;
        }
        Object mappingsObject = config.get(MAPPINGS_KEY);
        if (!(mappingsObject instanceof Map<?, ?>)) {
            return null;
        }

        Map<String, Map<String, Object>> sourceOptionsById = buildSourceOptions(testIds).stream()
                .filter(option -> !GenericValidator.isBlankOrNull(readString(option.get("id"))))
                .collect(Collectors.toMap(option -> readString(option.get("id")), option -> option, (left, right) -> left,
                        LinkedHashMap::new));

        Map<String, Object> mappings = (Map<String, Object>) mappingsObject;
        for (Map.Entry<String, Object> entry : mappings.entrySet()) {
            String parameterName = sanitize(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?>)) {
                continue;
            }
            Map<String, Object> mapping = (Map<String, Object>) entry.getValue();
            String type = readString(mapping.get("type"));
            if (!"conditional".equalsIgnoreCase(type)) {
                continue;
            }

            String conditionSource = readString(mapping.get("conditionSource"));
            if (GenericValidator.isBlankOrNull(conditionSource)) {
                return "Conditional mapping for " + parameterName + " requires a numeric source";
            }

            Map<String, Object> sourceMeta = sourceOptionsById.get(conditionSource);
            if (sourceMeta == null || !isNumericDataType(readString(sourceMeta.get("dataType")))) {
                return "Conditional mapping for " + parameterName + " must depend on a numeric source";
            }

            List<Map<String, Object>> rules = extractConditionalRules(mapping);
            if (rules.isEmpty()) {
                return "Conditional mapping for " + parameterName + " requires at least one rule";
            }
            for (Map<String, Object> rule : rules) {
                if (normalizeConditionalOperator(readString(rule.get("operator"))) == null) {
                    return "Conditional mapping for " + parameterName + " has an unsupported operator";
                }
                if (parseDecimal(readString(rule.get("compareTo"))) == null) {
                    return "Conditional mapping for " + parameterName
                            + " requires a valid numeric comparison value";
                }
                String branchError = validateConditionalBranch(parameterName, "rule", readString(rule.get("resultType")),
                        readString(rule.get("resultValue")), sourceOptionsById);
                if (branchError != null) {
                    return branchError;
                }
            }

            String fallbackType = StringUtils.defaultIfBlank(readString(mapping.get("fallbackType")),
                    StringUtils.defaultIfBlank(readString(mapping.get("falseType")), "empty"));
            String fallbackValue = StringUtils.defaultIfBlank(readString(mapping.get("fallbackValue")),
                    readString(mapping.get("falseValue")));
            String fallbackError = validateConditionalBranch(parameterName, "fallback", fallbackType, fallbackValue,
                    sourceOptionsById);
            if (fallbackError != null) {
                return fallbackError;
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeConfig(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return config;
        }
        Object mode = config.get(CONFIG_MODE_KEY);
        if (StringUtils.equals(CONFIG_MODE_DYNAMIC_JASPER, readString(mode))) {
            Object params = config.get(PARAMETER_DEFINITIONS_KEY);
            if (params == null) {
                config.put(PARAMETER_DEFINITIONS_KEY, new ArrayList<Map<String, String>>());
            }
            Object warnings = config.get(WARNING_MESSAGES_KEY);
            if (warnings == null) {
                config.put(WARNING_MESSAGES_KEY, new ArrayList<String>());
            }
        }
        return config;
    }

    private String readString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private String validateConditionalBranch(String parameterName, String branchName, String branchType,
            String branchValue, Map<String, Map<String, Object>> sourceOptionsById) {
        if (GenericValidator.isBlankOrNull(branchType)) {
            return "Conditional mapping for " + parameterName + " is missing the " + branchName + " branch type";
        }

        if ("empty".equalsIgnoreCase(branchType)) {
            return null;
        }

        if ("constant".equalsIgnoreCase(branchType)) {
            return GenericValidator.isBlankOrNull(branchValue)
                    ? "Conditional mapping for " + parameterName + " requires a value for the " + branchName
                            + " branch"
                    : null;
        }

        if (!"source".equalsIgnoreCase(branchType)) {
            return "Conditional mapping for " + parameterName + " has an unsupported " + branchName + " branch type";
        }

        if (GenericValidator.isBlankOrNull(branchValue)) {
            return "Conditional mapping for " + parameterName + " requires a source for the " + branchName + " branch";
        }

        Map<String, Object> sourceMeta = sourceOptionsById.get(branchValue);
        if (sourceMeta == null || DATA_TYPE_IMAGE.equalsIgnoreCase(readString(sourceMeta.get("dataType")))) {
            return "Conditional mapping for " + parameterName + " must use a valid value source for the " + branchName
                    + " branch";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractConditionalRules(Map<String, Object> mapping) {
        Object rulesObject = mapping.get("rules");
        if (rulesObject instanceof List<?>) {
            List<Map<String, Object>> rules = new ArrayList<>();
            for (Object ruleObject : (List<Object>) rulesObject) {
                if (ruleObject instanceof Map<?, ?>) {
                    rules.add((Map<String, Object>) ruleObject);
                }
            }
            if (!rules.isEmpty()) {
                return rules;
            }
        }

        String compareTo = readString(mapping.get("compareTo"));
        String operator = readString(mapping.get("operator"));
        String resultType = readString(mapping.get("trueType"));
        String resultValue = readString(mapping.get("trueValue"));
        if (GenericValidator.isBlankOrNull(compareTo) && GenericValidator.isBlankOrNull(operator)
                && GenericValidator.isBlankOrNull(resultType) && GenericValidator.isBlankOrNull(resultValue)) {
            return Collections.emptyList();
        }

        Map<String, Object> legacyRule = new LinkedHashMap<>();
        legacyRule.put("operator", operator);
        legacyRule.put("compareTo", compareTo);
        legacyRule.put("resultType", resultType);
        legacyRule.put("resultValue", resultValue);
        return Collections.singletonList(legacyRule);
    }

    private String normalizeConditionalOperator(String operator) {
        if (GenericValidator.isBlankOrNull(operator)) {
            return null;
        }
        String trimmed = operator.trim();
        if ("lt".equalsIgnoreCase(trimmed) || "<".equals(trimmed)) {
            return "lt";
        }
        if ("lte".equalsIgnoreCase(trimmed) || "<=".equals(trimmed)) {
            return "lte";
        }
        if ("eq".equalsIgnoreCase(trimmed) || "=".equals(trimmed) || "==".equals(trimmed)) {
            return "eq";
        }
        if ("gte".equalsIgnoreCase(trimmed) || ">=".equals(trimmed)) {
            return "gte";
        }
        if ("gt".equalsIgnoreCase(trimmed) || ">".equals(trimmed)) {
            return "gt";
        }
        return null;
    }

    private BigDecimal parseDecimal(String rawValue) {
        if (GenericValidator.isBlankOrNull(rawValue)) {
            return null;
        }
        String normalized = rawValue.trim().replace(" ", "");
        if (normalized.contains(",") && normalized.contains(".")) {
            if (normalized.lastIndexOf(',') > normalized.lastIndexOf('.')) {
                normalized = normalized.replace(".", "").replace(',', '.');
            } else {
                normalized = normalized.replace(",", "");
            }
        } else if (normalized.contains(",")) {
            normalized = normalized.replace(',', '.');
        }
        try {
            return new BigDecimal(normalized);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isNumericDataType(String dataType) {
        if (GenericValidator.isBlankOrNull(dataType)) {
            return false;
        }
        String normalized = dataType.trim().toUpperCase();
        return DATA_TYPE_NUMBER.equalsIgnoreCase(normalized) || "NUMBER".equals(normalized) || "DECIMAL".equals(normalized)
                || "DOUBLE".equals(normalized) || "FLOAT".equals(normalized) || "INTEGER".equals(normalized)
                || "LONG".equals(normalized) || "SHORT".equals(normalized) || "BIGDECIMAL".equals(normalized);
    }

    private boolean isSupportedImage(MultipartFile file) {
        String filename = StringUtils.defaultString(file.getOriginalFilename()).toLowerCase();
        boolean extensionAllowed = filename.endsWith(".png") || filename.endsWith(".jpg") || filename.endsWith(".jpeg")
                || filename.endsWith(".gif");
        if (!extensionAllowed) {
            return false;
        }
        try {
            BufferedImage image = ImageIO.read(file.getInputStream());
            return image != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildImageDescription(String requestedName, String originalFilename) {
        String base = StringUtils.defaultIfBlank(StringUtils.trimToNull(requestedName),
                StringUtils.defaultIfBlank(StringUtils.trimToNull(originalFilename), "validation-template-image"));
        String normalized = base.replaceAll("[^a-zA-Z0-9._\\- ]", "_").trim();
        if (normalized.isEmpty()) {
            normalized = "validation-template-image";
        }
        return "validation-template-override:" + normalized + ":" + System.currentTimeMillis();
    }

    private void addAdditionalFieldSourceOptions(List<Map<String, Object>> options, List<String> testIds) {
        if (testIds == null || testIds.isEmpty()) {
            return;
        }
        Map<String, List<TestAdditionalFieldPayload>> byTest = testAdditionalFieldService
                .getActiveFieldsForTests(testIds);
        if (byTest == null || byTest.isEmpty()) {
            return;
        }
        Set<String> addedKeys = new HashSet<>();
        byTest.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String testId = entry.getKey();
            List<TestAdditionalFieldPayload> fields = entry.getValue() == null ? Collections.emptyList()
                    : entry.getValue();
            fields.stream().filter(Objects::nonNull).sorted(Comparator
                    .comparing(TestAdditionalFieldPayload::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                    .forEach(field -> {
                        String fieldKey = sanitize(field.getFieldKey());
                        if (GenericValidator.isBlankOrNull(fieldKey)) {
                            return;
                        }
                        String sourceId = "additional." + fieldKey;
                        if (!addedKeys.add(sourceId)) {
                            return;
                        }
                        String displayName = sanitize(field.getDisplayName());
                        if (GenericValidator.isBlankOrNull(displayName)) {
                            displayName = fieldKey;
                        }
                        addSourceOption(options, sourceId,
                                "Additional: " + displayName + " (" + fieldKey + ") [Test " + testId + "]",
                                resolveFieldDataType(field.getFieldType()));
                    });
        });
    }

    private void addOrderAdditionalFieldSourceOptions(List<Map<String, Object>> options) {
        List<OrderAdditionalFieldPayload> fields = orderAdditionalFieldService.getFields(false);
        if (fields == null || fields.isEmpty()) {
            return;
        }
        fields.stream().filter(Objects::nonNull).sorted(Comparator.comparing(OrderAdditionalFieldPayload::getSortOrder,
                Comparator.nullsLast(Integer::compareTo))).forEach(field -> {
                    String fieldKey = sanitize(field.getFieldKey());
                    if (GenericValidator.isBlankOrNull(fieldKey)) {
                        return;
                    }
                    String displayName = sanitize(field.getDisplayName());
                    if (GenericValidator.isBlankOrNull(displayName)) {
                        displayName = fieldKey;
                    }
                    addSourceOption(options, "orderAdditional." + fieldKey,
                            "Order Additional: " + displayName + " (" + fieldKey + ")",
                            resolveFieldDataType(field.getFieldType()));
                });
    }

    private void addSampleAdditionalFieldSourceOptions(List<Map<String, Object>> options, List<String> testIds) {
        Set<String> addedKeys = new HashSet<>();

        // New sample type additional fields (configured in "Manage Sample Type
        // Additional Fields")
        if (testIds != null && !testIds.isEmpty()) {
            Set<String> sampleTypeIds = new LinkedHashSet<>();
            Map<String, String> sampleTypeIdToName = new HashMap<>();
            for (String testId : testIds) {
                if (GenericValidator.isBlankOrNull(testId)) {
                    continue;
                }
                List<TypeOfSample> sampleTypes = typeOfSampleService.getTypeOfSampleForTest(testId);
                if (sampleTypes == null) {
                    continue;
                }
                for (TypeOfSample sampleType : sampleTypes) {
                    if (sampleType == null || GenericValidator.isBlankOrNull(sampleType.getId())) {
                        continue;
                    }
                    sampleTypeIds.add(sampleType.getId());
                    sampleTypeIdToName.put(sampleType.getId(),
                            StringUtils.defaultIfBlank(sampleType.getLocalizedName(), sampleType.getDescription()));
                }
            }

            if (!sampleTypeIds.isEmpty()) {
                Map<String, List<SampleTypeAdditionalFieldPayload>> fieldsBySampleType = sampleTypeAdditionalFieldService
                        .getActiveFieldsForSampleTypes(new ArrayList<>(sampleTypeIds));
                if (fieldsBySampleType != null && !fieldsBySampleType.isEmpty()) {
                    fieldsBySampleType.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                        String sampleTypeId = entry.getKey();
                        String sampleTypeName = StringUtils.defaultIfBlank(sampleTypeIdToName.get(sampleTypeId),
                                sampleTypeId);
                        List<SampleTypeAdditionalFieldPayload> fields = entry.getValue() == null
                                ? Collections.emptyList()
                                : entry.getValue();
                        fields.stream().filter(Objects::nonNull)
                                .sorted(Comparator.comparing(SampleTypeAdditionalFieldPayload::getSortOrder,
                                        Comparator.nullsLast(Integer::compareTo)))
                                .forEach(field -> {
                                    String fieldKey = sanitize(field.getFieldKey());
                                    if (GenericValidator.isBlankOrNull(fieldKey)) {
                                        return;
                                    }
                                    String sourceId = "sampleAdditional." + fieldKey;
                                    if (!addedKeys.add(sourceId)) {
                                        return;
                                    }
                                    String displayName = sanitize(field.getDisplayName());
                                    if (GenericValidator.isBlankOrNull(displayName)) {
                                        displayName = fieldKey;
                                    }
                                    addSourceOption(options, sourceId, "Sample Additional: " + displayName + " ("
                                            + fieldKey + ") [" + sampleTypeName + "]",
                                            resolveFieldDataType(field.getFieldType()));
                                });
                    });
                }
            }
        }

        // Legacy sample additional fields (kept for backward compatibility)
        for (AdditionalFieldName field : AdditionalFieldName.values()) {
            String key = field.name();
            String sourceId = "sampleAdditional." + key;
            if (!addedKeys.add(sourceId)) {
                continue;
            }
            addSourceOption(options, sourceId, "Sample Additional (Legacy): " + toTitleCase(key) + " (" + key + ")");
        }
    }

    private String toTitleCase(String raw) {
        if (GenericValidator.isBlankOrNull(raw)) {
            return "";
        }
        String[] parts = raw.toLowerCase().split("_");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            normalized.add(part.substring(0, 1).toUpperCase() + part.substring(1));
        }
        return String.join(" ", normalized);
    }

    private List<Map<String, Object>> buildDefaultSectionFields() {
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(defaultField("patientName", "PATIENT", "APELLIDOS Y NOMBRES", "patientName", 10));
        fields.add(defaultField("dni", "PATIENT", "DNI", "dni", 20));
        fields.add(defaultField("hc", "PATIENT", "HC", "subjectNumber", 30));
        fields.add(defaultField("cug", "PATIENT", "CUG", "sampleCug", 40));
        fields.add(defaultField("gender", "PATIENT", "GENERO", "gender", 50));
        fields.add(defaultField("birthDate", "PATIENT", "FECHA DE NACIMIENTO", "dob", 60));
        fields.add(defaultField("contact", "PATIENT", "CONTACTO", "patientSiteNumber", 70));
        fields.add(defaultField("requestingPhysician", "REQUESTING_PHYSICIAN", "MEDICO SOLICITANTE", "prescriber", 10));
        fields.add(defaultField("requesterCmp", "REQUESTING_PHYSICIAN", "CMP", "requesterCmp", 20));
        fields.add(defaultField("requesterRne", "REQUESTING_PHYSICIAN", "RNE", "requesterRne", 30));
        fields.add(
                defaultField("requesterSpecialty", "REQUESTING_PHYSICIAN", "ESPECIALIDAD", "requesterSpecialty", 40));
        fields.add(defaultField("referenceCenter", "REQUESTING_PHYSICIAN", "CENTRO DE REFERENCIA", "siteInfo", 50));
        fields.add(defaultField("collectionDate", "SAMPLE", "Fecha de toma de muestra:", "collectionDate", 10));
        fields.add(defaultField("sampleStatus", "SAMPLE", "Estado de la muestra:", "sampleStatus", 20));
        fields.add(defaultField("sampleSource", "SAMPLE", "Fuente:", "sampleType", 30));
        fields.add(defaultField("allele1", "MOLECULAR_RESULT", "ALELO 1:", "orderAdditional.<field_key>", 10));
        fields.add(defaultField("allele2", "MOLECULAR_RESULT", "ALELO 2:", "orderAdditional.<field_key>", 20));
        fields.add(defaultField("resultDate", "MOLECULAR_RESULT", "Fecha de emision de resultados:", "orderFinishDate",
                30));
        return fields;
    }

    private Map<String, Object> defaultField(String key, String section, String label, String source, int order) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("section", section);
        row.put("label", label);
        row.put("source", source);
        row.put("order", order);
        row.put("enabled", true);
        return row;
    }

    private String resolveFieldDataType(String fieldType) {
        return isNumericDataType(fieldType) ? DATA_TYPE_NUMBER : DATA_TYPE_TEXT;
    }

    private void addSourceOption(List<Map<String, Object>> options, String id, String label) {
        addSourceOption(options, id, label, DATA_TYPE_TEXT);
    }

    private void addSourceOption(List<Map<String, Object>> options, String id, String label, String dataType) {
        Map<String, Object> option = new LinkedHashMap<>();
        option.put("id", id);
        option.put("value", label);
        option.put("dataType", StringUtils.defaultIfBlank(sanitize(dataType), DATA_TYPE_TEXT));
        options.add(option);
    }
}
