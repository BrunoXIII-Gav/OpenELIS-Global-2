package org.openelisglobal.test.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.Test;
import org.openelisglobal.test.daoimpl.TestSectionDAOImpl;
import org.springframework.test.util.ReflectionTestUtils;

public class TestSectionOriginMappingTest {
    @Test
    public void originPropertiesAndQueriesLoadWithActualApplicationMappings() throws Exception {
        var configuration = new Configuration().configure("hibernate/hibernate.cfg.xml");
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("org.openelisglobal"))
            configuration
                    .addAnnotatedClass(Class.forName(candidate.getBeanClassName(), false, getClass().getClassLoader()));
        configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        configuration.setProperty("hibernate.search.enabled", "false");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = configuration.buildSessionFactory()) {
            var actual = factory.unwrap(SessionFactoryImplementor.class);
            var manager = mock(EntityManager.class);
            when(manager.createQuery(anyString(), any(Class.class))).thenAnswer(invocation -> {
                String hql = invocation.getArgument(0);
                assertTrue(actual.getQueryPlanCache().getHQLQueryPlan(hql, false, Collections.emptyMap())
                        .getSqlStrings().length > 0);
                var query = mock(org.hibernate.query.Query.class, RETURNS_SELF);
                when(query.getResultList()).thenReturn(List.of());
                return query;
            });
            var dao = new TestSectionDAOImpl();
            ReflectionTestUtils.setField(dao, "entityManager", manager);
            assertTrue(dao.getUserCreatedTestSectionIds().isEmpty());
            assertTrue(dao.getUnclassifiedTestSections().isEmpty());
        }
    }
}
