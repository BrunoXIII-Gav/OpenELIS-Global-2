package org.openelisglobal.patient.merge.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.person.valueholder.Person;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for consolidating clinical and demographic data during patient merge.
 * Implements FR-004 (FK updates) and FR-009 (demographic conflict resolution).
 * 
 * Uses Hibernate batching while preserving entity lifecycle audit events.
 */
@Service
@Transactional
public class PatientMergeConsolidationService {

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * FR-004: Reassigns all clinical data from merged patient to primary patient.
     * Updates managed sample, contact, electronic-order and relation records in the
     * enclosing transaction, preserving their previous values in the audit ledger.
     *
     * @param primaryPatientId the patient that will keep all data
     * @param mergedPatientId  the patient being merged (data moves FROM here)
     * @param sysUserId        the user performing the action (for audit)
     * @return ConsolidationResult with counts of updated records
     */
    public ConsolidationResult consolidateClinicalData(String primaryPatientId, String mergedPatientId,
            String sysUserId) {
        ConsolidationResult result = new ConsolidationResult();

        // 1. Batch update sample_human records
        int samplesUpdated = bulkUpdateSampleHuman(primaryPatientId, mergedPatientId, sysUserId);
        result.setSamplesReassigned(samplesUpdated);
        LogEvent.logInfo(this.getClass().getName(), "consolidateClinicalData", "Batch reassigned " + samplesUpdated
                + " sample_human records from patient " + mergedPatientId + " to " + primaryPatientId);

        // Note: Patient identities are NOT reassigned - merged patient keeps their own
        // IDs
        // This is per OpenELIS design: system doesn't support multiple identities of
        // same type

        // 2. Batch update patient_contact records
        int contactsUpdated = bulkUpdatePatientContact(primaryPatientId, mergedPatientId, sysUserId);
        result.setContactsReassigned(contactsUpdated);
        LogEvent.logInfo(this.getClass().getName(), "consolidateClinicalData", "Batch reassigned " + contactsUpdated
                + " patient_contact records from patient " + mergedPatientId + " to " + primaryPatientId);

        // 3. Batch update electronic_order records
        int ordersUpdated = bulkUpdateElectronicOrder(primaryPatientId, mergedPatientId, sysUserId);
        result.setOrdersReassigned(ordersUpdated);
        LogEvent.logInfo(this.getClass().getName(), "consolidateClinicalData", "Batch reassigned " + ordersUpdated
                + " electronic_order records from patient " + mergedPatientId + " to " + primaryPatientId);

        // 4. Batch update patient_relations records (both patientId and
        // patientIdSource)
        int relationsUpdated = bulkUpdatePatientRelations(primaryPatientId, mergedPatientId, sysUserId);
        result.setRelationsReassigned(relationsUpdated);
        LogEvent.logInfo(this.getClass().getName(), "consolidateClinicalData", "Batch reassigned " + relationsUpdated
                + " patient_relations records from patient " + mergedPatientId + " to " + primaryPatientId);

        return result;
    }

    /**
     * FR-009: Merges demographic data from merged patient to primary patient.
     * Primary patient values take precedence - merged patient data fills gaps.
     *
     * @param primaryPatient the patient that will keep all data
     * @param mergedPatient  the patient being merged
     * @return list of fields that were filled from merged patient
     */
    public List<String> mergeDemographics(Patient primaryPatient, Patient mergedPatient) {
        List<String> mergedFields = new ArrayList<>();

        Person primaryPerson = primaryPatient.getPerson();
        Person mergedPerson = mergedPatient.getPerson();

        if (primaryPerson == null || mergedPerson == null) {
            return mergedFields;
        }

        // NOTE: Names (firstName, lastName, middleName) are intentionally NOT merged.
        // Names are core patient identifiers - the user selected the primary patient,
        // so that patient's name is kept. Per FR-009, only non-identifying demographics
        // (phone, email, address) are merged to fill gaps.

        // Fill empty primary fields with merged patient's data
        if (isEmpty(primaryPerson.getStreetAddress()) && !isEmpty(mergedPerson.getStreetAddress())) {
            primaryPerson.setStreetAddress(mergedPerson.getStreetAddress());
            mergedFields.add("streetAddress");
        }

        if (isEmpty(primaryPerson.getCity()) && !isEmpty(mergedPerson.getCity())) {
            primaryPerson.setCity(mergedPerson.getCity());
            mergedFields.add("city");
        }

        if (isEmpty(primaryPerson.getState()) && !isEmpty(mergedPerson.getState())) {
            primaryPerson.setState(mergedPerson.getState());
            mergedFields.add("state");
        }

        if (isEmpty(primaryPerson.getZipCode()) && !isEmpty(mergedPerson.getZipCode())) {
            primaryPerson.setZipCode(mergedPerson.getZipCode());
            mergedFields.add("zipCode");
        }

        if (isEmpty(primaryPerson.getCountry()) && !isEmpty(mergedPerson.getCountry())) {
            primaryPerson.setCountry(mergedPerson.getCountry());
            mergedFields.add("country");
        }

        if (isEmpty(primaryPerson.getPrimaryPhone()) && !isEmpty(mergedPerson.getPrimaryPhone())) {
            primaryPerson.setPrimaryPhone(mergedPerson.getPrimaryPhone());
            mergedFields.add("primaryPhone");
        }

        if (isEmpty(primaryPerson.getWorkPhone()) && !isEmpty(mergedPerson.getWorkPhone())) {
            primaryPerson.setWorkPhone(mergedPerson.getWorkPhone());
            mergedFields.add("workPhone");
        }

        if (isEmpty(primaryPerson.getCellPhone()) && !isEmpty(mergedPerson.getCellPhone())) {
            primaryPerson.setCellPhone(mergedPerson.getCellPhone());
            mergedFields.add("cellPhone");
        }

        if (isEmpty(primaryPerson.getFax()) && !isEmpty(mergedPerson.getFax())) {
            primaryPerson.setFax(mergedPerson.getFax());
            mergedFields.add("fax");
        }

        if (isEmpty(primaryPerson.getEmail()) && !isEmpty(mergedPerson.getEmail())) {
            primaryPerson.setEmail(mergedPerson.getEmail());
            mergedFields.add("email");
        }

        if (!mergedFields.isEmpty()) {
            LogEvent.logInfo(this.getClass().getName(), "mergeDemographics",
                    "Merged " + mergedFields.size() + " demographic fields from patient " + mergedPatient.getId()
                            + " to primary patient " + primaryPatient.getId() + ": " + mergedFields);
        }

        return mergedFields;
    }

    /**
     * Reassign managed entities so optimistic versions and per-field audit events
     * are preserved.
     */
    private int bulkUpdateSampleHuman(String primaryPatientId, String mergedPatientId, String sysUserId) {
        var rows = org.openelisglobal.common.util.NumericStringIdQuery
                .bind(entityManager.createQuery("FROM SampleHuman h WHERE h.patientId = :id",
                        org.openelisglobal.samplehuman.valueholder.SampleHuman.class), "id", mergedPatientId)
                .getResultList();
        rows.forEach(row -> {
            row.setPatientId(primaryPatientId);
            row.setSysUserId(sysUserId);
        });
        entityManager.flush();
        return rows.size();
    }

    private int bulkUpdatePatientContact(String primaryPatientId, String mergedPatientId, String sysUserId) {
        var rows = org.openelisglobal.common.util.NumericStringIdQuery
                .bind(entityManager.createQuery("FROM PatientContact c WHERE c.patientId = :id",
                        org.openelisglobal.patient.valueholder.PatientContact.class), "id", mergedPatientId)
                .getResultList();
        rows.forEach(row -> {
            row.setPatientId(primaryPatientId);
            row.setSysUserId(sysUserId);
        });
        entityManager.flush();
        return rows.size();
    }

    private int bulkUpdateElectronicOrder(String primaryPatientId, String mergedPatientId, String sysUserId) {
        var rows = org.openelisglobal.common.util.NumericStringIdQuery
                .bind(entityManager.createQuery("FROM ElectronicOrder o WHERE o.patient.id = :id",
                        org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder.class), "id", mergedPatientId)
                .getResultList();
        Patient primary = entityManager.getReference(Patient.class, primaryPatientId);
        rows.forEach(row -> {
            row.setPatient(primary);
            row.setSysUserId(sysUserId);
        });
        entityManager.flush();
        return rows.size();
    }

    private int bulkUpdatePatientRelations(String primaryPatientId, String mergedPatientId, String sysUserId) {
        var rows = org.openelisglobal.common.util.NumericStringIdQuery
                .bind(entityManager.createQuery(
                        "FROM PatientRelation r WHERE r.patientId = :id OR r.patientIdSource = :id",
                        org.openelisglobal.patientrelation.valueholder.PatientRelation.class), "id", mergedPatientId)
                .getResultList();
        int count = 0;
        for (var row : rows) {
            if (mergedPatientId.equals(row.getPatientId())) {
                row.setPatientId(primaryPatientId);
                count++;
            }
            if (mergedPatientId.equals(row.getPatientIdSource())) {
                row.setPatientIdSource(primaryPatientId);
                count++;
            }
            row.setSysUserId(sysUserId);
        }
        entityManager.flush();
        return count;
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Result object containing counts of reassigned records.
     */
    public static class ConsolidationResult {
        private int samplesReassigned;
        private int contactsReassigned;
        private int ordersReassigned;
        private int relationsReassigned;

        public int getSamplesReassigned() {
            return samplesReassigned;
        }

        public void setSamplesReassigned(int samplesReassigned) {
            this.samplesReassigned = samplesReassigned;
        }

        public int getContactsReassigned() {
            return contactsReassigned;
        }

        public void setContactsReassigned(int contactsReassigned) {
            this.contactsReassigned = contactsReassigned;
        }

        public int getOrdersReassigned() {
            return ordersReassigned;
        }

        public void setOrdersReassigned(int ordersReassigned) {
            this.ordersReassigned = ordersReassigned;
        }

        public int getRelationsReassigned() {
            return relationsReassigned;
        }

        public void setRelationsReassigned(int relationsReassigned) {
            this.relationsReassigned = relationsReassigned;
        }

        public int getTotalReassigned() {
            return samplesReassigned + contactsReassigned + ordersReassigned + relationsReassigned;
        }
    }
}
