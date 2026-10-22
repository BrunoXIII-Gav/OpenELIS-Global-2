package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.junit.Test;
import org.openelisglobal.common.services.SampleAddService.SampleTestCollection;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.daoimpl.SampleCugPrefixDAOImpl;
import org.openelisglobal.sample.daoimpl.SampleCugReservationDAOImpl;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

public class PatientCugWorkflowIntegrationTest {
    @Test
    public void patientOwnershipPreviewsRollbackAndConcurrentOrderCommits() throws Exception {
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
            support.migration(connection, "liquibase/cug-prefix-baseline.xml").update("");
            support.migration(connection, "liquibase/3.4.x.x/095-configurable-cug-prefix.xml").update("");
            var migration = support.migration(connection, "liquibase/3.4.x.x/097-patient-cug-prefix.xml");
            migration.update("");
            migration.rollback(1, "");
            migration.update("");
            var dbunit = new DatabaseConnection(connection, "clinlims");
            dbunit.getConfig().setProperty(org.dbunit.database.DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                    new org.dbunit.ext.postgresql.PostgresqlDataTypeFactory());
            DatabaseOperation.INSERT.execute(dbunit, new FlatXmlDataSetBuilder()
                    .build(getClass().getClassLoader().getResourceAsStream("testdata/patient-cug-workflow.xml")));
            connection.commit();
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
                var items = mock(SampleItemDAO.class);
                // The narrow baseline omits the full clinical model. Read committed sample
                // codes from PostgreSQL while exercising the real patient locks and inserts.
                when(items.findCugCodesByPatientId(anyString())).thenAnswer(call -> {
                    try (var read = DriverManager.getConnection(url, user, password);
                            var stmt = read.prepareStatement("select si.cug_code from clinlims.sample_item si "
                                    + "join clinlims.patient_cug_prefix p on si.cug_code like p.prefix::text || '.%' where p.patient_id = ?")) {
                        stmt.setInt(1, Integer.parseInt(call.getArgument(0)));
                        List<String> result = new ArrayList<>();
                        try (var rows = stmt.executeQuery()) {
                            while (rows.next())
                                result.add(rows.getString(1));
                        }
                        return result;
                    }
                });
                var target = new SampleCugServiceImpl();
                ReflectionTestUtils.setField(target, "prefixes", prefixes);
                ReflectionTestUtils.setField(target, "patientPrefixes", dao);
                ReflectionTestUtils.setField(target, "sampleCugReservationDAO", reservations);
                ReflectionTestUtils.setField(target, "sampleItemDAO", items);
                SampleCugService cugs = support.proxy(target, manager, SampleCugService.class);
                prefixes.setStart("5000", "42");
                assertEquals("5000", cugs.previewPatientPrefix(null));
                assertEquals("5000", cugs.previewPatientPrefix(null));
                assertEquals(0, support.scalar(connection, "select count(*) from clinlims.patient_cug_prefix"));
                assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample_cug_prefix"));

                var pool = Executors.newFixedThreadPool(4);
                try {
                    var gate = new CountDownLatch(1);
                    List<Future<String>> saves = new ArrayList<>();
                    for (String patient : List.of("10", "11"))
                        saves.add(pool.submit(() -> {
                            gate.await();
                            return cugs.ensurePatientPrefix(patient, "42");
                        }));
                    gate.countDown();
                    Set<String> allocated = Set.of(saves.get(0).get(10, TimeUnit.SECONDS),
                            saves.get(1).get(10, TimeUnit.SECONDS));
                    assertEquals(Set.of("5000", "5001"), allocated);
                    String prefix = cugs.previewPatientPrefix("10");
                    assertEquals(prefix, cugs.ensurePatientPrefix("10", "42"));
                    assertEquals(2, support.scalar(connection, "select count(*) from clinlims.patient_cug_prefix"));
                    var request = new SampleCugPreviewRequest();
                    request.setPatientId("10");
                    assertEquals(prefix + ".1", cugs.previewCugCode(request).getCugCode());
                    assertEquals(prefix + ".1", cugs.previewCugCode(request).getCugCode());
                    assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample_cug_reservation"));
                    assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample_item"));
                    assertEquals("5002", prefixes.getSettings().getNextAvailablePrefix());

                    // A failed patient transaction must roll back its prefix and counter.
                    try {
                        tx.execute(status -> {
                            em.createNativeQuery("insert into clinlims.patient(id) values (13)").executeUpdate();
                            assertEquals("5002", cugs.ensurePatientPrefix("13", "42"));
                            throw new IllegalStateException("rollback patient");
                        });
                        fail();
                    } catch (IllegalStateException expected) {
                        assertEquals("rollback patient", expected.getMessage());
                    }
                    assertEquals("5002", prefixes.getSettings().getNextAvailablePrefix());
                    assertEquals(0, support.scalar(connection,
                            "select count(*) from clinlims.patient_cug_prefix where patient_id=13"));
                    assertEquals(0, support.scalar(connection, "select count(*) from clinlims.patient where id=13"));

                    // Legacy callers without a displayed CUG retain automatic assignment.
                    // Strict displayed-CUG conflicts are covered separately.
                    var orderGate = new CountDownLatch(1);
                    List<Future<String>> orders = new ArrayList<>();
                    for (int i = 0; i < 2; i++) {
                        final int itemId = 100 + i;
                        orders.add(pool.submit(() -> {
                            orderGate.await();
                            return tx.execute(status -> {
                                var dto = sample();
                                cugs.assignMissingCugCodes(List.of(dto), "10", "42", "20");
                                em.createNativeQuery(
                                        "insert into clinlims.sample_item(id,cug_code,sys_user_id) values (:id,:cug,42)")
                                        .setParameter("id", itemId).setParameter("cug", dto.item.getCugCode())
                                        .executeUpdate();
                                return dto.item.getCugCode();
                            });
                        }));
                    }
                    orderGate.countDown();
                    assertEquals(Set.of(prefix + ".1", prefix + ".2"),
                            Set.of(orders.get(0).get(15, TimeUnit.SECONDS), orders.get(1).get(15, TimeUnit.SECONDS)));
                    assertEquals(prefix + ".3", cugs.previewCugCode(request).getCugCode());
                    request.setSampleOffset(1);
                    assertEquals(prefix + ".4", cugs.previewCugCode(request).getCugCode());
                    try {
                        tx.execute(status -> {
                            var dto = sample();
                            cugs.assignMissingCugCodes(List.of(dto), "10", "42", "20");
                            assertEquals(prefix + ".3", dto.item.getCugCode());
                            throw new IllegalStateException("rollback order");
                        });
                        fail();
                    } catch (IllegalStateException expected) {
                        assertEquals("rollback order", expected.getMessage());
                    }
                    request.setSampleOffset(0);
                    assertEquals(prefix + ".3", cugs.previewCugCode(request).getCugCode());
                } finally {
                    pool.shutdownNow();
                }
                assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample_cug_reservation"));
            }
        }
    }

    private SampleTestCollection sample() {
        var sample = new org.objenesis.ObjenesisStd().newInstance(SampleTestCollection.class);
        sample.item = new SampleItem();
        return sample;
    }
}
