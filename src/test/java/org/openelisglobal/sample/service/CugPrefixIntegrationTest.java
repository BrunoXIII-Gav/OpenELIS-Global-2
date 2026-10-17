package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.daoimpl.SampleCugPrefixDAOImpl;
import org.openelisglobal.sample.daoimpl.SampleCugReservationDAOImpl;
import org.openelisglobal.sample.valueholder.*;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real PostgreSQL migrations, HQL, triggers, rollback and concurrent
 * transactions.
 */
public class CugPrefixIntegrationTest {
    @Test
    public void realDatabaseCoversMigrationConfigurationReservationsConcurrencyAndLargeRanges() throws Exception {
        String external = System.getenv("CUG_TEST_JDBC_URL");
        if (external != null) {
            if (!external.matches("jdbc:postgresql://(127[.]0[.]0[.]1|localhost):[0-9]+/cug_prefix_test")) {
                throw new IllegalArgumentException("Use a disposable localhost cug_prefix_test database");
            }
            run(external, "cugtest", "");
        } else {
            try (var container = new PostgreSQLContainer<>("postgres:14.4")) {
                container.start();
                run(container.getJdbcUrl(), container.getUsername(), container.getPassword());
            }
        }
    }

    private void run(String url, String user, String password) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // Only this disposable test database is touched.
            connection.createStatement().execute("DROP SCHEMA IF EXISTS clinlims CASCADE; CREATE SCHEMA clinlims");
            connection.setSchema("clinlims");
            migration(connection, "liquibase/cug-prefix-baseline.xml").update("");
            var dbunit = new DatabaseConnection(connection, "clinlims");
            dbunit.getConfig().setProperty(DatabaseConfig.FEATURE_BATCHED_STATEMENTS, true);
            dbunit.getConfig().setProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                    new org.dbunit.ext.postgresql.PostgresqlDataTypeFactory());
            var fixture = new FlatXmlDataSetBuilder().setColumnSensing(true)
                    .build(getClass().getClassLoader().getResourceAsStream("testdata/cug-prefix.xml"));
            DatabaseOperation.INSERT.execute(dbunit, fixture);
            // DBUnit uses explicit ids; align the reservation sequence before
            // exercising the production insert path repeatedly.
            scalar(connection,
                    "select setval('clinlims.sample_cug_reservation_seq', (select max(id)::bigint from clinlims.sample_cug_reservation))");
            connection.commit();
            var migration = migration(connection, "liquibase/3.4.x.x/095-configurable-cug-prefix.xml");
            migration.update("");
            assertEquals(10,
                    scalar(connection, "select count(*) from clinlims.sample_item where cug_code like '500%'"));
            assertEquals(5010, scalar(connection,
                    "select end_prefix from clinlims.sample_cug_prefix_range where start_prefix=5000"));
            assertEquals(141, scalar(connection, "select next_candidate from clinlims.sample_cug_counter"));
            assertEquals(140, scalar(connection, "select last_value from clinlims.sample_cug_prefix_seq"));
            long before = scalar(connection, "select count(*) from clinlims.sample_item");
            // Prove reversible deployment on existing data, then reapply.
            migration.rollback(1, "");
            assertEquals(before, scalar(connection, "select count(*) from clinlims.sample_item"));
            migration.update("");

            try (SessionFactory factory = factory(url, user, password)) {
                var manager = new JpaTransactionManager(factory);
                var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
                var dao = new SampleCugPrefixDAOImpl();
                ReflectionTestUtils.setField(dao, "entityManager", em);
                var audit = new org.openelisglobal.audittrail.daoimpl.AuditEventDAOImpl();
                ReflectionTestUtils.setField(audit, "entityManager", em);
                CugPrefixService prefixes = proxy(new CugPrefixServiceImpl(dao, audit), manager,
                        CugPrefixService.class);
                assertEquals("5000", prefixes.setStart("5000", "42").getStartPrefix());
                assertEquals(5011, prefixes.allocate("42"));
                assertTrue(scalar(connection,
                        "select count(*) from clinlims.audit_event where source='/rest/sample-cug/prefix-settings' and actor_id='42' and new_value='5000'") > 0);
                prefixes.setStart("7000", "42");
                assertEquals(7000, prefixes.allocate("42"));
                prefixes.setStart("4000", "42");
                assertEquals(4000, prefixes.allocate("42"));
                // Manually/imported values written after migration join the registry.
                DatabaseOperation.INSERT.execute(dbunit,
                        new FlatXmlDataSetBuilder().build(new ByteArrayInputStream(
                                "<dataset><sample_item id=\"30\" cug_code=\"4500.9\" sys_user_id=\"42\"/></dataset>"
                                        .getBytes(StandardCharsets.UTF_8))));
                connection.commit();
                assertEquals("4501", prefixes.setStart("4500", "42").getNextAvailablePrefix());
                prefixes.setStart("4001", "42");
                assertEquals(4001, prefixes.allocate("42"));
                prefixes.setStart("4999", "42");
                assertEquals(4999, prefixes.allocate("42"));
                assertEquals(5012, prefixes.allocate("42")); // 5011 was allocated after migration.
                assertEquals(before + 1, scalar(connection, "select count(*) from clinlims.sample_item"));

                var reservations = new SampleCugReservationDAOImpl();
                ReflectionTestUtils.setField(reservations, "entityManager", em);
                var items = mock(SampleItemDAO.class);
                when(items.findCugCodesByPatientId("10")).thenReturn(List.of("140.2"));
                var cugTarget = new SampleCugServiceImpl();
                ReflectionTestUtils.setField(cugTarget, "prefixes", prefixes);
                ReflectionTestUtils.setField(cugTarget, "sampleCugReservationDAO", reservations);
                ReflectionTestUtils.setField(cugTarget, "sampleItemDAO", items);
                SampleCugService cugs = proxy(cugTarget, manager, SampleCugService.class);
                var oldPatient = request("10", "old-patient");
                var oldReservation = cugs.reserveCugCode(oldPatient, "42");
                assertEquals("140.3", oldReservation.getCugCode());
                prefixes.setStart("9000", "42");
                oldPatient.setReservationToken(oldReservation.getReservationToken());
                assertEquals("140.3", cugs.reserveCugCode(oldPatient, "42").getCugCode());
                assertEquals("140.4", cugs.reserveCugCode(request("10", "old-patient-next"), "42").getCugCode());
                assertEquals("9000.1", cugs.reserveCugCode(request(null, "new-patient"), "42").getCugCode());
                assertEquals("9001", prefixes.setStart("9000", "42").getNextAvailablePrefix());

                // Suffixes for the same patient serialize independently of the
                // global prefix cursor, including simultaneous preview requests.
                ExecutorService suffixPool = Executors.newFixedThreadPool(8);
                try {
                    List<Future<String>> suffixes = new ArrayList<>();
                    for (int i = 0; i < 8; i++) {
                        final int context = i;
                        suffixes.add(suffixPool.submit(
                                () -> cugs.reserveCugCode(request("10", "parallel-" + context), "42").getCugCode()));
                    }
                    Set<String> values = new java.util.HashSet<>();
                    for (var suffix : suffixes)
                        assertTrue(values.add(suffix.get(15, TimeUnit.SECONDS)));
                    assertEquals(8, values.size());
                    assertTrue(values.contains("140.5"));
                    assertTrue(values.contains("140.12"));
                } finally {
                    suffixPool.shutdownNow();
                }

                // An order transaction holding its token/patient locks must not
                // block unrelated patients from obtaining a new prefix.
                ExecutorService completionPool = Executors.newSingleThreadExecutor();
                CountDownLatch consumed = new CountDownLatch(1);
                CountDownLatch commitOrder = new CountDownLatch(1);
                Future<?> completion = completionPool.submit(() -> new TransactionTemplate(manager).execute(status -> {
                    var item = new org.openelisglobal.sampleitem.valueholder.SampleItem();
                    item.setCugCode(oldReservation.getCugCode());
                    var dto = new org.objenesis.ObjenesisStd().newInstance(
                            org.openelisglobal.common.services.SampleAddService.SampleTestCollection.class);
                    dto.item = item;
                    dto.cugReservationToken = oldReservation.getReservationToken();
                    cugs.assignMissingCugCodes(List.of(dto), "10", "42", "20");
                    // Exercise the real sample-item trigger in this unfinished
                    // order transaction, not just reservation consumption.
                    em.createNativeQuery(
                            "insert into clinlims.sample_item(id, cug_code, sys_user_id) values (31, '140.3', 42)")
                            .executeUpdate();
                    consumed.countDown();
                    try {
                        if (!commitOrder.await(10, TimeUnit.SECONDS))
                            throw new AssertionError("Order was never released");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return null;
                }));
                try {
                    assertTrue(consumed.await(10, TimeUnit.SECONDS));
                    assertEquals("9001.1", cugs.reserveCugCode(request(null, "unrelated-order"), "42").getCugCode());
                } finally {
                    commitOrder.countDown();
                    completion.get(15, TimeUnit.SECONDS);
                    completionPool.shutdownNow();
                }

                // Rollback a flushed registry insert, its interval merge and the cursor
                // together.
                prefixes.setStart("7500", "42");
                try {
                    new TransactionTemplate(manager).execute(status -> {
                        assertEquals(7500, prefixes.allocate("42"));
                        throw new IllegalStateException("forced rollback");
                    });
                    fail();
                } catch (IllegalStateException expected) {
                    assertEquals("forced rollback", expected.getMessage());
                }
                assertEquals("7500", prefixes.getSettings().getNextAvailablePrefix());
                assertEquals(0,
                        scalar(connection, "select count(*) from clinlims.sample_cug_prefix where prefix=7500"));

                prefixes.setStart("8000", "42");
                ExecutorService pool = Executors.newFixedThreadPool(8);
                var gate = new CountDownLatch(1);
                try {
                    List<Future<List<Long>>> futures = new ArrayList<>();
                    for (int worker = 0; worker < 8; worker++)
                        futures.add(pool.submit(() -> {
                            gate.await();
                            List<Long> values = new ArrayList<>();
                            for (int i = 0; i < 10; i++)
                                values.add(prefixes.allocate("42"));
                            return values;
                        }));
                    gate.countDown();
                    Set<Long> unique = new java.util.HashSet<>();
                    for (var future : futures)
                        for (long value : future.get(45, TimeUnit.SECONDS))
                            assertTrue(unique.add(value));
                    assertEquals(80, unique.size());
                    assertEquals("8080", prefixes.getSettings().getNextAvailablePrefix());
                    assertEquals(8079, scalar(connection,
                            "select end_prefix from clinlims.sample_cug_prefix_range where start_prefix=8000"));
                } finally {
                    pool.shutdownNow();
                }

                assertEquals("6001", prefixes.setStart("6000", "42").getNextAvailablePrefix());

                // Load a large legacy dataset through DBUnit BEFORE migration so the
                // migration's bulk backfill, rather than 100k row triggers, is measured.
            }
            migration.rollback(1, "");
            StringBuilder rows = new StringBuilder("<dataset>");
            for (int i = 0; i < 100000; i++)
                rows.append("<sample_item id=\"").append(1000 + i).append("\" cug_code=\"").append(100000 + i)
                        .append(".2\" sys_user_id=\"42\"/>");
            rows.append("</dataset>");
            DatabaseOperation.INSERT.execute(dbunit, new FlatXmlDataSetBuilder()
                    .build(new ByteArrayInputStream(rows.toString().getBytes(StandardCharsets.UTF_8))));
            connection.commit();
            long migrationStarted = System.nanoTime();
            migration.update("");
            long migrationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - migrationStarted);
            try (SessionFactory factory = factory(url, user, password)) {
                var manager = new JpaTransactionManager(factory);
                var dao = new SampleCugPrefixDAOImpl();
                ReflectionTestUtils.setField(dao, "entityManager",
                        SharedEntityManagerCreator.createSharedEntityManager(factory));
                var audit = new org.openelisglobal.audittrail.daoimpl.AuditEventDAOImpl();
                ReflectionTestUtils.setField(audit, "entityManager",
                        SharedEntityManagerCreator.createSharedEntityManager(factory));
                CugPrefixService prefixes = proxy(new CugPrefixServiceImpl(dao, audit), manager,
                        CugPrefixService.class);
                prefixes.setStart("100000", "42");
                factory.getStatistics().clear();
                long started = System.nanoTime();
                assertEquals(200000, prefixes.allocate("42"));
                long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                long queries = factory.getStatistics().getQueryExecutionCount();
                System.out.println("CUG benchmark: 100000 occupied prefixes; migration=" + migrationMillis
                        + " ms; allocation=" + millis + " ms; HQL queries=" + queries);
                assertTrue("Allocation must not query once per prefix: " + queries, queries <= 5);
                assertTrue("Range jump unexpectedly slow: " + millis, millis < 5000);
                var resetMigration = migration(connection,
                        "liquibase/3.4.x.x/096-reset-configurable-cug-in-training.xml");
                resetMigration.update("");
                try {
                    scalar(connection, "select clinlims.restart_training_cug_prefix()");
                    fail("A normal installation must not reset CUG numbering");
                } catch (java.sql.SQLException expected) {
                    connection.rollback();
                }
                var trainingFixture = new FlatXmlDataSetBuilder().build(new ByteArrayInputStream(
                        "<dataset><site_information name=\"TrainingInstallation\" value=\"true\"/></dataset>"
                                .getBytes(StandardCharsets.UTF_8)));
                DatabaseOperation.INSERT.execute(dbunit, trainingFixture);
                connection.commit();
                try {
                    scalar(connection, "select clinlims.restart_training_cug_prefix()");
                    fail("Clinical data must block a reset");
                } catch (java.sql.SQLException expected) {
                    connection.rollback();
                }
                assertEquals("200001", prefixes.getSettings().getNextAvailablePrefix());
                DatabaseOperation.DELETE_ALL.execute(dbunit, fixture);
                connection.commit();
                assertEquals(1, scalar(connection, "select clinlims.restart_training_cug_prefix()"));
                connection.commit();
                assertEquals("1", prefixes.getSettings().getNextAvailablePrefix());
                assertEquals(0, scalar(connection, "select count(*) from clinlims.sample_cug_prefix"));
                resetMigration.rollback(1, "");
            }
            // Empty-database reapplication also preserves the original sequence start.
            migration.rollback(1, "");
            migration.update("");
            assertEquals(0, scalar(connection, "select count(*) from clinlims.sample_cug_prefix"));
            assertEquals(1, scalar(connection, "select next_candidate from clinlims.sample_cug_counter"));
        }
    }

    private SampleCugPreviewRequest request(String patient, String context) {
        var request = new SampleCugPreviewRequest();
        request.setPatientId(patient);
        request.setReservationContextId(context);
        request.setExistingCugs(List.of());
        return request;
    }

    private Liquibase migration(Connection connection, String file) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName("clinlims");
        return new Liquibase(file, new ClassLoaderResourceAccessor(), database);
    }

    private SessionFactory factory(String url, String user, String password) {
        Configuration config = new Configuration().addAnnotatedClass(SampleCugCounter.class)
                .addAnnotatedClass(SampleCugPrefix.class).addAnnotatedClass(SampleCugPrefixRange.class)
                .addAnnotatedClass(SampleCugReservation.class)
                .addAnnotatedClass(org.openelisglobal.audittrail.valueholder.AuditEvent.class)
                .addResource("hibernate/cug-patient-lock.hbm.xml");
        config.setProperty("hibernate.connection.url", url + "?currentSchema=clinlims");
        config.setProperty("hibernate.connection.username", user);
        config.setProperty("hibernate.connection.password", password);
        config.setProperty("hibernate.default_schema", "clinlims");
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        config.setProperty("hibernate.search.enabled", "false");
        config.setProperty("hibernate.generate_statistics", "true");
        config.setProperty("hibernate.connection.pool_size", "12");
        return config.buildSessionFactory();
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, JpaTransactionManager manager, Class<T> type) {
        var factory = new ProxyFactory(target);
        factory.setInterfaces(type);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
