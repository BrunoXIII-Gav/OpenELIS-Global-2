package org.openelisglobal.orderadditionalfield.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldFileDAO;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldValueDAO;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderRequiredCheckboxTest {
    private OrderAdditionalFieldServiceImpl service;
    private SampleOrderAdditionalFieldValueDAO valueDAO;
    private OrderAdditionalFieldPayload consent;
    private List<OrderAdditionalFieldPayload> fields;

    @Before
    public void setup() {
        service = new OrderAdditionalFieldServiceImpl();
        valueDAO = mock(SampleOrderAdditionalFieldValueDAO.class);
        ReflectionTestUtils.setField(service, "valueDAO", valueDAO);
        ReflectionTestUtils.setField(service, "fileDAO", mock(SampleOrderAdditionalFieldFileDAO.class));
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", mock(UserFieldOptionResolver.class));
        consent = checkbox(1, "consent");
        consent.setMetadataJson("{\"rules\":{\"requiredWhen\":[{\"fieldKey\":\"genomic\",\"operator\":\"isTrue\"}]}}");
        fields = List.of(consent, checkbox(2, "genomic"));
    }

    @Test
    public void selectedGenomicDiagnosisRejectsUncheckedConsent() {
        assertRequiredFailure(Map.of("genomic", "true", "consent", "false"));
        assertRequiredFailure(Map.of("genomic", "true"));
    }

    @Test
    public void selectedGenomicDiagnosisAcceptsCheckedConsent() {
        persist(Map.of("genomic", "true", "consent", "true"));
        verify(valueDAO).insert(argThat(value -> "true".equals(value.getFieldValue())
                && Integer.valueOf(1).equals(value.getFieldDefinitionId())));
    }

    @Test
    public void unselectedGenomicDiagnosisAllowsUncheckedConsent() {
        persist(Map.of("genomic", "false", "consent", "false"));
        verify(valueDAO).insert(argThat(value -> "false".equals(value.getFieldValue())
                && Integer.valueOf(1).equals(value.getFieldDefinitionId())));
    }

    @Test
    public void unconditionallyRequiredCheckboxMustBeChecked() {
        consent.setRequired(true);
        assertRequiredFailure(Map.of("genomic", "false", "consent", "false"));
    }

    @Test
    public void hiddenRequiredCheckboxDoesNotBlockSaving() {
        consent.setRequired(true);
        consent.setMetadataJson("{\"rules\":{\"visibleWhen\":[{\"fieldKey\":\"genomic\",\"operator\":\"isFalse\"}]}}");
        persist(Map.of("genomic", "true", "consent", "false"));
        verify(valueDAO, never()).insert(argThat(value -> Integer.valueOf(1).equals(value.getFieldDefinitionId())));
    }

    @Test
    public void checkedDefaultDoesNotOverrideExplicitlyUncheckedConsent() {
        consent.setDefaultValue("true");
        persist(Map.of("genomic", "true"));
        assertRequiredFailure(Map.of("genomic", "true", "consent", "false"));
    }

    @Test
    public void omittedExistingConsentIsRevalidatedWhenItBecomesRequired() {
        var existing = new SampleOrderAdditionalFieldValue();
        existing.setId(3);
        existing.setFieldValue("false");
        when(valueDAO.findBySampleIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(existing));
        assertRequiredFailure(Map.of("genomic", "true"));
        existing.setFieldValue("true");
        persist(Map.of("genomic", "true"));
        verify(valueDAO, never()).update(any());
    }

    private OrderAdditionalFieldPayload checkbox(int id, String key) {
        var field = new OrderAdditionalFieldPayload();
        field.setId(id);
        field.setFieldKey(key);
        field.setDisplayName(key);
        field.setFieldType("BOOLEAN");
        return field;
    }

    private void persist(Map<String, String> values) {
        service.validateAndPersistSampleValues("100", values, "12", fields);
    }

    private void assertRequiredFailure(Map<String, String> values) {
        try {
            persist(values);
            fail("Unchecked required consent must prevent saving");
        } catch (LIMSRuntimeException exception) {
            assertTrue(exception.getMessage().contains("Order field is required: consent"));
        }
    }
}
