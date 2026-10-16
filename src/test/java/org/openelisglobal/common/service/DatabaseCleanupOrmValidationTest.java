package org.openelisglobal.common.service;

import static org.junit.Assert.*;

import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.audittrail.valueholder.ArchivedAuditEvent;
import org.openelisglobal.sample.valueholder.SampleEnvironmental;

public class DatabaseCleanupOrmValidationTest {
    @Test
    public void cleanupMappingsBuildWithoutDatabase() {
        var configuration = new Configuration().addAnnotatedClass(ArchivedAuditEvent.class)
                .addAnnotatedClass(SampleEnvironmental.class);
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        configuration.setProperty("hibernate.search.enabled", "false");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = configuration.buildSessionFactory()) {
            assertEquals(2, factory.getMetamodel().getEntities().size());
            assertEquals(String.class,
                    factory.getMetamodel().entity(ArchivedAuditEvent.class).getIdType().getJavaType());
            assertEquals(Long.class,
                    factory.getMetamodel().entity(SampleEnvironmental.class).getIdType().getJavaType());
        }
    }
}
