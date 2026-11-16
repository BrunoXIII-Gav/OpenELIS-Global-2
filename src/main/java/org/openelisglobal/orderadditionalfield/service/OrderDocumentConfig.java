package org.openelisglobal.orderadditionalfield.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

public record OrderDocumentConfig(String mode, String templateId, String buttonLabel) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    public static final String UPLOAD = "UPLOAD";
    public static final String CONSENT = "CONSENT";

    public boolean isConsent() {
        return CONSENT.equals(mode);
    }

    public static boolean canSupplyImage(String metadata) {
        if (parse(metadata).isConsent())
            return false;
        try {
            JsonNode accepted = MAPPER.readTree(StringUtils.defaultIfBlank(metadata, "{}")).path("document")
                    .path("accept");
            for (JsonNode mime : accepted) {
                if ("image/png".equalsIgnoreCase(mime.asText()) || "image/jpeg".equalsIgnoreCase(mime.asText()))
                    return true;
            }
            return false;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid metadataJson", e);
        }
    }

    public static OrderDocumentConfig parse(String metadata) {
        try {
            JsonNode document = MAPPER.readTree(StringUtils.defaultIfBlank(metadata, "{}")).path("document");
            String mode = document.path("mode").asText(UPLOAD);
            if (!UPLOAD.equals(mode) && !CONSENT.equals(mode))
                throw new IllegalArgumentException("order.document.error.mode");
            String template = StringUtils.trimToNull(document.path("templateId").asText(null));
            if (CONSENT.equals(mode) && template == null)
                throw new IllegalArgumentException("order.document.error.templateRequired");
            return new OrderDocumentConfig(mode, template, document.path("buttonLabel").asText(""));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid metadataJson", e);
        }
    }
}
