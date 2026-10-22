package org.openelisglobal.sample.dao;

import java.sql.Timestamp;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.sample.valueholder.SampleCugCounter;

public interface SampleCugPrefixDAO extends BaseDAO<SampleCugCounter, String> {
    SampleCugCounter lockCounter();

    Long occupiedRangeEnd(long candidate);

    boolean isAssigned(long prefix);

    void register(long prefix, String actorId);

    Long findPatientPrefix(String patientId);

    void bindPatientPrefix(String patientId, long prefix, String actorId);

    void changePatientPrefix(String patientId, long prefix, String actorId);

    boolean isPrefixReferenced(long prefix, Timestamp now);

    void releasePrefix(long prefix);
}
