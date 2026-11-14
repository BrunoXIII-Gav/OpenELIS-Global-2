package org.openelisglobal.testadditionalfield.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldOptionPayload;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.dao.AnalysisAdditionalFieldValueDAO;
import org.openelisglobal.testadditionalfield.dao.TestAdditionalFieldDefinitionDAO;
import org.openelisglobal.testadditionalfield.dao.TestAdditionalFieldOptionDAO;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldOption;
import org.springframework.test.util.ReflectionTestUtils;

public class TestAdditionalFieldOptionsTest {
    private TestAdditionalFieldServiceImpl service;
    private TestAdditionalFieldDefinitionDAO definitionDAO;
    private TestAdditionalFieldOptionDAO optionDAO;
    private AnalysisAdditionalFieldValueDAO valueDAO;
    private TestAdditionalFieldDefinition definition;
    private TestAdditionalFieldOption savedOption;

    @Before
    public void setup() {
        service = new TestAdditionalFieldServiceImpl();
        definitionDAO = mock(TestAdditionalFieldDefinitionDAO.class);
        optionDAO = mock(TestAdditionalFieldOptionDAO.class);
        valueDAO = mock(AnalysisAdditionalFieldValueDAO.class);
        ReflectionTestUtils.setField(service, "definitionDAO", definitionDAO);
        ReflectionTestUtils.setField(service, "optionDAO", optionDAO);
        ReflectionTestUtils.setField(service, "valueDAO", valueDAO);
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", mock(UserFieldOptionResolver.class));
        definition = new TestAdditionalFieldDefinition();
        definition.setId(1);
        definition.setTestId(2);
        definition.setFieldKey("selection");
        definition.setDisplayName("Selection");
        definition.setFieldType("SELECT");
        definition.setActive(true);
        savedOption = new TestAdditionalFieldOption();
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
        when(definitionDAO.findByTestId(2, false)).thenReturn(List.of(definition));
        when(optionDAO.findByDefinitionIds(List.of(1), false)).thenReturn(List.of(savedOption));
        var fields = service.getFieldsForTest("2", false, true);
        assertEquals(1, fields.size());
        assertEquals("Old", fields.get(0).getOptions().get(0).getOptionLabel());
        assertFalse(fields.get(0).getOptions().get(0).getActive());
    }

    @Test
    public void batchedActiveFieldsKeepRetiredOptionsForExistingRecords() {
        savedOption.setActive(false);
        when(definitionDAO.findByTestIds(List.of(2), true)).thenReturn(List.of(definition));
        when(optionDAO.findByDefinitionIds(List.of(1), false)).thenReturn(List.of(savedOption));
        var fields = service.getActiveFieldsForTests(List.of("2"));
        assertEquals("Old", fields.get("2").get(0).getOptions().get(0).getOptionLabel());
        assertFalse(fields.get("2").get(0).getOptions().get(0).getActive());
    }

    @Test
    public void usedFieldAllowsDeactivationAndAddingOptions() {
        var old = optionPayload(10, "old", "Old", false);
        var added = optionPayload(null, "new", "New", true);
        var newOption = new TestAdditionalFieldOption();
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
        var existing = new AnalysisAdditionalFieldValue();
        existing.setFieldValue("old");
        when(valueDAO.findByAnalysisIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(existing));
        persist(field, "old");
        service.validateAndPersistAnalysisValues("2", "100", Map.of(), "12", Map.of("2", List.of(field)), true);
        verify(valueDAO, never()).update(any());
        when(valueDAO.findByAnalysisIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.empty());
        assertRejected(() -> persist(field, "old"));
    }

    @Test
    public void multiselectCanKeepHistoricalSelectionWhileAddingAnActiveOption() {
        var field = fieldPayload("MULTISELECT", List.of(optionPayload(10, "old", "Old", false),
                optionPayload(11, "new", "New", true), optionPayload(12, "retired", "Retired", false)));
        var existing = new AnalysisAdditionalFieldValue();
        existing.setId(20);
        existing.setFieldValue("old");
        when(valueDAO.findByAnalysisIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(existing));
        persist(field, "old,new");
        assertEquals("old,new", existing.getFieldValue());
        verify(valueDAO).update(existing);
        assertRejected(() -> persist(field, "old,new,retired"));
    }

    @Test
    public void testConfigurationReplacementAllowsOptionDeactivationAndAddition() {
        when(definitionDAO.findByTestId(2, true)).thenReturn(List.of(definition));
        var newOption = new TestAdditionalFieldOption();
        newOption.setId(11);
        when(optionDAO.insert(any())).thenReturn(11);
        when(optionDAO.get(11)).thenReturn(Optional.of(newOption));
        service.replaceFieldsForTest("2", List.of(fieldPayload("SELECT", List.of(
                optionPayload(10, "old", "Old", false), optionPayload(null, "new", "New", true)))), "12");
        assertFalse(savedOption.getActive());
        verify(optionDAO).insert(argThat(option -> "new".equals(option.getOptionKey())));
        verify(optionDAO, never()).delete(any());
    }

    @Test
    public void testConfigurationReplacementRejectsRemovingOrChangingSavedOptions() {
        when(definitionDAO.findByTestId(2, true)).thenReturn(List.of(definition));
        assertRejected(() -> service.replaceFieldsForTest("2", List.of(fieldPayload("SELECT", List.of())), "12"));
        assertRejected(() -> service.replaceFieldsForTest("2", List.of(fieldPayload("SELECT", List.of(
                optionPayload(10, "changed", "Old", true)))), "12"));
        verify(optionDAO, never()).update(any());
    }

    private void persist(TestAdditionalFieldPayload field, String value) {
        service.validateAndPersistAnalysisValues("2", "100", Map.of("selection", value), "12",
                Map.of("2", List.of(field)), true);
    }

    private TestAdditionalFieldPayload fieldPayload(String type, List<TestAdditionalFieldOptionPayload> options) {
        var payload = new TestAdditionalFieldPayload();
        payload.setId(1);
        payload.setTestId("2");
        payload.setFieldKey("selection");
        payload.setDisplayName("Selection");
        payload.setFieldType(type);
        payload.setOptions(options);
        return payload;
    }

    private TestAdditionalFieldOptionPayload optionPayload(Integer id, String key, String label, boolean active) {
        var payload = new TestAdditionalFieldOptionPayload();
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
