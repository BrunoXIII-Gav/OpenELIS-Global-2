package org.openelisglobal.common.documentupload;

import java.util.Optional;

public interface TemporaryDocumentUploadService {

    TemporaryDocumentUploadPayload store(String scope, TemporaryDocumentUploadPayload payload);

    Optional<TemporaryDocumentUploadPayload> get(String scope, String uploadToken);
}
