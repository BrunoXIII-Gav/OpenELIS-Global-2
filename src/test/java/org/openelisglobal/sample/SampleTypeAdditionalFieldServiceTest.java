package org.openelisglobal.sample;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldOptionPayload;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.dao.SampleItemAdditionalFieldValueDAO;
import org.openelisglobal.sample.dao.SampleTypeAdditionalFieldDefinitionDAO;
import org.openelisglobal.sample.dao.SampleTypeAdditionalFieldOptionDAO;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldServiceImpl;
import org.openelisglobal.sample.valueholder.SampleItemAdditionalFieldValue;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldDefinition;
import org.openelisglobal.sample.valueholder.SampleTypeAdditionalFieldOption;

@RunWith(MockitoJUnitRunner.class)
public class SampleTypeAdditionalFieldServiceTest {

    @Mock
    private SampleTypeAdditionalFieldDefinitionDAO definitionDAO;

    @Mock
    private SampleTypeAdditionalFieldOptionDAO optionDAO;

    @Mock
    private SampleItemAdditionalFieldValueDAO valueDAO;

    @InjectMocks
    private SampleTypeAdditionalFieldServiceImpl service;

    @Test
    public void getFieldsForSampleType_shouldMapDefinitionsAndOptions() {
        SampleTypeAdditionalFieldDefinition definition = definition(10, 1, "sample_color", "Sample Color", "SELECT",
                true, true, 1, null, null);

        SampleTypeAdditionalFieldOption red = option(100, 10, "red", "Red", 1, true);
        SampleTypeAdditionalFieldOption blue = option(101, 10, "blue", "Blue", 2, true);

        when(definitionDAO.findBySampleTypeId(1, false)).thenReturn(Collections.singletonList(definition));
        when(optionDAO.findByDefinitionIds(Collections.singletonList(10), true)).thenReturn(Arrays.asList(red, blue));

        List<SampleTypeAdditionalFieldPayload> payloads = service.getFieldsForSampleType("1", false);

        assertEquals(1, payloads.size());
        SampleTypeAdditionalFieldPayload payload = payloads.get(0);
        assertEquals("1", payload.getSampleTypeId());
        assertEquals("sample_color", payload.getFieldKey());
        assertEquals("SELECT", payload.getFieldType());
        assertEquals(2, payload.getOptions().size());
        assertEquals("red", payload.getOptions().get(0).getOptionKey());
        assertEquals("blue", payload.getOptions().get(1).getOptionKey());
    }

    @Test(expected = IllegalArgumentException.class)
    public void createField_shouldFailWhenFieldKeyAlreadyExists() {
        SampleTypeAdditionalFieldPayload payload = new SampleTypeAdditionalFieldPayload();
        payload.setSampleTypeId("1");
        payload.setFieldKey("sample_color");
        payload.setDisplayName("Sample Color");
        payload.setFieldType("TEXT");

        when(definitionDAO.findBySampleTypeIdAndFieldKey(1, "sample_color"))
                .thenReturn(Optional.of(definition(10, 1, "sample_color", "Existing", "TEXT", false, true, 1, null, null)));

        service.createField(payload, "7");
    }

    @Test
    public void createField_shouldPersistDefinitionAndOptionsForSelectFields() {
        SampleTypeAdditionalFieldPayload payload = new SampleTypeAdditionalFieldPayload();
        payload.setSampleTypeId("1");
        payload.setDisplayName("Storage Condition");
        payload.setFieldType("SELECT");
        payload.setRequired(true);
        payload.setFieldKey("");

        SampleTypeAdditionalFieldOptionPayload optionCold = new SampleTypeAdditionalFieldOptionPayload();
        optionCold.setOptionLabel("Cold");
        optionCold.setSortOrder(1);
        optionCold.setActive(true);

        SampleTypeAdditionalFieldOptionPayload optionFrozen = new SampleTypeAdditionalFieldOptionPayload();
        optionFrozen.setOptionLabel("Frozen");
        optionFrozen.setSortOrder(2);
        optionFrozen.setActive(true);

        payload.setOptions(Arrays.asList(optionCold, optionFrozen));

        when(definitionDAO.findBySampleTypeIdAndFieldKey(1, "storage_condition")).thenReturn(Optional.empty());
        when(definitionDAO.findBySampleTypeId(1, true))
                .thenReturn(Collections.singletonList(definition(9, 1, "prev", "Previous", "TEXT", false, true, 2, null, null)));
        when(definitionDAO.insert(any(SampleTypeAdditionalFieldDefinition.class))).thenReturn(101);
        when(definitionDAO.get(101))
                .thenReturn(Optional.of(definition(101, 1, "storage_condition", "Storage Condition", "SELECT", true,
                        true, 3, null, null)));
        when(optionDAO.findByDefinitionId(101, true))
                .thenReturn(Arrays.asList(option(200, 101, "cold", "Cold", 1, true),
                        option(201, 101, "frozen", "Frozen", 2, true)));

        SampleTypeAdditionalFieldPayload created = service.createField(payload, "77");

        ArgumentCaptor<SampleTypeAdditionalFieldDefinition> definitionCaptor = ArgumentCaptor
                .forClass(SampleTypeAdditionalFieldDefinition.class);
        verify(definitionDAO).insert(definitionCaptor.capture());
        SampleTypeAdditionalFieldDefinition insertedDefinition = definitionCaptor.getValue();

        assertEquals("storage_condition", insertedDefinition.getFieldKey());
        assertEquals("Storage Condition", insertedDefinition.getDisplayName());
        assertEquals("SELECT", insertedDefinition.getFieldType());
        assertEquals(Integer.valueOf(3), insertedDefinition.getSortOrder());
        assertEquals("77", insertedDefinition.getSysUserId());

        ArgumentCaptor<SampleTypeAdditionalFieldOption> optionsCaptor = ArgumentCaptor
                .forClass(SampleTypeAdditionalFieldOption.class);
        verify(optionDAO, times(2)).insert(optionsCaptor.capture());
        List<SampleTypeAdditionalFieldOption> insertedOptions = optionsCaptor.getAllValues();
        assertEquals("cold", insertedOptions.get(0).getOptionKey());
        assertEquals("frozen", insertedOptions.get(1).getOptionKey());

        assertNotNull(created);
        assertEquals(Integer.valueOf(101), created.getId());
        assertEquals(2, created.getOptions().size());
    }

    @Test
    public void validateAndPersistSampleItemValues_shouldSkipWhenNoValuesProvided() {
        SampleTypeAdditionalFieldPayload requiredField = new SampleTypeAdditionalFieldPayload();
        requiredField.setId(10);
        requiredField.setSampleTypeId("1");
        requiredField.setFieldKey("batch_code");
        requiredField.setDisplayName("Batch Code");
        requiredField.setFieldType("TEXT");
        requiredField.setRequired(true);

        Map<String, List<SampleTypeAdditionalFieldPayload>> cache = new HashMap<>();
        cache.put("1", Collections.singletonList(requiredField));

        service.validateAndPersistSampleItemValues("1", "200", Collections.emptyMap(), "5", cache);

        verify(valueDAO, never()).insert(any(SampleItemAdditionalFieldValue.class));
        verify(valueDAO, never()).update(any(SampleItemAdditionalFieldValue.class));
    }

    @Test
    public void validateAndPersistSampleItemValues_shouldNormalizeMultiselectAndInsert() {
        SampleTypeAdditionalFieldPayload flags = new SampleTypeAdditionalFieldPayload();
        flags.setId(10);
        flags.setSampleTypeId("1");
        flags.setFieldKey("flags");
        flags.setDisplayName("Flags");
        flags.setFieldType("MULTISELECT");
        flags.setRequired(false);
        flags.setOptions(Arrays.asList(optionPayload("A", "A", true), optionPayload("B", "B", true)));

        Map<String, List<SampleTypeAdditionalFieldPayload>> cache = new HashMap<>();
        cache.put("1", Collections.singletonList(flags));

        Map<String, String> values = new HashMap<>();
        values.put("flags", "A, B ,A");

        when(valueDAO.findBySampleItemIdAndFieldDefinitionId(200, 10)).thenReturn(Optional.empty());

        service.validateAndPersistSampleItemValues("1", "200", values, "5", cache);

        ArgumentCaptor<SampleItemAdditionalFieldValue> valueCaptor = ArgumentCaptor
                .forClass(SampleItemAdditionalFieldValue.class);
        verify(valueDAO).insert(valueCaptor.capture());

        SampleItemAdditionalFieldValue inserted = valueCaptor.getValue();
        assertEquals(Integer.valueOf(200), inserted.getSampleItemId());
        assertEquals(Integer.valueOf(10), inserted.getFieldDefinitionId());
        assertEquals("A,B", inserted.getFieldValue());
        assertEquals("5", inserted.getSysUserId());
    }

    @Test(expected = LIMSRuntimeException.class)
    public void validateAndPersistSampleItemValues_shouldFailForInvalidSelectOption() {
        SampleTypeAdditionalFieldPayload status = new SampleTypeAdditionalFieldPayload();
        status.setId(11);
        status.setSampleTypeId("1");
        status.setFieldKey("status");
        status.setDisplayName("Status");
        status.setFieldType("SELECT");
        status.setRequired(false);
        status.setOptions(Collections.singletonList(optionPayload("ok", "OK", true)));

        Map<String, List<SampleTypeAdditionalFieldPayload>> cache = new HashMap<>();
        cache.put("1", Collections.singletonList(status));

        Map<String, String> values = new HashMap<>();
        values.put("status", "invalid");

        service.validateAndPersistSampleItemValues("1", "200", values, "5", cache);
    }

    @Test
    public void getActiveFieldsForSampleTypes_shouldReturnMappedListsBySampleType() {
        SampleTypeAdditionalFieldDefinition definition1 = definition(1, 1, "code", "Code", "TEXT", false, true, 1,
                null, null);
        SampleTypeAdditionalFieldDefinition definition2 = definition(2, 2, "room", "Room", "TEXT", false, true, 1,
                null, null);

        when(definitionDAO.findBySampleTypeIds(Arrays.asList(1, 2), true)).thenReturn(Arrays.asList(definition1, definition2));
        when(optionDAO.findByDefinitionIds(Arrays.asList(1, 2), true)).thenReturn(Collections.emptyList());

        Map<String, List<SampleTypeAdditionalFieldPayload>> result = service
                .getActiveFieldsForSampleTypes(Arrays.asList("1", "2", "1", "", " "));

        assertEquals(2, result.size());
        assertTrue(result.containsKey("1"));
        assertTrue(result.containsKey("2"));
        assertEquals(1, result.get("1").size());
        assertEquals("code", result.get("1").get(0).getFieldKey());
        assertEquals("room", result.get("2").get(0).getFieldKey());
    }

    @Test
    public void updateOption_shouldRejectDuplicateOptionKeyFromAnotherOption() {
        SampleTypeAdditionalFieldOption existing = option(5, 3, "old_key", "Old", 1, true);
        SampleTypeAdditionalFieldOption duplicate = option(6, 3, "dup", "Duplicate", 2, true);

        SampleTypeAdditionalFieldOptionPayload payload = new SampleTypeAdditionalFieldOptionPayload();
        payload.setOptionKey("dup");
        payload.setOptionLabel("Updated Label");

        when(optionDAO.get(5)).thenReturn(Optional.of(existing));
        when(optionDAO.findByDefinitionIdAndOptionKey(3, "dup")).thenReturn(Optional.of(duplicate));

        boolean thrown = false;
        try {
            service.updateOption(5, payload, "9");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }

        assertTrue(thrown);
        verify(optionDAO, never()).update(any(SampleTypeAdditionalFieldOption.class));
    }

    private SampleTypeAdditionalFieldDefinition definition(Integer id, Integer sampleTypeId, String fieldKey,
            String displayName, String fieldType, boolean required, boolean active, Integer sortOrder,
            String defaultValue, Integer maxLength) {
        SampleTypeAdditionalFieldDefinition definition = new SampleTypeAdditionalFieldDefinition();
        definition.setId(id);
        definition.setTypeOfSampleId(sampleTypeId);
        definition.setFieldKey(fieldKey);
        definition.setDisplayName(displayName);
        definition.setFieldType(fieldType);
        definition.setRequired(required);
        definition.setActive(active);
        definition.setSortOrder(sortOrder);
        definition.setDefaultValue(defaultValue);
        definition.setMaxLength(maxLength);
        definition.setMetadataJson(null);
        definition.setSysUserId("1");
        return definition;
    }

    private SampleTypeAdditionalFieldOption option(Integer id, Integer fieldDefinitionId, String optionKey,
            String optionLabel, Integer sortOrder, boolean active) {
        SampleTypeAdditionalFieldOption option = new SampleTypeAdditionalFieldOption();
        option.setId(id);
        option.setFieldDefinitionId(fieldDefinitionId);
        option.setOptionKey(optionKey);
        option.setOptionLabel(optionLabel);
        option.setSortOrder(sortOrder);
        option.setActive(active);
        option.setSysUserId("1");
        return option;
    }

    private SampleTypeAdditionalFieldOptionPayload optionPayload(String key, String label, boolean active) {
        SampleTypeAdditionalFieldOptionPayload payload = new SampleTypeAdditionalFieldOptionPayload();
        payload.setOptionKey(key);
        payload.setOptionLabel(label);
        payload.setActive(active);
        return payload;
    }
}
