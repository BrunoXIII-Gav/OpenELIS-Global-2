package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.*;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.hibernate.SessionFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.sample.daoimpl.SampleCugPrefixDAOImpl;
import org.openelisglobal.sample.daoimpl.SampleCugReservationDAOImpl;
import org.openelisglobal.sample.exception.PatientCugPrefixException;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

public class PatientManualCugIntegrationTest {
    private PostgreSQLContainer<?> container;
    private Connection connection;
    private SessionFactory factory;
    private CugPrefixService prefixes;
    private SampleCugService cugs;
    private TransactionTemplate tx;
    private jakarta.persistence.EntityManager em;

    @Before
    public void setup() throws Exception {
        String url = System.getenv("CUG_TEST_JDBC_URL");
        String user = "cugtest";
        String password = "";
        if (url != null) {
            if (!url.matches("jdbc:postgresql://(127[.]0[.]0[.]1|localhost):[0-9]+/cug_prefix_test")) {
                throw new IllegalArgumentException("Use a disposable localhost cug_prefix_test database");
            }
        } else {
            container = new PostgreSQLContainer<>("postgres:14.4");
            container.start();
            url = container.getJdbcUrl();
            user = container.getUsername();
            password = container.getPassword();
        }
        var support = new CugPrefixIntegrationTest();
        connection = DriverManager.getConnection(url, user, password);
        connection.createStatement().execute("DROP SCHEMA IF EXISTS clinlims CASCADE; CREATE SCHEMA clinlims");
        connection.setSchema("clinlims");
        support.migration(connection, "liquibase/cug-prefix-baseline.xml").update("");
        support.migration(connection, "liquibase/3.4.x.x/095-configurable-cug-prefix.xml").update("");
        support.migration(connection, "liquibase/3.4.x.x/097-patient-cug-prefix.xml").update("");
        var dbunit = new DatabaseConnection(connection, "clinlims");
        dbunit.getConfig().setProperty(org.dbunit.database.DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new org.dbunit.ext.postgresql.PostgresqlDataTypeFactory());
        DatabaseOperation.INSERT.execute(dbunit, new FlatXmlDataSetBuilder()
                .build(getClass().getClassLoader().getResourceAsStream("testdata/patient-cug-workflow.xml")));
        connection.commit();
        factory = support.factory(url, user, password);
        var manager = new JpaTransactionManager(factory);
        tx = new TransactionTemplate(manager);
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        var dao = new SampleCugPrefixDAOImpl();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        var audit = new org.openelisglobal.audittrail.daoimpl.AuditEventDAOImpl();
        ReflectionTestUtils.setField(audit, "entityManager", em);
        prefixes = support.proxy(new CugPrefixServiceImpl(dao, audit), manager, CugPrefixService.class);
        var reservations = new SampleCugReservationDAOImpl();
        ReflectionTestUtils.setField(reservations, "entityManager", em);
        var target = new SampleCugServiceImpl();
        ReflectionTestUtils.setField(target, "prefixes", prefixes);
        ReflectionTestUtils.setField(target, "patientPrefixes", dao);
        ReflectionTestUtils.setField(target, "sampleCugReservationDAO", reservations);
        ReflectionTestUtils.setField(target, "sampleItemDAO", mock(SampleItemDAO.class));
        cugs = support.proxy(target, manager, SampleCugService.class);
        prefixes.setStart("5006", "42");
    }

    @After
    public void close() throws Exception {
        if (factory != null)
            factory.close();
        if (connection != null)
            connection.close();
        if (container != null)
            container.stop();
    }

    @Test
    public void manualWinnerMakesAutomaticSaveUseTheNextFreePrefix() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var manual = pool.submit(() -> tx.execute(status -> {
                String result = cugs.assignPatientPrefix("10", "5006", "42");
                claimed.countDown();
                await(release);
                return result;
            }));
            assertTrue(claimed.await(10, TimeUnit.SECONDS));
            var automatic = pool.submit(() -> cugs.ensurePatientPrefix("11", "42"));
            release.countDown();
            assertEquals("5006", manual.get(10, TimeUnit.SECONDS));
            assertEquals("5007", automatic.get(10, TimeUnit.SECONDS));
            assertEquals(2, scalar("select count(*) from clinlims.patient_cug_prefix"));
            assertEquals("5006", cugs.getPatientPrefix("10"));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void automaticWinnerRejectsTheManualChoiceAndRollsBackTheNewPatient() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var claimed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try {
            var automatic = pool.submit(() -> tx.execute(status -> {
                String result = cugs.ensurePatientPrefix("10", "42");
                claimed.countDown();
                await(release);
                return result;
            }));
            assertTrue(claimed.await(10, TimeUnit.SECONDS));
            var manual = pool.submit(() -> tx.execute(status -> {
                em.createNativeQuery("insert into clinlims.patient(id) values (99)").executeUpdate();
                return cugs.assignPatientPrefix("99", "5006", "42");
            }));
            release.countDown();
            assertEquals("5006", automatic.get(10, TimeUnit.SECONDS));
            var failure = assertThrows(ExecutionException.class, () -> manual.get(10, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof PatientCugPrefixException);
            assertEquals(PatientCugPrefixException.OCCUPIED, failure.getCause().getMessage());
            assertEquals(0, scalar("select count(*) from clinlims.patient where id=99"));
            assertEquals(1, scalar("select count(*) from clinlims.patient_cug_prefix"));
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void simultaneousManualRequestsHaveOneWinnerAndNeverSubstituteANumber() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var requests = List.of("10", "11").stream().map(patient -> pool.submit(() -> {
                await(start);
                return cugs.assignPatientPrefix(patient, "5006", "42");
            })).toList();
            start.countDown();
            int accepted = 0;
            int rejected = 0;
            for (var request : requests) {
                try {
                    assertEquals("5006", request.get(10, TimeUnit.SECONDS));
                    accepted++;
                } catch (ExecutionException error) {
                    assertTrue(error.getCause() instanceof PatientCugPrefixException);
                    assertEquals(PatientCugPrefixException.OCCUPIED, error.getCause().getMessage());
                    rejected++;
                }
            }
            assertEquals(1, accepted);
            assertEquals(1, rejected);
            assertEquals(1, scalar("select count(*) from clinlims.patient_cug_prefix"));
            assertEquals(1, scalar("select count(*) from clinlims.sample_cug_prefix"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void failedRegistrationDoesNotConsumeTheManuallySelectedPrefix() throws Exception {
        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
            cugs.assignPatientPrefix("10", "5006", "42");
            throw new IllegalStateException("registration failed");
        }));
        assertEquals(0, scalar("select count(*) from clinlims.patient_cug_prefix"));
        assertEquals(0, scalar("select count(*) from clinlims.sample_cug_prefix"));
        assertEquals("5006", prefixes.getSettings().getNextAvailablePrefix());
        assertEquals("5006", cugs.assignPatientPrefix("11", "5006", "42"));
    }

    @Test
    public void anOccupiedPrefixWithoutAPatientBindingIsStillUnavailable() throws Exception {
        prefixes.register(5006, "42");
        assertThrows(PatientCugPrefixException.class, () -> cugs.assignPatientPrefix("10", "5006", "42"));
        assertEquals(0, scalar("select count(*) from clinlims.patient_cug_prefix"));
        assertEquals("5007", cugs.ensurePatientPrefix("11", "42"));
    }

    private long scalar(String query) throws Exception {
        return new CugPrefixIntegrationTest().scalar(connection, query);
    }

    private static void await(CountDownLatch gate) {
        try {
            if (!gate.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Timed out awaiting test gate");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
