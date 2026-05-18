package org.openelisglobal.sample.service;

import java.util.List;
import org.openelisglobal.common.services.SampleAddService.SampleTestCollection;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.bean.SampleCugPreviewResponse;

public interface SampleCugService {
    SampleCugPreviewResponse reserveCugCode(SampleCugPreviewRequest request, String currentUserId);

    void assignMissingCugCodes(List<SampleTestCollection> sampleTestCollections, String patientId, String currentUserId,
            String sampleId);
}
