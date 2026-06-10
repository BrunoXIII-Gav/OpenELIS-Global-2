package org.openelisglobal.sampleitem.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.form.SaveSampleManagementChangesForm;
import org.openelisglobal.sampleitem.valueholder.SampleItem;

@RunWith(MockitoJUnitRunner.class)
public class SampleManagementServiceImplTest {

    @InjectMocks
    private SampleManagementServiceImpl service;

    @Mock
    private SampleItemDAO sampleItemDAO;

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
        update.setCugCode(" 45.6 ");

        when(sampleItemDAO.existsByCugCode("45.6")).thenReturn(false);

        invokeApplyCugUpdate(sampleItem, update);

        assertEquals("45.6", sampleItem.getCugCode());
        verify(sampleItemDAO).existsByCugCode("45.6");
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
        update.setCugCode("45.6");

        when(sampleItemDAO.existsByCugCode("45.6")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> invokeApplyCugUpdate(sampleItem, update));
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
