package org.openelisglobal.patient.service;

import java.util.List;
import org.openelisglobal.common.provider.query.PatientSearchResults;

public interface PatientIdentifierSearchService {
    List<PatientSearchResults> findByCugPrefixOrOrderNumber(String searchValue);
}
