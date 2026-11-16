package org.openelisglobal.reports.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFilePayload;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

public class ConsentDocumentImageServiceTest {
    private ConsentDocumentImageServiceImpl service;
    private OrderAdditionalFieldService fields;
    private OrderAuthorizationService authorization;
    private SampleService samples;
    private ConsentPreviewForm form;

    @Before
    public void setup() {
        service = new ConsentDocumentImageServiceImpl();
        fields = mock(OrderAdditionalFieldService.class);
        authorization = mock(OrderAuthorizationService.class);
        samples = mock(SampleService.class);
        ReflectionTestUtils.setField(service, "orderFields", fields);
        ReflectionTestUtils.setField(service, "authorization", authorization);
        ReflectionTestUtils.setField(service, "samples", samples);
        form = new ConsentPreviewForm();
        when(authorization.hasPermission("42", "create")).thenReturn(true);
        when(authorization.hasPermission("42", "update")).thenReturn(true);
    }

    private OrderAdditionalFieldFilePayload file(byte[] bytes) {
        var file = new OrderAdditionalFieldFilePayload();
        file.setFileType("image/png");
        file.setContent(bytes);
        return file;
    }

    @Test
    public void acceptsRealPngAndJpegButRejectsPdfAndTextDisguisedAsImages() throws Exception {
        var png = ConsentJasperServiceTest.signaturePng();
        var jpegBytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(3, 2, java.awt.image.BufferedImage.TYPE_INT_RGB),
                "jpeg", jpegBytes);
        when(fields.getReportImageFiles(isNull(), eq(Set.of("patient", "representative")), any(), any(), any()))
                .thenReturn(Map.of("patient", file(png), "representative", file(jpegBytes.toByteArray())));
        var result = service.resolveImages(form, "42", Set.of("patient", "representative"));
        assertArrayEquals(png, result.get("patient"));
        assertArrayEquals(jpegBytes.toByteArray(), result.get("representative"));
        when(fields.getReportImageFiles(isNull(), anySet(), any(), any(), any()))
                .thenReturn(Map.of("patient", file("%PDF-not-an-image".getBytes())));
        assertTrue(service.resolveImages(form, "42", Set.of("patient")).isEmpty());
        verifyZeroInteractions(samples);
    }

    @Test
    public void savedImagesRequireOrderAndLaboratoryPermissionBeforeReadingAnyFiles() throws Exception {
        form.setOrderAccessionNumber("LAB-5");
        var sample = new Sample();
        sample.setId("5");
        when(samples.getSampleByAccessionNumber("LAB-5")).thenReturn(sample);
        assertThrows(AccessDeniedException.class, () -> service.resolveImages(form, "42", Set.of("patient")));
        verifyZeroInteractions(fields);
        when(authorization.canAccessAllTests("42", List.of(), "update")).thenReturn(true);
        when(fields.getReportImageFiles(eq("5"), anySet(), any(), any(), any()))
                .thenReturn(Map.of("patient", file(ConsentJasperServiceTest.signaturePng())));
        assertEquals(1, service.resolveImages(form, "42", Set.of("patient")).size());
    }

    @Test
    public void missingUserPermissionOrOrderCannotReadImages() {
        assertThrows(AccessDeniedException.class, () -> service.resolveImages(form, null, Set.of("patient")));
        assertThrows(AccessDeniedException.class, () -> service.resolveImages(form, "other", Set.of("patient")));
        form.setOrderAccessionNumber("missing");
        assertThrows(IllegalArgumentException.class, () -> service.resolveImages(form, "42", Set.of("patient")));
        verifyZeroInteractions(fields);
    }

    @Test
    public void reportsWithoutDocumentImageMappingsKeepTheirExistingFlow() {
        assertTrue(service.resolveImages(null, null, Set.of()).isEmpty());
        verifyZeroInteractions(fields, authorization, samples);
    }
}
