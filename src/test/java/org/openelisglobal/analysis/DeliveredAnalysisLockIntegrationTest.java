package org.openelisglobal.analysis;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A delivered result closes its cycle: every write path (screens, analyzers,
 * scripts) is stopped by the database, while delivery bookkeeping stays
 * editable and an amendment is a separate analysis.
 */
public class DeliveredAnalysisLockIntegrationTest {
    private static final String LOCKED = "analysis.delivered.locked";

    @Test
    public void deliveredAnalysisRejectsResultChangesButAllowsDeliveryBookkeeping() throws Exception {
        try (var container = new PostgreSQLContainer<>("postgres:14.4")) {
            container.start();
            try (Connection connection = DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(),
                    container.getPassword())) {
                connection.createStatement().execute("CREATE SCHEMA clinlims");
                connection.setSchema("clinlims");
                var database = DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                database.setDefaultSchemaName("clinlims");
                new Liquibase("liquibase/delivered-analysis-baseline.xml", new ClassLoaderResourceAccessor(), database)
                        .update("");
                connection.setAutoCommit(true);
                run(connection);
            }
        }
    }

    private void run(Connection c) throws SQLException {
        exec(c, "INSERT INTO analysis(id, status_id, validation_date, lastupdated) VALUES (1, 6, '2026-10-15', now())");
        exec(c, "INSERT INTO result(id, analysis_id, value, lastupdated) VALUES (10, 1, '5', now())");
        exec(c, "INSERT INTO analysis_additional_field_value(id, analysis_id, field_value) VALUES (20, 1, 'a')");

        // Before delivery everything stays editable.
        exec(c, "UPDATE result SET value = '6' WHERE id = 10");
        exec(c, "UPDATE analysis SET status_id = 6 WHERE id = 1");

        exec(c, "UPDATE analysis SET delivered_date = '2026-10-16', delivered_by = '7', lastupdated = now() WHERE id = 1");

        for (String sql : new String[] { "UPDATE result SET value = '7' WHERE id = 10",
                "INSERT INTO result(id, analysis_id, value) VALUES (11, 1, '1')", "DELETE FROM result WHERE id = 10",
                "UPDATE analysis_additional_field_value SET field_value = 'b' WHERE id = 20",
                "INSERT INTO analysis_additional_field_value(id, analysis_id, field_value) VALUES (21, 1, 'c')",
                "UPDATE analysis SET status_id = 4 WHERE id = 1",
                "UPDATE analysis SET validation_date = '2026-10-17' WHERE id = 1",
                "DELETE FROM analysis WHERE id = 1" }) {
            assertLocked(c, sql);
        }

        // Technical touches, reprints and delivery corrections are not result changes.
        exec(c, "UPDATE result SET lastupdated = now(), sys_user_id = '8' WHERE id = 10");
        exec(c, "UPDATE analysis SET corrected = false, printed_date = '2026-10-18', lastupdated = now(), "
                + "sys_user_id = '8' WHERE id = 1");
        exec(c, "UPDATE analysis SET delivered_date = '2026-10-17', delivery_correction_reason = 'typo' WHERE id = 1");

        // An amendment is a separate analysis with its own cycle.
        exec(c, "INSERT INTO analysis(id, status_id, amends_analysis_id, amendment_reason) VALUES (2, 1, 1, 'error')");
        exec(c, "INSERT INTO result(id, analysis_id, value) VALUES (12, 2, '9')");
        assertFails(c, "INSERT INTO analysis(id, status_id, amends_analysis_id) VALUES (3, 1, 1)", "");
        assertFails(c, "INSERT INTO analysis(id, status_id, amends_analysis_id) VALUES (4, 1, 999)", "");

        // An administrator reopening the delivery unlocks the analysis.
        exec(c, "UPDATE analysis SET delivered_date = NULL, delivered_by = NULL, "
                + "delivery_correction_reason = 'reopened' WHERE id = 1");
        exec(c, "UPDATE result SET value = '8' WHERE id = 10");

        // Training cleanup may wipe delivered data, only in a training installation
        // and only inside its own transaction.
        exec(c, "UPDATE analysis SET delivered_date = '2026-10-16' WHERE id = 1");
        assertFails(c, "SELECT clinlims.allow_training_cleanup_of_delivered()", "training installation");
        exec(c, "INSERT INTO site_information(name, value) VALUES ('TrainingInstallation', 'true')");
        c.setAutoCommit(false);
        exec(c, "SELECT clinlims.allow_training_cleanup_of_delivered()");
        exec(c, "UPDATE analysis SET amends_analysis_id = NULL");
        exec(c, "DELETE FROM result");
        exec(c, "DELETE FROM analysis_additional_field_value");
        exec(c, "DELETE FROM analysis");
        c.rollback();
        c.setAutoCommit(true);
        assertLocked(c, "DELETE FROM result WHERE id = 10");
    }

    private void exec(Connection c, String sql) throws SQLException {
        c.createStatement().execute(sql);
    }

    private void assertLocked(Connection c, String sql) {
        assertFails(c, sql, LOCKED);
    }

    private void assertFails(Connection c, String sql, String message) {
        try {
            exec(c, sql);
            fail("Expected failure: " + sql);
        } catch (SQLException e) {
            assertTrue(sql + " -> " + e.getMessage(), e.getMessage().contains(message));
        }
    }
}
