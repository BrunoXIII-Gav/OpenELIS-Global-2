package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory;
import org.openelisglobal.common.provider.validation.IAccessionNumberValidator;
import org.openelisglobal.common.provider.validation.IAccessionNumberValidator.ValidationResults;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.valueholder.AccessionNumberInfo;
import org.openelisglobal.sample.daoimpl.OrderAccessionDAOImpl;
import org.openelisglobal.sample.exception.OrderAccessionConflictException;
import org.openelisglobal.sample.exception.SampleCugConflictException;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

public class OrderAccessionIntegrationTest {
    @Test
    public void proposalsConcurrencyManualEntriesAndFailedOrdersUseTransactionalNumbers() throws Exception {
        String url = System.getenv("CUG_TEST_JDBC_URL");
        if (url != null) {
            if (!url.matches("jdbc:postgresql://(127[.]0[.]0[.]1|localhost):[0-9]+/cug_prefix_test"))
                throw new IllegalArgumentException("Use a disposable localhost cug_prefix_test database");
            run(url, "cugtest", "");
        } else
            try (var container = new PostgreSQLContainer<>("postgres:14.4")) {
                container.start();
                run(container.getJdbcUrl(), container.getUsername(), container.getPassword());
            }
    }

    private void run(String url, String user, String password) throws Exception {
        var support = new CugPrefixIntegrationTest();
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.createStatement().execute("DROP SCHEMA IF EXISTS clinlims CASCADE; CREATE SCHEMA clinlims");
            connection.setSchema("clinlims");
            support.migration(connection, "liquibase/order-accession-baseline.xml").update("");
            var dbunit = new DatabaseConnection(connection, "clinlims");
            dbunit.getConfig().setProperty(org.dbunit.database.DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                    new org.dbunit.ext.postgresql.PostgresqlDataTypeFactory());
            DatabaseOperation.INSERT.execute(dbunit, new FlatXmlDataSetBuilder()
                    .build(getClass().getClassLoader().getResourceAsStream("testdata/order-accession.xml")));
            connection.commit();
            var config = new Configuration().addAnnotatedClass(AccessionNumberInfo.class)
                    .addResource("hibernate/order-accession-sample.hbm.xml");
            config.setProperty("hibernate.connection.url", url + "?currentSchema=clinlims");
            config.setProperty("hibernate.connection.username", user);
            config.setProperty("hibernate.connection.password", password);
            config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            config.setProperty("hibernate.search.enabled", "false");
            config.setProperty("hibernate.hbm2ddl.auto", "none");
            try (var factory = config.buildSessionFactory()) {
                var manager = new JpaTransactionManager(factory);
                var tx = new TransactionTemplate(manager);
                var em = SharedEntityManagerCreator.createSharedEntityManager(factory);
                var dao = new OrderAccessionDAOImpl();
                ReflectionTestUtils.setField(dao, "entityManager", em);
                var validators = mock(AccessionNumberValidatorFactory.class);
                var validator = mock(IAccessionNumberValidator.class);
                when(validators.getValidator(any())).thenReturn(validator);
                when(validator.validFormat(anyString(), anyBoolean())).thenReturn(ValidationResults.SUCCESS);
                var target = new OrderAccessionServiceImpl() {
                    @Override
                    protected String property(Property property) {
                        return property == Property.AccessionFormat ? "PREFIXNUM" : "LAB";
                    }
                };
                ReflectionTestUtils.setField(target, "dao", dao);
                ReflectionTestUtils.setField(target, "validators", validators);
                OrderAccessionService numbers = support.proxy(target, manager, OrderAccessionService.class);
                assertEquals("LAB0000000000006", numbers.preview(null));
                assertEquals("LAB0000000000006", numbers.preview(null));
                assertEquals(3, support.scalar(connection,
                        "select cur_val from clinlims.accession_number_info where prefix='LAB'"));
                assertEquals(1, support.scalar(connection, "select count(*) from clinlims.sample"));
                var pool = Executors.newFixedThreadPool(2);
                var gate = new CountDownLatch(1);
                try {
                    List<Future<String>> saves = new ArrayList<>();
                    for (int id : List.of(100, 101))
                        saves.add(pool.submit(() -> {
                            gate.await();
                            return tx.execute(status -> {
                                String assigned = numbers.generate(null);
                                em.createNativeQuery(
                                        "insert into clinlims.sample(id,accession_number) values (:id,:number)")
                                        .setParameter("id", id).setParameter("number", assigned).executeUpdate();
                                return assigned;
                            });
                        }));
                    gate.countDown();
                    assertEquals(Set.of("LAB0000000000006", "LAB0000000000007"),
                            Set.of(saves.get(0).get(15, TimeUnit.SECONDS), saves.get(1).get(15, TimeUnit.SECONDS)));
                } finally {
                    pool.shutdownNow();
                }
                try {
                    tx.execute(status -> {
                        assertEquals("LAB0000000000008", numbers.generate(null));
                        em.createNativeQuery(
                                "insert into clinlims.sample(id,accession_number) values (102,'LAB0000000000008')")
                                .executeUpdate();
                        // A later CUG rejection must also roll back the laboratory counter and parent.
                        throw new SampleCugConflictException();
                    });
                    fail();
                } catch (SampleCugConflictException expected) {
                }
                assertEquals(7, support.scalar(connection,
                        "select cur_val from clinlims.accession_number_info where prefix='LAB'"));
                assertEquals(0, support.scalar(connection, "select count(*) from clinlims.sample where id=102"));
                assertEquals("LAB0000000000008", numbers.preview(null));
                tx.execute(status -> {
                    assertEquals("LAB0000000000025", numbers.confirm("LAB0000000000025", false));
                    em.createNativeQuery(
                            "insert into clinlims.sample(id,accession_number) values (103,'LAB0000000000025')")
                            .executeUpdate();
                    return null;
                });
                try {
                    numbers.confirm("LAB0000000000025", false);
                    fail();
                } catch (OrderAccessionConflictException expected) {
                }
                assertEquals("LAB0000000000026", numbers.preview(null));
                try {
                    tx.execute(status -> {
                        em.createNativeQuery(
                                "insert into clinlims.sample(id,accession_number) values (104,'LAB0000000000025')")
                                .executeUpdate();
                        return null;
                    });
                    fail();
                } catch (RuntimeException error) {
                    assertTrue(OrderAccessionConflictException.isConflict(error));
                }
            }
            var migration = support.migration(connection, "liquibase/3.4.x.x/099-order-accession-confirmation.xml");
            migration.rollback(2, "");
            assertEquals(4, support.scalar(connection, "select count(*) from clinlims.sample"));
            // Normal installations already have a unique accession index. Keep that
            // protection, including when this migration is subsequently rolled back.
            support.migration(connection, "liquibase/order-accession-existing-index.xml").update("");
            migration.update("");
            assertEquals(4, support.scalar(connection, "select count(*) from clinlims.sample"));
            assertEquals(0, support.scalar(connection,
                    "select count(*) from pg_indexes where schemaname='clinlims' and indexname='idx_sample_order_accession_unique'"));
            migration.rollback(2, "");
            assertEquals(1, support.scalar(connection,
                    "select count(*) from pg_indexes where schemaname='clinlims' and indexname='legacy_sample_accession_key'"));
            assertEquals(4, support.scalar(connection, "select count(*) from clinlims.sample"));
        }
    }
}
