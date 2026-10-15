package org.openelisglobal.audittrail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.BaseWebContextSensitiveTest;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.audittrail.form.AuditEventFilter;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.person.valueholder.Person;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Transactional
public class AuditEventAccessionIntegrationTest extends BaseWebContextSensitiveTest {
    @Autowired
    private AuditEventDAO auditEventDAO;
    @Autowired
    private org.openelisglobal.testconfiguration.packageio.service.TestConfigurationPackageService testCatalog;
    @Autowired
    private org.openelisglobal.test.service.SystemTestCatalog installationTests;

    @PersistenceContext
    private EntityManager entityManager;

    @Before
    public void loadFixtures() throws Exception {
        executeDataSetWithStateManagement("testdata/audit-accession.xml");
        for (String entity : List.of("Sample", "SampleItem", "Analysis", "Result", "Patient", "Person", "ResultFile")) {
            event(entity, "9201");
        }
        event("Sample", "9202");
        entityManager.flush();
    }

    @Test
    public void searchesExistingSampleWithClinicalDescendants() {
        var page = auditEventDAO.search(filter("AUDIT-LAB-0066"));
        assertEquals(7, page.total());
        assertTrue(page.items().stream().allMatch(item -> "9201".equals(item.entityId())));
        assertTrue(page.items().stream().anyMatch(item -> "ResultFile".equals(item.entityName())));
    }

    @Test
    public void searchesByCugWithTheSameClinicalHistory() {
        assertEquals(7, auditEventDAO.search(filter("AUDIT-CUG-0066")).total());
    }

    @Test
    public void missingAccessionReturnsEmptyHistory() {
        assertEquals(0, auditEventDAO.search(filter("MISSING-LAB")).total());
    }

    @Test
    public void accessionDoesNotMatchResultValues() {
        assertEquals(0, auditEventDAO.search(filter("9.28")).total());
    }

    @Test
    public void filtersClinicalHistoryByModule() {
        var filter = new AuditEventFilter("AUDIT-LAB-0066", "result", null, null, null, null, null, null, null, 0, 30);
        var page = auditEventDAO.search(filter);
        assertEquals(3, page.total());
        assertTrue(page.items().stream().allMatch(item -> "result".equals(item.module())));
    }

    @Test
    public void paginatesFilteredHistoryWithoutReturningOtherSamples() {
        var first = auditEventDAO
                .search(new AuditEventFilter("AUDIT-LAB-0066", null, null, null, null, null, null, null, null, 0, 2));
        var next = auditEventDAO
                .search(new AuditEventFilter("AUDIT-LAB-0066", null, null, null, null, null, null, null, null, 1, 2));
        assertEquals(7, first.total());
        assertEquals(7, next.total());
        assertEquals(2, first.items().size());
        assertEquals(2, next.items().size());
        var ids = new HashSet<String>();
        first.items().forEach(item -> ids.add(item.id()));
        next.items().forEach(item -> ids.add(item.id()));
        assertEquals(4, ids.size());
    }

    @Test
    public void sampleFilterIncludesPreviouslyMisclassifiedAdditionalFields() {
        var stored = additionalFieldEvent("sample", "9301");
        var page = auditEventDAO.search(moduleFilter("sampleitem"));
        assertEquals(2, page.total());
        var field = page.items().stream().filter(item -> "9301".equals(item.entityId())).findFirst().orElseThrow();
        assertEquals("sampleitem", field.module());
        assertEquals("procedimiento", field.fieldLabel());
        assertEquals("sample", stored.getModule());
        String persistedModule = entityManager
                .createQuery("SELECT e.module FROM AuditEvent e WHERE e.id = :id", String.class)
                .setParameter("id", stored.getId()).getSingleResult();
        assertEquals("Reading must preserve the original immutable event", "sample", persistedModule);
    }

    @Test
    public void orderFilterExcludesSampleAdditionalFields() {
        additionalFieldEvent("sample", "9301");
        var page = auditEventDAO.search(moduleFilter("sample"));
        assertEquals(1, page.total());
        assertEquals("Sample", page.items().get(0).entityName());
    }

    @Test
    public void sampleFilterIncludesOldAndNewClassificationWithConsistentPagination() {
        additionalFieldEvent("sample", "9301");
        additionalFieldEvent("sampleitem", "9302");
        var page = auditEventDAO.search(
                new AuditEventFilter("AUDIT-CUG-0066", "sampleitem", null, null, null, null, null, null, null, 0, 2));
        assertEquals(3, page.total());
        assertEquals(2, page.items().size());
        assertTrue(page.items().stream().allMatch(item -> "sampleitem".equals(item.module())));
    }

    @Test
    public void unfilteredHistoryDisplaysSampleFieldsUnderSamples() {
        additionalFieldEvent("sample", "9301");
        var page = auditEventDAO.search(filter("AUDIT-LAB-0066"));
        assertEquals(8, page.total());
        assertEquals("sampleitem", page.items().stream().filter(item -> "9301".equals(item.entityId())).findFirst()
                .orElseThrow().module());
    }

    @Test
    public void sampleFormDefinitionsBelongToConfigurationRatherThanOrders() {
        var definition = newEvent("SampleTypeAdditionalFieldDefinition", "9301");
        definition.setModule("sample");
        entityManager.persist(definition);
        var option = newEvent("SampleTypeAdditionalFieldOption", "9302");
        option.setModule("sample");
        entityManager.persist(option);
        entityManager.flush();
        var page = auditEventDAO.search(
                new AuditEventFilter(null, "sampleadditionalfield", null, null, null, null, null, null, null, 0, 30));
        assertEquals(2, page.total());
        assertTrue(page.items().stream().allMatch(item -> "sampleadditionalfield".equals(item.module())));
    }

    private AuditEvent additionalFieldEvent(String module, String id) {
        var event = newEvent("SampleItemAdditionalFieldValue", id);
        event.setModule(module);
        event.setSampleItemId("9201");
        event.setField("fieldValue");
        event.setFieldLabel("procedimiento");
        event.setNewValue("procedimiento 2");
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    @Test
    public void groupsOrderValuesAndDocumentsUnderOrders() {
        for (String entity : List.of("SampleOrderAdditionalFieldValue", "SampleOrderAdditionalFieldFile")) {
            var event = newEvent(entity, "9401");
            event.setModule("orderadditionalfield");
            event.setField(entity.endsWith("File") ? "fileName" : "fieldValue");
            event.setSampleId("9201");
            entityManager.persist(event);
        }
        entityManager.flush();
        var page = auditEventDAO.search(moduleFilter("sample"));
        assertEquals(3, page.total());
        assertTrue(page.items().stream().allMatch(item -> "sample".equals(item.module())));
        assertEquals(1, auditEventDAO.search(moduleFilter("sampleitem")).total());
    }

    @Test
    public void excludesTechnicalHistoryBeforeCountingAndPaging() {
        for (String field : List.of("fhirUuid", "revision", "analysisId", "nextItemSequence", "syncStatus")) {
            var event = newEvent("Sample", "9201");
            event.setField(field);
            event.setModule("sample");
            entityManager.persist(event);
        }
        var reservation = newEvent("SampleCugReservation", "9501");
        reservation.setModule("sample");
        reservation.setSampleId("9201");
        reservation.setField("status");
        entityManager.persist(reservation);
        entityManager.flush();
        var page = auditEventDAO
                .search(new AuditEventFilter("AUDIT-LAB-0066", null, null, null, null, null, null, null, null, 0, 2));
        assertEquals(7, page.total());
        assertEquals(2, page.items().size());
        assertEquals(1, auditEventDAO.search(moduleFilter("sample")).total());
    }

    @Test
    public void searchesCustomFieldsByTheirVisibleLabel() {
        additionalFieldEvent("sample", "9301");
        var page = auditEventDAO.search(new AuditEventFilter("AUDIT-CUG-0066", "sampleitem", null, null, null,
                "PROCEDIMIENTO", null, null, null, 0, 30));
        assertEquals(1, page.total());
        assertEquals("procedimiento", page.items().get(0).fieldLabel());
    }

    @Test
    public void hidesRequestedResultFieldsForEveryTestWithoutDeletingHistory() {
        var hiddenIds = new java.util.ArrayList<String>();
        for (String analysisId : List.of("9201", "9603")) {
            for (String action : List.of("I", "U", "D")) {
                for (String field : List.of("status", "statusId", "startedDate", "isReportable", "referredOut")) {
                    var event = newEvent("Analysis", analysisId);
                    event.setSampleItemId("9201");
                    event.setField(field);
                    event.setAction(action);
                    entityManager.persist(event);
                    hiddenIds.add(event.getId());
                }
            }
        }
        var reportable = newEvent("Result", "9201");
        reportable.setField("isReportable");
        entityManager.persist(reportable);
        hiddenIds.add(reportable.getId());
        entityManager.flush();

        var page = auditEventDAO.search(new AuditEventFilter("AUDIT-CUG-0066", "result", null, null, null, null,
                null, null, null, 0, 2));
        assertEquals(3, page.total());
        assertEquals(2, page.items().size());
        assertEquals(7, auditEventDAO.search(filter("AUDIT-LAB-0066")).total());
        assertEquals(0, auditEventDAO.search(new AuditEventFilter("AUDIT-LAB-0066", "result", null, null, null,
                "statusId", null, null, null, 0, 30)).total());
        assertEquals(hiddenIds.size(), entityManager
                .createQuery("SELECT COUNT(e) FROM AuditEvent e WHERE e.id IN (:ids)", Long.class)
                .setParameter("ids", hiddenIds).getSingleResult().longValue());
    }

    @Test
    public void keepsStatusInOrdersAndSamplesAndReportableInTestConfiguration() {
        for (String entity : List.of("Sample", "SampleItem", "Test")) {
            var event = newEvent(entity, "9201");
            event.setField("Test".equals(entity) ? "isReportable" : "statusId");
            entityManager.persist(event);
        }
        entityManager.flush();
        assertEquals(2, auditEventDAO.search(moduleFilter("sample")).total());
        assertEquals(2, auditEventDAO.search(moduleFilter("sampleitem")).total());
        var configuration = auditEventDAO.search(new AuditEventFilter(null, "testconfiguration", null, null, null,
                "isReportable", null, null, null, 0, 30));
        assertEquals(1, configuration.total());
        assertEquals("Test", configuration.items().get(0).entityName());
    }

    @Test
    public void groupsNotesByTheirClinicalContext() {
        var event = newEvent("Note", "9601");
        event.setModule("note");
        event.setField("text");
        event.setSampleId("9201");
        event.setSampleItemId("9201");
        entityManager.persist(event);
        entityManager.flush();
        assertEquals(2, auditEventDAO.search(moduleFilter("sampleitem")).total());
        assertEquals(1, auditEventDAO.search(moduleFilter("sample")).total());
    }

    @Test
    public void groupsRequestedTestsWithSamples() {
        var event = newEvent("Analysis", "9602");
        event.setModule("analysis");
        event.setField("test");
        event.setSampleItemId("9201");
        entityManager.persist(event);
        entityManager.flush();
        assertEquals(2, auditEventDAO.search(moduleFilter("sampleitem")).total());
        assertEquals(3, auditEventDAO.search(moduleFilter("result")).total());
    }

    @Test
    public void queriesEveryBusinessGroupWithTheSameHistoricalClassification() {
        var groups = java.util.Map.of("sample", 1L, "sampleitem", 1L, "patient", 2L, "result", 3L);
        for (String module : List.of("sample", "sampleitem", "patient", "result", "resultvalidation", "storage",
                "provider", "systemuser", "reports", "reportdefinition", "sampleadditionalfield", "testconfiguration",
                "siteinformation")) {
            assertEquals(module, groups.getOrDefault(module, 0L).longValue(),
                    auditEventDAO.search(moduleFilter(module)).total());
        }
    }

    @Test
    public void professionalDetailsAppearInProfessionalsWithoutRewritingOldEvents() {
        var professional = newEvent("Person", "9201");
        professional.setModule("patient");
        professional.setSource("POST /api/OpenELIS-Global/rest/Provider/FhirUuid");
        professional.setField("email");
        professional.setOldValue("old@example.org");
        professional.setNewValue("new@example.org");
        professional.setActorId("17");
        professional.setActorName("professional-editor");
        entityManager.persist(professional);
        entityManager.flush();

        // The same Person can be used in both contexts. Its patient event stays there.
        var page = auditEventDAO.search(moduleFilter("provider"));
        assertEquals(1, page.total());
        var item = page.items().get(0);
        assertEquals("provider", item.module());
        assertEquals("old@example.org", item.oldValue());
        assertEquals("new@example.org", item.newValue());
        assertEquals("professional-editor", item.actorName());
        assertEquals("17", item.actorId());
        assertEquals(2, auditEventDAO.search(moduleFilter("patient")).total());
        assertEquals(8, auditEventDAO.search(filter("AUDIT-LAB-0066")).total());
        assertEquals("patient", entityManager
                .createQuery("SELECT e.module FROM AuditEvent e WHERE e.id = :id", String.class)
                .setParameter("id", professional.getId()).getSingleResult());
    }

    @Test
    public void capturesProfessionalNameAndContactEditsUnderProfessionals() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/api/OpenELIS-Global/rest/Provider/FhirUuid");
        var user = new UserSessionData();
        user.setSytemUserId(17);
        user.setLoginName("professional-editor");
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            Person person = entityManager.find(Person.class, "9201");
            person.setFirstName("Updated professional");
            person.setEmail("contact@example.org");
            person.setWorkPhone("5550101");
            entityManager.flush();
            // Audit writes are queued until before commit. Drain them in this
            // rollback-only test just as Hibernate does on transaction completion.
            var session = entityManager.unwrap(org.hibernate.engine.spi.SessionImplementor.class);
            session.getActionQueue().beforeTransactionCompletion();
            entityManager.flush();
            var page = auditEventDAO.search(moduleFilter("provider"));
            assertEquals(3, page.total());
            assertTrue(page.items().stream().allMatch(item -> "provider".equals(item.module())
                    && "17".equals(item.actorId()) && "professional-editor".equals(item.actorName())));
            assertEquals(java.util.Set.of("firstName", "email", "workPhone"),
                    page.items().stream().map(item -> item.field()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(2, auditEventDAO.search(moduleFilter("patient")).total());
            assertTrue(entityManager
                    .createQuery("FROM AuditEvent e WHERE e.entityName = 'Person' AND e.source = :source", AuditEvent.class)
                    .setParameter("source", "POST /api/OpenELIS-Global/rest/Provider/FhirUuid").getResultList().stream()
                    .allMatch(event -> "provider".equals(event.getModule())));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private AuditEventFilter moduleFilter(String module) {
        return new AuditEventFilter("AUDIT-CUG-0066", module, null, null, null, null, null, null, null, 0, 30);
    }

    @Test
    public void testSelectorExcludesCsvAndInstallationTestsButRetainsInactiveManualTests() {
        var seeded = entityManager.find(org.openelisglobal.test.valueholder.Test.class, "9204");
        seeded.setGuid(installationTests.guids().iterator().next());
        entityManager.flush();
        var ids = testCatalog.tests().stream().map(test -> test.get("id")).toList();
        assertTrue(ids.contains("9201"));
        assertTrue(ids.contains("9202"));
        assertTrue(!ids.contains("9203"));
        assertTrue(!ids.contains("9204"));
    }

    @Test
    public void filtersOneTestAcrossOrdersBeforeCountingAndPaging() {
        event("Analysis", "9202");
        event("Result", "9202");
        event("Analysis", "9203");
        event("Result", "9203");
        entityManager.flush();
        var first = auditEventDAO.search(testFilter(null, "9201", 0, 2));
        var next = auditEventDAO.search(testFilter(null, "9201", 1, 2));
        assertEquals(5, first.total());
        assertEquals(5, next.total());
        assertEquals(2, first.items().size());
        assertEquals(2, next.items().size());
        var ids = new HashSet<String>();
        first.items().forEach(item -> ids.add(item.id()));
        next.items().forEach(item -> ids.add(item.id()));
        assertEquals(4, ids.size());
        assertEquals(3, auditEventDAO.search(testFilter("AUDIT-LAB-0066", "9201", 0, 30)).total());
        assertEquals(2, auditEventDAO.search(testFilter("AUDIT-LAB-OTHER", "9201", 0, 30)).total());
        assertTrue(auditEventDAO.search(testFilter("AUDIT-LAB-OTHER", "9201", 0, 30)).items().stream()
                .allMatch(item -> item.recordLabel().contains("AUDIT-LAB-OTHER")
                        && item.recordLabel().contains("AUDIT-CUG-OTHER")));
        assertEquals(2, auditEventDAO.search(testFilter(null, "9202", 0, 30)).total());
        assertEquals(0, auditEventDAO.search(testFilter(null, "9999999", 0, 30)).total());
    }

    @Test
    public void combinesTestUserDateAndLabelFiltersAndPreservesDeletedAnalysisHistory() {
        var assignment = newEvent("Analysis", "9701");
        assignment.setField("test");
        assignment.setAction("D");
        assignment.setOldValue("9201");
        entityManager.persist(assignment);
        var historical = newEvent("AnalysisAdditionalFieldValue", "9702");
        historical.setModule("testadditionalfield");
        historical.setAnalysisId("9701");
        historical.setField("fieldValue");
        historical.setFieldLabel("Audit additional measurement");
        historical.setActorId("17");
        historical.setNewValue("Historical answer");
        entityManager.persist(historical);
        var snapshot = newEvent("AnalysisAdditionalFieldValue", "9703");
        snapshot.setModule("testadditionalfield");
        snapshot.setTestId("9201");
        snapshot.setAnalysisId("9799");
        snapshot.setField("fieldValue");
        snapshot.setActorId("18");
        entityManager.persist(snapshot);
        entityManager.flush();
        assertEquals(5, auditEventDAO.search(testFilter(null, "9201", 0, 30)).total());
        var filtered = auditEventDAO.search(new AuditEventFilter(null, "result", null, null, "17", "measurement",
                "U", Instant.parse("2026-01-01T11:00:00Z"), Instant.parse("2026-01-01T13:00:00Z"), "9201", 0, 30));
        assertEquals(1, filtered.total());
        assertEquals("Historical answer", filtered.items().get(0).newValue());
        assertEquals(0, auditEventDAO.search(new AuditEventFilter(null, "result", null, null, "17", null, null,
                Instant.parse("2026-01-02T00:00:00Z"), null, "9201", 0, 30)).total());
        assertEquals(null, historical.getTestId());
    }

    @Test
    public void capturesStableTestOwnershipForResultsAndAdditionalAnswers() {
        var request = new MockHttpServletRequest("POST", "/rest/results");
        var user = new UserSessionData();
        user.setSytemUserId(17);
        user.setLoginName("result-editor");
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            entityManager.find(org.openelisglobal.result.valueholder.Result.class, "9201").setValue("42.1");
            var answer = new org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue();
            answer.setAnalysisId(9201);
            answer.setFieldDefinitionId(9201);
            answer.setFieldValue("Detailed answer");
            answer.setSysUserId("17");
            entityManager.persist(answer);
            entityManager.flush();
            entityManager.unwrap(org.hibernate.engine.spi.SessionImplementor.class).getActionQueue()
                    .beforeTransactionCompletion();
            entityManager.flush();
            var captured = entityManager.createQuery(
                    "FROM AuditEvent e WHERE e.actorId = '17' AND e.source = 'POST /rest/results'", AuditEvent.class)
                    .getResultList();
            assertEquals(2, captured.size());
            assertTrue(captured.stream().allMatch(event -> "9201".equals(event.getTestId())));
            assertEquals(2, auditEventDAO.search(new AuditEventFilter(null, "result", null, null, "17", null, null,
                    null, null, "9201", 0, 30)).total());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    private AuditEventFilter testFilter(String accession, String test, int page, int size) {
        return new AuditEventFilter(accession, "result", null, null, null, null, null, null, null, test, page, size);
    }

    private AuditEvent event(String entity, String id) {
        var event = newEvent(entity, id);
        entityManager.persist(event);
        return event;
    }

    private AuditEvent newEvent(String entity, String id) {
        var event = new AuditEvent();
        event.setId(UUID.randomUUID().toString());
        event.setOperationId(UUID.randomUUID().toString());
        event.setModule(entity.toLowerCase(java.util.Locale.ROOT));
        event.setEntityName(entity);
        event.setEntityId(id);
        event.setAction("U");
        event.setField(switch (entity) {
        case "Sample" -> "priority";
        case "SampleItem" -> "quantity";
        case "Analysis" -> "completedDate";
        case "Result" -> "value";
        case "Patient" -> "gender";
        case "Person" -> "firstName";
        case "ResultFile" -> "fileName";
        default -> "displayName";
        });
        event.setActorSource("SYSTEM");
        event.setOccurredAt(Timestamp.from(Instant.parse("2026-01-01T12:00:00Z")));
        return event;
    }

    private AuditEventFilter filter(String accession) {
        return new AuditEventFilter(accession, null, null, null, null, null, null, null, null, 0, 30);
    }
}
