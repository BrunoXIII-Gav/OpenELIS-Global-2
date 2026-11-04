package org.openelisglobal.audittrail;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Properties;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Assume;
import org.junit.Test;

public class AuditMigrationTest {
    @Test
    public void upgradesExistingApprovalsAndSupportsCleanRollback() throws Exception {
        String url = System.getProperty("audit.migration.url", System.getProperty("audit.test.url"));
        Assume.assumeTrue("Provide an isolated audit test database", url != null);
        if (!url.matches("jdbc:postgresql://[^/]+/oe_audit_[a-zA-Z0-9_]+"))
            throw new IllegalArgumentException("Test database required");
        Properties environment = new Properties();
        if (Files.exists(Path.of(".env"))) {
            try (var reader = Files.newBufferedReader(Path.of(".env"))) {
                environment.load(reader);
            }
        }
        try (Connection connection = DriverManager.getConnection(url, System.getProperty("audit.test.user", "clinlims"),
                environment.getProperty("OE_DB_PASSWORD", "clinlims"))) {
            connection.setSchema("public");
            var resources = new ClassLoaderResourceAccessor();
            Liquibase baseline = new Liquibase("liquibase/audit-baseline.xml", resources,
                    new JdbcConnection(connection));
            Liquibase migration = new Liquibase("liquibase/3.4.x.x/088-complete-audit-trail.xml", resources,
                    new JdbcConnection(connection));
            baseline.update("");
            try {
                var dataset = new FlatXmlDataSetBuilder()
                        .build(getClass().getClassLoader().getResourceAsStream("testdata/audit-approvals.xml"));
                DatabaseOperation.INSERT.execute(new DatabaseConnection(connection, "clinlims"), dataset);
                connection.commit();
                migration.update("");
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT COUNT(*) FROM clinlims.analysis_validation_approval WHERE revoked_at IS NULL")) {
                    rows.next();
                    assertEquals(2, rows.getInt(1));
                }
                try (var tables = connection.getMetaData().getTables(null, "clinlims", "audit_event", null)) {
                    assertTrue(tables.next());
                }
                var events = new FlatXmlDataSetBuilder()
                        .build(getClass().getClassLoader().getResourceAsStream("testdata/audit-event.xml"));
                DatabaseOperation.INSERT.execute(new DatabaseConnection(connection, "clinlims"), events);
                try (var statement = connection.createStatement()) {
                    try {
                        statement.executeUpdate(
                                "UPDATE clinlims.audit_event SET new_value = 'tampered' WHERE id = 'audit-test-event'");
                        fail("Database must reject audit changes");
                    } catch (java.sql.SQLException expected) {
                        assertTrue(expected.getMessage().contains("cannot be changed or deleted"));
                    }
                }
                connection.rollback();
                try (var statement = connection.createStatement()) {
                    try {
                        statement.executeUpdate("TRUNCATE TABLE clinlims.audit_event");
                        fail("Database must reject audit truncation");
                    } catch (java.sql.SQLException expected) {
                        assertTrue(expected.getMessage().contains("cannot be changed or deleted"));
                    }
                }
                connection.rollback();
                migration.rollback(2, "");
                // Exercise the empty-data upgrade too, then restore the baseline.
                DatabaseOperation.DELETE_ALL.execute(new DatabaseConnection(connection, "clinlims"), dataset);
                connection.commit();
                migration.update("");
                migration.rollback(2, "");
            } catch (Exception | AssertionError failure) {
                throw failure;
            }
            baseline.rollback(1, "");
        }
    }
}
