package org.openelisglobal.orderadditionalfield.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.dao.*;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderDocumentFieldsTest {
    private OrderAdditionalFieldServiceImpl service;
    private OrderAdditionalFieldDefinitionDAO definitions;
    private SampleOrderAdditionalFieldFileDAO files;
    private OrderAdditionalFieldDefinition document;

    @Before
    public void setup() {
        service = new OrderAdditionalFieldServiceImpl();
        definitions = mock(OrderAdditionalFieldDefinitionDAO.class);
        files = mock(SampleOrderAdditionalFieldFileDAO.class);
        ReflectionTestUtils.setField(service, "definitionDAO", definitions);
        ReflectionTestUtils.setField(service, "optionDAO", mock(OrderAdditionalFieldOptionDAO.class));
        ReflectionTestUtils.setField(service, "valueDAO", mock(SampleOrderAdditionalFieldValueDAO.class));
        ReflectionTestUtils.setField(service, "fileDAO", files);
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", mock(UserFieldOptionResolver.class));
        ReflectionTestUtils.setField(service, "reportDefinitionService",
                mock(org.openelisglobal.reportdefinition.service.ReportDefinitionService.class));
        document = new OrderAdditionalFieldDefinition();
        document.setId(1);
        document.setFieldKey("bank");
        document.setDisplayName("Bank");
        document.setFieldType("DOCUMENT");
        document.setRequired(true);
        document.setActive(true);
        document.setMetadataJson("{\"document\":{\"mode\":\"CONSENT\",\"templateId\":\"CONSENT-bank\"},"
                + "\"rules\":{\"visibleWhen\":[{\"fieldKey\":\"checked\",\"operator\":\"istrue\"}]}}");
        when(definitions.findAll(true)).thenReturn(List.of(document));
        when(definitions.get(1)).thenReturn(Optional.of(document));
    }

    @Test
    public void onlyVisibleRequiredConsentFieldsNeedDownloads() {
        assertEquals(Map.of(), service.getVisibleConsentTemplates(null, Map.of("checked", "false"), Map.of(), true));
        assertEquals(Map.of("bank", "CONSENT-bank"),
                service.getVisibleConsentTemplates(null, Map.of("checked", "true"), Map.of(), true));
        document.setRequired(false);
        assertEquals(Map.of(), service.getVisibleConsentTemplates(null, Map.of("checked", "true"), Map.of(), true));
        assertEquals(Map.of("bank", "CONSENT-bank"),
                service.getVisibleConsentTemplates(null, Map.of("checked", "true"), Map.of(), false));
    }

    @Test
    public void conditionalRequiredRulesAndFixedFieldsUseTheExistingRuleEngine() {
        document.setRequired(false);
        document.setMetadataJson("{\"document\":{\"mode\":\"CONSENT\",\"templateId\":\"CONSENT-bank\"},"
                + "\"rules\":{\"requiredWhen\":[{\"fieldKey\":\"priority\",\"operator\":\"equals\",\"value\":\"URGENT\"}]}}");
        assertEquals(Map.of(), service.getVisibleConsentTemplates(null, Map.of(), Map.of("priority", "ROUTINE"), true));
        assertEquals(Map.of("bank", "CONSENT-bank"),
                service.getVisibleConsentTemplates(null, Map.of(), Map.of("priority", "URGENT"), true));
    }

    @Test
    public void legacyDocumentsRemainUploadsAndCannotChangeModeWhenFilesExist() {
        document.setMetadataJson("{\"document\":{\"accept\":[\"application/pdf\"]}}");
        assertFalse(OrderDocumentConfig.parse(document.getMetadataJson()).isConsent());
        assertEquals(Map.of(), service.getVisibleConsentTemplates(null, Map.of(), Map.of(), true));
        when(files.countByFieldDefinitionId(1)).thenReturn(1L);
        var changed = new OrderAdditionalFieldPayload();
        changed.setMetadataJson("{\"document\":{\"mode\":\"CONSENT\",\"templateId\":\"CONSENT-bank\"}}");
        assertThrows(IllegalStateException.class, () -> service.updateField(1, changed, "42"));
        verify(definitions, never()).update(any());
    }

    @Test
    public void generatedConsentFieldsRejectFileUploadsAndDoNotRequireAnAttachment() {
        when(definitions.findByFieldKey("bank")).thenReturn(Optional.of(document));
        assertThrows(IllegalArgumentException.class, () -> service.prepareDocumentUpload("bank", "signed.pdf", "application/pdf", 4, new byte[] { 1, 2, 3, 4 }));
        var field = new OrderAdditionalFieldPayload();
        field.setId(1); field.setFieldKey("bank"); field.setFieldType("DOCUMENT");
        field.setRequired(true); field.setMetadataJson(document.getMetadataJson());
        service.validateAndPersistSampleValues("100", Map.of(), Map.of(), "42", List.of(field), Map.of("checked", "true"));
        assertThrows(IllegalArgumentException.class, () -> service.validateAndPersistSampleValues("100", Map.of(),
                Map.of("bank", new org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFilePayload()),
                "42", List.of(field), Map.of("checked", "true")));
        verify(files, never()).insert(any());
    }

    @Test
    public void newConsentFieldsRejectUnknownTemplateReferences() {
        var field = new OrderAdditionalFieldPayload();
        field.setFieldKey("bank");
        field.setDisplayName("Bank");
        field.setFieldType("DOCUMENT");
        field.setMetadataJson(document.getMetadataJson());
        assertThrows(IllegalArgumentException.class, () -> service.createField(field, "42"));
        verify(definitions, never()).insert(any());
    }

    @Test
    public void consentConfigurationRejectsMissingTemplatesAndUnknownModes() {
        assertThrows(IllegalArgumentException.class,
                () -> OrderDocumentConfig.parse("{\"document\":{\"mode\":\"CONSENT\"}}"));
        assertThrows(IllegalArgumentException.class,
                () -> OrderDocumentConfig.parse("{\"document\":{\"mode\":\"OTHER\"}}"));
    }
}
