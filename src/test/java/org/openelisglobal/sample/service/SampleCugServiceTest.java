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
import org.openelisglobal.sample.dao.SampleCugPrefixDAO;
import org.openelisglobal.sample.dao.SampleCugReservationDAO;
import org.openelisglobal.sample.exception.SampleCugConflictException;
import org.openelisglobal.sample.valueholder.SampleCugReservation;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.springframework.test.util.ReflectionTestUtils;

public class SampleCugServiceTest {
    private SampleCugServiceImpl service;
    private CugPrefixService prefixes;
    private SampleCugReservationDAO reservations;
    private SampleItemDAO items;
    private SampleCugPrefixDAO patientPrefixes;

    @Before
    public void setup() {
        service = new SampleCugServiceImpl();
        prefixes = mock(CugPrefixService.class);
        reservations = mock(SampleCugReservationDAO.class);
        items = mock(SampleItemDAO.class);
        patientPrefixes = mock(SampleCugPrefixDAO.class);
        when(patientPrefixes.findPatientPrefix(any())).thenReturn(null);
        ReflectionTestUtils.setField(service, "patientPrefixes", patientPrefixes);
        ReflectionTestUtils.setField(service, "prefixes", prefixes);
        ReflectionTestUtils.setField(service, "sampleCugReservationDAO", reservations);
        ReflectionTestUtils.setField(service, "sampleItemDAO", items);
        when(prefixes.allocate("42")).thenReturn(5000L, 7000L, 4000L);
        when(reservations.insertIfValueAvailable(any())).thenAnswer(call -> Optional.of(call.getArgument(0)));
    }

    @Test
    public void patientPreviewDoesNotAllocateOrReserve() {
        var settings = new org.openelisglobal.sample.form.CugPrefixSettingsForm();
        settings.setNextAvailablePrefix("5000");
        when(prefixes.getSettings()).thenReturn(settings);
        assertEquals("5000", service.previewPatientPrefix(null));
        verify(prefixes, never()).allocate(anyString());
        verify(reservations, never()).insertIfValueAvailable(any());
        verify(patientPrefixes, never()).bindPatientPrefix(anyString(), anyLong(), anyString());
    }

    @Test
    public void savedPatientKeepsItsPrefixEvenWithoutSamples() {
        when(patientPrefixes.findPatientPrefix("10")).thenReturn(5000L);
        assertEquals("5000", service.ensurePatientPrefix("10", "42"));
        assertEquals("5000.1", service.previewCugCode(request("10", List.of())).getCugCode());
        verify(prefixes, never()).allocate(anyString());
        verify(reservations, never()).insertIfValueAvailable(any());
    }

    @Test
    public void savingPatientBindsAllocatedPrefixInsteadOfTrustingPreview() {
        assertEquals("5000", service.ensurePatientPrefix("10", "42"));
        verify(reservations).lockPatientForCug("10");
        verify(patientPrefixes).bindPatientPrefix("10", 5000L, "42");
    }

    @Test
    public void existingHistoryDeterminesPatientPrefixWithoutRenumbering() {
        when(items.findCugCodesByPatientId("10")).thenReturn(List.of("140.1", "140.2", "90.1"));
        assertEquals("140", service.ensurePatientPrefix("10", "42"));
        verify(patientPrefixes).bindPatientPrefix("10", 140L, "42");
        verify(prefixes, never()).allocate(anyString());
    }

    @Test
    public void manualPrefixIsClaimedAndBoundToItsPatient() {
        when(prefixes.claim("5006", "42")).thenReturn(5006L);
        assertEquals("5006", service.assignPatientPrefix("10", "5006", "42"));
        verify(patientPrefixes).bindPatientPrefix("10", 5006L, "42");
        verify(prefixes, never()).allocate(anyString());
    }

    @Test
    public void manualPrefixCannotRenumberAnExistingPatientOrItsHistory() {
        when(patientPrefixes.findPatientPrefix("10")).thenReturn(5004L);
        assertThrows(org.openelisglobal.sample.exception.PatientCugPrefixException.class,
                () -> service.assignPatientPrefix("10", "8000", "42"));
        assertEquals("5004", service.assignPatientPrefix("10", "5004", "42"));
        verify(prefixes, never()).claim(anyString(), anyString());
        verify(patientPrefixes, never()).bindPatientPrefix(anyString(), anyLong(), anyString());
    }

    @Test
    public void legacyHistoryAlsoPreventsManualRenumbering() {
        when(items.findCugCodesByPatientId("10")).thenReturn(List.of("140.1"));
        assertThrows(org.openelisglobal.sample.exception.PatientCugPrefixException.class,
                () -> service.assignPatientPrefix("10", "8000", "42"));
        verify(prefixes, never()).claim(anyString(), anyString());
    }

    @Test
    public void legacyAssignmentWithoutPreviewStillUsesLatestHistory() {
        when(patientPrefixes.findPatientPrefix("10")).thenReturn(5000L);
        when(items.findCugCodesByPatientId("10")).thenReturn(List.of("5000.1"));
        assertEquals("5000.2", service.previewCugCode(request("10", List.of())).getCugCode());
        assertEquals("5000.2", service.previewCugCode(request("10", List.of())).getCugCode());
        when(items.findCugCodesByPatientId("10")).thenReturn(List.of("5000.1", "5000.2"));
        var first = sample(null);
        var second = sample(null);
        service.assignMissingCugCodes(List.of(first, second), "10", "42", "20");
        assertEquals("5000.3", first.item.getCugCode());
        assertEquals("5000.4", second.item.getCugCode());
        verify(reservations, never()).insertIfValueAvailable(any());
        verify(prefixes, never()).allocate(anyString());
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

    @Test
    public void strictPreviewKeepsDisplayedCugEvenIfLaterSuffixesExist() {
        when(patientPrefixes.findPatientPrefix("10")).thenReturn(5000L);
        when(items.findCugCodesByPatientId("10")).thenReturn(List.of("5000.1", "5000.3"));
        var sample = previewSample("5000.2");
        service.assignMissingCugCodes(List.of(sample), "10", "42", "20");
        assertEquals("5000.2", sample.item.getCugCode());
        verify(prefixes, never()).allocate(anyString());
        verify(prefixes, never()).lockAllocation();
        verify(reservations, never()).insertIfValueAvailable(any());
    }

    @Test
    public void usedPreviewIsRejectedWithoutIncreasingItsSuffix() {
        when(patientPrefixes.findPatientPrefix("10")).thenReturn(5000L);
        when(items.existsByCugCode("5000.1")).thenReturn(true);
        var sample = previewSample("5000.1");
        try {
            service.assignMissingCugCodes(List.of(sample), "10", "42", "20");
            fail("A used preview must not create another order");
        } catch (SampleCugConflictException expected) {
            assertEquals("sample.cug.conflict", expected.getMessage());
        }
        assertEquals("5000.1", sample.item.getCugCode());
        verify(prefixes, never()).allocate(anyString());
    }

    @Test
    public void duplicatePreviewsWithinOneOrderAreRejected() {
        when(patientPrefixes.findPatientPrefix("10")).thenReturn(5000L);
        try {
            service.assignMissingCugCodes(List.of(previewSample("5000.1"), previewSample("5000.1")), "10", "42", "20");
            fail();
        } catch (SampleCugConflictException expected) { }
    }

    @Test
    public void aPreviewCannotUseAnotherPatientsPrefix() {
        when(patientPrefixes.findPatientPrefix("10")).thenReturn(5000L);
        try {
            service.assignMissingCugCodes(List.of(previewSample("5001.1")), "10", "42", "20");
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("sample.cug.patient.prefix.mismatch", expected.getMessage());
        }
    }

    @Test
    public void missingPreviewCannotFallBackToAutomaticAssignment() {
        when(patientPrefixes.findPatientPrefix("10")).thenReturn(5000L);
        try {
            service.assignMissingCugCodes(List.of(previewSample(null)), "10", "42", "20");
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("sample.cug.required", expected.getMessage());
        }
        verify(prefixes, never()).allocate(anyString());
    }

    private SampleTestCollection previewSample(String code) {
        var sample = sample(code);
        sample.cugProvisional = true;
        return sample;
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
