package org.openelisglobal.testconfiguration;

import static org.junit.Assert.*;

import java.sql.DriverManager;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

public class TestSectionOriginMigrationTest {
    @Test
    public void migrationUsesReceiptsAndNeverGuessesLegacyOriginOrChangesUnits() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:14.4")) {
            database.start();
            try (var connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                    database.getPassword())) {
                var resources = new ClassLoaderResourceAccessor();
                new Liquibase("liquibase/test-section-origin-baseline.xml", resources, new JdbcConnection(connection))
                        .update("");
                var fixtures = new FlatXmlDataSetBuilder()
                        .build(getClass().getClassLoader().getResourceAsStream("testdata/test-section-origin.xml"));
                DatabaseOperation.INSERT.execute(new DatabaseConnection(connection, "clinlims"), fixtures);
                connection.commit();
                var migration = new Liquibase("liquibase/3.4.x.x/092-test-section-creation-origin.xml", resources,
                        new JdbcConnection(connection));
                migration.update("");
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT id, name, creation_origin, creation_origin_by FROM clinlims.test_section ORDER BY id")) {
                    assertTrue(rows.next());
                    assertEquals("Historical unit", rows.getString("name"));
                    assertEquals("UNKNOWN", rows.getString("creation_origin"));
                    assertTrue(rows.next());
                    assertEquals("Renamed imported unit", rows.getString("name"));
                    assertEquals("USER", rows.getString("creation_origin"));
                    assertEquals("117", rows.getString("creation_origin_by"));
                }
                try (var statement = connection.createStatement()) {
                    statement.execute(
                            "INSERT INTO clinlims.test_section(id, name) VALUES (3, 'Future SQL initialization')");
                    try (var rows = statement
                            .executeQuery("SELECT creation_origin FROM clinlims.test_section WHERE id = 3")) {
                        rows.next();
                        assertEquals("SYSTEM", rows.getString(1));
                    }
                }
                connection.commit();
                migration.rollback(1, "");
                try (var rows = connection.createStatement()
                        .executeQuery("SELECT count(*) FROM clinlims.test_section")) {
                    rows.next();
                    assertEquals(3, rows.getInt(1));
                }
                migration.update("");
            }
        }
    }
}
