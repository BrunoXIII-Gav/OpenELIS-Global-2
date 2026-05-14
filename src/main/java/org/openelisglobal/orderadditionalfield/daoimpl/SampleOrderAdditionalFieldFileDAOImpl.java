package org.openelisglobal.orderadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.orderadditionalfield.dao.SampleOrderAdditionalFieldFileDAO;
import org.openelisglobal.orderadditionalfield.valueholder.SampleOrderAdditionalFieldFile;
import org.springframework.stereotype.Component;

@Component
public class SampleOrderAdditionalFieldFileDAOImpl extends BaseDAOImpl<SampleOrderAdditionalFieldFile, Integer>
        implements SampleOrderAdditionalFieldFileDAO {

    public SampleOrderAdditionalFieldFileDAOImpl() {
        super(SampleOrderAdditionalFieldFile.class);
    }

    @Override
    public Optional<SampleOrderAdditionalFieldFile> findBySampleIdAndFieldDefinitionId(Integer sampleId,
            Integer fieldDefinitionId) {
        if (sampleId == null || fieldDefinitionId == null) {
            return Optional.empty();
        }

        String hql = "from SampleOrderAdditionalFieldFile f where f.sampleId = :sampleId and f.fieldDefinitionId = :fieldDefinitionId";
        Query<SampleOrderAdditionalFieldFile> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleOrderAdditionalFieldFile.class);
        query.setParameter("sampleId", sampleId);
        query.setParameter("fieldDefinitionId", fieldDefinitionId);
        return Optional.ofNullable(query.uniqueResult());
    }

    @Override
    public List<SampleOrderAdditionalFieldFile> findBySampleIdAndFieldDefinitionIds(Integer sampleId,
            List<Integer> fieldDefinitionIds) {
        if (sampleId == null || fieldDefinitionIds == null || fieldDefinitionIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "from SampleOrderAdditionalFieldFile f where f.sampleId = :sampleId and f.fieldDefinitionId in (:fieldDefinitionIds)";
        Query<SampleOrderAdditionalFieldFile> query = entityManager.unwrap(Session.class).createQuery(hql,
                SampleOrderAdditionalFieldFile.class);
        query.setParameter("sampleId", sampleId);
        query.setParameterList("fieldDefinitionIds", fieldDefinitionIds);
        return query.list();
    }
}
