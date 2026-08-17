/**
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.mozilla.org/MPL/
 *
 * <p>Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * <p>The Original Code is OpenELIS code.
 *
 * <p>Copyright (C) The Minnesota Department of Health. All Rights Reserved.
 *
 * <p>Contributor(s): CIRG, University of Washington, Seattle WA.
 */
package org.openelisglobal.result.action.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.service.AnalysisTubeLabelService;
import org.openelisglobal.analysis.service.AnalysisTubeUsageService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.analysis.valueholder.AnalysisTubeLabel;
import org.openelisglobal.analysis.valueholder.AnalysisTubeUsage;
import org.openelisglobal.analysis.valueholder.ResultFile;
import org.openelisglobal.analyte.service.AnalyteService;
import org.openelisglobal.analyte.valueholder.Analyte;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.formfields.FormFields;
import org.openelisglobal.common.formfields.FormFields.Field;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.QAService;
import org.openelisglobal.common.services.QAService.QAObservationType;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.StatusService.OrderStatus;
import org.openelisglobal.common.services.TestIdentityService;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.dictionary.service.DictionaryService;
import org.openelisglobal.dictionary.valueholder.Dictionary;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.localization.service.LocalizationService;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.method.service.MethodService;
import org.openelisglobal.method.valueholder.Method;
import org.openelisglobal.note.service.NoteService;
import org.openelisglobal.note.service.NoteServiceImpl.NoteType;
import org.openelisglobal.observationhistory.service.ObservationHistoryService;
import org.openelisglobal.observationhistory.valueholder.ObservationHistory;
import org.openelisglobal.observationhistory.valueholder.ObservationHistory.ValueType;
import org.openelisglobal.patient.form.PatientInfoForm;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.util.PatientUtil;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.patientidentity.valueholder.PatientIdentity;
import org.openelisglobal.patientidentitytype.util.PatientIdentityTypeMap;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.referral.service.ReferralService;
import org.openelisglobal.referral.valueholder.Referral;
import org.openelisglobal.result.service.ResultInventoryService;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.service.ResultSignatureService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.result.valueholder.ResultInventory;
import org.openelisglobal.result.valueholder.ResultSignature;
import org.openelisglobal.resultlimit.service.ResultLimitService;
import org.openelisglobal.resultlimits.valueholder.ResultLimit;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.service.SampleItemService;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.sampleqaevent.service.SampleQaEventService;
import org.openelisglobal.sampleqaevent.valueholder.SampleQaEvent;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.statusofsample.util.StatusRules;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.test.beanItems.BlockSampleUsageItem;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.openelisglobal.test.beanItems.TestResultItem.ResultDisplayType;
import org.openelisglobal.test.service.TbMethodTestService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.TbMethodTest;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.service.TestAdditionalFieldService;
import org.openelisglobal.testdependency.service.TestParentChildDependencyService;
import org.openelisglobal.testdependency.valueholder.TestParentChildDependency;
import org.openelisglobal.testreflex.action.util.TestReflexUtil;
import org.openelisglobal.testreflex.valueholder.TestReflex;
import org.openelisglobal.testresult.service.TestResultService;
import org.openelisglobal.testresult.valueholder.TestResult;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeoftestresult.service.TypeOfTestResultServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

@Service
@Scope("prototype")
public class ResultsLoadUtility {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final boolean SORT_FORWARD = true;

    public static final String TESTKIT = "TestKit";

    private static final String NO_PATIENT_NAME = " ";
    private static final String NO_PATIENT_INFO = " ";

    private List<Sample> samples;
    private String currentDate = "";
    private Sample currSample;

    private Set<Integer> excludedAnalysisStatus = new HashSet<>();
    private List<Integer> analysisStatusList = new ArrayList<>();
    private List<Integer> sampleStatusList = new ArrayList<>();

    // TODO: Re-enable after new inventory frontend integration
    // private List<InventoryKitItem> activeKits;

    private Patient currentPatient;

    @Autowired
    private PatientService patientService;
    @Autowired
    private ResultService resultService;
    @Autowired
    private DictionaryService dictionaryService;
    @Autowired
    private LocalizationService localizationService;
    @Autowired
    private ResultSignatureService resultSignatureService;
    @Autowired
    private ResultInventoryService resultInventoryService;
    @Autowired
    private ObservationHistoryService observationHistoryService;
    @Autowired
    private AnalysisService analysisService;
    @Autowired
    private ReferralService referralService;
    @Autowired
    private AnalyteService analyteService;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private SampleHumanService sampleHumanService;
    @Autowired
    private TestService testService;
    @Autowired
    private SampleItemService sampleItemService;
    @Autowired
    private SampleQaEventService sampleQaEventService;
    @Autowired
    private TestResultService testResultService;
    @Autowired
    private TestAdditionalFieldService testAdditionalFieldService;
    @Autowired
    private TestParentChildDependencyService testParentChildDependencyService;
    @Autowired
    private TbMethodTestService tbMethodTestService;
    @Autowired
    private MethodService methodService;
    @Autowired
    private AnalysisTubeUsageService analysisTubeUsageService;
    @Autowired
    private AnalysisTubeLabelService analysisTubeLabelService;

    private final StatusRules statusRules = new StatusRules();

    private boolean inventoryNeeded = false;

    private String ANALYTE_CONCLUSION_ID;
    private String ANALYTE_CD4_CNT_CONCLUSION_ID;
    private static final String NUMERIC_RESULT_TYPE = "N";
    private static boolean depersonalize = FormFields.getInstance().useField(Field.DepersonalizedResults);
    private boolean useTechSignature = ConfigurationProperties.getInstance()
            .isPropertyValueEqual(Property.resultTechnicianName, "true");
    private static boolean supportReferrals = FormFields.getInstance().useField(Field.ResultsReferral);
    private static boolean useInitialSampleCondition = FormFields.getInstance().useField(Field.InitialSampleCondition);
    private boolean useCurrentUserAsTechDefault = ConfigurationProperties.getInstance()
            .isPropertyValueEqual(Property.autoFillTechNameUser, "true");
    private String currentUserName = "";
    private int reflexGroup = 1;
    private boolean lockCurrentResults = false;
    private final Map<String, List<TestAdditionalFieldPayload>> additionalFieldDefinitionCache = new HashMap<>();
    private final Map<String, List<IdValuePair>> methodOptionsByTestIdCache = new HashMap<>();
    private final Map<String, String> methodLabelByIdCache = new HashMap<>();
    private final Map<String, TestParentChildDependency> dependencyByChildTestId = new HashMap<>();
    private final Map<String, Boolean> activeDependencyParentByTestId = new HashMap<>();
    private final Map<String, Analysis> parentAnalysisBySampleAndTest = new HashMap<>();
    private final Map<String, List<Analysis>> analysesBySampleItemIdCache = new HashMap<>();
    private final Map<String, BigDecimal> parentFieldNumericValueCache = new HashMap<>();
    private final Map<String, Map<String, TubeBlockContext>> parentTubeContextCache = new HashMap<>();
    private final Map<String, List<AnalysisTubeUsage>> tubeUsageByAnalysisIdCache = new HashMap<>();
    private final Map<String, List<AnalysisTubeUsage>> tubeUsageByParentAnalysisIdCache = new HashMap<>();
    private final Map<String, Map<String, AnalysisTubeLabel>> tubeLabelByAnalysisIdCache = new HashMap<>();

    @PostConstruct
    public void initializeGlobalVariables() {
        Analyte analyte = new Analyte();
        analyte.setAnalyteName("Conclusion");
        analyte = analyteService.getAnalyteByName(analyte, false);
        ANALYTE_CONCLUSION_ID = analyte == null ? "" : analyte.getId();
        analyte = new Analyte();
        analyte.setAnalyteName("generated CD4 Count");
        analyte = analyteService.getAnalyteByName(analyte, false);
        ANALYTE_CD4_CNT_CONCLUSION_ID = analyte == null ? "" : analyte.getId();
    }

    public void setSysUser(String currentUserId) {
        if (useCurrentUserAsTechDefault) {
            SystemUser systemUser = new SystemUser();
            systemUser.setId(currentUserId);
            systemUserService.getData(systemUser);

            if (systemUser.getId() != null) {
                currentUserName = systemUser.getFirstName() + " " + systemUser.getLastName();
            }
        }
    }

    /*
     * N.B. The patient info is used to determine the limits for the results, not
     * for including patient information
     */
    public List<TestResultItem> getGroupedTestsForSample(Sample sample) {
        return getGroupedTestsForSample(sample, sampleHumanService.getPatientForSample(sample));
    }

    /*
     * N.B. The patient info is used to determine the limits for the results, not
     * for including patient information
     */
    public List<TestResultItem> getGroupedTestsForSample(Sample sample, Patient patient) {

        reflexGroup = 1;
        additionalFieldDefinitionCache.clear();
        methodOptionsByTestIdCache.clear();
        methodLabelByIdCache.clear();
        clearDependencyCaches();
        // TODO: Re-enable after new inventory frontend integration
        // activeKits = null;
        samples = new ArrayList<>();

        if (sample != null) {
            samples.add(sample);
        }

        currentPatient = patient;
        if (patient != null && patient.getPerson() != null) {
            PersonService personService = SpringContext.getBean(PersonService.class);
            personService.getData(patient.getPerson());
        }

        return getGroupedTestsForSamples();
    }

    public List<TestResultItem> getGroupedTestsForPatient(Patient patient) {
        reflexGroup = 1;
        additionalFieldDefinitionCache.clear();
        methodOptionsByTestIdCache.clear();
        methodLabelByIdCache.clear();
        clearDependencyCaches();
        // TODO: Re-enable after new inventory frontend integration
        // activeKits = null;
        inventoryNeeded = false;

        currentPatient = patient;
        PersonService personService = SpringContext.getBean(PersonService.class);
        personService.getData(patient.getPerson());

        samples = sampleHumanService.getSamplesForPatient(patient.getId());

        return getGroupedTestsForSamples();
    }

    public void addIdentifingPatientInfo(Patient patient, PatientInfoForm form) {

        if (patient == null) {
            return;
        }

        PatientIdentityTypeMap identityMap = PatientIdentityTypeMap.getInstance();
        List<PatientIdentity> identityList = PatientUtil.getIdentityListForPatient(patient);

        if (!depersonalize) {
            form.setFirstName(patient.getPerson().getFirstName());
            form.setLastName(patient.getPerson().getLastName());
            form.setDob(patient.getBirthDateForDisplay());
            form.setGender(patient.getGender());
        }

        form.setSt(identityMap.getIdentityValue(identityList, "ST"));
        form.setNationalId(GenericValidator.isBlankOrNull(patient.getNationalId()) ? patient.getExternalId()
                : patient.getNationalId());
        form.setSubjectNumber(patientService.getSubjectNumber(patient));
    }

    public List<TestResultItem> getUnfinishedTestResultItemsInTestSection(String testSectionId) {

        List<Analysis> fullAnalysisList = analysisService.getAllAnalysisByTestSectionAndStatus(testSectionId,
                analysisStatusList, sampleStatusList);
        // request.setAttribute("analysisesSize", fullAnalysisList.size());
        // List<Analysis> analysisList =
        // analysisService.getPageAnalysisByTestSectionAndStatus(testSectionId,
        // analysisStatusList, sampleStatusList);

        return getGroupedTestsForAnalysisList(fullAnalysisList, SORT_FORWARD);
    }

    public int getTotalCountAnalysisByTestSectionAndStatus(String testSectionId) {
        return analysisService.getCountAnalysisByTestSectionAndStatus(testSectionId, analysisStatusList,
                sampleStatusList);
    }

    public List<TestResultItem> getGroupedTestsForAnalysisList(List<Analysis> filteredAnalysisList, boolean forwardSort)
            throws LIMSRuntimeException {

        // TODO: Re-enable after new inventory frontend integration
        // activeKits = null;
        inventoryNeeded = false;
        reflexGroup = 1;
        additionalFieldDefinitionCache.clear();
        methodOptionsByTestIdCache.clear();
        methodLabelByIdCache.clear();
        clearDependencyCaches();

        List<TestResultItem> selectedTestList = new ArrayList<>();

        for (Analysis analysis : filteredAnalysisList) {
            patientService = SpringContext.getBean(PatientService.class);
            SampleService sampleService = SpringContext.getBean(SampleService.class);
            Sample sample = analysis.getSampleItem().getSample();
            currentPatient = sampleService.getPatient(sample);

            String patientName = "";
            String patientInfo;
            String nationalId = patientService.getNationalId(currentPatient);
            if (depersonalize) {
                patientInfo = GenericValidator.isBlankOrNull(nationalId) ? patientService.getExternalId(currentPatient)
                        : nationalId;
            } else {
                patientName = patientService.getLastFirstName(currentPatient);
                patientInfo = nationalId + ", " + patientService.getGender(currentPatient) + ", "
                        + patientService.getBirthdayForDisplay(currentPatient);
            }

            currSample = analysis.getSampleItem().getSample();
            List<TestResultItem> testResultItemList = getTestResultItemFromAnalysis(analysis, patientName, patientInfo,
                    nationalId);

            for (TestResultItem selectionItem : testResultItemList) {
                selectedTestList.add(selectionItem);
            }
        }

        if (forwardSort) {
            sortByAccessionAndSequence(selectedTestList);
        } else {
            reverseSortByAccessionAndSequence(selectedTestList);
        }

        setSampleGroupingNumbers(selectedTestList);
        addUserSelectionReflexes(selectedTestList);

        return selectedTestList;
    }

    private void reverseSortByAccessionAndSequence(List<? extends ResultItem> selectedTest) {
        Collections.sort(selectedTest, new Comparator<ResultItem>() {
            @Override
            public int compare(ResultItem a, ResultItem b) {
                int accessionSort = b.getSequenceAccessionNumber().compareTo(a.getSequenceAccessionNumber());

                if (accessionSort == 0) { // only the accession number sorting is reversed
                    if (!GenericValidator.isBlankOrNull(a.getTestSortOrder())
                            && !GenericValidator.isBlankOrNull(b.getTestSortOrder())) {
                        try {
                            return Integer.parseInt(a.getTestSortOrder()) - Integer.parseInt(b.getTestSortOrder());
                        } catch (NumberFormatException e) {
                            return a.getTestName().compareTo(b.getTestName());
                        }

                    } else {
                        return a.getTestName().compareTo(b.getTestName());
                    }
                }

                return accessionSort;
            }
        });
    }

    public void sortByAccessionAndSequence(List<? extends ResultItem> selectedTest) {
        Collections.sort(selectedTest, new Comparator<ResultItem>() {
            @Override
            public int compare(ResultItem a, ResultItem b) {
                int accessionSort = a.getSequenceAccessionNumber().compareTo(b.getSequenceAccessionNumber());

                if (accessionSort == 0) {
                    if (!GenericValidator.isBlankOrNull(a.getTestSortOrder())
                            && !GenericValidator.isBlankOrNull(b.getTestSortOrder())) {
                        try {
                            return Integer.parseInt(a.getTestSortOrder()) - Integer.parseInt(b.getTestSortOrder());
                        } catch (NumberFormatException e) {
                            return a.getTestName().compareTo(b.getTestName());
                        }

                    } else if (!GenericValidator.isBlankOrNull(a.getTestName())
                            && !GenericValidator.isBlankOrNull(b.getTestName())) {
                        return a.getTestName().compareTo(b.getTestName());
                    }
                }

                return accessionSort;
            }
        });
    }

    public void setSampleGroupingNumbers(List<? extends ResultItem> selectedTests) {
        int groupingNumber = 1; // the header is always going to be 0

        String currentSequenceAccession = "";

        for (ResultItem item : selectedTests) {
            if (!currentSequenceAccession.equals(item.getSequenceAccessionNumber()) || item.getIsGroupSeparator()) {
                groupingNumber++;
                currentSequenceAccession = item.getSequenceAccessionNumber();
                item.setShowSampleDetails(true);
            } else {
                item.setShowSampleDetails(false);
            }

            item.setSampleGroupingNumber(groupingNumber);
        }
    }

    @SuppressWarnings("unchecked")
    public List<Test> getTestsInSection(String id) {

        return testService.getTestsByTestSection(id);
    }

    private List<TestResultItem> getTestResultItemFromAnalysis(Analysis analysis, String patientName,
            String patientInfo, String nationalId) throws LIMSRuntimeException {
        List<TestResultItem> testResultList = new ArrayList<>();
        DependencyContext dependencyContext = resolveDependencyContext(analysis);
        if (dependencyContext.isDependentChild && !dependencyContext.parentCompleted) {
            return testResultList;
        }

        SampleItem sampleItem = analysis.getSampleItem();
        List<Result> resultList = resultService.getResultsByAnalysis(analysis);

        ResultInventory testKit = null;

        String techSignature = "";
        String techSignatureId = "";

        if (resultList == null) {
            return testResultList;
        }

        // For historical reasons we add a null member to the collection if it
        // is empty
        // this should be refactored.
        // The result list are results associated with the analysis, if there is
        // none we want
        // to present the user with a blank one
        if (resultList.isEmpty()) {
            resultList.add(null);
        }

        boolean multiSelectionResult = false;
        for (Result result : resultList) {
            // If the parentResult has a value then this result was handled with
            // the parent
            if (result != null && result.getParentResult() != null) {
                continue;
            }

            if (result != null) {
                if (useTechSignature) {
                    List<ResultSignature> signatures = resultSignatureService.getResultSignaturesByResults(resultList);

                    for (ResultSignature signature : signatures) {
                        // we no longer use supervisor signature but there may be some in db
                        if (!signature.getIsSupervisor()) {
                            techSignature = signature.getNonUserName();
                            techSignatureId = signature.getId();
                        }
                    }
                }

                testKit = getInventoryForResult(result);

                multiSelectionResult = TypeOfTestResultServiceImpl.ResultType
                        .isMultiSelectVariant(result.getResultType());
            }

            String initialConditions = getInitialSampleConditionString(sampleItem);
            NoteType[] noteTypes = { NoteType.EXTERNAL, NoteType.INTERNAL, NoteType.REJECTION_REASON,
                    NoteType.NON_CONFORMITY };
            NoteService noteService = SpringContext.getBean(NoteService.class);
            String notes = noteService.getNotesAsString(analysis, true, true, "<br/>", noteTypes, false);

            TestResultItem resultItem = createTestResultItem(analysis, testKit, notes, sampleItem.getSortOrder(),
                    result, sampleItem.getSample().getAccessionNumber(), patientName, patientInfo, techSignature,
                    techSignatureId, initialConditions, SpringContext.getBean(TypeOfSampleService.class)
                            .getTypeOfSampleNameForId(sampleItem.getTypeOfSampleId()));
            applyDependencyContextToResultItem(resultItem, dependencyContext, analysis);
            applyParentSampleUsageContextToResultItem(resultItem, analysis);
            applyTubeLabelContextToResultItem(resultItem, analysis);
            resultItem.setNationalId(nationalId);
            testResultList.add(resultItem);

            if (multiSelectionResult) {
                break;
            }
        }

        return testResultList;
    }

    private String getInitialSampleConditionString(SampleItem sampleItem) {
        if (useInitialSampleCondition) {
            List<ObservationHistory> observationList = observationHistoryService
                    .getObservationHistoriesBySampleItemId(sampleItem.getId());
            StringBuilder conditions = new StringBuilder();

            for (ObservationHistory observation : observationList) {
                if (ValueType.DICTIONARY.getCode().equals(observation.getValueType())) {
                    Dictionary dictionary = dictionaryService.getDictionaryById(observation.getValue());
                    if (dictionary != null) {
                        conditions.append(dictionary.getLocalizedName());
                        conditions.append(", ");
                    }
                } else if (ValueType.LITERAL.getCode().equals(observation.getValueType())) {
                    conditions.append(observation.getValue());
                    conditions.append(", ");
                } else if (ValueType.KEY.getCode().equals(observation.getValueType())) {
                    Localization localization = localizationService.get(observation.getValue());
                    conditions.append(localization.getLocalizedValue());
                    conditions.append(", ");
                }
            }

            if (conditions.length() > 2) {
                return conditions.substring(0, conditions.length() - 2);
            }
        }

        return null;
    }

    private ResultInventory getInventoryForResult(Result result) throws LIMSRuntimeException {
        List<ResultInventory> inventoryList = resultInventoryService.getResultInventorysByResult(result);

        return inventoryList.size() > 0 ? inventoryList.get(0) : null;
    }

    private List<TestResultItem> getGroupedTestsForSamples() {

        List<TestResultItem> testList = new ArrayList<>();

        TestResultItem[] tests = getSortedTestsFromSamples();

        String currentAccessionNumber = "";

        for (TestResultItem testItem : tests) {
            if (!currentAccessionNumber.equals(testItem.getAccessionNumber())) {

                TestResultItem separatorItem = new TestResultItem();
                separatorItem.setIsGroupSeparator(true);
                separatorItem.setAccessionNumber(testItem.getAccessionNumber());
                separatorItem.setReceivedDate(testItem.getReceivedDate());
                testList.add(separatorItem);

                currentAccessionNumber = testItem.getAccessionNumber();
                reflexGroup++;
            }

            testList.add(testItem);
        }

        return testList;
    }

    private TestResultItem[] getSortedTestsFromSamples() {

        List<TestResultItem> testList = new ArrayList<>();

        for (Sample sample : samples) {
            currSample = sample;
            List<SampleItem> sampleItems = getSampleItemsForSample(sample);

            for (SampleItem item : sampleItems) {
                List<Analysis> analysisList = getAnalysisForSampleItem(item);

                for (Analysis analysis : analysisList) {

                    List<TestResultItem> selectedItemList = getTestResultItemFromAnalysis(analysis, NO_PATIENT_NAME,
                            NO_PATIENT_INFO, "");

                    for (TestResultItem selectedItem : selectedItemList) {
                        testList.add(selectedItem);
                    }
                }
            }
        }

        reverseSortByAccessionAndSequence(testList);
        setSampleGroupingNumbers(testList);
        addUserSelectionReflexes(testList);

        TestResultItem[] testArray = new TestResultItem[testList.size()];
        testList.toArray(testArray);

        return testArray;
    }

    private void addUserSelectionReflexes(List<TestResultItem> testList) {
        TestReflexUtil reflexUtil = new TestReflexUtil();

        Map<String, TestResultItem> groupedSibReflexMapping = new HashMap<>();

        for (TestResultItem resultItem : testList) {
            // N.B. showSampleDetails should be renamed. It means that it is the first
            // result for that group of accession numbers
            if (resultItem.isShowSampleDetails()) {
                groupedSibReflexMapping = new HashMap<>();
                reflexGroup++;
            }

            if (resultItem.isReflexGroup()) {
                resultItem.setReflexParentGroup(reflexGroup);
            }

            List<TestReflex> reflexList = reflexUtil.getPossibleUserChoiceTestReflexsForTest(resultItem.getTestId());
            resultItem.setUserChoiceReflex(reflexList.size() > 0);

            boolean possibleSibs = !groupedSibReflexMapping.isEmpty();

            for (TestReflex testReflex : reflexList) {
                if (!GenericValidator.isBlankOrNull(testReflex.getSiblingReflexId())) {
                    if (possibleSibs) {
                        TestResultItem sibTestResultItem = groupedSibReflexMapping.get(testReflex.getSiblingReflexId());
                        if (sibTestResultItem != null) {
                            Random r = new Random();
                            String key1 = Long.toString(Math.abs(r.nextLong()), 36);
                            String key2 = Long.toString(Math.abs(r.nextLong()), 36);

                            sibTestResultItem.setThisReflexKey(key1);
                            sibTestResultItem.setSiblingReflexKey(key2);

                            resultItem.setThisReflexKey(key2);
                            resultItem.setSiblingReflexKey(key1);

                            break;
                        }
                    }
                    groupedSibReflexMapping.put(testReflex.getId(), resultItem);
                }
            }
        }
    }

    private List<SampleItem> getSampleItemsForSample(Sample sample) {
        return sampleItemService.getSampleItemsBySampleId(sample.getId());
    }

    private List<Analysis> getAnalysisForSampleItem(SampleItem item) {
        return analysisService.getAnalysesBySampleItemsExcludingByStatusIds(item, excludedAnalysisStatus);
    }

    private TestResultItem createTestResultItem(Analysis analysis, ResultInventory testKit, String notes,
            String sequenceNumber, Result result, String accessionNumber, String patientName, String patientInfo,
            String techSignature, String techSignatureId, String initialSampleConditions, String sampleType) {

        TestService testService = SpringContext.getBean(TestService.class);
        Test test = analysisService.getTest(analysis);

        // Guard against null test - this can happen if analysis has null test_id or
        // test relationship isn't loaded
        if (test == null) {
            LogEvent.logError(this.getClass().getSimpleName(), "createTestResultItem",
                    "Analysis " + analysis.getId() + " has null test. Cannot create TestResultItem.");
            // Return a minimal TestResultItem with error indication
            TestResultItem errorItem = new TestResultItem();
            errorItem.setAccessionNumber(accessionNumber);
            errorItem.setAnalysisId(analysis.getId());
            errorItem.setSequenceNumber(sequenceNumber);
            errorItem.setTestName("ERROR: Test not found for analysis");
            return errorItem;
        }

        ResultLimit resultLimit = SpringContext.getBean(ResultLimitService.class).getResultLimitForTestAndPatient(test,
                currentPatient);

        String receivedDate = currSample == null ? getCurrentDate() : currSample.getReceivedDateForDisplay();
        String testMethodName = testService.getTestMethodName(test);
        List<TestResult> testResults = testService.getPossibleTestResults(test);

        String testKitId = null;
        String testKitInventoryId = null;
        Result testKitResult = new Result();
        boolean testKitInactive = false;

        if (testKit != null) {
            testKitId = testKit.getId();
            testKitInventoryId = testKit.getInventoryLocationId();
            testKitResult.setId(testKit.getResultId());
            resultService.getData(testKitResult);
            // TODO: Re-enable after new inventory frontend integration
            // testKitInactive = kitNotInActiveKitList(testKitInventoryId);
        }

        String displayTestName = analysisService.getTestDisplayName(analysis);

        boolean isConclusion = false;
        boolean isCD4Conclusion = false;

        if (result != null && result.getAnalyte() != null) {
            isConclusion = result.getAnalyte().getId().equals(ANALYTE_CONCLUSION_ID);
            isCD4Conclusion = result.getAnalyte().getId().equals(ANALYTE_CD4_CNT_CONCLUSION_ID);

            if (isConclusion) {
                displayTestName = MessageUtil.getMessage("result.conclusion");
            } else if (isCD4Conclusion) {
                displayTestName = MessageUtil.getMessage("result.conclusion.cd4");
            }
        }

        String referralId = null;
        String referralReasonId = null;
        boolean referralCanceled = false;
        if (supportReferrals) {
            Referral referral = referralService.getReferralByAnalysisId(analysis.getId());
            if (referral != null) {
                referralCanceled = referral.isCanceled();
                referralId = referral.getId();
                if (!referral.isCanceled()) {
                    referralReasonId = referral.getReferralReasonId();
                }
            }
        }

        String uom = testService.getUOM(test, isCD4Conclusion);

        String testDate = GenericValidator.isBlankOrNull(analysisService.getCompletedDateForDisplay(analysis))
                ? getCurrentDate()
                : analysisService.getCompletedDateForDisplay(analysis);
        ResultDisplayType resultDisplayType = testService.getDisplayTypeForTestMethod(test);
        if (resultDisplayType != ResultDisplayType.TEXT) {
            inventoryNeeded = true;
        }
        ResultFile file = analysis.getResultFile();

        TestResultItem.ResultFileForm form = new TestResultItem.ResultFileForm();
        if (file != null) {
            form.setFileName(file.getFileName());
            form.setFileType(file.getFileType());
            form.setContent(file.getContent());
            form.setUploadedAt(file.getUploadedAt());
            form.setLastupdated(file.getLastupdated());
        }

        TestResultItem testItem = new TestResultItem();

        testItem.setAccessionNumber(accessionNumber);
        testItem.setAnalysisId(analysis.getId());
        // Set SampleItem ID for storage location lookup
        if (analysis.getSampleItem() != null && analysis.getSampleItem().getId() != null) {
            testItem.setSampleItemId(analysis.getSampleItem().getId());
        }
        testItem.setCugCode(analysis.getSampleItem() != null ? analysis.getSampleItem().getCugCode() : null);
        testItem.setSampleItemExternalId(
                analysis.getSampleItem() != null ? analysis.getSampleItem().getExternalId() : null);
        testItem.setSequenceNumber(sequenceNumber);
        testItem.setReceivedDate(receivedDate);
        testItem.setTestName(displayTestName);
        testItem.setResultName(test.getStoredName());
        testItem.setResultDisplayConfigJson(test.getResultDisplayConfigJson());
        testItem.setTestId(test.getId());
        setResultLimitDependencies(resultLimit, testItem, testResults);
        testItem.setPatientName(patientName);
        testItem.setPatientInfo(patientInfo);
        testItem.setReportable(testService.isReportable(test));
        testItem.setUnitsOfMeasure(uom);
        testItem.setTestDate(testDate);
        testItem.setResultDisplayType(resultDisplayType);
        testItem.setAnalysisMethod(analysisService.getAnalysisType(analysis));
        testItem.setTestMethod(analysisService.getMethodId(analysis));
        testItem.setMethods(getMethodOptionsForTest(test, testItem.getTestMethod(), testMethodName));
        testItem.setResult(result);
        testItem.setResultValue(getFormattedResultValue(result));
        testItem.setMultiSelectResultValues(analysisService.getJSONMultiSelectResults(analysis));
        testItem.setAnalysisStatusId(analysisService.getStatusId(analysis));
        // setDictionaryResults must come after setResultType, it may override it
        testItem.setResultType(testService.getResultType(test));
        setDictionaryResults(testItem, isConclusion, result, testResults);
        List<TestAdditionalFieldPayload> additionalFieldDefinitions = getAdditionalFieldsForTest(test.getId());
        Map<String, String> additionalFieldValues = testAdditionalFieldService
                .getAnalysisValuesForFields(analysis.getId(), additionalFieldDefinitions);
        testItem.setAdditionalFieldDefinitions(additionalFieldDefinitions);
        testItem.setAdditionalFieldValues(new HashMap<>(additionalFieldValues));
        testItem.setAdditionalFieldShadowValues(new HashMap<>(additionalFieldValues));

        testItem.setTechnician(techSignature);
        testItem.setTechnicianSignatureId(techSignatureId);
        testItem.setTestKitId(testKitId);
        testItem.setTestKitInventoryId(testKitInventoryId);
        testItem.setTestKitInactive(testKitInactive);
        testItem.setReadOnly(isReadOnly(isConclusion, isCD4Conclusion) && result != null && result.getId() != null);
        testItem.setReferralId(referralId);
        testItem.setReferredOut(!GenericValidator.isBlankOrNull(referralId) && !referralCanceled);
        testItem.setShadowReferredOut(testItem.isReferredOut());
        testItem.setReferralReasonId(referralReasonId);
        testItem.setReferralCanceled(referralCanceled);
        testItem.setInitialSampleCondition(initialSampleConditions);
        testItem.setSampleType(sampleType);
        testItem.setTestSortOrder(testService.getSortOrder(test));
        testItem.setFailedValidation(statusRules.hasFailedValidation(analysisService.getStatusId(analysis)));
        if (useCurrentUserAsTechDefault && GenericValidator.isBlankOrNull(testItem.getTechnician())) {
            testItem.setTechnician(currentUserName);
        }
        testItem.setReflexGroup(analysisService.getTriggeredReflex(analysis));
        testItem.setChildReflex(
                analysisService.getTriggeredReflex(analysis) && analysisService.resultIsConclusion(result, analysis));
        testItem.setPastNotes(notes);
        testItem.setDisplayResultAsLog(hasLogValue(test));
        testItem.setResultFile(form);
        testItem.setNonconforming(
                analysisService.isParentNonConforming(analysis) || SpringContext.getBean(IStatusService.class)
                        .matches(analysisService.getStatusId(analysis), AnalysisStatus.TechnicalRejected));
        if (FormFields.getInstance().useField(Field.QaEventsBySection)) {
            testItem.setNonconforming(testItem.isNonconforming() || getQaEventByTestSection(analysis));
        }

        Result quantifiedResult = analysisService.getQuantifiedResult(analysis);
        if (quantifiedResult != null) {
            testItem.setQualifiedResultId(quantifiedResult.getId());
            testItem.setQualifiedResultValue(quantifiedResult.getValue());
            testItem.setHasQualifiedResult(true);
        }

        if (!testResults.isEmpty() && NUMERIC_RESULT_TYPE.equals(testResults.get(0).getTestResultType())
                && !GenericValidator.isBlankOrNull(testResults.get(0).getSignificantDigits())) {
            testItem.setSignificantDigits(Integer.parseInt(testResults.get(0).getSignificantDigits()));
        }

        if (test.getDefaultTestResult() != null) {
            testItem.setDefaultResultValue(test.getDefaultTestResult().getValue());
        }
        return testItem;
    }

    private List<TestAdditionalFieldPayload> getAdditionalFieldsForTest(String testId) {
        if (GenericValidator.isBlankOrNull(testId)) {
            return new ArrayList<>();
        }
        return additionalFieldDefinitionCache.computeIfAbsent(testId,
                ignored -> testAdditionalFieldService.getFieldsForTest(testId, false, true));
    }

    private boolean isReadOnly(boolean isConclusion, boolean isCD4Conclusion) {
        return isConclusion || isCD4Conclusion || isLockCurrentResults();
    }

    private void clearDependencyCaches() {
        dependencyByChildTestId.clear();
        activeDependencyParentByTestId.clear();
        parentAnalysisBySampleAndTest.clear();
        analysesBySampleItemIdCache.clear();
        parentFieldNumericValueCache.clear();
        parentTubeContextCache.clear();
        tubeUsageByAnalysisIdCache.clear();
        tubeUsageByParentAnalysisIdCache.clear();
        tubeLabelByAnalysisIdCache.clear();
    }

    private DependencyContext resolveDependencyContext(Analysis analysis) {
        DependencyContext context = new DependencyContext();
        if (analysis == null || analysis.getTest() == null || analysis.getSampleItem() == null) {
            return context;
        }

        String childTestId = analysis.getTest().getId();
        if (GenericValidator.isBlankOrNull(childTestId)) {
            return context;
        }

        TestParentChildDependency dependency = dependencyByChildTestId.get(childTestId);
        if (dependency == null && !dependencyByChildTestId.containsKey(childTestId)) {
            dependency = testParentChildDependencyService.getActiveByChildTestId(childTestId);
            dependencyByChildTestId.put(childTestId, dependency);
        }

        if (dependency == null || dependency.getParentTest() == null) {
            return context;
        }

        context.isDependentChild = true;
        context.parentTestId = dependency.getParentTest().getId();
        context.parentTestName = dependency.getParentTest().getLocalizedName();

        String parentLookupKey = analysis.getSampleItem().getId() + ":" + dependency.getParentTest().getId();
        Analysis parentAnalysis = parentAnalysisBySampleAndTest.get(parentLookupKey);
        if (parentAnalysis == null) {
            parentAnalysis = analysisService.getAnalysisBySampleItemAndTest(analysis.getSampleItem().getId(),
                    dependency.getParentTest().getId());
            parentAnalysisBySampleAndTest.put(parentLookupKey, parentAnalysis);
        }

        context.parentAnalysis = parentAnalysis;
        context.parentCompleted = isAnalysisCompleted(parentAnalysis);
        context.sampleUsageSource = dependency.getSampleUsageSource();
        context.parentResultFieldKey = dependency.getParentResultFieldKey();

        if (TestParentChildDependency.SAMPLE_USAGE_SOURCE_PARENT_TEST_FIELD.equals(context.sampleUsageSource)) {
            Map<String, TubeBlockContext> tubeContexts = resolveTubeBlockContexts(parentAnalysis, context.parentTestId);
            if (!tubeContexts.isEmpty()) {
                context.sampleUsageFromParentField = true;
                context.tubeBasedUsage = true;
                context.tubeBlocks = tubeContexts;
                context.selectedTubeBlockName = analysis.getParentUsageBlockName();
                BigDecimal totalRemaining = calculateTotalRemainingFromTubeBlocks(analysis, context);
                if (totalRemaining != null) {
                    context.sampleRemainingQuantity = totalRemaining.toPlainString();
                }
                if (!GenericValidator.isBlankOrNull(context.selectedTubeBlockName)) {
                    TubeBlockContext selectedContext = tubeContexts.get(context.selectedTubeBlockName);
                    if (selectedContext != null) {
                        BigDecimal parentBasedRemaining = calculateRemainingFromTubeBlock(analysis, context,
                                context.selectedTubeBlockName);
                        if (parentBasedRemaining != null) {
                            context.sampleRemainingQuantity = parentBasedRemaining.toPlainString();
                        }
                    }
                }
            } else {
                context.sampleUsageFromParentField = true;
                BigDecimal parentBasedRemaining = calculateRemainingFromParentField(analysis, context);
                if (parentBasedRemaining != null) {
                    context.sampleRemainingQuantity = parentBasedRemaining.toPlainString();
                }
            }
        }
        return context;
    }

    private boolean isAnalysisCompleted(Analysis analysis) {
        if (analysis == null || GenericValidator.isBlankOrNull(analysis.getStatusId())) {
            return false;
        }

        IStatusService statusService = SpringContext.getBean(IStatusService.class);
        return statusService.matches(analysis.getStatusId(), AnalysisStatus.Finalized)
                || statusService.matches(analysis.getStatusId(), AnalysisStatus.TechnicalAcceptance);
    }

    private void applyDependencyContextToResultItem(TestResultItem resultItem, DependencyContext context,
            Analysis analysis) {
        if (!context.isDependentChild) {
            return;
        }

        resultItem.setDependentChild(true);
        resultItem.setDependencyParentTestId(context.parentTestId);
        resultItem.setDependencyParentTestName(context.parentTestName);
        resultItem.setDependencyParentAnalysisId(context.parentAnalysis != null ? context.parentAnalysis.getId() : null);
        resultItem.setDependencyParentCompleted(context.parentCompleted);
        if (context.tubeBasedUsage) {
            Map<String, String> optionLabels = new LinkedHashMap<>();
            Map<String, String> remainingByBlock = new LinkedHashMap<>();
            for (Map.Entry<String, TubeBlockContext> entry : context.tubeBlocks.entrySet()) {
                String blockName = entry.getKey();
                TubeBlockContext tubeContext = entry.getValue();
                optionLabels.put(blockName, blockName);
                BigDecimal remaining = calculateRemainingFromTubeBlock(analysis, context, blockName);
                if (remaining != null) {
                    remainingByBlock.put(blockName, remaining.toPlainString());
                }
            }
            resultItem.setParentTubeOptions(optionLabels);
            resultItem.setParentTubeRemainingQuantities(remainingByBlock);

            List<String> childBlocks = resolveChildTubeUsageBlocks(resultItem);
            if (!childBlocks.isEmpty()) {
                resultItem.setBlockTubeUsageEnabled(true);
                resultItem.setParentTubeSelectionEnabled(false);
                resultItem.setParentUsageBlockName(null);
                Map<String, AnalysisTubeUsage> persistedByBlock = getPersistedTubeUsageByBlock(analysis.getId());
                List<BlockSampleUsageItem> blockUsages = new ArrayList<>();
                BigDecimal totalUsed = BigDecimal.ZERO;
                for (String childBlockName : childBlocks) {
                    AnalysisTubeUsage persistedUsage = persistedByBlock.get(childBlockName);
                    BlockSampleUsageItem blockUsage = new BlockSampleUsageItem();
                    blockUsage.setChildBlockName(childBlockName);
                    if (persistedUsage != null) {
                        blockUsage.setParentTubeBlockName(persistedUsage.getParentTubeBlockName());
                        blockUsage.setUsedQuantity(persistedUsage.getUsedQuantity() == null ? ""
                                : persistedUsage.getUsedQuantity().toPlainString());
                        blockUsage.setLocked(true);
                        totalUsed = totalUsed.add(persistedUsage.getUsedQuantity() == null ? BigDecimal.ZERO
                                : persistedUsage.getUsedQuantity());
                    } else {
                        blockUsage.setLocked(false);
                    }
                    BigDecimal blockRemaining = GenericValidator.isBlankOrNull(blockUsage.getParentTubeBlockName()) ? null
                            : calculateRemainingFromTubeBlock(analysis, context, blockUsage.getParentTubeBlockName());
                    if (blockRemaining != null) {
                        blockUsage.setRemainingQuantity(blockRemaining.toPlainString());
                    }
                    blockUsages.add(blockUsage);
                }
                resultItem.setBlockSampleUsages(blockUsages);
                resultItem.setSampleUsageLocked(false);
                resultItem.setSampleRemainingQuantity(context.sampleRemainingQuantity);
                if (!blockUsages.isEmpty()) {
                    resultItem.setSampleUsageQuantity(totalUsed.compareTo(BigDecimal.ZERO) > 0
                            ? totalUsed.toPlainString()
                            : resultItem.getSampleUsageQuantity());
                    return;
                }
            }

            resultItem.setParentTubeSelectionEnabled(true);
            resultItem.setParentUsageBlockName(context.selectedTubeBlockName);
        }

        if (context.sampleUsageFromParentField) {
            resultItem.setSampleRemainingQuantity(context.sampleRemainingQuantity);
        } else {
            BigDecimal remaining = analysis.getSampleItem().getEffectiveRemainingQuantity();
            if (remaining != null) {
                resultItem.setSampleRemainingQuantity(remaining.toPlainString());
            }
        }

        if (analysis.getSampleUsedQuantity() != null) {
            resultItem.setSampleUsageQuantity(analysis.getSampleUsedQuantity().toPlainString());
            resultItem.setSampleUsageLocked(true);
        } else {
            resultItem.setSampleUsageLocked(false);
        }
    }

    private void applyParentSampleUsageContextToResultItem(TestResultItem resultItem, Analysis analysis) {
        if (resultItem == null || analysis == null || analysis.getTest() == null || analysis.getSampleItem() == null) {
            return;
        }

        if (resultItem.isDependentChild()) {
            return;
        }

        boolean directSampleUsageEnabled = Boolean.TRUE.equals(analysis.getTest().getDirectSampleUsageEnabled());
        if (!directSampleUsageEnabled && !hasActiveChildDependencies(analysis.getTest().getId())) {
            return;
        }

        resultItem.setParentSampleUsageEnabled(true);
        BigDecimal remaining = analysis.getSampleItem().getEffectiveRemainingQuantity();
        if (remaining != null) {
            resultItem.setParentSampleRemainingQuantity(remaining.toPlainString());
        }

        if (analysis.getSampleUsedQuantity() != null) {
            resultItem.setParentSampleUsageQuantity(analysis.getSampleUsedQuantity().toPlainString());
            resultItem.setParentSampleUsageLocked(true);
        } else {
            resultItem.setParentSampleUsageLocked(false);
        }
    }

    private void applyTubeLabelContextToResultItem(TestResultItem resultItem, Analysis analysis) {
        if (resultItem == null || analysis == null || analysis.getTest() == null) {
            return;
        }

        List<String> activeBlocks = resolveActiveTubeLabelBlocks(resultItem, analysis);
        if (activeBlocks.isEmpty()) {
            resultItem.setTubeLabelBlocks(new ArrayList<>());
            resultItem.setTubeLabels(new LinkedHashMap<>());
            return;
        }

        Map<String, AnalysisTubeLabel> persistedLabels = getPersistedTubeLabelsByBlock(analysis.getId());
        Map<String, String> labelValues = new LinkedHashMap<>();
        persistedLabels.values().forEach(label -> {
            if (label == null || StringUtils.isBlank(label.getTubeBlockName())
                    || StringUtils.isBlank(label.getLabelCode())) {
                return;
            }
            labelValues.putIfAbsent(normalizeBlockName(label.getTubeBlockName()),
                    StringUtils.defaultString(label.getLabelCode()));
        });
        for (String blockName : activeBlocks) {
            AnalysisTubeLabel persisted = persistedLabels.get(normalizeBlockName(blockName));
            labelValues.put(blockName, persisted == null ? "" : StringUtils.defaultString(persisted.getLabelCode()));
        }

        resultItem.setTubeLabelBlocks(activeBlocks);
        resultItem.setTubeLabels(labelValues);
    }

    private boolean hasActiveChildDependencies(String parentTestId) {
        if (GenericValidator.isBlankOrNull(parentTestId)) {
            return false;
        }

        if (activeDependencyParentByTestId.containsKey(parentTestId)) {
            return Boolean.TRUE.equals(activeDependencyParentByTestId.get(parentTestId));
        }

        List<TestParentChildDependency> dependencies = testParentChildDependencyService.getByParentTestId(parentTestId);
        boolean hasActiveDependencies = dependencies != null
                && dependencies.stream().anyMatch(dependency -> dependency != null && Boolean.TRUE.equals(dependency.getActive()));
        activeDependencyParentByTestId.put(parentTestId, hasActiveDependencies);
        return hasActiveDependencies;
    }

    private static class DependencyContext {
        private boolean isDependentChild = false;
        private String parentTestId;
        private String parentTestName;
        private Analysis parentAnalysis;
        private boolean parentCompleted = false;
        private String sampleUsageSource = TestParentChildDependency.SAMPLE_USAGE_SOURCE_SAMPLE_ITEM_REMAINING;
        private String parentResultFieldKey;
        private String sampleRemainingQuantity;
        private boolean sampleUsageFromParentField = false;
        private boolean tubeBasedUsage = false;
        private String selectedTubeBlockName;
        private Map<String, TubeBlockContext> tubeBlocks = new LinkedHashMap<>();
    }

    private static class TubeBlockContext {
        private String blockName;
        private String label;
        private String quantityFieldKey;
        private BigDecimal totalAvailable;
    }

    private BigDecimal calculateTotalRemainingFromTubeBlocks(Analysis childAnalysis, DependencyContext context) {
        if (context == null || context.tubeBlocks == null || context.tubeBlocks.isEmpty()) {
            return null;
        }

        BigDecimal totalRemaining = BigDecimal.ZERO;
        for (String blockName : context.tubeBlocks.keySet()) {
            BigDecimal remaining = calculateRemainingFromTubeBlock(childAnalysis, context, blockName);
            if (remaining != null) {
                totalRemaining = totalRemaining.add(remaining);
            }
        }
        return totalRemaining;
    }

    private BigDecimal calculateRemainingFromParentField(Analysis childAnalysis, DependencyContext context) {
        if (childAnalysis == null || childAnalysis.getSampleItem() == null || context == null
                || context.parentAnalysis == null || GenericValidator.isBlankOrNull(context.parentResultFieldKey)) {
            return null;
        }

        BigDecimal totalAvailable = resolveParentFieldNumericValue(context.parentAnalysis, context.parentTestId,
                context.parentResultFieldKey);
        if (totalAvailable == null) {
            return null;
        }

        BigDecimal alreadyConsumed = getConsumedUsageForParentAnalysis(childAnalysis.getSampleItem().getId(),
                context.parentAnalysis.getId());
        BigDecimal remaining = totalAvailable.subtract(alreadyConsumed);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return remaining;
    }

    private BigDecimal calculateRemainingFromTubeBlock(Analysis childAnalysis, DependencyContext context, String blockName) {
        if (childAnalysis == null || context == null || context.parentAnalysis == null || GenericValidator.isBlankOrNull(blockName)
                || context.tubeBlocks == null || context.tubeBlocks.isEmpty()) {
            return null;
        }

        TubeBlockContext tubeContext = context.tubeBlocks.get(blockName);
        if (tubeContext == null || tubeContext.totalAvailable == null) {
            return null;
        }

        BigDecimal alreadyConsumed = getConsumedUsageForParentAnalysis(childAnalysis.getSampleItem().getId(),
                context.parentAnalysis.getId(), blockName);
        BigDecimal remaining = tubeContext.totalAvailable.subtract(alreadyConsumed);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return remaining;
    }

    private BigDecimal resolveParentFieldNumericValue(Analysis parentAnalysis, String parentTestId, String fieldKey) {
        String cacheKey = parentAnalysis.getId() + ":" + fieldKey;
        if (parentFieldNumericValueCache.containsKey(cacheKey)) {
            return parentFieldNumericValueCache.get(cacheKey);
        }

        List<TestAdditionalFieldPayload> parentFieldDefinitions = getAdditionalFieldsForTest(parentTestId);
        Map<String, String> parentValues = testAdditionalFieldService.getAnalysisValuesForFields(parentAnalysis.getId(),
                parentFieldDefinitions);
        String rawValue = parentValues == null ? null : parentValues.get(fieldKey);
        BigDecimal numericValue = parsePositiveBigDecimal(rawValue);
        parentFieldNumericValueCache.put(cacheKey, numericValue);
        return numericValue;
    }

    private Map<String, TubeBlockContext> resolveTubeBlockContexts(Analysis parentAnalysis, String parentTestId) {
        if (parentAnalysis == null || GenericValidator.isBlankOrNull(parentTestId)) {
            return Collections.emptyMap();
        }

        String cacheKey = parentAnalysis.getId();
        if (parentTubeContextCache.containsKey(cacheKey)) {
            return parentTubeContextCache.get(cacheKey);
        }

        List<TestAdditionalFieldPayload> parentFieldDefinitions = getAdditionalFieldsForTest(parentTestId);
        if (parentFieldDefinitions.isEmpty()) {
            parentTubeContextCache.put(cacheKey, Collections.emptyMap());
            return Collections.emptyMap();
        }

        JsonNode primaryMetadata = readMetadataNode(parentAnalysis.getTest().getResultDisplayConfigJson());
        boolean primarySelectorEnabled = primaryMetadata.path("tubeSelector").path("enabled").asBoolean(false);
        String selectorFieldKey = null;
        for (TestAdditionalFieldPayload fieldDefinition : parentFieldDefinitions) {
            if (isTubeSelectorField(fieldDefinition)) {
                selectorFieldKey = fieldDefinition.getFieldKey();
                break;
            }
        }

        if (!primarySelectorEnabled && GenericValidator.isBlankOrNull(selectorFieldKey)) {
            parentTubeContextCache.put(cacheKey, Collections.emptyMap());
            return Collections.emptyMap();
        }

        Map<String, String> parentValues = testAdditionalFieldService.getAnalysisValuesForFields(parentAnalysis.getId(),
                parentFieldDefinitions);
        Map<String, AnalysisTubeLabel> persistedLabels = getPersistedTubeLabelsByBlock(parentAnalysis.getId());
        BigDecimal selectedTubeCount = primarySelectorEnabled
                ? parsePositiveBigDecimal(resolvePrimaryResultNumericValue(parentAnalysis))
                : parsePositiveBigDecimal(parentValues == null ? null : parentValues.get(selectorFieldKey));
        if (selectedTubeCount == null) {
            parentTubeContextCache.put(cacheKey, Collections.emptyMap());
            return Collections.emptyMap();
        }

        Map<String, TubeBlockContext> tubeContexts = new LinkedHashMap<>();
        int selectedCount = selectedTubeCount.intValue();
        if (primaryMetadata.path("tubeQuantitySource").asBoolean(false)) {
            int primaryActivationCount = getTubeActivationCount(primaryMetadata);
            if (primaryActivationCount <= 0 || selectedCount >= primaryActivationCount) {
                TubeBlockContext primaryTubeContext = new TubeBlockContext();
                primaryTubeContext.blockName = getPrimaryResultBlockName(parentAnalysis);
                primaryTubeContext.label = resolveTubeBlockLabel(primaryTubeContext.blockName, persistedLabels);
                primaryTubeContext.quantityFieldKey = "__PRIMARY_RESULT__";
                primaryTubeContext.totalAvailable = parsePositiveBigDecimal(resolvePrimaryResultNumericValue(parentAnalysis));
                tubeContexts.put(primaryTubeContext.blockName, primaryTubeContext);
            }
        }
        for (TestAdditionalFieldPayload fieldDefinition : parentFieldDefinitions) {
            if (!isTubeQuantitySourceField(fieldDefinition)) {
                continue;
            }
            int activationCount = getTubeActivationCount(fieldDefinition);
            if (activationCount > 0 && selectedCount < activationCount) {
                continue;
            }

            FieldBlockContext blockContext = getFieldBlockContext(fieldDefinition);
            BigDecimal totalAvailable = parsePositiveBigDecimal(parentValues == null ? null
                    : parentValues.get(fieldDefinition.getFieldKey()));
            TubeBlockContext tubeContext = new TubeBlockContext();
            tubeContext.blockName = blockContext.blockName;
            tubeContext.label = resolveTubeBlockLabel(blockContext.blockName, persistedLabels);
            tubeContext.quantityFieldKey = fieldDefinition.getFieldKey();
            tubeContext.totalAvailable = totalAvailable;
            tubeContexts.put(blockContext.blockName, tubeContext);
        }

        parentTubeContextCache.put(cacheKey, tubeContexts);
        return tubeContexts;
    }

    private String resolvePrimaryResultNumericValue(Analysis analysis) {
        if (analysis == null) {
            return null;
        }
        List<Result> results = resultService.getResultsByAnalysis(analysis);
        if (results == null || results.isEmpty()) {
            return null;
        }
        return getFormattedResultValue(results.get(0));
    }

    private BigDecimal getConsumedUsageForParentAnalysis(String sampleItemId, String parentAnalysisId) {
        return getConsumedUsageForParentAnalysis(sampleItemId, parentAnalysisId, null);
    }

    private BigDecimal getConsumedUsageForParentAnalysis(String sampleItemId, String parentAnalysisId, String blockName) {
        if (GenericValidator.isBlankOrNull(sampleItemId) || GenericValidator.isBlankOrNull(parentAnalysisId)) {
            return BigDecimal.ZERO;
        }

        BigDecimal persistedTubeUsage = getPersistedConsumedUsageForParentAnalysis(parentAnalysisId, blockName);
        if (persistedTubeUsage.compareTo(BigDecimal.ZERO) > 0) {
            return persistedTubeUsage;
        }

        List<Analysis> analyses = analysesBySampleItemIdCache.computeIfAbsent(sampleItemId, ignored -> {
            SampleItem sampleItem = sampleItemService.get(sampleItemId);
            if (sampleItem == null) {
                return new ArrayList<>();
            }
            return analysisService.getAnalysesBySampleItem(sampleItem);
        });

        BigDecimal consumed = BigDecimal.ZERO;
        for (Analysis analysis : analyses) {
            if (analysis == null || analysis.getParentAnalysis() == null || analysis.getSampleUsedQuantity() == null) {
                continue;
            }
            if (parentAnalysisId.equals(analysis.getParentAnalysis().getId())) {
                if (!GenericValidator.isBlankOrNull(blockName)
                        && !blockName.equals(String.valueOf(analysis.getParentUsageBlockName()))) {
                    continue;
                }
                consumed = consumed.add(analysis.getSampleUsedQuantity());
            }
        }
        return consumed;
    }

    private BigDecimal getPersistedConsumedUsageForParentAnalysis(String parentAnalysisId, String blockName) {
        if (GenericValidator.isBlankOrNull(parentAnalysisId)) {
            return BigDecimal.ZERO;
        }

        List<AnalysisTubeUsage> usages = tubeUsageByParentAnalysisIdCache.computeIfAbsent(parentAnalysisId,
                analysisTubeUsageService::getByParentAnalysisId);
        BigDecimal consumed = BigDecimal.ZERO;
        for (AnalysisTubeUsage usage : usages) {
            if (usage == null || usage.getUsedQuantity() == null) {
                continue;
            }
            if (!GenericValidator.isBlankOrNull(blockName)
                    && !blockName.equals(normalizeBlockName(usage.getParentTubeBlockName()))) {
                continue;
            }
            consumed = consumed.add(usage.getUsedQuantity());
        }
        return consumed;
    }

    private Map<String, AnalysisTubeUsage> getPersistedTubeUsageByBlock(String analysisId) {
        List<AnalysisTubeUsage> usages = tubeUsageByAnalysisIdCache.computeIfAbsent(analysisId,
                analysisTubeUsageService::getByAnalysisId);
        Map<String, AnalysisTubeUsage> byBlock = new LinkedHashMap<>();
        for (AnalysisTubeUsage usage : usages) {
            if (usage == null || GenericValidator.isBlankOrNull(usage.getChildBlockName())) {
                continue;
            }
            byBlock.put(normalizeBlockName(usage.getChildBlockName()), usage);
        }
        return byBlock;
    }

    private Map<String, AnalysisTubeLabel> getPersistedTubeLabelsByBlock(String analysisId) {
        return tubeLabelByAnalysisIdCache.computeIfAbsent(analysisId,
                analysisTubeLabelService::getByAnalysisIdGroupedByBlock);
    }

    private String resolveTubeBlockLabel(String blockName, Map<String, AnalysisTubeLabel> persistedLabels) {
        if (GenericValidator.isBlankOrNull(blockName) || persistedLabels == null || persistedLabels.isEmpty()) {
            return blockName;
        }
        AnalysisTubeLabel persisted = persistedLabels.get(normalizeBlockName(blockName));
        return persisted == null || GenericValidator.isBlankOrNull(persisted.getLabelCode()) ? blockName
                : persisted.getLabelCode();
    }

    private List<String> resolveChildTubeUsageBlocks(TestResultItem resultItem) {
        Set<String> blockNames = new LinkedHashSet<>();
        if (resultItem == null) {
            return Collections.emptyList();
        }

        JsonNode primaryMetadata = readMetadataNode(resultItem.getResultDisplayConfigJson());
        if (isChildTubeUsageBlockEnabled(primaryMetadata)) {
            String primaryBlockName = normalizeBlockName(primaryMetadata.path("resultBlock").asText(null));
            if (!GenericValidator.isBlankOrNull(primaryBlockName)) {
                blockNames.add(primaryBlockName);
            }
        }

        if (resultItem.getAdditionalFieldDefinitions() == null) {
            return new ArrayList<>(blockNames);
        }

        for (TestAdditionalFieldPayload fieldDefinition : resultItem.getAdditionalFieldDefinitions()) {
            if (fieldDefinition == null || fieldDefinition.getActive() == Boolean.FALSE
                    || !isChildTubeUsageBlockEnabled(fieldDefinition)) {
                continue;
            }
            FieldBlockContext context = getFieldBlockContext(fieldDefinition);
            if (!GenericValidator.isBlankOrNull(context.blockName)) {
                blockNames.add(normalizeBlockName(context.blockName));
            }
        }
        return new ArrayList<>(blockNames);
    }

    private List<String> resolveActiveTubeLabelBlocks(TestResultItem resultItem, Analysis analysis) {
        Set<String> blockNames = new LinkedHashSet<>();
        if (resultItem == null || analysis == null) {
            return Collections.emptyList();
        }

        JsonNode primaryMetadata = readMetadataNode(resultItem.getResultDisplayConfigJson());
        if (isTubeLabelEnabled(primaryMetadata) && isPrimaryResultVisibleForLabels(resultItem, analysis, primaryMetadata)) {
            String primaryBlockName = getPrimaryResultBlockName(analysis);
            if (!GenericValidator.isBlankOrNull(primaryBlockName)) {
                blockNames.add(normalizeBlockName(primaryBlockName));
            }
        }

        if (resultItem.getAdditionalFieldDefinitions() != null) {
            for (TestAdditionalFieldPayload fieldDefinition : resultItem.getAdditionalFieldDefinitions()) {
                if (fieldDefinition == null || fieldDefinition.getActive() == Boolean.FALSE || !isTubeLabelEnabled(fieldDefinition)
                        || !isTubeBlockActive(fieldDefinition, resultItem, primaryMetadata)) {
                    continue;
                }
                FieldBlockContext context = getFieldBlockContext(fieldDefinition);
                if (!GenericValidator.isBlankOrNull(context.blockName)) {
                    blockNames.add(normalizeBlockName(context.blockName));
                }
            }
        }

        return new ArrayList<>(blockNames);
    }

    private boolean isChildTubeUsageBlockEnabled(TestAdditionalFieldPayload fieldDefinition) {
        return readMetadataNode(fieldDefinition).path("tubeUsage").path("childBlockEnabled").asBoolean(false);
    }

    private boolean isChildTubeUsageBlockEnabled(JsonNode metadataNode) {
        return metadataNode.path("tubeUsage").path("childBlockEnabled").asBoolean(false);
    }

    private boolean isTubeLabelEnabled(TestAdditionalFieldPayload fieldDefinition) {
        return readMetadataNode(fieldDefinition).path("tubeLabel").path("enabled").asBoolean(false);
    }

    private boolean isTubeLabelEnabled(JsonNode metadataNode) {
        return metadataNode.path("tubeLabel").path("enabled").asBoolean(false);
    }

    private String normalizeBlockName(String blockName) {
        return GenericValidator.isBlankOrNull(blockName) ? null : blockName.trim();
    }

    private BigDecimal parsePositiveBigDecimal(String value) {
        if (GenericValidator.isBlankOrNull(value)) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private JsonNode readMetadataNode(TestAdditionalFieldPayload fieldDefinition) {
        if (fieldDefinition == null || GenericValidator.isBlankOrNull(fieldDefinition.getMetadataJson())) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(fieldDefinition.getMetadataJson());
            return root != null && root.isObject() ? root : OBJECT_MAPPER.createObjectNode();
        } catch (Exception e) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private boolean isTubeSelectorField(TestAdditionalFieldPayload fieldDefinition) {
        return readMetadataNode(fieldDefinition).path("tubeSelector").path("enabled").asBoolean(false);
    }

    private boolean isTubeQuantitySourceField(TestAdditionalFieldPayload fieldDefinition) {
        return readMetadataNode(fieldDefinition).path("tubeQuantitySource").asBoolean(false);
    }

    private int getTubeActivationCount(TestAdditionalFieldPayload fieldDefinition) {
        JsonNode tubeBlockNode = readMetadataNode(fieldDefinition).path("tubeBlock");
        return tubeBlockNode.path("activationCount").isInt() ? tubeBlockNode.path("activationCount").asInt() : 0;
    }

    private int getTubeActivationCount(JsonNode metadataNode) {
        JsonNode activationNode = metadataNode.path("tubeBlock").path("activationCount");
        if (activationNode.isInt()) {
            return activationNode.asInt();
        }
        if (activationNode.isTextual()) {
            try {
                return Integer.parseInt(activationNode.asText().trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private boolean isTubeBlockActive(TestAdditionalFieldPayload fieldDefinition, TestResultItem resultItem,
            JsonNode primaryMetadata) {
        int activationCount = getTubeActivationCount(fieldDefinition);
        if (activationCount <= 0) {
            return true;
        }
        Integer selectedTubeCount = resolveSelectedTubeCount(resultItem, primaryMetadata);
        return selectedTubeCount != null && selectedTubeCount.intValue() >= activationCount;
    }

    private boolean isPrimaryResultVisibleForLabels(TestResultItem resultItem, Analysis analysis, JsonNode primaryMetadata) {
        int activationCount = getTubeActivationCount(primaryMetadata);
        if (activationCount <= 0) {
            return true;
        }
        Integer selectedTubeCount = resolveSelectedTubeCount(resultItem, primaryMetadata);
        return selectedTubeCount != null && selectedTubeCount.intValue() >= activationCount;
    }

    private Integer resolveSelectedTubeCount(TestResultItem resultItem, JsonNode primaryMetadata) {
        if (resultItem == null) {
            return null;
        }
        if (primaryMetadata != null && primaryMetadata.path("tubeSelector").path("enabled").asBoolean(false)) {
            BigDecimal parsed = parsePositiveBigDecimal(
                    StringUtils.defaultIfBlank(resultItem.getShadowResultValue(), resultItem.getResultValue()));
            return parsed == null ? null : Integer.valueOf(parsed.intValue());
        }
        if (resultItem.getAdditionalFieldDefinitions() == null || resultItem.getAdditionalFieldValues() == null) {
            return null;
        }
        for (TestAdditionalFieldPayload definition : resultItem.getAdditionalFieldDefinitions()) {
            if (definition == null || definition.getActive() == Boolean.FALSE || !isTubeSelectorField(definition)) {
                continue;
            }
            BigDecimal parsed = parsePositiveBigDecimal(resultItem.getAdditionalFieldValues().get(definition.getFieldKey()));
            return parsed == null ? null : Integer.valueOf(parsed.intValue());
        }
        return null;
    }

    private String getPrimaryResultBlockName(Analysis analysis) {
        JsonNode metadata = readMetadataNode(analysis.getTest().getResultDisplayConfigJson());
        String entryScope = metadata.path("entryScope").asText("OFFICIAL").trim().toUpperCase();
        String fallbackBlock = "PRELIMINARY".equals(entryScope) ? "Preliminary" : "Official";
        String blockName = metadata.path("resultBlock").asText("");
        if (GenericValidator.isBlankOrNull(blockName)) {
            blockName = fallbackBlock;
        }
        return blockName;
    }

    private JsonNode readMetadataNode(String metadataJson) {
        if (GenericValidator.isBlankOrNull(metadataJson)) {
            return OBJECT_MAPPER.createObjectNode();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(metadataJson);
            return root != null && root.isObject() ? root : OBJECT_MAPPER.createObjectNode();
        } catch (Exception e) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private FieldBlockContext getFieldBlockContext(TestAdditionalFieldPayload fieldDefinition) {
        JsonNode metadataNode = readMetadataNode(fieldDefinition);
        String entryScope = String.valueOf(fieldDefinition.getEntryScope() != null ? fieldDefinition.getEntryScope()
                : metadataNode.path("entryScope").asText("OFFICIAL")).toUpperCase();
        String fallbackBlock = "PRELIMINARY".equals(entryScope) ? "Preliminary" : "Official";
        String blockName = fieldDefinition.getBlockName();
        if (GenericValidator.isBlankOrNull(blockName)) {
            blockName = metadataNode.path("resultBlock").asText("");
        }
        if (GenericValidator.isBlankOrNull(blockName)) {
            blockName = metadataNode.path("blockName").asText("");
        }
        if (GenericValidator.isBlankOrNull(blockName)) {
            blockName = fallbackBlock;
        }
        FieldBlockContext context = new FieldBlockContext();
        context.blockName = blockName;
        return context;
    }

    private static class FieldBlockContext {
        private String blockName;
    }

    private void setResultLimitDependencies(ResultLimit resultLimit, TestResultItem testItem,
            List<TestResult> testResults) {
        if (resultLimit != null) {
            testItem.setResultLimitId(resultLimit.getId());
            testItem.setLowerNormalRange(
                    resultLimit.getLowNormal() == Double.NEGATIVE_INFINITY ? 0 : resultLimit.getLowNormal());
            testItem.setUpperNormalRange(
                    resultLimit.getHighNormal() == Double.POSITIVE_INFINITY ? 0 : resultLimit.getHighNormal());
            testItem.setLowerAbnormalRange(
                    resultLimit.getLowValid() == Double.NEGATIVE_INFINITY ? 0 : resultLimit.getLowValid());
            testItem.setUpperAbnormalRange(
                    resultLimit.getHighValid() == Double.POSITIVE_INFINITY ? 0 : resultLimit.getHighValid());
            testItem.setLowerCritical(
                    resultLimit.getLowCritical() == Double.NEGATIVE_INFINITY ? 0 : resultLimit.getLowCritical());
            testItem.setHigherCritical(
                    resultLimit.getHighCritical() == Double.POSITIVE_INFINITY ? 0 : resultLimit.getHighCritical());

            testItem.setValid(getIsValid(testItem.getResultValue(), resultLimit));
            testItem.setNormal(getIsNormal(testItem.getResultValue(), resultLimit));
            testItem.setNormalRange(SpringContext.getBean(ResultLimitService.class).getDisplayReferenceRange(
                    resultLimit, testResults.isEmpty() ? "0" : testResults.get(0).getSignificantDigits(), " - "));
        }
    }

    private void setDictionaryResults(TestResultItem testItem, boolean isConclusion, Result result,
            List<TestResult> testResults) {
        if (isConclusion) {
            testItem.setDictionaryResults(getAnyDictionaryValues(result));
        } else {
            setDictionaryResults(testItem, testResults, result);
        }
    }

    private void setDictionaryResults(TestResultItem testItem, List<TestResult> testResults, Result result) {

        List<IdValuePair> values = null;
        Dictionary dictionary;

        if (testResults != null && !testResults.isEmpty()
                && TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(testResults.get(0).getTestResultType())) {
            values = new ArrayList<>();

            Collections.sort(testResults, new Comparator<TestResult>() {
                @Override
                public int compare(TestResult o1, TestResult o2) {
                    if (GenericValidator.isBlankOrNull(o1.getSortOrder())
                            || GenericValidator.isBlankOrNull(o2.getSortOrder())) {
                        return 1;
                    }

                    return Integer.parseInt(o1.getSortOrder()) - Integer.parseInt(o2.getSortOrder());
                }
            });

            String qualifiedDictionaryIds = "";
            for (TestResult testResult : testResults) {
                if (TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(testResult.getTestResultType())) {
                    dictionary = new Dictionary();
                    dictionary.setId(testResult.getValue());
                    dictionaryService.getData(dictionary);
                    String displayValue = dictionary.getLocalizedName();

                    if ("unknown".equals(displayValue)) {
                        displayValue = GenericValidator.isBlankOrNull(dictionary.getLocalAbbreviation())
                                ? dictionary.getDictEntry()
                                : dictionary.getLocalAbbreviation();
                    }
                    values.add(new IdValuePair(testResult.getValue(), displayValue));
                    if (testResult.getIsQuantifiable()) {
                        if (!GenericValidator.isBlankOrNull(qualifiedDictionaryIds)) {
                            qualifiedDictionaryIds += ",";
                        }
                        qualifiedDictionaryIds += testResult.getValue();
                        setQualifiedValues(testItem, result);
                    }
                }
            }

            if (!GenericValidator.isBlankOrNull(qualifiedDictionaryIds)) {
                testItem.setQualifiedDictionaryId("[" + qualifiedDictionaryIds + "]");
            }
        }
        if (!GenericValidator.isBlankOrNull(testItem.getQualifiedResultValue())) {
            testItem.setHasQualifiedResult(true);
        }

        testItem.setDictionaryResults(values);
    }

    private void setQualifiedValues(TestResultItem testItem, Result result) {
        if (result != null) {
            List<Result> results = resultService.getChildResults(result.getId());
            if (!results.isEmpty()) {
                Result childResult = results.get(0);
                testItem.setQualifiedResultId(childResult.getId());
                testItem.setQualifiedResultValue(childResult.getValue());
            }
        }
    }

    private String getFormattedResultValue(Result result) {
        ResultService resultResultService = SpringContext.getBean(ResultService.class);
        return result != null ? resultResultService.getResultValue(result, false) : "";
    }

    private boolean hasLogValue(Test test) { // Analysis analysis, String resultValue) {
        // TO-DO refactor
        // if ( ){
        // if (GenericValidator.isBlankOrNull(resultValue)) {
        // return true;
        // }
        // try {
        // Double.parseDouble(resultValue);
        // return true;
        // } catch (NumberFormatException e) {
        // return false;
        // }

        // return true;
        // }

        // return false;
        return TestIdentityService.getInstance().isTestNumericViralLoad(test);
    }

    private List<IdValuePair> getAnyDictionaryValues(Result result) {
        List<IdValuePair> values = null;

        if (result != null && TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(result.getResultType())) {
            values = new ArrayList<>();

            Dictionary dictionaryValue = new Dictionary();
            dictionaryValue.setId(result.getValue());
            dictionaryService.getData(dictionaryValue);

            List<Dictionary> dictionaryList = dictionaryService
                    .getDictionaryEntriesByCategoryId(dictionaryValue.getDictionaryCategory().getId());

            for (Dictionary dictionary : dictionaryList) {
                String displayValue = dictionary.getLocalizedName();

                if ("unknown".equals(displayValue)) {
                    displayValue = GenericValidator.isBlankOrNull(dictionary.getLocalAbbreviation())
                            ? dictionary.getDictEntry()
                            : dictionary.getLocalAbbreviation();
                }
                values.add(new IdValuePair(dictionary.getId(), displayValue));
            }
        }

        return values;
    }

    private boolean getIsValid(String resultValue, ResultLimit resultLimit) {
        boolean valid = true;

        if (!GenericValidator.isBlankOrNull(resultValue) && resultLimit != null) {
            try {
                double value = Double.valueOf(resultValue);

                valid = value >= resultLimit.getLowValid() && value <= resultLimit.getHighValid();

            } catch (NumberFormatException e) {
                LogEvent.logInfo(this.getClass().getSimpleName(), "getIsValid", e.getMessage());
                // no-op
            }
        }
        return valid;
    }

    private boolean getIsNormal(String resultValue, ResultLimit resultLimit) {
        boolean normal = true;

        if (!GenericValidator.isBlankOrNull(resultValue) && resultLimit != null) {
            try {
                double value = Double.valueOf(resultValue);

                normal = value >= resultLimit.getLowNormal() && value <= resultLimit.getHighNormal();
            } catch (NumberFormatException e) {
                LogEvent.logInfo(this.getClass().getSimpleName(), "getIsNormal", e.getMessage());
                // no-op
            }
        }

        return normal;
    }

    // TODO: Re-enable after new inventory frontend integration
    // private boolean kitNotInActiveKitList(String testKitId) {
    // List<InventoryKitItem> activeKits = getActiveKits();
    //
    // for (InventoryKitItem kit : activeKits) {
    // // The locationID is the reference held in the DB
    // if (testKitId.equals(kit.getInventoryLocationId())) {
    // return false;
    // }
    // }
    //
    // return true;
    // }

    private List<IdValuePair> getMethodOptionsForTest(Test test, String selectedMethodId, String fallbackMethodName) {
        List<IdValuePair> resolved = new ArrayList<>();
        if (test == null || GenericValidator.isBlankOrNull(test.getId())) {
            return resolved;
        }

        String testId = test.getId();
        if (methodOptionsByTestIdCache.containsKey(testId)) {
            resolved = new ArrayList<>(methodOptionsByTestIdCache.get(testId));
        } else {
            Set<String> seenMethodIds = new HashSet<>();
            List<TbMethodTest> links = tbMethodTestService.getAllMatching("testId", testId);

            for (TbMethodTest link : links) {
                if (!"Y".equals(link.getIsActive()) || GenericValidator.isBlankOrNull(link.getMethodId())) {
                    continue;
                }
                String methodId = link.getMethodId();
                if (seenMethodIds.contains(methodId)) {
                    continue;
                }
                seenMethodIds.add(methodId);
                resolved.add(new IdValuePair(methodId, resolveMethodLabel(methodId)));
            }

            // Backward-compatible fallback: if no mapped methods exist, use the single
            // test.method value so existing environments keep working.
            if (resolved.isEmpty() && test.getMethod() != null
                    && !GenericValidator.isBlankOrNull(test.getMethod().getId())) {
                String methodId = test.getMethod().getId();
                String methodLabel = GenericValidator.isBlankOrNull(fallbackMethodName) ? resolveMethodLabel(methodId)
                        : fallbackMethodName;
                resolved.add(new IdValuePair(methodId, methodLabel));
            }

            resolved.sort((a, b) -> a.getValue().compareToIgnoreCase(b.getValue()));
            methodOptionsByTestIdCache.put(testId, new ArrayList<>(resolved));
        }

        if (!GenericValidator.isBlankOrNull(selectedMethodId)) {
            boolean alreadyPresent = resolved.stream().anyMatch(option -> selectedMethodId.equals(option.getId()));
            if (!alreadyPresent) {
                resolved.add(new IdValuePair(selectedMethodId, resolveMethodLabel(selectedMethodId)));
            }
        }

        return resolved;
    }

    private String resolveMethodLabel(String methodId) {
        if (GenericValidator.isBlankOrNull(methodId)) {
            return "";
        }
        if (methodLabelByIdCache.containsKey(methodId)) {
            return methodLabelByIdCache.get(methodId);
        }

        Method method = methodService.get(methodId);
        String label = methodId;
        if (method != null && !GenericValidator.isBlankOrNull(method.getId())) {
            if (!GenericValidator.isBlankOrNull(method.getMethodName())) {
                label = method.getMethodName();
            } else if (!GenericValidator.isBlankOrNull(method.getLocalizedValue())) {
                label = method.getLocalizedValue();
            }
        }

        methodLabelByIdCache.put(methodId, label);
        return label;
    }

    private String getCurrentDate() {
        if (GenericValidator.isBlankOrNull(currentDate)) {
            currentDate = DateUtil.getCurrentDateAsText();
        }

        return currentDate;
    }

    public boolean inventoryNeeded() {
        return inventoryNeeded;
    }

    public void addExcludedAnalysisStatus(AnalysisStatus status) {
        excludedAnalysisStatus.add(Integer.parseInt(SpringContext.getBean(IStatusService.class).getStatusID(status)));
    }

    public void addIncludedSampleStatus(OrderStatus status) {
        sampleStatusList.add(Integer.parseInt(SpringContext.getBean(IStatusService.class).getStatusID(status)));
    }

    public void addIncludedAnalysisStatus(AnalysisStatus status) {
        analysisStatusList.add(Integer.parseInt(SpringContext.getBean(IStatusService.class).getStatusID(status)));
    }

    // TODO: Re-enable after new inventory frontend integration
    // TODO: Re-enable after new inventory frontend integration
    // // private List<InventoryKitItem> getActiveKits() {
    // if (activeKits == null) {
    // InventoryUtility inventoryUtil =
    // SpringContext.getBean(InventoryUtility.class);
    // activeKits = inventoryUtil.getExistingActiveInventory();
    // }
    //
    // return activeKits;
    // }

    public void setLockCurrentResults(boolean lockCurrentResults) {
        this.lockCurrentResults = lockCurrentResults;
    }

    public boolean isLockCurrentResults() {
        return lockCurrentResults;
    }

    private boolean getQaEventByTestSection(Analysis analysis) {

        if (analysis.getTestSection() != null && analysis.getSampleItem().getSample() != null) {
            Sample sample = analysis.getSampleItem().getSample();
            List<SampleQaEvent> sampleQaEventsList = getSampleQaEvents(sample);
            for (SampleQaEvent event : sampleQaEventsList) {
                QAService qa = new QAService(event);
                if (!GenericValidator.isBlankOrNull(qa.getObservationValue(QAObservationType.SECTION))
                        && qa.getObservationValue(QAObservationType.SECTION)
                                .equals(analysis.getTestSection().getNameKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    public List<SampleQaEvent> getSampleQaEvents(Sample sample) {
        return sampleQaEventService.getSampleQaEventsBySample(sample);
    }

    public List<TestResultItem> getUnfinishedTestResultItemsByAccession(String accessionNumber) {
        LogEvent.logInfo(this.getClass().getSimpleName(), "getUnfinishedTestResultItemsByAccession",
                "Searching for unfinished tests with accessionNumber: " + accessionNumber + ", "
                        + "analysisStatusList size: " + (analysisStatusList != null ? analysisStatusList.size() : 0)
                        + ", " + "sampleStatusList size: " + (sampleStatusList != null ? sampleStatusList.size() : 0));
        List<Analysis> analysisList = analysisService.getPageAnalysisByStatusFromAccession(analysisStatusList,
                sampleStatusList, accessionNumber);
        LogEvent.logInfo(this.getClass().getSimpleName(), "getUnfinishedTestResultItemsByAccession",
                "Found " + (analysisList != null ? analysisList.size() : 0) + " analyses for accessionNumber: "
                        + accessionNumber);

        List<TestResultItem> result = getGroupedTestsForAnalysisList(analysisList, SORT_FORWARD);
        LogEvent.logInfo(this.getClass().getSimpleName(), "getUnfinishedTestResultItemsByAccession",
                "getGroupedTestsForAnalysisList returned " + (result != null ? result.size() : 0)
                        + " test result items");
        return result;
    }

    public List<TestResultItem> getUnfinishedTestResultItemsByAccession(String accessionNumber,
            String upperRangeAccessionNumber, boolean doRange, boolean finished) {
        List<Analysis> analysisList = analysisService.getPageAnalysisByStatusFromAccession(analysisStatusList,
                sampleStatusList, accessionNumber, upperRangeAccessionNumber, doRange, finished);

        return getGroupedTestsForAnalysisList(analysisList, SORT_FORWARD);
    }

    public int getTotalCountAnalysisByAccessionAndStatus(String accessionNumber) {
        return analysisService.getCountAnalysisByStatusFromAccession(analysisStatusList, sampleStatusList,
                accessionNumber);
    }
}
