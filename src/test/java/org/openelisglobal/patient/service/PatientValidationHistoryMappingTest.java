package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.List;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.Test;
import org.openelisglobal.patient.daoimpl.PatientValidationHistoryDAOImpl;
import org.springframework.test.util.ReflectionTestUtils;

public class PatientValidationHistoryMappingTest {
    @Test
    public void patientAndApproverQueriesMatchActualApplicationMappings() throws Exception {
        Configuration configuration = new Configuration().configure("hibernate/hibernate.cfg.xml");
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
                var plan = actual.getQueryPlanCache().getHQLQueryPlan(hql, false, Collections.emptyMap());
                assertTrue(plan.getSqlStrings().length > 0);
                var query = mock(org.hibernate.query.Query.class, RETURNS_SELF);
                when(query.unwrap(org.hibernate.query.Query.class)).thenReturn(query);
                when(query.getResultList()).thenReturn(List.of());
                return query;
            });
            var dao = new PatientValidationHistoryDAOImpl();
            ReflectionTestUtils.setField(dao, "entityManager", manager);
            assertTrue(dao.findValidatedAnalyses("10", "6", null).isEmpty());
            assertTrue(dao.findValidatedAnalyses("10", "6", "78").isEmpty());
            assertTrue(dao.findActiveApprovers(List.of("78", "79")).isEmpty());
        }
    }
}
