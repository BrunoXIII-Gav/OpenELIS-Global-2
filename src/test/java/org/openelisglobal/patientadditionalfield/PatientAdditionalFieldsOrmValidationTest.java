package org.openelisglobal.patientadditionalfield;

import static org.junit.Assert.assertNotNull;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldDefinition;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldOption;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldValue;

public class PatientAdditionalFieldsOrmValidationTest {

    @Test
    public void testPatientAdditionalFieldsMappingsLoadSuccessfully() {
        Configuration configuration = new Configuration();

        configuration.addAnnotatedClass(PatientAdditionalFieldDefinition.class);
        configuration.addAnnotatedClass(PatientAdditionalFieldOption.class);
        configuration.addAnnotatedClass(PatientAdditionalFieldValue.class);

        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");

        SessionFactory sessionFactory = configuration.buildSessionFactory(
                new StandardServiceRegistryBuilder().applySettings(configuration.getProperties()).build());

        assertNotNull(sessionFactory);
        assertNotNull(sessionFactory.getMetamodel().entity(PatientAdditionalFieldDefinition.class));
        assertNotNull(sessionFactory.getMetamodel().entity(PatientAdditionalFieldOption.class));
        assertNotNull(sessionFactory.getMetamodel().entity(PatientAdditionalFieldValue.class));

        sessionFactory.close();
    }
}
