package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Year;
import java.util.EnumMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat;
import org.openelisglobal.common.provider.validation.IAccessionNumberValidator;
import org.openelisglobal.common.provider.validation.IAccessionNumberValidator.ValidationResults;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.valueholder.AccessionNumberInfo;
import org.openelisglobal.common.valueholder.AccessionNumberInfo.AccessionIdentity;
import org.openelisglobal.sample.dao.OrderAccessionDAO;
import org.openelisglobal.sample.exception.OrderAccessionConflictException;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderAccessionServiceTest {
    private OrderAccessionServiceImpl service;
    private OrderAccessionDAO dao;
    private IAccessionNumberValidator validator;
    private final Map<Property, String> properties = new EnumMap<>(Property.class);

    @Before
    public void setup() throws Exception {
        properties.put(Property.AccessionFormat, "PREFIXNUM");
        properties.put(Property.ACCESSION_NUMBER_PREFIX, "LAB");
        service = new OrderAccessionServiceImpl() {
            @Override
            protected String property(Property property) {
                return properties.getOrDefault(property, "");
            }
        };
        dao = mock(OrderAccessionDAO.class);
        validator = mock(IAccessionNumberValidator.class);
        var factory = mock(AccessionNumberValidatorFactory.class);
        when(factory.getValidator(any())).thenReturn(validator);
        when(validator.validFormat(anyString(), anyBoolean())).thenReturn(ValidationResults.SUCCESS);
        ReflectionTestUtils.setField(service, "dao", dao);
        ReflectionTestUtils.setField(service, "validators", factory);
    }

    @Test
    public void previewsNeverCreateCountersReserveOrLock() {
        assertEquals("LAB0000000000001", service.preview(null));
        assertEquals("LAB0000000000001", service.preview(null));
        verify(dao, never()).saveCounter(any());
        verify(dao, never()).lockSubmission();
        verify(dao, never()).counter(any(), eq(true));
    }

    @Test
    public void previewContinuesFromBothSavedSamplesAndOlderReservations() {
        var counter = counter(4);
        when(dao.counter(any(), eq(false))).thenReturn(counter);
        when(dao.largest("LAB", 16)).thenReturn("LAB0000000000008");
        assertEquals("LAB0000000000009", service.preview(null));
        assertEquals(Long.valueOf(4), counter.getCurVal());
    }

    @Test public void concurrentGeneratedNumberAdvancesAndReturnsTheActualNumber() {
        when(dao.largest("LAB", 16)).thenReturn("LAB0000000000001");
        assertEquals("LAB0000000000002", service.confirm("LAB0000000000001", true));
        verify(dao).lockSubmission();
        var saved = org.mockito.ArgumentCaptor.forClass(AccessionNumberInfo.class);
        verify(dao).saveCounter(saved.capture());
        assertEquals(Long.valueOf(2), saved.getValue().getCurVal());
    }

    @Test
    public void generationRespectsNumbersReservedByOtherModulesAfterPreview() {
        var counter = counter(9);
        when(dao.counter(any(), eq(true))).thenReturn(counter);
        assertEquals("LAB0000000000010", service.confirm("LAB0000000000001", true));
        assertEquals(Long.valueOf(10), counter.getCurVal());
    }

    @Test
    public void manualNumbersArePreservedAndNeverAutoIncremented() {
        assertEquals("LAB0000000000025", service.confirm("LAB0000000000025", false));
        verify(dao, never()).saveCounter(any());
        when(dao.isUsed("LAB0000000000025")).thenReturn(true);
        try {
            service.confirm("LAB0000000000025", false);
            fail();
        } catch (OrderAccessionConflictException expected) {
            assertEquals("order.labnumber.conflict", expected.getMessage());
        }
        verify(dao, never()).largest(anyString(), anyInt());
    }

    @Test public void invalidNumbersAreRejectedBeforePersistenceOrLocks() {
        when(validator.validFormat(anyString(), anyBoolean())).thenReturn(ValidationResults.FORMAT_FAIL);
        try { service.confirm("invalid", false); fail(); }
        catch (IllegalArgumentException expected) { assertEquals("order.labnumber.invalid", expected.getMessage()); }
        verifyZeroInteractions(dao);
    }

    @Test
    public void previewsPreserveAllConfiguredNumberFormats() {
        String year = String.format(java.util.Locale.ROOT, "%02d", Year.now().getValue() % 100);
        Map<String, String> expected = Map.of("SITEYEARNUM", "LAB" + year + "0000000000001", "ALT_YEAR",
                "ALT" + year + "0000000000001", "ALPHANUM", year + "A000001", "YEARNUM_SIX", year + "000001",
                "YEARNUM_SEVEN", year + "0000001", "YEARNUM_DASH_SEVEN", year + "-0000001", "PROGRAMNUM", "TEST00001");
        properties.put(Property.ALT_ACCESSION_PREFIX, "ALT");
        properties.put(Property.ALPHANUM_ACCESSION_PREFIX, "A");
        expected.forEach((format, code) -> {
            properties.put(Property.AccessionFormat, format);
            assertEquals(format, code, service.preview("TEST"));
        });
    }

    @Test
    public void alphanumericSequencesUseTheExistingBase27Alphabet() {
        properties.put(Property.AccessionFormat, "ALPHANUM");
        String year = String.format(java.util.Locale.ROOT, "%02d", Year.now().getValue() % 100);
        when(dao.largest(year, 8)).thenReturn(year + "000009");
        assertEquals(year + "00000C", service.preview(null));
    }

    private AccessionNumberInfo counter(long value) {
        var row = new AccessionNumberInfo();
        row.setAccessionIdentity(new AccessionIdentity("LAB", AccessionFormat.PREFIXNUM));
        row.setCurVal(value);
        return row;
    }
}
