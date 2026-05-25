package org.openelisglobal.sample.daoimpl;

import java.sql.Timestamp;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.hibernate.type.StandardBasicTypes;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.sample.dao.SampleCugReservationDAO;
import org.openelisglobal.sample.valueholder.SampleCugReservation;
import org.openelisglobal.sample.valueholder.SampleCugReservation.ReservationStatus;
import org.springframework.stereotype.Component;

@Component
public class SampleCugReservationDAOImpl extends BaseDAOImpl<SampleCugReservation, Integer>
        implements SampleCugReservationDAO {

    private static final String RESERVATION_ENTITY = SampleCugReservation.class.getName();

    public SampleCugReservationDAOImpl() {
        super(SampleCugReservation.class);
    }

    @Override
    public Optional<SampleCugReservation> insertIfValueAvailable(SampleCugReservation reservation) {
        if (reservation == null) {
            return Optional.empty();
        }

        String sql = "insert into sample_cug_reservation ("
                + "id, reserved_value, reservation_token, reservation_context_id, sample_id, patient_id, "
                + "reserved_by_user_id, status, expires_at, sys_user_id, last_updated"
                + ") values ("
                + "nextval('sample_cug_reservation_seq'), :reservedValue, :reservationToken, "
                + ":reservationContextId, :sampleId, :patientId, :reservedByUserId, :status, :expiresAt, :sysUserId, now()"
                + ") on conflict (reserved_value) do nothing returning id";

        NativeQuery<?> query = entityManager.unwrap(Session.class).createNativeQuery(sql);
        query.setParameter("reservedValue", reservation.getReservedValue());
        query.setParameter("reservationToken", reservation.getReservationToken());
        query.setParameter("reservationContextId", reservation.getReservationContextId());
        query.setParameter("sampleId", reservation.getSampleId(), StandardBasicTypes.INTEGER);
        query.setParameter("patientId", reservation.getPatientId(), StandardBasicTypes.INTEGER);
        query.setParameter("reservedByUserId", reservation.getReservedByUserId(), StandardBasicTypes.INTEGER);
        query.setParameter("status", reservation.getStatus());
        query.setParameter("expiresAt", reservation.getExpiresAt(), StandardBasicTypes.TIMESTAMP);
        query.setParameter("sysUserId",
                reservation.getSysUserId() == null ? null : Integer.valueOf(reservation.getSysUserId()),
                StandardBasicTypes.INTEGER);

        Object result = query.uniqueResult();
        if (!(result instanceof Number)) {
            return Optional.empty();
        }
        Integer reservationId = ((Number) result).intValue();
        return get(reservationId);
    }

    @Override
    public Optional<SampleCugReservation> findByReservationToken(String reservationToken) {
        String token = StringUtils.trimToNull(reservationToken);
        if (token == null) {
            return Optional.empty();
        }

        String hql = "from " + RESERVATION_ENTITY + " r where r.reservationToken = :reservationToken";
        Query<SampleCugReservation> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleCugReservation.class);
        query.setParameter("reservationToken", token);
        return Optional.ofNullable(query.uniqueResult());
    }

    @Override
    public Optional<SampleCugReservation> findActiveByContextAndUser(String reservationContextId, Integer userId) {
        String contextId = StringUtils.trimToNull(reservationContextId);
        if (contextId == null || userId == null) {
            return Optional.empty();
        }

        String hql = "from " + RESERVATION_ENTITY + " r where r.reservationContextId = :reservationContextId "
                + "and r.reservedByUserId = :reservedByUserId and r.status = :status and r.expiresAt > :now "
                + "order by r.id desc";
        Query<SampleCugReservation> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleCugReservation.class);
        query.setParameter("reservationContextId", contextId);
        query.setParameter("reservedByUserId", userId);
        query.setParameter("status", ReservationStatus.RESERVED.name());
        query.setParameter("now", new Timestamp(System.currentTimeMillis()));
        query.setMaxResults(1);
        return Optional.ofNullable(query.uniqueResult());
    }

    @Override
    public int expireReservations(Timestamp asOf) {
        String sql = "update sample_cug_reservation set status = :expiredStatus, last_updated = now() "
                + "where status = :reservedStatus and expires_at <= :asOf";
        Query<?> query = entityManager.unwrap(Session.class).createNativeQuery(sql);
        query.setParameter("expiredStatus", ReservationStatus.EXPIRED.name());
        query.setParameter("reservedStatus", ReservationStatus.RESERVED.name());
        query.setParameter("asOf", asOf);
        return query.executeUpdate();
    }
}
