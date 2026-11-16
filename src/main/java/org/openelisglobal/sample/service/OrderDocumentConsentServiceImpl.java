package org.openelisglobal.sample.service;

import static org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldServiceImpl.fixedConditionValues;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.openelisglobal.reports.service.ConsentTemplateService;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.exception.ConsentDownloadRequiredException;
import org.openelisglobal.sample.exception.OrderDocumentConsentRequiredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderDocumentConsentServiceImpl implements OrderDocumentConsentService {
    @Autowired
    private OrderAdditionalFieldService orderFields;
    @Autowired
    private ConsentTemplateService templates;
    @Autowired
    private OrderConsentDownloadService receipts;
    @Autowired
    private SampleService sampleService;
    @Autowired
    private OrderAuthorizationService authorization;

    @Override
    @Transactional(readOnly = true)
    public void validateDownload(String userId, ConsentPreviewForm form) {
        String action = StringUtils.isBlank(form.getOrderAccessionNumber()) ? "create" : "update";
        if (!authorization.hasPermission(userId, action))
            throw new AccessDeniedException("Order permission required");
        String sampleId = resolveSampleId(form.getOrderAccessionNumber(), userId);
        Map<String, String> visible = orderFields.getVisibleConsentTemplates(sampleId,
                form.getOrderAdditionalFieldValues(), form.getOrderFixedFieldValues(), false);
        if (!StringUtils.equals(visible.get(form.getOrderFieldKey()), form.getTemplateId()))
            throw new IllegalArgumentException("order.document.error.unavailable");
        requireActiveTemplate(form.getTemplateId());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderConsentDownloadService.Permit claimRequired(HttpServletRequest request, String userId, String patientId,
            String accessionNumber, SampleOrderItem items, Boolean rememberRequester) {
        Map<String, String> required = orderFields.getVisibleConsentTemplates(resolveSampleId(accessionNumber, null),
                items == null ? null : items.getAdditionalFieldValues(), fixedConditionValues(items, rememberRequester),
                true);
        List<OrderConsentDownloadService.Permit> permits = new ArrayList<>();
        try {
            for (var entry : required.entrySet()) {
                String fieldKey = entry.getKey();
                try {
                    requireActiveTemplate(entry.getValue());
                    String token = items == null || items.getAdditionalFieldConsentTokens() == null ? null
                            : items.getAdditionalFieldConsentTokens().get(fieldKey);
                    permits.add(receipts.claimFieldDownload(request, userId, token, patientId, accessionNumber,
                            fieldKey, entry.getValue()));
                } catch (ConsentDownloadRequiredException | IllegalArgumentException e) {
                    throw new OrderDocumentConsentRequiredException(fieldKey);
                }
            }
        } catch (RuntimeException e) {
            permits.forEach(OrderConsentDownloadService.Permit::close);
            throw e;
        }
        return new OrderConsentDownloadService.Permit() {
            public void complete() {
                permits.forEach(OrderConsentDownloadService.Permit::complete);
            }

            public void close() {
                permits.forEach(OrderConsentDownloadService.Permit::close);
            }
        };
    }

    private void requireActiveTemplate(String id) {
        if (Boolean.FALSE.equals(templates.getConfig(id).getIsActive()))
            throw new IllegalArgumentException("consent.template.error.inactive");
    }

    private String resolveSampleId(String accessionNumber, String userId) {
        if (StringUtils.isBlank(accessionNumber))
            return null;
        var sample = sampleService.getSampleByAccessionNumber(accessionNumber);
        if (sample == null)
            throw new IllegalArgumentException("order.document.error.orderNotFound");
        // Download requests need their own scope check. Save controllers already
        // authorize the affected tests; keep their existing update policy intact.
        if (StringUtils.isNotBlank(userId)) {
            List<String> testIds = sampleService.getAnalysis(sample).stream()
                    .map(analysis -> analysis.getTest().getId()).distinct().toList();
            if (!authorization.canAccessAllTests(userId, testIds, "update"))
                throw new AccessDeniedException("Order laboratory scope required");
        }
        return sample.getId();
    }
}
