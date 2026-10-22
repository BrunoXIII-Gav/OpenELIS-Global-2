package org.openelisglobal.sampleitem.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.sample.service.SampleCugService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.form.SaveSampleManagementChangesForm;
import org.openelisglobal.sampleitem.valueholder.SampleItem;

@RunWith(MockitoJUnitRunner.class)
public class SampleManagementServiceImplTest {

    @InjectMocks
    private SampleManagementServiceImpl service;

    @Mock
    private SampleItemDAO sampleItemDAO;

    @Mock
    private SampleHumanService sampleHumanService;

    @Mock
    private SampleCugService sampleCugService;

    private Method applyCugUpdateMethod;

    @Before
    public void setUp() throws Exception {
        applyCugUpdateMethod = SampleManagementServiceImpl.class.getDeclaredMethod("applyCugUpdate", SampleItem.class,
                SaveSampleManagementChangesForm.SampleUpdate.class);
        applyCugUpdateMethod.setAccessible(true);
    }

    @Test
    public void applyCugUpdate_trimsAndPersistsValidValue() throws Exception {
        SampleItem sampleItem = new SampleItem();
        sampleItem.setCugCode("12.3");

        SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setCugCode(" 12.6 ");

        when(sampleItemDAO.existsByCugCode("12.6")).thenReturn(false);

        invokeApplyCugUpdate(sampleItem, update);

        assertEquals("12.6", sampleItem.getCugCode());
        verify(sampleItemDAO).existsByCugCode("12.6");
    }

    @Test
    public void applyCugUpdate_rejectsInvalidFormat() {
        SampleItem sampleItem = new SampleItem();
        sampleItem.setCugCode("12.3");

        SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setCugCode("invalid");

        assertThrows(IllegalArgumentException.class, () -> invokeApplyCugUpdate(sampleItem, update));
    }

    @Test
    public void applyCugUpdate_rejectsDuplicateValue() throws Exception {
        SampleItem sampleItem = new SampleItem();
        sampleItem.setCugCode("12.3");
        sampleItem.setSample(mock(Sample.class));

        SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setCugCode("12.6");

        when(sampleItemDAO.existsByCugCode("12.6")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> invokeApplyCugUpdate(sampleItem, update));
        assertEquals("12.3", sampleItem.getCugCode());
    }

    @Test
    public void rejectsPrefixChangeEvenWhenFullCodeIsAvailable() {
        SampleItem sampleItem = new SampleItem();
        sampleItem.setCugCode("5004.2");
        SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setCugCode("8000.3");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> invokeApplyCugUpdate(sampleItem, update));
        assertEquals("sample.cug.prefix.readonly", error.getMessage());
        assertEquals("5004.2", sampleItem.getCugCode());
        verify(sampleItemDAO, never()).existsByCugCode(anyString());
    }

    @Test
    public void rejectsNonCanonicalOrOutOfRangeNumericComponents() {
        for (String value : new String[] { "", "12", "12.0", "0.1", "ABC.1", "12.-1", "12.1.2", "012.1", "12.01",
                "12.1e3", "12.9223372036854775808", "9223372036854775807.1" }) {
            SampleItem sampleItem = new SampleItem();
            sampleItem.setCugCode("12.3");
            SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
            update.setCugCode(value);
            assertThrows(value, IllegalArgumentException.class, () -> invokeApplyCugUpdate(sampleItem, update));
            assertEquals("12.3", sampleItem.getCugCode());
        }
    }

    @Test
    public void changedCugMustMatchTheSavedPatientPrefix() {
        SampleItem sampleItem = sampleForPatient("8000.3", "5004");
        SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setCugCode("8000.4");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> invokeApplyCugUpdate(sampleItem, update));
        assertEquals("sample.cug.patient.prefix.mismatch", error.getMessage());
        assertEquals("8000.3", sampleItem.getCugCode());
    }

    @Test
    public void suffixChangeUsesThePatientPrefixWithoutReassigningIt() throws Exception {
        SampleItem sampleItem = sampleForPatient("5004.2", "5004");
        SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setCugCode("5004.3");
        invokeApplyCugUpdate(sampleItem, update);
        assertEquals("5004.3", sampleItem.getCugCode());
        verify(sampleCugService, never()).ensurePatientPrefix(anyString(), anyString());
    }

    @Test
    public void unchangedHistoricalCugDoesNotBlockCollectionEditsOrRenumberIt() throws Exception {
        SampleItem sampleItem = new SampleItem();
        sampleItem.setCugCode("8000.3");
        SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setCugCode("8000.3");
        invokeApplyCugUpdate(sampleItem, update);
        assertEquals("8000.3", sampleItem.getCugCode());
        verify(sampleItemDAO, never()).existsByCugCode(anyString());
        verify(sampleCugService, never()).getPatientPrefix(anyString());
    }

    @Test
    public void sampleWithoutCugCanOnlyUseAnExistingPatientPrefix() throws Exception {
        SampleItem sampleItem = sampleForPatient(null, "5004");
        SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setCugCode("8000.3");
        assertThrows(IllegalArgumentException.class, () -> invokeApplyCugUpdate(sampleItem, update));
        update.setCugCode("5004.3");
        invokeApplyCugUpdate(sampleItem, update);
        assertEquals("5004.3", sampleItem.getCugCode());
    }

    @Test
    public void sampleWithoutCugOrPatientPrefixCannotInventAPrefix() {
        SampleItem sampleItem = new SampleItem();
        SaveSampleManagementChangesForm.SampleUpdate update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setCugCode("8000.3");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> invokeApplyCugUpdate(sampleItem, update));
        assertEquals("sample.cug.patient.required", error.getMessage());
    }

    private SampleItem sampleForPatient(String code, String prefix) {
        SampleItem item = new SampleItem();
        item.setCugCode(code);
        Sample sample = new Sample();
        item.setSample(sample);
        Patient patient = new Patient();
        patient.setId("10");
        when(sampleHumanService.getPatientForSample(sample)).thenReturn(patient);
        when(sampleCugService.getPatientPrefix("10")).thenReturn(prefix);
        return item;
    }

    private void invokeApplyCugUpdate(SampleItem sampleItem, SaveSampleManagementChangesForm.SampleUpdate update)
            throws Exception {
        try {
            applyCugUpdateMethod.invoke(service, sampleItem, update);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }
}
