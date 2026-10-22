package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
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
import org.openelisglobal.sample.exception.SampleCugConflictException;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

public class SampleCugStrictOrderIntegrationTest {
    @Test
    public void sameDisplayedCugHasOneWinnerAndRollsBackTheEntireLosingOrder() throws Exception {
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
                // Read actual committed clinical links while using production prefix/patient
                // locks.
                when(items.findCugCodesByPatientId(anyString())).thenAnswer(call -> {
                    try (var read = DriverManager.getConnection(url, user, password);
                            var stmt = read.prepareStatement("select si.cug_code from clinlims.sample_item si "
                                    + "join clinlims.sample_human sh on sh.samp_id=si.samp_id where sh.patient_id=?")) {
                        stmt.setInt(1, Integer.parseInt(call.getArgument(0)));
                        List<String> result = new ArrayList<>();
                        try (var rows = stmt.executeQuery()) {
                            while (rows.next())
                                result.add(rows.getString(1));
                        }
                        return result;
                    }
                });
                when(items.existsByCugCode(anyString())).thenAnswer(call -> {
                    try (var read = DriverManager.getConnection(url, user, password);
                            var stmt = read.prepareStatement(
                                    "select count(*) from clinlims.sample_item where lower(cug_code)=lower(?)")) {
                        stmt.setString(1, call.getArgument(0));
                        try (var rows = stmt.executeQuery()) {
                            rows.next();
                            return rows.getLong(1) > 0;
                        }
                    }
                });
                var target = new SampleCugServiceImpl();
                ReflectionTestUtils.setField(target, "prefixes", prefixes);
                ReflectionTestUtils.setField(target, "patientPrefixes", dao);
                ReflectionTestUtils.setField(target, "sampleCugReservationDAO", reservations);
                ReflectionTestUtils.setField(target, "sampleItemDAO", items);
                SampleCugService cugs = support.proxy(target, manager, SampleCugService.class);
                prefixes.setStart("5000", "42");
                assertEquals("5000", cugs.ensurePatientPrefix("10", "42"));
                assertEquals("5001", cugs.ensurePatientPrefix("11", "42"));
                assertEquals("5000.1", preview(cugs, "10"));
                assertEquals("5000.1", preview(cugs, "10"));
                assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample_cug_reservation"));

                var pool = Executors.newFixedThreadPool(2);
                var gate = new CountDownLatch(1);
                try {
                    List<Future<String>> saves = new ArrayList<>();
                    for (int id : List.of(100, 101))
                        saves.add(pool.submit(() -> {
                            gate.await();
                            try {
                                save(tx, em, cugs, id, "10", "5000.1");
                                return "5000.1";
                            } catch (RuntimeException error) {
                                if (!SampleCugConflictException.isConflict(error))
                                    throw error;
                                return "CONFLICT";
                            }
                        }));
                    gate.countDown();
                    assertEquals(Set.of("5000.1", "CONFLICT"),
                            Set.of(saves.get(0).get(15, TimeUnit.SECONDS), saves.get(1).get(15, TimeUnit.SECONDS)));
                } finally {
                    pool.shutdownNow();
                }
                assertEquals(1,
                        support.scalar(connection, "select count(*) from clinlims.sample where id in (100,101)"));
                assertEquals(1, support.scalar(connection, "select count(*) from clinlims.sample_human"));
                assertEquals(1, support.scalar(connection, "select count(*) from clinlims.sample_item"));
                assertEquals(0, support.scalar(connection,
                        "select count(*) from clinlims.sample_item where cug_code='5000.2'"));

                // Deliberate second orders and another patient's first sample are valid.
                assertEquals("5000.2", preview(cugs, "10"));
                save(tx, em, cugs, 102, "10", "5000.2");
                save(tx, em, cugs, 103, "11", "5001.1");

                // A conflict in one sample rolls back the parent and every sample in the order.
                for (String[] batch : List.of(new String[] { "5000.3", "5000.1" },
                        new String[] { "5000.3", "5000.3" })) {
                    try {
                        save(tx, em, cugs, 104, "10", batch);
                        fail("The entire conflicting order must be rejected");
                    } catch (RuntimeException error) {
                        assertTrue(SampleCugConflictException.isConflict(error));
                    }
                    assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample where id=104"));
                    assertEquals(0,
                            support.scalar(connection, "select count(*) from clinlims.sample_human where samp_id=104"));
                    assertEquals(0, support.scalar(connection,
                            "select count(*) from clinlims.sample_item where cug_code='5000.3'"));
                    assertEquals("5000.3", preview(cugs, "10"));
                }
                // Even a writer bypassing service locks cannot duplicate the complete CUG.
                try {
                    tx.execute(status -> {
                        em.createNativeQuery(
                                "insert into clinlims.sample_item(id,samp_id,cug_code,sys_user_id) values (999,20,'5000.1',42)")
                                .executeUpdate();
                        return null;
                    });
                    fail("The production unique index must reject duplicates");
                } catch (RuntimeException error) {
                    assertTrue(SampleCugConflictException.isConflict(error));
                }
                assertEquals(3, support.scalar(connection, "select count(*) from clinlims.sample_item"));
                assertEquals("5002", prefixes.getSettings().getNextAvailablePrefix());
                assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample_cug_reservation"));
            }
        }
    }

    private String preview(SampleCugService cugs, String patient) {
        var request = new SampleCugPreviewRequest();
        request.setPatientId(patient);
        return cugs.previewCugCode(request).getCugCode();
    }

    private void save(TransactionTemplate tx, EntityManager em, SampleCugService cugs, int orderId, String patient,
            String... codes) {
        tx.execute(status -> {
            em.createNativeQuery("insert into clinlims.sample(id) values (:id)").setParameter("id", orderId)
                    .executeUpdate();
            List<SampleTestCollection> samples = new ArrayList<>();
            for (String code : codes) {
                var sample = new org.objenesis.ObjenesisStd().newInstance(SampleTestCollection.class);
                sample.item = new SampleItem();
                sample.item.setCugCode(code);
                sample.cugProvisional = true;
                samples.add(sample);
            }
            cugs.assignMissingCugCodes(samples, patient, "42", Integer.toString(orderId));
            // Production validates/locks before inserting the patient/order link.
            em.createNativeQuery("insert into clinlims.sample_human(samp_id,patient_id) values (:id,:patient)")
                    .setParameter("id", orderId).setParameter("patient", Integer.parseInt(patient)).executeUpdate();
            for (int i = 0; i < samples.size(); i++) {
                assertEquals(codes[i], samples.get(i).item.getCugCode());
                em.createNativeQuery(
                        "insert into clinlims.sample_item(id,samp_id,cug_code,sys_user_id) values (:id,:sample,:cug,42)")
                        .setParameter("id", orderId * 10 + i).setParameter("sample", orderId)
                        .setParameter("cug", codes[i]).executeUpdate();
            }
            return null;
        });
    }
}
