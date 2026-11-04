package org.openelisglobal.menu;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

public class TraceabilityMenuMigrationTest {
    @Test
    public void movesExistingEntryOutOfDisabledReportsAndSupportsRollbackAndReapply() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:14.4")) {
            database.start();
            try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                    database.getPassword())) {
                var resources = new ClassLoaderResourceAccessor();
                new Liquibase("liquibase/traceability-menu-baseline.xml", resources, new JdbcConnection(connection))
                        .update("");
                var dataset = new FlatXmlDataSetBuilder().setColumnSensing(true)
                        .build(getClass().getClassLoader().getResourceAsStream("testdata/traceability-menu.xml"));
                var fixtureConnection = new DatabaseConnection(connection, "clinlims");
                fixtureConnection.getConfig().setProperty(DatabaseConfig.FEATURE_ALLOW_EMPTY_FIELDS, true);
                DatabaseOperation.INSERT.execute(fixtureConnection, dataset);
                connection.commit();
                var migration = new Liquibase("liquibase/3.4.x.x/090-add-traceability-menu.xml", resources,
                        new JdbcConnection(connection));
                migration.update("");
                assertMovedEntry(connection);
                migration.update("");
                assertMovedEntry(connection);

                migration.rollback(1, "");
                try (var rows = connection.createStatement()
                        .executeQuery("SELECT parent_id, presentation_order FROM clinlims.menu WHERE id = 25")) {
                    assertTrue(rows.next());
                    assertEquals(20, rows.getInt(1));
                    assertEquals(70, rows.getInt(2));
                }
                try (var rows = connection.createStatement()
                        .executeQuery("SELECT COUNT(*) FROM clinlims.menu WHERE element_id = 'menu_traceability'")) {
                    rows.next();
                    assertEquals(0, rows.getInt(1));
                }
                migration.update("");
                assertMovedEntry(connection);
            }
        }
    }

    private void assertMovedEntry(Connection connection) throws Exception {
        try (var rows = connection.createStatement().executeQuery(
                "SELECT child.id, child.action_url, child.display_key, child.is_active, parent.element_id, "
                        + "parent.parent_id, parent.is_active, parent.display_key FROM clinlims.menu child "
                        + "JOIN clinlims.menu parent ON parent.id = child.parent_id "
                        + "WHERE child.element_id = 'menu_reports_auditTrail'")) {
            assertTrue(rows.next());
            assertEquals(25, rows.getInt(1));
            assertEquals("/AuditTrailReport", rows.getString(2));
            assertEquals("reports.auditTrail", rows.getString(3));
            assertTrue(rows.getBoolean(4));
            assertEquals("menu_traceability", rows.getString(5));
            assertNull(rows.getObject(6));
            assertTrue(rows.getBoolean(7));
            assertEquals("banner.menu.traceability", rows.getString(8));
            assertFalse(rows.next());
        }
        try (var rows = connection.createStatement()
                .executeQuery("SELECT COUNT(*) FROM clinlims.menu WHERE element_id IN "
                        + "('menu_reports', 'menu_reports_routine', 'menu_reports_management') AND is_active")) {
            rows.next();
            assertEquals(0, rows.getInt(1));
        }
    }
}
