package org.openelisglobal.reports.service;

import java.util.Map;
import java.util.Set;
import org.openelisglobal.reports.form.ConsentPreviewForm;

public interface ConsentDocumentImageService {
    Map<String, byte[]> resolveImages(ConsentPreviewForm form, String userId, Set<String> fieldKeys);
}
