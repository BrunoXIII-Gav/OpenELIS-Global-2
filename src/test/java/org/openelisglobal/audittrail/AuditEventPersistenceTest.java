package org.openelisglobal.audittrail;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.StaleStateException;
import org.hibernate.cfg.Configuration;
import org.junit.*;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.reportdefinition.valueholder.ReportDefinition;
import org.openelisglobal.resultvalidation.valueholder.AnalysisValidationApproval;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Real PostgreSQL, isolated database only; enable with -Daudit.test.url=... */
public class AuditEventPersistenceTest {
    private static SessionFactory factory;
    private static Integer firstField;
    private static Integer secondField;

    @BeforeClass
    public static void setUpDatabase() throws Exception {
        String url = System.getProperty("audit.test.url");
        Assume.assumeTrue("Provide an isolated audit test database", url != null);
        if (!url.matches("jdbc:postgresql://[^/]+/oe_audit_[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Only a dedicated oe_audit_ database is allowed");
        }
        Properties environment = new Properties();
        if (Files.exists(Path.of(".env"))) {
            try (var reader = Files.newBufferedReader(Path.of(".env"))) {
                environment.load(reader);
            }
        }
        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(AuditEvent.class);
        configuration.addResource("hibernate/hbm/SystemUser.hbm.xml");
        configuration.addResource("hibernate/hbm/Note.hbm.xml");
        configuration.addResource("hibernate/hbm/ReferenceTables.hbm.xml");
        configuration.addAnnotatedClass(
                org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition.class);
        configuration.addAnnotatedClass(
                org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldFile.class);
        configuration.addAnnotatedClass(TestAdditionalFieldDefinition.class);
        configuration
                .addAnnotatedClass(org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldOption.class);
        configuration.addAnnotatedClass(AnalysisAdditionalFieldValue.class);
        configuration.addAnnotatedClass(ReportDefinition.class);
        configuration.addAnnotatedClass(AnalysisValidationApproval.class);
        configuration.addAnnotatedClass(org.openelisglobal.userrole.valueholder.UserLabUnitRoles.class);
        configuration.addAnnotatedClass(org.openelisglobal.userrole.valueholder.LabUnitRoleMap.class);
        configuration.setProperty("hibernate.connection.url", url);
        configuration.setProperty("hibernate.connection.username", System.getProperty("audit.test.user", "clinlims"));
        configuration.setProperty("hibernate.connection.password",
                environment.getProperty("OE_DB_PASSWORD", "clinlims"));
        configuration.setProperty("hibernate.connection.driver_class", "org.postgresql.Driver");
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "create-drop");
        configuration.setProperty("hibernate.query.factory_class",
                "org.hibernate.hql.internal.classic.ClassicQueryTranslatorFactory");
        factory = configuration.buildSessionFactory();
        assertTrue("Audit integrator must be installed", java.util.stream.StreamSupport.stream(
                factory.unwrap(org.hibernate.engine.spi.SessionFactoryImplementor.class).getServiceRegistry()
                        .getService(org.hibernate.event.service.spi.EventListenerRegistry.class)
                        .getEventListenerGroup(org.hibernate.event.spi.EventType.POST_INSERT).listeners().spliterator(),
                false).anyMatch(
                        listener -> listener instanceof org.openelisglobal.audittrail.persistence.AuditEventListener));
        actor(1);
        transaction(session -> {
            firstField = (Integer) session.save(definition("a", "First measurement"));
            secondField = (Integer) session.save(definition("b", "Second measurement"));
        });
        RequestContextHolder.resetRequestAttributes();
    }

    @AfterClass
    public static void closeFactory() {
        RequestContextHolder.resetRequestAttributes();
        if (factory != null)
            factory.close();
    }

    @After
    public void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static TestAdditionalFieldDefinition definition(String key, String label) {
        TestAdditionalFieldDefinition field = new TestAdditionalFieldDefinition();
        field.setTestId(1);
        field.setFieldKey(key);
        field.setDisplayName(label);
        field.setFieldType("TEXT");
        field.setActive(true);
        field.setRequired(false);
        field.setSortOrder(1);
        field.setSysUserId("1");
        return field;
    }

    private static void actor(int id) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rest/results");
        UserSessionData user = new UserSessionData();
        user.setSytemUserId(id);
        user.setLoginName("user-" + id);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static void transaction(Consumer<Session> work) {
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                work.accept(session);
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive())
                    tx.rollback();
                throw e;
            }
        }
    }

    private Integer insertValue(int analysis, int field, String value, int user) {
        actor(user);
        Integer[] id = new Integer[1];
        transaction(session -> {
            AnalysisAdditionalFieldValue entity = new AnalysisAdditionalFieldValue();
            entity.setAnalysisId(analysis);
            entity.setFieldDefinitionId(field);
            entity.setFieldValue(value);
            entity.setSysUserId("999"); // Caller-supplied attribution must not override authenticated actor.
            id[0] = (Integer) session.save(entity);
        });
        return id[0];
    }

    private List<AuditEvent> history(Integer id) {
        try (Session session = factory.openSession()) {
            return session.createQuery(
                    "FROM AuditEvent e WHERE e.entityName = 'AnalysisAdditionalFieldValue'"
                            + " AND e.entityId = :id AND e.field = 'fieldValue' ORDER BY e.occurredAt, e.id",
                    AuditEvent.class).setParameter("id", id.toString()).list();
        }
    }

    @Test
    public void preservesBothActorsAndActualPreviousManagedStateAfterRenamingField() {
        Integer a = insertValue(101, firstField, "10", 11);
        Integer b = insertValue(101, secondField, "positive", 12);
        actor(13);
        transaction(session -> {
            AnalysisAdditionalFieldValue value = session.get(AnalysisAdditionalFieldValue.class, a);
            value.setFieldValue("12");
            session.get(TestAdditionalFieldDefinition.class, firstField).setDisplayName("Renamed measurement");
        });
        List<AuditEvent> events = history(a);
        assertEquals(2, events.size());
        assertEquals("11", events.get(0).getActorId());
        assertEquals("First measurement", events.get(0).getFieldLabel());
        assertEquals("13", events.get(1).getActorId());
        assertEquals("10", events.get(1).getOldValue());
        assertEquals("12", events.get(1).getNewValue());
        assertEquals("12", history(b).get(0).getActorId());
        assertEquals("user-11", events.get(0).getActorName());
    }

    @Test
    public void doesNotRecordUnchangedFieldAndPreservesDeletionSnapshot() {
        Integer id = insertValue(102, secondField, "old", 21);
        actor(22);
        transaction(session -> {
            AnalysisAdditionalFieldValue value = session.get(AnalysisAdditionalFieldValue.class, id);
            value.setFieldValue("old");
            value.setSysUserId("22");
        });
        assertEquals(1, history(id).size());
        actor(23);
        transaction(session -> session.delete(session.get(AnalysisAdditionalFieldValue.class, id)));
        List<AuditEvent> events = history(id);
        assertEquals(2, events.size());
        assertEquals("D", events.get(1).getAction());
        assertEquals("old", events.get(1).getOldValue());
        assertNull(events.get(1).getNewValue());
    }

    @Test
    public void rollsBackBusinessChangeAndAuditTogetherAfterFlush() {
        Integer id = insertValue(103, secondField, "before", 31);
        actor(32);
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            session.get(AnalysisAdditionalFieldValue.class, id).setFieldValue("rolled back");
            session.flush();
            tx.rollback();
        }
        assertEquals(1, history(id).size());
        try (Session session = factory.openSession()) {
            assertEquals("before", session.get(AnalysisAdditionalFieldValue.class, id).getFieldValue());
        }
    }

    @Test
    public void rejectsStaleConcurrentWriterWithoutFalseAudit() {
        Integer id = insertValue(104, secondField, "before", 41);
        try (Session first = factory.openSession(); Session second = factory.openSession()) {
            var tx1 = first.beginTransaction();
            var tx2 = second.beginTransaction();
            var a = first.get(AnalysisAdditionalFieldValue.class, id);
            var b = second.get(AnalysisAdditionalFieldValue.class, id);
            actor(42);
            a.setFieldValue("winner");
            tx1.commit();
            actor(43);
            b.setFieldValue("stale");
            try {
                tx2.commit();
                fail("Expected optimistic lock failure");
            } catch (jakarta.persistence.OptimisticLockException | StaleStateException expected) {
                if (tx2.isActive())
                    tx2.rollback();
            }
        }
        assertEquals(2, history(id).size());
        assertEquals("42", history(id).get(1).getActorId());
    }

    @Test
    public void protectsLedgerAgainstOrmDeletion() {
        Integer id = insertValue(105, secondField, "kept", 51);
        AuditEvent event = history(id).get(0);
        try {
            transaction(session -> session.delete(session.get(AuditEvent.class, event.getId())));
            fail("Audit deletion must fail");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("cannot be changed or deleted"));
        }
        assertEquals(1, history(id).size());
    }

    @Test
    public void capturesRoleCollectionsIncludingRemovedPermissions() {
        actor(61);
        Integer[] id = new Integer[1];
        transaction(session -> {
            var roles = new org.openelisglobal.userrole.valueholder.LabUnitRoleMap();
            roles.setLabUnit("7");
            roles.setRoles(new java.util.HashSet<>(java.util.Set.of("read", "write")));
            id[0] = (Integer) session.save(roles);
        });
        actor(62);
        transaction(session -> session.get(org.openelisglobal.userrole.valueholder.LabUnitRoleMap.class, id[0])
                .getRoles().remove("write"));
        try (Session session = factory.openSession()) {
            List<AuditEvent> events = session
                    .createQuery(
                            "FROM AuditEvent e WHERE e.entityName = 'LabUnitRoleMap'"
                                    + " AND e.entityId = :id AND e.field = 'roles' ORDER BY e.occurredAt",
                            AuditEvent.class)
                    .setParameter("id", id[0].toString()).list();
            assertEquals(2, events.size());
            assertTrue(events.get(1).getOldValue().contains("write"));
            assertFalse(events.get(1).getNewValue().contains("write"));
            assertEquals("62", events.get(1).getActorId());
        }
    }

    @Test
    public void preservesApprovalAndInvalidationActorInTheSameHistory() {
        actor(71);
        String[] id = new String[1];
        transaction(session -> {
            var approvals = approvals(session);
            assertEquals(1, approvals.registerApprovalAndGetCount("701", "71"));
            id[0] = session.createQuery("SELECT a.id FROM AnalysisValidationApproval a WHERE a.analysisId = '701'",
                    String.class).uniqueResult();
        });
        actor(72);
        transaction(session -> {
            var approvals = approvals(session);
            approvals.clearApprovals("701", "72", "REJECTION");
            assertEquals(1, approvals.registerApprovalAndGetCount("701", "71"));
        });
        try (Session session = factory.openSession()) {
            assertEquals("71", session.get(AnalysisValidationApproval.class, id[0]).getApproverUserId());
            assertEquals(Long.valueOf(2),
                    session.createQuery("SELECT COUNT(a) FROM AnalysisValidationApproval a WHERE a.analysisId = '701'",
                            Long.class).uniqueResult());
            List<AuditEvent> events = session
                    .createQuery("FROM AuditEvent e WHERE e.entityName = 'AnalysisValidationApproval'"
                            + " AND e.entityId = :id AND e.field = 'revocationReason'", AuditEvent.class)
                    .setParameter("id", id[0]).list();
            assertEquals(1, events.size());
            assertEquals("72", events.get(0).getActorId());
            assertEquals("REJECTION", events.get(0).getNewValue());
        }
    }

    private org.openelisglobal.resultvalidation.service.AnalysisValidationApprovalServiceImpl approvals(
            Session session) {
        var dao = new org.openelisglobal.resultvalidation.dao.AnalysisValidationApprovalDAOImpl();
        org.springframework.test.util.ReflectionTestUtils.setField(dao, "entityManager", session);
        return new org.openelisglobal.resultvalidation.service.AnalysisValidationApprovalServiceImpl(dao);
    }

    @Test
    public void auditWriteFailureAbortsTheBusinessChange() {
        Integer id = insertValue(106, secondField, "before", 81);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/rest/results");
        UserSessionData user = new UserSessionData();
        user.setSytemUserId(82);
        user.setLoginName("x".repeat(300));
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            transaction(session -> session.get(AnalysisAdditionalFieldValue.class, id).setFieldValue("must roll back"));
            fail("Audit insert must fail for an oversized actor name");
        } catch (RuntimeException expected) {
            try (Session session = factory.openSession()) {
                assertEquals("before", session.get(AnalysisAdditionalFieldValue.class, id).getFieldValue());
            }
            assertEquals(1, history(id).size());
        }
    }

    @Test
    public void selectedProfessionalSnapshotIsSeparateFromTheActingUser() {
        actor(91);
        Integer[] fieldId = new Integer[1];
        String[] userId = new String[1];
        transaction(session -> {
            var selected = new org.openelisglobal.systemuser.valueholder.SystemUser();
            selected.setFirstName("Ana");
            selected.setLastName("Original");
            selected.setLoginName("ana");
            selected.setIsActive("Y");
            selected.setIsEmployee("Y");
            userId[0] = (String) session.save(selected);
            var field = definition("responsible", "Responsible professional");
            field.setFieldType("USER");
            fieldId[0] = (Integer) session.save(field);
        });
        Integer id = insertValue(107, fieldId[0], userId[0], 92);
        transaction(session -> session.get(org.openelisglobal.systemuser.valueholder.SystemUser.class, userId[0])
                .setLastName("Renamed"));
        AuditEvent event = history(id).get(0);
        assertEquals("92", event.getActorId());
        assertEquals(userId[0], event.getNewValue());
        assertEquals("Ana Original", event.getNewDisplayValue());
    }

    @Test
    public void queriesRealSnapshotsWithBoundFiltersAndPagination() {
        Integer id = insertValue(108, secondField, "first", 101);
        actor(102);
        transaction(session -> session.get(AnalysisAdditionalFieldValue.class, id).setFieldValue("second"));
        try (Session session = factory.openSession()) {
            var dao = new org.openelisglobal.audittrail.daoimpl.AuditEventDAOImpl();
            org.springframework.test.util.ReflectionTestUtils.setField(dao, "entityManager", session);
            var page = dao.search(new org.openelisglobal.audittrail.form.AuditEventFilter(null, "testadditionalfield",
                    "AnalysisAdditionalFieldValue", id.toString(), null, "fieldValue", null, null, null, 0, 1));
            assertEquals(2, page.total());
            assertEquals(1, page.items().size());
            assertEquals("102", page.items().get(0).actorId());
            assertEquals("first", page.items().get(0).oldValue());
            var none = dao.search(new org.openelisglobal.audittrail.form.AuditEventFilter(null, null, null,
                    "' OR 1=1 --", null, null, null, null, null, 0, 30));
            assertEquals(0, none.total());
        }
    }

    @Test
    public void discardedTransactionDoesNotLeakEventsIntoReusedSession() {
        Integer id = insertValue(109, secondField, "first", 111);
        try (Session session = factory.openSession()) {
            actor(112);
            var discarded = session.beginTransaction();
            session.get(AnalysisAdditionalFieldValue.class, id).setFieldValue("discarded");
            session.flush();
            discarded.rollback();
            session.clear();
            actor(113);
            var committed = session.beginTransaction();
            session.get(AnalysisAdditionalFieldValue.class, id).setFieldValue("committed");
            committed.commit();
        }
        List<AuditEvent> events = history(id);
        assertEquals(2, events.size());
        assertEquals("113", events.get(1).getActorId());
        assertEquals("first", events.get(1).getOldValue());
        assertEquals("committed", events.get(1).getNewValue());
    }

    @Test
    public void capturesLegacyAssociationsAndPolymorphicSampleLinks() {
        actor(121);
        String[] noteId = new String[1];
        String[] userId = new String[1];
        transaction(session -> {
            var selected = new org.openelisglobal.systemuser.valueholder.SystemUser();
            selected.setFirstName("Test");
            selected.setLastName("Author");
            selected.setLoginName("note-author");
            selected.setIsActive("Y");
            selected.setIsEmployee("Y");
            userId[0] = (String) session.save(selected);
            var table = new org.openelisglobal.referencetables.valueholder.ReferenceTables();
            table.setTableName("SAMPLE");
            table.setKeepHistory("Y");
            String tableId = (String) session.save(table);
            var note = new org.openelisglobal.note.valueholder.Note();
            note.setSystemUser(selected);
            note.setReferenceTableId(tableId);
            note.setReferenceId("1234");
            note.setText("before");
            noteId[0] = (String) session.save(note);
        });
        actor(122);
        transaction(session -> session.get(org.openelisglobal.note.valueholder.Note.class, noteId[0]).setText("after"));
        try (Session session = factory.openSession()) {
            var events = session.createQuery(
                    "FROM AuditEvent e WHERE e.entityName = 'Note' AND e.entityId = :id AND e.field = 'text' ORDER BY e.occurredAt",
                    AuditEvent.class).setParameter("id", noteId[0]).list();
            assertEquals(2, events.size());
            assertEquals("before", events.get(1).getOldValue());
            assertEquals("1234", events.get(1).getSampleId());
            assertEquals("122", events.get(1).getActorId());
            var association = session.createQuery(
                    "FROM AuditEvent e WHERE e.entityName = 'Note' AND e.entityId = :id AND e.field = 'systemUser'",
                    AuditEvent.class).setParameter("id", noteId[0]).uniqueResult();
            assertNull("Implicit note ownership must not create a separate functional event", association);
        }
    }

    @Test
    public void fingerprintsDocumentsInsteadOfCopyingTheirContents() {
        actor(131);
        Integer[] id = new Integer[1];
        transaction(session -> {
            var file = new org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldFile();
            file.setSampleId(1234);
            file.setFieldDefinitionId(999);
            file.setFileName("test.pdf");
            file.setFileType("application/pdf");
            file.setFileSize(6L);
            file.setUploadedAt(new java.sql.Timestamp(System.currentTimeMillis()));
            file.setSysUserId("131");
            file.setFileContent("secret".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            id[0] = (Integer) session.save(file);
        });
        try (Session session = factory.openSession()) {
            var event = session.createQuery(
                    "FROM AuditEvent e WHERE e.entityName = 'SampleOrderAdditionalFieldFile' AND e.entityId = :id AND e.field = 'fileContent'",
                    AuditEvent.class).setParameter("id", id[0].toString()).uniqueResult();
            assertTrue(event.getNewValue().startsWith("sha256:"));
            assertFalse(event.getNewValue().contains("secret"));
            assertEquals("1234", event.getSampleId());
        }
    }

    @Test
    public void redactsSensitiveConfigurationValues() {
        actor(141);
        transaction(session -> {
            var definition = new ReportDefinition();
            definition.setId("sensitive-config");
            definition.setName("smtp.password");
            definition.setDefinitionJson("secret-password");
            definition.setIsActive(true);
            session.persist(definition);
        });
        try (Session session = factory.openSession()) {
            var event = session.createQuery(
                    "FROM AuditEvent e WHERE e.entityName = 'ReportDefinition' AND e.entityId = 'sensitive-config' AND e.field = 'definitionJson'",
                    AuditEvent.class).uniqueResult();
            assertEquals("[REDACTED]", event.getNewValue());
        }
    }

    @Test
    public void capturesOnlyTheEnteredFieldAndRetainsPrivateClinicalLinks() {
        Integer id = insertValue(501, secondField, "entered", 151);
        try (Session session = factory.openSession()) {
            var events = session.createQuery(
                    "FROM AuditEvent e WHERE e.entityName = 'AnalysisAdditionalFieldValue'" + " AND e.entityId = :id",
                    AuditEvent.class).setParameter("id", id.toString()).list();
            assertEquals(1, events.size());
            assertEquals("fieldValue", events.get(0).getField());
            assertEquals("501", events.get(0).getAnalysisId());
            assertEquals(secondField.toString(), events.get(0).getFieldDefinitionId());
            assertEquals("result", events.get(0).getModule());
        }
    }

    @Test
    public void snapshotsConfiguredOptionLabelsInsteadOfShowingOptionKeys() {
        actor(152);
        Integer[] fieldId = new Integer[1];
        Integer[] optionId = new Integer[1];
        transaction(session -> {
            var field = definition("dynamic-option", "Selected procedure");
            field.setFieldType("SELECT");
            fieldId[0] = (Integer) session.save(field);
            var option = new org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldOption();
            option.setFieldDefinitionId(fieldId[0]);
            option.setOptionKey("option-code");
            option.setOptionLabel("Original procedure name");
            option.setActive(true);
            option.setSortOrder(1);
            option.setSysUserId("152");
            optionId[0] = (Integer) session.save(option);
        });
        Integer id = insertValue(502, fieldId[0], "option-code", 153);
        transaction(session -> session
                .get(org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldOption.class, optionId[0])
                .setOptionLabel("Renamed procedure"));
        var event = history(id).get(0);
        assertEquals("option-code", event.getNewValue());
        assertEquals("Original procedure name", event.getNewDisplayValue());
    }

    @Test
    public void auditsUploadedDocumentNamesWithoutDuplicatingTheirBase64Content() {
        actor(154);
        Integer[] fieldId = new Integer[1];
        transaction(session -> {
            var field = definition("dynamic-document", "Attached document");
            field.setFieldType("DOCUMENT");
            fieldId[0] = (Integer) session.save(field);
        });
        String document = "{\"fileName\":\"attachment.pdf\",\"base64Content\":\"c2VjcmV0\"}";
        Integer id = insertValue(503, fieldId[0], document, 154);
        var event = history(id).get(0);
        assertEquals("attachment.pdf", event.getNewDisplayValue());
        assertFalse(event.getNewValue().contains("base64Content"));
        assertFalse(event.getNewValue().contains("c2VjcmV0"));
        assertTrue(event.getNewValue().contains("sha256:"));
    }

}
