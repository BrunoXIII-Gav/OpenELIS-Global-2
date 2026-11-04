package org.openelisglobal.test.service;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

public class SystemTestCatalogTest {
    private Resource resource(String text) {
        return new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void readsBootstrapIdentityWithoutUsingIdNameOrDate() {
        String guid = UUID.randomUUID().toString();
        var catalog = new SystemTestCatalog(
                resource("COPY clinlims.test (description, guid, id, lastupdated) FROM stdin;\n" + "Renamed seed\t"
                        + guid + "\t90000\t2099-01-01\n\\.\n"),
                new Resource[0]);
        assertTrue(catalog.contains(guid.toUpperCase()));
        assertFalse(catalog.contains(UUID.randomUUID().toString()));
        assertFalse(catalog.contains(null));
    }

    @Test
    public void readsOnlyTestGuidsFromXmlAndSqlInsertChanges() {
        String xmlGuid = UUID.randomUUID().toString();
        String sqlGuid = UUID.randomUUID().toString();
        String otherGuid = UUID.randomUUID().toString();
        String xml = "<databaseChangeLog><changeSet><insert tableName='test'><column name='guid' value='" + xmlGuid
                + "'/></insert><insert tableName='patient'><column name='guid' value='" + otherGuid
                + "'/></insert><sql>INSERT INTO clinlims.test (id, description, guid) VALUES "
                + "(nextval('test_seq'), 'Name, (with) ''quotes'';', '" + sqlGuid + "'); "
                + "INSERT INTO patient (guid) VALUES ('" + otherGuid + "');</sql></changeSet></databaseChangeLog>";
        var catalog = new SystemTestCatalog(resource(""), new Resource[] { resource(xml) });
        assertEquals(2, catalog.guids().size());
        assertTrue(catalog.contains(xmlGuid));
        assertTrue(catalog.contains(sqlGuid));
        assertFalse(catalog.contains(otherGuid));
    }

    @Test
    public void readsPackagedInstallationSources() {
        var catalog = new SystemTestCatalog();
        assertFalse(catalog.guids().isEmpty());
        assertFalse(catalog.contains(UUID.randomUUID().toString()));
    }
}
