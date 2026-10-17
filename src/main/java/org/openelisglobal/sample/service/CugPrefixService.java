package org.openelisglobal.sample.service;

import org.openelisglobal.sample.form.CugPrefixSettingsForm;

public interface CugPrefixService {
    CugPrefixSettingsForm getSettings();

    CugPrefixSettingsForm setStart(String start, String actorId);

    void lockAllocation();

    long allocate(String actorId);

    boolean isAssigned(long prefix);

    void register(long prefix, String actorId);
}
