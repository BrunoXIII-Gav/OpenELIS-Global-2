package org.openelisglobal.audittrail;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Test;
import org.testcontainers.containers.PostgreSQLContainer;

public class AuditTestFilterMigrationTest {
    @Test
    public void addsIndexWithoutRewritingHistoryAndProtectsSnapshotsDuringRollback() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:14.4")) {
            database.start();
            try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                    database.getPassword())) {
                var resources = new ClassLoaderResourceAccessor();
                new Liquibase("liquibase/audit-baseline.xml", resources, new JdbcConnection(connection)).update("");
                new Liquibase("liquibase/3.4.x.x/088-complete-audit-trail.xml", resources,
                        new JdbcConnection(connection)).update("");
                insert(connection, "testdata/audit-event.xml");
                connection.commit();
                var migration = new Liquibase("liquibase/3.4.x.x/089-audit-test-filter.xml", resources,
                        new JdbcConnection(connection));
                migration.update("");
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT old_value,new_value,test_id FROM clinlims.audit_event WHERE id='audit-test-event'")) {
                    assertTrue(rows.next());
                    assertEquals("Ana", rows.getString(1));
                    assertEquals("Luis", rows.getString(2));
                    assertNull(rows.getString(3));
                }
                try (var indexes = connection.getMetaData().getIndexInfo(null, "clinlims", "audit_event", false, false)) {
                    boolean found = false;
                    while (indexes.next())
                        found |= "audit_event_test_history_idx".equals(indexes.getString("INDEX_NAME"));
                    assertTrue(found);
                }
                // Existing ledger rows with no ownership snapshot allow clean rollback.
                migration.rollback(1, "");
                migration.update("");
                connection.setAutoCommit(false);
                insert(connection, "testdata/audit-test-snapshot.xml");
                connection.commit();
                try {
                    migration.rollback(1, "");
                    fail("Must not discard immutable test ownership");
                } catch (liquibase.exception.LiquibaseException expected) {
                    assertTrue(expected.toString().contains("Archive audit test ownership"));
                    connection.rollback();
                }
                try (var rows = connection.createStatement().executeQuery(
                        "SELECT test_id FROM clinlims.audit_event WHERE id='audit-test-snapshot'")) {
                    assertTrue(rows.next());
                    assertEquals("9201", rows.getString(1));
                }
            }
        }
    }

    private void insert(Connection connection, String resource) throws Exception {
        var dataset = new FlatXmlDataSetBuilder().build(getClass().getClassLoader().getResourceAsStream(resource));
        DatabaseOperation.INSERT.execute(new DatabaseConnection(connection, "clinlims"), dataset);
    }
}
