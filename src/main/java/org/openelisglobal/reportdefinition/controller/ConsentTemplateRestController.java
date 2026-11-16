package org.openelisglobal.reportdefinition.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.openelisglobal.reports.service.ConsentTemplateService;
import org.openelisglobal.sample.service.OrderConsentDownloadService;
import org.openelisglobal.sample.service.OrderDocumentConsentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/rest/reports/consent-template")
public class ConsentTemplateRestController extends BaseRestController {

    private static final Logger logger = LoggerFactory.getLogger(ConsentTemplateRestController.class);

    @Autowired
    private ConsentTemplateService consentTemplateService;

    @Autowired
    private OrderConsentDownloadService orderConsentDownloadService;

    @Autowired
    private OrderDocumentConsentService orderDocumentConsentService;

    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
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

    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @PutMapping
    public ResponseEntity<?> saveConfig(HttpServletRequest request, @RequestBody ConsentTemplateConfigForm form) {
        try {
            if (form == null) {
                return ResponseEntity.badRequest().body("Request body is required");
            }
            ConsentTemplateConfigForm saved = consentTemplateService.saveConfig(getSysUserId(request), form);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error saving consent template config", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error saving consent template config");
        }
    }

    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @GetMapping("/templates")
    public ResponseEntity<?> getTemplates() {
        return ResponseEntity.ok(consentTemplateService.getTemplates(true));
    }

    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @GetMapping("/templates/{templateId}")
    public ResponseEntity<?> getTemplate(@PathVariable String templateId) {
        try {
            return ResponseEntity.ok(consentTemplateService.getConfig(templateId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @PostMapping("/templates")
    public ResponseEntity<?> createTemplate(HttpServletRequest request, @RequestBody ConsentTemplateConfigForm form) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(consentTemplateService.saveTemplate(getSysUserId(request), null, form));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @PutMapping("/templates/{templateId}")
    public ResponseEntity<?> updateTemplate(HttpServletRequest request, @PathVariable String templateId,
            @RequestBody ConsentTemplateConfigForm form) {
        try {
            return ResponseEntity.ok(consentTemplateService.saveTemplate(getSysUserId(request), templateId, form));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/options")
    public ResponseEntity<?> getOptions() {
        try {
            return ResponseEntity.ok(consentTemplateService.getOptions());
        } catch (Exception e) {
            logger.error("Error retrieving consent template options", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving consent template options");
        }
    }

    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> previewConsent(HttpServletRequest request, @RequestBody ConsentPreviewForm form) {
        try {
            byte[] pdf = consentTemplateService.generateConsentPdf(form, getSysUserId(request));
            if (pdf == null || pdf.length == 0) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error generating consent template PDF");
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.add("Content-Disposition", "inline; filename=consent-template.pdf");
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Error generating consent template PDF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error generating consent template PDF");
        }
    }

    @PostMapping(value = "/download", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<?> downloadConsent(HttpServletRequest request, @RequestBody ConsentPreviewForm form) {
        if (form == null || form.getSelectedTests() == null || form.getSelectedTests().isEmpty()
                || (StringUtils.isBlank(form.getOrderAccessionNumber())
                        && !StringUtils.isNumeric(form.getOrderPatientId()))) {
            return ResponseEntity.badRequest().build();
        }
        boolean fieldConsent = StringUtils.isNotBlank(form.getOrderFieldKey())
                || StringUtils.isNotBlank(form.getTemplateId());
        if (fieldConsent && (StringUtils.isBlank(form.getOrderFieldKey()) || StringUtils.isBlank(form.getTemplateId())))
            return ResponseEntity.badRequest().build();
        byte[] pdf;
        try {
            if (fieldConsent)
                orderDocumentConsentService.validateDownload(getSysUserId(request), form);
            pdf = consentTemplateService.generateConsentPdf(form, getSysUserId(request));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error generating consent template PDF", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        if (pdf == null || pdf.length == 0)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        String token = fieldConsent ? orderConsentDownloadService.recordFieldDownload(request, getSysUserId(request),
                form.getOrderPatientId(), form.getOrderAccessionNumber(), form.getOrderFieldKey(), form.getTemplateId())
                : orderConsentDownloadService.recordDownload(request, getSysUserId(request), form.getOrderPatientId(),
                        form.getOrderAccessionNumber());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(fieldConsent ? form.getOrderFieldKey() + ".pdf" : "consent-template.pdf",
                                        StandardCharsets.UTF_8)
                                .build().toString())
                .header("X-OpenELIS-Consent-Token", token).body(pdf);
    }

    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @PostMapping("/parse-template")
    public ResponseEntity<?> parseTemplate(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("error", "consent.template.error.file"));
            }
            return ResponseEntity.ok(consentTemplateService.parseTemplate(file.getOriginalFilename(),
                    new String(file.getBytes(), StandardCharsets.UTF_8)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error parsing consent template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "consent.template.error.invalid"));
        }
    }

    @PreAuthorize("@accessControl.hasPermission(T(org.openelisglobal.common.constants.SystemPermission).ADMINISTRATION)")
    @GetMapping(value = "/download-template", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> downloadTemplate(@RequestParam(required = false) String templateId) {
        ConsentTemplateConfigForm config = consentTemplateService.getConfig(templateId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(config.getTemplateFilename(), StandardCharsets.UTF_8).build().toString())
                .body(config.getTemplateContent().getBytes(StandardCharsets.UTF_8));
    }
}
