package org.openelisglobal.reports.service;

public interface ConsentSignatureService {
    byte[] getProviderSignature(String providerId, String personId);
}
