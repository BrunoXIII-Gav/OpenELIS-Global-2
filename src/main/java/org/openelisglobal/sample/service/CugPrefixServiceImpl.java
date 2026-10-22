package org.openelisglobal.sample.service;

import java.sql.Timestamp;
import java.util.UUID;
import org.openelisglobal.audittrail.dao.AuditEventDAO;
import org.openelisglobal.audittrail.valueholder.AuditEvent;
import org.openelisglobal.sample.dao.SampleCugPrefixDAO;
import org.openelisglobal.sample.exception.PatientCugPrefixException;
import org.openelisglobal.sample.form.CugPrefixSettingsForm;
import org.openelisglobal.sample.valueholder.SampleCugCounter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CugPrefixServiceImpl implements CugPrefixService {
    public static final long MAX_PREFIX = Long.MAX_VALUE - 1;
    private final SampleCugPrefixDAO prefixes;
    private final AuditEventDAO audit;

    public CugPrefixServiceImpl(SampleCugPrefixDAO prefixes, AuditEventDAO audit) {
        this.prefixes = prefixes;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public CugPrefixSettingsForm getSettings() {
        return toForm(prefixes.get("1").orElseThrow(() -> new IllegalStateException("CUG counter is not initialized")));
    }

    @Override
    public CugPrefixSettingsForm setStart(String start, String actorId) {
        long number = parseStart(start);
        SampleCugCounter counter = prefixes.lockCounter();
        AuditEvent event = new AuditEvent();
        event.setId(UUID.randomUUID().toString());
        event.setOperationId(UUID.randomUUID().toString());
        event.setModule("sample");
        event.setEntityName("SampleCugCounter");
        event.setEntityId("1");
        event.setAction("U");
        event.setField("configuredStart");
        event.setFieldLabel("cug.prefix.start");
        event.setOldValue(counter.getConfiguredStart().toString());
        event.setNewValue(Long.toString(number));
        event.setActorId(actorId);
        event.setActorSource("USER");
        event.setOccurredAt(new Timestamp(System.currentTimeMillis()));
        event.setSource("/rest/sample-cug/prefix-settings");
        counter.setConfiguredStart(number);
        counter.setNextCandidate(number);
        counter.setSysUserId(actorId);
        audit.insert(event);
        return toForm(counter);
    }

    @Override
    public void lockAllocation() {
        prefixes.lockCounter();
    }

    @Override
    public long allocate(String actorId) {
        SampleCugCounter counter = prefixes.lockCounter();
        long candidate = nextFree(counter.getNextCandidate());
        if (candidate > MAX_PREFIX)
            throw new IllegalArgumentException("cug.prefix.error.exhausted");
        prefixes.register(candidate, actorId);
        counter.setNextCandidate(candidate + 1);
        counter.setSysUserId(actorId);
        return candidate;
    }

    @Override
    public long claim(String requestedPrefix, String actorId) {
        long candidate;
        try {
            candidate = parseStart(requestedPrefix);
            if (!Long.toString(candidate).equals(requestedPrefix)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new PatientCugPrefixException(PatientCugPrefixException.INVALID);
        }
        // The same lock protects automatic allocation and trigger-based imports.
        prefixes.lockCounter();
        if (prefixes.isAssigned(candidate)) {
            throw new PatientCugPrefixException(PatientCugPrefixException.OCCUPIED);
        }
        prefixes.register(candidate, actorId);
        // Do not move the cursor to an arbitrary manual choice. The allocator
        // skips its occupied range when it reaches that prefix.
        return candidate;
    }

    @Override
    public boolean releaseIfUnused(long prefix) {
        if (prefix < 1 || prefix > MAX_PREFIX)
            throw new IllegalArgumentException("cug.prefix.error.invalid");
        prefixes.lockCounter();
        if (prefixes.isPrefixReferenced(prefix, new Timestamp(System.currentTimeMillis())))
            return false;
        prefixes.releasePrefix(prefix);
        return true;
    }

    @Override
    public boolean isAssigned(long prefix) {
        return prefixes.isAssigned(prefix);
    }

    @Override
    public void register(long prefix, String actorId) {
        prefixes.lockCounter();
        prefixes.register(prefix, actorId);
    }

    private long nextFree(long candidate) {
        Long end = prefixes.occupiedRangeEnd(candidate);
        if (end == null || end < candidate)
            return candidate;
        // Ranges are disjoint and adjacent ranges are coalesced by the database.
        return end >= MAX_PREFIX ? Long.MAX_VALUE : end + 1;
    }

    private CugPrefixSettingsForm toForm(SampleCugCounter counter) {
        CugPrefixSettingsForm form = new CugPrefixSettingsForm();
        form.setStartPrefix(counter.getConfiguredStart().toString());
        long next = nextFree(counter.getNextCandidate());
        form.setNextAvailablePrefix(next <= MAX_PREFIX ? Long.toString(next) : null);
        return form;
    }

    private long parseStart(String raw) {
        try {
            if (raw == null || !raw.matches("[0-9]+"))
                throw new NumberFormatException();
            long value = Long.parseLong(raw);
            if (value < 1 || value > MAX_PREFIX)
                throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("cug.prefix.error.invalid");
        }
    }
}
