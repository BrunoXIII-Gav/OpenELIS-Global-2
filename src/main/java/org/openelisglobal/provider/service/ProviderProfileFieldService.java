package org.openelisglobal.provider.service;

import java.util.List;
import java.util.Map;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldDefinitionForm;
import org.openelisglobal.provider.valueholder.Provider;

public interface ProviderProfileFieldService {

    List<ProfessionalProfileFieldDefinitionForm> getFieldDefinitionsForProvider(String profileCode, Provider provider);

    Map<String, Object> normalizeAndValidateProfileFieldValues(String profileCode, Map<String, Object> submittedValues);

    void saveProfileFieldValues(Provider provider, String profileCode, Map<String, Object> submittedValues);

    void hydrateProfileFieldValues(Provider provider);

    void hydrateProfileFieldValues(List<Provider> providers);

    void hydrateProfileFieldsJson(Provider provider);

    void hydrateProfileFieldsJson(List<Provider> providers);
}
