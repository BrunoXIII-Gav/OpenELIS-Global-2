package org.openelisglobal.provider.daoimpl;

import java.util.Collections;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openelisglobal.common.daoimpl.BaseDAOImpl;
import org.openelisglobal.provider.dao.ProviderProfileFieldValueDAO;
import org.openelisglobal.provider.valueholder.ProviderProfileFieldValue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class ProviderProfileFieldValueDAOImpl extends BaseDAOImpl<ProviderProfileFieldValue, Integer>
        implements ProviderProfileFieldValueDAO {

    private static final String ENTITY_NAME = ProviderProfileFieldValue.class.getName();

    public ProviderProfileFieldValueDAOImpl() {
        super(ProviderProfileFieldValue.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderProfileFieldValue> findByProviderIdAndProfessionalProfileCode(Integer providerId, String profileCode) {
        if (providerId == null || profileCode == null || profileCode.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "FROM " + ENTITY_NAME
                + " v WHERE v.providerId = :providerId AND v.professionalProfileCode = :profileCode ORDER BY v.id ASC";
        Query<ProviderProfileFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                ProviderProfileFieldValue.class);
        query.setParameter("providerId", providerId);
        query.setParameter("profileCode", profileCode.trim().toUpperCase());
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderProfileFieldValue> findByProviderIds(List<Integer> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return Collections.emptyList();
        }

        String hql = "FROM " + ENTITY_NAME + " v WHERE v.providerId IN (:providerIds) ORDER BY v.providerId ASC, v.id ASC";
        Query<ProviderProfileFieldValue> query = entityManager.unwrap(Session.class).createQuery(hql,
                ProviderProfileFieldValue.class);
        query.setParameterList("providerIds", providerIds);
        return query.list();
    }

    @Override
    @Transactional(readOnly = true)
    public long countByProfessionalProfileCodeAndFieldKey(String profileCode, String fieldKey) {
        String normalizedProfileCode = profileCode == null ? null : profileCode.trim().toUpperCase();
        String normalizedFieldKey = fieldKey == null ? null : fieldKey.trim().toUpperCase();
        if (normalizedProfileCode == null || normalizedProfileCode.isEmpty() || normalizedFieldKey == null
                || normalizedFieldKey.isEmpty()) {
            return 0L;
        }

        String hql = "SELECT COUNT(v) FROM " + ENTITY_NAME
                + " v WHERE v.professionalProfileCode = :profileCode AND v.fieldKey = :fieldKey";
        Query<Long> query = entityManager.unwrap(Session.class).createQuery(hql, Long.class);
        query.setParameter("profileCode", normalizedProfileCode);
        query.setParameter("fieldKey", normalizedFieldKey);
        Long count = query.uniqueResult();
        return count == null ? 0L : count;
    }

    @Override
    public void deleteByProviderId(Integer providerId) {
        if (providerId == null) {
            return;
        }

        String sql = "DELETE FROM provider_profile_field_value WHERE provider_id = :providerId";
        jakarta.persistence.Query query = entityManager.createNativeQuery(sql);
        query.setParameter("providerId", providerId);
        query.executeUpdate();
    }
}
