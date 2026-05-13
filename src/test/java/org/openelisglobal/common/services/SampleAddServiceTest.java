package org.openelisglobal.common.services;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.Map;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.openelisglobal.sample.valueholder.Sample;

@Ignore("SampleAddService uses static SpringContext lookups at class init and requires full app context")
public class SampleAddServiceTest {

    private SampleAddService sampleAddService;

    @Before
    public void setUp() {
        Sample sample = new Sample();
        sample.setAccessionNumber("ACC-001");
        sampleAddService = new SampleAddService("<samples/>", "1", sample, "2026-03-21");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void parseAdditionalFieldValues_shouldReadAttributeValueAndTextValue() throws Exception {
        Document document = DocumentHelper
                .parseText("<sample>" + "  <additionalFields>" + "    <field key='batch_code' value='  BC-001  '/>"
                        + "    <field key='storage_note'>  Keep in cold room  </field>" + "  </additionalFields>"
                        + "</sample>");
        Element sampleElement = document.getRootElement();

        Method method = SampleAddService.class.getDeclaredMethod("parseAdditionalFieldValues", Element.class);
        method.setAccessible(true);

        Map<String, String> values = (Map<String, String>) method.invoke(sampleAddService, sampleElement);

        assertEquals(2, values.size());
        assertEquals("BC-001", values.get("batch_code"));
        assertEquals("Keep in cold room", values.get("storage_note"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void parseAdditionalFieldValues_shouldIgnoreBlankKeysAndReturnEmptyWhenNodeMissing() throws Exception {
        Document withoutAdditionalFields = DocumentHelper.parseText("<sample></sample>");

        Method method = SampleAddService.class.getDeclaredMethod("parseAdditionalFieldValues", Element.class);
        method.setAccessible(true);

        Map<String, String> emptyValues = (Map<String, String>) method.invoke(sampleAddService,
                withoutAdditionalFields.getRootElement());
        assertTrue(emptyValues.isEmpty());

        Document withBlankKey = DocumentHelper.parseText("<sample>" + "  <additionalFields>"
                + "    <field key='' value='x'/>" + "    <field value='y'/>" + "  </additionalFields>" + "</sample>");

        Map<String, String> valuesWithBlankKeys = (Map<String, String>) method.invoke(sampleAddService,
                withBlankKey.getRootElement());
        assertTrue(valuesWithBlankKeys.isEmpty());
    }
}
