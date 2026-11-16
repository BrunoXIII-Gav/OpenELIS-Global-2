package org.openelisglobal.sample.service;

import jakarta.servlet.http.HttpServletRequest;
import org.openelisglobal.reports.form.ConsentPreviewForm;
import org.openelisglobal.sample.bean.SampleOrderItem;

public interface OrderDocumentConsentService {
    void validateDownload(String userId, ConsentPreviewForm form);

    OrderConsentDownloadService.Permit claimRequired(HttpServletRequest request, String userId, String patientId,
            String accessionNumber, SampleOrderItem items, Boolean rememberRequester);
}
