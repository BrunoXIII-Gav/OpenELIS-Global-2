package org.openelisglobal.sample.service;

import org.openelisglobal.sample.form.CugPrefixSettingsForm;

public interface CugPrefixService {
    CugPrefixSettingsForm getSettings();

    CugPrefixSettingsForm setStart(String start, String actorId);

    void lockAllocation();

    long allocate(String actorId);

    long claim(String requestedPrefix, String actorId);

    boolean isAssigned(long prefix);

    void register(long prefix, String actorId);

    /**
     * Releases an unreferenced prefix and its inactive technical reservations under
     * the allocation lock, without moving the counter backwards.
     */
    boolean releaseIfUnused(long prefix);
}
