package org.openelisglobal.sampleitem.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import jakarta.persistence.OptimisticLockException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.sampleitem.dto.SaveSampleManagementChangesResponse;
import org.openelisglobal.sampleitem.form.SaveSampleManagementChangesForm;
import org.openelisglobal.sampleitem.valueholder.SampleDataCompletionStatus;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.typeofsample.valueholder.TypeOfSample;
import org.openelisglobal.unitofmeasure.service.UnitOfMeasureService;
import org.openelisglobal.unitofmeasure.valueholder.UnitOfMeasure;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Reproduces an audited update returning a merged object with a new version.
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class SampleManagementSaveVersionTest {
    @InjectMocks
    private SampleManagementServiceImpl service;
    @Mock
    private SampleItemService sampleItems;
    @Mock
    private org.openelisglobal.sampleitem.dao.SampleItemDAO sampleItemDAO;
    @Mock
    private org.openelisglobal.samplehuman.service.SampleHumanService sampleHumanService;
    @Mock
    private SampleManagementAuthorizationService authorization;
    @Mock
    private ModuleAuthorizationService moduleAuthorization;
    @Mock
    private UnitOfMeasureService units;
    @Mock
    private SampleTypeAdditionalFieldService additionalFields;
    @Mock
    private AnalysisService analyses;
    private Object previousFactory;
    private SampleItem original;
    private SaveSampleManagementChangesForm form;
    private SaveSampleManagementChangesForm.SampleUpdate update;
    private AtomicReference<Timestamp> databaseVersion;
    private AtomicReference<SampleItem> persisted;

    @Before
    public void setup() {
        previousFactory = ReflectionTestUtils.getField(SpringContext.class, "factory");
        AutowireCapableBeanFactory factory = mock(AutowireCapableBeanFactory.class);
        IStatusService statuses = mock(IStatusService.class);
        when(factory.getBean(IStatusService.class)).thenReturn(statuses);
        when(statuses.getStatusID(StatusService.AnalysisStatus.Canceled)).thenReturn("99");
        when(statuses.getStatusID(StatusService.SampleStatus.Canceled)).thenReturn("88");
        ReflectionTestUtils.setField(SpringContext.class, "factory", factory);

        original = new SampleItem();
        original.setId("54");
        original.setSample(new Sample());
        original.setCugCode("13.1");
        original.setCollector("CME - Carla Enciso Manrique");
        original.setCollectorUserId("117");
        original.setDataCompletionState(SampleDataCompletionStatus.PENDING_COMPLETION);
        TypeOfSample type = new TypeOfSample();
        type.setId("2");
        original.setTypeOfSample(type);
        original.setLastupdated(Timestamp.valueOf("2026-10-11 14:30:00"));
        databaseVersion = new AtomicReference<>(original.getLastupdated());
        persisted = new AtomicReference<>();
        UnitOfMeasure unit = new UnitOfMeasure();
        unit.setId("3");
        when(units.getUnitOfMeasureById("3")).thenReturn(unit);
        when(sampleItems.getData("54")).thenReturn(original);
        when(authorization.getAccess("1")).thenReturn(SampleManagementAccess.fullAccess());
        when(moduleAuthorization.getRestrictedFieldGroupKeys("1", "sample-management")).thenReturn(Set.of());
        when(moduleAuthorization.getRestrictedFieldTagKeys("1", "sample-management")).thenReturn(Set.of());
        when(additionalFields.getFieldsForSampleType("2", false, false)).thenReturn(List.of());
        when(sampleItems.update(any(SampleItem.class))).thenAnswer(invocation -> {
            SampleItem incoming = invocation.getArgument(0);
            if (!databaseVersion.get().equals(incoming.getLastupdated())) {
                throw new OptimisticLockException("Sample update used a stale version");
            }
            SampleItem merged = new SampleItem();
            merged.setId(incoming.getId());
            merged.setSample(incoming.getSample());
            merged.setTypeOfSample(incoming.getTypeOfSample());
            merged.setCugCode(incoming.getCugCode());
            merged.setQuantity(incoming.getQuantity());
            merged.setUnitOfMeasure(incoming.getUnitOfMeasure());
            merged.setCollector(incoming.getCollector());
            merged.setCollectorUserId(incoming.getCollectorUserId());
            merged.setCollectionDate(incoming.getCollectionDate());
            merged.setDataCompletionState(incoming.getDataCompletionState());
            merged.setStatusId(incoming.getStatusId());
            merged.setSysUserId(incoming.getSysUserId());
            Timestamp nextVersion = new Timestamp(databaseVersion.get().getTime() + 1000);
            merged.setLastupdated(nextVersion);
            databaseVersion.set(nextVersion);
            persisted.set(merged);
            return merged;
        });
        update = new SaveSampleManagementChangesForm.SampleUpdate();
        update.setSampleItemId("54");
        update.setCugCode("13.1");
        update.setQuantity("4");
        update.setUnitOfMeasureId("3");
        update.setCollector("CME - Carla Enciso Manrique");
        update.setCollectorUserId("117");
        update.setCollectionDate("2026-10-11");
        update.setCollectionTime("14:31");
        form = new SaveSampleManagementChangesForm();
        form.setSampleUpdates(List.of(update));
    }

    @After
    public void restoreSpringFactory() {
        ReflectionTestUtils.setField(SpringContext.class, "factory", previousFactory);
    }

    @Test
    public void completingCollectionUsesTheMergedVersionForTheCompletionUpdate() {
        SaveSampleManagementChangesResponse response = service.saveSampleManagementChanges(form, "1");
        assertEquals(1, response.getUpdatedSamplesCount());
        assertEquals(SampleDataCompletionStatus.COMPLETED, persisted.get().getDataCompletionState());
        assertEquals(Double.valueOf(4), persisted.get().getQuantity());
        assertEquals("117", persisted.get().getCollectorUserId());
        assertEquals(Timestamp.valueOf("2026-10-11 14:31:00"), persisted.get().getCollectionDate());
        verify(sampleItems, times(2)).update(any(SampleItem.class));
    }

    @Test
    public void clearingCollectionDateUsesTheMergedVersionToReturnToPending() {
        original.setDataCompletionState(SampleDataCompletionStatus.COMPLETED);
        update.setCollectionDate("");
        SaveSampleManagementChangesResponse response = service.saveSampleManagementChanges(form, "1");
        assertEquals(1, response.getUpdatedSamplesCount());
        assertEquals(SampleDataCompletionStatus.PENDING_COMPLETION, persisted.get().getDataCompletionState());
        verify(sampleItems, times(2)).update(any(SampleItem.class));
    }

    @Test
    public void removingSampleUsesTheMergedVersionAndStillCancelsItsTests() {
        update.setRemoveSample(true);
        Analysis analysis = new Analysis();
        analysis.setId("9");
        analysis.setStatusId("20");
        when(analyses.getAnalysesBySampleItem(any(SampleItem.class))).thenReturn(List.of(analysis));
        SaveSampleManagementChangesResponse response = service.saveSampleManagementChanges(form, "1");
        assertEquals(1, response.getUpdatedSamplesCount());
        assertEquals(1, response.getCancelledTestsCount());
        assertEquals("88", persisted.get().getStatusId());
        assertEquals("99", analysis.getStatusId());
        verify(analyses).update(analysis);
    }

    @Test
    public void refreshingAfterPrefixChangeRejectsTheStaleSamplePayloadBeforeWriting() {
        doAnswer(call -> {
            original.setCugCode("8000.1");
            return null;
        }).when(sampleItemDAO).lockAndRefreshForCugChange(original);
        assertEquals("sample.cug.prefix.readonly",
                assertThrows(IllegalArgumentException.class, () -> service.saveSampleManagementChanges(form, "1"))
                        .getMessage());
        verify(sampleItems, never()).update(any(SampleItem.class));
    }

    @Test
    public void aGenuineConcurrentUpdateIsStillRejected() {
        databaseVersion.set(new Timestamp(original.getLastupdated().getTime() + 1000));
        assertThrows(OptimisticLockException.class, () -> service.saveSampleManagementChanges(form, "1"));
        assertNull(persisted.get());
        verify(sampleItems).update(original);
    }
}
