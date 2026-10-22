package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.ReplacementDataSet;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.hibernate.SessionFactory;
import org.junit.*;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.daoimpl.SampleCugPrefixDAOImpl;
import org.openelisglobal.sample.daoimpl.SampleCugReservationDAOImpl;
import org.openelisglobal.sample.exception.PatientCugPrefixException;
import org.openelisglobal.sampleitem.daoimpl.SampleItemDAOImpl;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real mappings, row locks, unique CUG index, audit and transactional rollback.
 */
public class PatientPrefixChangeIntegrationTest {
    private PostgreSQLContainer<?> container;
    private Connection connection;
    private SessionFactory factory;
    private SampleCugService cugs;
    private CugPrefixService prefixes;
    private SampleItemDAOImpl items;
    private TransactionTemplate tx;
    private jakarta.persistence.EntityManager em;

    @Before
    public void setup() throws Exception {
        String url = System.getenv("CUG_TEST_JDBC_URL"), user = "cugtest", password = "";
        if (url != null) {
            if (!url.matches("jdbc:postgresql://(127[.]0[.]0[.]1|localhost):[0-9]+/cug_prefix_test"))
                throw new IllegalArgumentException("Use a disposable localhost cug_prefix_test database");
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
        support.migration(connection, "liquibase/patient-prefix-change-baseline.xml").update("");
        var dbunit = new DatabaseConnection(connection, "clinlims");
        dbunit.getConfig().setProperty(org.dbunit.database.DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                new org.dbunit.ext.postgresql.PostgresqlDataTypeFactory());
        var fixture = new ReplacementDataSet(new FlatXmlDataSetBuilder().setColumnSensing(true)
                .build(getClass().getClassLoader().getResourceAsStream("testdata/patient-prefix-change.xml")));
        fixture.addReplacementObject("[NULL]", null);
        DatabaseOperation.INSERT.execute(dbunit, fixture);
        connection.commit();
        factory = support.factory(url, user, password, "hibernate/order-accession-sample.hbm.xml",
                "hibernate/patient-prefix-change.hbm.xml");
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
        items = spy(new SampleItemDAOImpl());
        ReflectionTestUtils.setField(items, "entityManager", em);
        var target = new SampleCugServiceImpl();
        ReflectionTestUtils.setField(target, "prefixes", prefixes);
        ReflectionTestUtils.setField(target, "patientPrefixes", dao);
        ReflectionTestUtils.setField(target, "sampleCugReservationDAO", reservations);
        ReflectionTestUtils.setField(target, "sampleItemDAO", items);
        ReflectionTestUtils.setField(target, "auditEvents", audit);
        cugs = support.proxy(target, manager, SampleCugService.class);
        assertEquals("5004", cugs.ensurePatientPrefix("10", "42"));
        assertEquals("6000", cugs.ensurePatientPrefix("11", "42"));
        prefixes.setStart("8000", "42");
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
    public void renamesAllOrdersPreservesSuffixesAndAuditsEveryChange() throws Exception {
        assertEquals("8000", cugs.changePatientPrefix("10", "5004", "8000", "42"));
        assertEquals("8000", cugs.getPatientPrefix("10"));
        assertEquals(List.of("8000.1", "8000.2"), tx.execute(s -> items.findCugCodesByPatientId("10")));
        assertEquals(1, scalar("select count(*) from clinlims.sample_item where id=3 and cug_code is null"));
        assertEquals(1, scalar("select count(*) from clinlims.sample_item where id=4 and cug_code='6000.1'"));
        assertEquals(0, scalar("select count(*) from clinlims.sample_cug_prefix where prefix=5004"));
        assertEquals(0, scalar("select count(*) from clinlims.sample_item where cug_code='5004.2'"));
        assertEquals(1, scalar("select count(*) from clinlims.sample_item where samp_id=21 and cug_code='8000.2'"));
        assertEquals(3, scalar(
                "select count(*) from clinlims.audit_event where source='/rest/PatientManagement' and patient_id='10' and actor_id='42'"));
        assertEquals(1, scalar(
                "select count(distinct operation_id) from clinlims.audit_event where source='/rest/PatientManagement'"));
        assertEquals(2, scalar("select count(*) from clinlims.audit_event where source='/rest/PatientManagement' "
                + "and module='sampleitem' and sample_item_id in ('1','2') and sample_id in ('20','21')"));
        assertEquals("8001", prefixes.getSettings().getNextAvailablePrefix());
    }

    @Test
    public void canChange5007ToUnused5006ForPatientWithoutOrders() throws Exception {
        assertEquals("5007", cugs.assignPatientPrefix("12", "5007", "42"));
        assertFalse(prefixes.isAssigned(5006));
        assertEquals("5006", cugs.changePatientPrefix("12", "5007", "5006", "42"));
        assertEquals("5006", cugs.getPatientPrefix("12"));
        assertEquals(1, scalar("select count(*) from clinlims.patient_cug_prefix where patient_id=12 and prefix=5006"));
    }

    @Test
    public void canChange5007ToUnused5006PreservingSampleSuffixes() {
        assertEquals("5007", cugs.changePatientPrefix("10", "5004", "5007", "42"));
        assertFalse(prefixes.isAssigned(5006));
        assertEquals("5006", cugs.changePatientPrefix("10", "5007", "5006", "42"));
        assertEquals(List.of("5006.1", "5006.2"), tx.execute(s -> items.findCugCodesByPatientId("10")));
        assertEquals("5006", cugs.getPatientPrefix("10"));
        assertFalse(prefixes.isAssigned(5007));
        assertEquals("5007", cugs.assignPatientPrefix("12", "5007", "42"));
        assertEquals("sample.cug.patient.prefix.mismatch",
                assertThrows(IllegalArgumentException.class, () -> cugs.lockPatientAndCheckPrefix("10", "5007"))
                        .getMessage());
    }

    @Test
    public void patientWithoutOrdersCanChangeItsPrefixAndTheAllocatorSkipsTheManualChoice() throws Exception {
        assertEquals("8000", cugs.ensurePatientPrefix("12", "42"));
        assertEquals("8001", cugs.changePatientPrefix("12", "8000", "8001", "42"));
        assertEquals("8002", prefixes.getSettings().getNextAvailablePrefix());
        assertEquals(0, scalar("select count(*) from clinlims.sample_cug_reservation where patient_id=12"));
        assertEquals(1, scalar(
                "select count(*) from clinlims.audit_event where source='/rest/PatientManagement' and patient_id='12'"));
    }

    @Test
    public void conflictsInvalidInputAndStaleSnapshotsLeaveEverythingUnchanged() throws Exception {
        for (String invalid : List.of("0", "08", "8000.3", "x", "9223372036854775807")) {
            assertEquals(PatientCugPrefixException.INVALID, assertThrows(PatientCugPrefixException.class,
                    () -> cugs.changePatientPrefix("10", "5004", invalid, "42")).getMessage());
        }
        assertEquals(PatientCugPrefixException.OCCUPIED, assertThrows(PatientCugPrefixException.class,
                () -> cugs.changePatientPrefix("10", "5004", "6000", "42")).getMessage());
        assertEquals(PatientCugPrefixException.STALE, assertThrows(PatientCugPrefixException.class,
                () -> cugs.changePatientPrefix("10", "5003", "8000", "42")).getMessage());
        unchanged();
    }

    @Test
    public void inconsistentLegacySamplesAbortInsteadOfSilentlyRenumberingThem() throws Exception {
        tx.execute(s -> {
            em.createNativeQuery("update clinlims.sample_item set cug_code='7000.3' where id=2").executeUpdate();
            return null;
        });
        assertEquals(PatientCugPrefixException.INCONSISTENT, assertThrows(PatientCugPrefixException.class,
                () -> cugs.changePatientPrefix("10", "5004", "8000", "42")).getMessage());
        assertEquals("5004", cugs.getPatientPrefix("10"));
        assertEquals(0, scalar("select count(*) from clinlims.sample_cug_prefix where prefix=8000"));
    }

    @Test
    public void failureAfterFirstSampleUpdateRollsBackSamplesRegistryBindingAndAudit() throws Exception {
        var updates = new AtomicInteger();
        doAnswer(call -> {
            if (updates.incrementAndGet() == 2)
                throw new IllegalStateException("forced failure");
            return call.callRealMethod();
        }).when(items).update(any(SampleItem.class));
        assertThrows(IllegalStateException.class, () -> cugs.changePatientPrefix("10", "5004", "8000", "42"));
        assertEquals(2, updates.get());
        unchanged();
    }

    @Test
    public void simultaneousChangesHaveOneWinnerAndOneStaleRequest() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var gate = new CountDownLatch(1);
        try {
            var futures = List.of("8000", "9000").stream().map(prefix -> pool.submit(() -> {
                await(gate);
                return cugs.changePatientPrefix("10", "5004", prefix, "42");
            })).toList();
            gate.countDown();
            int accepted = 0, rejected = 0;
            for (var future : futures) {
                try {
                    assertTrue(List.of("8000", "9000").contains(future.get(10, TimeUnit.SECONDS)));
                    accepted++;
                } catch (ExecutionException error) {
                    assertEquals(PatientCugPrefixException.STALE, error.getCause().getMessage());
                    rejected++;
                }
            }
            assertEquals(1, accepted);
            assertEquals(1, rejected);
            assertEquals(1, scalar("select count(*) from clinlims.sample_cug_prefix where prefix in (8000,9000)"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void renameWaitsForOrderAndIncludesItsJustSavedSample() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var saved = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try {
            var order = pool.submit(() -> tx.execute(s -> {
                cugs.lockPatientAndCheckPrefix("10", "5004");
                em.createNativeQuery("insert into clinlims.sample_item(id,samp_id,cug_code) values (5,20,'5004.3')")
                        .executeUpdate();
                saved.countDown();
                await(commit);
                return null;
            }));
            assertTrue(saved.await(10, TimeUnit.SECONDS));
            var rename = pool.submit(() -> cugs.changePatientPrefix("10", "5004", "8000", "42"));
            commit.countDown();
            order.get(10, TimeUnit.SECONDS);
            assertEquals("8000", rename.get(10, TimeUnit.SECONDS));
            assertEquals(1, scalar("select count(*) from clinlims.sample_item where id=5 and cug_code='8000.3'"));
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void staleOrderAndStaleSampleEditorCannotRestoreTheOldPrefix() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var loaded = new CountDownLatch(1);
        var refresh = new CountDownLatch(1);
        try {
            var editor = pool.submit(() -> tx.execute(s -> {
                SampleItem old = em.find(SampleItem.class, "1");
                assertEquals("5004.1", old.getCugCode());
                loaded.countDown();
                await(refresh);
                cugs.lockPatientAndCheckPrefix("10", null);
                items.lockAndRefreshForCugChange(old);
                return old.getCugCode();
            }));
            assertTrue(loaded.await(10, TimeUnit.SECONDS));
            cugs.changePatientPrefix("10", "5004", "8000", "42");
            refresh.countDown();
            assertEquals("8000.1", editor.get(10, TimeUnit.SECONDS));
            var failure = assertThrows(IllegalArgumentException.class, () -> tx.execute(s -> {
                em.createNativeQuery("insert into clinlims.sample(id) values(99)").executeUpdate();
                cugs.lockPatientAndCheckPrefix("10", "5004");
                return null;
            }));
            assertEquals("sample.cug.patient.prefix.mismatch", failure.getMessage());
            assertEquals(0, scalar("select count(*) from clinlims.sample where id=99"));
        } finally {
            refresh.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void pendingReservationIsCanceledAndNewPreviewUsesTheNewPrefix() throws Exception {
        var request = new SampleCugPreviewRequest();
        request.setPatientId("10");
        request.setReservationContextId("old-draft");
        request.setExistingCugs(List.of());
        assertEquals("5004.3", cugs.reserveCugCode(request, "42").getCugCode());
        cugs.changePatientPrefix("10", "5004", "8000", "42");
        assertEquals(0, scalar("select count(*) from clinlims.sample_cug_reservation where patient_id=10"));
        assertEquals("8000.3", cugs.previewCugCode(request).getCugCode());
        assertEquals("5004", cugs.assignPatientPrefix("12", "5004", "42"));
        request.setPatientId("12");
        request.setReservationContextId("new-owner");
        assertEquals("5004.1", cugs.reserveCugCode(request, "42").getCugCode());
    }

    @Test
    public void rollbackAfterReleaseRestoresOldPrefixRangesTokensSamplesAndAudit() throws Exception {
        var request = new SampleCugPreviewRequest();
        request.setPatientId("10");
        request.setReservationContextId("rollback");
        request.setExistingCugs(List.of());
        var reservation = cugs.reserveCugCode(request, "42");
        assertThrows(IllegalStateException.class, () -> tx.execute(s -> {
            cugs.changePatientPrefix("10", "5004", "8000", "42");
            assertFalse(prefixes.isAssigned(5004));
            throw new IllegalStateException("failure after release");
        }));
        unchanged();
        assertTrue(prefixes.isAssigned(5004));
        assertEquals(1, scalar(
                "select count(*) from clinlims.sample_cug_prefix_range where start_prefix<=5004 and end_prefix>=5004"));
        assertEquals(1, scalar(
                "select count(*) from clinlims.sample_cug_reservation where status='RESERVED' and reserved_value='5004.3'"));
        request.setReservationToken(reservation.getReservationToken());
        assertEquals("5004.3", cugs.reserveCugCode(request, "42").getCugCode());
    }

    @Test
    public void releaseSplitsAndShrinksRangesAndAllowsAutomaticReuse() throws Exception {
        for (long prefix : List.of(5005L, 5006L, 5007L, 6001L, 6002L, 6003L))
            prefixes.register(prefix, "42");
        assertTrue(prefixes.releaseIfUnused(5006));
        assertEquals(1, scalar(
                "select count(*) from clinlims.sample_cug_prefix_range where start_prefix=5004 and end_prefix=5005"));
        assertEquals(1, scalar(
                "select count(*) from clinlims.sample_cug_prefix_range where start_prefix=5007 and end_prefix=5007"));
        assertTrue(prefixes.releaseIfUnused(5005));
        assertTrue(prefixes.releaseIfUnused(5007));
        assertTrue(prefixes.releaseIfUnused(6003));
        assertEquals(1, scalar(
                "select count(*) from clinlims.sample_cug_prefix_range where start_prefix=6000 and end_prefix=6002"));
        prefixes.setStart("5006", "42");
        assertEquals("5006", cugs.ensurePatientPrefix("12", "42"));
        assertFalse(prefixes.releaseIfUnused(5004));
        assertFalse(prefixes.releaseIfUnused(5006));
    }

    @Test
    public void anotherPatientBindingKeepsTheOldPrefixAssigned() throws Exception {
        tx.execute(s -> {
            em.createNativeQuery("update clinlims.patient_cug_prefix set prefix=5004 where patient_id=11")
                    .executeUpdate();
            return null;
        });
        cugs.changePatientPrefix("10", "5004", "8000", "42");
        assertTrue(prefixes.isAssigned(5004));
        assertEquals("5004", cugs.getPatientPrefix("11"));
    }

    @Test
    public void legacySampleWithPaddedPrefixPreventsRelease() throws Exception {
        tx.execute(s -> {
            em.createNativeQuery("update clinlims.sample_item set cug_code=' 0005004.9 ' where id=4").executeUpdate();
            return null;
        });
        cugs.changePatientPrefix("10", "5004", "8000", "42");
        assertTrue(prefixes.isAssigned(5004));
        assertEquals(1, scalar("select count(*) from clinlims.sample_item where id=4 and cug_code=' 0005004.9 '"));
    }

    @Test
    public void activeReservationForAnotherPatientPreventsRelease() {
        tx.execute(s -> {
            var reservation = new org.openelisglobal.sample.valueholder.SampleCugReservation();
            reservation.setReservedValue("5004.9");
            reservation.setReservationToken("another-patient");
            reservation.setPatientId(12);
            reservation.setReservedByUserId(42);
            reservation.setSysUserId("42");
            reservation.setStatus("RESERVED");
            reservation.setExpiresAt(new java.sql.Timestamp(System.currentTimeMillis() + 600000));
            em.persist(reservation);
            return null;
        });
        cugs.changePatientPrefix("10", "5004", "8000", "42");
        assertTrue(prefixes.isAssigned(5004));
    }

    @Test
    public void releasedPrefixBecomesAvailableToAnotherPatientOnlyAfterCommit() throws Exception {
        reuseWaitsForRename(false);
    }

    @Test
    public void rollbackPreventsAnotherPatientFromTakingTheOriginalPrefix() throws Exception {
        reuseWaitsForRename(true);
    }

    private void reuseWaitsForRename(boolean rollback) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var renamed = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try {
            var rename = pool.submit(() -> tx.execute(s -> {
                String result = cugs.changePatientPrefix("10", "5004", "8000", "42");
                renamed.countDown();
                await(commit);
                if (rollback)
                    throw new IllegalStateException("forced outer rollback");
                return result;
            }));
            assertTrue(renamed.await(10, TimeUnit.SECONDS));
            var reuse = pool.submit(() -> cugs.assignPatientPrefix("12", "5004", "42"));
            assertThrows(TimeoutException.class, () -> reuse.get(150, TimeUnit.MILLISECONDS));
            commit.countDown();
            if (rollback) {
                assertThrows(ExecutionException.class, () -> rename.get(10, TimeUnit.SECONDS));
                assertEquals(PatientCugPrefixException.OCCUPIED,
                        assertThrows(ExecutionException.class, () -> reuse.get(10, TimeUnit.SECONDS)).getCause()
                                .getMessage());
                unchanged();
            } else {
                assertEquals("8000", rename.get(10, TimeUnit.SECONDS));
                assertEquals("5004", reuse.get(10, TimeUnit.SECONDS));
                assertEquals("8000", cugs.getPatientPrefix("10"));
                assertEquals("5004", cugs.getPatientPrefix("12"));
            }
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void concurrentLegacySampleInsertPreventsDeletingItsRegistryPrefix() throws Exception {
        prefixes.register(9900, "42");
        var pool = Executors.newFixedThreadPool(2);
        var inserted = new CountDownLatch(1);
        var commit = new CountDownLatch(1);
        try {
            var writer = pool.submit(() -> tx.execute(s -> {
                em.createNativeQuery("insert into clinlims.sample_item(id,samp_id,cug_code) values(5,20,'9900.1')")
                        .executeUpdate();
                inserted.countDown();
                await(commit);
                return null;
            }));
            assertTrue(inserted.await(10, TimeUnit.SECONDS));
            var release = pool.submit(() -> prefixes.releaseIfUnused(9900));
            assertThrows(TimeoutException.class, () -> release.get(150, TimeUnit.MILLISECONDS));
            commit.countDown();
            writer.get(10, TimeUnit.SECONDS);
            assertThrows(ExecutionException.class, () -> release.get(10, TimeUnit.SECONDS));
            assertTrue(prefixes.isAssigned(9900));
            assertEquals(1, scalar("select count(*) from clinlims.sample_item where cug_code='9900.1'"));
        } finally {
            commit.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    public void migrationRollsBackAndReappliesWithoutChangingClinicalRecords() throws Exception {
        var migration = new CugPrefixIntegrationTest().migration(connection,
                "liquibase/3.4.x.x/100-release-unused-cug-prefix.xml");
        migration.rollback(1, "");
        assertEquals(0, scalar("select count(*) from pg_trigger where tgname='guard_cug_prefix_release'"));
        assertEquals(4, scalar("select count(*) from clinlims.sample_item"));
        migration.update("");
        assertEquals(1, scalar("select count(*) from pg_trigger where tgname='guard_cug_prefix_release'"));
        cugs.changePatientPrefix("10", "5004", "8000", "42");
        assertFalse(prefixes.isAssigned(5004));
    }

    @Test
    public void databaseGuardAlsoRejectsDeletingAPrefixStillUsedBySamples() {
        tx.execute(s -> {
            em.createNativeQuery("delete from clinlims.patient_cug_prefix where patient_id=10").executeUpdate();
            return null;
        });
        assertThrows(RuntimeException.class, () -> tx.execute(s -> {
            em.remove(em.find(org.openelisglobal.sample.valueholder.SampleCugPrefix.class, "5004"));
            em.flush();
            return null;
        }));
        assertTrue(prefixes.isAssigned(5004));
    }

    private void unchanged() throws Exception {
        assertEquals("5004", cugs.getPatientPrefix("10"));
        assertEquals(2, scalar("select count(*) from clinlims.sample_item where cug_code in ('5004.1','5004.2')"));
        assertEquals(0, scalar("select count(*) from clinlims.sample_cug_prefix where prefix=8000"));
        assertEquals(0, scalar("select count(*) from clinlims.audit_event where source='/rest/PatientManagement'"));
    }

    private long scalar(String sql) throws Exception {
        return new CugPrefixIntegrationTest().scalar(connection, sql);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Test gate timed out");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }
}
