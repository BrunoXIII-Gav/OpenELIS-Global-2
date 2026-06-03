package org.openelisglobal.testconfiguration.service;

import java.util.List;

public interface SampleTypeUomAssignService {
    void replaceAssignments(String sampleTypeId, List<String> unitOfMeasureIds, String systemUserId);
}
