package org.openelisglobal.provider.controller.rest;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
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
public class ProviderRestController {

    @Autowired
    private ProviderService providerService;
    @Autowired
    private PersonService personService;

    @GetMapping(value = "/Provider/raw/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Provider> getProvider(@PathVariable String id) {
        Provider provider = providerService.get(id);
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

    @PostMapping(value = "/Provider/FhirUuid", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> insertOrUpdateProviderByFhirUuid(@RequestParam(required = false) UUID fhirUuid,
            @RequestBody Provider provider) {
        try {
            String dni = StringUtils.trimToEmpty(provider.getDni());
            if (!dni.matches("\\d{1,8}")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("DNI is required and must be at most 8 digits.");
            }
            if (fhirUuid == null) {
                fhirUuid = UUID.randomUUID();
            }
            Provider updatedProvider = providerService.insertOrUpdateProviderByFhirUuid(fhirUuid, provider);
            return ResponseEntity.ok(updatedProvider);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing request.");
        }
    }

    private String buildProviderDisplayLabel(Provider provider, String normalizedProfileCode) {
        String dni = StringUtils.trimToEmpty(provider.getDni());
        if ("BIOLOGIST".equals(normalizedProfileCode)) {
            String initials = StringUtils.trimToEmpty(provider.getProfessionalInitials());
            if (StringUtils.isNotBlank(dni) && StringUtils.isNotBlank(initials)) {
                return dni + " - " + initials;
            }
            if (StringUtils.isNotBlank(dni)) {
                return dni;
            }
            return initials;
        }

        String firstName = provider.getPerson() == null ? "" : StringUtils.trimToEmpty(provider.getPerson().getFirstName());
        String lastName = provider.getPerson() == null ? "" : StringUtils.trimToEmpty(provider.getPerson().getLastName());
        String fullName = (lastName + ", " + firstName).trim();
        if (StringUtils.isNotBlank(dni) && StringUtils.isNotBlank(fullName) && !",".equals(fullName)) {
            String cleanName = fullName.replaceAll("^,\\s*", "").replaceAll("\\s+,\\s*$", "");
            return dni + " - " + cleanName;
        }
        if (StringUtils.isNotBlank(dni)) {
            return dni;
        }
        if (StringUtils.isNotBlank(fullName) && !",".equals(fullName)) {
            return fullName.replaceAll("^,\\s*", "").replaceAll("\\s+,\\s*$", "");
        }
        return StringUtils.trimToEmpty(provider.getId());
    }

    private String normalizeProfessionalProfileCode(String rawValue) {
        String normalized = StringUtils.upperCase(StringUtils.trimToEmpty(rawValue));
        if (StringUtils.isBlank(normalized)) {
            return "";
        }

        if ("BIOLOGO".equals(normalized) || "BIOLOGISTA".equals(normalized)) {
            return "BIOLOGIST";
        }
        if ("MEDICO".equals(normalized) || "MÉDICO".equals(normalized) || "DOCTOR".equals(normalized)) {
            return "MEDICAL_DOCTOR";
        }
        return normalized;
    }
}
