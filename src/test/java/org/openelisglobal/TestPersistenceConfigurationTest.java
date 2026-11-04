package org.openelisglobal;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;

/**
 * Guards the test persistence unit against omissions when application entities
 * are added.
 */
public class TestPersistenceConfigurationTest {
    @Test
    public void testPersistenceIncludesEveryExplicitApplicationEntity() throws Exception {
        Set<String> missing = entities("persistence/persistence.xml");
        missing.removeAll(entities("persistence/test-persistence.xml"));
        assertTrue("Entities missing from the test persistence unit: " + missing, missing.isEmpty());
    }

    private Set<String> entities(String resource) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var classes = factory.newDocumentBuilder().parse(stream).getElementsByTagName("class");
            Set<String> entities = new HashSet<>();
            for (int index = 0; index < classes.getLength(); index++) {
                entities.add(classes.item(index).getTextContent().trim());
            }
            return entities;
        }
    }
}
