package org.openelisglobal.reports.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.openelisglobal.sample.service.SampleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ConsentDocumentImageServiceImpl implements ConsentDocumentImageService {
    @Autowired
    private OrderAdditionalFieldService orderFields;
    @Autowired
    private SampleService samples;
    @Autowired
    private OrderAuthorizationService authorization;

    @Override
    public Map<String, byte[]> resolveImages(ConsentPreviewForm form, String userId, Set<String> fieldKeys) {
        if (fieldKeys.isEmpty())
            return Map.of();
        if (form == null || StringUtils.isBlank(userId))
            throw new AccessDeniedException("Order permission required");
        String action = StringUtils.isBlank(form.getOrderAccessionNumber()) ? "create" : "update";
        if (!authorization.hasPermission(userId, action))
            throw new AccessDeniedException("Order permission required");
        String sampleId = null;
        if (StringUtils.isNotBlank(form.getOrderAccessionNumber())) {
            var sample = samples.getSampleByAccessionNumber(form.getOrderAccessionNumber());
            if (sample == null)
                throw new IllegalArgumentException("order.document.error.orderNotFound");
            var testIds = samples.getAnalysis(sample).stream().map(a -> a.getTest().getId()).distinct().toList();
            if (!authorization.canAccessAllTests(userId, testIds, "update"))
                throw new AccessDeniedException("Order laboratory scope required");
            sampleId = sample.getId();
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        orderFields.getReportImageFiles(sampleId, fieldKeys, form.getOrderDocumentFiles(),
                form.getOrderAdditionalFieldValues(), form.getOrderFixedFieldValues()).forEach((key, file) -> {
                    byte[] image = ConsentImageValidator.validatedImage(file.getContent());
                    if (image != null)
                        result.put(key, image);
                });
        return result;
    }
}
