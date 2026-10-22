package org.openelisglobal.sample.controller.rest;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.Test;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.sample.exception.SampleCugConflictException;

public class SampleCugConflictResponseTest {
    @Test
    public void displayedCugConflictReturns409WithLocalizedClientCode() {
        var response = new SamplePatientEntryRestController().handleCugConflict(new SampleCugConflictException());
        assertEquals(409, response.getStatusCode().value());
        assertEquals("sample.cug.conflict", response.getHeaders().getFirst("X-OpenELIS-Error-Code"));
        assertEquals(Map.of("code", "sample.cug.conflict"), response.getBody());
    }

    @Test
    public void nestedDatabaseUniqueIndexViolationIsAlsoReportedAsConflict() {
        var sql = new SQLException("duplicate CUG", "23505");
        var constraint = new ConstraintViolationException("conflict", sql, "idx_sample_item_cug_code_unique");
        var error = new LIMSRuntimeException("DAO insert failed", constraint);
        var response = new SamplePatientEntryRestController().handleCugConflict(error);
        assertEquals(409, response.getStatusCode().value());
        assertTrue(SampleCugConflictException.isConflict(
                new RuntimeException(new SQLException("constraint \"idx_sample_item_cug_code_unique\"", "23505"))));
    }

    @Test
    public void unrelatedErrorsAreNotReportedAsCugConflicts() {
        var error = new ConstraintViolationException("other key", new SQLException("other key", "23505"),
                "sample_pkey");
        assertFalse(SampleCugConflictException.isConflict(error));
        try {
            new SamplePatientEntryRestController().handleCugConflict(error);
            fail();
        } catch (RuntimeException actual) {
            assertSame(error, actual);
        }
    }
}
