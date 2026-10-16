package org.openelisglobal.audittrail;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.Collections;
import java.util.List;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.Test;
import org.openelisglobal.audittrail.daoimpl.AuditEventDAOImpl;
import org.openelisglobal.audittrail.form.AuditEventFilter;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Compiles audit queries against the actual application metadata and HQL
 * parser.
 */
public class AuditApplicationMappingTest {
    @Test
    public void accessionQueriesMatchApplicationMappings() throws Exception {
        Configuration configuration = new Configuration().configure("hibernate/hibernate.cfg.xml");
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(Entity.class));
        for (var candidate : scanner.findCandidateComponents("org.openelisglobal")) {
            configuration
                    .addAnnotatedClass(Class.forName(candidate.getBeanClassName(), false, getClass().getClassLoader()));
        }
        configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        configuration.setProperty("hibernate.search.enabled", "false");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = configuration.buildSessionFactory()) {
            var actual = factory.unwrap(SessionFactoryImplementor.class);
            EntityManager manager = mock(EntityManager.class);
            when(manager.createQuery(anyString(), any(Class.class))).thenAnswer(invocation -> {
                String hql = invocation.getArgument(0);
                var plan = actual.getQueryPlanCache().getHQLQueryPlan(hql, false, Collections.emptyMap());
                if (hql.contains("selectedTestId"))
                    org.junit.Assert.assertTrue("Classic HQL must render valid SQL numeric-to-text conversions",
                            java.util.Arrays.toString(plan.getSqlStrings()).contains("concat("));
                TypedQuery<?> query = mock(org.hibernate.query.Query.class, RETURNS_SELF);
                Class<?> result = invocation.getArgument(1);
                assertEquals(hql, result, plan.getReturnMetadata().getReturnTypes()[0].getReturnedClass());
                if (result == Long.class)
                    doReturn(0L).when(query).getSingleResult();
                else if (result == String.class)
                    doReturn(List.of("1")).when(query).getResultList();
                else if (result == Integer.class)
                    doReturn(List.of(1)).when(query).getResultList();
                else
                    doReturn(Collections.emptyList()).when(query).getResultList();
                return query;
            });
            var dao = new AuditEventDAOImpl();
            ReflectionTestUtils.setField(dao, "entityManager", manager);
            dao.search(
                    new AuditEventFilter("LAB-1", "sampleitem", null, null, "7", "collector", "U", null, null, 0, 30));
            for (String module : List.of("provider", "patient"))
                dao.search(new AuditEventFilter("LAB-1", module, null, null, null, null, null, null, null, 0, 30));
            dao.search(new AuditEventFilter(null, "result", null, null, null, null, null, null, null, "9201", 0, 30));
        }
    }
}
