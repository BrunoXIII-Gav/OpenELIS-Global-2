package org.openelisglobal.sample.daoimpl;

import jakarta.persistence.LockModeType;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.common.provider.validation.AccessionNumberValidatorFactory.AccessionFormat;
import org.openelisglobal.common.valueholder.AccessionNumberInfo;
import org.openelisglobal.common.valueholder.AccessionNumberInfo.AccessionIdentity;
import org.openelisglobal.sample.dao.OrderAccessionDAO;
import org.openelisglobal.sample.valueholder.Sample;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class OrderAccessionDAOImpl extends BaseDAOImpl<Sample, String> implements OrderAccessionDAO {
    public OrderAccessionDAOImpl() {
        super(Sample.class);
    }

    @Override
    public void lockSubmission() {
        if (counter(new AccessionIdentity("__ORDER_SUBMISSION__", AccessionFormat.MAIN), true) == null)
            throw new IllegalStateException("Order accession migration is required");
    }

    @Override
    public AccessionNumberInfo counter(AccessionIdentity identity, boolean lock) {
        return entityManager.find(AccessionNumberInfo.class, identity,
                lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
    }

    @Override
    public void saveCounter(AccessionNumberInfo counter) {
        entityManager.persist(counter);
    }

    @Override
    public String largest(String prefix, int length) {
        // Escape LIKE metacharacters in configurable prefixes.
        String pattern = prefix.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var rows = entityManager
                .createQuery("select s.accessionNumber from Sample s "
                        + "where s.accessionNumber like :prefix escape '!' and length(s.accessionNumber)=:length "
                        + "order by s.accessionNumber desc", String.class)
                .setParameter("prefix", pattern).setParameter("length", length).setMaxResults(1).getResultList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public boolean isUsed(String number) {
        return entityManager.createQuery("select count(s.id) from Sample s where s.accessionNumber=:number", Long.class)
                .setParameter("number", number).getSingleResult() > 0;
    }
}
