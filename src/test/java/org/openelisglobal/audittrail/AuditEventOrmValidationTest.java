package org.openelisglobal.audittrail;

import static org.junit.Assert.assertNotNull;

import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.openelisglobal.resultvalidation.valueholder.AnalysisValidationApproval;

public class AuditEventOrmValidationTest {
    @Test
    public void validatesMappingsWithoutDatabase() {
        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(AuditEvent.class);
        configuration.addAnnotatedClass(AnalysisValidationApproval.class);
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = configuration.buildSessionFactory()) {
            assertNotNull(factory.getMetamodel().entity(AuditEvent.class));
            assertNotNull(factory.getMetamodel().entity(AnalysisValidationApproval.class));
        }
    }
}
