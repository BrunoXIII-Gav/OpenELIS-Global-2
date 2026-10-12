package org.openelisglobal.testadditionalfield.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.dao.AnalysisAdditionalFieldValueDAO;
import org.openelisglobal.testadditionalfield.dao.TestAdditionalFieldDefinitionDAO;
import org.openelisglobal.testadditionalfield.dao.TestAdditionalFieldOptionDAO;
import org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition;

@RunWith(MockitoJUnitRunner.class)
public class TestAdditionalFieldServiceTest {
    @Mock
    private TestAdditionalFieldDefinitionDAO definitionDAO;
    @Mock
    private TestAdditionalFieldOptionDAO optionDAO;
    @Mock
    private AnalysisAdditionalFieldValueDAO valueDAO;
    @Mock
    private UserFieldOptionResolver userFieldOptionResolver;
    @InjectMocks
    private TestAdditionalFieldServiceImpl service;

    @Test
    public void createsUserFieldWithSameLabelAsSavedTextFields() {
        assertCreatesIndependentField("USER");
    }

    @Test
    public void createsTextFieldWithSameLabelAsSavedTextFields() {
        assertCreatesIndependentField("TEXT");
    }

    private void assertCreatesIndependentField(String newType) {
        TestAdditionalFieldDefinition first = definition(10, "responsible_analyst_1", "TEXT", 1);
        TestAdditionalFieldDefinition second = definition(11, "responsible_analyst_2", "TEXT", 2);
        when(definitionDAO.findByTestId(100, true)).thenReturn(List.of(first, second));
        when(valueDAO.countByFieldDefinitionId(10)).thenReturn(1L);
        when(valueDAO.countByFieldDefinitionId(11)).thenReturn(1L);
        when(definitionDAO.insert(any(TestAdditionalFieldDefinition.class))).thenReturn(12);
        TestAdditionalFieldPayload newField = payload(null, "responsible_validator", newType);

        service.replaceFieldsForTest("100", List.of(payload(10, first.getFieldKey(), "TEXT"),
                payload(11, second.getFieldKey(), "TEXT"), newField), "1");

        ArgumentCaptor<TestAdditionalFieldDefinition> created = ArgumentCaptor
                .forClass(TestAdditionalFieldDefinition.class);
        verify(definitionDAO).insert(created.capture());
        assertEquals(Integer.valueOf(100), created.getValue().getTestId());
        assertEquals(Integer.valueOf(12), created.getValue().getId());
        assertEquals("responsible_validator", created.getValue().getFieldKey());
        assertEquals("Responsible", created.getValue().getDisplayName());
        assertEquals(newType, created.getValue().getFieldType());
        assertEquals("responsible_analyst_1", first.getFieldKey());
        assertEquals("responsible_analyst_2", second.getFieldKey());
        assertEquals("TEXT", first.getFieldType());
        assertEquals("TEXT", second.getFieldType());
        assertTrue(first.getActive());
        assertTrue(second.getActive());
        verify(definitionDAO).update(first);
        verify(definitionDAO).update(second);
    }

    @Test
    public void updatesExistingFieldByKeyWithoutIdAfterLabelChange() {
        TestAdditionalFieldDefinition existing = definition(10, "responsible_analyst", "TEXT", 1);
        when(definitionDAO.findByTestId(100, true)).thenReturn(List.of(existing));
        when(valueDAO.countByFieldDefinitionId(10)).thenReturn(1L);
        TestAdditionalFieldPayload field = payload(null, existing.getFieldKey(), "TEXT");
        field.setDisplayName("Analyst responsible");

        service.replaceFieldsForTest("100", List.of(field), "1");

        verify(definitionDAO).update(existing);
        verify(definitionDAO, never()).insert(any(TestAdditionalFieldDefinition.class));
        assertEquals("Analyst responsible", existing.getDisplayName());
        assertEquals(Integer.valueOf(10), existing.getId());
    }

    @Test
    public void updatesExistingFieldByIdAfterLabelChange() {
        TestAdditionalFieldDefinition existing = definition(10, "responsible_analyst", "TEXT", 1);
        when(definitionDAO.findByTestId(100, true)).thenReturn(List.of(existing));
        TestAdditionalFieldPayload field = payload(10, existing.getFieldKey(), "TEXT");
        field.setDisplayName("Analyst responsible");

        service.replaceFieldsForTest("100", List.of(field), "1");

        verify(definitionDAO).update(existing);
        verify(definitionDAO, never()).insert(any(TestAdditionalFieldDefinition.class));
        assertEquals("Analyst responsible", existing.getDisplayName());
    }

    @Test
    public void rejectsTypeChangeForSameKeyWithSavedValues() {
        TestAdditionalFieldDefinition existing = definition(10, "responsible_analyst", "TEXT", 1);
        when(definitionDAO.findByTestId(100, true)).thenReturn(List.of(existing));
        when(valueDAO.countByFieldDefinitionId(10)).thenReturn(1L);

        try {
            service.replaceFieldsForTest("100", List.of(payload(null, existing.getFieldKey(), "USER")), "1");
            fail("A saved field must retain its type");
        } catch (IllegalStateException expected) {
            assertEquals("This field already has saved data and its type cannot be changed.", expected.getMessage());
        }

        assertEquals("TEXT", existing.getFieldType());
        verify(definitionDAO, never()).insert(any(TestAdditionalFieldDefinition.class));
        verify(definitionDAO, never()).update(any(TestAdditionalFieldDefinition.class));
    }

    private TestAdditionalFieldDefinition definition(int id, String key, String type, int order) {
        TestAdditionalFieldDefinition definition = new TestAdditionalFieldDefinition();
        definition.setId(id);
        definition.setTestId(100);
        definition.setFieldKey(key);
        definition.setDisplayName("Responsible");
        definition.setFieldType(type);
        definition.setActive(true);
        definition.setSortOrder(order);
        return definition;
    }

    private TestAdditionalFieldPayload payload(Integer id, String key, String type) {
        TestAdditionalFieldPayload payload = new TestAdditionalFieldPayload();
        payload.setId(id);
        payload.setFieldKey(key);
        payload.setDisplayName("Responsible");
        payload.setFieldType(type);
        return payload;
    }
}
