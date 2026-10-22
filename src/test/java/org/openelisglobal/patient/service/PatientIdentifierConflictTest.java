package org.openelisglobal.patient.service;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.Test;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.patient.controller.rest.PatientManagementRestController;
import org.openelisglobal.patient.exception.PatientIdentifierConflict;

public class PatientIdentifierConflictTest {
    @Test
    public void nestedDatabaseConflictReturns409WithoutExposingDocumentOrPatient() {
        var sql = new SQLException("Patient identifier already exists", "23505");
        var constraint = new ConstraintViolationException("conflict", sql, "uq_patient_document_identifier");
        var error = new LIMSRuntimeException("DAO insert failed", constraint);
        assertTrue(PatientIdentifierConflict.isDuplicate(error));
        var response = new PatientManagementRestController().handleRegistrationFailure(error);
        assertEquals(409, response.getStatusCode().value());
        assertEquals(Map.of("code", "patient.identifier.duplicate"), response.getBody());
    }

    @Test
    public void unrelatedDatabaseErrorsAreNotMisreportedAsDuplicateDocuments() {
        var error = new ConstraintViolationException("other key", new SQLException("other key", "23505"),
                "patient_pkey");
        assertFalse(PatientIdentifierConflict.isDuplicate(error));
        try {
            new PatientManagementRestController().handleRegistrationFailure(error);
            fail();
        } catch (RuntimeException actual) {
            assertSame(error, actual);
        }
    }

    @Test
    public void plainSqlCauseIsRecognizedOnlyForTheDocumentConstraint() {
        assertTrue(PatientIdentifierConflict.isDuplicate(
                new RuntimeException(new SQLException("constraint uq_patient_document_identifier", "23505"))));
        assertFalse(PatientIdentifierConflict.isDuplicate(new SQLException("unrelated error", "23505")));
    }
}
