package org.openelisglobal.common.documentupload;

import jakarta.servlet.http.HttpSession;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class TemporaryDocumentUploadServiceImpl implements TemporaryDocumentUploadService {

    private static final String SESSION_ATTRIBUTE = TemporaryDocumentUploadServiceImpl.class.getName() + ".uploads";
    private static final Duration MAX_AGE = Duration.ofHours(24);

    @Override
    public TemporaryDocumentUploadPayload store(String scope, TemporaryDocumentUploadPayload payload) {
        if (StringUtils.isBlank(scope)) {
            throw new IllegalArgumentException("Upload scope is required");
        }
        if (payload == null || payload.getContent() == null || payload.getContent().length == 0) {
            throw new IllegalArgumentException("Upload payload is required");
        }

        HttpSession session = getCurrentSession();
        pruneExpiredEntries(session);
        Map<String, Map<String, TemporaryDocumentUploadPayload>> uploadsByScope = getUploadsByScope(session);
        Map<String, TemporaryDocumentUploadPayload> scopedUploads = uploadsByScope.computeIfAbsent(scope,
                ignored -> new HashMap<>());

        TemporaryDocumentUploadPayload stored = new TemporaryDocumentUploadPayload();
        stored.setUploadToken(UUID.randomUUID().toString());
        stored.setFileName(payload.getFileName());
        stored.setFileType(payload.getFileType());
        stored.setFileSize(payload.getFileSize());
        stored.setUploadedAt(new Timestamp(System.currentTimeMillis()));
        stored.setContent(payload.getContent());

        scopedUploads.put(stored.getUploadToken(), stored);
        session.setAttribute(SESSION_ATTRIBUTE, uploadsByScope);
        return copy(stored, false);
    }

    @Override
    public Optional<TemporaryDocumentUploadPayload> get(String scope, String uploadToken) {
        if (StringUtils.isBlank(scope) || StringUtils.isBlank(uploadToken)) {
            return Optional.empty();
        }

        HttpSession session = getCurrentSession();
        pruneExpiredEntries(session);
        Map<String, Map<String, TemporaryDocumentUploadPayload>> uploadsByScope = getUploadsByScope(session);
        Map<String, TemporaryDocumentUploadPayload> scopedUploads = uploadsByScope.get(scope);
        if (scopedUploads == null) {
            return Optional.empty();
        }
        TemporaryDocumentUploadPayload payload = scopedUploads.get(uploadToken);
        return payload == null ? Optional.empty() : Optional.of(copy(payload, true));
    }

    private HttpSession getCurrentSession() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest().getSession(true);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, TemporaryDocumentUploadPayload>> getUploadsByScope(HttpSession session) {
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Map<String, TemporaryDocumentUploadPayload>>) map;
        }
        return new HashMap<>();
    }

    private void pruneExpiredEntries(HttpSession session) {
        Map<String, Map<String, TemporaryDocumentUploadPayload>> uploadsByScope = getUploadsByScope(session);
        if (uploadsByScope.isEmpty()) {
            return;
        }

        Instant cutoff = Instant.now().minus(MAX_AGE);
        Iterator<Map.Entry<String, Map<String, TemporaryDocumentUploadPayload>>> scopeIterator = uploadsByScope.entrySet()
                .iterator();
        while (scopeIterator.hasNext()) {
            Map.Entry<String, Map<String, TemporaryDocumentUploadPayload>> scopeEntry = scopeIterator.next();
            Map<String, TemporaryDocumentUploadPayload> scopedUploads = scopeEntry.getValue();
            if (scopedUploads == null) {
                scopeIterator.remove();
                continue;
            }

            scopedUploads.entrySet().removeIf(entry -> {
                TemporaryDocumentUploadPayload payload = entry.getValue();
                Timestamp uploadedAt = payload == null ? null : payload.getUploadedAt();
                return uploadedAt == null || uploadedAt.toInstant().isBefore(cutoff);
            });

            if (scopedUploads.isEmpty()) {
                scopeIterator.remove();
            }
        }

        session.setAttribute(SESSION_ATTRIBUTE, uploadsByScope);
    }

    private TemporaryDocumentUploadPayload copy(TemporaryDocumentUploadPayload source, boolean includeContent) {
        TemporaryDocumentUploadPayload copy = new TemporaryDocumentUploadPayload();
        copy.setUploadToken(source.getUploadToken());
        copy.setFileName(source.getFileName());
        copy.setFileType(source.getFileType());
        copy.setFileSize(source.getFileSize());
        copy.setUploadedAt(source.getUploadedAt());
        if (includeContent) {
            copy.setContent(source.getContent());
        }
        return copy;
    }
}
