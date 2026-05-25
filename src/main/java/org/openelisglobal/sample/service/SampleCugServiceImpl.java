package org.openelisglobal.sample.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.services.SampleAddService.SampleTestCollection;
import org.openelisglobal.sample.bean.SampleCugPreviewRequest;
import org.openelisglobal.sample.bean.SampleCugPreviewResponse;
import org.openelisglobal.sample.dao.SampleCugReservationDAO;
import org.openelisglobal.sample.valueholder.SampleCugReservation;
import org.openelisglobal.sample.valueholder.SampleCugReservation.ReservationStatus;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SampleCugServiceImpl implements SampleCugService {

    private static final int DEFAULT_RESERVATION_TTL_MINUTES = 15;
    private static final int MAX_RESERVATION_ATTEMPTS = 30;
    private static final int MAX_MANUAL_INCREMENT = 5;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private SampleItemDAO sampleItemDAO;

    @Autowired
    private SampleCugReservationDAO sampleCugReservationDAO;

    @Override
    public SampleCugPreviewResponse reserveCugCode(SampleCugPreviewRequest request, String currentUserId) {
        String userId = StringUtils.trimToNull(currentUserId);
        if (userId == null) {
            throw new IllegalArgumentException("Current user is required");
        }
        Integer userNumericId = Integer.parseInt(userId);
        expireOutdatedReservations();

        String patientId = request == null ? null : request.getPatientId();
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
        expireOutdatedReservations();

        List<String> inTransactionCugs = new ArrayList<>();
        for (SampleTestCollection sampleTestCollection : sampleTestCollections) {
            if (sampleTestCollection == null || sampleTestCollection.item == null) {
                continue;
            }

            String cugCode = StringUtils.trimToNull(sampleTestCollection.item.getCugCode());
            String reservationToken = StringUtils.trimToNull(sampleTestCollection.cugReservationToken);
            if (reservationToken != null) {
                String resolvedValue = consumeReservationToken(reservationToken, cugCode, sampleId, userId);
                validateCugUniqueness(resolvedValue, inTransactionCugs);
                sampleTestCollection.item.setCugCode(resolvedValue);
                inTransactionCugs.add(resolvedValue);
                continue;
            }

            if (cugCode != null) {
                validateCugUniqueness(cugCode, inTransactionCugs);
                inTransactionCugs.add(cugCode);
                continue;
            }

            String generated = generateNextCug(patientId, inTransactionCugs);
            sampleTestCollection.item.setCugCode(generated);
            inTransactionCugs.add(generated);
        }
    }

    private SampleCugReservation createReservation(String patientId, List<String> existingCugs, String contextId,
            String currentUserId, Integer userNumericId) {
        List<String> generatedInContext = existingCugs == null ? new ArrayList<>() : new ArrayList<>(existingCugs);
        for (int attempt = 0; attempt < MAX_RESERVATION_ATTEMPTS; attempt++) {
            String generatedValue = generateNextCug(patientId, generatedInContext);
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
            advanceCugPrefixSequence(resolvedValue);
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
            throw new IllegalArgumentException("Duplicate CUG value in request: " + cugCode);
        }
        if (sampleItemDAO.existsByCugCode(cugCode)) {
            throw new IllegalArgumentException("Duplicate CUG value already exists: " + cugCode);
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

        long maxAllowedPrefix = reserved.prefix + MAX_MANUAL_INCREMENT;
        long maxAllowedSuffix = reserved.suffix + MAX_MANUAL_INCREMENT;

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

    private void advanceCugPrefixSequence(String cugValue) {
        CugComponents components = parseCugComponents(cugValue);
        sampleItemDAO.ensureCugPrefixAtLeast(components.prefix);
    }

    private String generateNextCug(String patientId, List<String> existingCugs) {
        List<String> historicalCugs = new ArrayList<>();
        List<String> activeReservedCugs = new ArrayList<>();
        if (StringUtils.isNotBlank(patientId)) {
            lockPatientForCugGeneration(patientId);
            historicalCugs = sampleItemDAO.findCugCodesByPatientId(patientId);
            activeReservedCugs = getActiveReservedCugsForPatient(patientId);
        }

        Long prefix = selectCanonicalPrefix(historicalCugs);
        if (prefix == null) {
            prefix = selectCanonicalPrefix(activeReservedCugs);
        }
        if (prefix == null) {
            prefix = selectCanonicalPrefix(existingCugs);
        }
        if (prefix == null) {
            prefix = sampleItemDAO.getNextCugPrefix();
        }

        long maxSuffix = 0L;
        maxSuffix = Math.max(maxSuffix, extractMaxSuffixForPrefix(historicalCugs, prefix));
        maxSuffix = Math.max(maxSuffix, extractMaxSuffixForPrefix(activeReservedCugs, prefix));
        maxSuffix = Math.max(maxSuffix, extractMaxSuffixForPrefix(existingCugs, prefix));
        return prefix + "." + (maxSuffix + 1);
    }

    @SuppressWarnings("unchecked")
    private List<String> getActiveReservedCugsForPatient(String patientId) {
        String normalizedPatientId = StringUtils.trimToNull(patientId);
        if (normalizedPatientId == null) {
            return List.of();
        }

        List<Object> rows = entityManager.createNativeQuery(
                "SELECT reserved_value FROM sample_cug_reservation "
                        + "WHERE patient_id = :patientId "
                        + "AND status = :status "
                        + "AND expires_at > now()")
                .setParameter("patientId", Integer.parseInt(normalizedPatientId))
                .setParameter("status", ReservationStatus.RESERVED.name())
                .getResultList();

        List<String> values = new ArrayList<>();
        for (Object row : rows) {
            String value = StringUtils.trimToNull(row == null ? null : String.valueOf(row));
            if (value != null) {
                values.add(value);
            }
        }
        return values;
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

        return frequencyByPrefix.entrySet().stream()
                .sorted(Comparator.<Map.Entry<Long, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparingLong(Map.Entry::getKey))
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

    private void lockPatientForCugGeneration(String patientId) {
        if (StringUtils.isBlank(patientId)) {
            return;
        }
        entityManager.createNativeQuery("SELECT id FROM patient WHERE id = :patientId FOR UPDATE")
                .setParameter("patientId", Integer.parseInt(patientId.trim())).getResultList();
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
