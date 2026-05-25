package org.openelisglobal.sample.dao;

import java.sql.Timestamp;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.sample.valueholder.SampleCugReservation;

public interface SampleCugReservationDAO extends BaseDAO<SampleCugReservation, Integer> {

    Optional<SampleCugReservation> insertIfValueAvailable(SampleCugReservation reservation);

    Optional<SampleCugReservation> findByReservationToken(String reservationToken);

    Optional<SampleCugReservation> findActiveByContextAndUser(String reservationContextId, Integer userId);

    int expireReservations(Timestamp asOf);
}
