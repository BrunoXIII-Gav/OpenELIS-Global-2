package org.openelisglobal.provider.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.dao.ProviderDAO;
import org.openelisglobal.provider.valueholder.Provider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderServiceImpl extends AuditableBaseObjectServiceImpl<Provider, String> implements ProviderService {
    @Autowired
    protected ProviderDAO baseObjectDAO;
    @Autowired
    protected PersonService personService;
    @Autowired
    private ProviderProfileFieldService providerProfileFieldService;

    ProviderServiceImpl() {
        super(Provider.class);
    }

    @Override
    protected ProviderDAO getBaseObjectDAO() {
        return baseObjectDAO;
    }

    @Override
    @Transactional(readOnly = true)
    public void getData(Provider provider) {
        getBaseObjectDAO().getData(provider);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Provider> getPageOfProviders(int startingRecNo) {
        return getBaseObjectDAO().getPageOfProviders(startingRecNo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Provider> getAllProviders() {
        return getBaseObjectDAO().getAllProviders();
    }

    @Override
    @Transactional(readOnly = true)
    public Provider getProviderByPerson(Person person) {
        return getBaseObjectDAO().getProviderByPerson(person);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Provider> getAllActiveProviders() {
        return getBaseObjectDAO().getAllMatching("active", Boolean.TRUE);
    }

    @Override
    @Transactional
    public void deactivateAllProviders() {
        for (Provider provider : getBaseObjectDAO().getAll()) {
            provider.setActive(false);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Provider getProviderByFhirId(UUID fhirUuid) {
        List<Provider> providers = getBaseObjectDAO().getAllMatching("fhirUuid", fhirUuid);
        if (providers.size() <= 0) {
            return null;
        } else {
            return providers.get(0);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String getProviderIdByFhirId(UUID fhirUuid) {
        List<Provider> providers = getBaseObjectDAO().getAllMatching("fhirUuid", fhirUuid);
        if (providers.size() <= 0) {
            return null;
        } else {
            return providers.get(0).getId();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Provider> getPagesOfSearchedProviders(int startingRecNo, String parameter) {
        return baseObjectDAO.getPagesOfSearchedProviders(startingRecNo, parameter);
    }

    @Override
    @Transactional(readOnly = true)
    public int getTotalSearchedProviderCount(String parameter) {
        return baseObjectDAO.getTotalSearchedProviderCount(parameter);
    }

    @Override
    @Transactional
    public void deactivateProviders(List<Provider> providers) {
        for (Provider deactivateProvider : providers) {
            Optional<Provider> dbProvider = baseObjectDAO.get(deactivateProvider.getId());
            if (dbProvider.isPresent()) {
                dbProvider.get().setActive(false);
            } else {
                LogEvent.logWarn(this.getClass().getSimpleName(), "deactivateProviders",
                        "could not deactivate Provider with id '" + deactivateProvider.getId()
                                + "' as it could not be found");
            }
        }
    }

    @Override
    @Transactional
    public Provider insertOrUpdateProviderByFhirUuid(UUID fhirUuid, Provider provider) {
        return insertOrUpdateProviderByFhirUuid(fhirUuid, provider, StringUtils.defaultIfBlank(provider.getSysUserId(), "1"));
    }

    @Override
    @Transactional
    public Provider insertOrUpdateProviderByFhirUuid(UUID fhirUuid, Provider provider, String currentUserId) {
        validateRequiredProviderFields(provider);
        Provider dbProvider = getProviderByFhirId(fhirUuid);
        String normalizedProfileCode = resolveProfessionalProfileCode(provider.getProfessionalProfileCode());

        if (dbProvider != null) {
            dbProvider.setActive(provider.getActive());
            dbProvider.setNpi(provider.getNpi());
            dbProvider.setExternalId(provider.getExternalId());
            dbProvider.setSpecialty(provider.getSpecialty());
            dbProvider.setDni(provider.getDni());
            dbProvider.setProfessionalProfileCode(normalizedProfileCode);
            dbProvider.setProfessionalInitials(provider.getProfessionalInitials());
            dbProvider.setCbpCode(provider.getCbpCode());
            dbProvider.setProfileFieldsJson(provider.getProfileFieldsJson());
            dbProvider.setSysUserId(currentUserId);
            dbProvider.getPerson().setLastName(provider.getPerson().getLastName());
            dbProvider.getPerson().setMiddleName(provider.getPerson().getMiddleName());
            dbProvider.getPerson().setFirstName(provider.getPerson().getFirstName());
            dbProvider.getPerson().setEmail(provider.getPerson().getEmail());
            dbProvider.getPerson().setPrimaryPhone(provider.getPerson().getPrimaryPhone());
            dbProvider.getPerson().setWorkPhone(provider.getPerson().getWorkPhone());
            dbProvider.getPerson().setFax(provider.getPerson().getFax());
            dbProvider.getPerson().setCellPhone(provider.getPerson().getCellPhone());
            dbProvider.getPerson().setSysUserId(currentUserId);
            dbProvider = save(dbProvider);
        } else {
            if (fhirUuid == null) {
                fhirUuid = UUID.randomUUID();
            }
            provider.setFhirUuid(fhirUuid);
            provider.setProfessionalProfileCode(normalizedProfileCode);
            provider.setSysUserId(currentUserId);
            provider.getPerson().setSysUserId(currentUserId);
            provider.setPerson(personService.save(provider.getPerson()));
            dbProvider = save(provider);
        }
        return dbProvider;
    }

    @Override
    @Transactional
    public Provider insertOrUpdateProviderByFhirUuid(UUID fhirUuid, Provider provider, String currentUserId,
            Map<String, Object> profileFieldValues) {
        Provider savedProvider = insertOrUpdateProviderByFhirUuid(fhirUuid, provider, currentUserId);
        providerProfileFieldService.saveProfileFieldValues(savedProvider, savedProvider.getProfessionalProfileCode(),
                profileFieldValues);
        providerProfileFieldService.hydrateProfileFieldValues(savedProvider);
        return savedProvider;
    }

    private void validateRequiredProviderFields(Provider provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required.");
        }

        validateRequiredValue("Professional profile", provider.getProfessionalProfileCode());
        validateRequiredValue("Professional last name", provider.getPerson() == null ? null : provider.getPerson().getLastName());
        validateRequiredValue("Professional first name", provider.getPerson() == null ? null : provider.getPerson().getFirstName());
        validateRequiredValue("DNI", provider.getDni());
        validateRequiredValue("Initials", provider.getProfessionalInitials());
    }

    private void validateRequiredValue(String label, String value) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    private String resolveProfessionalProfileCode(String profileCode) {
        String normalized = StringUtils.upperCase(StringUtils.trimToNull(profileCode));
        return normalized == null ? "" : normalized;
    }
}
