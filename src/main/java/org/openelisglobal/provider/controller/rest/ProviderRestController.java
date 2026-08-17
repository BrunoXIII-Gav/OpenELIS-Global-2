package org.openelisglobal.provider.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileDefinitionForm;
import org.openelisglobal.professionalprofile.service.ProfessionalProfileDefinitionService;
import org.openelisglobal.provider.form.ProviderUpsertForm;
import org.openelisglobal.provider.service.ProviderProfileFieldService;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest")
public class ProviderRestController extends BaseRestController {

    @Autowired
    private ProviderService providerService;
    @Autowired
    private PersonService personService;
    @Autowired
    private ProviderProfileFieldService providerProfileFieldService;
    @Autowired
    private ProfessionalProfileDefinitionService professionalProfileDefinitionService;

    @GetMapping(value = "/Provider/raw/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Provider> getProvider(@PathVariable String id) {
        Provider provider = providerService.get(id);
        providerProfileFieldService.hydrateProfileFieldValues(provider);
        return ResponseEntity.ok(provider);
    }

    @GetMapping(value = "/Provider/Person/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Person> getPerson(@PathVariable String id) {
        Person person = personService.get(id);
        return ResponseEntity.ok(person);
    }

    @GetMapping(value = "/providers/professional-profile/{profileCode}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<List<IdValuePair>> getProviderPersonsByProfessionalProfile(@PathVariable String profileCode) {
        final String normalizedProfileCode = normalizeProfessionalProfileCode(profileCode);
        if (StringUtils.isBlank(normalizedProfileCode)) {
            return ResponseEntity.ok(List.of());
        }

        List<IdValuePair> providers = providerService.getAllActiveProviders().stream()
                .filter(provider -> provider.getPerson() != null && StringUtils.isNotBlank(provider.getPerson().getId()))
                .filter(provider -> normalizedProfileCode
                        .equals(normalizeProfessionalProfileCode(provider.getProfessionalProfileCode())))
                .map(provider -> new IdValuePair(provider.getPerson().getId(),
                        buildProviderDisplayLabel(provider, normalizedProfileCode)))
                .filter(pair -> StringUtils.isNotBlank(pair.getValue()))
                .sorted(Comparator.comparing(IdValuePair::getValue, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        return ResponseEntity.ok(providers);
    }

    @GetMapping(value = "/providers/form-config", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getProviderFormConfig(@RequestParam String profileCode,
            @RequestParam(required = false) String providerId) {
        Provider provider = StringUtils.isBlank(providerId) ? new Provider() : providerService.get(providerId);
        if (provider == null) {
            provider = new Provider();
        }
        ProfessionalProfileDefinitionForm profile = professionalProfileDefinitionService.getProfile(profileCode);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("fields", providerProfileFieldService.getFieldDefinitionsForProvider(profileCode, provider));
        response.put("specialtyOptions", profile == null ? List.of() : profile.getSpecialtyOptions());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/Provider/FhirUuid", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> insertOrUpdateProviderByFhirUuid(HttpServletRequest request,
            @RequestParam(required = false) UUID fhirUuid, @RequestBody ProviderUpsertForm form) {
        try {
            String dni = StringUtils.trimToEmpty(provider.getDni());
            if (!dni.matches("\\d{1,8}")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("DNI is required and must be at most 8 digits.");
            }
            if (fhirUuid == null) {
                fhirUuid = UUID.randomUUID();
            }
            Provider provider = mapFormToProvider(form, fhirUuid);
            Map<String, Object> normalizedProfileFieldValues = providerProfileFieldService
                    .normalizeAndValidateProfileFieldValues(provider.getProfessionalProfileCode(),
                            form.getProfileFieldValues());
            Provider updatedProvider = providerService.insertOrUpdateProviderByFhirUuid(fhirUuid, provider,
                    getSysUserId(request), normalizedProfileFieldValues);
            return ResponseEntity.ok(updatedProvider);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing request.");
        }
    }

    private Provider mapFormToProvider(ProviderUpsertForm form, UUID fhirUuid) {
        Provider provider = new Provider();
        provider.setId(StringUtils.trimToNull(form.getProviderId()));
        provider.setFhirUuid(fhirUuid);
        provider.setProfessionalProfileCode(normalizeProfessionalProfileCode(form.getProfessionalProfileCode()));
        provider.setActive(Boolean.TRUE.equals(form.getActive()));
        provider.setDni(StringUtils.trimToNull(form.getDni()));
        provider.setSpecialty(StringUtils.trimToNull(form.getSpecialty()));
        provider.setProfessionalInitials(StringUtils.trimToNull(form.getProfessionalInitials()));

        Person person = new Person();
        person.setLastName(StringUtils.trimToNull(form.getLastName()));
        person.setFirstName(StringUtils.trimToNull(form.getFirstName()));
        person.setWorkPhone(StringUtils.trimToNull(form.getTelephone()));
        person.setFax(StringUtils.trimToNull(form.getFax()));
        person.setEmail(StringUtils.trimToNull(form.getEmail()));
        provider.setPerson(person);
        return provider;
    }

    private String buildProviderDisplayLabel(Provider provider, String normalizedProfileCode) {
        String dni = StringUtils.trimToEmpty(provider.getDni());
        String initials = StringUtils.trimToEmpty(provider.getProfessionalInitials());
        String firstName = provider.getPerson() == null ? "" : StringUtils.trimToEmpty(provider.getPerson().getFirstName());
        String lastName = provider.getPerson() == null ? "" : StringUtils.trimToEmpty(provider.getPerson().getLastName());
        String fullName = (lastName + ", " + firstName).trim();
        String cleanName = StringUtils.isBlank(fullName) || ",".equals(fullName) ? ""
                : fullName.replaceAll("^,\\s*", "").replaceAll("\\s+,\\s*$", "");
        if (StringUtils.isNotBlank(dni) && StringUtils.isNotBlank(cleanName)) {
            return dni + " - " + cleanName;
        }
        if (StringUtils.isNotBlank(cleanName)) {
            return cleanName;
        }
        if (StringUtils.isNotBlank(dni) && StringUtils.isNotBlank(initials)) {
            return dni + " - " + initials;
        }
        if (StringUtils.isNotBlank(dni)) {
            return dni;
        }
        if (StringUtils.isNotBlank(initials)) {
            return initials;
        }
        return StringUtils.trimToEmpty(provider.getId());
    }

    private String normalizeProfessionalProfileCode(String rawValue) {
        return StringUtils.upperCase(StringUtils.trimToEmpty(rawValue));
    }
}
