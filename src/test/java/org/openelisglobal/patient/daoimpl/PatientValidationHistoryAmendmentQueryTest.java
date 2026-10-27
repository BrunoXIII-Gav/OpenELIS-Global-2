package org.openelisglobal.patient.daoimpl;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Runs the amendment lookup through the real Analysis mapping and JDBC binding.
 */
public class PatientValidationHistoryAmendmentQueryTest {
    @Test
    public void amendmentLookupBindsAnalysisIdsAsNumbers() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        try (SessionFactory factory = buildFactory();
                Session session = factory.withOptions().connection(connection).openSession()) {
            PatientValidationHistoryDAOImpl dao = new PatientValidationHistoryDAOImpl();
            ReflectionTestUtils.setField(dao, "entityManager", session);

            assertTrue(dao.findAmendments(List.of("78", "79")).isEmpty());
            assertTrue(dao.findAmendments(List.of()).isEmpty());

            verify(statement).setInt(anyInt(), eq(78));
            verify(statement).setInt(anyInt(), eq(79));
            verify(statement, never()).setString(anyInt(), anyString());
            verify(connection, times(1)).prepareStatement(contains("AMENDS_ANALYSIS_ID in"));
        }
    }

    private SessionFactory buildFactory() {
        Configuration config = new Configuration();
        config.addResource("hibernate/hbm/Analysis.hbm.xml");
        config.addResource("hibernate/hbm/SampleItem.hbm.xml");
        config.addResource("hibernate/hbm/TestSection.hbm.xml");
        for (String entity : new String[] { "org.openelisglobal.sample.valueholder.Sample",
                "org.openelisglobal.typeofsample.valueholder.TypeOfSample",
                "org.openelisglobal.unitofmeasure.valueholder.UnitOfMeasure",
                "org.openelisglobal.organization.valueholder.Organization",
                "org.openelisglobal.localization.valueholder.Localization",
                "org.openelisglobal.result.valueholder.Result", "org.openelisglobal.panel.valueholder.Panel",
                "org.openelisglobal.method.valueholder.Method", "org.openelisglobal.analysis.valueholder.ResultFile",
                "org.openelisglobal.test.valueholder.Test" }) {
            String association = entity.endsWith(".Test")
                    ? "<many-to-one name=\"testSection\" class=\"org.openelisglobal.test.valueholder.TestSection\" column=\"TEST_SECTION_ID\"/>"
                    : "";
            String xml = "<?xml version=\"1.0\"?><!DOCTYPE hibernate-mapping PUBLIC \"-//Hibernate/Hibernate Mapping DTD 3.0//EN\" \"http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd\">"
                    + "<hibernate-mapping><class name=\"" + entity
                    + "\"><id name=\"id\" type=\"org.openelisglobal.hibernate.resources.usertype.LIMSStringNumberUserType\"/>"
                    + association + "</class></hibernate-mapping>";
            config.addInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        }
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        config.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        return config.buildSessionFactory();
    }
}
