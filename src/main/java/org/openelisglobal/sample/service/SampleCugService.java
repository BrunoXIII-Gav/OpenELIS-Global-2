package org.openelisglobal.sample.service;

import java.util.List;
import org.openelisglobal.common.services.SampleAddService.SampleTestCollection;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.bean.SampleCugPreviewResponse;

public interface SampleCugService {
    String getPatientPrefix(String patientId);

    String previewPatientPrefix(String patientId);

    String ensurePatientPrefix(String patientId, String currentUserId);

    String assignPatientPrefix(String patientId, String requestedPrefix, String currentUserId);

    String changePatientPrefix(String patientId, String expectedPrefix, String requestedPrefix, String currentUserId);

    void lockPatientAndCheckPrefix(String patientId, String expectedPrefix);

    SampleCugPreviewResponse previewCugCode(SampleCugPreviewRequest request);

    SampleCugPreviewResponse reserveCugCode(SampleCugPreviewRequest request, String currentUserId);

    void assignMissingCugCodes(List<SampleTestCollection> sampleTestCollections, String patientId, String currentUserId,
            String sampleId);
}
