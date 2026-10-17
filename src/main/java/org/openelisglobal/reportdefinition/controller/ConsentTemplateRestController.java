package org.openelisglobal.reportdefinition.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.openelisglobal.reports.service.ConsentTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<?> downloadTemplate() {
        ConsentTemplateConfigForm config = consentTemplateService.getConfig();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(config.getTemplateFilename(), StandardCharsets.UTF_8).build().toString())
                .body(config.getTemplateContent().getBytes(StandardCharsets.UTF_8));
    }
}
