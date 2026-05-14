package org.openelisglobal.sampleadditionalfield.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.sampleadditionalfield.bean.SampleFixedFieldConfigPayload;
import org.openelisglobal.sampleadditionalfield.service.SampleAdditionalFieldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/sample-additional-fields")
public class SampleAdditionalFieldRestController extends BaseRestController {

    @Autowired
    private SampleAdditionalFieldService sampleAdditionalFieldService;

    @GetMapping("/fixed")
    public List<SampleFixedFieldConfigPayload> getFixedFieldConfigs() {
        return sampleAdditionalFieldService.getFixedFieldConfigs();
    }

    @PutMapping("/fixed")
    public ResponseEntity<?> upsertFixedFieldConfigs(
            @RequestBody(required = false) List<SampleFixedFieldConfigPayload> payloads, HttpServletRequest request) {
        try {
            sampleAdditionalFieldService.upsertFixedFieldConfigs(payloads == null ? Collections.emptyList() : payloads,
                    getSysUserId(request));
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
