package org.openelisglobal.externalconnections.controller.rest.bean;

import java.util.ArrayList;
import java.util.List;

public class ExternalConnectionConfigOptions {

    private List<Option> programmedConnections = new ArrayList<>();
    private List<Option> authenticationTypes = new ArrayList<>();

    public List<Option> getProgrammedConnections() {
        return programmedConnections;
    }

    public void setProgrammedConnections(List<Option> programmedConnections) {
        this.programmedConnections = programmedConnections;
    }

    public List<Option> getAuthenticationTypes() {
        return authenticationTypes;
    }

    public void setAuthenticationTypes(List<Option> authenticationTypes) {
        this.authenticationTypes = authenticationTypes;
    }

    public static class Option {
        private String value;
        private String label;

        public Option() {
        }

        public Option(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }
}
