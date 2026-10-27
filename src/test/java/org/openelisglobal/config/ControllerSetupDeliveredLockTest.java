package org.openelisglobal.config;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Map;
import org.junit.Test;
import org.springframework.http.HttpStatus;

public class ControllerSetupDeliveredLockTest {
    @Test
    public void deliveredLockFromTheDatabaseBecomesAConflict() {
        var wrapped = new RuntimeException("could not execute statement",
                new SQLException("ERROR: analysis.delivered.locked"));
        var response = ControllerSetup.deliveredLockResponse(wrapped).orElseThrow();
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(Map.of("error", "analysis.delivered.locked"), response.getBody());
    }

    @Test
    public void otherFailuresKeepTheGenericHandling() {
        assertTrue(ControllerSetup.deliveredLockResponse(new RuntimeException("boom")).isEmpty());
    }
}
