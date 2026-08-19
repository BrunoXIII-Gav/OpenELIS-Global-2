package org.openelisglobal.provider.dao;

import java.util.List;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.provider.valueholder.ProviderProfileFieldValue;

public interface ProviderProfileFieldValueDAO extends BaseDAO<ProviderProfileFieldValue, Integer> {

    List<ProviderProfileFieldValue> findByProviderIdAndProfessionalProfileCode(Integer providerId, String profileCode);

    List<ProviderProfileFieldValue> findByProviderIds(List<Integer> providerIds);

    long countByProfessionalProfileCodeAndFieldKey(String profileCode, String fieldKey);

    void deleteByProviderId(Integer providerId);
}
