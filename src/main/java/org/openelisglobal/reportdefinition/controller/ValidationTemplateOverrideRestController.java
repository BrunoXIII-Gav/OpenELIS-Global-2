package org.openelisglobal.reportdefinition.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.awt.image.BufferedImage;
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
import org.springframework.http.HttpStatus;
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
            List<IdValuePair> options = VALIDATION_REPORT_CANDIDATES.stream()
                    .filter(report -> ReportImplementationFactory.getReportCreator(report) != null)
                    .map(report -> new IdValuePair(report, report)).collect(Collectors.toList());
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
            return ResponseEntity.ok(payload.toMap());
        } catch (Exception e) {
            logger.error("Error retrieving validation template field options", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving validation template field options");
        }
    }

    @PostMapping
    public ResponseEntity<?> createOverride(HttpServletRequest request,
            @RequestBody ValidationTemplateOverrideForm form) {
        return saveOverride(request, null, form);
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
            String reportName = sanitize(form.getReport());
            List<String> testIds = sanitizeList(form.getTestIds());
            List<String> testCodes = sanitizeList(form.getTestCodes());

            if (GenericValidator.isBlankOrNull(reportName)) {
                return ResponseEntity.badRequest().body("Field 'report' is required");
            }
            if (testIds.isEmpty() && testCodes.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("At least one test identifier is required (testIds or testCodes)");
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
            entity.setName(resolveName(form, reportName, testIds, testCodes));
            entity.setDescription(sanitize(form.getDescription()));
            entity.setDefinitionJson(buildDefinitionJson(reportName, testIds, testCodes, form.getConfig()));
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
            List<String> testCodes) {
        String requestedName = sanitize(form.getName());
        if (!GenericValidator.isBlankOrNull(requestedName)) {
            return requestedName;
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

    private List<Map<String, String>> buildSourceOptions(List<String> testIds) {
        List<Map<String, String>> options = new ArrayList<>();
        addSourceOption(options, "patientName", "Patient Name");
        addSourceOption(options, "nationalId", "National ID");
        addSourceOption(options, "subjectNumber", "HC / Subject Number");
        addSourceOption(options, "sampleCug", "Sample CUG (fixed)");
        addSourceOption(options, "accessionNumber", "CUG / Accession Number");
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
        addSourceOption(options, "collectionDateTime", "Collection Date/Time");
        addSourceOption(options, "sampleType", "Sample Source");
        addSourceOption(options, "orderFinishDate", "Order Finish Date");
        addSourceOption(options, "testDate", "Test Date");
        addSourceOption(options, "analysisResult1", "Analysis Result #1");
        addSourceOption(options, "analysisResult2", "Analysis Result #2");
        addAdditionalFieldSourceOptions(options, testIds);
        addOrderAdditionalFieldSourceOptions(options);
        addSampleAdditionalFieldSourceOptions(options, testIds);
        addSourceOption(options, "additional.<field_key>", "Additional Field (use key)");
        addSourceOption(options, "orderAdditional.<field_key>", "Order Additional Field (use key)");
        addSourceOption(options, "sampleAdditional.<field_key>", "Sample Additional Field (use key)");
        return options;
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

    private void addAdditionalFieldSourceOptions(List<Map<String, String>> options, List<String> testIds) {
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
                                "Additional: " + displayName + " (" + fieldKey + ") [Test " + testId + "]");
                    });
        });
    }

    private void addOrderAdditionalFieldSourceOptions(List<Map<String, String>> options) {
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
                            "Order Additional: " + displayName + " (" + fieldKey + ")");
                });
    }

    private void addSampleAdditionalFieldSourceOptions(List<Map<String, String>> options, List<String> testIds) {
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
                                            + fieldKey + ") [" + sampleTypeName + "]");
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
        fields.add(defaultField("dni", "PATIENT", "DNI", "nationalId", 20));
        fields.add(defaultField("hc", "PATIENT", "HC", "subjectNumber", 30));
        fields.add(defaultField("cug", "PATIENT", "CUG", "accessionNumber", 40));
        fields.add(defaultField("gender", "PATIENT", "GENERO", "gender", 50));
        fields.add(defaultField("birthDate", "PATIENT", "FECHA DE NACIMIENTO", "dob", 60));
        fields.add(defaultField("contact", "PATIENT", "CONTACTO", "patientSiteNumber", 70));
        fields.add(defaultField("requestingPhysician", "REQUESTING_PHYSICIAN", "MEDICO SOLICITANTE", "prescriber", 10));
        fields.add(defaultField("requesterCmp", "REQUESTING_PHYSICIAN", "CMP", "requesterCmp", 20));
        fields.add(defaultField("requesterRne", "REQUESTING_PHYSICIAN", "RNE", "requesterRne", 30));
        fields.add(
                defaultField("requesterSpecialty", "REQUESTING_PHYSICIAN", "ESPECIALIDAD", "requesterSpecialty", 40));
        fields.add(defaultField("referenceCenter", "REQUESTING_PHYSICIAN", "CENTRO DE REFERENCIA", "siteInfo", 50));
        fields.add(defaultField("collectionDate", "SAMPLE", "Fecha de toma de muestra:", "collectionDateTime", 10));
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

    private void addSourceOption(List<Map<String, String>> options, String id, String label) {
        Map<String, String> option = new LinkedHashMap<>();
        option.put("id", id);
        option.put("value", label);
        options.add(option);
    }
}
