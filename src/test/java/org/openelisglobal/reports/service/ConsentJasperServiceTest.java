package org.openelisglobal.reports.service;

import static org.junit.Assert.*;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Test;
import org.openelisglobal.reportdefinition.form.ConsentTemplateConfigForm;

public class ConsentJasperServiceTest {
    private final ConsentJasperService service = new ConsentJasperService();

    static String template(String definitions, String expression) {
        return "<?xml version=\"1.0\"?><jasperReport xmlns=\"http://jasperreports.sourceforge.net/jasperreports\" "
                + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd\" "
                + "name=\"Consent\" pageWidth=\"595\" pageHeight=\"842\" columnWidth=\"555\" "
                + "leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\">" + definitions
                + "<detail><band height=\"40\"><textField><reportElement x=\"0\" y=\"0\" width=\"500\" height=\"40\"/>"
                + "<textFieldExpression><![CDATA[" + expression
                + "]]></textFieldExpression></textField></band></detail></jasperReport>";
    }

    @Test
    public void detectsAndFillsParametersAndFieldsByTheirActualNames() throws Exception {
        String content = template(
                "<parameter name=\"APELLIDOS\" class=\"java.lang.String\"/>"
                        + "<field name=\"DOCUMENTO\" class=\"java.lang.String\"/>",
                "$P{APELLIDOS} + \" / \" + $F{DOCUMENTO}");
        ConsentTemplateConfigForm parsed = service.parse("new-consent.jrxml", content);
        assertEquals(2, parsed.getParameters().size());
        assertEquals("field:DOCUMENTO", parsed.getParameters().get(1).get("id"));
        PdfReader reader = new PdfReader(
                service.render(parsed, Map.of("APELLIDOS", "Paciente de prueba", "field:DOCUMENTO", "12345678")));
        assertTrue(PdfTextExtractor.getTextFromPage(reader, 1).contains("Paciente de prueba / 12345678"));
        reader.close();
    }

    @Test
    public void nonPromptingParametersRemainAvailableForApplicationMappings() throws Exception {
        String content = template(
                "<parameter name=\"NOMBRE\" class=\"java.lang.String\" isForPrompting=\"false\"/>"
                        + "<parameter name=\"CONFIRMADO\" class=\"java.lang.Boolean\" isForPrompting=\"false\"/>",
                "$P{NOMBRE} + \" / \" + $P{CONFIRMADO}");
        ConsentTemplateConfigForm parsed = service.parse("bank.jrxml", content);
        assertEquals(2, parsed.getParameters().size());
        PdfReader reader = new PdfReader(
                service.render(parsed, Map.of("NOMBRE", "Paciente de prueba", "CONFIRMADO", "true")));
        try {
            assertTrue(PdfTextExtractor.getTextFromPage(reader, 1).contains("Paciente de prueba / true"));
        } finally {
            reader.close();
        }
    }

    @Test
    public void optionalSignatureStreamsAreDetectedAsImagesAndRemainOptional() throws Exception {
        String content = template(
                "<parameter name=\"NOMBRE\" class=\"java.lang.String\" isForPrompting=\"false\"/>"
                        + "<parameter name=\"FIRMA\" class=\"java.io.InputStream\" isForPrompting=\"false\">"
                        + "<defaultValueExpression><![CDATA[null]]></defaultValueExpression></parameter>",
                "$P{NOMBRE} + ($P{FIRMA} == null ? \" / firma pendiente\" : \" / firmado\")");
        ConsentTemplateConfigForm parsed = service.parse("bank.jrxml", content);
        assertEquals(2, parsed.getParameters().size());
        assertEquals("NOMBRE", parsed.getParameters().get(0).get("name"));
        assertEquals("image", parsed.getParameters().get(1).get("valueType"));
        assertEquals(content, parsed.getTemplateContent());
        PdfReader reader = new PdfReader(service.render(parsed, Map.of("NOMBRE", "Paciente de prueba")));
        try {
            assertTrue(PdfTextExtractor.getTextFromPage(reader, 1).contains("Paciente de prueba / firma pendiente"));
        } finally {
            reader.close();
        }
    }

    static byte[] signaturePng() throws Exception {
        var image = new java.awt.image.BufferedImage(3, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(1, 1, 0xffffff);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    static String signatureTemplate() {
        return template(
                "<parameter name=\"FIRMA\" class=\"java.io.InputStream\" isForPrompting=\"false\"/>"
                        + "<parameter name=\"PACIENTE_FIRMA\" class=\"java.io.InputStream\" isForPrompting=\"false\"/>",
                "($P{FIRMA} == null ? \"sin firma\" : \"firma solicitante\")"
                        + " + ($P{PACIENTE_FIRMA} == null ? \" / paciente sin firma\" : \" / paciente firmado\")")
                .replace("</band>", "<image><reportElement x=\"0\" y=\"0\" width=\"20\" height=\"10\">"
                        + "<printWhenExpression><![CDATA[$P{FIRMA} != null]]></printWhenExpression></reportElement>"
                        + "<imageExpression><![CDATA[$P{FIRMA}]]></imageExpression></image></band>");
    }

    @Test
    public void rendersSignatureBytesAsAnImageWithoutFillingOtherSignatures() throws Exception {
        var config = service.parse("signature.jrxml", signatureTemplate());
        // Text/base64 values must never be interpreted as an image input.
        var unsigned = new PdfReader(service.render(config, Map.of("FIRMA", "untrusted-text")));
        assertTrue(PdfTextExtractor.getTextFromPage(unsigned, 1).contains("sin firma / paciente sin firma"));
        unsigned.close();
        var signed = new PdfReader(service.render(config, Map.of(), Map.of("FIRMA", signaturePng())));
        try {
            assertTrue(PdfTextExtractor.getTextFromPage(signed, 1).contains("firma solicitante / paciente sin firma"));
            var objects = signed.getPageN(1).getAsDict(com.itextpdf.text.pdf.PdfName.RESOURCES)
                    .getAsDict(com.itextpdf.text.pdf.PdfName.XOBJECT);
            assertNotNull(objects);
            assertTrue(objects.getKeys().stream()
                    .anyMatch(key -> com.itextpdf.text.pdf.PdfName.IMAGE
                            .equals(((com.itextpdf.text.pdf.PdfDictionary) PdfReader.getPdfObject(objects.get(key)))
                                    .getAsName(com.itextpdf.text.pdf.PdfName.SUBTYPE))));
        } finally {
            signed.close();
        }
    }

    @Test
    public void unmappedAliasesAndComputedParametersKeepTheirJasperDefaults() throws Exception {
        String content = template("<parameter name=\"NOMBRE\" class=\"java.lang.String\" isForPrompting=\"false\"/>"
                + "<parameter name=\"ALIAS\" class=\"java.lang.String\" isForPrompting=\"false\">"
                + "<defaultValueExpression><![CDATA[$P{NOMBRE}]]></defaultValueExpression></parameter>"
                + "<parameter name=\"GENERADO\" class=\"java.lang.String\" isForPrompting=\"false\">"
                + "<defaultValueExpression><![CDATA[new String(\"generado\")]]></defaultValueExpression></parameter>",
                "$P{ALIAS} + \" / \" + $P{GENERADO}");
        ConsentTemplateConfigForm parsed = service.parse("bank.jrxml", content);
        PdfReader reader = new PdfReader(service.render(parsed, Map.of("NOMBRE", "Paciente de prueba")));
        try {
            assertTrue(PdfTextExtractor.getTextFromPage(reader, 1).contains("Paciente de prueba / generado"));
        } finally {
            reader.close();
        }
    }

    @Test
    public void defaultTemplatePreservesPageAndAllSelectedTests() throws Exception {
        String content = new String(getClass().getClassLoader()
                .getResourceAsStream("reports/consent/consent-template.jrxml").readAllBytes(), StandardCharsets.UTF_8);
        ConsentTemplateConfigForm config = service.parse("consent-template.jrxml", content);
        assertEquals(10, config.getParameters().size());
        assertTrue(config.getParameters().stream()
                .anyMatch(p -> "printedAt".equals(p.get("id")) && "true".equals(p.get("hasDefault"))));
        assertTrue(config.getParameters().stream().anyMatch(p -> "guardianName".equals(p.get("id"))));
        assertTrue(config.getParameters().stream().anyMatch(p -> "guardianDni".equals(p.get("id"))));
        String tests = java.util.stream.IntStream.rangeClosed(1, 12).mapToObj(i -> "Prueba " + i)
                .collect(java.util.stream.Collectors.joining("\n"));
        byte[] output = service.render(config,
                Map.of("patientName", "Paciente de prueba", "patientDni", "12345678", "providerName",
                        "Profesional de prueba", "providerDni", "87654321", "cug", "5000.1", "date", "16/10/2026",
                        "tests", tests, "guardianName", "Responsable de prueba", "guardianDni", "11223344"));
        PdfReader reader = new PdfReader(output);
        assertEquals(1, reader.getNumberOfPages());
        assertEquals(595, reader.getPageSize(1).getWidth(), 0.01);
        assertEquals(842, reader.getPageSize(1).getHeight(), 0.01);
        String text = PdfTextExtractor.getTextFromPage(reader, 1);
        assertTrue(text.contains("Paciente de prueba"));
        assertTrue(text.contains("Prueba 12"));
        assertTrue(text.contains("Responsable de prueba"));
        assertTrue(text.contains("11223344"));
        assertFalse(text.contains("7024556"));
        reader.close();
        Files.write(Path.of("/tmp/consent-jasper-preview.pdf"), output);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsExternalEntities() {
        service.parse("bad.jrxml",
                "<!DOCTYPE x [<!ENTITY ex SYSTEM 'file:///etc/passwd'>]><jasperReport>&ex;</jasperReport>");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDatabaseQueries() {
        service.parse("bad.jrxml", template(
                "<parameter name=\"name\" class=\"java.lang.String\"/><queryString>select secret from anything</queryString>",
                "$P{name}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedTypes() {
        service.parse("bad.jrxml",
                template("<parameter name=\"connection\" class=\"java.sql.Connection\"/>", "\"test\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPromptingDoesNotPermitUnsupportedDataSourceTypes() {
        service.parse("bad.jrxml", template(
                "<parameter name=\"connection\" class=\"java.sql.Connection\" isForPrompting=\"false\"/>", "\"test\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOversizedUploads() {
        service.parse("bad.jrxml", "x".repeat(2 * 1024 * 1024 + 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidExpressionsBeforeSaving() {
        service.parse("bad.jrxml", template("<parameter name=\"name\" class=\"java.lang.String\"/>", "$P{missing}"));
    }
}
