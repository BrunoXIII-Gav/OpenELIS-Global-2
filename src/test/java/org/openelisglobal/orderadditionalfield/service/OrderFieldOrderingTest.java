package org.openelisglobal.orderadditionalfield.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.dao.*;
import org.openelisglobal.orderadditionalfield.valueholder.*;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderFieldOrderingTest {
    private OrderAdditionalFieldServiceImpl service;
    private OrderAdditionalFieldDefinitionDAO definitions;
    private OrderAdditionalFieldDefinition field;

    @Before
    public void setup() {
        service = new OrderAdditionalFieldServiceImpl();
        definitions = mock(OrderAdditionalFieldDefinitionDAO.class);
        var fixed = mock(OrderFixedFieldConfigDAO.class);
        ReflectionTestUtils.setField(service, "definitionDAO", definitions);
        ReflectionTestUtils.setField(service, "fixedFieldConfigDAO", fixed);
        ReflectionTestUtils.setField(service, "optionDAO", mock(OrderAdditionalFieldOptionDAO.class));
        ReflectionTestUtils.setField(service, "valueDAO", mock(SampleOrderAdditionalFieldValueDAO.class));
        ReflectionTestUtils.setField(service, "fileDAO", mock(SampleOrderAdditionalFieldFileDAO.class));
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", mock(UserFieldOptionResolver.class));
        field = definition(1, false, 5);
        when(definitions.get(1)).thenReturn(Optional.of(field));
        when(definitions.findAll(true)).thenReturn(List.of(definition(2, true, 340)));
        when(definitions.findAll(false))
                .thenReturn(List.of(field, definition(2, true, 340), definition(3, false, 900)));
        var visible = new OrderFixedFieldConfig();
        visible.setFieldKey("priority");
        visible.setVisible(true);
        visible.setSortOrder(450);
        var hidden = new OrderFixedFieldConfig();
        hidden.setFieldKey("requestDate");
        hidden.setVisible(false);
        hidden.setSortOrder(1000);
        when(fixed.findAllOrdered()).thenReturn(List.of(visible, hidden));
    }

    @Test
    public void activatingAppendsAfterVisibleFixedAndActiveAdditionalFields() {
        var payload = new OrderAdditionalFieldPayload();
        payload.setActive(true);
        payload.setSortOrder(1);
        var result = service.updateField(1, payload, "12");
        assertTrue(field.getActive());
        assertEquals(Integer.valueOf(451), result.getSortOrder());
        assertEquals("12", field.getSysUserId());
    }

    @Test
    public void inactiveFieldsCannotBeAssignedAnOrder() {
        var payload = new OrderAdditionalFieldPayload();
        payload.setSortOrder(10);
        assertNull(service.updateField(1, payload, "12").getSortOrder());
        assertNull(field.getSortOrder());
    }

    @Test
    public void deactivatingClearsTheOrder() {
        field.setActive(true);
        service.deactivateField(1, "12");
        assertFalse(field.getActive());
        assertNull(field.getSortOrder());
    }

    @Test
    public void activeFieldsCanStillBeReordered() {
        field.setActive(true);
        var payload = new OrderAdditionalFieldPayload();
        payload.setSortOrder(7);
        assertEquals(Integer.valueOf(7), service.updateField(1, payload, "12").getSortOrder());
    }

    @Test
    public void activatingAppendsAfterAdditionalFieldsAndIgnoresItsPreviousPosition() {
        field.setSortOrder(9000);
        when(definitions.findAll(true)).thenReturn(List.of(field, definition(2, true, 500)));
        var payload = new OrderAdditionalFieldPayload();
        payload.setActive(true);
        assertEquals(Integer.valueOf(501), service.updateField(1, payload, "12").getSortOrder());
    }

    @Test
    public void creatingAnInactiveFieldDiscardsItsRequestedOrder() {
        var payload = new OrderAdditionalFieldPayload();
        payload.setFieldKey("newField");
        payload.setDisplayName("New field");
        payload.setFieldType("TEXT");
        payload.setActive(false);
        payload.setSortOrder(4);
        when(definitions.insert(any())).thenReturn(1);
        service.createField(payload, "12");
        verify(definitions).insert(argThat(created -> !created.getActive() && created.getSortOrder() == null));
    }

    @Test
    public void requesterLayoutEntriesCanBeSavedThroughFixedConfiguration() {
        var configs = service.getFixedFieldConfigs();
        for (String key : List.of("requesterSearch", "requesterProfileFields")) {
            var config = configs.stream().filter(c -> key.equals(c.getFieldKey())).findFirst().orElseThrow();
            config.setSortOrder(3);
            service.upsertFixedFieldConfigs(List.of(config), "12");
        }
    }

    private OrderAdditionalFieldDefinition definition(int id, boolean active, int order) {
        var definition = new OrderAdditionalFieldDefinition();
        definition.setId(id);
        definition.setFieldKey("field" + id);
        definition.setDisplayName("Field " + id);
        definition.setFieldType("TEXT");
        definition.setActive(active);
        definition.setSortOrder(order);
        return definition;
    }
}
