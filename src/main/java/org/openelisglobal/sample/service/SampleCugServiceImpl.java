package org.openelisglobal.sample.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
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

    private static final Pattern CUG_SUFFIX_PATTERN = Pattern.compile("^.+\\.(\\d+)$");
    private static final int DEFAULT_RESERVATION_TTL_MINUTES = 15;
    private static final int MAX_RESERVATION_ATTEMPTS = 30;

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
                String reservedValue = consumeReservationToken(reservationToken, cugCode, sampleId, userId);
                sampleTestCollection.item.setCugCode(reservedValue);
                inTransactionCugs.add(reservedValue);
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
            try {
                Integer reservationId = sampleCugReservationDAO.insert(reservation);
                return sampleCugReservationDAO.get(reservationId).orElse(reservation);
            } catch (ConstraintViolationException e) {
                generatedInContext.add(generatedValue);
            }
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
        if (expectedValue != null && !StringUtils.equalsIgnoreCase(reservation.getReservedValue(), expectedValue)) {
            throw new IllegalArgumentException("CUG value does not match the active reservation");
        }

        reservation.setStatus(ReservationStatus.CONSUMED.name());
        reservation.setSampleId(parseNumericId(sampleId));
        reservation.setSysUserId(currentUserId);
        sampleCugReservationDAO.update(reservation);
        return reservation.getReservedValue();
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

    private String generateNextCug(String patientId, List<String> existingCugs) {
        long maxSuffix = 0L;
        if (StringUtils.isNotBlank(patientId)) {
            lockPatientForCugGeneration(patientId);
            maxSuffix = Math.max(maxSuffix, extractMaxSuffix(sampleItemDAO.findCugCodesByPatientId(patientId)));
        }
        maxSuffix = Math.max(maxSuffix, extractMaxSuffix(existingCugs));
        long prefix = sampleItemDAO.getNextCugPrefix();
        return prefix + "." + (maxSuffix + 1);
    }

    private long extractMaxSuffix(List<String> cugCodes) {
        long maxSuffix = 0L;
        if (cugCodes == null || cugCodes.isEmpty()) {
            return maxSuffix;
        }
        for (String cugCode : cugCodes) {
            String value = StringUtils.trimToNull(cugCode);
            if (value == null) {
                continue;
            }
            Matcher matcher = CUG_SUFFIX_PATTERN.matcher(value);
            if (!matcher.matches()) {
                continue;
            }
            try {
                long suffix = Long.parseLong(matcher.group(1));
                if (suffix > maxSuffix) {
                    maxSuffix = suffix;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed historical values when calculating next suffix.
            }
        }
        return maxSuffix;
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
}
