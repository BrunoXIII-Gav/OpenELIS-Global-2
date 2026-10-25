package org.openelisglobal.common.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.Entity;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.audittrail.daoimpl.AuditEventDAOImpl;
import org.openelisglobal.audittrail.form.AuditEventFilter;
import org.openelisglobal.audittrail.valueholder.History;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.common.daoimpl.DatabaseCleanDAOImpl;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DefaultConfigurationProperties;
import org.openelisglobal.history.service.HistoryService;
import org.openelisglobal.security.service.UserPermissionService;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Exercises the real HQL, foreign keys and transaction rollback in a disposable
 * database.
 */
public class DatabaseCleanupIntegrationTest {
    private Object previousFactory;
    private Object previousMessages;

    @org.junit.Before
    public void configureDateFormatting() {
        previousFactory = ReflectionTestUtils.getField(org.openelisglobal.spring.util.SpringContext.class, "factory");
        previousMessages = ReflectionTestUtils.getField(org.openelisglobal.internationalization.MessageUtil.class,
                "instance");
        var beans = mock(org.springframework.beans.factory.config.AutowireCapableBeanFactory.class);
        var configuration = mock(DefaultConfigurationProperties.class);
        when(beans.getBean(DefaultConfigurationProperties.class)).thenReturn(configuration);
        when(configuration.getPropertyValue(Property.AmbiguousDateHolder)).thenReturn("X");
        when(configuration.getPropertyValue(Property.DEFAULT_DATE_LOCALE)).thenReturn("en-US");
        ReflectionTestUtils.setField(org.openelisglobal.spring.util.SpringContext.class, "factory", beans);
        var messages = new org.springframework.context.support.StaticMessageSource();
        messages.addMessage("date.format.formatKey", java.util.Locale.US, "MM/dd/yyyy");
        messages.addMessage("time.format.formatKey", java.util.Locale.US, "HH:mm");
        messages.addMessage("timestamp.format.formatKey", java.util.Locale.US, "MM/dd/yyyy HH:mm");
        org.openelisglobal.internationalization.MessageUtil.setMessageSource(messages);
    }

    @org.junit.After
    public void restoreDateFormatting() {
        ReflectionTestUtils.setField(org.openelisglobal.spring.util.SpringContext.class, "factory", previousFactory);
        ReflectionTestUtils.setField(org.openelisglobal.internationalization.MessageUtil.class, "instance",
                previousMessages);
    }

    @Test
    public void cleanupPreservesConfigurationIdentitiesAndCountersAndArchivesOnlyClinicalHistory() throws Exception {
        try (var database = new PostgreSQLContainer<>("postgres:14.4")) {
            database.start();
            try (Connection connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                    database.getPassword())) {
                connection.createStatement().execute("CREATE SCHEMA clinlims; CREATE DOMAIN public.longtext AS text");
            }
            try (SessionFactory factory = configuration(database).buildSessionFactory();
                    Connection connection = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                            database.getPassword())) {
                // Simulate the exact pre-migration state, then apply and roll back the
                // production migration before fixtures are loaded.
                connection.createStatement().execute("DROP TABLE clinlims.archived_audit_event;"
                        + " ALTER TABLE clinlims.sample_environmental DROP COLUMN last_updated");
                var migration = new Liquibase("liquibase/3.4.x.x/093-training-cleanup-audit-archive.xml",
                        new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
                migration.update("");
                migration.rollback(2, "");
                migration.update("");
                new Liquibase("liquibase/database-cleanup-test-constraints.xml", new ClassLoaderResourceAccessor(),
                        new JdbcConnection(connection)).update("");
                var codeMigration = new Liquibase("liquibase/3.4.x.x/094-training-cleanup-code-reset.xml",
                        new ClassLoaderResourceAccessor(), new JdbcConnection(connection));
                codeMigration.update("");
                codeMigration.rollback(1, "");
                codeMigration.update("");
                assertEquals(901, scalar(connection, "SELECT last_value FROM clinlims.sample_cug_prefix_seq"));
                var fixtures = new FlatXmlDataSetBuilder().setColumnSensing(true)
                        .build(getClass().getClassLoader().getResourceAsStream("testdata/database-cleanup.xml"));
                var fixtureConnection = new DatabaseConnection(connection, "clinlims");
                fixtureConnection.getConfig().setProperty(org.dbunit.database.DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                        new org.dbunit.ext.postgresql.PostgresqlDataTypeFactory());
                DatabaseOperation.INSERT.execute(fixtureConnection, fixtures);
                connection.commit();
                // Lock existing data (one analysis is delivered); cleanup must still wipe it.
                new Liquibase("liquibase/3.4.x.x/103-delivered-analysis-lock.xml", new ClassLoaderResourceAccessor(),
                        new JdbcConnection(connection)).update("");

                var manager = SharedEntityManagerCreator.createSharedEntityManager(factory);
                var cleanup = new DatabaseCleanDAOImpl();
                ReflectionTestUtils.setField(cleanup, "entityManager", manager);
                var audit = new AuditEventDAOImpl();
                ReflectionTestUtils.setField(audit, "entityManager", manager);
                var history = mock(HistoryService.class);
                var failReceipt = new java.util.concurrent.atomic.AtomicBoolean(false);
                when(history.save(any(History.class))).thenAnswer(invocation -> {
                    if (failReceipt.getAndSet(false))
                        throw new IllegalStateException("Simulated cleanup receipt failure");
                    History receipt = invocation.getArgument(0);
                    manager.persist(receipt);
                    return receipt;
                });
                var permissions = mock(UserPermissionService.class);
                when(permissions.hasPermission("7", SystemPermission.GLOBAL_ADMIN)).thenReturn(true);
                var settings = mock(DefaultConfigurationProperties.class);
                when(settings.getPropertyValueLowerCase(Property.TrainingInstallation)).thenReturn("true");
                when(settings.getPropertyValueUpperCase(Property.AccessionFormat)).thenReturn("PREFIXNUM");
                when(settings.getPropertyValueUpperCase(Property.ACCESSION_NUMBER_PREFIX)).thenReturn("LNG");
                var target = new DatabaseCleanServiceImpl(cleanup, permissions, settings, history, audit);
                ProxyFactory proxy = new ProxyFactory(target);
                proxy.addAdvice(new TransactionInterceptor(new JpaTransactionManager(factory),
                        new AnnotationTransactionAttributeSource()));
                DatabaseCleanService service = (DatabaseCleanService) proxy.getProxy();
                var transactions = new org.springframework.transaction.support.TransactionTemplate(
                        new JpaTransactionManager(factory));
                // Even direct access to the operation is refused while clinical
                // data remains, and does not leave the LNG counter reset.
                try {
                    transactions.execute(status -> {
                        cleanup.resetTrainingCodes("LNG");
                        return null;
                    });
                    fail("Public codes must not be reset while clinical data remains");
                } catch (RuntimeException expected) {
                    assertTrue(rootMessage(expected).contains("Clear clinical data"));
                    assertEquals(67, scalar(connection, "SELECT cur_val FROM clinlims.accession_number_info"
                            + " WHERE prefix='LNG' AND type='PREFIXNUM'"));
                }
                connection.createStatement().execute(
                        "UPDATE clinlims.site_information SET value='false'" + " WHERE name='TrainingInstallation'");
                connection.commit();
                try {
                    transactions.execute(status -> {
                        cleanup.resetTrainingCodes("LNG");
                        return null;
                    });
                    fail("Database-side protection must refuse production resets");
                } catch (RuntimeException expected) {
                    assertTrue(rootMessage(expected).contains("training installation"));
                }
                connection.createStatement().execute(
                        "UPDATE clinlims.site_information SET value='true'" + " WHERE name='TrainingInstallation'");
                connection.commit();

                long sequence = scalar(connection, "SELECT nextval('clinlims.patient_seq')");
                assertEquals(1000, sequence);
                var countersBefore = counters(connection);
                var retainedBefore = retainedState(connection);
                assertEquals(4, count(connection, "clinlims.patient"));
                // Force a late FK failure after the archive and clinical deletes.
                // A failed cleanup must not leave data or audit half-cleared.
                try {
                    service.cleanDatabase("7");
                    fail("Unrecognized references must abort cleanup, never cascade into their table");
                } catch (RuntimeException expected) {
                    assertEquals(4, count(connection, "clinlims.patient"));
                    assertEquals(1, count(connection, "clinlims.sample"));
                    assertEquals(0, count(connection, "clinlims.archived_audit_event"));
                    assertEquals(2, count(connection, "clinlims.system_user"));
                }
                connection.createStatement().execute("DELETE FROM clinlims.cleanup_test_hold");
                connection.commit();
                // This fails after both public codes were reset. The CUG reset
                // must roll back too; setval() would incorrectly survive it.
                failReceipt.set(true);
                try {
                    service.cleanDatabase("7");
                    fail("Receipt failure must roll back deletions and both public-code resets");
                } catch (IllegalStateException expected) {
                    assertEquals(4, count(connection, "clinlims.patient"));
                    assertEquals(0, count(connection, "clinlims.archived_audit_event"));
                    assertEquals(67, scalar(connection, "SELECT cur_val FROM clinlims.accession_number_info"
                            + " WHERE prefix='LNG' AND type='PREFIXNUM'"));
                    assertEquals(901, scalar(connection, "SELECT last_value FROM clinlims.sample_cug_prefix_seq"));
                }
                service.cleanDatabase("7");

                for (String entity : List.of("patient", "sample", "sample_item", "analysis", "result", "result_file",
                        "sample_environmental", "patient_identity", "patient_contact",
                        "analysis_additional_field_value", "sample_storage_assignment", "sample_storage_movement",
                        "sample_order_additional_field_file"))
                    assertEquals(entity, 0, count(connection, "clinlims." + entity));
                for (String table : List.of("provider", "organization_contact", "test", "test_section",
                        "test_additional_field_def", "storage_room", "site_information"))
                    assertEquals(table, 1, count(connection, "clinlims." + table));
                assertEquals(2, count(connection, "clinlims.system_user"));
                assertEquals(retainedBefore, retainedState(connection));
                assertEquals(3, count(connection, "clinlims.person"));
                assertEquals(3, count(connection, "clinlims.person_address"));
                assertEquals(1, scalar(connection, "SELECT count(*) FROM clinlims.system_user WHERE id=7"
                        + " AND linked_provider_person_id=100 AND login_name='admin' AND signature_image_data='signature'"));
                assertEquals(1, scalar(connection, "SELECT count(*) FROM clinlims.provider WHERE id=9"
                        + " AND person_id=100 AND professional_profile_code='GENETIST' AND specialty='Genetics'"));
                assertEquals(countersBefore, counters(connection));
                assertEquals(sequence + 1, scalar(connection, "SELECT nextval('clinlims.patient_seq')"));
                assertEquals(1, scalar(connection, "SELECT nextval('clinlims.sample_cug_prefix_seq')"));
                assertEquals(0, scalar(connection, "SELECT cur_val FROM clinlims.accession_number_info"
                        + " WHERE prefix='LNG' AND type='PREFIXNUM'"));
                assertEquals(500,
                        scalar(connection, "SELECT cur_val FROM clinlims.accession_number_info WHERE prefix='LNG26'"));
                assertEquals(700, scalar(connection, "SELECT cur_val FROM clinlims.accession_number_info"
                        + " WHERE prefix='OTHER' AND type='PREFIXNUM'"));
                assertEquals(900, scalar(connection, "SELECT cur_val FROM clinlims.accession_number_info"
                        + " WHERE prefix='LNG' AND type='ALPHANUM'"));
                var accession = new org.openelisglobal.common.dao.AccessionDAOImpl();
                ReflectionTestUtils.setField(accession, "entityManager", manager);
                assertEquals(1, accession.getNextNumberNoIncrement("LNG",
                        org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat.PREFIXNUM));
                assertEquals(Long.valueOf(1), transactions.execute(status -> accession.getNextNumberIncrement("LNG",
                        org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat.PREFIXNUM)));
                assertEquals(1, count(connection, "clinlims.history"));
                assertEquals(4, count(connection, "clinlims.archived_audit_event"));
                assertEquals(8, count(connection, "clinlims.audit_event"));

                try (var session = factory.openSession()) {
                    var queryDAO = new AuditEventDAOImpl();
                    ReflectionTestUtils.setField(queryDAO, "entityManager", session);
                    var page = queryDAO.search(filter(null));
                    assertEquals(4, page.total()); // professional, user, configuration, cleanup receipt
                    assertTrue(page.items().stream().noneMatch(item -> item.id().startsWith("clinical-")));
                    assertEquals(0, queryDAO.search(filter("result")).total());
                    assertEquals(1, queryDAO.search(filter("provider")).total());
                }
                // A second cleanup is idempotent, including archived rows and receipts.
                var subsequent = new FlatXmlDataSetBuilder().build(getClass().getClassLoader()
                        .getResourceAsStream("testdata/database-cleanup-subsequent-audit.xml"));
                DatabaseOperation.INSERT.execute(fixtureConnection, subsequent);
                connection.commit();
                try (var session = factory.openSession()) {
                    var queryDAO = new AuditEventDAOImpl();
                    ReflectionTestUtils.setField(queryDAO, "entityManager", session);
                    var freshResults = queryDAO.search(filter("result"));
                    assertEquals(1, freshResults.total());
                    assertEquals("subsequent-result", freshResults.items().get(0).id());
                    // Reusing the same LNG and CUG does not bring back archived
                    // events belonging to the previous, deleted clinical IDs.
                    for (String code : List.of("LNG0000000000001", "1.1")) {
                        var reused = queryDAO.search(
                                new AuditEventFilter(code, "result", null, null, null, null, null, null, null, 0, 100));
                        assertEquals(1, reused.total());
                        assertEquals("subsequent-result", reused.items().get(0).id());
                    }
                }
                service.cleanDatabase("7");
                assertEquals(5, count(connection, "clinlims.archived_audit_event"));
                assertEquals(2, count(connection, "clinlims.system_user"));
                assertEquals(1, count(connection, "clinlims.history"));
                assertEquals(1, scalar(connection, "SELECT nextval('clinlims.sample_cug_prefix_seq')"));
                assertEquals(0, scalar(connection, "SELECT cur_val FROM clinlims.accession_number_info"
                        + " WHERE prefix='LNG' AND type='PREFIXNUM'"));
                // The append-only ledger still rejects destructive changes.
                try {
                    connection.createStatement().execute("DELETE FROM clinlims.audit_event");
                    fail("Audit snapshots must remain immutable");
                } catch (java.sql.SQLException expected) {
                    connection.rollback();
                }
                try {
                    migration.rollback(2, "");
                    fail("Rollback must not expose archived clinical history again");
                } catch (liquibase.exception.LiquibaseException expected) {
                    assertTrue(expected.toString().contains("Archive cleanup receipts"));
                    connection.rollback();
                    assertEquals(5, count(connection, "clinlims.archived_audit_event"));
                }
            }
        }
    }

    private Configuration configuration(PostgreSQLContainer<?> database) throws Exception {
        var configuration = new Configuration().configure("hibernate/hibernate.cfg.xml");
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("org.openelisglobal"))
            configuration.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
        configuration.setProperty("hibernate.connection.url", database.getJdbcUrl()
                + (database.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=clinlims,public");
        configuration.setProperty("hibernate.connection.username", database.getUsername());
        configuration.setProperty("hibernate.connection.password", database.getPassword());
        configuration.setProperty("hibernate.search.enabled", "false");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-only");
        configuration.setProperty("hibernate.hbm2ddl.halt_on_error", "true");
        return configuration;
    }

    private AuditEventFilter filter(String module) {
        return new AuditEventFilter(null, module, null, null, null, null, null, null, null, 0, 100);
    }

    private long count(Connection connection, String table) throws Exception {
        return scalar(connection, "SELECT count(*) FROM " + table);
    }

    private String rootMessage(Throwable failure) {
        while (failure.getCause() != null)
            failure = failure.getCause();
        return failure.getMessage();
    }

    private java.util.Map<String, Object> counters(Connection connection) throws Exception {
        var counters = new java.util.LinkedHashMap<String, Object>();
        try (var rows = connection.createStatement().executeQuery("SELECT sequencename,last_value FROM pg_sequences"
                + " WHERE schemaname='clinlims' AND sequencename NOT IN ('history_seq','sample_cug_prefix_seq') ORDER BY sequencename")) {
            while (rows.next())
                counters.put(rows.getString(1), rows.getObject(2));
        }
        return counters;
    }

    private java.util.Map<String, String> retainedState(Connection connection) throws Exception {
        var state = new java.util.LinkedHashMap<String, String>();
        for (String table : List.of("system_user", "login_user", "system_role", "system_user_role", "provider",
                "organization_contact", "test", "test_section", "test_additional_field_def", "storage_room",
                "site_information")) {
            try (var rows = connection.createStatement().executeQuery("SELECT jsonb_agg(to_jsonb(t)"
                    + " ORDER BY to_jsonb(t)::text)::text FROM clinlims." + table + " t")) {
                assertTrue(rows.next());
                state.put(table, rows.getString(1));
            }
        }
        return state;
    }

    private long scalar(Connection connection, String sql) throws Exception {
        try (var result = connection.createStatement().executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }
}
