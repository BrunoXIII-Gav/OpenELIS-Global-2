package org.openelisglobal.test.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Test;
import org.openelisglobal.test.dao.TestCreationOriginDAO;
import org.springframework.test.util.ReflectionTestUtils;

public class TestCreationOriginServiceTest {
    @Test
    public void invalidOrRepeatedSelectionsCannotClassifyRecords() {
        var dao = mock(TestCreationOriginDAO.class);
        var service = new TestCreationOriginServiceImpl();
        ReflectionTestUtils.setField(service, "dao", dao);
        for (List<String> ids : List.of(List.<String>of(), List.of("1", "1"), List.of("invalid"))) {
            try {
                service.classify(ids, true, "17");
                fail("Invalid selection accepted");
            } catch (IllegalArgumentException expected) {
                assertEquals("testPackage.issue.selection", expected.getMessage());
            }
        }
        verifyZeroInteractions(dao);
    }

    @Test
    public void successfulClassificationRecordsTheAuthenticatedActor() {
        var dao = mock(TestCreationOriginDAO.class);
        var service = new TestCreationOriginServiceImpl();
        ReflectionTestUtils.setField(service, "dao", dao);
        when(dao.classify(List.of("2", "3"), true, "17")).thenReturn(2);
        assertEquals(2, service.classify(List.of("2", "3"), true, "17"));
        verify(dao).classify(List.of("2", "3"), true, "17");
    }
}
