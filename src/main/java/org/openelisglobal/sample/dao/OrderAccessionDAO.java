package org.openelisglobal.sample.dao;

import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.common.valueholder.AccessionNumberInfo;
import org.openelisglobal.common.valueholder.AccessionNumberInfo.AccessionIdentity;
import org.openelisglobal.sample.valueholder.Sample;

public interface OrderAccessionDAO extends BaseDAO<Sample, String> {
    void lockSubmission();

    AccessionNumberInfo counter(AccessionIdentity identity, boolean lock);

    void saveCounter(AccessionNumberInfo counter);

    String largest(String prefix, int length);

    boolean isUsed(String number);
}
