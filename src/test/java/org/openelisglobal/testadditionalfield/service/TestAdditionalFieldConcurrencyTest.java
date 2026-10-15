package org.openelisglobal.testadditionalfield.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.sql.Timestamp;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.openelisglobal.testadditionalfield.dao.*;
import org.openelisglobal.testadditionalfield.util.FieldVersion;
import org.openelisglobal.testadditionalfield.valueholder.*;
import org.springframework.test.util.ReflectionTestUtils;

public class TestAdditionalFieldConcurrencyTest {
    private final TestAdditionalFieldServiceImpl service = new TestAdditionalFieldServiceImpl();
    private final AnalysisAdditionalFieldValueDAO values = mock(AnalysisAdditionalFieldValueDAO.class);
    private final TestAdditionalFieldDefinitionDAO definitions = mock(TestAdditionalFieldDefinitionDAO.class);
    private final TestAdditionalFieldOptionDAO options = mock(TestAdditionalFieldOptionDAO.class);
    private AnalysisAdditionalFieldValue a, b;

    @Before
    public void setup() {
        ReflectionTestUtils.setField(service, "valueDAO", values);
        ReflectionTestUtils.setField(service, "definitionDAO", definitions);
        ReflectionTestUtils.setField(service, "optionDAO", options);
        ReflectionTestUtils.setField(service, "userFieldOptionResolver",
                mock(org.openelisglobal.common.service.UserFieldOptionResolver.class));
        TestAdditionalFieldDefinition da = definition(1, "a"), db = definition(2, "b");
        when(definitions.findByTestIds(List.of(100), true)).thenReturn(List.of(da, db));
        a = value(1, "A changed");
        b = value(2, "B original");
        when(values.findByAnalysisIdAndFieldDefinitionIds(eq(50), anyList())).thenReturn(List.of(a, b));
    }

    @Test
    public void fullOldFormCannotOverwriteAnUnchangedField() {
        var form = form(Map.of("a", "A original", "b", "B changed"),
                Map.of("a", FieldVersion.of(value(1, "A original")), "b", FieldVersion.of(b)));
        service.prepareConcurrentUpdates(List.of(form));
        assertEquals(Map.of("b", "B changed"), form.getAdditionalFieldChanges());
        assertEquals("A changed", form.getAdditionalFieldValues().get("a"));
        verify(values).lockAnalyses(List.of("50"));
    }

    @Test
    public void sameFieldChangedByAnotherUserRejectsTheTransaction() {
        var form = form(Map.of("a", "My change"), Map.of("a", FieldVersion.of(value(1, "A original"))));
        try {
            service.prepareConcurrentUpdates(List.of(form));
            fail("Expected conflict");
        } catch (AdditionalFieldConflictException e) {
            assertEquals("RESULT_FIELD_CONFLICT", e.getCode());
            assertEquals("A changed", e.getConflicts().get(0).currentValue());
            assertEquals(FieldVersion.of(a), e.getConflicts().get(0).currentVersion());
        }
        verify(values, never()).update(any());
        verify(values, never()).insert(any());
    }

    @Test public void initialCreationHasAnExplicitAbsentVersion() {
        when(values.findByAnalysisIdAndFieldDefinitionIds(eq(50), anyList())).thenReturn(List.of());
        var form = form(Map.of("a", "First"), Map.of("a", FieldVersion.of(null)));
        service.prepareConcurrentUpdates(List.of(form));
        assertEquals(Map.of("a", "First"), form.getAdditionalFieldChanges());
    }

    @Test
    public void missingSnapshotCannotWriteBlindly() {
        var form = form(Map.of("a", "Change"), null);
        try {
            service.prepareConcurrentUpdates(List.of(form));
            fail("Expected reload requirement");
        } catch (AdditionalFieldConflictException e) {
            assertEquals("RESULT_FIELD_SNAPSHOT_REQUIRED", e.getCode());
        }
    }

    @Test
    public void versionsDetectAValueChangedAndChangedBack() {
        var original = value(1, "same");
        var newer = value(1, "same");
        newer.setLastupdated(Timestamp.valueOf("2026-10-14 12:00:01"));
        assertNotEquals(FieldVersion.of(original), FieldVersion.of(newer));
    }

    private TestResultItem form(Map<String, String> submitted, Map<String, String> versions) {
        TestResultItem form = new TestResultItem();
        form.setAnalysisId("50");
        form.setTestId("100");
        form.setAdditionalFieldValues(new HashMap<>(submitted));
        form.setAdditionalFieldVersions(versions);
        return form;
    }

    private TestAdditionalFieldDefinition definition(int id, String key) {
        var d = new TestAdditionalFieldDefinition();
        d.setId(id);
        d.setTestId(100);
        d.setFieldKey(key);
        d.setDisplayName(key);
        d.setFieldType("TEXT");
        d.setActive(true);
        return d;
    }

    private AnalysisAdditionalFieldValue value(int id, String text) {
        var v = new AnalysisAdditionalFieldValue();
        v.setId(id);
        v.setAnalysisId(50);
        v.setFieldDefinitionId(id);
        v.setFieldValue(text);
        v.setLastupdated(Timestamp.valueOf("2026-10-14 12:00:00"));
        return v;
    }
}
