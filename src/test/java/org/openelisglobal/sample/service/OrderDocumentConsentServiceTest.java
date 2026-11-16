package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.openelisglobal.reports.service.ConsentTemplateService;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.exception.OrderDocumentConsentRequiredException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderDocumentConsentServiceTest {
    private OrderDocumentConsentService service;
    private OrderConsentDownloadServiceImpl receipts;
    private OrderAdditionalFieldService fields;
    private ConsentTemplateService templates;
    private OrderAuthorizationService authorization;
    private MockHttpServletRequest request;
    private SampleOrderItem items;

    @Before
    public void setup() {
        service = new OrderDocumentConsentServiceImpl();
        receipts = new OrderConsentDownloadServiceImpl();
        fields = mock(OrderAdditionalFieldService.class);
        templates = mock(ConsentTemplateService.class);
        authorization = mock(OrderAuthorizationService.class);
        ReflectionTestUtils.setField(service, "orderFields", fields);
        ReflectionTestUtils.setField(service, "templates", templates);
        ReflectionTestUtils.setField(service, "receipts", receipts);
        ReflectionTestUtils.setField(service, "authorization", authorization);
        ReflectionTestUtils.setField(service, "sampleService", mock(SampleService.class));
        request = new MockHttpServletRequest();
        items = new SampleOrderItem();
        items.setAdditionalFieldValues(Map.of("checked", "true"));
        items.setAdditionalFieldConsentTokens(new LinkedHashMap<>());
        var active = new ConsentTemplateConfigForm();
        active.setIsActive(true);
        when(templates.getConfig(anyString())).thenReturn(active);
        when(authorization.hasPermission("42", "create")).thenReturn(true);
    }

    @Test
    public void allVisibleRequiredFieldsNeedTheirOwnReceiptAndFailuresReleasePriorClaims() {
        var required = new LinkedHashMap<String, String>();
        required.put("bank", "CONSENT-bank");
        required.put("research", "CONSENT-research");
        when(fields.getVisibleConsentTemplates(isNull(), any(), any(), eq(true))).thenReturn(required);
        String bank = receipts.recordFieldDownload(request, "42", "10", null, "bank", "CONSENT-bank");
        items.getAdditionalFieldConsentTokens().put("bank", bank);
        var missing = assertThrows(OrderDocumentConsentRequiredException.class,
                () -> service.claimRequired(request, "42", "10", null, items, null));
        assertEquals("research", missing.getFieldKey());
        String research = receipts.recordFieldDownload(request, "42", "10", null, "research", "CONSENT-research");
        items.getAdditionalFieldConsentTokens().put("research", research);
        // An unsuccessful order save releases both receipts for a retry.
        service.claimRequired(request, "42", "10", null, items, null).close();
        try (var permit = service.claimRequired(request, "42", "10", null, items, null)) {
            permit.complete();
        }
        assertThrows(OrderDocumentConsentRequiredException.class,
                () -> service.claimRequired(request, "42", "10", null, items, null));
    }

    @Test
    public void noRequiredFieldsDoNotAddAnOrderDownloadRequirement() {
        when(fields.getVisibleConsentTemplates(isNull(), any(), any(), eq(true))).thenReturn(Map.of());
        try (var permit = service.claimRequired(request, "42", "10", null, items, null)) { permit.complete(); }
        assertNull(request.getSession(false));
        verifyZeroInteractions(templates);
    }

    @Test
    public void downloadChecksPermissionVisibilityAndTheConfiguredTemplate() {
        when(fields.getVisibleConsentTemplates(isNull(), any(), any(), eq(false)))
                .thenReturn(Map.of("bank", "CONSENT-bank"));
        var form = new ConsentPreviewForm();
        form.setOrderPatientId("10");
        form.setOrderFieldKey("bank");
        form.setTemplateId("CONSENT-other");
        form.setOrderAdditionalFieldValues(Map.of("checked", "true"));
        form.setOrderFixedFieldValues(Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.validateDownload("42", form));
        form.setTemplateId("CONSENT-bank");
        service.validateDownload("42", form);
        when(authorization.hasPermission("42", "create")).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.validateDownload("42", form));
    }

    @Test
    public void receiptChecksDoNotChangeTheExistingOrderUpdateAuthorizationPolicy() {
        var samples = mock(SampleService.class);
        var sample = new org.openelisglobal.sample.valueholder.Sample();
        sample.setId("5");
        when(samples.getSampleByAccessionNumber("LAB2")).thenReturn(sample);
        ReflectionTestUtils.setField(service, "sampleService", samples);
        when(fields.getVisibleConsentTemplates(eq("5"), any(), any(), eq(true))).thenReturn(Map.of());
        try (var permit = service.claimRequired(request, "42", null, "LAB2", items, null)) {
            permit.complete();
        }
        verify(authorization, never()).canAccessAllTests(any(), any(), any());
        verify(samples, never()).getAnalysis(any());
    }

    @Test
    public void anInactiveTemplateCannotSatisfyARequiredField() {
        when(fields.getVisibleConsentTemplates(isNull(), any(), any(), eq(true))).thenReturn(Map.of("bank", "CONSENT-bank"));
        var inactive = new ConsentTemplateConfigForm();
        inactive.setIsActive(false);
        when(templates.getConfig("CONSENT-bank")).thenReturn(inactive);
        assertThrows(OrderDocumentConsentRequiredException.class,
                () -> service.claimRequired(request, "42", "10", null, items, null));
    }
}
