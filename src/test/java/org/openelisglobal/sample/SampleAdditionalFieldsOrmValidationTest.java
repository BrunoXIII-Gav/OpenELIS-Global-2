package org.openelisglobal.sample;

import static org.junit.Assert.assertNotNull;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.sample.valueholder.SampleItemAdditionalFieldValue;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldDefinition;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldOption;

/**
 * ORM mapping validation for sample additional fields entities.
 *
 * Executes without database access and fails fast if entity
 * annotations/mappings are inconsistent.
 */
public class SampleAdditionalFieldsOrmValidationTest {

    @Test
    public void testSampleAdditionalFieldsMappingsLoadSuccessfully() {
        Configuration configuration = new Configuration();

        configuration.addAnnotatedClass(SampleTypeAdditionalFieldDefinition.class);
        configuration.addAnnotatedClass(SampleTypeAdditionalFieldOption.class);
        configuration.addAnnotatedClass(SampleItemAdditionalFieldValue.class);

        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");

        SessionFactory sessionFactory = configuration.buildSessionFactory(
                new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build());

        assertNotNull(sessionFactory);
        assertNotNull(sessionFactory.getMetamodel().entity(SampleTypeAdditionalFieldDefinition.class));
        assertNotNull(sessionFactory.getMetamodel().entity(SampleTypeAdditionalFieldOption.class));
        assertNotNull(sessionFactory.getMetamodel().entity(SampleItemAdditionalFieldValue.class));

        sessionFactory.close();
    }
}
