package org.openelisglobal.testadditionalfield.service;

import java.util.List;
import java.util.Map;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldOptionPayload;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;

public interface TestAdditionalFieldService {

    List<TestAdditionalFieldPayload> getFieldsForTest(String testId, boolean includeInactive);

    Map<String, List<TestAdditionalFieldPayload>> getActiveFieldsForTests(List<String> testIds);

    void replaceFieldsForTest(String testId, List<TestAdditionalFieldPayload> payloads, String currentUserId);

    void replaceFieldsForTests(List<String> testIds, List<TestAdditionalFieldPayload> payloads, String currentUserId);

    Map<String, String> getAnalysisValuesForFields(String analysisId,
            List<TestAdditionalFieldPayload> fieldDefinitions);

    void validateAndPersistAnalysisValues(String testId, String analysisId, Map<String, String> fieldValues,
            String currentUserId, Map<String, List<TestAdditionalFieldPayload>> activeFieldsByTestCache,
            boolean enforceRequired);

    TestAdditionalFieldPayload createField(TestAdditionalFieldPayload payload, String currentUserId);

    TestAdditionalFieldPayload updateField(Integer fieldId, TestAdditionalFieldPayload payload, String currentUserId);

    void deactivateField(Integer fieldId, String currentUserId);

    TestAdditionalFieldOptionPayload createOption(Integer fieldId, TestAdditionalFieldOptionPayload payload,
            String currentUserId);

    TestAdditionalFieldOptionPayload updateOption(Integer optionId, TestAdditionalFieldOptionPayload payload,
            String currentUserId);

    void deactivateOption(Integer optionId, String currentUserId);
}
