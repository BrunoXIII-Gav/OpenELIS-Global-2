package org.openelisglobal.sample.form;

/** Strings preserve exact BIGINT values in JavaScript clients. */
public class CugPrefixSettingsForm {
    private String startPrefix;
    private String nextAvailablePrefix;

    public String getStartPrefix() {
        return startPrefix;
    }

    public void setStartPrefix(String value) {
        startPrefix = value;
    }

    public String getNextAvailablePrefix() {
        return nextAvailablePrefix;
    }

    public void setNextAvailablePrefix(String value) {
        nextAvailablePrefix = value;
    }
}
