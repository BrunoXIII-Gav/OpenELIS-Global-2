package org.openelisglobal.audittrail;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.openelisglobal.common.service.UserFieldOptionResolver;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.dao.AnalysisAdditionalFieldValueDAO;
import org.openelisglobal.testadditionalfield.service.TestAdditionalFieldServiceImpl;
import org.openelisglobal.testadditionalfield.valueholder.AnalysisAdditionalFieldValue;
import org.springframework.test.util.ReflectionTestUtils;

public class AdditionalFieldAuthorshipTest {
    @Test
    public void unchangedAndOmittedTestFieldsKeepTheirOriginalUser() {
        TestAdditionalFieldServiceImpl service = new TestAdditionalFieldServiceImpl();
        AnalysisAdditionalFieldValueDAO dao = mock(AnalysisAdditionalFieldValueDAO.class);
        ReflectionTestUtils.setField(service, "valueDAO", dao);
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", mock(UserFieldOptionResolver.class));
        TestAdditionalFieldPayload a = field(1, "a");
        TestAdditionalFieldPayload b = field(2, "b");
        AnalysisAdditionalFieldValue first = value(10, 1, "10", "11");
        AnalysisAdditionalFieldValue second = value(11, 2, "20", "12");
        when(dao.findByAnalysisIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(first));
        when(dao.findByAnalysisIdAndFieldDefinitionId(100, 2)).thenReturn(Optional.of(second));
        service.validateAndPersistAnalysisValues("9", "100", Map.of("a", "10"), "13", Map.of("9", List.of(a, b)),
                false);
        assertEquals("11", first.getSysUserId());
        assertEquals("12", second.getSysUserId());
        verify(dao, never()).update(any());
        verify(dao, never()).insert(any());
    }

    @Test
    public void changingOneFieldDoesNotReassignTheOther() {
        TestAdditionalFieldServiceImpl service = new TestAdditionalFieldServiceImpl();
        AnalysisAdditionalFieldValueDAO dao = mock(AnalysisAdditionalFieldValueDAO.class);
        ReflectionTestUtils.setField(service, "valueDAO", dao);
        ReflectionTestUtils.setField(service, "userFieldOptionResolver", mock(UserFieldOptionResolver.class));
        AnalysisAdditionalFieldValue a = value(10, 1, "10", "11");
        AnalysisAdditionalFieldValue b = value(11, 2, "20", "12");
        when(dao.findByAnalysisIdAndFieldDefinitionId(100, 1)).thenReturn(Optional.of(a));
        when(dao.findByAnalysisIdAndFieldDefinitionId(100, 2)).thenReturn(Optional.of(b));
        service.validateAndPersistAnalysisValues("9", "100", Map.of("a", "12", "b", "20"), "13",
                Map.of("9", List.of(field(1, "a"), field(2, "b"))), false);
        assertEquals("13", a.getSysUserId());
        assertEquals("12", b.getSysUserId());
        verify(dao).update(a);
        verify(dao, never()).update(b);
    }

    private TestAdditionalFieldPayload field(int id, String key) {
        TestAdditionalFieldPayload field = new TestAdditionalFieldPayload();
        field.setId(id);
        field.setTestId("9");
        field.setFieldKey(key);
        field.setFieldType("TEXT");
        field.setDisplayName(key);
        return field;
    }

    private AnalysisAdditionalFieldValue value(int id, int field, String text, String user) {
        AnalysisAdditionalFieldValue value = new AnalysisAdditionalFieldValue();
        value.setId(id);
        value.setAnalysisId(100);
        value.setFieldDefinitionId(field);
        value.setFieldValue(text);
        value.setSysUserId(user);
        return value;
    }
}
