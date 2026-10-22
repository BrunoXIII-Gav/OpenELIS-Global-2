package org.openelisglobal.sample.service;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.openelisglobal.common.services.SampleAddService.SampleTestCollection;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.bean.SampleCugPreviewResponse;
import org.openelisglobal.sample.dao.SampleCugPrefixDAO;
import org.openelisglobal.sample.dao.SampleCugReservationDAO;
import org.openelisglobal.sample.exception.PatientCugPrefixException;
import org.openelisglobal.sample.exception.SampleCugConflictException;
import org.openelisglobal.sample.valueholder.SampleCugReservation;
import org.openelisglobal.sample.valueholder.SampleCugReservation.ReservationStatus;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SampleCugServiceImpl implements SampleCugService {

    private static final int DEFAULT_RESERVATION_TTL_MINUTES = 15;
    private static final int MAX_RESERVATION_ATTEMPTS = 30;
    private static final int MAX_MANUAL_INCREMENT = 5;

    @Autowired
    private CugPrefixService prefixes;

    @Autowired
    private SampleItemDAO sampleItemDAO;

    @Autowired
    private SampleCugReservationDAO sampleCugReservationDAO;

    @Autowired
    private SampleCugPrefixDAO patientPrefixes;

    @Autowired
    private AuditEventDAO auditEvents;

    @Override
    @Transactional(readOnly = true)
    public String getPatientPrefix(String patientId) {
        Long prefix = patientPrefixes.findPatientPrefix(patientId);
        if (prefix == null && StringUtils.isNotBlank(patientId)) {
            prefix = selectCanonicalPrefix(sampleItemDAO.findCugCodesByPatientId(patientId));
        }
        return prefix == null ? null : prefix.toString();
    }

    @Override
    @Transactional(readOnly = true)
    public String previewPatientPrefix(String patientId) {
        String prefix = getPatientPrefix(patientId);
        return prefix == null ? prefixes.getSettings().getNextAvailablePrefix() : prefix;
    }

    @Override
    public String ensurePatientPrefix(String patientId, String currentUserId) {
        if (StringUtils.isBlank(patientId) || StringUtils.isBlank(currentUserId)) {
            throw new IllegalArgumentException("A saved patient and current user are required");
        }
        sampleCugReservationDAO.lockPatientForCug(patientId);
        Long prefix = patientPrefixes.findPatientPrefix(patientId);
        if (prefix != null)
            return prefix.toString();
        prefix = selectCanonicalPrefix(sampleItemDAO.findCugCodesByPatientId(patientId));
        if (prefix == null) {
            prefix = prefixes.allocate(currentUserId);
        } else if (!prefixes.isAssigned(prefix)) {
            prefixes.register(prefix, currentUserId);
        }
        patientPrefixes.bindPatientPrefix(patientId, prefix, currentUserId);
        return prefix.toString();
    }

    @Override
    public String assignPatientPrefix(String patientId, String requestedPrefix, String currentUserId) {
        if (StringUtils.isBlank(patientId) || StringUtils.isBlank(currentUserId)) {
            throw new IllegalArgumentException("A saved patient and current user are required");
        }
        sampleCugReservationDAO.lockPatientForCug(patientId);
        String assigned = getPatientPrefix(patientId);
        if (assigned != null) {
            if (!assigned.equals(requestedPrefix)) {
                throw new PatientCugPrefixException(PatientCugPrefixException.READONLY);
            }
            return ensurePatientPrefix(patientId, currentUserId);
        }
        long prefix = prefixes.claim(requestedPrefix, currentUserId);
        patientPrefixes.bindPatientPrefix(patientId, prefix, currentUserId);
        return Long.toString(prefix);
    }

    @Override
    public String changePatientPrefix(String patientId, String expectedPrefix, String requestedPrefix,
            String currentUserId) {
        if (StringUtils.isBlank(patientId) || StringUtils.isBlank(currentUserId)) {
            throw new IllegalArgumentException("A saved patient and current user are required");
        }
        sampleCugReservationDAO.lockPatientForCug(patientId);
        String previous = getPatientPrefix(patientId);
        if (previous == null || !previous.equals(expectedPrefix)) {
            throw new PatientCugPrefixException(PatientCugPrefixException.STALE);
        }
        if (previous.equals(requestedPrefix)) {
            return previous;
        }
        if (StringUtils.isBlank(requestedPrefix)) {
            throw new PatientCugPrefixException(PatientCugPrefixException.INVALID);
        }
        List<SampleItem> items = sampleItemDAO.lockPatientItemsForCugChange(patientId);
        Map<SampleItem, String> replacements = new java.util.LinkedHashMap<>();
        var codes = new java.util.HashSet<String>();
        for (SampleItem item : items) {
            if (StringUtils.isBlank(item.getCugCode()))
                continue;
            CugComponents parsed = tryParseCugComponents(item.getCugCode());
            if (parsed == null || parsed.suffix < 1 || !previous.equals(Long.toString(parsed.prefix))
                    || !item.getCugCode().equals(parsed.prefix + "." + parsed.suffix)) {
                throw new PatientCugPrefixException(PatientCugPrefixException.INCONSISTENT);
            }
            String next = requestedPrefix + "." + parsed.suffix;
            if (!codes.add(next) || sampleItemDAO.existsByCugCode(next)) {
                throw new PatientCugPrefixException(PatientCugPrefixException.OCCUPIED);
            }
            replacements.put(item, next);
        }
        long nextPrefix = prefixes.claim(requestedPrefix, currentUserId);
        String operation = UUID.randomUUID().toString();
        for (var entry : replacements.entrySet()) {
            SampleItem item = entry.getKey();
            String old = item.getCugCode();
            item.setCugCode(entry.getValue());
            item.setSysUserId(currentUserId);
            sampleItemDAO.update(item);
            auditPrefixChange(operation, patientId, "SampleItem", item.getId(), "cugCode", "sample.cug.label", old,
                    entry.getValue(), currentUserId, item);
        }
        patientPrefixes.changePatientPrefix(patientId, nextPrefix, currentUserId);
        for (SampleCugReservation reservation : sampleCugReservationDAO.lockReservedForPatient(patientId)) {
            reservation.setStatus(ReservationStatus.CANCELED.name());
            reservation.setSysUserId(currentUserId);
            sampleCugReservationDAO.update(reservation);
        }
        auditPrefixChange(operation, patientId, "PatientCugPrefix", patientId, "prefix", "patient.cug.prefix.label",
                previous, requestedPrefix, currentUserId, null);
        prefixes.releaseIfUnused(Long.parseLong(previous));
        return requestedPrefix;
    }

    private void auditPrefixChange(String operation, String patientId, String entity, String entityId, String field,
            String label, String old, String next, String actor, SampleItem item) {
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID().toString());
        event.setOperationId(operation);
        event.setModule(item == null ? "patient" : "sampleitem");
        event.setEntityName(entity);
        event.setEntityId(entityId);
        event.setPatientId(patientId);
        if (item != null) {
            event.setSampleItemId(item.getId());
            event.setSampleId(item.getSample().getId());
        }
        event.setAction("U");
        event.setField(field);
        event.setFieldLabel(label);
        event.setOldValue(old);
        event.setNewValue(next);
        event.setActorId(actor);
        event.setActorSource("USER");
        event.setOccurredAt(new Timestamp(System.currentTimeMillis()));
        event.setSource("/rest/PatientManagement");
        auditEvents.insert(event);
    }

    @Override
    public void lockPatientAndCheckPrefix(String patientId, String expectedPrefix) {
        sampleCugReservationDAO.lockPatientForCug(patientId);
        if (StringUtils.isNotBlank(expectedPrefix) && !expectedPrefix.equals(getPatientPrefix(patientId))) {
            throw new IllegalArgumentException("sample.cug.patient.prefix.mismatch");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SampleCugPreviewResponse previewCugCode(SampleCugPreviewRequest request) {
        if (request == null || StringUtils.isBlank(request.getPatientId())) {
            throw new IllegalArgumentException("A saved patient is required");
        }
        String patientId = request.getPatientId();
        Long prefix = patientPrefixes.findPatientPrefix(patientId);
        List<String> history = sampleItemDAO.findCugCodesByPatientId(patientId);
        if (prefix == null)
            prefix = selectCanonicalPrefix(history);
        if (prefix == null) {
            String candidate = prefixes.getSettings().getNextAvailablePrefix();
            if (candidate == null)
                throw new IllegalArgumentException("cug.prefix.error.exhausted");
            prefix = Long.valueOf(candidate);
        }
        if (request.getSampleOffset() < 0 || request.getSampleOffset() > 1000) {
            throw new IllegalArgumentException("Invalid sample offset");
        }
        String next = nextCode(prefix, history, getActiveReservedCugsForPatient(patientId), List.of());
        long suffix = parseCugComponents(next).suffix;
        if (Long.MAX_VALUE - suffix < request.getSampleOffset()) {
            throw new IllegalArgumentException("sample.cug.suffix.exhausted");
        }
        return new SampleCugPreviewResponse(prefix + "." + (suffix + request.getSampleOffset()));
    }

    private String nextCode(long prefix, List<String> history, List<String> reserved, List<String> existing) {
        long suffix = Math.max(extractMaxSuffixForPrefix(history, prefix),
                Math.max(extractMaxSuffixForPrefix(reserved, prefix), extractMaxSuffixForPrefix(existing, prefix)));
        if (suffix == Long.MAX_VALUE)
            throw new IllegalArgumentException("sample.cug.suffix.exhausted");
        return prefix + "." + (suffix + 1);
    }

    @Override
    public SampleCugPreviewResponse reserveCugCode(SampleCugPreviewRequest request, String currentUserId) {
        String userId = StringUtils.trimToNull(currentUserId);
        if (userId == null) {
            throw new IllegalArgumentException("Current user is required");
        }
        Integer userNumericId = Integer.parseInt(userId);
        String patientId = request == null ? null : request.getPatientId();
        // Only requests for the same patient share a suffix lock. The global
        // counter is locked by allocate() only when a new prefix is needed.
        sampleCugReservationDAO.lockPatientForCug(patientId);
        expireOutdatedReservations();
        List<String> existingCugs = request == null ? List.of()
                : (request.getExistingCugs() == null ? List.of() : request.getExistingCugs());
        String reservationToken = StringUtils.trimToNull(request == null ? null : request.getReservationToken());
        String reservationContextId = StringUtils
                .trimToNull(request == null ? null : request.getReservationContextId());

        if (reservationToken != null) {
            Optional<SampleCugReservation> existingTokenReservation = sampleCugReservationDAO
                    .findByReservationToken(reservationToken);
            if (existingTokenReservation.isPresent()) {
                SampleCugReservation reservation = existingTokenReservation.get();
                ensureReservationOwner(reservation, userId);

                if (ReservationStatus.RESERVED.name().equals(reservation.getStatus()) && !isExpired(reservation)) {
                    return mapReservationResponse(reservation);
                }
                if (ReservationStatus.RESERVED.name().equals(reservation.getStatus()) && isExpired(reservation)) {
                    reservation.setStatus(ReservationStatus.EXPIRED.name());
                    reservation.setSysUserId(userId);
                    sampleCugReservationDAO.update(reservation);
                }
            }
        }

        if (reservationContextId != null) {
            Optional<SampleCugReservation> activeContextReservation = sampleCugReservationDAO
                    .findActiveByContextAndUser(reservationContextId, userNumericId);
            if (activeContextReservation.isPresent() && !isExpired(activeContextReservation.get())) {
                return mapReservationResponse(activeContextReservation.get());
            }
        }

        SampleCugReservation reservation = createReservation(patientId, existingCugs, reservationContextId, userId,
                userNumericId);
        return mapReservationResponse(reservation);
    }

    @Override
    public void assignMissingCugCodes(List<SampleTestCollection> sampleTestCollections, String patientId,
            String currentUserId, String sampleId) {
        if (sampleTestCollections == null || sampleTestCollections.isEmpty()) {
            return;
        }

        String userId = StringUtils.trimToNull(currentUserId);
        if (userId == null) {
            throw new IllegalArgumentException("Current user is required");
        }
        sampleCugReservationDAO.lockPatientForCug(patientId);
        expireOutdatedReservations();

        List<String> inTransactionCugs = new ArrayList<>();
        for (SampleTestCollection sampleTestCollection : sampleTestCollections) {
            if (sampleTestCollection == null || sampleTestCollection.item == null) {
                continue;
            }

            String cugCode = StringUtils.trimToNull(sampleTestCollection.item.getCugCode());
            if (cugCode != null && StringUtils.isNotBlank(patientId)) {
                Long assigned = patientPrefixes.findPatientPrefix(patientId);
                if (assigned != null && assigned != parseCugComponents(cugCode).prefix) {
                    throw new IllegalArgumentException("sample.cug.patient.prefix.mismatch");
                }
            }
            String reservationToken = StringUtils.trimToNull(sampleTestCollection.cugReservationToken);
            if (sampleTestCollection.cugProvisional) {
                validateDisplayedCug(cugCode, patientId, userId, inTransactionCugs);
                inTransactionCugs.add(cugCode);
                continue;
            }
            if (reservationToken != null) {
                String resolvedValue = consumeReservationToken(reservationToken, cugCode, sampleId, userId);
                validateCugUniqueness(resolvedValue, inTransactionCugs);
                sampleTestCollection.item.setCugCode(resolvedValue);
                inTransactionCugs.add(resolvedValue);
                continue;
            }

            if (cugCode != null) {
                prefixes.lockAllocation();
                long prefix = parseCugComponents(cugCode).prefix;
                List<String> patientCugs = StringUtils.isBlank(patientId) ? List.of()
                        : sampleItemDAO.findCugCodesByPatientId(patientId);
                if (prefixes.isAssigned(prefix)
                        && !Long.valueOf(prefix).equals(patientPrefixes.findPatientPrefix(patientId))
                        && extractMaxSuffixForPrefix(patientCugs, prefix) == 0
                        && extractMaxSuffixForPrefix(inTransactionCugs, prefix) == 0) {
                    throw new IllegalArgumentException("sample.cug.prefix.alreadyAssigned");
                }
                validateCugUniqueness(cugCode, inTransactionCugs);
                prefixes.register(prefix, userId);
                inTransactionCugs.add(cugCode);
                continue;
            }

            if (StringUtils.isNotBlank(patientId))
                ensurePatientPrefix(patientId, userId);
            String generated = generateNextCug(patientId, inTransactionCugs, userId);
            validateCugUniqueness(generated, inTransactionCugs);
            sampleTestCollection.item.setCugCode(generated);
            inTransactionCugs.add(generated);
        }
    }

    private void validateDisplayedCug(String cugCode, String patientId, String userId, List<String> inTransactionCugs) {
        if (cugCode == null)
            throw new IllegalArgumentException("sample.cug.required");
        if (StringUtils.isBlank(patientId))
            throw new IllegalArgumentException("sample.cug.patient.required");
        CugComponents parts = parseCugComponents(cugCode);
        if (parts.prefix < 1 || parts.suffix < 1 || !cugCode.equals(parts.prefix + "." + parts.suffix)) {
            throw new IllegalArgumentException("sample.cug.invalid.format");
        }
        String prefix = ensurePatientPrefix(patientId, userId);
        if (!prefix.equals(Long.toString(parts.prefix))) {
            throw new IllegalArgumentException("sample.cug.patient.prefix.mismatch");
        }
        validateCugUniqueness(cugCode, inTransactionCugs);
    }

    private SampleCugReservation createReservation(String patientId, List<String> existingCugs, String contextId,
            String currentUserId, Integer userNumericId) {
        List<String> generatedInContext = existingCugs == null ? new ArrayList<>() : new ArrayList<>(existingCugs);
        for (int attempt = 0; attempt < MAX_RESERVATION_ATTEMPTS; attempt++) {
            String generatedValue = generateNextCug(patientId, generatedInContext, currentUserId);
            if (sampleItemDAO.existsByCugCode(generatedValue)) {
                generatedInContext.add(generatedValue);
                continue;
            }

            SampleCugReservation reservation = new SampleCugReservation();
            reservation.setReservedValue(generatedValue);
            reservation.setReservationToken(UUID.randomUUID().toString());
            reservation.setReservationContextId(contextId);
            reservation.setReservedByUserId(userNumericId);
            reservation.setPatientId(parseNumericId(patientId));
            reservation.setStatus(ReservationStatus.RESERVED.name());
            reservation.setExpiresAt(calculateExpiry(DEFAULT_RESERVATION_TTL_MINUTES));
            reservation.setSysUserId(currentUserId);
            Optional<SampleCugReservation> insertedReservation = sampleCugReservationDAO
                    .insertIfValueAvailable(reservation);
            if (insertedReservation.isPresent()) {
                return insertedReservation.get();
            }

            Optional<SampleCugReservation> activeContextReservation = sampleCugReservationDAO
                    .findActiveByContextAndUser(contextId, userNumericId);
            if (activeContextReservation.isPresent() && !isExpired(activeContextReservation.get())) {
                return activeContextReservation.get();
            }
            generatedInContext.add(generatedValue);
        }

        throw new IllegalArgumentException(
                "Unable to reserve CUG value after " + MAX_RESERVATION_ATTEMPTS + " attempts");
    }

    private String consumeReservationToken(String reservationToken, String expectedValue, String sampleId,
            String currentUserId) {
        SampleCugReservation reservation = sampleCugReservationDAO.findByReservationToken(reservationToken)
                .orElseThrow(() -> new IllegalArgumentException("CUG reservation token not found"));
        ensureReservationOwner(reservation, currentUserId);

        if (!ReservationStatus.RESERVED.name().equals(reservation.getStatus())) {
            throw new IllegalArgumentException("CUG reservation is no longer active");
        }
        if (isExpired(reservation)) {
            reservation.setStatus(ReservationStatus.EXPIRED.name());
            reservation.setSysUserId(currentUserId);
            sampleCugReservationDAO.update(reservation);
            throw new IllegalArgumentException("CUG reservation has expired");
        }
        String resolvedValue = reservation.getReservedValue();
        if (expectedValue != null && !StringUtils.equalsIgnoreCase(resolvedValue, expectedValue)) {
            resolvedValue = validateManualCugOverride(resolvedValue, expectedValue);
            long prefix = parseCugComponents(resolvedValue).prefix;
            long reservedPrefix = parseCugComponents(reservation.getReservedValue()).prefix;
            prefixes.lockAllocation();
            if (prefix != reservedPrefix && prefixes.isAssigned(prefix)) {
                throw new IllegalArgumentException("sample.cug.prefix.alreadyAssigned");
            }
            prefixes.register(prefix, currentUserId);
        }

        reservation.setStatus(ReservationStatus.CONSUMED.name());
        reservation.setSampleId(parseNumericId(sampleId));
        reservation.setSysUserId(currentUserId);
        sampleCugReservationDAO.update(reservation);
        return resolvedValue;
    }

    private SampleCugPreviewResponse mapReservationResponse(SampleCugReservation reservation) {
        return new SampleCugPreviewResponse(reservation.getReservedValue(), reservation.getReservationToken(),
                reservation.getExpiresAt());
    }

    private void ensureReservationOwner(SampleCugReservation reservation, String currentUserId) {
        if (reservation == null || reservation.getReservedByUserId() == null || !StringUtils
                .equals(String.valueOf(reservation.getReservedByUserId()), StringUtils.trimToNull(currentUserId))) {
            throw new IllegalArgumentException("CUG reservation token does not belong to current user");
        }
    }

    private boolean isExpired(SampleCugReservation reservation) {
        return reservation == null || reservation.getExpiresAt() == null
                || reservation.getExpiresAt().getTime() <= System.currentTimeMillis();
    }

    private Timestamp calculateExpiry(int ttlMinutes) {
        return new Timestamp(System.currentTimeMillis() + (long) ttlMinutes * 60L * 1000L);
    }

    private Integer parseNumericId(String value) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return Integer.parseInt(trimmed);
    }

    private void validateCugUniqueness(String cugCode, List<String> inTransactionCugs) {
        if (containsIgnoreCase(inTransactionCugs, cugCode)) {
            throw new SampleCugConflictException();
        }
        if (sampleItemDAO.existsByCugCode(cugCode)) {
            throw new SampleCugConflictException();
        }
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        if (values == null || target == null) {
            return false;
        }
        return values.stream().anyMatch(value -> StringUtils.equalsIgnoreCase(value, target));
    }

    private String validateManualCugOverride(String reservedValue, String manualValue) {
        CugComponents reserved = parseCugComponents(reservedValue);
        CugComponents manual = parseCugComponents(manualValue);

        long maxAllowedPrefix = reserved.prefix > Long.MAX_VALUE - MAX_MANUAL_INCREMENT ? Long.MAX_VALUE
                : reserved.prefix + MAX_MANUAL_INCREMENT;
        long maxAllowedSuffix = reserved.suffix > Long.MAX_VALUE - MAX_MANUAL_INCREMENT ? Long.MAX_VALUE
                : reserved.suffix + MAX_MANUAL_INCREMENT;

        if (manual.prefix < reserved.prefix || manual.prefix > maxAllowedPrefix) {
            throw new IllegalArgumentException(
                    "Manual CUG prefix must be between " + reserved.prefix + " and " + maxAllowedPrefix);
        }
        if (manual.suffix < reserved.suffix || manual.suffix > maxAllowedSuffix) {
            throw new IllegalArgumentException(
                    "Manual CUG suffix must be between " + reserved.suffix + " and " + maxAllowedSuffix);
        }

        return manual.rawValue;
    }

    private CugComponents parseCugComponents(String cugValue) {
        String normalized = StringUtils.trimToNull(cugValue);
        if (normalized == null) {
            throw new IllegalArgumentException("CUG value is required");
        }

        int splitIndex = normalized.lastIndexOf('.');
        if (splitIndex <= 0 || splitIndex == normalized.length() - 1) {
            throw new IllegalArgumentException("Invalid CUG format. Expected '<prefix>.<suffix>'");
        }

        String prefixRaw = normalized.substring(0, splitIndex).trim();
        String suffixRaw = normalized.substring(splitIndex + 1).trim();
        if (!StringUtils.isNumeric(prefixRaw) || !StringUtils.isNumeric(suffixRaw)) {
            throw new IllegalArgumentException("CUG prefix and suffix must be numeric");
        }

        try {
            return new CugComponents(normalized, Long.parseLong(prefixRaw), Long.parseLong(suffixRaw));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("CUG prefix/suffix exceeds supported range");
        }
    }

    private String generateNextCug(String patientId, List<String> existingCugs, String actorId) {
        List<String> historicalCugs = new ArrayList<>();
        List<String> activeReservedCugs = new ArrayList<>();
        if (StringUtils.isNotBlank(patientId)) {
            historicalCugs = sampleItemDAO.findCugCodesByPatientId(patientId);
            activeReservedCugs = getActiveReservedCugsForPatient(patientId);
        }

        Long prefix = patientPrefixes.findPatientPrefix(patientId);
        if (prefix == null)
            prefix = selectCanonicalPrefix(historicalCugs);
        if (prefix == null) {
            prefix = selectCanonicalPrefix(activeReservedCugs);
        }
        if (prefix == null) {
            prefix = selectCanonicalPrefix(existingCugs);
        }
        if (prefix == null) {
            prefix = prefixes.allocate(actorId);
        }

        long maxSuffix = 0L;
        maxSuffix = Math.max(maxSuffix, extractMaxSuffixForPrefix(historicalCugs, prefix));
        maxSuffix = Math.max(maxSuffix, extractMaxSuffixForPrefix(activeReservedCugs, prefix));
        maxSuffix = Math.max(maxSuffix, extractMaxSuffixForPrefix(existingCugs, prefix));
        if (maxSuffix == Long.MAX_VALUE)
            throw new IllegalArgumentException("sample.cug.suffix.exhausted");
        return prefix + "." + (maxSuffix + 1);
    }

    private List<String> getActiveReservedCugsForPatient(String patientId) {
        return sampleCugReservationDAO.findActiveValuesForPatient(Integer.valueOf(patientId.trim()),
                new Timestamp(System.currentTimeMillis()));
    }

    private long extractMaxSuffixForPrefix(List<String> cugCodes, long prefix) {
        long maxSuffix = 0L;
        if (cugCodes == null || cugCodes.isEmpty()) {
            return maxSuffix;
        }
        for (String cugCode : cugCodes) {
            CugComponents components = tryParseCugComponents(cugCode);
            if (components == null || components.prefix != prefix) {
                continue;
            }
            if (components.suffix > maxSuffix) {
                maxSuffix = components.suffix;
            }
        }
        return maxSuffix;
    }

    private Long selectCanonicalPrefix(List<String> cugCodes) {
        if (cugCodes == null || cugCodes.isEmpty()) {
            return null;
        }

        Map<Long, Long> frequencyByPrefix = new HashMap<>();
        for (String cugCode : cugCodes) {
            CugComponents components = tryParseCugComponents(cugCode);
            if (components == null) {
                continue;
            }
            frequencyByPrefix.merge(components.prefix, 1L, Long::sum);
        }

        return frequencyByPrefix
                .entrySet().stream().sorted(Comparator.<Map.Entry<Long, Long>>comparingLong(Map.Entry::getValue)
                        .reversed().thenComparingLong(Map.Entry::getKey))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    private CugComponents tryParseCugComponents(String cugValue) {
        String value = StringUtils.trimToNull(cugValue);
        if (value == null) {
            return null;
        }
        try {
            return parseCugComponents(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void expireOutdatedReservations() {
        sampleCugReservationDAO.expireReservations(new Timestamp(System.currentTimeMillis()));
    }

    private static final class CugComponents {
        private final String rawValue;
        private final long prefix;
        private final long suffix;

        private CugComponents(String rawValue, long prefix, long suffix) {
            this.rawValue = rawValue;
            this.prefix = prefix;
            this.suffix = suffix;
        }
    }
}
