package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.openelisglobal.sample.dao.SampleCugPrefixDAO;
import org.openelisglobal.sample.valueholder.SampleCugCounter;

public class CugPrefixServiceTest {
    private SampleCugPrefixDAO dao;
    private AuditEventDAO audit;
    private CugPrefixService service;
    private SampleCugCounter counter;

    @Before
    public void setup() {
        dao = mock(SampleCugPrefixDAO.class);
        audit = mock(AuditEventDAO.class);
        service = new CugPrefixServiceImpl(dao, audit);
        counter = new SampleCugCounter();
        counter.setId("1");
        counter.setConfiguredStart(141L);
        counter.setNextCandidate(141L);
        when(dao.lockCounter()).thenReturn(counter);
        when(dao.occupiedRangeEnd(anyLong())).thenReturn(null);
        when(dao.get("1")).thenReturn(java.util.Optional.of(counter));
    }

    @Test
    public void acceptsMovingUpThenDownAndAuditsEverySettingChange() {
        for (String start : new String[] { "5000", "7000", "4000" }) {
            assertEquals(start, service.setStart(start, "42").getNextAvailablePrefix());
            assertEquals(Long.valueOf(start), counter.getNextCandidate());
        }
        var capture = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(3)).insert(capture.capture());
        assertEquals("7000", capture.getValue().getOldValue());
        assertEquals("4000", capture.getValue().getNewValue());
        assertEquals("42", capture.getValue().getActorId());
    }

    @Test
    public void jumpsTheEntireOccupiedRangeInOneLookupAndPersistsNextCandidate() {
        counter.setNextCandidate(5000L);
        when(dao.occupiedRangeEnd(5000L)).thenReturn(999999L);
        assertEquals(1000000L, service.allocate("42"));
        assertEquals(Long.valueOf(1000001), counter.getNextCandidate());
        verify(dao).occupiedRangeEnd(5000L);
        verify(dao).register(1000000L, "42");
    }

    @Test
    public void viewingSettingsDoesNotConsumeAPrefix() {
        assertEquals("141", service.getSettings().getNextAvailablePrefix());
        assertEquals(Long.valueOf(141), counter.getNextCandidate());
        verify(dao, never()).register(anyLong(), anyString());
    }

    @Test
    public void manualChoiceLocksAndRegistersExactlyThatPrefixWithoutMovingTheCursor() {
        assertEquals(5006L, service.claim("5006", "42"));
        var order = inOrder(dao);
        order.verify(dao).lockCounter();
        order.verify(dao).isAssigned(5006L);
        order.verify(dao).register(5006L, "42");
        assertEquals(Long.valueOf(141), counter.getNextCandidate());
    }

    @Test
    public void manualChoiceNeverSubstitutesAnOccupiedPrefix() {
        when(dao.isAssigned(5006L)).thenReturn(true);
        var error = assertThrows(org.openelisglobal.sample.exception.PatientCugPrefixException.class,
                () -> service.claim("5006", "42"));
        assertEquals("patient.cug.prefix.duplicate", error.getMessage());
        verify(dao, never()).register(anyLong(), anyString());
    }

    @Test
    public void invalidManualChoicesDoNotAllocateOrLock() {
        for (String raw : new String[] { null, "", "0", "-1", "5006.1", "05006", " 5006", "abc", "9223372036854775807",
                "999999999999999999999" }) {
            var error = assertThrows(org.openelisglobal.sample.exception.PatientCugPrefixException.class,
                    () -> service.claim(raw, "42"));
            assertEquals("patient.cug.prefix.invalid", error.getMessage());
        }
        verify(dao, never()).lockCounter();
        verify(dao, never()).register(anyLong(), anyString());
    }

    @Test
    public void rejectsInvalidAndOutOfRangeStartingPointsWithoutChangingTheCounter() {
        for (String start : new String[] { "0", "-1", "4.2", "", "abc", "9223372036854775807",
                "999999999999999999999" }) {
            try {
                service.setStart(start, "42");
                fail(start);
            } catch (IllegalArgumentException expected) {
                assertEquals("cug.prefix.error.invalid", expected.getMessage());
            }
        }
        verify(dao, never()).lockCounter();
        verifyZeroInteractions(audit);
    }

    @Test
    public void releaseChecksReferencesUnderTheAllocationLockWithoutRewindingCounter() {
        assertTrue(service.releaseIfUnused(5007));
        var order = inOrder(dao);
        order.verify(dao).lockCounter();
        order.verify(dao).isPrefixReferenced(eq(5007L), any());
        order.verify(dao).releasePrefix(5007L);
        assertEquals(Long.valueOf(141), counter.getNextCandidate());
    }

    @Test
    public void referencedPrefixIsNeverReleased() {
        when(dao.isPrefixReferenced(eq(5007L), any())).thenReturn(true);
        assertFalse(service.releaseIfUnused(5007));
        verify(dao, never()).releasePrefix(anyLong());
    }

    @Test
    public void handlesExhaustionWithoutOverflow() {
        counter.setNextCandidate(CugPrefixServiceImpl.MAX_PREFIX);
        when(dao.occupiedRangeEnd(CugPrefixServiceImpl.MAX_PREFIX)).thenReturn(Long.MAX_VALUE);
        assertNull(service.getSettings().getNextAvailablePrefix());
        try {
            service.allocate("42");
            fail();
        } catch (IllegalArgumentException expected) {
            assertEquals("cug.prefix.error.exhausted", expected.getMessage());
        }
        verify(dao, never()).register(anyLong(), anyString());
    }
}
