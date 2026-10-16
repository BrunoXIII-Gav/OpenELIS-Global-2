package org.openelisglobal.test.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Test;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.test.dao.TestSectionDAO;
import org.openelisglobal.test.valueholder.TestSection;
import org.springframework.test.util.ReflectionTestUtils;

public class TestSectionOriginTest {
    private final TestSectionDAO dao = mock(TestSectionDAO.class);
    private final TestSectionServiceImpl service = new TestSectionServiceImpl();

    public TestSectionOriginTest() {
        ReflectionTestUtils.setField(service, "baseObjectDAO", dao);
    }

    @Test
    public void userCreatedFilterCannotAddUnauthorizedOrInactiveUnits() {
        when(dao.getUserCreatedTestSectionIds()).thenReturn(List.of("2", "3"));
        var allowed = new IdValuePair("2", "My unit");
        assertEquals(List.of(allowed), service.filterUserCreatedTestSections(
                List.of(new IdValuePair("1", "Seeded unit"), allowed, new IdValuePair("4", "CSV unit"))));
        assertTrue(service.filterUserCreatedTestSections(List.of()).isEmpty());
    }

    @Test
    public void legacyClassificationRecordsAuthenticatedActorAndPreservesOtherFields() {
        var section = new TestSection();
        section.setId("27");
        section.setTestSectionName("Renamed unit");
        section.setIsActive("Y");
        section.setLastupdated(java.sql.Timestamp.valueOf("2026-10-15 10:00:00"));
        section.setCreationOrigin(TestSection.ORIGIN_UNKNOWN);
        when(dao.getTestSectionForUpdate("27")).thenReturn(section);
        assertEquals(1, service.classifyTestSectionOrigins(List.of("27"), true, "117"));
        assertEquals(TestSection.ORIGIN_USER, section.getCreationOrigin());
        assertEquals("117", section.getCreationOriginBy());
        assertEquals("117", section.getSysUserId());
        assertNotNull(section.getCreationOriginAt());
        assertEquals("Renamed unit", section.getTestSectionName());
        assertEquals("Y", section.getIsActive());
        verify(dao).update(section);
    }

    @Test
    public void classifiedOriginsCannotBeOverwrittenAndInvalidSelectionsAreRejected() {
        var section = new TestSection();
        section.setId("27");
        section.setCreationOrigin(TestSection.ORIGIN_SYSTEM);
        when(dao.getTestSectionForUpdate("27")).thenReturn(section);
        for (List<String> ids : List.of(List.<String>of(), List.of("27", "27"), List.of("bad"), List.of("27"))) {
            try {
                service.classifyTestSectionOrigins(ids, true, "117");
                fail("Invalid classification accepted");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().startsWith("testPackage.issue."));
            }
        }
        verify(dao, never()).update(any(TestSection.class));
    }
}
