package org.openelisglobal.resultvalidation.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.note.service.NoteService;
import org.openelisglobal.notification.service.TestNotificationService;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.resultvalidation.bean.AnalysisItem;
import org.openelisglobal.sample.service.SampleService;

@RunWith(MockitoJUnitRunner.class)
public class ResultValidationServiceImplTest {

    @Mock
    private AnalysisService analysisService;

    @Mock
    private ResultService resultService;

    @Mock
    private NoteService noteService;

    @Mock
    private SampleService sampleService;

    @Mock
    private TestNotificationService testNotificationService;

    @InjectMocks
    private ResultValidationServiceImpl resultValidationService;

    @Test
    public void persistdata_shouldApplyValidationDateToReleasedDate() {
        Analysis analysis = mock(Analysis.class);
        when(analysis.getId()).thenReturn("123");

        AnalysisItem analysisItem = new AnalysisItem();
        analysisItem.setAnalysisId("123");
        analysisItem.setAccessionNumber("ACC-1");
        analysisItem.setValidationDate("2026-05-28");

        resultValidationService.applyValidationDates(List.of(analysis), List.of(analysisItem));

        verify(analysis).setValidationDate(Date.valueOf("2026-05-28"));
    }
}
