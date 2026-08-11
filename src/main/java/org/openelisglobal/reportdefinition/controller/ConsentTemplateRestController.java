package org.openelisglobal.reportdefinition.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.openelisglobal.reports.service.ConsentTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/reports/consent-template")
public class ConsentTemplateRestController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(ConsentTemplateRestController.class);

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
    private static final String SOURCE_ORDER_ADDITIONAL_PREFIX = "orderAdditional.";

    private static final String LABEL_FIELD_PATIENT_NAME = "consent.template.field.patientName";
    private static final String LABEL_FIELD_PATIENT_DNI = "consent.template.field.patientDni";
    private static final String LABEL_FIELD_PROVIDER_NAME = "consent.template.field.providerName";
    private static final String LABEL_FIELD_PROVIDER_DNI = "consent.template.field.providerDni";
    private static final String LABEL_FIELD_DATE = "consent.template.field.date";
    private static final String LABEL_FIELD_TESTS = "consent.template.field.tests";

    private static final String LABEL_SOURCE_PATIENT_NAME = "consent.template.source.patientFullName";
    private static final String LABEL_SOURCE_PATIENT_NATIONAL_ID = "consent.template.source.patientNationalId";
    private static final String LABEL_SOURCE_PROVIDER_NAME = "consent.template.source.providerFullName";
    private static final String LABEL_SOURCE_PROVIDER_DNI = "consent.template.source.providerDni";
    private static final String LABEL_SOURCE_ORDER_DATE = "consent.template.source.orderDate";
    private static final String LABEL_SOURCE_TESTS_SELECTED = "consent.template.source.testsSelectedNames";
    private static final String LABEL_SOURCE_ORDER_ADDITIONAL = "consent.template.source.orderAdditional";

    @Autowired
    private ConsentTemplateService consentTemplateService;

    @Autowired
    private OrderAdditionalFieldService orderAdditionalFieldService;

    @GetMapping
    public ResponseEntity<?> getConfig() {
        try {
            ConsentTemplateConfigForm config = consentTemplateService.getConfig();
            return ResponseEntity.ok(config);
        } catch (Exception e) {
            logger.error("Error retrieving consent template config", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving consent template config");
        }
    }

    @PutMapping
    public ResponseEntity<?> saveConfig(HttpServletRequest request, @RequestBody ConsentTemplateConfigForm form) {
        try {
            if (form == null) {
                return ResponseEntity.badRequest().body("Request body is required");
            }
            ConsentTemplateConfigForm saved = consentTemplateService.saveConfig(getSysUserId(request), form);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            logger.error("Error saving consent template config", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving consent template config");
        }
    }

    @GetMapping("/options")
    public ResponseEntity<?> getOptions() {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("fields", buildFieldOptions());
            payload.put("sources", buildSourceOptions());
            return ResponseEntity.ok(payload);
        } catch (Exception e) {
            logger.error("Error retrieving consent template options", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving consent template options");
        }
    }

    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> previewConsent(@RequestBody ConsentPreviewForm form) {
        try {
            byte[] pdf = consentTemplateService.generateConsentPdf(form);
            if (pdf == null || pdf.length == 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error generating consent template PDF");
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add("Content-Disposition", "inline; filename=consent-template.pdf");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (Exception e) {
            logger.error("Error generating consent template PDF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating consent template PDF");
        }
    }

    private List<Map<String, String>> buildFieldOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        addOption(options, FIELD_PATIENT_NAME, LABEL_FIELD_PATIENT_NAME, null);
        addOption(options, FIELD_PATIENT_DNI, LABEL_FIELD_PATIENT_DNI, null);
        addOption(options, FIELD_PROVIDER_NAME, LABEL_FIELD_PROVIDER_NAME, null);
        addOption(options, FIELD_PROVIDER_DNI, LABEL_FIELD_PROVIDER_DNI, null);
        addOption(options, FIELD_DATE, LABEL_FIELD_DATE, null);
        addOption(options, FIELD_TESTS, LABEL_FIELD_TESTS, null);
        return options;
    }

    private List<Map<String, String>> buildSourceOptions() {
        List<Map<String, String>> options = new ArrayList<>();
        addOption(options, SOURCE_PATIENT_NAME, LABEL_SOURCE_PATIENT_NAME, null);
        addOption(options, SOURCE_PATIENT_NATIONAL_ID, LABEL_SOURCE_PATIENT_NATIONAL_ID, null);
        addOption(options, SOURCE_PROVIDER_NAME, LABEL_SOURCE_PROVIDER_NAME, null);
        addOption(options, SOURCE_PROVIDER_DNI, LABEL_SOURCE_PROVIDER_DNI, null);
        addOption(options, SOURCE_ORDER_DATE, LABEL_SOURCE_ORDER_DATE, null);
        addOption(options, SOURCE_TESTS_SELECTED, LABEL_SOURCE_TESTS_SELECTED, null);
        addOrderAdditionalFieldSourceOptions(options);
        return options;
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
                    String label = displayName + " (" + fieldKey + ")";
                    addOption(options, SOURCE_ORDER_ADDITIONAL_PREFIX + fieldKey, LABEL_SOURCE_ORDER_ADDITIONAL, label);
                });
    }

    private void addOption(List<Map<String, String>> options, String id, String labelKey, String label) {
        Map<String, String> option = new HashMap<>();
        option.put("id", id);
        option.put("labelKey", labelKey);
        if (!GenericValidator.isBlankOrNull(label)) {
            option.put("label", label);
        }
        options.add(option);
    }

    private String sanitize(String raw) {
        return raw == null ? null : raw.trim();
    }
}
