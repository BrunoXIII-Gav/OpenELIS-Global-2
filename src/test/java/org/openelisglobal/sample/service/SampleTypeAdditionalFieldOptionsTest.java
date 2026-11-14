package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldOptionPayload;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.dao.SampleItemAdditionalFieldValueDAO;
import org.openelisglobal.sample.dao.SampleTypeAdditionalFieldDefinitionDAO;
import org.openelisglobal.sample.dao.SampleTypeAdditionalFieldOptionDAO;
import org.openelisglobal.sample.valueholder.SampleItemAdditionalFieldValue;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldDefinition;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldOption;
import org.springframework.test.util.ReflectionTestUtils;

public class SampleTypeAdditionalFieldOptionsTest {
    private SampleTypeAdditionalFieldServiceImpl service;
    private SampleTypeAdditionalFieldDefinitionDAO definitionDAO;
    private SampleTypeAdditionalFieldOptionDAO optionDAO;
    private SampleItemAdditionalFieldValueDAO valueDAO;
    private SampleTypeAdditionalFieldDefinition definition;
    private SampleTypeAdditionalFieldOption savedOption;

    @Before
    public void setup() {
        service = new SampleTypeAdditionalFieldServiceImpl();
        definitionDAO = mock(SampleTypeAdditionalFieldDefinitionDAO.class);
        optionDAO = mock(SampleTypeAdditionalFieldOptionDAO.class);
        valueDAO = mock(SampleItemAdditionalFieldValueDAO.class);
        ReflectionTestUtils.setField(service, "definitionDAO", definitionDAO);
        ReflectionTestUtils.setField(service, "optionDAO", optionDAO);
        ReflectionTestUtils.setField(service, "valueDAO", valueDAO);
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", mock(UserFieldOptionResolver.class));
        definition = new SampleTypeAdditionalFieldDefinition();
        definition.setId(1);
        definition.setTypeOfSampleId(2);
        definition.setFieldKey("selection");
        definition.setDisplayName("Selection");
        definition.setFieldType("SELECT");
        definition.setActive(true);
        savedOption = new SampleTypeAdditionalFieldOption();
        savedOption.setId(10);
        savedOption.setFieldDefinitionId(1);
        savedOption.setOptionKey("old");
        savedOption.setOptionLabel("Old");
        savedOption.setActive(true);
        when(definitionDAO.get(1)).thenReturn(Optional.of(definition));
        when(optionDAO.get(10)).thenReturn(Optional.of(savedOption));
        when(optionDAO.findByDefinitionId(1, true)).thenReturn(List.of(savedOption));
        when(valueDAO.countByFieldDefinitionId(1)).thenReturn(1L);
    }

    @Test
    public void activeFieldsIncludeRetiredOptionsForHistoricalDisplay() {
        savedOption.setActive(false);
        when(definitionDAO.findBySampleTypeId(2, false)).thenReturn(List.of(definition));
        when(optionDAO.findByDefinitionIds(List.of(1), false)).thenReturn(List.of(savedOption));
        var fields = service.getFieldsForSampleType("2", false, true);
        assertEquals(1, fields.size());
        assertEquals("Old", fields.get(0).getOptions().get(0).getOptionLabel());
        assertFalse(fields.get(0).getOptions().get(0).getActive());
    }

    @Test
    public void batchedActiveFieldsKeepRetiredOptionsForExistingRecords() {
        savedOption.setActive(false);
        when(definitionDAO.findBySampleTypeIds(List.of(2), true)).thenReturn(List.of(definition));
        when(optionDAO.findByDefinitionIds(List.of(1), false)).thenReturn(List.of(savedOption));
        var fields = service.getActiveFieldsForSampleTypes(List.of("2"));
        assertEquals("Old", fields.get("2").get(0).getOptions().get(0).getOptionLabel());
        assertFalse(fields.get("2").get(0).getOptions().get(0).getActive());
    }

    @Test
    public void usedFieldAllowsDeactivationAndAddingOptions() {
        var old = optionPayload(10, "old", "Old", false);
        var added = optionPayload(null, "new", "New", true);
        var newOption = new SampleTypeAdditionalFieldOption();
        newOption.setId(11);
        when(optionDAO.insert(any())).thenReturn(11);
        when(optionDAO.get(11)).thenReturn(Optional.of(newOption));

        service.updateField(1, fieldPayload("SELECT", List.of(old, added)), "12");

        assertFalse(savedOption.getActive());
        assertEquals("old", savedOption.getOptionKey());
        assertEquals("Old", savedOption.getOptionLabel());
        assertEquals("12", savedOption.getSysUserId());
        verify(optionDAO).insert(argThat(option -> "new".equals(option.getOptionKey())
                && "New".equals(option.getOptionLabel()) && Boolean.TRUE.equals(option.getActive())));
        verify(optionDAO, never()).delete(any());
    }

    @Test
    public void usedFieldAllowsReactivation() {
        savedOption.setActive(false);
        service.updateField(1, fieldPayload("SELECT", List.of(optionPayload(10, "old", "Old", true))), "12");
        assertTrue(savedOption.getActive());
    }

    @Test
    public void usedFieldRejectsRemovingSavedOptions() {
        assertRejected(() -> service.updateField(1, fieldPayload("SELECT", List.of()), "12"));
        verify(optionDAO, never()).update(any());
    }

    @Test
    public void usedFieldRejectsChangingOptionIdentityAndType() {
        assertRejected(() -> service.updateField(1,
                fieldPayload("SELECT", List.of(optionPayload(10, "changed", "Old", true))), "12"));
        assertRejected(() -> service.updateField(1,
                fieldPayload("SELECT", List.of(optionPayload(10, "old", "Changed", true))), "12"));
        assertRejected(() -> service.updateField(1, fieldPayload("TEXT", null), "12"));
        verify(optionDAO, never()).update(any());
    }

    @Test
    public void directOptionUpdateAlsoProtectsHistoricalKeys() {
        assertRejected(() -> service.updateOption(10, optionPayload(10, "changed", "Old", false), "12"));
        assertEquals("old", savedOption.getOptionKey());
        verify(optionDAO, never()).update(any());
    }

    @Test
    public void optionFromAnotherFieldCannotBeUpdated() {
        when(valueDAO.countByFieldDefinitionId(1)).thenReturn(0L);
        savedOption.setFieldDefinitionId(2);
        assertRejected(() -> service.updateField(1,
                fieldPayload("SELECT", List.of(optionPayload(10, "old", "Old", false))), "12"));
        verify(optionDAO, never()).update(any());
    }

    @Test
    public void inactiveSingleSelectionCanBeRetainedButCannotBeNewlyAssigned() {
        var field = fieldPayload("SELECT", List.of(optionPayload(10, "old", "Old", false)));
        field.setRequired(true);
        var existing = new SampleItemAdditionalFieldValue();
        existing.setFieldValue("old");
        when(valueDAO.findBySampleItemIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(existing));
        persist(field, "old");
        service.validateAndPersistSampleItemValues("2", "100", Map.of(), "12", Map.of("2", List.of(field)));
        verify(valueDAO, never()).update(any());
        when(valueDAO.findBySampleItemIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.empty());
        assertRejected(() -> persist(field, "old"));
    }

    @Test
    public void multiselectCanKeepHistoricalSelectionWhileAddingAnActiveOption() {
        var field = fieldPayload("MULTISELECT", List.of(optionPayload(10, "old", "Old", false),
                optionPayload(11, "new", "New", true), optionPayload(12, "retired", "Retired", false)));
        var existing = new SampleItemAdditionalFieldValue();
        existing.setId(20);
        existing.setFieldValue("old");
        when(valueDAO.findBySampleItemIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(existing));
        persist(field, "old,new");
        assertEquals("old,new", existing.getFieldValue());
        verify(valueDAO).update(existing);
        assertRejected(() -> persist(field, "old,new,retired"));
    }

    private void persist(SampleTypeAdditionalFieldPayload field, String value) {
        service.validateAndPersistSampleItemValues("2", "100", Map.of("selection", value), "12",
                Map.of("2", List.of(field)));
    }

    private SampleTypeAdditionalFieldPayload fieldPayload(String type,
            List<SampleTypeAdditionalFieldOptionPayload> options) {
        var payload = new SampleTypeAdditionalFieldPayload();
        payload.setId(1);
        payload.setSampleTypeId("2");
        payload.setFieldKey("selection");
        payload.setDisplayName("Selection");
        payload.setFieldType(type);
        payload.setOptions(options);
        return payload;
    }

    private SampleTypeAdditionalFieldOptionPayload optionPayload(Integer id, String key, String label, boolean active) {
        var payload = new SampleTypeAdditionalFieldOptionPayload();
        payload.setId(id);
        payload.setOptionKey(key);
        payload.setOptionLabel(label);
        payload.setActive(active);
        return payload;
    }

    private void assertRejected(Runnable action) {
        try {
            action.run();
            fail("Invalid option change must be rejected");
        } catch (IllegalArgumentException | IllegalStateException | LIMSRuntimeException expected) {
            // The service must reject unsafe changes before persisting them.
        }
    }
}
