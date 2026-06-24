package org.openelisglobal.systemuser.service;

import java.util.List;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.systemuser.valueholder.SystemUser;

public interface ProfessionalProfileRecipientService {

    List<SystemUser> getEligibleUsersForProfessionalProfile(String profileCode, boolean activeOnly);

    List<SystemUser> getEligibleUsersForProfessionalProfile(String profileCode, boolean activeOnly,
            boolean requireDeliverableEmail);

    List<IdValuePair> getEligibleUserOptionsForProfessionalProfile(String profileCode, boolean activeOnly);

    List<IdValuePair> getEligibleUserOptionsForProfessionalProfile(String profileCode, boolean activeOnly,
            boolean useFullNameLabel);

    List<IdValuePair> getEligibleUserOptionsForProfessionalProfile(String profileCode, boolean activeOnly,
            boolean useFullNameLabel, boolean requireDeliverableEmail);

    String normalizeProfessionalProfileCode(String rawValue);
}
