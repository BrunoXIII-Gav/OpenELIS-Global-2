package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Test;
import org.openelisglobal.patient.exception.PatientIdentifierConflict;
import org.openelisglobal.sample.daoimpl.SampleCugPrefixDAOImpl;
import org.openelisglobal.sample.daoimpl.SampleCugReservationDAOImpl;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

public class PatientIdentifierConcurrencyIntegrationTest {
    @Test
    public void documentsAreUniqueDuringConcurrentRegistrationsWithoutChangingHistoricalData() throws Exception {
        String url = System.getenv("CUG_TEST_JDBC_URL");
        if (url != null) {
            if (!url.matches("jdbc:postgresql://(127[.]0[.]0[.]1|localhost):[0-9]+/cug_prefix_test")) {
                throw new IllegalArgumentException("Use a disposable localhost cug_prefix_test database");
            }
            run(url, "cugtest", "");
        } else {
            try (var pg = new PostgreSQLContainer<>("postgres:14.4")) {
                pg.start();
                run(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
            }
        }
    }

    private void run(String url, String user, String password) throws Exception {
        var support = new CugPrefixIntegrationTest();
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.createStatement().execute("DROP SCHEMA IF EXISTS clinlims CASCADE; CREATE SCHEMA clinlims");
            connection.setSchema("clinlims");
            support.migration(connection, "liquibase/patient-identifier-baseline.xml").update("");
            var migration = support.migration(connection, "liquibase/3.4.x.x/098-patient-document-uniqueness.xml");
            migration.update("");
            migration.rollback(1, "");
            var dbunit = new DatabaseConnection(connection, "clinlims");
            dbunit.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                    new org.dbunit.ext.postgresql.PostgresqlDataTypeFactory());
            var data = new FlatXmlDataSetBuilder().setColumnSensing(true).build(
                    getClass().getClassLoader().getResourceAsStream("testdata/patient-identifier-concurrency.xml"));
            DatabaseOperation.INSERT.execute(dbunit, data);
            connection.commit();
            migration.update("");
            migration.rollback(1, "");
            migration.update("");
            assertEquals(2, support.scalar(connection,
                    "select count(*) from clinlims.patient where upper(btrim(national_id))='HIST-1'"));
            assertEquals(2, support.scalar(connection,
                    "select count(*) from clinlims.patient_identity where identity_data='OLD-ALT'"));
            try (var factory = support.factory(url, user, password)) {
                var manager = new JpaTransactionManager(factory);
                var tx = new TransactionTemplate(manager);
                var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
                var dao = new SampleCugPrefixDAOImpl();
                ReflectionTestUtils.setField(dao, "entityManager", em);
                var audit = new org.openelisglobal.audittrail.daoimpl.AuditEventDAOImpl();
                ReflectionTestUtils.setField(audit, "entityManager", em);
                CugPrefixService prefixes = support.proxy(new CugPrefixServiceImpl(dao, audit), manager,
                        CugPrefixService.class);
                var reservations = new SampleCugReservationDAOImpl();
                ReflectionTestUtils.setField(reservations, "entityManager", em);
                var target = new SampleCugServiceImpl();
                ReflectionTestUtils.setField(target, "prefixes", prefixes);
                ReflectionTestUtils.setField(target, "patientPrefixes", dao);
                ReflectionTestUtils.setField(target, "sampleCugReservationDAO", reservations);
                ReflectionTestUtils.setField(target, "sampleItemDAO", mock(SampleItemDAO.class));
                SampleCugService cugs = support.proxy(target, manager, SampleCugService.class);
                prefixes.setStart("5000", "42");
                var pool = Executors.newFixedThreadPool(4);
                try {
                    for (int type = 1; type <= 3; type++) {
                        int docType = type;
                        var gate = new CountDownLatch(1);
                        List<Future<String>> attempts = new ArrayList<>();
                        for (int contender = 0; contender < 2; contender++) {
                            int id = 100 + type * 10 + contender;
                            attempts.add(pool.submit(() -> {
                                gate.await();
                                try {
                                    return tx.execute(status -> {
                                        em.createNativeQuery(
                                                "insert into clinlims.patient(id,national_id) values (:id,:value)")
                                                .setParameter("id", id).setParameter("value", "PRIMARY-" + id)
                                                .executeUpdate();
                                        em.createNativeQuery(
                                                "insert into clinlims.patient_identity(id,patient_id,identity_type_id,identity_data) values (:id,:patient,:type,:value)")
                                                .setParameter("id", id).setParameter("patient", id)
                                                .setParameter("type", docType)
                                                .setParameter("value", id % 2 == 0 ? "DOCUMENT-" + docType
                                                        : " document-" + docType + " ")
                                                .executeUpdate();
                                        return cugs.ensurePatientPrefix(Integer.toString(id), "42");
                                    });
                                } catch (RuntimeException e) {
                                    if (!PatientIdentifierConflict.isDuplicate(e))
                                        throw e;
                                    return "DUPLICATE";
                                }
                            }));
                        }
                        gate.countDown();
                        Set<String> results = Set.of(attempts.get(0).get(10, TimeUnit.SECONDS),
                                attempts.get(1).get(10, TimeUnit.SECONDS));
                        assertTrue(results.contains("DUPLICATE"));
                        assertTrue(results.contains(Integer.toString(4999 + type)));
                    }
                    assertEquals(3, support.scalar(connection, "select count(*) from clinlims.patient_cug_prefix"));
                    assertEquals("5003", prefixes.getSettings().getNextAvailablePrefix());
                    // A primary identifier must conflict with another patient's optional document,
                    // even across types.
                    assertDuplicate(() -> tx.execute(status -> {
                        em.createNativeQuery("insert into clinlims.patient(id,national_id) values (200,'DOCUMENT-1')")
                                .executeUpdate();
                        return null;
                    }));
                    assertDuplicate(() -> tx.execute(status -> {
                        em.createNativeQuery("insert into clinlims.patient(id,national_id) values (201,'hist-1')")
                                .executeUpdate();
                        return null;
                    }));
                    tx.execute(status -> {
                        em.createNativeQuery("update clinlims.patient set national_id=' HIST-1 ' where id=10")
                                .executeUpdate();
                        em.createNativeQuery(
                                "update clinlims.patient_identity set identity_data=' old-alt ' where id=1")
                                .executeUpdate();
                        return null;
                    });
                    // The same patient's primary identifier and document may repeat each other.
                    tx.execute(status -> {
                        em.createNativeQuery("insert into clinlims.patient(id,national_id) values (202,'SAME-OWNER')")
                                .executeUpdate();
                        em.createNativeQuery(
                                "insert into clinlims.patient_identity(id,patient_id,identity_type_id,identity_data) values (202,202,1,'same-owner')")
                                .executeUpdate();
                        return null;
                    });
                    assertDuplicate(() -> tx.execute(status -> {
                        em.createNativeQuery("update clinlims.patient set national_id='DOCUMENT-1' where id=202")
                                .executeUpdate();
                        return null;
                    }));
                    assertDuplicate(() -> tx.execute(status -> {
                        em.createNativeQuery("update clinlims.patient_identity set patient_id=10 where id=202")
                                .executeUpdate();
                        return null;
                    }));
                    tx.execute(status -> {
                        em.createNativeQuery(
                                "insert into clinlims.patient_identity(id,patient_id,identity_type_id,identity_data) values (601,202,4,'SHARED-METADATA'),(602,10,4,'SHARED-METADATA')")
                                .executeUpdate();
                        em.createNativeQuery("insert into clinlims.patient(id,national_id) values (600,null),(601,' ')")
                                .executeUpdate();
                        return null;
                    });
                    // Unrelated documents proceed while a registration for another document remains
                    // open.
                    var held = new CountDownLatch(1);
                    var release = new CountDownLatch(1);
                    var first = pool.submit(() -> tx.execute(status -> {
                        em.createNativeQuery("insert into clinlims.patient(id,national_id) values (300,'HELD')")
                                .executeUpdate();
                        held.countDown();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS))
                                throw new AssertionError("Lock was not released");
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                        return null;
                    }));
                    try {
                        assertTrue(held.await(10, TimeUnit.SECONDS));
                        var independent = pool.submit(() -> tx.execute(status -> {
                            em.createNativeQuery(
                                    "insert into clinlims.patient(id,national_id) values (301,'INDEPENDENT')")
                                    .executeUpdate();
                            return cugs.ensurePatientPrefix("301", "42");
                        }));
                        assertEquals("5003", independent.get(3, TimeUnit.SECONDS));
                    } finally {
                        release.countDown();
                        first.get(10, TimeUnit.SECONDS);
                    }
                    // Reverting a patient and its CUG releases both its document and its prefix.
                    try {
                        tx.execute(status -> {
                            em.createNativeQuery("insert into clinlims.patient(id,national_id) values (400,'RETRY')")
                                    .executeUpdate();
                            assertEquals("5004", cugs.ensurePatientPrefix("400", "42"));
                            throw new IllegalStateException("rollback registration");
                        });
                        fail();
                    } catch (IllegalStateException expected) {
                        assertEquals("rollback registration", expected.getMessage());
                    }
                    tx.execute(status -> {
                        em.createNativeQuery("insert into clinlims.patient(id,national_id) values (401,'retry')")
                                .executeUpdate();
                        assertEquals("5004", cugs.ensurePatientPrefix("401", "42"));
                        return null;
                    });
                    // Configurable duplicate acceptance remains supported; switching it off
                    // protects new writes.
                    tx.execute(status -> {
                        em.createNativeQuery(
                                "update clinlims.site_information set value='true' where name='Allow duplicate national ids'")
                                .executeUpdate();
                        em.createNativeQuery(
                                "insert into clinlims.patient(id,national_id) values (500,'ALLOW'),(501,'allow')")
                                .executeUpdate();
                        em.createNativeQuery(
                                "update clinlims.site_information set value='false' where name='Allow duplicate national ids'")
                                .executeUpdate();
                        return null;
                    });
                    assertDuplicate(() -> tx.execute(status -> {
                        em.createNativeQuery("insert into clinlims.patient(id,national_id) values (502,'ALLOW')")
                                .executeUpdate();
                        return null;
                    }));
                    assertEquals(0, support.scalar(connection,
                            "select count(*) from clinlims.patient where id in (200,201,400,502)"));
                    assertEquals("5005", prefixes.getSettings().getNextAvailablePrefix());
                } finally {
                    pool.shutdownNow();
                }
            }
        }
    }

    private void assertDuplicate(Runnable operation) {
        try {
            operation.run();
            fail("Duplicate identifier must be rejected");
        } catch (RuntimeException e) {
            assertTrue("Unexpected failure: " + e, PatientIdentifierConflict.isDuplicate(e));
        }
    }
}
