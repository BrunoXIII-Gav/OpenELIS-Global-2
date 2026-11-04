package org.openelisglobal.sample.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.sample.service.OrderAccessionService;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/orders/laboratory-number")
public class OrderAccessionRestController extends BaseRestController {
    @Autowired
    private OrderAccessionService numbers;
    @Autowired
    private OrderAuthorizationService authorization;

    @GetMapping("/suggest")
    public Map<String, Object> suggest(@RequestParam(required = false) String programCode, HttpServletRequest request) {
        if (!authorization.hasPermission(getSysUserId(request), "create"))
            throw new AccessDeniedException("Cannot create orders");
        return Map.of("status", true, "body", numbers.preview(programCode));
    }
}
