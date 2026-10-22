package org.openelisglobal.sample.service;

import static org.junit.Assert.*;

import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.common.valueholder.AccessionNumberInfo;

public class OrderAccessionOrmValidationTest {
    @Test
    public void existingAccessionCounterMappingSupportsTheCompositeKeyWithoutDatabase() {
        var config = new Configuration().addAnnotatedClass(AccessionNumberInfo.class);
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        config.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        config.setProperty("hibernate.search.enabled", "false");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        try (var factory = config.buildSessionFactory()) {
            assertEquals(AccessionNumberInfo.AccessionIdentity.class,
                    factory.getMetamodel().entity(AccessionNumberInfo.class).getIdType().getJavaType());
        }
    }
}
