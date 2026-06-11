package org.openelisglobal.patientadditionalfield;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldOptionPayload;
import org.openelisglobal.patientadditionalfield.bean.PatientAdditionalFieldPayload;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldDefinitionDAO;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldOptionDAO;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldValueDAO;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldServiceImpl;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldDefinition;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldOption;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldValue;

@RunWith(MockitoJUnitRunner.class)
public class PatientAdditionalFieldServiceTest {

    @Mock
    private PatientAdditionalFieldDefinitionDAO definitionDAO;

    @Mock
    private PatientAdditionalFieldOptionDAO optionDAO;

    @Mock
    private PatientAdditionalFieldValueDAO valueDAO;

    @InjectMocks
    private PatientAdditionalFieldServiceImpl service;

    @Test
    public void getFields_shouldMapDefinitionsAndOptions() {
        PatientAdditionalFieldDefinition definition = definition(10, "patient_color", "Patient Color", "SELECT", true,
                true, 1, null, null);
        PatientAdditionalFieldOption red = option(100, 10, "red", "Red", 1, true);
        PatientAdditionalFieldOption blue = option(101, 10, "blue", "Blue", 2, true);

        when(definitionDAO.findAll(true)).thenReturn(Collections.singletonList(definition));
        when(optionDAO.findByDefinitionIds(Collections.singletonList(10), true)).thenReturn(Arrays.asList(red, blue));

        List<PatientAdditionalFieldPayload> payloads = service.getFields(false);

        assertEquals(1, payloads.size());
        PatientAdditionalFieldPayload payload = payloads.get(0);
        assertEquals(Integer.valueOf(10), payload.getId());
        assertEquals("patient_color", payload.getFieldKey());
        assertEquals("SELECT", payload.getFieldType());
        assertEquals(2, payload.getOptions().size());
        assertEquals("red", payload.getOptions().get(0).getOptionKey());
        assertEquals("blue", payload.getOptions().get(1).getOptionKey());
    }

    @Test
    public void createField_shouldPersistDefinitionAndOptionsForSelectFields() {
        PatientAdditionalFieldPayload payload = new PatientAdditionalFieldPayload();
        payload.setDisplayName("Patient Status");
        payload.setFieldType("SELECT");
        payload.setRequired(true);
        payload.setFieldKey("");

        PatientAdditionalFieldOptionPayload active = optionPayload("active", "Active", true, 1);
        PatientAdditionalFieldOptionPayload inactive = optionPayload("inactive", "Inactive", true, 2);
        payload.setOptions(Arrays.asList(active, inactive));

        when(definitionDAO.findByFieldKey("patient_status")).thenReturn(Optional.empty());
        when(definitionDAO.findAll(false))
                .thenReturn(Collections.singletonList(definition(9, "existing", "Existing", "TEXT", false, true, 2,
                        null, null)));
        when(definitionDAO.insert(any(PatientAdditionalFieldDefinition.class))).thenReturn(101);
        when(definitionDAO.get(101)).thenReturn(
                Optional.of(definition(101, "patient_status", "Patient Status", "SELECT", true, true, 3, null, null)));
        when(optionDAO.findByDefinitionId(101, true)).thenReturn(Arrays.asList(option(200, 101, "active", "Active", 1,
                true), option(201, 101, "inactive", "Inactive", 2, true)));

        PatientAdditionalFieldPayload created = service.createField(payload, "77");

        ArgumentCaptor<PatientAdditionalFieldDefinition> definitionCaptor = ArgumentCaptor
                .forClass(PatientAdditionalFieldDefinition.class);
        verify(definitionDAO).insert(definitionCaptor.capture());
        PatientAdditionalFieldDefinition insertedDefinition = definitionCaptor.getValue();

        assertEquals("patient_status", insertedDefinition.getFieldKey());
        assertEquals("Patient Status", insertedDefinition.getDisplayName());
        assertEquals("SELECT", insertedDefinition.getFieldType());
        assertEquals(Integer.valueOf(3), insertedDefinition.getSortOrder());
        assertEquals("77", insertedDefinition.getSysUserId());

        ArgumentCaptor<PatientAdditionalFieldOption> optionCaptor = ArgumentCaptor
                .forClass(PatientAdditionalFieldOption.class);
        verify(optionDAO, org.mockito.Mockito.times(2)).insert(optionCaptor.capture());
        List<PatientAdditionalFieldOption> insertedOptions = optionCaptor.getAllValues();
        assertEquals("active", insertedOptions.get(0).getOptionKey());
        assertEquals("inactive", insertedOptions.get(1).getOptionKey());

        assertNotNull(created);
        assertEquals(Integer.valueOf(101), created.getId());
        assertEquals(2, created.getOptions().size());
    }

    @Test
    public void validateAndPersistPatientValues_shouldNormalizeMultiselectAndInsert() {
        PatientAdditionalFieldPayload flags = new PatientAdditionalFieldPayload();
        flags.setId(10);
        flags.setFieldKey("flags");
        flags.setDisplayName("Flags");
        flags.setFieldType("MULTISELECT");
        flags.setRequired(false);
        flags.setOptions(Arrays.asList(optionPayload("A", "A", true, 1), optionPayload("B", "B", true, 2)));

        Map<String, String> values = new HashMap<>();
        values.put("flags", "A, B ,A");

        when(valueDAO.findByPatientIdAndFieldDefinitionId(200, 10)).thenReturn(Optional.empty());

        service.validateAndPersistPatientValues("200", values, "5", Collections.singletonList(flags));

        ArgumentCaptor<PatientAdditionalFieldValue> valueCaptor = ArgumentCaptor
                .forClass(PatientAdditionalFieldValue.class);
        verify(valueDAO).insert(valueCaptor.capture());

        PatientAdditionalFieldValue inserted = valueCaptor.getValue();
        assertEquals(Integer.valueOf(200), inserted.getPatientId());
        assertEquals(Integer.valueOf(10), inserted.getFieldDefinitionId());
        assertEquals("A,B", inserted.getFieldValue());
        assertEquals("5", inserted.getSysUserId());
    }

    @Test(expected = LIMSRuntimeException.class)
    public void validateAndPersistPatientValues_shouldFailForMissingRequiredValue() {
        PatientAdditionalFieldPayload requiredField = new PatientAdditionalFieldPayload();
        requiredField.setId(11);
        requiredField.setFieldKey("national_program");
        requiredField.setDisplayName("National Program");
        requiredField.setFieldType("TEXT");
        requiredField.setRequired(true);

        service.validateAndPersistPatientValues("200", Collections.emptyMap(), "5",
                Collections.singletonList(requiredField));
    }

    @Test
    public void getPatientValues_shouldIgnoreUnknownDefinitions() {
        PatientAdditionalFieldPayload known = new PatientAdditionalFieldPayload();
        known.setId(10);
        known.setFieldKey("known_key");

        PatientAdditionalFieldValue storedKnown = valueEntity(1, 200, 10, "stored");
        PatientAdditionalFieldValue storedUnknown = valueEntity(2, 200, 99, "ignored");

        when(valueDAO.findByPatientId(200)).thenReturn(Arrays.asList(storedKnown, storedUnknown));

        Map<String, String> result = service.getPatientValues("200", Collections.singletonList(known));

        assertEquals(1, result.size());
        assertEquals("stored", result.get("known_key"));
        assertTrue(!result.containsKey("99"));
        verify(definitionDAO, never()).findAll(any(Boolean.class));
    }

    private PatientAdditionalFieldDefinition definition(Integer id, String fieldKey, String displayName,
            String fieldType, boolean required, boolean active, Integer sortOrder, String defaultValue,
            Integer maxLength) {
        PatientAdditionalFieldDefinition definition = new PatientAdditionalFieldDefinition();
        definition.setId(id);
        definition.setFieldKey(fieldKey);
        definition.setDisplayName(displayName);
        definition.setFieldType(fieldType);
        definition.setRequired(required);
        definition.setActive(active);
        definition.setSortOrder(sortOrder);
        definition.setDefaultValue(defaultValue);
        definition.setMaxLength(maxLength);
        return definition;
    }

    private PatientAdditionalFieldOption option(Integer id, Integer fieldDefinitionId, String optionKey,
            String optionLabel, Integer sortOrder, boolean active) {
        PatientAdditionalFieldOption option = new PatientAdditionalFieldOption();
        option.setId(id);
        option.setFieldDefinitionId(fieldDefinitionId);
        option.setOptionKey(optionKey);
        option.setOptionLabel(optionLabel);
        option.setSortOrder(sortOrder);
        option.setActive(active);
        return option;
    }

    private PatientAdditionalFieldOptionPayload optionPayload(String optionKey, String optionLabel, boolean active,
            Integer sortOrder) {
        PatientAdditionalFieldOptionPayload payload = new PatientAdditionalFieldOptionPayload();
        payload.setOptionKey(optionKey);
        payload.setOptionLabel(optionLabel);
        payload.setActive(active);
        payload.setSortOrder(sortOrder);
        return payload;
    }

    private PatientAdditionalFieldValue valueEntity(Integer id, Integer patientId, Integer fieldDefinitionId,
            String fieldValue) {
        PatientAdditionalFieldValue value = new PatientAdditionalFieldValue();
        value.setId(id);
        value.setPatientId(patientId);
        value.setFieldDefinitionId(fieldDefinitionId);
        value.setFieldValue(fieldValue);
        return value;
    }
}
