package org.openelisglobal.patientadditionalfield.daoimpl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.patientadditionalfield.dao.PatientAdditionalFieldOptionDAO;
import org.openelisglobal.patientadditionalfield.valueholder.PatientAdditionalFieldOption;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class PatientAdditionalFieldOptionDAOImpl extends BaseDAOImpl<PatientAdditionalFieldOption, Integer>
        implements PatientAdditionalFieldOptionDAO {

    public PatientAdditionalFieldOptionDAOImpl() {
        super(PatientAdditionalFieldOption.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientAdditionalFieldOption> findByDefinitionId(Integer definitionId, boolean includeInactive) {
        if (definitionId == null) {
            return Collections.emptyList();
        }
        String hql = "from PatientAdditionalFieldOption o where o.fieldDefinitionId = :definitionId"
                + (includeInactive ? "" : " and o.active = true") + " order by o.sortOrder asc, o.id asc";
        Query<PatientAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientAdditionalFieldOption.class);
        query.setParameter("definitionId", definitionId);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientAdditionalFieldOption> findByDefinitionIds(List<Integer> definitionIds, boolean activeOnly) {
        if (definitionIds == null || definitionIds.isEmpty()) {
            return Collections.emptyList();
        }
        String hql = "from PatientAdditionalFieldOption o where o.fieldDefinitionId in (:definitionIds)"
                + (activeOnly ? " and o.active = true" : "") + " order by o.sortOrder asc, o.id asc";
        Query<PatientAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientAdditionalFieldOption.class);
        query.setParameterList("definitionIds", definitionIds);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatientAdditionalFieldOption> findByDefinitionIdAndOptionKey(Integer definitionId,
            String optionKey) {
        if (definitionId == null || optionKey == null || optionKey.trim().isEmpty()) {
            return Optional.empty();
        }
        String hql = "from PatientAdditionalFieldOption o where o.fieldDefinitionId = :definitionId"
                + " and lower(o.optionKey) = :optionKey";
        Query<PatientAdditionalFieldOption> query = entityManager.unwrap(Session.class).createQuery(hql,
                PatientAdditionalFieldOption.class);
        query.setParameter("definitionId", definitionId);
        query.setParameter("optionKey", optionKey.trim().toLowerCase());
        return query.uniqueResultOptional();
    }
}
