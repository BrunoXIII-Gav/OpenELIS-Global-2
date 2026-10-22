package org.openelisglobal.sample.dao;

import java.sql.Timestamp;
import java.util.Optional;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.sample.valueholder.SampleCugReservation;

public interface SampleCugReservationDAO extends BaseDAO<SampleCugReservation, Integer> {

    void lockPatientForCug(String patientId);

    Optional<SampleCugReservation> insertIfValueAvailable(SampleCugReservation reservation);

    Optional<SampleCugReservation> findByReservationToken(String reservationToken);

    Optional<SampleCugReservation> findActiveByContextAndUser(String reservationContextId, Integer userId);

    java.util.List<String> findActiveValuesForPatient(Integer patientId, Timestamp asOf);

    int expireReservations(Timestamp asOf);

    java.util.List<SampleCugReservation> lockReservedForPatient(String patientId);
}
