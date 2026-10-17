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

    @Before
    public void setup() {
        service = new ConsentTemplateServiceImpl();
        definitions = mock(ReportDefinitionService.class);
        jasper = spy(new ConsentJasperService());
        sources = mock(ConsentSourceService.class);
        ReflectionTestUtils.setField(service, "reportDefinitionService", definitions);
        ReflectionTestUtils.setField(service, "jasper", jasper);
        ReflectionTestUtils.setField(service, "sources", sources);
        when(definitions.getDefinitionsByCategory("consent_template")).thenReturn(Collections.emptyList());
        when(definitions.getMatch("id", "RPT-CONSENT-TEMPLATE")).thenReturn(Optional.empty());
        List<Map<String, Object>> catalog = List.of(Map.of("id", "orderAdditional.newField"));
        when(sources.getSources()).thenReturn(catalog);
        when(sources.getSources(false)).thenReturn(catalog);
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

}
