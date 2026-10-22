package org.openelisglobal.sample.service;

import static org.junit.Assert.*;

import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.sample.valueholder.*;

public class CugPrefixOrmValidationTest {
    @Test
    public void mappingsBuildWithoutDatabase() {
        Configuration config = new Configuration().addAnnotatedClass(SampleCugCounter.class)
                .addAnnotatedClass(PatientCugPrefix.class).addAnnotatedClass(SampleCugPrefix.class)
                .addAnnotatedClass(SampleCugPrefixRange.class);
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        config.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        config.setProperty("hibernate.search.enabled", "false");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = config.buildSessionFactory()) {
            assertEquals(4, factory.getMetamodel().getEntities().size());
            assertEquals(String.class, factory.getMetamodel().entity(SampleCugCounter.class).getIdType().getJavaType());
        }
    }
}
