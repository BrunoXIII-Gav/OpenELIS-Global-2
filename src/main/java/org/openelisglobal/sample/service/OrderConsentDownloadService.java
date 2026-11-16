package org.openelisglobal.sample.service;

import jakarta.servlet.http.HttpServletRequest;

public interface OrderConsentDownloadService {
    String recordDownload(HttpServletRequest request, String userId, String patientId, String accessionNumber);

    void requireDownload(HttpServletRequest request, String userId, String token, String patientId,
            String accessionNumber);

    Permit claim(HttpServletRequest request, String userId, String token, String patientId, String accessionNumber);

    String recordFieldDownload(HttpServletRequest request, String userId, String patientId, String accessionNumber,
            String fieldKey, String templateId);

    Permit claimFieldDownload(HttpServletRequest request, String userId, String token, String patientId,
            String accessionNumber, String fieldKey, String templateId);

    interface Permit extends AutoCloseable {
        void complete();

        @Override
        void close();
    }
}
