package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.services.SampleAddService.SampleTestCollection;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.dao.SampleCugReservationDAO;
import org.openelisglobal.sample.valueholder.SampleCugReservation;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.springframework.test.util.ReflectionTestUtils;

public class SampleCugServiceTest {
    private SampleCugServiceImpl service;
    private CugPrefixService prefixes;
    private SampleCugReservationDAO reservations;
    private SampleItemDAO items;

    @Before
    public void setup() {
        service = new SampleCugServiceImpl();
        prefixes = mock(CugPrefixService.class);
        reservations = mock(SampleCugReservationDAO.class);
        items = mock(SampleItemDAO.class);
        ReflectionTestUtils.setField(service, "prefixes", prefixes);
        ReflectionTestUtils.setField(service, "sampleCugReservationDAO", reservations);
        ReflectionTestUtils.setField(service, "sampleItemDAO", items);
        when(prefixes.allocate("42")).thenReturn(5000L, 7000L, 4000L);
        when(reservations.insertIfValueAvailable(any())).thenAnswer(call -> Optional.of(call.getArgument(0)));
    }

    @Test public void existingPatientKeepsPrefixAndIncreasesSuffixAfterAnyConfigurationChange() {
        when(items.findCugCodesByPatientId("10")).thenReturn(List.of("140.1", "140.2"));
        when(reservations.findActiveValuesForPatient(eq(10), any())).thenReturn(List.of("140.3"));
        var request = request("10", List.of("140.4"));
        assertEquals("140.5", service.reserveCugCode(request, "42").getCugCode());
        verify(prefixes, never()).allocate(anyString());
        verify(prefixes, never()).lockAllocation();
    }

    @Test
    public void multipleSamplesOfANewPatientShareTheirPrefixAndOnlyOnePrefixIsAllocated() {
        String first = service.reserveCugCode(request(null, List.of()), "42").getCugCode();
        assertEquals("5000.1", first);
        assertEquals("5000.2", service.reserveCugCode(request(null, List.of(first)), "42").getCugCode());
        verify(prefixes, times(1)).allocate("42");
    }

    @Test
    public void newPatientsUseTheConfiguredAllocationService() {
        assertEquals("5000.1", service.reserveCugCode(request(null, List.of()), "42").getCugCode());
        assertEquals("7000.1", service.reserveCugCode(request(null, List.of()), "42").getCugCode());
        assertEquals("4000.1", service.reserveCugCode(request(null, List.of()), "42").getCugCode());
    }

    @Test
    public void completingTheOrderConsumesItsReservationWithoutRenumberingOrAllocatingAgain() {
        var reservation = reservation("5000.1");
        when(reservations.findByReservationToken("token")).thenReturn(Optional.of(reservation));
        var sample = sample("5000.1");
        sample.cugReservationToken = "token";
        service.assignMissingCugCodes(List.of(sample), "10", "42", "20");
        assertEquals("5000.1", sample.item.getCugCode());
        assertEquals("CONSUMED", reservation.getStatus());
        assertEquals(Integer.valueOf(20), reservation.getSampleId());
        verify(prefixes, never()).allocate(anyString());
        try {
            service.assignMissingCugCodes(List.of(sample), "10", "42", "20");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("no longer active"));
        }
    }

    @Test
    public void manualOverrideCannotTakeAnotherAllocatedPrefixEvenIfItsFirstSuffixIsFree() {
        var reservation = reservation("4999.1");
        when(reservations.findByReservationToken("token")).thenReturn(Optional.of(reservation));
        when(prefixes.isAssigned(5000L)).thenReturn(true);
        var sample = sample("5000.1");
        sample.cugReservationToken = "token";
        try {
            service.assignMissingCugCodes(List.of(sample), null, "42", "20");
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("sample.cug.prefix.alreadyAssigned", expected.getMessage());
        }
        verify(reservations, never()).update(any());
    }

    @Test
    public void suffixExhaustionCannotWrapAroundToANegativeCode() {
        when(items.findCugCodesByPatientId("10")).thenReturn(List.of("140.9223372036854775807"));
        try {
            service.reserveCugCode(request("10", List.of()), "42");
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("sample.cug.suffix.exhausted", expected.getMessage());
        }
        verify(reservations, never()).insertIfValueAvailable(any());
    }

    private SampleCugPreviewRequest request(String patient, List<String> existing) {
        var r = new SampleCugPreviewRequest();
        r.setPatientId(patient);
        r.setExistingCugs(existing);
        return r;
    }

    private SampleCugReservation reservation(String value) {
        var r = new SampleCugReservation();
        r.setReservedValue(value);
        r.setReservedByUserId(42);
        r.setExpiresAt(new Timestamp(System.currentTimeMillis() + 60000));
        r.setStatus("RESERVED");
        return r;
    }

    private SampleTestCollection sample(String value) {
        var item = new SampleItem();
        item.setCugCode(value);
        // The legacy final inner DTO's outer service has static Spring lookups.
        // Allocate just the DTO; the CUG service reads only its public fields.
        var sample = new org.objenesis.ObjenesisStd().newInstance(SampleTestCollection.class);
        sample.item = item;
        return sample;
    }
}
