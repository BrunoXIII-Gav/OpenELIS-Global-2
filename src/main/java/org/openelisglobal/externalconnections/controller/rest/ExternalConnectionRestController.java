package org.openelisglobal.externalconnections.controller.rest;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.externalconnections.controller.rest.bean.ExternalConnectionConfig;
import org.openelisglobal.externalconnections.controller.rest.bean.ExternalConnectionConfigOptions;
import org.openelisglobal.externalconnections.controller.rest.bean.ExternalConnectionContactConfig;
import org.openelisglobal.externalconnections.service.ExternalConnectionAuthenticationDataService;
import org.openelisglobal.externalconnections.service.ExternalConnectionContactService;
import org.openelisglobal.externalconnections.service.ExternalConnectionService;
import org.openelisglobal.externalconnections.valueholder.BasicAuthenticationData;
import org.openelisglobal.externalconnections.valueholder.ExternalConnection;
import org.openelisglobal.externalconnections.valueholder.ExternalConnection.AuthType;
import org.openelisglobal.externalconnections.valueholder.ExternalConnection.ProgrammedConnection;
import org.openelisglobal.externalconnections.valueholder.ExternalConnectionAuthenticationData;
import org.openelisglobal.externalconnections.valueholder.ExternalConnectionContact;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.person.valueholder.Person;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/external-connections")
public class ExternalConnectionRestController extends BaseRestController {

    @Autowired
    private ExternalConnectionService externalConnectionService;
    @Autowired
    private ExternalConnectionContactService externalConnectionContactService;
    @Autowired
    private ExternalConnectionAuthenticationDataService externalConnectionAuthenticationDataService;

    @GetMapping
    public ResponseEntity<List<ExternalConnectionConfig>> getAll() {
        List<ExternalConnectionConfig> response = externalConnectionService.getAll().stream().map(this::toConfigSummary)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/options")
    public ResponseEntity<ExternalConnectionConfigOptions> getOptions() {
        ExternalConnectionConfigOptions response = new ExternalConnectionConfigOptions();
        response.setProgrammedConnections(Arrays.stream(ProgrammedConnection.values())
                .map(option -> new ExternalConnectionConfigOptions.Option(option.getValue(), option.getMessage()))
                .collect(Collectors.toList()));
        response.setAuthenticationTypes(Arrays.stream(AuthType.values())
                .map(option -> new ExternalConnectionConfigOptions.Option(option.getValue(), option.getMessage()))
                .collect(Collectors.toList()));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExternalConnectionConfig> getById(@PathVariable("id") Integer id) {
        ExternalConnection connection = externalConnectionService.get(id);
        if (connection == null || connection.getId() == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toConfigDetail(connection));
    }

    @PostMapping
    public ResponseEntity<?> save(@RequestBody ExternalConnectionConfig payload) {
        if (GenericValidator.isBlankOrNull(payload.getName())
                || GenericValidator.isBlankOrNull(payload.getProgrammedConnection())
                || GenericValidator.isBlankOrNull(payload.getAuthenticationType())
                || GenericValidator.isBlankOrNull(payload.getUri())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        AuthType authType = resolveAuthType(payload.getAuthenticationType());
        ProgrammedConnection programmedConnection = resolveProgrammedConnection(payload.getProgrammedConnection());

        if (authType == null || programmedConnection == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid connection type"));
        }

        if (authType == AuthType.CERTIFICATE) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Certificate upload is not supported in this screen yet"));
        }

        ExternalConnection connection;
        BasicAuthenticationData basicAuthData = null;
        List<ExternalConnectionContact> contacts = new ArrayList<>();

        if (payload.getId() != null) {
            ExternalConnection existing = externalConnectionService.get(payload.getId());
            if (existing == null || existing.getId() == null) {
                return ResponseEntity.notFound().build();
            }
            connection = existing;
            Map<AuthType, ExternalConnectionAuthenticationData> authDataMap = externalConnectionAuthenticationDataService
                    .getForExternalConnection(connection.getId());
            if (authDataMap.containsKey(AuthType.BASIC)) {
                basicAuthData = (BasicAuthenticationData) authDataMap.get(AuthType.BASIC);
            }
            contacts = externalConnectionContactService.getAllMatching("externalConnection.id", connection.getId());
        } else {
            connection = new ExternalConnection();
            connection.setNameLocalization(new Localization());
            connection.setDescriptionLocalization(new Localization());
        }

        connection.setActive(payload.getActive() == null ? Boolean.TRUE : payload.getActive());
        connection.setProgrammedConnection(programmedConnection);
        connection.setActiveAuthenticationType(authType);
        connection.setUri(URI.create(payload.getUri()));
        connection.getNameLocalization().setLocalizedValue(payload.getName());
        connection.getDescriptionLocalization().setLocalizedValue(payload.getDescription() == null ? "" : payload.getDescription());

        if (authType == AuthType.BASIC) {
            if (basicAuthData == null) {
                basicAuthData = new BasicAuthenticationData();
            }
            basicAuthData.setUsername(payload.getUsername());
            basicAuthData.setPassword(payload.getPassword());
        }

        List<ExternalConnectionContact> mappedContacts = mapContacts(payload.getContacts(), contacts);

        if (payload.getId() == null) {
            Map<AuthType, ExternalConnectionAuthenticationData> authDataToCreate = authType == AuthType.BASIC
                    ? Map.of(AuthType.BASIC, basicAuthData)
                    : Map.of();
            externalConnectionService.createNewExternalConnection(authDataToCreate, mappedContacts, connection);
        } else {
            Map<AuthType, ExternalConnectionAuthenticationData> authDataToSave = authType == AuthType.BASIC
                    ? Map.of(AuthType.BASIC, basicAuthData)
                    : Map.of();
            externalConnectionService.updateExternalConnection(authDataToSave, mappedContacts, connection);
        }

        ConfigurationProperties.loadDBValuesIntoConfiguration();

        ExternalConnection saved = payload.getId() == null
                ? externalConnectionService.getAll().stream()
                        .filter(item -> item.getProgrammedConnection() == programmedConnection)
                        .findFirst().orElse(connection)
                : connection;

        return ResponseEntity.ok(toConfigDetail(saved));
    }

    private List<ExternalConnectionContact> mapContacts(List<ExternalConnectionContactConfig> payloadContacts,
            List<ExternalConnectionContact> existingContacts) {
        List<ExternalConnectionContactConfig> safeContacts = payloadContacts == null ? new ArrayList<>() : payloadContacts;

        return safeContacts.stream().filter(this::hasAnyContactValue).map(contactPayload -> {
            ExternalConnectionContact contact = existingContacts.stream()
                    .filter(existing -> existing.getId() != null && existing.getId().equals(contactPayload.getId()))
                    .findFirst().orElseGet(ExternalConnectionContact::new);

            Person person = contact.getPerson();
            if (person == null) {
                person = new Person();
                contact.setPerson(person);
            }

            person.setId(contactPayload.getPersonId());
            person.setLastName(contactPayload.getLastName());
            person.setFirstName(contactPayload.getFirstName());
            person.setPrimaryPhone(contactPayload.getPrimaryPhone());
            person.setEmail(contactPayload.getEmail());
            return contact;
        }).collect(Collectors.toList());
    }

    private boolean hasAnyContactValue(ExternalConnectionContactConfig contact) {
        return !GenericValidator.isBlankOrNull(contact.getLastName())
                || !GenericValidator.isBlankOrNull(contact.getFirstName())
                || !GenericValidator.isBlankOrNull(contact.getPrimaryPhone())
                || !GenericValidator.isBlankOrNull(contact.getEmail());
    }

    private ExternalConnectionConfig toConfigSummary(ExternalConnection connection) {
        ExternalConnectionConfig response = new ExternalConnectionConfig();
        response.setId(connection.getId());
        response.setActive(connection.getActive());
        response.setName(connection.getNameLocalization() == null ? "" : connection.getNameLocalization().getLocalizedValue());
        response.setDescription(connection.getDescriptionLocalization() == null ? ""
                : connection.getDescriptionLocalization().getLocalizedValue());
        response.setProgrammedConnection(connection.getProgrammedConnection() == null ? ""
                : connection.getProgrammedConnection().getValue());
        response.setProgrammedConnectionLabel(connection.getProgrammedConnection() == null ? ""
                : connection.getProgrammedConnection().getMessage());
        response.setAuthenticationType(connection.getActiveAuthenticationType() == null ? ""
                : connection.getActiveAuthenticationType().getValue());
        response.setAuthenticationTypeLabel(connection.getActiveAuthenticationType() == null ? ""
                : connection.getActiveAuthenticationType().getMessage());
        response.setUri(connection.getUri() == null ? "" : connection.getUri().toString());
        response.setLastUpdated(connection.getLastupdated() == null ? "" : connection.getLastupdated().toString());
        return response;
    }

    private ExternalConnectionConfig toConfigDetail(ExternalConnection connection) {
        ExternalConnectionConfig response = toConfigSummary(connection);
        Map<AuthType, ExternalConnectionAuthenticationData> authDataMap = externalConnectionAuthenticationDataService
                .getForExternalConnection(connection.getId());
        if (authDataMap.containsKey(AuthType.BASIC)) {
            BasicAuthenticationData basicAuthData = (BasicAuthenticationData) authDataMap.get(AuthType.BASIC);
            response.setUsername(basicAuthData.getUsername());
            response.setPassword(basicAuthData.getPassword());
        }
        List<ExternalConnectionContactConfig> contacts = externalConnectionContactService
                .getAllMatching("externalConnection.id", connection.getId()).stream().map(contact -> {
                    ExternalConnectionContactConfig contactConfig = new ExternalConnectionContactConfig();
                    contactConfig.setId(contact.getId());
                    if (contact.getPerson() != null) {
                        contactConfig.setPersonId(contact.getPerson().getId());
                        contactConfig.setLastName(contact.getPerson().getLastName());
                        contactConfig.setFirstName(contact.getPerson().getFirstName());
                        contactConfig.setPrimaryPhone(contact.getPerson().getPrimaryPhone());
                        contactConfig.setEmail(contact.getPerson().getEmail());
                    }
                    return contactConfig;
                }).collect(Collectors.toList());
        response.setContacts(contacts);
        return response;
    }

    private AuthType resolveAuthType(String value) {
        return Arrays.stream(AuthType.values()).filter(type -> type.getValue().equals(value)).findFirst().orElse(null);
    }

    private ProgrammedConnection resolveProgrammedConnection(String value) {
        return Arrays.stream(ProgrammedConnection.values()).filter(type -> type.getValue().equals(value)).findFirst()
                .orElse(null);
    }
}
