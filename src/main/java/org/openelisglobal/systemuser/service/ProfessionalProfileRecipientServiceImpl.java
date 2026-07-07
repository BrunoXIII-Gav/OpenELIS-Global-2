package org.openelisglobal.systemuser.service;

import java.sql.Date;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.login.service.LoginUserService;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProfessionalProfileRecipientServiceImpl implements ProfessionalProfileRecipientService {

    private static final String YES = "Y";
    private static final String BIOLOGIST = "BIOLOGIST";
    private static final String MEDICAL_DOCTOR = "MEDICAL_DOCTOR";

    @Autowired
    private SystemUserService systemUserService;

    @Autowired
    private ProviderService providerService;

    @Autowired
    private LoginUserService loginUserService;

    @Override
    public List<SystemUser> getEligibleUsersForProfessionalProfile(String profileCode, boolean activeOnly) {
        return getEligibleUsersForProfessionalProfile(profileCode, activeOnly, true);
    }

    @Override
    public List<SystemUser> getEligibleUsersForProfessionalProfile(String profileCode, boolean activeOnly,
            boolean requireDeliverableEmail) {
        String normalizedProfileCode = normalizeProfessionalProfileCode(profileCode);
        if (StringUtils.isBlank(normalizedProfileCode)) {
            return Collections.emptyList();
        }

        Map<String, Provider> providersByPersonId = providerService.getAllActiveProviders().stream()
                .filter(provider -> provider.getPerson() != null && StringUtils.isNotBlank(provider.getPerson().getId()))
                .collect(Collectors.toMap(provider -> provider.getPerson().getId(), provider -> provider,
                        (left, right) -> left));

        Map<String, LoginUser> loginBySystemUserId = loginUserService.getAll().stream()
                .collect(Collectors.toMap(login -> String.valueOf(loginUserService.getSystemUserId(login)), login -> login,
                        (left, right) -> left));

        return systemUserService.getAll().stream()
                .filter(user -> !activeOnly || YES.equalsIgnoreCase(user.getIsActive()))
                .filter(user -> normalizedProfileCode
                        .equals(normalizeProfessionalProfileCode(user.getProfessionalProfileCode())))
                .filter(user -> isUserEligibleForProfile(user, providersByPersonId, loginBySystemUserId,
                        normalizedProfileCode, requireDeliverableEmail))
                .sorted(Comparator.comparing(SystemUser::getNameForDisplay, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    @Override
    public List<IdValuePair> getEligibleUserOptionsForProfessionalProfile(String profileCode, boolean activeOnly) {
        return getEligibleUserOptionsForProfessionalProfile(profileCode, activeOnly, false, true);
    }

    @Override
    public List<IdValuePair> getEligibleUserOptionsForProfessionalProfile(String profileCode, boolean activeOnly,
            boolean useFullNameLabel) {
        return getEligibleUserOptionsForProfessionalProfile(profileCode, activeOnly, useFullNameLabel, true);
    }

    @Override
    public List<IdValuePair> getEligibleUserOptionsForProfessionalProfile(String profileCode, boolean activeOnly,
            boolean useFullNameLabel, boolean requireDeliverableEmail) {
        Map<String, Provider> providersByPersonId = providerService.getAllActiveProviders().stream()
                .filter(provider -> provider.getPerson() != null && StringUtils.isNotBlank(provider.getPerson().getId()))
                .collect(Collectors.toMap(provider -> provider.getPerson().getId(), provider -> provider,
                        (left, right) -> left));

        return getEligibleUsersForProfessionalProfile(profileCode, activeOnly, requireDeliverableEmail).stream()
                .map(user -> new IdValuePair(user.getId(),
                        buildProfessionalDisplayLabel(user, providersByPersonId.get(user.getLinkedProviderPersonId()),
                                useFullNameLabel)))
                .filter(option -> StringUtils.isNotBlank(option.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public String normalizeProfessionalProfileCode(String rawValue) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(rawValue));
        if (StringUtils.isBlank(normalized)) {
            return "";
        }
        if ("BIOLOGO".equals(normalized) || "BIOLOGISTA".equals(normalized)) {
            return BIOLOGIST;
        }
        if ("MEDICO".equals(normalized) || "MÉDICO".equals(normalized) || "DOCTOR".equals(normalized)) {
            return MEDICAL_DOCTOR;
        }
        return normalized;
    }

    private boolean isUserEligibleForProfile(SystemUser user, Map<String, Provider> providersByPersonId,
            Map<String, LoginUser> loginBySystemUserId, String normalizedProfileCode, boolean requireDeliverableEmail) {
        Provider linkedProvider = providersByPersonId.get(user.getLinkedProviderPersonId());
        if (linkedProvider == null) {
            return false;
        }
        if (requireDeliverableEmail && !hasDeliverableEmail(linkedProvider.getPerson())) {
            return false;
        }

        LoginUser login = loginBySystemUserId.get(user.getId());
        if (!isLoginEligible(login)) {
            return false;
        }

        if (!BIOLOGIST.equals(normalizedProfileCode)) {
            return true;
        }

        String providerProfileCode = normalizeProfessionalProfileCode(linkedProvider.getProfessionalProfileCode());
        if (!BIOLOGIST.equals(providerProfileCode)) {
            return false;
        }
        return true;
    }

    private boolean isLoginEligible(LoginUser login) {
        if (login == null) {
            return false;
        }
        if (YES.equalsIgnoreCase(login.getAccountDisabled()) || YES.equalsIgnoreCase(login.getAccountLocked())) {
            return false;
        }
        Date passwordExpiredDate = login.getPasswordExpiredDate();
        return passwordExpiredDate == null || !passwordExpiredDate.before(new Date(System.currentTimeMillis()));
    }

    private boolean hasDeliverableEmail(Person person) {
        return person != null && StringUtils.isNotBlank(StringUtils.trimToEmpty(person.getEmail()));
    }

    private String buildProfessionalDisplayLabel(SystemUser user, Provider linkedProvider, boolean useFullNameLabel) {
        if (useFullNameLabel) {
            return user.getNameForDisplay();
        }
        String profileCode = normalizeProfessionalProfileCode(user.getProfessionalProfileCode());
        if (BIOLOGIST.equals(profileCode) && linkedProvider != null) {
            String initials = StringUtils.trimToEmpty(linkedProvider.getProfessionalInitials());
            if (StringUtils.isNotBlank(initials)) {
                return initials;
            }
            return user.getNameForDisplay();
        }
        return user.getNameForDisplay();
    }
}
