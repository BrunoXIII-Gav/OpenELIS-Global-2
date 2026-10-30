package org.openelisglobal.sample.service;

import static org.junit.Assert.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Test;
import org.openelisglobal.sample.daoimpl.SampleCugPrefixDAOImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;

public class PatientCugPrefixLookupIntegrationTest {
    @Test
    public void exactPrefixFindsBoundPatientsWithoutOrdersOrSamples() throws Exception {
        String url = System.getenv("CUG_TEST_JDBC_URL");
        if (url != null) {
            if (!url.matches("jdbc:postgresql://(127[.]0[.]0[.]1|localhost):[0-9]+/cug_prefix_test")) {
                throw new IllegalArgumentException("Use a disposable localhost cug_prefix_test database");
            }
            run(url, "cugtest", "");
        } else {
            try (var container = new PostgreSQLContainer<>("postgres:14.4")) {
                container.start();
                run(container.getJdbcUrl(), container.getUsername(), container.getPassword());
            }
        }
    }

    private void run(String url, String user, String password) throws Exception {
        var support = new CugPrefixIntegrationTest();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.createStatement().execute("DROP SCHEMA IF EXISTS clinlims CASCADE; CREATE SCHEMA clinlims");
            connection.setSchema("clinlims");
            support.migration(connection, "liquibase/sample-cug-conflict-baseline.xml").update("");
            var fixture = new DatabaseConnection(connection, "clinlims");
            fixture.getConfig().setProperty(org.dbunit.database.DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                    new org.dbunit.ext.postgresql.PostgresqlDataTypeFactory());
            DatabaseOperation.INSERT.execute(fixture, new FlatXmlDataSetBuilder()
                    .build(getClass().getClassLoader().getResourceAsStream("testdata/patient-cug-prefix-lookup.xml")));
            connection.commit();

            try (var factory = support.factory(url, user, password); var em = factory.createEntityManager()) {
                var dao = new SampleCugPrefixDAOImpl();
                ReflectionTestUtils.setField(dao, "entityManager", em);
                em.getTransaction().begin();
                assertEquals(List.of(21), dao.findPatientIdsByPrefix(5415L));
                assertEquals(List.of(22), dao.findPatientIdsByPrefix(5416L));
                assertEquals(List.of(23), dao.findPatientIdsByPrefix(54150L));
                assertTrue(dao.findPatientIdsByPrefix(541L).isEmpty());
                assertTrue(dao.findPatientIdsByPrefix(9999L).isEmpty());
                em.getTransaction().commit();
            }

            assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample"));
            assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample_human"));
            assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample_item"));
            assertEquals(3, support.scalar(connection, "select count(*) from clinlims.patient_cug_prefix"));
        }
    }
}
