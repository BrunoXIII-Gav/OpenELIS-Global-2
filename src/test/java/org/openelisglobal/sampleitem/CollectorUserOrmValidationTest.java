package org.openelisglobal.sampleitem;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.Test;
import org.openelisglobal.sampleitem.valueholder.SampleItem;

/**
 * Loads the actual legacy XML mapping and its associations without a database.
 */
public class CollectorUserOrmValidationTest {
    @Test
    public void replacingLegacyTextCannotKeepAnUnrelatedUserReference() {
        SampleItem item = new SampleItem();
        item.setCollector("Ana Perez");
        item.setCollectorUserId("17");
        item.setCollector("Ana Perez");
        assertEquals("17", item.getCollectorUserId());
        item.setCollector("External collector");
        assertNull(item.getCollectorUserId());
    }

    @Test
    public void collectorReferenceAndHistoricalTextHaveDistinctMappings() {
        Configuration config = new Configuration();
        config.addResource("hibernate/hbm/SampleItem.hbm.xml");
        for (String entity : new String[] { "org.openelisglobal.sample.valueholder.Sample",
                "org.openelisglobal.typeofsample.valueholder.TypeOfSample",
                "org.openelisglobal.unitofmeasure.valueholder.UnitOfMeasure" }) {
            String xml = "<?xml version=\"1.0\"?><!DOCTYPE hibernate-mapping PUBLIC \"-//Hibernate/Hibernate Mapping DTD 3.0//EN\" \"http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd\">"
                    + "<hibernate-mapping><class name=\"" + entity
                    + "\"><id name=\"id\" type=\"string\"/></class></hibernate-mapping>";
            config.addInputStream(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        }
        config.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        config.setProperty("hibernate.temp.use_jdbc_metadata_defaults", "false");
        config.setProperty("hibernate.hbm2ddl.auto", "none");
        try (SessionFactory factory = config.buildSessionFactory()) {
            assertEquals(String.class,
                    factory.getMetamodel().entity(SampleItem.class).getAttribute("collectorUserId").getJavaType());
            assertEquals(String.class,
                    factory.getMetamodel().entity(SampleItem.class).getAttribute("collector").getJavaType());
        }
    }
}
