package org.openelisglobal.professionalprofile.service;

import java.util.List;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileDefinitionForm;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileSettingsForm;

public interface ProfessionalProfileDefinitionService {

    List<ProfessionalProfileDefinitionForm> getProfiles();

    ProfessionalProfileDefinitionForm getProfile(String code);

    ProfessionalProfileDefinitionForm createProfile(ProfessionalProfileDefinitionForm form, String currentUserId);

    ProfessionalProfileDefinitionForm updateProfile(String code, ProfessionalProfileDefinitionForm form, String currentUserId);

    void deleteProfile(String code, String currentUserId);

    ProfessionalProfileSettingsForm getSettings();

    ProfessionalProfileSettingsForm updateSettings(ProfessionalProfileSettingsForm form, String currentUserId);
}
