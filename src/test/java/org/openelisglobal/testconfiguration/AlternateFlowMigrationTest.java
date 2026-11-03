package org.openelisglobal.testconfiguration;

import static org.junit.Assert.*;

import java.sql.DriverManager;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.Assume;
import org.junit.Test;

/**
 * Runs only against a disposable local database, never a deployment database.
 */
public class AlternateFlowMigrationTest {
    @Test
    public void emptyDatabaseDefaultsOffAndSupportsRollback() throws Exception {
        migrate(false);
    }

    @Test
    public void existingParentTestKeepsItsFlagAndStartsWithExceptionOff() throws Exception {
        migrate(true);
    }

    private void migrate(boolean populated) throws Exception {
        String url = System.getenv("TEST_ALTERNATE_FLOW_DATABASE_URL");
        Assume.assumeTrue("Set TEST_ALTERNATE_FLOW_DATABASE_URL to a disposable local alternateflow_test database",
                url != null);
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/alternateflow_test"))
            throw new IllegalArgumentException("Disposable local database required");
        try (var connection = DriverManager.getConnection(url, "postgres", "")) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection) {
                        @Override
                        public void close() {
                            // The outer connection owns its lifetime.
                        }
                    });
            try (var baseline = new Liquibase("test-alternate-flow-baseline.xml", new ClassLoaderResourceAccessor(),
                    database);
                    var migration = new Liquibase("liquibase/3.4.x.x/104-test-alternate-flow-processing.xml",
                            new ClassLoaderResourceAccessor(), database)) {
                baseline.update(new Contexts(populated ? "populated" : "empty"));
                migration.update(new Contexts());
                baseline.update(new Contexts("after"));
                try (var query = connection.createStatement();
                        var rows = query.executeQuery(
                                "SELECT id, allow_processing_in_alternate_flow, skip_validation_when_parent_complete FROM test ORDER BY id")) {
                    int count = 0;
                    while (rows.next()) {
                        assertFalse(rows.getBoolean(2));
                        assertFalse(rows.wasNull());
                        assertEquals(rows.getInt(1) == 1, rows.getBoolean(3));
                        count++;
                    }
                    assertEquals(populated ? 2 : 1, count);
                }
                migration.rollback(1, "");
                try (var columns = connection.getMetaData().getColumns(null, "public", "test",
                        "allow_processing_in_alternate_flow")) {
                    assertFalse(columns.next());
                }
                baseline.rollback(populated ? 3 : 2, "");
            }
        }
    }
}
