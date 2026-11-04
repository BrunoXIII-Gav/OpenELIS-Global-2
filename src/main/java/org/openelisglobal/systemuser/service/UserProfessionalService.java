package org.openelisglobal.systemuser.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.form.ProviderUpsertForm;
import org.openelisglobal.provider.service.ProviderProfileFieldService;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compiles and saves the account and its optional professional record in one
 * transaction.
 */
@Service
@Transactional
public class UserProfessionalService {
    @Autowired
    private UserService userService;
    @Autowired
    private PersonService personService;
    @Autowired
    private ProviderService providerService;
    @Autowired
    private ProviderProfileFieldService profileFieldService;

    @Transactional(readOnly = true)
    public ProviderUpsertForm getProfessional(String personId) {
        Provider provider = linkedProvider(personId);
        if (provider == null)
            return null;
        profileFieldService.hydrateProfileFieldValues(provider);
        ProviderUpsertForm form = new ProviderUpsertForm();
        form.setProviderId(provider.getId());
        form.setFhirUuid(provider.getFhirUuidAsString());
        form.setProfessionalProfileCode(provider.getProfessionalProfileCode());
        form.setActive(provider.getActive());
        form.setDni(provider.getDni());
        form.setSpecialty(provider.getSpecialty());
        form.setProfessionalInitials(provider.getProfessionalInitials());
        form.setFirstName(provider.getPerson().getFirstName());
        form.setLastName(provider.getPerson().getLastName());
        form.setTelephone(provider.getPerson().getWorkPhone());
        form.setFax(provider.getPerson().getFax());
        form.setEmail(provider.getPerson().getEmail());
        form.setProfileFieldValues(provider.getProfileFieldValues());
        return form;
    }

    public void save(LoginUser login, boolean newLogin, SystemUser user, boolean newUser, List<String> roles,
            Map<String, Set<String>> labRoles, ProviderUpsertForm professional, String actorId) {
        if (professional != null)
            saveProfessional(user, professional, actorId);
        userService.updateLoginUser(login, newLogin, user, newUser, roles, actorId);
        userService.saveUserLabUnitRoles(user, labRoles, actorId);
    }

    private Provider linkedProvider(String personId) {
        if (StringUtils.isBlank(personId))
            return null;
        Person person = personService.getPersonById(personId);
        return person == null ? null : providerService.getProviderByPerson(person);
    }

    private void saveProfessional(SystemUser user, ProviderUpsertForm form, String actorId) {
        if (StringUtils.isBlank(form.getProfessionalProfileCode()))
            throw new IllegalArgumentException("Professional profile is required for a professional record");
        if (StringUtils.isBlank(form.getDni()) || !form.getDni().matches("\\d{1,8}"))
            throw new IllegalArgumentException("DNI is required and must be at most 8 digits");
        Provider existing = linkedProvider(user.getLinkedProviderPersonId());
        if (existing == null && StringUtils.isNotBlank(user.getLinkedProviderPersonId()))
            throw new IllegalArgumentException("Linked professional does not exist");
        if (existing != null && StringUtils.isNotBlank(form.getProviderId())
                && !existing.getId().equals(form.getProviderId()))
            throw new IllegalArgumentException("Professional record does not match the linked person");
        UUID uuid = existing == null || existing.getFhirUuid() == null ? UUID.randomUUID() : existing.getFhirUuid();
        if (existing != null && existing.getFhirUuid() == null) {
            existing.setFhirUuid(uuid);
            existing.setSysUserId(actorId);
            providerService.update(existing);
        }
        Provider provider = existing == null ? new Provider() : existing;
        provider.setFhirUuid(uuid);
        provider.setProfessionalProfileCode(StringUtils.upperCase(form.getProfessionalProfileCode().trim()));
        provider.setActive(form.getActive() == null || form.getActive());
        provider.setDni(form.getDni().trim());
        provider.setSpecialty(StringUtils.trimToNull(form.getSpecialty()));
        provider.setProfessionalInitials(StringUtils.trimToNull(form.getProfessionalInitials()));
        Person person = existing == null ? new Person() : existing.getPerson();
        person.setFirstName(user.getFirstName());
        person.setLastName(user.getLastName());
        person.setWorkPhone(StringUtils.trimToNull(form.getTelephone()));
        person.setFax(StringUtils.trimToNull(form.getFax()));
        person.setEmail(StringUtils.trimToNull(form.getEmail()));
        provider.setPerson(person);
        Map<String, Object> values = profileFieldService.normalizeAndValidateProfileFieldValues(
                provider.getProfessionalProfileCode(), form.getProfileFieldValues());
        provider = providerService.insertOrUpdateProviderByFhirUuid(uuid, provider, actorId, values);
        user.setLinkedProviderPersonId(provider.getPerson().getId());
        user.setProfessionalProfileCode(provider.getProfessionalProfileCode());
    }
}
