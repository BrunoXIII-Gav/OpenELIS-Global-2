package org.openelisglobal.testconfiguration.packageio;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.*;
import org.openelisglobal.testconfiguration.packageio.dao.*;
import org.openelisglobal.testconfiguration.packageio.dao.TestConfigurationPackageDAO.*;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage;
import org.openelisglobal.testconfiguration.packageio.form.TestConfigurationPackage.*;
import org.openelisglobal.testconfiguration.packageio.service.TestConfigurationPackageServiceImpl;
import org.openelisglobal.testconfiguration.packageio.valueholder.TestConfigurationRevision;
import org.springframework.test.util.ReflectionTestUtils;

public class TestConfigurationPackageServiceTest {
    private static SessionFactory factory;
    private static final ObjectMapper JSON = new ObjectMapper();
    private TestConfigurationPackageDAO dao;
    private TestConfigurationPackageServiceImpl service;
    private ImportRequest request;

    @BeforeClass
    public static void mappings() {
        factory = TestPackageOrmValidationTest.configuration().buildSessionFactory();
    }

    @AfterClass
    public static void close() {
        factory.close();
    }

    @Before
    public void setup() {
        EntityManager em = mock(EntityManager.class);
        EntityManagerFactory emf = mock(EntityManagerFactory.class);
        when(em.getEntityManagerFactory()).thenReturn(emf);
        when(emf.unwrap(SessionFactoryImplementor.class)).thenReturn(factory.unwrap(SessionFactoryImplementor.class));
        TestConfigurationPackageDAOImpl metadataDao = new TestConfigurationPackageDAOImpl();
        ReflectionTestUtils.setField(metadataDao, "entityManager", em);
        dao = mock(TestConfigurationPackageDAO.class);
        when(dao.attributes(anyString())).thenAnswer(a -> metadataDao.attributes(a.getArgument(0)));
        when(dao.findRevision(anyString())).thenReturn(Optional.empty());
        when(dao.latestRevision(anyString())).thenReturn(Optional.empty());
        AtomicInteger counter = new AtomicInteger(1000);
        when(dao.insert(anyString(), anyMap(), anyMap(), anyString()))
                .thenAnswer(a -> String.valueOf(counter.incrementAndGet()));
        service = new TestConfigurationPackageServiceImpl();
        ReflectionTestUtils.setField(service, "dao", dao);
        org.openelisglobal.common.service.UserFieldOptionResolver resolver = new org.openelisglobal.common.service.UserFieldOptionResolver();
        ReflectionTestUtils.setField(resolver, "authorizationCatalogService",
                new org.openelisglobal.security.service.AuthorizationCatalogService());
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", resolver);
        ReflectionTestUtils.setField(service, "events",
                mock(org.springframework.context.ApplicationEventPublisher.class));
        request = new ImportRequest();
        request.bundle = new TestConfigurationPackage();
        request.bundle.roots.add("test-a");
        node("test-a", "Test", Map.of("description", "DNA test", "localCode", "DNA"), Map.of());
    }

    private Node node(String key, String kind, Map<String, ?> values, Map<String, String> references) {
        Node node = new Node();
        node.key = key;
        node.kind = kind;
        node.catalog = !TestPackageRegistry.OWNED.contains(kind);
        values.forEach((p, v) -> node.values.put(p, JSON.valueToTree(v)));
        node.references.putAll(references);
        request.bundle.nodes.add(node);
        if ("Test".equals(kind) && !node.references.containsKey("localizedTestName")) {
            node.references.put("localizedTestName", key + "-name");
            node(key + "-name", "Localization", Map.of("english", String.valueOf(values.get("description")), "french",
                    String.valueOf(values.get("description"))), Map.of());
        }
        return node;
    }

    private void failure(String code, Runnable operation) {
        try {
            operation.run();
            fail("Expected " + code);
        } catch (IllegalArgumentException e) {
            assertEquals("testPackage.issue." + code, e.getMessage());
        }
    }

    @Test
    public void packageCannotOverrideLocalCreationOrigin() {
        request.bundle.nodes.get(0).values.put("userCreated", BooleanNode.FALSE);
        failure("property", () -> service.preview(request));
        verify(dao, never()).insert(anyString(), anyMap(), anyMap(), anyString());
    }

    @Test
    public void legacyPackageCanImportOneTestWithoutItsParentChildOrReflexLinks() {
        request.bundle.nodes.get(0).values.put("resultDisplayConfigJson",
                TextNode.valueOf("{\"tubeQuantitySource\":true}"));
        node("test-b", "Test", Map.of("description", "Other test", "localCode", "OTHER", "resultDisplayConfigJson",
                "{\"tubeUsage\":{\"childBlockEnabled\":true}}"), Map.of());
        request.bundle.roots.add("test-b");
        node("result-a", "TestResult", Map.of("testResultType", "N", "value", "0"), Map.of("test", "test-a"));
        node("result-b", "TestResult", Map.of("testResultType", "N", "value", "1"), Map.of("test", "test-b"));
        node("field-a", "TestAdditionalFieldDefinition", Map.of("fieldKey", "count", "displayName", "Count",
                "fieldType", "NUMBER", "required", true, "active", true, "sortOrder", 1), Map.of("testId", "test-a"));
        node("field-b", "TestAdditionalFieldDefinition", Map.of("fieldKey", "other", "displayName", "Other",
                "fieldType", "TEXT", "required", false, "active", true, "sortOrder", 1), Map.of("testId", "test-b"));
        node("dependency", "TestParentChildDependency",
                Map.of("active", true, "sampleUsageSource", "SAMPLE_ITEM_REMAINING"),
                Map.of("parentTest", "test-a", "childTest", "test-b"));
        node("reflex", "TestReflex", Map.of(),
                Map.of("test", "test-a", "addedTest", "test-b", "testResult", "result-a"));
        TestConfigurationPackage source = request.bundle;
        TestConfigurationPackage prepared = service.prepareImport(source, "test-a");
        assertEquals(List.of("test-a"), prepared.roots);
        assertEquals(Set.of("test-a", "test-a-name", "result-a", "field-a"),
                prepared.nodes.stream().map(n -> n.key).collect(java.util.stream.Collectors.toSet()));
        assertEquals(2, source.roots.size());
        assertTrue(source.nodes.stream().anyMatch(n -> "dependency".equals(n.key)));
        request.bundle = prepared;
        request.previewToken = service.preview(request).token;
        assertEquals(1, service.importPackage(request, "17").testIds().size());
        verify(dao, times(1)).insert(eq("Test"), anyMap(), anyMap(), anyString());
        verify(dao, never()).insert(eq("TestParentChildDependency"), anyMap(), anyMap(), anyString());
        verify(dao, never()).insert(eq("TestReflex"), anyMap(), anyMap(), anyString());
    }

    @Test
    public void selectingAnUnknownTestFromAnArchiveIsRejected() {
        failure("selection", () -> service.prepareImport(request.bundle, "unknown-test"));
    }

    @Test
    public void importingMultipleTestsDirectlyIsRejectedWithoutWriting() {
        node("test-b", "Test", Map.of("description", "Other test", "localCode", "OTHER"), Map.of());
        request.bundle.roots.add("test-b");
        failure("selection", () -> service.importPackage(request, "17"));
        verify(dao, never()).lockImports();
        verify(dao, never()).insert(anyString(), anyMap(), anyMap(), anyString());
    }

    @Test
    public void exportingMultipleTestsIsRejected() {
        failure("selection", () -> service.exportTests(List.of("11", "12")));
        verify(dao, never()).snapshot(anyString(), anyString());
    }

    @Test
    public void exportingDoesNotReadOrExpandRelatedTests() {
        when(dao.snapshot("Test", "11")).thenReturn(new Snapshot("Test", "11", "test-a",
                Map.of("description", "DNA test", "localCode", "DNA"), Map.of("localizedTestName", new Link("Localization", "21"))));
        when(dao.snapshot("Localization", "21")).thenReturn(new Snapshot("Localization", "21", "test-name",
                Map.of("english", "DNA test", "french", "DNA test"), Map.of()));
        TestConfigurationPackage exported = service.exportTests(List.of("11"));
        assertEquals(List.of("test-a"), exported.roots);
        assertEquals(1, exported.nodes.stream().filter(n -> "Test".equals(n.kind)).count());
        verify(dao, never()).list("TestParentChildDependency");
        verify(dao, never()).list("TestReflex");
    }

    @Test
    public void copiesRequiredFieldsAndRemapsAllOwnedReferences() {
        node("field-a", "TestAdditionalFieldDefinition", Map.of("fieldKey", "tube_count", "displayName", "Tube count",
                "fieldType", "NUMBER", "required", true, "active", true, "sortOrder", 2), Map.of("testId", "test-a"));
        node("result-a", "TestResult", Map.of("testResultType", "N", "value", "0"), Map.of("test", "test-a"));
        request.bundle.nodes.get(0).references.put("defaultTestResult", "result-a");
        request.previewToken = service.preview(request).token;
        ImportResult saved = service.importPackage(request, "17");
        assertEquals(1, saved.testIds().size());
        verify(dao).insert(eq("TestAdditionalFieldDefinition"), argThat(v -> Boolean.TRUE.equals(v.get("required"))),
                argThat(r -> saved.testIds().get("test-a").equals(r.get("testId"))), eq("17"));
        verify(dao).link(eq("Test"), eq(saved.testIds().get("test-a")), eq("defaultTestResult"), anyString());
        verify(dao).saveIdentity(eq("Test"), eq(saved.testIds().get("test-a")), eq("test-a"), eq(saved.revisionId()));
    }

    @Test
    public void emptyCodesBecomeNullInsteadOfDuplicateEmptyStrings() {
        request.bundle.nodes.get(0).values.put("localCode", TextNode.valueOf("  "));
        request.previewToken = service.preview(request).token;
        service.importPackage(request, "17");
        verify(dao).insert(eq("Test"), argThat(v -> v.containsKey("localCode") && v.get("localCode") == null), anyMap(),
                eq("17"));
    }

    @Test public void duplicateNameAndCodeRequireExplicitChanges() {
        when(dao.list("Test")).thenReturn(List.of(new Snapshot("Test","9","existing",Map.of("description"," DNA TEST ","localCode","dna"),Map.of())));
        Preview preview=service.preview(request);
        assertFalse(preview.ready); assertTrue(preview.issues.stream().anyMatch(i->i.code().equals("name")));
        assertTrue(preview.issues.stream().anyMatch(i->i.code().equals("code")));
        request.overrides.put("test-a",Map.of("description",TextNode.valueOf("DNA v2"),"localCode",TextNode.valueOf("DNA2")));
        assertTrue(service.preview(request).ready);
    }

    @Test
    public void existingCatalogIsReusedAndItsExportedDependenciesAreNotCreated() {
        node("unit-a", "TestSection", Map.of("testSectionName", "Genetics", "description", "Genetics"),
                Map.of("localization", "unit-name"));
        node("unit-name", "Localization", Map.of("english", "Genetics", "french", "Génétique"), Map.of());
        request.bundle.nodes.get(0).references.put("testSection", "unit-a");
        when(dao.list("TestSection")).thenReturn(List.of(new Snapshot("TestSection", "88", "destination-unit",
                Map.of("testSectionName", "Genetics", "description", "Genetics"), Map.of())));
        Preview preview = service.preview(request);
        assertTrue(preview.ready);
        assertEquals("88", preview.catalogs.get(0).suggestedId());
        request.previewToken = preview.token;
        service.importPackage(request, "17");
        verify(dao).insert(eq("Localization"), argThat(v -> "DNA test".equals(v.get("english"))), anyMap(),
                anyString());
        verify(dao, never()).insert(eq("TestSection"), anyMap(), anyMap(), anyString());
        verify(dao).insert(eq("Test"), anyMap(), argThat(r -> "88".equals(r.get("testSection"))), eq("17"));
    }

    @Test
    public void unmatchedCatalogRequiresAnExplicitMappingAndCannotSilentlyCrossUnits() {
        node("unit-a", "TestSection", Map.of("testSectionName", "Genetics", "description", "Genetics"), Map.of());
        request.bundle.nodes.get(0).references.put("testSection", "unit-a");
        assertFalse(service.preview(request).ready);
        request.catalogs.put("unit-a", "CREATE");
        assertTrue(service.preview(request).ready);
    }

    @Test
    public void cannotInjectPatientEntitiesDatabaseIdsOrExecutableScripts() {
        request.bundle.nodes.get(0).kind = "Patient";
        failure("reference", () -> service.preview(request));
        request.bundle.nodes.get(0).kind = "Test";
        request.bundle.nodes.get(0).values.put("id", TextNode.valueOf("113"));
        failure("property", () -> service.preview(request));
        request.bundle.nodes.get(0).values.remove("id");
        node("script-a", "Scriptlet", Map.of("scriptletName", "unsafe", "code", "anything"), Map.of());
        request.bundle.nodes.get(0).references.put("scriptlet", "script-a");
        failure("property", () -> service.preview(request));
    }

    @Test
    public void defaultResultCannotBelongToAnotherTest() {
        node("test-b", "Test", Map.of("description", "Other test", "localCode", "OTHER"), Map.of());
        request.bundle.roots.add("test-b");
        node("result-b", "TestResult", Map.of("testResultType", "N", "value", "0"), Map.of("test", "test-b"));
        request.bundle.nodes.get(0).references.put("defaultTestResult", "result-b");
        failure("ownership", () -> service.preview(request));
    }

    @Test
    public void circularParentChildRelationsAreRejectedBeforeSaving() {
        request.bundle.nodes.get(0).values.put("resultDisplayConfigJson",
                TextNode.valueOf("{\"tubeQuantitySource\":true,\"tubeUsage\":{\"childBlockEnabled\":true}}"));
        node("test-b", "Test", Map.of("description", "Other test", "localCode", "OTHER", "resultDisplayConfigJson",
                "{\"tubeQuantitySource\":true,\"tubeUsage\":{\"childBlockEnabled\":true}}"), Map.of());
        request.bundle.roots.add("test-b");
        node("dep-a", "TestParentChildDependency", Map.of("active", true, "sampleUsageSource", "SAMPLE_ITEM_REMAINING"),
                Map.of("parentTest", "test-a", "childTest", "test-b"));
        node("dep-b", "TestParentChildDependency", Map.of("active", true, "sampleUsageSource", "SAMPLE_ITEM_REMAINING"),
                Map.of("parentTest", "test-b", "childTest", "test-a"));
        failure("cycle", () -> service.preview(request));
        verify(dao, never()).insert(anyString(), anyMap(), anyMap(), anyString());
    }

    @Test
    public void replayReturnsOriginalIdsWithoutWritingAgain() {
        request.previewToken = service.preview(request).token;
        TestConfigurationRevision saved = new TestConfigurationRevision();
        saved.id = "revision";
        saved.mappingJson = "{\"tests\":{\"test-a\":\"1234\"}}";
        when(dao.findRevision(anyString())).thenReturn(Optional.of(saved));
        ImportResult result = service.importPackage(request, "17");
        assertTrue(result.alreadyImported());
        assertEquals("1234", result.testIds().get("test-a"));
        verify(dao, never()).insert(anyString(), anyMap(), anyMap(), anyString());
    }

    private Map<String, TestConfigurationRevision> rememberImports() {
        Map<String, TestConfigurationRevision> revisions = new HashMap<>();
        when(dao.findRevision(anyString())).thenAnswer(a -> Optional.ofNullable(revisions.get(a.getArgument(0))));
        doAnswer(a -> {
            TestConfigurationRevision revision = a.getArgument(0);
            revisions.put(revision.fingerprint, revision);
            return null;
        }).when(dao).saveRevision(any(TestConfigurationRevision.class));
        return revisions;
    }

    private ImportResult importReviewed() {
        Preview preview = service.preview(request);
        assertTrue(preview.ready);
        request.previewToken = preview.token;
        return service.importPackage(request, "17");
    }

    private void useIdentity(String name, String code) {
        request.overrides.put("test-a",
                Map.of("description", TextNode.valueOf(name), "localCode", TextNode.valueOf(code)));
    }

    private ImportResult rememberLegacyImport(Map<String, TestConfigurationRevision> revisions) {
        ImportResult saved = importReviewed();
        TestConfigurationRevision revision = revisions.values().iterator().next();
        revisions.clear();
        revision.fingerprint = ReflectionTestUtils.invokeMethod(service, "packageFingerprint", request.bundle);
        revisions.put(revision.fingerprint, revision);
        return saved;
    }

    @Test
    public void sameJsonCanCreateDifferentTestsAndEachReplayReturnsItsOwnIds() {
        rememberImports();
        useIdentity("First DNA test", "DNA1");
        ImportResult first = importReviewed();
        useIdentity("Second DNA test", "DNA2");
        Preview preview = service.preview(request);
        assertFalse(preview.alreadyImported);
        ImportResult second = importReviewed();
        assertFalse(second.alreadyImported());
        assertNotEquals(first.testIds(), second.testIds());
        assertEquals(second.testIds(), importReviewed().testIds());
        useIdentity("First DNA test", "DNA1");
        assertTrue(service.preview(request).alreadyImported);
        assertEquals(first.testIds(), importReviewed().testIds());
        verify(dao, times(2)).insert(eq("Test"), anyMap(), anyMap(), anyString());
        verify(dao, times(2)).saveRevision(any(TestConfigurationRevision.class));
    }

    @Test
    public void replayUsesEffectiveIdentityAndIgnoresJsonNodeOrder() {
        rememberImports();
        ImportResult saved = importReviewed();
        Collections.reverse(request.bundle.nodes);
        useIdentity("DNA test", "DNA");
        assertTrue(service.preview(request).alreadyImported);
        assertEquals(saved.testIds(), importReviewed().testIds());
        verify(dao, times(1)).insert(eq("Test"), anyMap(), anyMap(), anyString());
    }

    @Test
    public void legacyReplayStillReturnsExistingTestWithoutInserting() {
        Map<String, TestConfigurationRevision> revisions = rememberImports();
        useIdentity("Previously imported test", "OLD");
        ImportResult saved = rememberLegacyImport(revisions);
        assertTrue(service.preview(request).alreadyImported);
        ImportResult replay = importReviewed();
        assertTrue(replay.alreadyImported());
        assertEquals(saved.testIds(), replay.testIds());
        verify(dao, times(1)).insert(eq("Test"), anyMap(), anyMap(), anyString());
    }

    @Test
    public void legacyImportOfSameJsonDoesNotBlockANewIdentity() {
        Map<String, TestConfigurationRevision> revisions = rememberImports();
        useIdentity("Previously imported test", "OLD");
        ImportResult first = rememberLegacyImport(revisions);
        useIdentity("New test from same JSON", "NEW");
        assertFalse(service.preview(request).alreadyImported);
        ImportResult second = importReviewed();
        assertFalse(second.alreadyImported());
        assertNotEquals(first.testIds(), second.testIds());
        assertTrue(importReviewed().alreadyImported());
        verify(dao, times(2)).insert(eq("Test"), anyMap(), anyMap(), anyString());
    }

    @Test
    public void changingOnlyNameOrCodeStillBlocksDuplicatesOfLegacyImport() {
        Map<String, TestConfigurationRevision> revisions = rememberImports();
        ImportResult saved = rememberLegacyImport(revisions);
        when(dao.list("Test")).thenReturn(List.of(new Snapshot("Test", saved.testIds().get("test-a"), "test-a",
                Map.of("description", "DNA test", "localCode", "DNA"), Map.of())));
        useIdentity("Another name", "DNA");
        Preview nameChanged = service.preview(request);
        assertFalse(nameChanged.alreadyImported);
        assertFalse(nameChanged.ready);
        assertTrue(nameChanged.issues.stream().anyMatch(i -> "code".equals(i.code())));
        request.previewToken = nameChanged.token;
        failure("conflicts", () -> service.importPackage(request, "17"));
        useIdentity("DNA test", "DNA2");
        Preview codeChanged = service.preview(request);
        assertFalse(codeChanged.alreadyImported);
        assertFalse(codeChanged.ready);
        assertTrue(codeChanged.issues.stream().anyMatch(i -> "name".equals(i.code())));
        verify(dao, times(1)).insert(eq("Test"), anyMap(), anyMap(), anyString());
    }

    @Test
    public void changingCatalogDestinationDoesNotReturnAnUnrelatedPreviousImport() {
        Map<String, TestConfigurationRevision> revisions = rememberImports();
        node("unit-a", "TestSection", Map.of("testSectionName", "Genetics", "description", "Genetics"), Map.of());
        request.bundle.nodes.get(0).references.put("testSection", "unit-a");
        when(dao.list("TestSection")).thenReturn(List.of(
                new Snapshot("TestSection", "88", "unit-one", Map.of("testSectionName", "Genetics"), Map.of()),
                new Snapshot("TestSection", "99", "unit-two", Map.of("testSectionName", "Other unit"), Map.of())));
        request.catalogs.put("unit-a", "88");
        ImportResult saved = rememberLegacyImport(revisions);
        when(dao.list("Test")).thenReturn(List.of(new Snapshot("Test", saved.testIds().get("test-a"), "test-a",
                Map.of("description", "DNA test", "localCode", "DNA"), Map.of())));
        request.catalogs.put("unit-a", "99");
        Preview preview = service.preview(request);
        assertFalse(preview.alreadyImported);
        assertFalse(preview.ready);
        request.previewToken = preview.token;
        failure("conflicts", () -> service.importPackage(request, "17"));
        verify(dao, times(1)).insert(eq("Test"), anyMap(), anyMap(), anyString());
    }

    @Test
    public void editingAfterReviewInvalidatesConfirmationToken() {
        request.previewToken = service.preview(request).token;
        request.overrides.put("test-a", Map.of("description", TextNode.valueOf("Changed")));
        failure("preview", () -> service.importPackage(request, "17"));
        verify(dao, never()).lockImports();
    }

    @Test
    public void copyGetsSeparateConfigurationLineage() {
        request.copy = true;
        request.previewToken = service.preview(request).token;
        ImportResult result = service.importPackage(request, "17");
        verify(dao).saveIdentity(eq("Test"), eq(result.testIds().get("test-a")), argThat(k -> !"test-a".equals(k)),
                eq(result.revisionId()));
    }

    @Test
    public void userDefaultsAndMetadataIdsCannotCarryUserAssignments() {
        node("field-a", "TestAdditionalFieldDefinition", Map.of("fieldKey", "reviewer", "displayName", "Reviewer",
                "fieldType", "USER", "required", true, "active", true, "sortOrder", 1, "defaultValue", "116"),
                Map.of("testId", "test-a"));
        failure("user", () -> service.preview(request));
        request.bundle.nodes.stream().filter(n -> "field-a".equals(n.key)).findFirst().orElseThrow().values
                .remove("defaultValue");
        request.bundle.nodes.stream().filter(n -> "field-a".equals(n.key)).findFirst().orElseThrow().values
                .put("metadataJson", TextNode.valueOf("{\"userIds\":[116]}"));
        failure("metadataReference", () -> service.preview(request));
    }
}
