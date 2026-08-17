package org.openelisglobal.professionalprofile.service;

import java.util.List;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldDefinitionForm;

public interface ProfessionalProfileFieldConfigService {

    List<ProfessionalProfileFieldDefinitionForm> getFieldsForProfile(String profileCode, boolean includeInactive);

    void saveFieldsForProfile(String profileCode, List<ProfessionalProfileFieldDefinitionForm> fields, String currentUserId);

    void deleteFieldsForProfile(String profileCode, String currentUserId);
}
