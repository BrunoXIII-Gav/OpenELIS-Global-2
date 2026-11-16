package org.openelisglobal.reports.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;
import org.openelisglobal.reportdefinition.service.ReportDefinitionService;
import org.openelisglobal.reportdefinition.valueholder.ReportDefinition;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.springframework.test.util.ReflectionTestUtils;

public class ConsentTemplateServiceTest {
    private ConsentTemplateServiceImpl service;
    private ReportDefinitionService definitions;
    private ConsentJasperService jasper;
    private ConsentSourceService sources;
    private ConsentSignatureService signatures;
    private ConsentDocumentImageService documentImages;

    @Before
    public void setup() {
        service = new ConsentTemplateServiceImpl();
        definitions = mock(ReportDefinitionService.class);
        jasper = spy(new ConsentJasperService());
        sources = mock(ConsentSourceService.class);
        signatures = mock(ConsentSignatureService.class);
        documentImages = mock(ConsentDocumentImageService.class);
        ReflectionTestUtils.setField(service, "reportDefinitionService", definitions);
        ReflectionTestUtils.setField(service, "jasper", jasper);
        ReflectionTestUtils.setField(service, "sources", sources);
        ReflectionTestUtils.setField(service, "signatures", signatures);
        ReflectionTestUtils.setField(service, "documentImages", documentImages);
        when(definitions.getDefinitionsByCategory("consent_template")).thenReturn(Collections.emptyList());
        when(definitions.getMatch("id", "RPT-CONSENT-TEMPLATE")).thenReturn(Optional.empty());
        List<Map<String, Object>> catalog = List.of(Map.of("id", "orderAdditional.newField"));
        when(sources.getSources()).thenReturn(catalog);
        when(sources.getSources(false)).thenReturn(catalog);
    }

    @Test
    public void documentImagesAndRequesterSignatureUseIndependentMappings() throws Exception {
        var catalog = List.<Map<String, Object>>of(Map.of("id", "provider.signature", "valueType", "image"),
                Map.of("id", "orderDocumentImage.patient_upload", "valueType", "image", "format", "orderDocumentImage",
                        "fieldKey", "patient_upload"),
                Map.of("id", "orderDocumentImage.representative_upload", "valueType", "image", "format",
                        "orderDocumentImage", "fieldKey", "representative_upload"));
        when(sources.getSources()).thenReturn(catalog);
        when(sources.getSources(false)).thenReturn(catalog);
        String content = ConsentJasperServiceTest.signatureTemplate();
        var config = service.parseTemplate("signature.jrxml", content);
        config.setFields(Map.of("FIRMA", "orderDocumentImage.representative_upload", "PACIENTE_FIRMA",
                "orderDocumentImage.patient_upload"));
        service.saveConfig("42", config);
        var stored = ArgumentCaptor.forClass(ReportDefinition.class);
        verify(definitions).insert(stored.capture());
        when(definitions.getDefinitionsByCategory("consent_template")).thenReturn(List.of(stored.getValue()));
        var form = new ConsentPreviewForm();
        form.setSourceValues(Map.of("orderDocumentImage.patient_upload", "forged image"));
        byte[] image = ConsentJasperServiceTest.signaturePng();
        when(documentImages.resolveImages(form, "42", java.util.Set.of("patient_upload", "representative_upload")))
                .thenReturn(Map.of("patient_upload", image, "representative_upload", image));
        assertTrue(pdfText(service.generateConsentPdf(form, "42")).contains("firma solicitante / paciente firmado"));
        when(documentImages.resolveImages(form, "42", java.util.Set.of("patient_upload", "representative_upload")))
                .thenReturn(Map.of("patient_upload", image));
        assertTrue(pdfText(service.generateConsentPdf(form, "42")).contains("sin firma / paciente firmado"));
        verifyZeroInteractions(signatures);
    }

    @Test
    public void savedImageMappingUsesServerSignatureAndIgnoresClientImageValues() throws Exception {
        var catalog = List.<Map<String, Object>>of(Map.of("id", "provider.signature", "valueType", "image"));
        when(sources.getSources()).thenReturn(catalog);
        when(sources.getSources(false)).thenReturn(catalog);
        var config = service.parseTemplate("signature.jrxml", ConsentJasperServiceTest.signatureTemplate());
        config.setFields(Map.of("FIRMA", "provider.signature"));
        service.saveConfig("42", config);
        var stored = ArgumentCaptor.forClass(ReportDefinition.class);
        verify(definitions).insert(stored.capture());
        when(definitions.getDefinitionsByCategory("consent_template")).thenReturn(List.of(stored.getValue()));
        var form = new ConsentPreviewForm();
        var provider = new ConsentPreviewForm.ProviderInfo();
        provider.setProviderId("selected-provider");
        provider.setPersonId("selected-person");
        form.setProvider(provider);
        form.setSourceValues(Map.of("provider.signature", "forged image data"));
        assertTrue(pdfText(service.generateConsentPdf(form)).contains("sin firma / paciente sin firma"));
        when(signatures.getProviderSignature("selected-provider", "selected-person"))
                .thenReturn(ConsentJasperServiceTest.signaturePng());
        assertTrue(pdfText(service.generateConsentPdf(form)).contains("firma solicitante / paciente sin firma"));
        provider.setProviderId("another-provider");
        provider.setPersonId("another-person");
        assertTrue(pdfText(service.generateConsentPdf(form)).contains("sin firma / paciente sin firma"));
        verify(signatures).getProviderSignature("another-provider", "another-person");
    }

    @Test
    public void imageAndTextSourcesCannotBeAssignedToEachOthersParameters() {
        var catalog = List.<Map<String, Object>>of(Map.of("id", "provider.signature", "valueType", "image"),
                Map.of("id", "provider.fullName"));
        when(sources.getSources()).thenReturn(catalog);
        var image = service.parseTemplate("signature.jrxml", ConsentJasperServiceTest.signatureTemplate());
        image.setFields(Map.of("FIRMA", "provider.fullName"));
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig("42", image));
        var text = service.parseTemplate("text.jrxml",
                ConsentJasperServiceTest.template("<parameter name=\"name\" class=\"java.lang.String\"/>", "$P{name}"));
        text.setFields(Map.of("name", "provider.signature"));
        assertThrows(IllegalArgumentException.class, () -> service.saveConfig("42", text));
    }

    @Test
    public void additionalTemplatesKeepIndependentMappingsAndNeverReplaceTheDefault() {
        Map<String, ReportDefinition> stored = new java.util.LinkedHashMap<>();
        when(definitions.getDefinitionsByCategory("consent_template"))
                .thenAnswer(invocation -> new java.util.ArrayList<>(stored.values()));
        when(definitions.getMatch(eq("id"), anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(1))));
        doAnswer(invocation -> {
            ReportDefinition definition = invocation.getArgument(0);
            stored.put(definition.getId(), definition);
            return definition.getId();
        }).when(definitions).insert(any());
        var primary = service.parseTemplate("primary.jrxml", ConsentJasperServiceTest
                .template("<parameter name=\"general\" class=\"java.lang.String\"/>", "\"GENERAL: \" + $P{general}"));
        primary.setName("General");
        primary.setFields(Map.of("general", "orderAdditional.newField"));
        service.saveConfig("42", primary);
        var bank = service.parseTemplate("bank.jrxml", ConsentJasperServiceTest
                .template("<parameter name=\"bank\" class=\"java.lang.String\"/>", "\"BANK: \" + $P{bank}"));
        bank.setName("Bank");
        bank.setFields(Map.of("bank", "orderAdditional.newField"));
        var saved = service.saveTemplate("42", null, bank);
        assertNotEquals("RPT-CONSENT-TEMPLATE", saved.getId());
        assertEquals(Map.of("general", "orderAdditional.newField"), service.getConfig().getFields());
        assertEquals(Map.of("bank", "orderAdditional.newField"), service.getConfig(saved.getId()).getFields());
        assertEquals(2, service.getTemplates(false).size());
        assertNull(service.getTemplates(false).get(1).getTemplateContent());
        var values = new ConsentPreviewForm();
        values.setSourceValues(Map.of("orderAdditional.newField", "Ana"));
        values.setTemplateId(saved.getId());
        assertTrue(pdfText(service.generateConsentPdf(values)).contains("BANK: Ana"));
        values.setTemplateId(null);
        assertTrue(pdfText(service.generateConsentPdf(values)).contains("GENERAL: Ana"));
        var deactivation = new ConsentTemplateConfigForm();
        deactivation.setIsActive(false);
        service.saveTemplate("42", saved.getId(), deactivation);
        assertEquals(bank.getTemplateContent(), service.getConfig(saved.getId()).getTemplateContent());
        assertEquals(bank.getFields(), service.getConfig(saved.getId()).getFields());
        assertEquals(1, service.getTemplates(false).size());
        assertEquals(2, service.getTemplates(true).size());
        ConsentPreviewForm download = new ConsentPreviewForm();
        download.setTemplateId(saved.getId());
        assertThrows(IllegalArgumentException.class, () -> service.generateConsentPdf(download));
    }

    @Test
    public void templateLookupRejectsUnknownIdsAndOtherReportCategories() {
        var report = new ReportDefinition();
        report.setId("other-report");
        report.setCategory("results");
        when(definitions.getMatch("id", "other-report")).thenReturn(Optional.of(report));
        assertThrows(IllegalArgumentException.class, () -> service.getConfig("other-report"));
        assertThrows(IllegalArgumentException.class, () -> service.getConfig("missing"));
    }

    @Test
    public void savesUploadedTemplateAndMappingsAndAuditsUser() {
        ConsentTemplateConfigForm config = service.parseTemplate("consent.jrxml", ConsentJasperServiceTest
                .template("<parameter name=\"custom\" class=\"java.lang.String\"/>", "$P{custom}"));
        config.setFields(Map.of("custom", "orderAdditional.newField"));
        ConsentTemplateConfigForm saved = service.saveConfig("42", config);
        ArgumentCaptor<ReportDefinition> capture = ArgumentCaptor.forClass(ReportDefinition.class);
        verify(definitions).insert(capture.capture());
        assertEquals("42", capture.getValue().getCreatedBy());
        assertTrue(capture.getValue().getDefinitionJson().contains("templateContent"));
        assertEquals(config.getTemplateContent(), saved.getTemplateContent());
        assertEquals("orderAdditional.newField", saved.getFields().get("custom"));
    }

    @Test
    public void keepsExistingMappingsWhenMigratingThePdfConfiguration() {
        ReportDefinition previous = new ReportDefinition();
        previous.setDefinitionJson("{\"fields\":{\"cug\":\"sample.cug\",\"patientName\":\"patient.fullName\"}}");
        when(definitions.getDefinitionsByCategory("consent_template")).thenReturn(List.of(previous));
        ConsentTemplateConfigForm config = service.getConfig();
        assertEquals("sample.cug", config.getFields().get("cug"));
        assertEquals("patient.fullName", config.getFields().get("patientName"));
        assertTrue(config.getTemplateContent().contains("jasperReport"));
    }

    @Test
    public void preservesIntentionallyUnmappedParameters() {
        ConsentTemplateConfigForm config = service.parseTemplate("consent.jrxml", ConsentJasperServiceTest
                .template("<parameter name=\"patientName\" class=\"java.lang.String\"/>", "$P{patientName}"));
        config.setFields(Map.of("patientName", ""));
        assertEquals("", service.saveConfig("42", config).getFields().get("patientName"));
    }

    @Test
    public void unmappedReportParametersKeepTheirDefaultExpressions() {
        var config = service.parseTemplate("defaults.jrxml",
                ConsentJasperServiceTest.template("<parameter name=\"name\" class=\"java.lang.String\"/>"
                        + "<parameter name=\"caption\" class=\"java.lang.String\" isForPrompting=\"false\">"
                        + "<defaultValueExpression><![CDATA[\"Texto calculado\"]]></defaultValueExpression></parameter>",
                        "$P{caption} + \" / \" + $P{name}"));
        config.setFields(Map.of("name", "orderAdditional.newField", "caption", ""));
        service.saveConfig("42", config);
        var stored = ArgumentCaptor.forClass(ReportDefinition.class);
        verify(definitions).insert(stored.capture());
        when(definitions.getDefinitionsByCategory("consent_template")).thenReturn(List.of(stored.getValue()));
        var values = new ConsentPreviewForm();
        values.setSourceValues(Map.of("orderAdditional.newField", "Ana"));
        assertTrue(pdfText(service.generateConsentPdf(values)).contains("Texto calculado / Ana"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInternalIdsAsDataSources() {
        ConsentTemplateConfigForm config = service.parseTemplate("consent.jrxml",
                ConsentJasperServiceTest.template("<parameter name=\"name\" class=\"java.lang.String\"/>", "$P{name}"));
        config.setFields(Map.of("name", "patient.patientPK"));
        service.saveConfig("42", config);
    }

    @Test
    public void rendersSavedCustomMappingsWithVisibleValuesAndTypedBooleans() throws Exception {
        String template = ConsentJasperServiceTest.template(
                "<parameter name=\"custom\" class=\"java.lang.String\"/>"
                        + "<parameter name=\"confirmed\" class=\"java.lang.Boolean\"/>",
                "$P{custom} + \" / \" + $P{confirmed}.toString()");
        List<Map<String, Object>> catalog = List.of(Map.of("id", "orderAdditional.newField"),
                Map.of("id", "orderAdditional.consent"));
        when(sources.getSources()).thenReturn(catalog);
        when(sources.getSources(false)).thenReturn(catalog);
        ConsentTemplateConfigForm config = service.parseTemplate("uploaded.jrxml", template);
        config.setFields(Map.of("custom", "orderAdditional.newField", "confirmed", "orderAdditional.consent"));
        service.saveConfig("42", config);
        ArgumentCaptor<ReportDefinition> capture = ArgumentCaptor.forClass(ReportDefinition.class);
        verify(definitions).insert(capture.capture());
        when(definitions.getDefinitionsByCategory("consent_template")).thenReturn(List.of(capture.getValue()));
        ConsentPreviewForm form = new ConsentPreviewForm();
        form.setSourceValues(Map.of("orderAdditional.newField", "Responsable visible", "orderAdditional.consent", "Sí",
                "patient.patientPK", "secret-id"));
        form.setBooleanValues(Map.of("orderAdditional.consent", true));
        com.itextpdf.text.pdf.PdfReader reader = new com.itextpdf.text.pdf.PdfReader(service.generateConsentPdf(form));
        String text = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, 1);
        assertTrue(text.contains("Responsable visible / true"));
        assertFalse(text.contains("secret-id"));
        reader.close();
    }

    private String pdfText(byte[] pdf) {
        try {
            var reader = new com.itextpdf.text.pdf.PdfReader(pdf);
            try {
                return com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, 1);
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

}
