package org.openelisglobal.testconfiguration.packageio;

import static org.junit.Assert.*;

import jakarta.persistence.Entity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.testconfiguration.packageio.dao.TestPackageRegistry;
import org.openelisglobal.testconfiguration.packageio.valueholder.*;

public class TestPackageOrmValidationTest {
    public static Configuration configuration() {
        return configuration(true);
    }

    public static Configuration configuration(boolean journal) {
        Configuration config = new Configuration();
        for (Class<?> type : TestPackageRegistry.TYPES.values()) {
            if (type.isAnnotationPresent(Entity.class))
                config.addAnnotatedClass(type);
            else if (!journal && "Test".equals(type.getSimpleName())) {
                // Bootstrap the real pre-origin schema so Liquibase must add/backfill the
                // column.
                try (var mapping = TestPackageOrmValidationTest.class.getClassLoader()
                        .getResourceAsStream("hibernate/hbm/Test.hbm.xml")) {
                    String legacy = new String(mapping.readAllBytes(), StandardCharsets.UTF_8).replaceAll(
                            "(?s)<property name=\"(?:userCreated|creationOriginVerified(?:By|At)?)\".*?</property>",
                            "");
                    config.addInputStream(new ByteArrayInputStream(legacy.getBytes(StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else
                config.addResource("hibernate/hbm/" + type.getSimpleName() + ".hbm.xml");
        }
        config.addResource("hibernate/hbm/OrganizationType.hbm.xml");
        if (journal) {
            config.addAnnotatedClass(org.openelisglobal.test.valueholder.TestCreationOrigin.class);
            config.addAnnotatedClass(TestConfigurationRevision.class);
            config.addAnnotatedClass(TestConfigurationImportLock.class);
            config.addAnnotatedClass(TestConfigurationIdentity.class);
        }
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        config.setProperty("hibernate.default_schema", "clinlims");
        config.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        config.setProperty("hibernate.query.factory_class",
                "org.hibernate.hql.internal.classic.ClassicQueryTranslatorFactory");
        return config;
    }

    @Test
    public void allPortableConfigurationMappingsLoadWithoutADatabase() {
        try (SessionFactory factory = configuration().buildSessionFactory()) {
            for (Class<?> type : TestPackageRegistry.TYPES.values())
                assertNotNull(factory.getMetamodel().entity(type));
            assertEquals(String.class,
                    factory.getMetamodel().entity(TestConfigurationIdentity.class).getIdType().getJavaType());
            assertEquals(Integer.class,
                    factory.getMetamodel().entity(TestConfigurationImportLock.class).getIdType().getJavaType());
            assertEquals(boolean.class, factory.getMetamodel().entity(org.openelisglobal.test.valueholder.Test.class)
                    .getAttribute("userCreated").getJavaType());
            assertTrue(TestPackageRegistry.omitted("Test", "userCreated"));
            assertTrue(TestPackageRegistry.omitted("Test", "creationOriginVerified"));
            assertTrue(TestPackageRegistry.omitted("Test", "creationOriginVerifiedBy"));
            assertTrue(TestPackageRegistry.omitted("Test", "creationOriginVerifiedAt"));
        }
    }
}
