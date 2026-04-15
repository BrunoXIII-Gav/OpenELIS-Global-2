package org.openelisglobal.testconfiguration.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import javax.validation.Valid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.hibernate.HibernateException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.services.DisplayListService.ListType;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.common.util.StringUtil;
import org.openelisglobal.dictionary.service.DictionaryService;
import org.openelisglobal.dictionary.valueholder.Dictionary;
import org.openelisglobal.localization.service.LocalizationService;
import org.openelisglobal.localization.service.LocalizationServiceImpl;
import org.openelisglobal.localization.valueholder.Localization;
import org.openelisglobal.panel.service.PanelService;
import org.openelisglobal.panel.valueholder.Panel;
import org.openelisglobal.panelitem.valueholder.PanelItem;
import org.openelisglobal.resultlimit.service.ResultLimitService;
import org.openelisglobal.resultlimits.valueholder.ResultLimit;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.test.service.TestSectionService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.test.valueholder.TestSection;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldOptionPayload;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.service.TestAdditionalFieldService;
import org.openelisglobal.testconfiguration.beans.ResultLimitBean;
import org.openelisglobal.testconfiguration.beans.TestCatalogBean;
import org.openelisglobal.testconfiguration.controller.TestModifyEntryController;
import org.openelisglobal.testconfiguration.controller.TestModifyEntryController.DictionaryParams;
import org.openelisglobal.testconfiguration.controller.TestModifyEntryController.ResultLimitParams;
import org.openelisglobal.testconfiguration.controller.TestModifyEntryController.SampleTypeListAndTestOrder;
import org.openelisglobal.testconfiguration.controller.TestModifyEntryController.TestAddParams;
import org.openelisglobal.testconfiguration.controller.TestModifyEntryController.TestSet;
import org.openelisglobal.testconfiguration.form.TestModifyEntryForm;
import org.openelisglobal.testconfiguration.service.TestModifyService;
import org.openelisglobal.testconfiguration.validator.TestModifyEntryFormValidator;
import org.openelisglobal.testresult.service.TestResultService;
import org.openelisglobal.testresult.valueholder.TestResult;
import org.openelisglobal.typeofsample.service.TypeOfSampleService;
import org.openelisglobal.typeofsample.service.TypeOfSampleTestService;
import org.openelisglobal.typeofsample.valueholder.TypeOfSample;
import org.openelisglobal.typeofsample.valueholder.TypeOfSampleTest;
import org.openelisglobal.typeoftestresult.service.TypeOfTestResultService;
import org.openelisglobal.typeoftestresult.service.TypeOfTestResultServiceImpl;
import org.openelisglobal.unitofmeasure.service.UnitOfMeasureService;
import org.openelisglobal.unitofmeasure.valueholder.UnitOfMeasure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/rest")
public class TestModifyEntryRestController extends BaseController {

    private static final String[] ALLOWED_FIELDS = new String[] { "jsonWad", "testId", "loinc" };

    @Autowired
    private TestModifyEntryFormValidator formValidator;
    @Autowired
    private DictionaryService dictionaryService;
    @Autowired
    private PanelService panelService;
    @Autowired
    private TypeOfSampleService typeOfSampleService;
    @Autowired
    private TypeOfSampleTestService typeOfSampleTestService;
    @Autowired
    private TestService testService;
    @Autowired
    private TestResultService testResultService;
    @Autowired
    private ResultLimitService resultLimitService;
    @Autowired
    private UnitOfMeasureService unitOfMeasureService;
    @Autowired
    private TestModifyService testModifyService;
    @Autowired
    private LocalizationService localizationService;
    @Autowired
    private TestSectionService testSectionService;
    @Autowired
    private TestModifyEntryController testModifyEntryController;
    @Autowired
    private TestAdditionalFieldService testAdditionalFieldService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @GetMapping(value = "/TestModifyEntry")
    public TestModifyEntryForm showTestModifyEntry(
            @RequestParam(value = "sampleType", required = false) String sampleTypeParam,
            @RequestParam(value = "testSection", required = false) String testSectionParam,
            HttpServletRequest request) {

        TestModifyEntryForm form = new TestModifyEntryForm();
        setupDisplayItems(form, sampleTypeParam, testSectionParam);

        // return findForward(FWD_SUCCESS, form);
        return form;

    }

    private void setupDisplayItems(TestModifyEntryForm form) {
        setupDisplayItems(form, null, null);
    }

    private void setupDisplayItems(TestModifyEntryForm form, String sampleTypeParam, String testSectionParam) {

        List<IdValuePair> allSampleTypesList = new ArrayList<>();
        allSampleTypesList.addAll(DisplayListService.getInstance().getList(ListType.SAMPLE_TYPE_ACTIVE));
        allSampleTypesList.addAll(DisplayListService.getInstance().getList(ListType.SAMPLE_TYPE_INACTIVE));

        form.setSampleTypeList(allSampleTypesList);
        form.setPanelList(DisplayListService.getInstance().getList(ListType.PANELS));
        form.setResultTypeList(DisplayListService.getInstance().getList(ListType.RESULT_TYPE_LOCALIZED));
        form.setUomList(DisplayListService.getInstance().getList(ListType.UNIT_OF_MEASURE));
        form.setLabUnitList(DisplayListService.getInstance().getList(ListType.TEST_SECTION_ACTIVE));
        form.setAgeRangeList(SpringContext.getBean(ResultLimitService.class).getPredefinedAgeRanges());
        form.setDictionaryList(DisplayListService.getInstance().getList(ListType.DICTIONARY_TEST_RESULTS));
        form.setGroupedDictionaryList(createGroupedDictionaryList());
        // form.setTestList(DisplayListService.getInstance().getFreshList(DisplayListService.ListType.ALL_TESTS));

        // Only include testCatBeanList when a filter is applied to avoid returning the
        // full catalogue on initial page load
        List<TestCatalogBean> testCatBeanList = new ArrayList<>();
        if (StringUtils.isBlank(sampleTypeParam) && StringUtils.isBlank(testSectionParam)) {
            testCatBeanList = new ArrayList<>();
        } else {
            testCatBeanList = createTestCatBeanList(sampleTypeParam, testSectionParam);
        }
        form.setTestCatBeanList(testCatBeanList);
    }

    private List<TestCatalogBean> createTestCatBeanList(String sampleTypeParam, String testSectionParam) {
        List<TestCatalogBean> beanList = new ArrayList<>();

        List<Test> testList = testService.getAllTests(false);

        // Apply server-side filtering if parameters are provided
        if (StringUtils.isNotBlank(sampleTypeParam)) {
            testList = filterTestsBySampleType(testList, sampleTypeParam);
        } else if (StringUtils.isNotBlank(testSectionParam)) {
            testList = filterTestsByTestSection(testList, testSectionParam);
        }

        for (Test test : testList) {

            TestCatalogBean bean = new TestCatalogBean();
            TestService testService = SpringContext.getBean(TestService.class);
            String resultType = testService.getResultType(test);
            bean.setId(test.getId());
            bean.setLocalization(test.getLocalizedTestName());
            bean.setReportLocalization(test.getLocalizedReportingName());
            if (test.getSortOrder() != null) {
                bean.setTestSortOrder(Integer.parseInt(test.getSortOrder()));
            }

            bean.setTestUnit(testService.getTestSectionName(test));
            bean.setPanel(createPanelList(testService, test));
            bean.setPanelIds(createPanelIds(testService, test));
            bean.setResultType(resultType);
            TypeOfSample typeOfSample = testService.getTypeOfSample(test);
            bean.setSampleType(typeOfSample != null ? typeOfSample.getLocalizedName() : "n/a");
            bean.setSampleTypeId(typeOfSample != null ? typeOfSample.getId() : null);
            Boolean orderable = test.getOrderable();
            bean.setOrderable(orderable != null && orderable ? "Orderable" : "Not orderable");
            Boolean notifyResults = test.isNotifyResults();
            bean.setNotifyResults(notifyResults != null ? notifyResults : false);
            bean.setInLabOnly(test.isInLabOnly());
            Boolean antimicrobialResistance = test.getAntimicrobialResistance();
            bean.setAntimicrobialResistance(antimicrobialResistance != null ? antimicrobialResistance : false);
            bean.setLoinc(test.getLoinc());
            bean.setActive(test.isActive() ? "Active" : "Not active");
            bean.setUom(testService.getUOM(test, false));
            bean.setAdditionalFields(testAdditionalFieldService.getFieldsForTest(test.getId(), false));
            if (TypeOfTestResultServiceImpl.ResultType.NUMERIC.matches(resultType)
                    && testResultService.getAllActiveTestResultsPerTest(test).size() != 0) {
                bean.setSignificantDigits(
                        testResultService.getAllActiveTestResultsPerTest(test).get(0).getSignificantDigits());
                bean.setHasLimitValues(true);
                bean.setResultLimits(getResultLimits(test, bean.getSignificantDigits()));
            }
            bean.setHasDictionaryValues(
                    TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(bean.getResultType()));
            if (bean.isHasDictionaryValues()) {
                bean.setDictionaryValues(createDictionaryValues(testService, test));
                bean.setReferenceValue(createReferenceValueForDictionaryType(test));
                bean.setDictionaryIds(createDictionaryIds(testService, test));
                bean.setReferenceId(createReferenceIdForDictionaryType(test));
                bean.setReferenceId(getDictionaryIdByDictEntry(bean.getReferenceValue(), bean.getDictionaryIds(),
                        bean.getDictionaryValues()));
            }
            beanList.add(bean);
        }

        Collections.sort(beanList, new Comparator<TestCatalogBean>() {
            @Override
            public int compare(TestCatalogBean o1, TestCatalogBean o2) {
                // sort by test section, sample type, panel, sort order
                int comparison = o1.getTestUnit().compareTo(o2.getTestUnit());
                if (comparison != 0) {
                    return comparison;
                }

                comparison = o1.getSampleType().compareTo(o2.getSampleType());
                if (comparison != 0) {
                    return comparison;
                }

                comparison = o1.getPanel().compareTo(o2.getPanel());
                if (comparison != 0) {
                    return comparison;
                }

                return o1.getTestSortOrder() - o2.getTestSortOrder();
            }
        });

        return beanList;
    }

    private List<ResultLimitBean> getResultLimits(Test test, String significantDigits) {
        List<ResultLimitBean> limitBeans = new ArrayList<>();

        List<ResultLimit> resultLimitList = SpringContext.getBean(ResultLimitService.class).getResultLimits(test);

        Collections.sort(resultLimitList, new Comparator<ResultLimit>() {
            @Override
            public int compare(ResultLimit o1, ResultLimit o2) {
                return (int) (o1.getMinAge() - o2.getMinAge());
            }
        });

        for (ResultLimit limit : resultLimitList) {
            ResultLimitBean bean = new ResultLimitBean();
            bean.setNormalRange(SpringContext.getBean(ResultLimitService.class).getDisplayReferenceRange(limit,
                    significantDigits, "-"));
            bean.setValidRange(SpringContext.getBean(ResultLimitService.class).getDisplayValidRange(limit,
                    significantDigits, "-"));
            bean.setReportingRange(SpringContext.getBean(ResultLimitService.class).getDisplayReportingRange(limit,
                    significantDigits, "-"));
            bean.setCriticalRange(SpringContext.getBean(ResultLimitService.class).getDisplayCriticalRange(limit,
                    significantDigits, "-"));
            bean.setGender(limit.getGender());
            bean.setAgeRange(SpringContext.getBean(ResultLimitService.class).getDisplayAgeRange(limit, "-"));
            limitBeans.add(bean);
        }
        return limitBeans;
    }

    private String createReferenceValueForDictionaryType(Test test) {
        List<ResultLimit> resultLimits = SpringContext.getBean(ResultLimitService.class).getResultLimits(test);

        if (resultLimits.isEmpty()) {
            return "n/a";
        }

        return SpringContext.getBean(ResultLimitService.class).getDisplayReferenceRange(resultLimits.get(0), null,
                null);
    }

    private List<String> createDictionaryValues(TestService testService, Test test) {
        List<String> dictionaryList = new ArrayList<>();
        List<TestResult> testResultList = testService.getPossibleTestResults(test);
        for (TestResult testResult : testResultList) {
            CollectionUtils.addIgnoreNull(dictionaryList, getDictionaryValue(testResult));
        }

        return dictionaryList;
    }

    private String getDictionaryValue(TestResult testResult) {

        if (TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(testResult.getTestResultType())) {
            Dictionary dictionary = dictionaryService.getDataForId(testResult.getValue());
            String displayValue = dictionary.getLocalizedName();

            if ("unknown".equals(displayValue)) {
                displayValue = !org.apache.commons.validator.GenericValidator.isBlankOrNull(dictionary.getDictEntry())
                        ? dictionary.getDictEntry()
                        : dictionary.getLocalAbbreviation();
            }

            if (testResult.getIsQuantifiable()) {
                displayValue += " Qualifiable";
            }
            return displayValue;
        }

        return null;
    }

    private String createReferenceIdForDictionaryType(Test test) {
        List<ResultLimit> resultLimits = SpringContext.getBean(ResultLimitService.class).getResultLimits(test);

        if (resultLimits.isEmpty()) {
            return "n/a";
        }

        return SpringContext.getBean(ResultLimitService.class).getDisplayReferenceRange(resultLimits.get(0), null,
                null);
    }

    private List<String> createDictionaryIds(TestService testService, Test test) {
        List<String> dictionaryList = new ArrayList<>();
        List<TestResult> testResultList = testService.getPossibleTestResults(test);
        for (TestResult testResult : testResultList) {
            CollectionUtils.addIgnoreNull(dictionaryList, getDictionaryId(testResult));
        }

        return dictionaryList;
    }

    private String getDictionaryIdByDictEntry(String dict_entry, List<String> ids, List<String> values) {

        if ("n/a".equals(dict_entry)) {
            return null;
        }

        for (int i = 0; i < ids.size(); i++) {
            if (values.get(i).equals(dict_entry)) {
                return ids.get(i);
            }
        }

        return null;
    }

    private String getDictionaryId(TestResult testResult) {

        if (TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(testResult.getTestResultType())) {
            Dictionary dictionary = dictionaryService.getDataForId(testResult.getValue());
            String displayId = dictionary.getId();

            if ("unknown".equals(displayId)) {
                displayId = !org.apache.commons.validator.GenericValidator.isBlankOrNull(dictionary.getDictEntry())
                        ? dictionary.getDictEntry()
                        : dictionary.getLocalAbbreviation();
            }

            if (testResult.getIsQuantifiable()) {
                displayId += " Qualifiable";
            }
            return displayId;
        }

        return null;
    }

    private String createPanelList(TestService testService, Test test) {
        StringBuilder builder = new StringBuilder();

        List<Panel> panelList = testService.getPanels(test);
        for (Panel panel : panelList) {
            builder.append(localizationService.getLocalizedValueById(panel.getLocalization().getId()));
            builder.append(", ");
        }

        String panelString = builder.toString();
        if (panelString.isEmpty()) {
            panelString = "None";
        } else {
            panelString = panelString.substring(0, panelString.length() - 2);
        }

        return panelString;
    }

    private List<String> createPanelIds(TestService testService, Test test) {
        List<String> panelIds = new ArrayList<>();
        List<Panel> panelList = testService.getPanels(test);
        for (Panel panel : panelList) {
            if (panel != null && panel.getId() != null) {
                panelIds.add(panel.getId());
            }
        }
        return panelIds;
    }

    private List<List<IdValuePair>> createGroupedDictionaryList() {
        List<TestResult> testResults = getSortedTestResults();

        HashSet<String> dictionaryIdGroups = getDictionaryIdGroups(testResults);

        return getGroupedDictionaryPairs(dictionaryIdGroups);
    }

    private List<TestResult> getSortedTestResults() {
        List<TestResult> testResults = testResultService.getAllTestResults();

        Collections.sort(testResults, new Comparator<TestResult>() {
            @Override
            public int compare(TestResult o1, TestResult o2) {
                int result = o1.getTest().getId().compareTo(o2.getTest().getId());

                if (result != 0) {
                    return result;
                }

                return (GenericValidator.isBlankOrNull(o1.getSortOrder())
                        || GenericValidator.isBlankOrNull(o2.getSortOrder())) ? 0
                                : Integer.parseInt(o1.getSortOrder()) - Integer.parseInt(o2.getSortOrder());
            }
        });
        return testResults;
    }

    private HashSet<String> getDictionaryIdGroups(List<TestResult> testResults) {
        HashSet<String> dictionaryIdGroups = new HashSet<>();
        String currentTestId = null;
        String dictionaryIdGroup = null;
        for (TestResult testResult : testResults) {
            if (TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(testResult.getTestResultType())) {
                if (testResult.getTest().getId().equals(currentTestId)) {
                    dictionaryIdGroup += "," + testResult.getValue();
                } else {
                    currentTestId = testResult.getTest().getId();
                    if (dictionaryIdGroup != null) {
                        dictionaryIdGroups.add(dictionaryIdGroup);
                    }

                    dictionaryIdGroup = testResult.getValue();
                }
            }
        }

        if (dictionaryIdGroup != null) {
            dictionaryIdGroups.add(dictionaryIdGroup);
        }

        return dictionaryIdGroups;
    }

    private List<List<IdValuePair>> getGroupedDictionaryPairs(HashSet<String> dictionaryIdGroups) {
        List<List<IdValuePair>> groups = new ArrayList<>();
        for (String group : dictionaryIdGroups) {
            List<IdValuePair> dictionaryPairs = new ArrayList<>();
            for (String id : group.split(",")) {
                Dictionary dictionary = dictionaryService.getDictionaryById(id);
                if (dictionary != null) {
                    dictionaryPairs.add(new IdValuePair(id, dictionary.getLocalizedName()));
                }
            }
            groups.add(dictionaryPairs);
        }

        Collections.sort(groups, new Comparator<List<IdValuePair>>() {
            @Override
            public int compare(List<IdValuePair> o1, List<IdValuePair> o2) {
                return o1.size() - o2.size();
            }
        });
        return groups;
    }

    @PostMapping(value = "/TestModifyEntry")
    public TestModifyEntryForm postTestModifyEntry(HttpServletRequest request,
            @RequestBody @Valid TestModifyEntryForm form, BindingResult result) {
        formValidator.validate(form, result);
        if (result.hasErrors()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Validation failed for test modification");
        }
        String currentUserId = getSysUserId(request);
        String changeList = form.getJsonWad();

        JSONParser parser = new JSONParser();
        JSONObject obj = null;
        try {
            obj = (JSONObject) parser.parse(changeList);
        } catch (ParseException e) {
            LogEvent.logError(e);
        }

        TestAddParams testAddParams = extractTestAddParms(obj, parser);
        if (StringUtils.isBlank(testAddParams.testId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing testId in TestModifyEntry payload");
        }

        Localization nameLocalization = createNameLocalization(testAddParams);
        Localization reportingNameLocalization = createReportingNameLocalization(testAddParams);

        List<TestSet> testSets;
        try {
            testSets = createTestSets(testAddParams);
        } catch (Exception e) {
            LogEvent.logError(e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    StringUtils.defaultIfBlank(e.getMessage(), "Invalid TestModifyEntry payload"), e);
        }

        try {
            testModifyService.updateTestSets(testSets, testAddParams, nameLocalization, reportingNameLocalization,
                    currentUserId);
        } catch (HibernateException e) {
            LogEvent.logError(e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Database error while modifying test",
                    e);
        } catch (Exception e) {
            LogEvent.logError(e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    StringUtils.defaultIfBlank(e.getMessage(), "Error while modifying test"), e);
        }

        testService.refreshTestNames();
        SpringContext.getBean(TypeOfSampleService.class).clearCache();

        return form;
    }

    private void createPanelItems(ArrayList<PanelItem> panelItems, TestAddParams testAddParams) {
        for (String panelId : testAddParams.panelList) {
            PanelItem panelItem = new PanelItem();
            panelItem.setPanel(panelService.getPanelById(panelId));
            panelItems.add(panelItem);
        }
    }

    private void createTestResults(ArrayList<TestResult> testResults, String significantDigits,
            TestAddParams testAddParams) {
        TypeOfTestResultServiceImpl.ResultType type = SpringContext.getBean(TypeOfTestResultService.class)
                .getResultTypeById(testAddParams.resultTypeId);
        if (type == null) {
            throw new IllegalArgumentException("Invalid result type id: " + testAddParams.resultTypeId);
        }

        if (TypeOfTestResultServiceImpl.ResultType.isTextOnlyVariant(type)
                || TypeOfTestResultServiceImpl.ResultType.isNumeric(type)) {
            TestResult testResult = new TestResult();
            testResult.setTestResultType(type.getCharacterValue());
            testResult.setSortOrder("1");
            testResult.setIsActive(true);
            testResult.setSignificantDigits(significantDigits);
            testResults.add(testResult);
        } else if (TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(type.getCharacterValue())) {
            if (testAddParams.dictionaryParamList == null || testAddParams.dictionaryParamList.isEmpty()) {
                throw new IllegalArgumentException(
                        "Dictionary result type requires at least one dictionary value.");
            }
            int sortOrder = 10;
            for (DictionaryParams params : testAddParams.dictionaryParamList) {
                TestResult testResult = new TestResult();
                testResult.setTestResultType(type.getCharacterValue());
                testResult.setSortOrder(String.valueOf(sortOrder));
                sortOrder += 10;
                testResult.setIsActive(true);
                testResult.setValue(params.dictionaryId);
                testResult.setDefault(params.isDefault);
                testResult.setIsQuantifiable(params.isQuantifiable);
                testResults.add(testResult);
            }
        }
    }

    private Localization createNameLocalization(TestAddParams testAddParams) {
        return LocalizationServiceImpl.createNewLocalization(testAddParams.testNameEnglish,
                testAddParams.testNameFrench, LocalizationServiceImpl.LocalizationType.TEST_NAME);
    }

    private Localization createReportingNameLocalization(TestAddParams testAddParams) {
        return LocalizationServiceImpl.createNewLocalization(testAddParams.testReportNameEnglish,
                testAddParams.testReportNameFrench, LocalizationServiceImpl.LocalizationType.REPORTING_TEST_NAME);
    }

    private List<TestSet> createTestSets(TestAddParams testAddParams) {
        Double lowValid = null;
        Double highValid = null;
        Double lowReportingRange = null;
        Double highReportingRange = null;
        Double lowCritical = null;
        Double highCritical = null;
        String significantDigits = testAddParams.significantDigits;
        boolean numericResults = TypeOfTestResultServiceImpl.ResultType.isNumericById(testAddParams.resultTypeId);
        boolean dictionaryResults = TypeOfTestResultServiceImpl.ResultType
                .isDictionaryVarientById(testAddParams.resultTypeId);
        List<TestSet> testSets = new ArrayList<>();
        UnitOfMeasure uom = null;
        if (!org.apache.commons.validator.GenericValidator.isBlankOrNull(testAddParams.uomId)
                || "0".equals(testAddParams.uomId)) {
            uom = unitOfMeasureService.getUnitOfMeasureById(testAddParams.uomId);
        }
        TestSection testSection = testSectionService.get(testAddParams.testSectionId);

        if (numericResults) {
            lowValid = StringUtil.doubleWithInfinity(testAddParams.lowValid);
            highValid = StringUtil.doubleWithInfinity(testAddParams.highValid);
            lowReportingRange = StringUtil.doubleWithInfinity(testAddParams.lowReportingRange);
            highReportingRange = StringUtil.doubleWithInfinity(testAddParams.highReportingRange);
            lowCritical = StringUtil.doubleWithInfinity(testAddParams.lowCritical);
            highCritical = StringUtil.doubleWithInfinity(testAddParams.highCritical);
        }
        // The number of test sets depend on the number of sampleTypes
        for (int i = 0; i < testAddParams.sampleList.size(); i++) {
            TypeOfSample typeOfSample = typeOfSampleService
                    .getTypeOfSampleById(testAddParams.sampleList.get(i).sampleTypeId);
            if (typeOfSample == null) {
                continue;
            }
            TestSet testSet = testModifyEntryController.new TestSet();
            Test test = new Test();
            test.setId(testAddParams.testId);

            test.setUnitOfMeasure(uom);
            test.setDescription(testAddParams.testNameEnglish + "(" + typeOfSample.getDescription() + ")");
            test.setLocalCode(testAddParams.testNameEnglish);
            test.setIsActive(testAddParams.active);
            test.setOrderable("Y".equals(testAddParams.orderable));
            test.setNotifyResults("Y".equals(testAddParams.notifyResults));
            test.setInLabOnly("Y".equals(testAddParams.inLabOnly));
            test.setAntimicrobialResistance("Y".equals(testAddParams.antimicrobialResistance));
            test.setIsReportable("N");
            test.setTestSection(testSection);
            if (GenericValidator.isBlankOrNull(test.getGuid())) {
                test.setGuid(String.valueOf(UUID.randomUUID()));
            }
            ArrayList<String> orderedTests = testAddParams.sampleList.get(i).orderedTests;
            for (int j = 0; j < orderedTests.size(); j++) {
                if ("0".equals(orderedTests.get(j))) {
                    test.setSortOrder(String.valueOf(j));
                    testSet.sortedTests.add(test);
                } else {
                    Test orderedTest = SpringContext.getBean(TestService.class).get(orderedTests.get(j));
                    orderedTest.setSortOrder(String.valueOf(j));
                    testSet.sortedTests.add(orderedTest);
                }
            }

            testSet.test = test;

            TypeOfSampleTest typeOfSampleTest = new TypeOfSampleTest();
            typeOfSampleTest.setTypeOfSampleId(typeOfSample.getId());
            testSet.sampleTypeTest = typeOfSampleTest;

            createPanelItems(testSet.panelItems, testAddParams);
            createTestResults(testSet.testResults, significantDigits, testAddParams);
            if (numericResults) {
                testSet.resultLimits = createResultLimits(lowValid, highValid, lowReportingRange, highReportingRange,
                        testAddParams, highCritical, lowCritical);
            } else if (dictionaryResults) {
                testSet.resultLimits = createDictionaryResultLimit(testAddParams);
            }

            testSets.add(testSet);
        }

        return testSets;
    }

    private ArrayList<ResultLimit> createDictionaryResultLimit(TestAddParams testAddParams) {

        List<TestResult> testResults = testResultService.getActiveTestResultsByTest(testAddParams.testId);
        for (int i = 0; i < testResults.size(); i++) {
            testResults.get(i).setIsActive(false);
        }
        testResultService.updateAll(testResults);

        ArrayList<ResultLimit> resultLimits = new ArrayList<>();
        if (!org.apache.commons.validator.GenericValidator.isBlankOrNull(testAddParams.dictionaryReferenceId)) {
            ResultLimit limit = new ResultLimit();
            limit.setResultTypeId(testAddParams.resultTypeId);
            limit.setDictionaryNormalId(testAddParams.dictionaryReferenceId);
            resultLimits.add(limit);
        }

        return resultLimits;
    }

    private ArrayList<ResultLimit> createResultLimits(Double lowValid, Double highValid, Double lowReportingRange,
            Double highReportingRange, TestAddParams testAddParams, Double highCritical, Double lowCritical) {
        ArrayList<ResultLimit> resultLimits = new ArrayList<>();
        for (ResultLimitParams params : testAddParams.limits) {
            ResultLimit limit = new ResultLimit();
            limit.setResultTypeId(testAddParams.resultTypeId);
            limit.setGender(params.gender);
            limit.setMinAge(StringUtil.doubleWithInfinity(params.lowAge));
            limit.setMaxAge(StringUtil.doubleWithInfinity(params.highAge));
            limit.setLowNormal(StringUtil.doubleWithInfinity(params.lowNormalLimit));
            limit.setHighNormal(StringUtil.doubleWithInfinity(params.highNormalLimit));
            limit.setLowValid(lowValid);
            limit.setHighValid(highValid);
            if (lowReportingRange != null && highReportingRange != null && lowCritical != null
                    && highCritical != null) {
                limit.setLowReportingRange(lowReportingRange);
                limit.setHighReportingRange(highReportingRange);
                limit.setLowCritical(lowCritical);
                limit.setHighCritical(highCritical);
            }
            resultLimits.add(limit);
        }

        return resultLimits;
    }

    private TestAddParams extractTestAddParms(JSONObject obj, JSONParser parser) {
        TestAddParams testAddParams = testModifyEntryController.new TestAddParams();
        try {

            testAddParams.testId = asString(obj.get("testId"));
            if (StringUtils.isBlank(testAddParams.testId)) {
                testAddParams.testId = asString(obj.get("id"));
            }
            testAddParams.testNameEnglish = asString(obj.get("testNameEnglish"));
            testAddParams.testNameFrench = asString(obj.get("testNameFrench"));
            testAddParams.testReportNameEnglish = asString(obj.get("testReportNameEnglish"));
            testAddParams.testReportNameFrench = asString(obj.get("testReportNameFrench"));
            testAddParams.testSectionId = asString(obj.get("testSection"));
            testAddParams.dictionaryReferenceId = asString(obj.get("dictionaryReference"));
            extractPanels(obj, parser, testAddParams);
            testAddParams.uomId = asString(obj.get("uom"));
            testAddParams.loinc = asString(obj.get("loinc"));
            testAddParams.resultTypeId = asString(obj.get("resultType"));
            extractSampleTypes(obj, parser, testAddParams);
            extractAdditionalFields(obj, testAddParams);
            testAddParams.active = asString(obj.get("active"));
            testAddParams.orderable = asString(obj.get("orderable"));
            testAddParams.notifyResults = asString(obj.get("notifyResults"));
            testAddParams.inLabOnly = asString(obj.get("inLabOnly"));
            testAddParams.antimicrobialResistance = asString(obj.get("antimicrobialResistance"));
            if (TypeOfTestResultServiceImpl.ResultType.isNumericById(testAddParams.resultTypeId)) {
                testAddParams.lowValid = obj.get("lowValid").toString();
                testAddParams.highValid = obj.get("highValid").toString();
                testAddParams.lowReportingRange = obj.get("lowReportingRange").toString();
                testAddParams.highReportingRange = obj.get("highReportingRange").toString();
                testAddParams.lowCritical = obj.get("lowCritical").toString();
                testAddParams.highCritical = obj.get("highCritical").toString();
                testAddParams.significantDigits = obj.get("significantDigits").toString();
                extractLimits(obj, parser, testAddParams);
            } else if (TypeOfTestResultServiceImpl.ResultType.isDictionaryVarientById(testAddParams.resultTypeId)) {
                populateDictionaryParams(obj, testAddParams);
            }

        } catch (ParseException e) {
            LogEvent.logError(e);
        }

        return testAddParams;
    }

    private void populateDictionaryParams(JSONObject obj, TestAddParams testAddParams) {
        String defaultTestResult = asString(obj.get("defaultTestResult"));
        Object rawDictionary = obj.get("dictionary");

        if (rawDictionary instanceof JSONArray dictionaryArray && !dictionaryArray.isEmpty()) {
            for (Object dictionaryItem : dictionaryArray) {
                if (!(dictionaryItem instanceof JSONObject)) {
                    continue;
                }
                JSONObject dictionaryObject = (JSONObject) dictionaryItem;
                String dictionaryId = asString(dictionaryObject.get("id"));
                if (StringUtils.isBlank(dictionaryId)) {
                    continue;
                }
                DictionaryParams params = testModifyEntryController.new DictionaryParams();
                params.dictionaryId = dictionaryId;
                params.isQuantifiable = "Y".equals(asString(dictionaryObject.get("qualified")));
                params.isDefault = StringUtils.equals(dictionaryId, defaultTestResult);
                testAddParams.dictionaryParamList.add(params);
            }
        }

        if (!testAddParams.dictionaryParamList.isEmpty()) {
            return;
        }

        // Defensive fallback for modify flow: preserve existing dictionary setup
        // if the client payload omitted dictionary values.
        List<TestResult> existingResults = testResultService.getActiveTestResultsByTest(testAddParams.testId);
        for (TestResult existingResult : existingResults) {
            if (!TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(existingResult.getTestResultType())) {
                continue;
            }
            DictionaryParams params = testModifyEntryController.new DictionaryParams();
            params.dictionaryId = existingResult.getValue();
            params.isQuantifiable = existingResult.getIsQuantifiable();
            params.isDefault = existingResult.getDefault();
            testAddParams.dictionaryParamList.add(params);
        }

        if (StringUtils.isNotBlank(testAddParams.dictionaryReferenceId) && !"0".equals(testAddParams.dictionaryReferenceId)) {
            return;
        }

        List<ResultLimit> existingLimits = resultLimitService.getAllResultLimitsForTest(testAddParams.testId);
        if (!existingLimits.isEmpty()) {
            String existingDictionaryNormalId = existingLimits.get(0).getDictionaryNormalId();
            if (StringUtils.isNotBlank(existingDictionaryNormalId)) {
                testAddParams.dictionaryReferenceId = existingDictionaryNormalId;
            }
        }
    }

    private void extractLimits(JSONObject obj, JSONParser parser, TestAddParams testAddParams) throws ParseException {
        String lowAge = "0";
        if (obj.containsKey("resultLimits")) {
            JSONArray limitArray = (JSONArray) obj.get("resultLimits");
            String globalLowCritical = obj.get("lowCritical").toString();
            String globalHighCritical = obj.get("highCritical").toString();
            String globalLowReporting = obj.get("lowReportingRange").toString();
            String globalHighReporting = obj.get("highReportingRange").toString();
            String globalReportingRange = globalLowReporting + " - " + globalHighReporting;
            for (int i = 0; i < limitArray.size(); i++) {
                ResultLimitParams params = testModifyEntryController.new ResultLimitParams();
                Boolean gender = (Boolean) ((JSONObject) limitArray.get(i)).get("gender");
                if (gender) {
                    params.gender = "M";
                }
                String highAge = (((JSONObject) limitArray.get(i)).get("highAgeRange")).toString();
                params.displayRange = globalReportingRange;
                params.lowNormalLimit = (((JSONObject) limitArray.get(i)).get("lowNormal")).toString();
                params.highNormalLimit = (((JSONObject) limitArray.get(i)).get("highNormal")).toString();
                params.lowCritical = globalLowCritical;
                params.highCritical = globalHighCritical;
                params.lowAge = lowAge;
                params.highAge = highAge;
                testAddParams.limits.add(params);

                if (gender) {
                    params = testModifyEntryController.new ResultLimitParams();
                    params.gender = "F";
                    params.lowNormalLimit = (((JSONObject) limitArray.get(i)).get("lowNormalFemale")).toString();
                    params.highNormalLimit = (((JSONObject) limitArray.get(i)).get("highNormalFemale")).toString();
                    params.lowAge = lowAge;
                    params.highAge = highAge;
                    testAddParams.limits.add(params);
                }

                lowAge = highAge;
            }
        }
    }

    private void extractPanels(JSONObject obj, JSONParser parser, TestAddParams testAddParams) throws ParseException {
        Object rawPanels = obj.get("panels");
        if (!(rawPanels instanceof JSONArray panelArray)) {
            return;
        }

        for (int i = 0; i < panelArray.size(); i++) {
            if (!(panelArray.get(i) instanceof JSONObject)) {
                continue;
            }
            String panelId = asString(((JSONObject) panelArray.get(i)).get("id"));
            if (StringUtils.isNotBlank(panelId)) {
                testAddParams.panelList.add(panelId);
            }
        }
    }

    private void extractSampleTypes(JSONObject obj, JSONParser parser, TestAddParams testAddParams)
            throws ParseException {
        Object rawSampleTypes = obj.get("sampleTypes");
        if (rawSampleTypes instanceof JSONArray sampleTypeArray && !sampleTypeArray.isEmpty()) {
            for (int i = 0; i < sampleTypeArray.size(); i++) {
                if (!(sampleTypeArray.get(i) instanceof JSONObject)) {
                    continue;
                }
                JSONObject sampleTypeObject = (JSONObject) sampleTypeArray.get(i);
                String sampleTypeId = asString(sampleTypeObject.get("typeId"));
                if (StringUtils.isBlank(sampleTypeId)) {
                    continue;
                }

                SampleTypeListAndTestOrder sampleTypeTests = testModifyEntryController.new SampleTypeListAndTestOrder();
                sampleTypeTests.sampleTypeId = sampleTypeId;

                Object rawTests = sampleTypeObject.get("tests");
                if (rawTests instanceof JSONArray testArray) {
                    for (int j = 0; j < testArray.size(); j++) {
                        if (!(testArray.get(j) instanceof JSONObject)) {
                            continue;
                        }
                        String orderedTestId = asString(((JSONObject) testArray.get(j)).get("id"));
                        if (StringUtils.isNotBlank(orderedTestId)) {
                            sampleTypeTests.orderedTests.add(orderedTestId);
                        }
                    }
                }
                if (sampleTypeTests.orderedTests.isEmpty()) {
                    sampleTypeTests.orderedTests.add("0");
                }
                testAddParams.sampleList.add(sampleTypeTests);
            }
        }

        if (!testAddParams.sampleList.isEmpty()) {
            return;
        }

        // Defensive fallback for modify flow when sampleTypes are omitted by client.
        List<TypeOfSampleTest> existingSampleTypeTests = typeOfSampleTestService
                .getTypeOfSampleTestsForTest(testAddParams.testId);
        for (TypeOfSampleTest existingSampleTypeTest : existingSampleTypeTests) {
            String sampleTypeId = asString(existingSampleTypeTest.getTypeOfSampleId());
            if (StringUtils.isBlank(sampleTypeId)) {
                continue;
            }
            SampleTypeListAndTestOrder sampleTypeTests = testModifyEntryController.new SampleTypeListAndTestOrder();
            sampleTypeTests.sampleTypeId = sampleTypeId;
            sampleTypeTests.orderedTests.add("0");
            testAddParams.sampleList.add(sampleTypeTests);
        }
    }

    private void extractAdditionalFields(JSONObject obj, TestAddParams testAddParams) {
        Object rawAdditionalFields = obj.get("additionalFields");
        if (!(rawAdditionalFields instanceof JSONArray)) {
            return;
        }

        JSONArray additionalFields = (JSONArray) rawAdditionalFields;
        int fallbackSortOrder = 1;
        for (Object rawField : additionalFields) {
            if (!(rawField instanceof JSONObject)) {
                continue;
            }

            JSONObject fieldObject = (JSONObject) rawField;
            TestAdditionalFieldPayload payload = new TestAdditionalFieldPayload();
            payload.setId(asInteger(fieldObject.get("id"), null));
            payload.setTestId(testAddParams.testId);
            payload.setFieldKey(asString(fieldObject.get("fieldKey")));
            payload.setDisplayName(asString(fieldObject.get("displayName")));
            payload.setFieldType(asString(fieldObject.get("fieldType")));
            payload.setRequired(asBoolean(fieldObject.get("required"), false));
            payload.setActive(asBoolean(fieldObject.get("active"), true));
            payload.setSortOrder(asInteger(fieldObject.get("sortOrder"), fallbackSortOrder));
            payload.setDefaultValue(asString(fieldObject.get("defaultValue")));
            payload.setMaxLength(asInteger(fieldObject.get("maxLength"), null));
            payload.setMetadataJson(asString(fieldObject.get("metadataJson")));

            Object rawOptions = fieldObject.get("options");
            if (rawOptions instanceof JSONArray optionsArray) {
                int fallbackOptionSort = 1;
                for (Object rawOption : optionsArray) {
                    if (!(rawOption instanceof JSONObject)) {
                        continue;
                    }
                    JSONObject optionObject = (JSONObject) rawOption;
                    TestAdditionalFieldOptionPayload optionPayload = new TestAdditionalFieldOptionPayload();
                    optionPayload.setId(asInteger(optionObject.get("id"), null));
                    optionPayload.setOptionKey(asString(optionObject.get("optionKey")));
                    optionPayload.setOptionLabel(asString(optionObject.get("optionLabel")));
                    optionPayload.setActive(asBoolean(optionObject.get("active"), true));
                    optionPayload.setSortOrder(asInteger(optionObject.get("sortOrder"), fallbackOptionSort));
                    payload.getOptions().add(optionPayload);
                    fallbackOptionSort++;
                }
            }

            if (payload.getDisplayName() != null && payload.getFieldType() != null) {
                testAddParams.additionalFields.add(payload);
                fallbackSortOrder++;
            }
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String asString = String.valueOf(value).trim();
        return asString.isEmpty() ? null : asString;
    }

    private Integer asInteger(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private List<Test> filterTestsBySampleType(List<Test> testList, String sampleTypeId) {
        List<Test> filteredTests = new ArrayList<>();
        for (Test test : testList) {
            List<TypeOfSample> sampleTypesForTest = typeOfSampleService.getTypeOfSampleForTest(test.getId());
            if (sampleTypesForTest != null) {
                boolean testMatchesSampleType = sampleTypesForTest.stream()
                        .anyMatch(sampleType -> sampleTypeId.equals(sampleType.getId()));
                if (testMatchesSampleType) {
                    filteredTests.add(test);
                }
            }
        }
        return filteredTests;
    }

    private List<Test> filterTestsByTestSection(List<Test> testList, String testSectionId) {
        List<Test> filteredTests = new ArrayList<>();
        for (Test test : testList) {
            TestSection testSection = test.getTestSection();
            if (testSection != null && testSectionId.equals(testSection.getId())) {
                filteredTests.add(test);
            }
        }
        return filteredTests;
    }

    @Override
    protected String findLocalForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "testModifyDefinition";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "testModifyDefinition";
        } else if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/TestModifyEntry";
        } else {
            return "PageNotFound";
        }
    }

    @Override
    protected String getPageTitleKey() {
        return null;
    }

    @Override
    protected String getPageSubtitleKey() {
        return null;
    }

}
