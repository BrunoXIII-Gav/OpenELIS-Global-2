package org.openelisglobal.sample.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DefaultConfigurationProperties;
import org.openelisglobal.sample.exception.ConsentDownloadRequiredException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Session-scoped receipts: one delivered PDF permits one successful order save.
 */
@Service
public class OrderConsentDownloadServiceImpl implements OrderConsentDownloadService {
    private static final String SESSION_KEY = OrderConsentDownloadServiceImpl.class.getName();
    private static final long TTL = Duration.ofHours(2).toMillis();
    private static final int MAX_RECEIPTS = 32;
    private static final Permit DOWNLOAD_NOT_REQUIRED = new Permit() {
        @Override
        public void complete() {
        }

        @Override
        public void close() {
        }
    };

    @Autowired
    private DefaultConfigurationProperties configurationProperties;

    protected long now() {
        return System.currentTimeMillis();
    }

    @Override
    public String recordDownload(HttpServletRequest request, String userId, String patientId, String accessionNumber) {
        String target = target(patientId, accessionNumber);
        if (StringUtils.isBlank(userId))
            throw new ConsentDownloadRequiredException();
        HttpSession session = request.getSession();
        synchronized (session) {
            Map<String, Receipt> receipts = receipts(session);
            if (receipts.size() >= MAX_RECEIPTS) {
                String oldestAvailable = receipts.entrySet().stream().filter(entry -> entry.getValue().lease() == null)
                        .map(Map.Entry::getKey).findFirst().orElseThrow(ConsentDownloadRequiredException::new);
                receipts.remove(oldestAvailable);
            }
            String token = UUID.randomUUID().toString();
            receipts.put(token, new Receipt(userId, target, now() + TTL, null));
            session.setAttribute(SESSION_KEY, receipts);
            return token;
        }
    }

    @Override
    public void requireDownload(HttpServletRequest request, String userId, String token, String patientId,
            String accessionNumber) {
        if (!configurationProperties.isPropertyValueEqual(Property.ORDER_CONSENT_DOWNLOAD_ENABLED, "true"))
            return;
        HttpSession session = request.getSession(false);
        if (session == null)
            throw new ConsentDownloadRequiredException();
        synchronized (session) {
            matchingReceipt(receipts(session), userId, token, patientId, accessionNumber);
        }
    }

    @Override
    public Permit claim(HttpServletRequest request, String userId, String token, String patientId,
            String accessionNumber) {
        if (!configurationProperties.isPropertyValueEqual(Property.ORDER_CONSENT_DOWNLOAD_ENABLED, "true"))
            return DOWNLOAD_NOT_REQUIRED;
        HttpSession session = request.getSession(false);
        if (session == null)
            throw new ConsentDownloadRequiredException();
        synchronized (session) {
            Map<String, Receipt> receipts = receipts(session);
            Receipt receipt = matchingReceipt(receipts, userId, token, patientId, accessionNumber);
            String lease = UUID.randomUUID().toString();
            receipts.put(token, new Receipt(receipt.userId(), receipt.target(), receipt.expiresAt(), lease));
            session.setAttribute(SESSION_KEY, receipts);
            return new Permit() {
                @Override
                public void complete() {
                    finish(session, token, lease, true);
                }

                @Override
                public void close() {
                    finish(session, token, lease, false);
                }
            };
        }
    }

    private Receipt matchingReceipt(Map<String, Receipt> receipts, String userId, String token, String patientId,
            String accessionNumber) {
        Receipt receipt = receipts.get(token);
        if (receipt == null || !receipt.userId().equals(userId)
                || !receipt.target().equals(target(patientId, accessionNumber)) || receipt.lease() != null)
            throw new ConsentDownloadRequiredException();
        return receipt;
    }

    private void finish(HttpSession session, String token, String lease, boolean complete) {
        synchronized (session) {
            try {
                Map<String, Receipt> receipts = receipts(session);
                Receipt receipt = receipts.get(token);
                if (receipt == null || !lease.equals(receipt.lease()))
                    return;
                if (complete)
                    receipts.remove(token);
                else
                    receipts.put(token, new Receipt(receipt.userId(), receipt.target(), receipt.expiresAt(), null));
                session.setAttribute(SESSION_KEY, receipts);
            } catch (IllegalStateException expiredSession) {
                // Logout/session expiry already invalidated all receipts.
            }
        }
    }

    private String target(String patientId, String accessionNumber) {
        if (StringUtils.isNotBlank(accessionNumber))
            return "update:" + accessionNumber.trim();
        if (!StringUtils.isNumeric(patientId))
            throw new ConsentDownloadRequiredException();
        return "create:" + patientId;
    }

    private Map<String, Receipt> receipts(HttpSession session) {
        Map<String, Receipt> result = new LinkedHashMap<>();
        if (session.getAttribute(SESSION_KEY) instanceof Map<?, ?> stored)
            stored.forEach((key, value) -> {
                if (key instanceof String token && value instanceof Receipt receipt && receipt.expiresAt() > now())
                    result.put(token, receipt);
            });
        return result;
    }

    private record Receipt(String userId, String target, long expiresAt, String lease) implements Serializable {
    }
}
