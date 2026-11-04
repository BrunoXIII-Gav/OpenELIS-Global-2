package org.openelisglobal.testadditionalfield.service;

import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Base64;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.openelisglobal.testadditionalfield.controller.rest.TestAdditionalFieldRestController;
import org.openelisglobal.testadditionalfield.daoimpl.AnalysisAdditionalFieldValueDAOImpl;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.PostgreSQLContainer;

public class AnalysisDocumentReadbackTest {
    @ClassRule
    public static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:14.4");
    private static SessionFactory factory;
    private static byte[] savedPdf;

    @BeforeClass
    public static void loadPersistedDocuments() throws Exception {
        try (Connection connection = DriverManager.getConnection(DATABASE.getJdbcUrl(), DATABASE.getUsername(),
                DATABASE.getPassword())) {
            new Liquibase("liquibase/analysis-document-baseline.xml", new ClassLoaderResourceAccessor(),
                    new JdbcConnection(connection)).update("");
            var fixtures = new FlatXmlDataSetBuilder().setColumnSensing(true).build(AnalysisDocumentReadbackTest.class
                    .getClassLoader().getResourceAsStream("testdata/analysis-documents.xml"));
            DatabaseOperation.INSERT.execute(new DatabaseConnection(connection, "clinlims"), fixtures);
            connection.commit();
            String json = (String) fixtures.getTable("analysis_additional_field_value").getValue(0, "field_value");
            savedPdf = Base64.getDecoder().decode(new ObjectMapper().readTree(json).get("base64Content").asText());
        }
        var configuration = new Configuration().addAnnotatedClass(AnalysisAdditionalFieldValue.class)
                .addAnnotatedClass(TestAdditionalFieldDefinition.class);
        configuration.setProperty("hibernate.connection.url", DATABASE.getJdbcUrl());
        configuration.setProperty("hibernate.connection.username", DATABASE.getUsername());
        configuration.setProperty("hibernate.connection.password", DATABASE.getPassword());
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.default_schema", "clinlims");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        factory = configuration.buildSessionFactory();
    }

    @AfterClass
    public static void closeFactory() {
        if (factory != null)
            factory.close();
    }

    @Test
    public void opensPersistedPdfInFreshSessionsEvenWithInactiveDefinition() throws Exception {
        for (int visit = 0; visit < 2; visit++) {
            var response = open("78", "doccc1", false).getResponse();
            assertEquals(200, response.getStatus());
            assertEquals("application/pdf", response.getContentType());
            assertEquals("inline; filename=\"saved-document.pdf\"", response.getHeader("Content-Disposition"));
            assertArrayEquals(savedPdf, response.getContentAsByteArray());
        }
    }

    @Test
    public void sameKeyInAnotherTestReturnsItsOwnDocument() throws Exception {
        var response = open("79", "doccc1", false).getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("application/octet-stream", response.getContentType());
        assertEquals("Other test document", response.getContentAsString(StandardCharsets.UTF_8));
        assertFalse(java.util.Arrays.equals(savedPdf, response.getContentAsByteArray()));
    }

    @Test
    public void missingClearedAndNonDocumentValuesReturnNotFound() throws Exception {
        assertEquals(404, open("999", "doccc1", false).getResponse().getStatus());
        assertEquals(404, open("78", "missing", false).getResponse().getStatus());
        assertEquals(404, open("78", "plain_text", false).getResponse().getStatus());
        assertEquals(404, open("78", "cleared_document", false).getResponse().getStatus());
    }

    @Test
    public void downloadKeepsStoredBytesAndFileName() throws Exception {
        var response = open("78", "doccc1", true).getResponse();
        assertEquals(200, response.getStatus());
        assertEquals("attachment; filename=\"saved-document.pdf\"", response.getHeader("Content-Disposition"));
        assertArrayEquals(savedPdf, response.getContentAsByteArray());
    }

    private MvcResult open(String analysisId, String fieldKey, boolean download) throws Exception {
        // A fresh persistence context and HTTP session emulate returning later
        // or opening the same saved attachment as a different signed-in user.
        try (var session = factory.openSession()) {
            var dao = new AnalysisAdditionalFieldValueDAOImpl();
            ReflectionTestUtils.setField(dao, "entityManager", session);
            var service = new TestAdditionalFieldServiceImpl();
            ReflectionTestUtils.setField(service, "valueDAO", dao);
            var controller = new TestAdditionalFieldRestController();
            ReflectionTestUtils.setField(controller, "testAdditionalFieldService", service);
            return MockMvcBuilders.standaloneSetup(controller).build()
                    .perform(get("/rest/test-additional-fields/files/{analysisId}/{fieldKey}", analysisId, fieldKey)
                            .param("download", String.valueOf(download)).session(new MockHttpSession()))
                    .andReturn();
        }
    }
}
