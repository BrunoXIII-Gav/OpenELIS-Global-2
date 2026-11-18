package org.openelisglobal.orderadditionalfield;

import static org.junit.Assert.assertNotNull;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.orderadditionalfield.valueholder.OrderAdditionalFieldDefinition;
import org.openelisglobal.orderadditionalfield.valueholder.OrderFixedFieldConfig;

public class OrderAdditionalFieldOrmValidationTest {
    @Test
    public void orderFieldMappingsBuildWithoutADatabase() {
        Configuration configuration = new Configuration();
        configuration.addAnnotatedClass(OrderAdditionalFieldDefinition.class);
        configuration.addAnnotatedClass(OrderFixedFieldConfig.class);
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        configuration.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        try (SessionFactory factory = configuration.buildSessionFactory(
                new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build())) {
            assertNotNull(factory.getMetamodel().entity(OrderAdditionalFieldDefinition.class));
            assertNotNull(factory.getMetamodel().entity(OrderFixedFieldConfig.class));
        }
    }
}
