package org.openelisglobal;

import static org.junit.Assert.*;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class UcdInstallationTest extends BaseWebContextSensitiveTest {
    @Autowired
    private DataSource dataSource;

    @Test
    public void installationUsesLaboratoryIdentifiersWithoutExtraSchema() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            var metadata = connection.getMetaData();
            try (var columns = metadata.getColumns(null, "clinlims", "sample_item", "cug_code")) {
                assertFalse("UCD must not create the removed identifier column", columns.next());
            }
            try (var tables = metadata.getTables(null, "clinlims", "%", new String[] { "TABLE" })) {
                while (tables.next())
                    assertFalse(tables.getString("TABLE_NAME").toLowerCase().contains("cug"));
            }
            try (var columns = metadata.getColumns(null, "clinlims", "sample", "accession_number")) {
                assertTrue("Orders retain their laboratory number", columns.next());
            }
        }
    }

    @Test
    public void removedServiceIsNotPresentInTheBuiltApplication() {
        assertNull(getClass().getClassLoader().getResource("org/openelisglobal/sample/service/SampleCugService.class"));
    }
}
