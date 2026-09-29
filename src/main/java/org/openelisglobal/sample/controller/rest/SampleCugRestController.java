package org.openelisglobal.sample.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.bean.SampleCugPreviewResponse;
import org.openelisglobal.sample.service.SampleCugService;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/rest/sample-cug")
public class SampleCugRestController extends BaseRestController {

    @Autowired
    private SampleCugService sampleCugService;

    @Autowired
    private OrderAuthorizationService orderAuthorizationService;

    @PostMapping(value = "/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> preview(@RequestBody SampleCugPreviewRequest request, HttpServletRequest httpRequest) {
        if (!orderAuthorizationService.hasPermission(getSysUserId(httpRequest), "create")) {
            throw new AccessDeniedException("The user does not have permission to create orders");
        }
        try {
            SampleCugPreviewResponse response = sampleCugService.reserveCugCode(request, getSysUserId(httpRequest));
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
