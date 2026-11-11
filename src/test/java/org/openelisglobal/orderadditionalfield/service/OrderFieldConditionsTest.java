package org.openelisglobal.orderadditionalfield.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldFileDAO;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldValueDAO;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldValue;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.valueholder.OrderPriority;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderFieldConditionsTest {
    private OrderAdditionalFieldServiceImpl service;
    private SampleOrderAdditionalFieldValueDAO values;
    private OrderAdditionalFieldPayload source;
    private OrderAdditionalFieldPayload target;

    @Before
    public void setup() {
        service = new OrderAdditionalFieldServiceImpl();
        values = mock(SampleOrderAdditionalFieldValueDAO.class);
        ReflectionTestUtils.setField(service, "valueDAO", values);
        ReflectionTestUtils.setField(service, "fileDAO", mock(SampleOrderAdditionalFieldFileDAO.class));
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", mock(UserFieldOptionResolver.class));
        source = field(1, "source");
        target = field(2, "target");
    }

    @Test
    public void defaultValuesActivateRequiredRules() {
        source.setFieldType("BOOLEAN");
        source.setDefaultValue("true");
        target.setMetadataJson("{\"rules\":{\"requiredWhen\":[{\"fieldKey\":\"source\",\"operator\":\"istrue\"}]}}");
        assertThrows(LIMSRuntimeException.class, () -> persist(Map.of(), Map.of()));
        persist(Map.of("source", "false"), Map.of());
    }

    @Test
    public void initialUncheckedCheckboxMatchesIsFalse() {
        source.setFieldType("BOOLEAN");
        target.setMetadataJson("{\"rules\":{\"requiredWhen\":[{\"fieldKey\":\"source\",\"operator\":\"isfalse\"}]}}");
        assertThrows(LIMSRuntimeException.class, () -> persist(Map.of(), Map.of()));
    }

    @Test
    public void partialEditUsesStoredSourceUnlessExplicitlyOverridden() {
        source.setFieldType("BOOLEAN");
        target.setMetadataJson("{\"rules\":{\"requiredWhen\":[{\"fieldKey\":\"source\",\"operator\":\"istrue\"}]}}");
        var previous = new SampleOrderAdditionalFieldValue();
        previous.setFieldDefinitionId(1);
        previous.setFieldValue("true");
        when(values.findBySampleIdAndFieldDefinitionIds(eq(100), anyList())).thenReturn(List.of(previous));
        assertThrows(LIMSRuntimeException.class, () -> persist(Map.of("target", ""), Map.of()));
        persist(Map.of("source", "false", "target", ""), Map.of());
    }

    @Test
    public void fixedOrderFieldsActivateRulesOnTheServer() {
        target.setMetadataJson(
                "{\"rules\":{\"requiredWhen\":[{\"fieldKey\":\"priority\",\"operator\":\"equals\",\"value\":\"STAT\"}]}}");
        var order = new SampleOrderItem();
        order.setPriority(OrderPriority.STAT);
        order.setProviderFirstName("Ana");
        var context = OrderAdditionalFieldServiceImpl.fixedConditionValues(order, true);
        assertEquals("STAT", context.get("priority"));
        assertEquals("Ana", context.get("providerFirstName"));
        assertEquals("true", context.get("rememberSiteAndRequester"));
        assertThrows(LIMSRuntimeException.class, () -> persist(Map.of(), context));
        persist(Map.of(), Map.of("priority", "ROUTINE"));
    }

    @Test
    public void clearingRequiredDefaultedTextDoesNotPassValidation() {
        target.setRequired(true);
        target.setDefaultValue("Default");
        assertThrows(LIMSRuntimeException.class, () -> persist(Map.of("target", ""), Map.of()));
        persist(Map.of(), Map.of());
    }

    @Test
    public void allAndAnyApplyToVisibilityAndRequiredIndependently() {
        String conditions = "[{\"fieldKey\":\"source\",\"operator\":\"equals\",\"value\":\"yes\"},"
                + "{\"fieldKey\":\"priority\",\"operator\":\"equals\",\"value\":\"STAT\"}]";
        target.setMetadataJson("{\"rules\":{\"logic\":\"ALL\",\"requiredWhen\":" + conditions + "}}");
        persist(Map.of("source", "yes"), Map.of("priority", "ROUTINE"));
        target.setMetadataJson("{\"rules\":{\"logic\":\"ANY\",\"requiredWhen\":" + conditions + "}}");
        assertThrows(LIMSRuntimeException.class, () -> persist(Map.of("source", "yes"), Map.of("priority", "ROUTINE")));
        target.setRequired(true);
        target.setMetadataJson("{\"rules\":{\"logic\":\"ALL\",\"visibleWhen\":" + conditions + "}}");
        persist(Map.of("source", "yes"), Map.of("priority", "ROUTINE"));
        target.setMetadataJson("{\"rules\":{\"logic\":\"ANY\",\"visibleWhen\":" + conditions + "}}");
        assertThrows(LIMSRuntimeException.class, () -> persist(Map.of("source", "yes"), Map.of("priority", "ROUTINE")));
    }

    @Test
    public void allComparisonOperatorsCanActivateARequiredRule() {
        for (String rule : List.of("\"operator\":\"equals\",\"value\":\"YES\"",
                "\"operator\":\"notequals\",\"value\":\"no\"", "\"operator\":\"in\",\"values\":[\"no\",\"YES\"]",
                "\"operator\":\"notin\",\"values\":[\"no\"]", "\"operator\":\"hasvalue\"", "\"operator\":\"istrue\"")) {
            target.setMetadataJson("{\"rules\":{\"requiredWhen\":[{\"fieldKey\":\"source\"," + rule + "}]}}");
            assertThrows(rule, LIMSRuntimeException.class, () -> persist(Map.of("source", "yes"), Map.of()));
        }
    }

    private OrderAdditionalFieldPayload field(int id, String key) {
        var field = new OrderAdditionalFieldPayload();
        field.setId(id);
        field.setFieldKey(key);
        field.setDisplayName(key);
        field.setFieldType("TEXT");
        return field;
    }

    private void persist(Map<String, String> input, Map<String, String> fixed) {
        service.validateAndPersistSampleValues("100", input, null, "12", List.of(source, target), fixed);
    }
}
