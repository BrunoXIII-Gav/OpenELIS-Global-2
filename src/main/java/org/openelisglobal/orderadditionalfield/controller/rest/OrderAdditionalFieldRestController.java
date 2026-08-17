package org.openelisglobal.orderadditionalfield.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFilePayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldOptionPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderFixedFieldConfigPayload;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/order-additional-fields")
public class OrderAdditionalFieldRestController extends BaseRestController {

    @Autowired
    private OrderAdditionalFieldService orderAdditionalFieldService;

    @GetMapping
    public List<OrderAdditionalFieldPayload> getFields(
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive,
            @RequestParam(name = "resolveUserOptions", defaultValue = "false") boolean resolveUserOptions) {
        return orderAdditionalFieldService.getFields(includeInactive, resolveUserOptions);
    }

    @PostMapping
    public ResponseEntity<?> createField(@RequestBody OrderAdditionalFieldPayload payload, HttpServletRequest request) {
        try {
            OrderAdditionalFieldPayload created = orderAdditionalFieldService.createField(payload,
                    getSysUserId(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PutMapping("/{fieldId}")
    public ResponseEntity<?> updateField(@PathVariable Integer fieldId,
            @RequestBody OrderAdditionalFieldPayload payload, HttpServletRequest request) {
        try {
            OrderAdditionalFieldPayload updated = orderAdditionalFieldService.updateField(fieldId, payload,
                    getSysUserId(request));
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @DeleteMapping("/{fieldId}")
    public ResponseEntity<?> deactivateField(@PathVariable Integer fieldId, HttpServletRequest request) {
        try {
            orderAdditionalFieldService.deactivateField(fieldId, getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PostMapping("/{fieldId}/options")
    public ResponseEntity<?> createOption(@PathVariable Integer fieldId,
            @RequestBody OrderAdditionalFieldOptionPayload payload, HttpServletRequest request) {
        try {
            OrderAdditionalFieldOptionPayload created = orderAdditionalFieldService.createOption(fieldId, payload,
                    getSysUserId(request));
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @PutMapping("/options/{optionId}")
    public ResponseEntity<?> updateOption(@PathVariable Integer optionId,
            @RequestBody OrderAdditionalFieldOptionPayload payload, HttpServletRequest request) {
        try {
            OrderAdditionalFieldOptionPayload updated = orderAdditionalFieldService.updateOption(optionId, payload,
                    getSysUserId(request));
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @DeleteMapping("/options/{optionId}")
    public ResponseEntity<?> deactivateOption(@PathVariable Integer optionId, HttpServletRequest request) {
        try {
            orderAdditionalFieldService.deactivateOption(optionId, getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/fixed")
    public List<OrderFixedFieldConfigPayload> getFixedFieldConfigs() {
        return orderAdditionalFieldService.getFixedFieldConfigs();
    }

    @PutMapping("/fixed")
    public ResponseEntity<?> upsertFixedFieldConfigs(
            @RequestBody(required = false) List<OrderFixedFieldConfigPayload> payloads, HttpServletRequest request) {
        try {
            orderAdditionalFieldService.upsertFixedFieldConfigs(payloads == null ? Collections.emptyList() : payloads,
                    getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/values/{sampleId}")
    public ResponseEntity<?> getSampleValues(@PathVariable String sampleId,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        try {
            List<OrderAdditionalFieldPayload> definitions = orderAdditionalFieldService.getFields(includeInactive, true);
            Map<String, String> values = orderAdditionalFieldService.getSampleValues(sampleId, definitions);
            return ResponseEntity.ok(values);
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    @GetMapping("/files/{sampleId}/{fieldKey}")
    public ResponseEntity<?> downloadSampleFile(@PathVariable String sampleId, @PathVariable String fieldKey,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        try {
            Optional<OrderAdditionalFieldFilePayload> file = orderAdditionalFieldService.getSampleFile(sampleId,
                    fieldKey);
            if (file.isEmpty() || file.get().getContent() == null || file.get().getContent().length == 0) {
                return ResponseEntity.notFound().build();
            }

            OrderAdditionalFieldFilePayload payload = file.get();
            String contentType = StringUtils.defaultIfBlank(payload.getFileType(),
                    MediaType.APPLICATION_OCTET_STREAM_VALUE);
            String fileName = StringUtils.defaultIfBlank(payload.getFileName(), fieldKey + ".bin")
                    .replaceAll("[\\r\\n\"]", "_");

            String dispositionType = download ? "attachment" : "inline";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, dispositionType + "; filename=\"" + fileName + "\"")
                    .body(payload.getContent());
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        }
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
