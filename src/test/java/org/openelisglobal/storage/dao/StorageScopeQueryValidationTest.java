package org.openelisglobal.storage.dao;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.Query;
import org.hibernate.type.Type;
import org.junit.Test;
import org.openelisglobal.storage.valueholder.*;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Compiles the actual DAO HQL against the legacy specimen mapping without a
 * database.
 */
public class StorageScopeQueryValidationTest {
    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void scopeAndEveryHierarchyQueryCompileWithoutAConnection() {
        try (SessionFactory factory = buildFactory()) {
            EntityManager manager = mock(EntityManager.class);
            when(manager.createQuery(anyString(), any(Class.class))).thenAnswer(invocation -> {
                String hql = invocation.getArgument(0);
                ((SessionFactoryImplementor) factory).getQueryPlanCache().getHQLQueryPlan(hql, false, Map.of());
                Query query = mock(Query.class);
                when(query.unwrap(Query.class)).thenReturn(query);
                when(query.setParameter(anyString(), any())).thenReturn(query);
                when(query.setParameterList(anyString(), anyCollection(), any(Type.class))).thenReturn(query);
                when(query.getResultList()).thenReturn(List.of());
                return query;
            });
            StorageSampleScopeDAOImpl dao = new StorageSampleScopeDAOImpl();
            ReflectionTestUtils.setField(dao, "entityManager", manager);
            assertTrue(dao.findSampleItemIdsByLabUnits(Set.of("203")).isEmpty());
            assertTrue(dao.findUnanalysedSampleItemParents().isEmpty());
            for (String type : List.of("room", "device", "shelf", "rack", "box")) {
                assertTrue(dao.findAssignedSampleItemIdsInHierarchy(type, 1).isEmpty());
                assertTrue(dao.findAssignmentsInHierarchy(type, 1).isEmpty());
            }
            verify(manager, times(12)).createQuery(anyString(), any(Class.class));
            assertThrows(IllegalArgumentException.class, () -> dao.findAssignedSampleItemIdsInHierarchy("invalid", 1));
        }
    }

    @Test
    public void laboratoryUnitIdentifiersAreBoundAsNumbersRatherThanVarchar() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet results = mock(ResultSet.class);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(results);
        try (SessionFactory factory = buildFactory();
                Session session = factory.withOptions().connection(connection).openSession()) {
            StorageSampleScopeDAOImpl dao = new StorageSampleScopeDAOImpl();
            ReflectionTestUtils.setField(dao, "entityManager", session);

            assertTrue(dao.findSampleItemIdsByLabUnits(Set.of("203")).isEmpty());
            assertTrue(dao.findSampleItemIdsByLabUnits(Set.of("203", "56")).isEmpty());

            // PostgreSQL numeric identifiers reject VARCHAR comparisons. Execute
            // the real Hibernate query through JDBC to verify its parameter binding.
            verify(statement, atLeastOnce()).setInt(anyInt(), eq(203));
            verify(statement, atLeastOnce()).setInt(anyInt(), eq(56));
            verify(statement, never()).setString(anyInt(), anyString());
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
        for (Class<?> entity : new Class<?>[] { StorageRoom.class, StorageDevice.class, StorageShelf.class,
                StorageRack.class, StorageBox.class, SampleStorageAssignment.class }) {
            config.addAnnotatedClass(entity);
        }
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        config.setProperty("hibernate.query.factory_class",
                "org.hibernate.hql.internal.classic.ClassicQueryTranslatorFactory");
        config.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        return config.buildSessionFactory();
    }
}
