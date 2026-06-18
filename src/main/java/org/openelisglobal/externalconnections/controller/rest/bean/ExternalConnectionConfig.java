package org.openelisglobal.externalconnections.controller.rest.bean;

import java.util.ArrayList;
import java.util.List;

public class ExternalConnectionConfig {

    private Integer id;
    private Boolean active;
    private String name;
    private String description;
    private String programmedConnection;
    private String programmedConnectionLabel;
    private String authenticationType;
    private String authenticationTypeLabel;
    private String uri;
    private String username;
    private String password;
    private String lastUpdated;
    private List<ExternalConnectionContactConfig> contacts = new ArrayList<>();

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getProgrammedConnection() {
        return programmedConnection;
    }

    public void setProgrammedConnection(String programmedConnection) {
        this.programmedConnection = programmedConnection;
    }

    public String getProgrammedConnectionLabel() {
        return programmedConnectionLabel;
    }

    public void setProgrammedConnectionLabel(String programmedConnectionLabel) {
        this.programmedConnectionLabel = programmedConnectionLabel;
    }

    public String getAuthenticationType() {
        return authenticationType;
    }

    public void setAuthenticationType(String authenticationType) {
        this.authenticationType = authenticationType;
    }

    public String getAuthenticationTypeLabel() {
        return authenticationTypeLabel;
    }

    public void setAuthenticationTypeLabel(String authenticationTypeLabel) {
        this.authenticationTypeLabel = authenticationTypeLabel;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<ExternalConnectionContactConfig> getContacts() {
        return contacts;
    }

    public void setContacts(List<ExternalConnectionContactConfig> contacts) {
        this.contacts = contacts;
    }
}
