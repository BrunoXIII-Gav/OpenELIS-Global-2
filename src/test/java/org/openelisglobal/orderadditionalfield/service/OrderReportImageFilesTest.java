package org.openelisglobal.orderadditionalfield.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.documentupload.TemporaryDocumentUploadPayload;
import org.openelisglobal.common.documentupload.TemporaryDocumentUploadServiceImpl;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFileReference;
import org.openelisglobal.orderadditionalfield.dao.*;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldFile;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class OrderReportImageFilesTest {
    private OrderAdditionalFieldServiceImpl service;
    private OrderAdditionalFieldDefinition field;
    private SampleOrderAdditionalFieldFileDAO files;
    private TemporaryDocumentUploadServiceImpl uploads;
    private String token;

    @Before
    public void setup() {
        service = new OrderAdditionalFieldServiceImpl();
        var definitions = mock(OrderAdditionalFieldDefinitionDAO.class);
        files = mock(SampleOrderAdditionalFieldFileDAO.class);
        ReflectionTestUtils.setField(service, "definitionDAO", definitions);
        ReflectionTestUtils.setField(service, "fileDAO", files);
        ReflectionTestUtils.setField(service, "optionDAO", mock(OrderAdditionalFieldOptionDAO.class));
        ReflectionTestUtils.setField(service, "valueDAO", mock(SampleOrderAdditionalFieldValueDAO.class));
        field = new OrderAdditionalFieldDefinition();
        field.setId(1);
        field.setFieldKey("patient_signature");
        field.setDisplayName("Firma del paciente");
        field.setFieldType("DOCUMENT");
        field.setActive(true);
        field.setMetadataJson("{\"document\":{\"accept\":[\"image/png\"],\"maxSizeMb\":1}}");
        when(definitions.findAll(true)).thenReturn(List.of(field));
        when(definitions.findByFieldKey("patient_signature")).thenReturn(Optional.of(field));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        uploads = new TemporaryDocumentUploadServiceImpl();
        ReflectionTestUtils.setField(service, "temporaryDocumentUploadService", uploads);
        token = store(new byte[] { 1, 2, 3 }, "image/png");
        var saved = new SampleOrderAdditionalFieldFile();
        saved.setFileName("saved.png");
        saved.setFileType("image/png");
        saved.setFileContent(new byte[] { 9 });
        when(files.findBySampleIdAndFieldDefinitionId(5, 1)).thenReturn(Optional.of(saved));
    }

    @After
    public void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    private String store(byte[] content, String type) {
        var file = new TemporaryDocumentUploadPayload();
        file.setContent(content);
        file.setFileType(type);
        file.setFileName("uploaded.png");
        return uploads.store("order-additional-field", file).getUploadToken();
    }

    private OrderAdditionalFieldFileReference reference(String value, boolean deleted) {
        var reference = new OrderAdditionalFieldFileReference();
        reference.setUploadToken(value);
        reference.setDeleteFile(deleted);
        return reference;
    }

    private Map<String, org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFilePayload> read(
            String sampleId, OrderAdditionalFieldFileReference reference, Map<String, String> values) {
        return service.getReportImageFiles(sampleId, Set.of("patient_signature"),
                reference == null ? Map.of() : Map.of("patient_signature", reference), values, Map.of());
    }

    @Test
    public void creationUsesSessionUploadWithoutConsumingIt() {
        var result = read(null, reference(token, false), Map.of());
        assertArrayEquals(new byte[] { 1, 2, 3 }, result.get("patient_signature").getContent());
        assertTrue(uploads.get("order-additional-field", token).isPresent());
        verify(files, never()).findBySampleIdAndFieldDefinitionId(any(), any());
    }

    @Test
    public void editingUsesSavedFileAndPrioritizesReplacementOrRemoval() {
        assertArrayEquals(new byte[] { 9 }, read("5", null, Map.of()).get("patient_signature").getContent());
        assertArrayEquals(new byte[] { 1, 2, 3 },
                read("5", reference(token, false), Map.of()).get("patient_signature").getContent());
        assertTrue(read("5", reference(null, true), Map.of()).isEmpty());
        verify(files, times(1)).findBySampleIdAndFieldDefinitionId(5, 1);
        verify(files, never()).delete(any());
        verify(files, never()).update(any());
    }

    @Test
    public void uploadTokensFromOtherSessionsNeverFallBackToSavedFile() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        assertThrows(IllegalArgumentException.class, () -> read("5", reference(token, false), Map.of()));
        verify(files, never()).findBySampleIdAndFieldDefinitionId(any(), any());
    }

    @Test
    public void hiddenInactivePdfOnlyAndGeneratedDocumentFieldsAreExcluded() {
        field.setMetadataJson("{\"document\":{\"accept\":[\"image/png\"]},\"rules\":{\"visibleWhen\":["
                + "{\"fieldKey\":\"checked\",\"operator\":\"istrue\"}]}}");
        assertTrue(read(null, reference(token, false), Map.of("checked", "false")).isEmpty());
        assertFalse(read(null, reference(token, false), Map.of("checked", "true")).isEmpty());
        field.setActive(false);
        assertTrue(read(null, reference(token, false), Map.of("checked", "true")).isEmpty());
        field.setActive(true);
        field.setMetadataJson("{\"document\":{\"accept\":[\"application/pdf\"]}}");
        assertTrue(read(null, reference(token, false), Map.of()).isEmpty());
        field.setMetadataJson(
                "{\"document\":{\"mode\":\"CONSENT\",\"templateId\":\"bank\",\"accept\":[\"image/png\"]}}");
        assertTrue(read(null, reference(token, false), Map.of()).isEmpty());
        verify(files, never()).findBySampleIdAndFieldDefinitionId(any(), any());
    }

    @Test
    public void filesMustMeetTheFieldsMimeAndSizeConfiguration() {
        String jpeg = store(new byte[] { 1 }, "image/jpeg");
        assertThrows(IllegalArgumentException.class, () -> read(null, reference(jpeg, false), Map.of()));
        String large = store(new byte[1024 * 1024 + 1], "image/png");
        assertThrows(IllegalArgumentException.class, () -> read(null, reference(large, false), Map.of()));
    }
}
