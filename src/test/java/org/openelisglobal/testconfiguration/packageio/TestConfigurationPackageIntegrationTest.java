package org.openelisglobal.testconfiguration.packageio;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.persistence.EntityManager;
import java.util.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.*;
import org.openelisglobal.testconfiguration.packageio.dao.TestConfigurationPackageDAOImpl;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage.*;
import org.openelisglobal.testconfiguration.packageio.service.*;
import org.openelisglobal.testconfiguration.packageio.valueholder.TestConfigurationImportLock;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Uses only an explicitly configured disposable database; never the
 * application's datasource.
 */
public class TestConfigurationPackageIntegrationTest {
    private static SessionFactory factory;
    private static EntityManager em;
    private static TransactionTemplate transaction;
    private static TestConfigurationPackageDAOImpl dao;
    private static TestConfigurationPackageService service;
    private static org.openelisglobal.test.service.TestCreationOriginService origins;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> legacyIds = new LinkedHashMap<>();
    private static final org.openelisglobal.test.service.SystemTestCatalog installation = new org.openelisglobal.test.service.SystemTestCatalog();

    @BeforeClass
    public static void isolatedDatabase() {
        String url = System.getenv("TEST_PACKAGE_DATABASE_URL");
        Assume.assumeTrue("Set TEST_PACKAGE_DATABASE_URL to a disposable database ending in configpackage_test",
                url != null);
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/configpackage_test"))
            throw new IllegalArgumentException("Disposable local database required");
        Configuration config = TestPackageOrmValidationTest.configuration(false);
        config.setProperty("hibernate.connection.url", url);
        config.setProperty("hibernate.connection.username", "postgres");
        config.setProperty("hibernate.connection.password", "configuration_test_only");
        config.setProperty("hibernate.hbm2ddl.auto", "create");
        config.setProperty("hibernate.hbm2ddl.create_namespaces", "true");
        try (SessionFactory bootstrap = config.buildSessionFactory();
                org.hibernate.Session session = bootstrap.openSession()) {
            migrate(session, true);
            var setup = session.beginTransaction();
            for (String kind : List.of("SEEDED", "USER", "NULL_GUID")) {
                var test = new org.openelisglobal.test.valueholder.Test();
                test.setDescription("Legacy origin " + kind);
                test.setLocalCode("L" + kind);
                test.setStoredName(test.getDescription());
                test.setIsActive("Y");
                test.setGuid("NULL_GUID".equals(kind) ? null
                        : "SEEDED".equals(kind) ? installation.guids().iterator().next()
                                : UUID.randomUUID().toString());
                session.persist(test);
                legacyIds.put(kind, test.getId());
            }
            setup.commit();
            migrate(session, false);
        }
        Configuration complete = TestPackageOrmValidationTest.configuration();
        complete.setProperty("hibernate.connection.url", url);
        complete.setProperty("hibernate.connection.username", "postgres");
        complete.setProperty("hibernate.connection.password", "configuration_test_only");
        factory = complete.buildSessionFactory();
        JpaTransactionManager manager = new JpaTransactionManager(factory);
        em = SharedEntityManagerCreator.createSharedEntityManager(factory);
        transaction = new TransactionTemplate(manager);
        var originDao = new org.openelisglobal.test.daoimpl.TestCreationOriginDAOImpl();
        ReflectionTestUtils.setField(originDao, "entityManager", em);
        ReflectionTestUtils.setField(originDao, "systemTestCatalog", installation);
        var originTarget = new org.openelisglobal.test.service.TestCreationOriginServiceImpl();
        ReflectionTestUtils.setField(originTarget, "dao", originDao);
        ReflectionTestUtils.setField(originTarget, "systemTestCatalog", installation);
        ProxyFactory originProxy = new ProxyFactory(originTarget);
        originProxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        origins = (org.openelisglobal.test.service.TestCreationOriginService) originProxy.getProxy();
        dao = new TestConfigurationPackageDAOImpl();
        ReflectionTestUtils.setField(dao, "entityManager", em);
        ReflectionTestUtils.setField(dao, "systemTestCatalog", installation);
        TestConfigurationPackageServiceImpl target = new TestConfigurationPackageServiceImpl();
        ReflectionTestUtils.setField(target, "dao", dao);
        org.openelisglobal.common.service.UserFieldOptionResolver resolver = new org.openelisglobal.common.service.UserFieldOptionResolver();
        ReflectionTestUtils.setField(resolver, "authorizationCatalogService",
                new org.openelisglobal.security.service.AuthorizationCatalogService());
        ReflectionTestUtils.setField(target, "userFieldOptionResolver", resolver);
        ReflectionTestUtils.setField(target, "events",
                org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class));
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (TestConfigurationPackageService) proxy.getProxy();
        assertNotNull(transaction.execute(s -> em.find(TestConfigurationImportLock.class, 1)).originKey);
    }

    private static void migrate(org.hibernate.Session session, boolean verifyEmptyDatabaseAndRollback) {
        session.doWork(connection -> {
            try {
                liquibase.database.Database database = liquibase.database.DatabaseFactory.getInstance()
                        .findCorrectDatabaseImplementation(new liquibase.database.jvm.JdbcConnection(connection) {
                            @Override
                            public void close() {
                                // Hibernate owns this borrowed connection and closes it with the session
                                // factory.
                            }
                        });
                try (liquibase.Liquibase migration = new liquibase.Liquibase(
                        "test-configuration-packages-changelog.xml",
                        new liquibase.resource.ClassLoaderResourceAccessor(), database)) {
                    migration.update(new liquibase.Contexts());
                    if (verifyEmptyDatabaseAndRollback)
                        migration.rollback(3, "");
                }
            } catch (liquibase.exception.LiquibaseException e) {
                throw new java.sql.SQLException(e);
            }
        });
    }

    @AfterClass
    public static void close() {
        if (factory != null)
            factory.close();
    }

    private ImportRequest bundle(String suffix) {
        ImportRequest req = new ImportRequest();
        req.bundle = new TestConfigurationPackage();
        req.bundle.roots.add("test-" + suffix);
        add(req, "test-" + suffix, "Test",
                Map.of("description", "Test " + suffix, "localCode", suffix, "isActive", "Y", "storedName",
                        "Test " + suffix),
                Map.of("localizedTestName", "name-" + suffix, "defaultTestResult", "result-" + suffix));
        add(req, "name-" + suffix, "Localization", Map.of("english", "Test " + suffix, "french", "Test " + suffix),
                Map.of());
        add(req, "result-" + suffix, "TestResult", Map.of("testResultType", "N", "value", "0", "isActive", true),
                Map.of("test", "test-" + suffix));
        add(req, "field-" + suffix, "TestAdditionalFieldDefinition", Map.of("fieldKey", "required_count", "displayName",
                "Count", "fieldType", "NUMBER", "required", true, "active", true, "sortOrder", 1),
                Map.of("testId", "test-" + suffix));
        return req;
    }

    private void add(ImportRequest req, String key, String kind, Map<String, ?> values, Map<String, String> refs) {
        Node n = new Node();
        n.key = key;
        n.kind = kind;
        n.catalog = !org.openelisglobal.testconfiguration.packageio.dao.TestPackageRegistry.OWNED.contains(kind);
        values.forEach((p, v) -> n.values.put(p, JSON.valueToTree(v)));
        n.references.putAll(refs);
        req.bundle.nodes.add(n);
    }

    @Test
    public void selectorExcludesInstalledTestsEvenAfterEditingAndIncludesLegacyUserDefinitions() {
        // Only installation identities and explicit system markers are excluded;
        // historical custom definitions do not require manual confirmation.
        var unclassified = origins.unclassifiedTests().stream().map(t -> t.get("id")).toList();
        assertFalse(unclassified.contains(legacyIds.get("SEEDED")));
        assertTrue(unclassified.contains(legacyIds.get("USER")));
        assertTrue(unclassified.contains(legacyIds.get("NULL_GUID")));
        var initial = service.tests().stream().map(t -> t.get("id")).toList();
        assertFalse(initial.contains(legacyIds.get("SEEDED")));
        assertTrue(initial.contains(legacyIds.get("USER")));
        assertTrue(initial.contains(legacyIds.get("NULL_GUID")));
        try {
            origins.classify(List.of(legacyIds.get("SEEDED")), true, "17");
            fail("An installation identity cannot be classified as a user-created test");
        } catch (IllegalArgumentException expected) {
            assertEquals("testPackage.issue.conflicts", expected.getMessage());
        }
        origins.recognizeConfigurationTest(legacyIds.get("SEEDED"));
        origins.classify(List.of(legacyIds.get("USER"), legacyIds.get("NULL_GUID")), true, "17");
        var ids = service.tests().stream().map(t -> t.get("id")).toList();
        assertFalse(ids.contains(legacyIds.get("SEEDED")));
        assertTrue(ids.contains(legacyIds.get("USER")));
        assertTrue(ids.contains(legacyIds.get("NULL_GUID")));
        assertFalse(transaction.execute(
                s -> em.find(org.openelisglobal.test.valueholder.Test.class, legacyIds.get("SEEDED")).isUserCreated()));
        transaction.executeWithoutResult(s -> {
            // The edit screens build detached Test instances; origin must survive that
            // merge.
            var test = em.find(org.openelisglobal.test.valueholder.Test.class, legacyIds.get("SEEDED"));
            em.detach(test);
            test.setDescription("Renamed installed test");
            test.setUserCreated(true);
            em.merge(test);
        });
        assertFalse(service.tests().stream().anyMatch(t -> legacyIds.get("SEEDED").equals(t.get("id"))));
        assertTrue(transaction.execute(s -> dao.list("Test")).stream()
                .anyMatch(t -> legacyIds.get("SEEDED").equals(t.id())));
        assertEquals("17", transaction.execute(s -> em
                .find(org.openelisglobal.test.valueholder.TestCreationOrigin.class, legacyIds.get("USER")).actorId));
        // A configuration file cannot reclassify an explicitly verified user
        // definition.
        origins.recognizeConfigurationTest(legacyIds.get("USER"));
        assertTrue(service.tests().stream().anyMatch(t -> legacyIds.get("USER").equals(t.get("id"))));
    }

    @Test
    public void classificationConflictRollsBackAllOriginUpdates() {
        String id = transaction.execute(s -> {
            var test = new org.openelisglobal.test.valueholder.Test();
            test.setDescription("Unknown classification conflict");
            test.setLocalCode("OR_CONFL");
            test.setIsActive("Y");
            test.setCreationOriginVerified(false);
            em.persist(test);
            return test.getId();
        });
        try {
            origins.classify(List.of(id, "999999999"), true, "17");
            fail("Missing ID must reject the entire classification");
        } catch (IllegalArgumentException expected) {
            assertEquals("testPackage.issue.conflicts", expected.getMessage());
        }
        assertFalse(transaction
                .execute(s -> em.find(org.openelisglobal.test.valueholder.Test.class, id).isCreationOriginVerified()));
        origins.classify(List.of(id), false, "17");
        assertFalse(service.tests().stream().anyMatch(t -> id.equals(t.get("id"))));
    }

    @Test
    public void selectorIncludesNewUserDefinitionsAndCopiesButExcludesStartupConfiguration() {
        String created = transaction.execute(s -> dao.insert("Test",
                Map.of("description", "Manual origin", "localCode", "ORG_MAN", "isActive", "Y"), Map.of(), "1"));
        String startup = transaction.execute(s -> {
            String nameId = dao.insert("Localization", Map.of("english", "Startup origin", "french", "Startup origin"),
                    Map.of(), "1");
            var test = new org.openelisglobal.test.valueholder.Test();
            test.setDescription("Startup origin");
            test.setLocalCode("ORG_BOOT");
            test.setStoredName(test.getDescription());
            test.setLocalizedTestName(em.find(org.openelisglobal.localization.valueholder.Localization.class, nameId));
            test.setIsActive("Y");
            test.setGuid(installation.guids().stream().skip(1).findFirst().orElseThrow());
            test.setUserCreated(false);
            em.persist(test);
            return test.getId();
        });
        assertTrue(service.tests().stream().anyMatch(t -> created.equals(t.get("id"))));
        assertFalse(service.tests().stream().anyMatch(t -> startup.equals(t.get("id"))));
        // Provenance is local metadata, never imported from the source installation.
        var exported = service.exportTests(List.of(startup));
        assertTrue(exported.nodes.stream().noneMatch(n -> n.values.containsKey("userCreated")));
        ImportRequest copy = new ImportRequest();
        copy.bundle = exported;
        copy.copy = true;
        copy.overrides.put(exported.roots.get(0), Map.of("description", TextNode.valueOf("User copy of startup"),
                "localCode", TextNode.valueOf("ORG_COPY")));
        copy.previewToken = service.preview(copy).token;
        assertTrue(service.preview(copy).ready);
        String copied = service.importPackage(copy, "1").testIds().values().iterator().next();
        assertTrue(service.tests().stream().anyMatch(t -> copied.equals(t.get("id"))));
        assertFalse(transaction.execute(
                s -> installation.contains(em.find(org.openelisglobal.test.valueholder.Test.class, copied).getGuid())));
    }

    @Test
    public void realRoundTripPreservesRequiredFieldsDefaultResultsAndIdempotency() {
        ImportRequest req = bundle("ROUNDTRIP");
        req.previewToken = service.preview(req).token;
        ImportResult saved = service.importPackage(req, "17");
        String id = saved.testIds().get("test-ROUNDTRIP");
        assertTrue(service.tests().stream().anyMatch(t -> id.equals(t.get("id"))));
        TestConfigurationPackage exported = service.exportTests(List.of(id));
        Node test = exported.nodes.stream().filter(n -> "Test".equals(n.kind)).findFirst().orElseThrow();
        Node result = exported.nodes.stream().filter(n -> n.key.equals(test.references.get("defaultTestResult")))
                .findFirst().orElseThrow();
        assertEquals(test.key, result.references.get("test"));
        assertTrue(exported.nodes.stream().filter(n -> "TestAdditionalFieldDefinition".equals(n.kind))
                .allMatch(n -> n.values.get("required").asBoolean()));
        assertTrue(service.importPackage(req, "17").alreadyImported());
        assertEquals(saved.testIds(), service.importPackage(req, "17").testIds());
        assertFalse(test.values.containsKey("id"));
        assertFalse(test.values.containsKey("guid"));
    }

    @Test
    public void sameJsonCreatesIndependentTestsOncePerReviewedIdentity() {
        ImportRequest req = bundle("VARIANTS");
        int journalCount = service.history().size();
        req.overrides.put("test-VARIANTS", Map.of("description", TextNode.valueOf("Import variant one"),
                "localCode", TextNode.valueOf("IMP_V1")));
        req.previewToken = service.preview(req).token;
        ImportResult first = service.importPackage(req, "17");
        String firstId = first.testIds().get("test-VARIANTS");
        assertTrue(service.tests().stream().anyMatch(t -> firstId.equals(t.get("id"))));

        req.overrides.put("test-VARIANTS", Map.of("description", TextNode.valueOf("Import variant two"),
                "localCode", TextNode.valueOf("IMP_V2")));
        Preview preview = service.preview(req);
        assertFalse(preview.alreadyImported);
        assertTrue(preview.ready);
        req.previewToken = preview.token;
        ImportResult second = service.importPackage(req, "17");
        String secondId = second.testIds().get("test-VARIANTS");
        assertFalse(second.alreadyImported());
        assertNotEquals(firstId, secondId);
        assertTrue(service.tests().stream().anyMatch(t -> secondId.equals(t.get("id"))));
        assertEquals(second.testIds(), service.importPackage(req, "17").testIds());

        req.overrides.put("test-VARIANTS", Map.of("description", TextNode.valueOf("Import variant one"),
                "localCode", TextNode.valueOf("IMP_V1")));
        req.previewToken = service.preview(req).token;
        assertTrue(service.importPackage(req, "17").alreadyImported());
        assertEquals(first.testIds(), service.importPackage(req, "17").testIds());
        assertEquals(journalCount + 2, service.history().size());
        transaction.executeWithoutResult(s -> {
            var tests = dao.list("Test");
            assertEquals(1, tests.stream().filter(t -> "IMP_V1".equals(t.values().get("localCode"))).count());
            assertEquals(1, tests.stream().filter(t -> "IMP_V2".equals(t.values().get("localCode"))).count());
            for (String id : List.of(firstId, secondId)) {
                var test = dao.snapshot("Test", id);
                var field = dao.related("TestAdditionalFieldDefinition", "testId", List.of(id));
                assertEquals(1, field.size());
                assertEquals("required_count", field.get(0).values().get("fieldKey"));
                var result = dao.snapshot("TestResult", test.links().get("defaultTestResult").id());
                assertEquals(id, result.links().get("test").id());
            }
        });
    }

    @Test
    public void legacyHistoryPreservesReplaysAndAllowsANewTestFromTheSameJson() {
        ImportRequest req = bundle("LEG_RETRY");
        req.overrides.put("test-LEG_RETRY", Map.of("description", TextNode.valueOf("Legacy import original"),
                "localCode", TextNode.valueOf("LEG_ONE")));
        req.previewToken = service.preview(req).token;
        ImportResult first = service.importPackage(req, "17");
        TestConfigurationPackageServiceImpl target = org.springframework.test.util.AopTestUtils.getTargetObject(service);
        String legacyFingerprint = ReflectionTestUtils.invokeMethod(target, "packageFingerprint", req.bundle);
        transaction.executeWithoutResult(s -> em
                .find(org.openelisglobal.testconfiguration.packageio.valueholder.TestConfigurationRevision.class,
                        first.revisionId())
                .fingerprint = legacyFingerprint);

        assertTrue(service.preview(req).alreadyImported);
        assertEquals(first.testIds(), service.importPackage(req, "17").testIds());
        req.overrides.put("test-LEG_RETRY", Map.of("description", TextNode.valueOf("Legacy import new test"),
                "localCode", TextNode.valueOf("LEG_TWO")));
        Preview preview = service.preview(req);
        assertFalse(preview.alreadyImported);
        assertTrue(preview.ready);
        req.previewToken = preview.token;
        ImportResult second = service.importPackage(req, "17");
        assertFalse(second.alreadyImported());
        assertNotEquals(first.testIds(), second.testIds());
        assertEquals(second.testIds(), service.importPackage(req, "17").testIds());
        assertTrue(service.importPackage(req, "17").alreadyImported());
    }

    @Test
    public void insertFailureRollsBackPreviouslyInsertedTestsAndJournal() {
        ImportRequest req = bundle("ROLLBACK");
        TestConfigurationPackageServiceImpl target = org.springframework.test.util.AopTestUtils
                .getTargetObject(service);
        TestConfigurationPackageDAOImpl failing = org.mockito.Mockito.spy(dao);
        org.mockito.Mockito.doThrow(new IllegalStateException("Simulated persistence failure")).when(failing).insert(
                org.mockito.ArgumentMatchers.eq("TestAdditionalFieldDefinition"), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.anyString());
        ReflectionTestUtils.setField(target, "dao", failing);
        int journalCount = service.history().size();
        req.previewToken = service.preview(req).token;
        try {
            service.importPackage(req, "17");
            fail("Required database column should reject insert");
        } catch (IllegalStateException expected) {
            assertEquals("Simulated persistence failure", expected.getMessage());
        } finally {
            ReflectionTestUtils.setField(target, "dao", dao);
        }
        assertFalse(service.tests().stream().anyMatch(t -> "Test ROLLBACK".equals(t.get("name"))));
        assertEquals(journalCount, service.history().size());
    }

    @Test
    public void copyingDoesNotRenameTheOriginalDefinition() {
        ImportRequest original = bundle("ORIGINAL");
        original.previewToken = service.preview(original).token;
        String originalId = service.importPackage(original, "17").testIds().get("test-ORIGINAL");
        ImportRequest copy = new ImportRequest();
        copy.bundle = service.exportTests(List.of(originalId));
        copy.copy = true;
        copy.overrides.put(copy.bundle.roots.get(0),
                Map.of("description", TextNode.valueOf("Copied test"), "localCode", TextNode.valueOf("COPY")));
        copy.previewToken = service.preview(copy).token;
        String newId = service.importPackage(copy, "17").testIds().values().iterator().next();
        assertNotEquals(originalId, newId);
        assertEquals("Test ORIGINAL",
                transaction.execute(s -> dao.snapshot("Test", originalId).values().get("description")));
        assertEquals("Copied test",
                transaction.execute(s -> dao
                        .snapshot("Localization", dao.snapshot("Test", newId).links().get("localizedTestName").id())
                        .values().get("english")));
    }

    @Test
    public void standaloneCopiesPreserveOwnConfigurationAndAllowManualParentLinks() {
        String typeId = transaction.execute(s -> dao.insert("TypeOfTestResult",
                Map.of("testResultType", "D", "description", "Dictionary"), Map.of(), "17"));
        ImportRequest req = bundle("FULLA");
        req.bundle.nodes.removeIf(n -> n.kind.equals("TestResult"));
        req.bundle.nodes.get(0).references.put("defaultTestResult", "dictionary-result");
        req.bundle.nodes.get(0).values.put("resultDisplayConfigJson",
                TextNode.valueOf("{\"tubeQuantitySource\":true}"));
        add(req, "test-FULLB", "Test",
                Map.of("description", "Test FULLB", "localCode", "FULLB", "isActive", "Y", "directSampleUsageEnabled",
                        false, "resultDisplayConfigJson", "{\"tubeUsage\":{\"childBlockEnabled\":true}}"),
                Map.of("localizedTestName", "name-FULLB"));
        add(req, "name-FULLB", "Localization", Map.of("english", "Test FULLB", "french", "Test FULLB"), Map.of());
        req.bundle.roots.add("test-FULLB");
        add(req, "sample-kind", "TypeOfSample",
                Map.of("description", "Blood", "localAbbreviation", "PKGBLOOD", "domain", "H", "active", true),
                Map.of());
        add(req, "sample-link", "TypeOfSampleTest", Map.of(),
                Map.of("testId", "test-FULLA", "typeOfSampleId", "sample-kind"));
        add(req, "panel", "Panel",
                Map.of("panelName", "Package panel", "description", "Package panel", "isActive", "Y"), Map.of());
        add(req, "panel-link", "PanelItem", Map.of("sortOrder", "1"), Map.of("test", "test-FULLA", "panel", "panel"));
        add(req, "category", "DictionaryCategory",
                Map.of("categoryName", "Package options", "description", "Package options"), Map.of());
        add(req, "dictionary", "Dictionary",
                Map.of("dictEntry", "Present", "isActive", "Y", "localAbbreviation", "PKGPRESENT"),
                Map.of("dictionaryCategory", "category"));
        add(req, "dictionary-result", "TestResult", Map.of("testResultType", "D", "isActive", true),
                Map.of("test", "test-FULLA", "value", "dictionary"));
        add(req, "result-type", "TypeOfTestResult", Map.of("testResultType", "D", "description", "Dictionary"),
                Map.of());
        add(req, "range", "ResultLimit", Map.of("minAge", 0.0, "maxAge", 130.0, "alwaysValidate", false),
                Map.of("testId", "test-FULLA", "resultTypeId", "result-type", "dictionaryNormalId", "dictionary"));
        add(req, "analyte", "Analyte", Map.of("analyteName", "Package analyte"), Map.of());
        add(req, "test-analyte", "TestAnalyte", Map.of("sortOrder", "1"),
                Map.of("test", "test-FULLA", "analyte", "analyte"));
        add(req, "reflex", "TestReflex", Map.of(), Map.of("test", "test-FULLA", "addedTest", "test-FULLB", "testResult",
                "dictionary-result", "testAnalyte", "test-analyte"));
        add(req, "dependency", "TestParentChildDependency",
                Map.of("active", true, "sampleUsageSource", "SAMPLE_ITEM_REMAINING"),
                Map.of("parentTest", "test-FULLA", "childTest", "test-FULLB"));
        add(req, "select-field", "TestAdditionalFieldDefinition",
                Map.of("fieldKey", "reception", "displayName", "Reception", "fieldType", "SELECT", "required", true,
                        "active", true, "sortOrder", 2, "defaultValue", "received"),
                Map.of("testId", "test-FULLA"));
        add(req, "select-option", "TestAdditionalFieldOption",
                Map.of("optionKey", "received", "optionLabel", "Received", "active", true, "sortOrder", 1),
                Map.of("fieldDefinitionId", "select-field"));
        for (Node n : req.bundle.nodes)
            if (n.catalog)
                req.catalogs.put(n.key, n.key.equals("result-type") ? typeId : "CREATE");
        ImportRequest first = independent(req, "test-FULLA");
        first.previewToken = service.preview(first).token;
        ImportResult source = service.importPackage(first, "17");
        ImportRequest second = independent(req, "test-FULLB");
        second.previewToken = service.preview(second).token;
        String originalB = service.importPackage(second, "17").testIds().get("test-FULLB");
        String originalA = source.testIds().get("test-FULLA");
        transaction.execute(s -> {
            dao.insert("TestParentChildDependency",
                    Map.of("active", true, "sampleUsageSource", "SAMPLE_ITEM_REMAINING"),
                    Map.of("parentTest", originalA, "childTest", originalB), "17");
            dao.insert("TestReflex", Map.of(),
                    Map.of("test", originalA, "addedTest", originalB, "testResult",
                            dao.snapshot("Test", originalA).links().get("defaultTestResult").id(), "testAnalyte",
                            dao.related("TestAnalyte", "test.id", List.of(originalA)).get(0).id()),
                    "17");
            dao.flush();
            return null;
        });
        TestConfigurationPackage exported = service.exportTests(List.of(source.testIds().get("test-FULLA")));
        assertEquals(1, exported.roots.size());
        assertFalse(exported.nodes.stream()
                .anyMatch(n -> Set.of("TestParentChildDependency", "TestReflex").contains(n.kind)));
        ImportRequest copy = new ImportRequest();
        copy.bundle = exported;
        copy.copy = true;
        int code = 1;
        for (Node n : exported.nodes)
            if (n.kind.equals("Test"))
                copy.overrides.put(n.key,
                        Map.of("description", TextNode.valueOf(n.values.get("description").asText() + " copy"),
                                "localCode", TextNode.valueOf("CPY" + code++)));
        copy.previewToken = service.preview(copy).token;
        assertTrue(service.preview(copy).ready);
        ImportResult target = service.importPackage(copy, "17");
        assertEquals(1, target.testIds().size());
        String copiedA = target.testIds().get("test-FULLA");
        assertNotEquals(source.testIds().get("test-FULLA"), copiedA);
        transaction.execute(s -> {
            assertTrue(dao.related("TestReflex", "test.id", List.of(copiedA)).isEmpty());
            assertTrue(dao.related("TestParentChildDependency", "parentTest.id", List.of(copiedA)).isEmpty());
            assertEquals(1, dao.related("TestReflex", "test.id", List.of(originalA)).size());
            assertEquals(originalB, dao.related("TestParentChildDependency", "parentTest.id", List.of(originalA)).get(0)
                    .links().get("childTest").id());
            var testResult = dao.snapshot("TestResult",
                    dao.snapshot("Test", copiedA).links().get("defaultTestResult").id());
            assertEquals(copiedA, testResult.links().get("test").id());
            assertEquals(dao.related("ResultLimit", "testId", List.of(copiedA)).get(0).links().get("dictionaryNormalId")
                    .id(), testResult.links().get("value").id());
            assertEquals(1, dao.related("PanelItem", "test.id", List.of(copiedA)).size());
            assertEquals(1, dao.related("TypeOfSampleTest", "testId", List.of(copiedA)).size());
            var field = dao.related("TestAdditionalFieldDefinition", "testId", List.of(copiedA)).stream()
                    .filter(f -> "reception".equals(f.values().get("fieldKey"))).findFirst().orElseThrow();
            assertEquals("received", field.values().get("defaultValue"));
            assertEquals("Received", dao.related("TestAdditionalFieldOption", "fieldDefinitionId", List.of(field.id()))
                    .get(0).values().get("optionLabel"));
            return null;
        });
        ImportRequest childCopy = new ImportRequest();
        childCopy.bundle = service.exportTests(List.of(originalB));
        childCopy.copy = true;
        childCopy.overrides.put("test-FULLB", Map.of("description", TextNode.valueOf("Independent child copy"),
                "localCode", TextNode.valueOf("CHILDCOPY")));
        childCopy.previewToken = service.preview(childCopy).token;
        String copiedB = service.importPackage(childCopy, "17").testIds().get("test-FULLB");
        transaction.execute(s -> {
            assertTrue(dao.related("TestParentChildDependency", "childTest.id", List.of(copiedB)).isEmpty());
            dao.insert("TestParentChildDependency",
                    Map.of("active", true, "sampleUsageSource", "SAMPLE_ITEM_REMAINING"),
                    Map.of("parentTest", copiedA, "childTest", copiedB), "17");
            dao.flush();
            assertEquals(copiedB, dao.related("TestParentChildDependency", "parentTest.id", List.of(copiedA)).get(0)
                    .links().get("childTest").id());
            return null;
        });
    }

    private ImportRequest independent(ImportRequest source, String testKey) {
        ImportRequest result = new ImportRequest();
        result.bundle = service.prepareImport(source.bundle, testKey);
        Set<String> keys = result.bundle.nodes.stream().map(n -> n.key).collect(java.util.stream.Collectors.toSet());
        source.catalogs.forEach((key, value) -> {
            if (keys.contains(key))
                result.catalogs.put(key, value);
        });
        return result;
    }
}
