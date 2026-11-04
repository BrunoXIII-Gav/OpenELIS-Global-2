package org.openelisglobal.sample.service;

import java.sql.Connection;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

public class IdentifierIntegrationSupport {
    Liquibase migration(Connection connection, String file) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        database.setDefaultSchemaName("clinlims");
        return new Liquibase(file, new ClassLoaderResourceAccessor(), database);
    }

    SessionFactory factory(String url, String user, String password, String... mappings) {
        Configuration config = new Configuration()
                .addAnnotatedClass(org.openelisglobal.audittrail.valueholder.AuditEvent.class)
                .addResource("hibernate/patient-identifier-lock.hbm.xml");
        for (String mapping : mappings)
            config.addResource(mapping);
        config.setProperty("hibernate.connection.url",
                url + (url.contains("?") ? "&" : "?") + "currentSchema=clinlims");
        config.setProperty("hibernate.connection.username", user);
        config.setProperty("hibernate.connection.password", password);
        config.setProperty("hibernate.default_schema", "clinlims");
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        config.setProperty("hibernate.query.factory_class",
                "org.hibernate.hql.internal.classic.ClassicQueryTranslatorFactory");
        config.setProperty("hibernate.search.enabled", "false");
        config.setProperty("hibernate.generate_statistics", "true");
        config.setProperty("hibernate.connection.pool_size", "12");
        return config.buildSessionFactory();
    }

    @SuppressWarnings("unchecked")
    <T> T proxy(T target, JpaTransactionManager manager, Class<T> type) {
        var factory = new ProxyFactory(target);
        factory.setInterfaces(type);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    long scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
