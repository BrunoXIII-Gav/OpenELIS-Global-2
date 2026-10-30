package org.openelisglobal.sample.service;

import static org.junit.Assert.*;

import java.sql.DriverManager;
import java.util.List;
import org.dbunit.database.DatabaseConnection;
import org.dbunit.dataset.xml.FlatXmlDataSetBuilder;
import org.dbunit.operation.DatabaseOperation;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.common.rest.provider.bean.homedashboard.OrderDisplayBean;
import org.openelisglobal.sampleitem.daoimpl.SampleItemDAOImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;

public class OrderDashboardCugIntegrationTest {
    @Test
    public void groupedOrdersIncludeAllActiveSampleCugsWithoutRequiringAnalyses() throws Exception {
        String url = System.getenv("CUG_TEST_JDBC_URL");
        if (url != null) {
            if (!url.matches("jdbc:postgresql://(127[.]0[.]0[.]1|localhost):[0-9]+/cug_prefix_test")) {
                throw new IllegalArgumentException("Use a disposable localhost cug_prefix_test database");
            }
            run(url, "cugtest", "");
        } else {
            try (var container = new PostgreSQLContainer<>("postgres:14.4")) {
                container.start();
                run(container.getJdbcUrl(), container.getUsername(), container.getPassword());
            }
        }
    }

    private void run(String url, String user, String password) throws Exception {
        try (var connection = DriverManager.getConnection(url, user, password)) {
            connection.createStatement().execute("DROP SCHEMA IF EXISTS clinlims CASCADE; CREATE SCHEMA clinlims");
            connection.setSchema("clinlims");
            new CugPrefixIntegrationTest().migration(connection, "liquibase/dashboard-cug-baseline.xml").update("");
            var fixture = new DatabaseConnection(connection, "clinlims");
            fixture.getConfig().setProperty(org.dbunit.database.DatabaseConfig.PROPERTY_DATATYPE_FACTORY,
                    new org.dbunit.ext.postgresql.PostgresqlDataTypeFactory());
            DatabaseOperation.INSERT.execute(fixture, new FlatXmlDataSetBuilder()
                    .build(getClass().getClassLoader().getResourceAsStream("testdata/dashboard-cug.xml")));
            connection.commit();

            var config = new Configuration().addResource("hibernate/dashboard-cug.hbm.xml");
            config.setProperty("hibernate.connection.url", url + "?currentSchema=clinlims");
            config.setProperty("hibernate.connection.username", user);
            config.setProperty("hibernate.connection.password", password);
            config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            config.setProperty("hibernate.hbm2ddl.auto", "none");
            config.setProperty("hibernate.search.enabled", "false");
            try (var factory = config.buildSessionFactory(); var em = factory.createEntityManager()) {
                var dao = new SampleItemDAOImpl();
                ReflectionTestUtils.setField(dao, "entityManager", em);
                em.getTransaction().begin();
                assertTrue(dao.findCugCodesByAccessionNumbers(List.of()).isEmpty());
                var first = order("LNG1");
                var second = order("LNG2");
                var withoutSamples = order("LNG3");
                var rows = List.of(first, second, withoutSamples);
                new OrderDashboardCugServiceImpl(dao).populateOrderCugs(rows);
                assertEquals(3, rows.size());
                assertEquals("5415.1, 5415.2", first.getCugCode());
                assertEquals("5416.1", second.getCugCode());
                assertEquals("", withoutSamples.getCugCode());
                em.getTransaction().commit();
            }
        }
    }

    private OrderDisplayBean order(String number) {
        var row = new OrderDisplayBean();
        row.setLabNumber(number);
        return row;
    }
}
