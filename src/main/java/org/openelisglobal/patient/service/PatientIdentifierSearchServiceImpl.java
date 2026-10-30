package org.openelisglobal.patient.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.provider.query.PatientSearchResults;
import org.openelisglobal.observationhistory.service.ObservationHistoryService;
import org.openelisglobal.observationhistory.service.ObservationHistoryServiceImpl.ObservationType;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.sample.dao.SampleCugPrefixDAO;
import org.openelisglobal.sample.service.CugPrefixServiceImpl;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PatientIdentifierSearchServiceImpl implements PatientIdentifierSearchService {
    @Autowired
    private SampleCugPrefixDAO patientPrefixes;
    @Autowired
    private PatientService patients;
    @Autowired
    private PersonService persons;
    @Autowired
    private ObservationHistoryService observations;
    @Autowired
    private OrderAdditionalFieldService orderFields;
    @Autowired
    private SampleService samples;
    @Autowired
    private SampleHumanService sampleHumans;

    @Override
    public List<PatientSearchResults> findByCugPrefixOrOrderNumber(String rawValue) {
        String value = StringUtils.trimToNull(rawValue);
        if (value == null)
            return List.of();

        Long prefix = parsePrefix(value);
        if (prefix != null) {
            List<PatientSearchResults> results = new ArrayList<>();
            for (Integer patientId : patientPrefixes.findPatientIdsByPrefix(prefix)) {
                Patient patient = patients.getData(patientId.toString());
                if (patient != null && StringUtils.isNotBlank(patient.getId())) {
                    results.add(toSearchResult(patient));
                }
            }
            if (!results.isEmpty())
                return results;
        }

        // Preserve order numbers, configured searchable order fields and legacy
        // complete sample CUGs when there is no assigned patient prefix match.
        Sample sample = orderFields.findSampleIdBySearchableFieldValue(value).map(id -> samples.get(id.toString()))
                .orElse(null);
        if (sample == null) {
            sample = samples.getSampleByAccessionNumber(value);
            if (sample == null && value.contains("-")) {
                sample = samples.getSampleByAccessionNumber(value.substring(0, value.indexOf('-')));
            }
        }
        if (sample == null || StringUtils.isBlank(sample.getId()))
            return List.of();
        Patient patient = sampleHumans.getPatientForSample(sample);
        return patient == null || StringUtils.isBlank(patient.getId()) ? List.of() : List.of(toSearchResult(patient));
    }

    private Long parsePrefix(String value) {
        if (!value.matches("[0-9]+"))
            return null;
        try {
            long prefix = Long.parseLong(value);
            return prefix > 0 && prefix <= CugPrefixServiceImpl.MAX_PREFIX ? prefix : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private PatientSearchResults toSearchResult(Patient patient) {
        persons.getData(patient.getPerson());
        return new PatientSearchResults(new BigDecimal(patient.getId()), patients.getFirstName(patient),
                patients.getLastName(patient), patients.getGender(patient), patients.getEnteredDOB(patient),
                patients.getNationalId(patient), patient.getExternalId(), patients.getSTNumber(patient),
                patients.getSubjectNumber(patient), patients.getGUID(patient),
                observations.getMostRecentValueForPatient(ObservationType.REFERRERS_PATIENT_ID, patient.getId()));
    }
}
