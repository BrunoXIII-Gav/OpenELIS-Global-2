
package org.openelisglobal.result.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.lang.reflect.InvocationTargetException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.hibernate.StaleObjectStateException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.analysis.valueholder.ResultFile;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.formfields.FormFields;
import org.openelisglobal.common.formfields.FormFields.Field;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.provider.validation.AlphanumAccessionValidator;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.services.DisplayListService.ListType;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.ResultSaveService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.beanAdapters.ResultSaveBeanAdapter;
import org.openelisglobal.common.services.registration.ResultUpdateRegister;
import org.openelisglobal.common.services.registration.interfaces.IResultUpdate;
import org.openelisglobal.common.services.serviceBeans.ResultSaveBean;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.dataexchange.fhir.exception.FhirPersistanceException;
import org.openelisglobal.dataexchange.fhir.exception.FhirTransformationException;
import org.openelisglobal.dataexchange.fhir.service.FhirTransformService;
import org.openelisglobal.dictionary.service.DictionaryService;
import org.openelisglobal.dictionary.valueholder.Dictionary;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.method.service.MethodService;
import org.openelisglobal.note.service.NoteService;
import org.openelisglobal.note.service.NoteServiceImpl.NoteType;
import org.openelisglobal.note.valueholder.Note;
import org.openelisglobal.notifications.dao.NotificationDAO;
import org.openelisglobal.notifications.entity.Notification;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.organization.service.OrganizationService;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.referral.action.beanitems.ReferralItem;
import org.openelisglobal.referral.service.ReferralTypeService;
import org.openelisglobal.referral.valueholder.Referral;
import org.openelisglobal.referral.valueholder.ReferralResult;
import org.openelisglobal.referral.valueholder.ReferralSet;
import org.openelisglobal.referral.valueholder.ReferralStatus;
import org.openelisglobal.referral.valueholder.ReferralType;
import org.openelisglobal.result.action.util.ResultSet;
import org.openelisglobal.result.action.util.ResultUtil;
import org.openelisglobal.result.action.util.ResultsLoadUtility;
import org.openelisglobal.result.action.util.ResultsPaging;
import org.openelisglobal.result.action.util.ResultsUpdateDataSet;
import org.openelisglobal.result.controller.LogbookResultsBaseController;
import org.openelisglobal.result.form.LogbookResultsForm;
import org.openelisglobal.result.form.LogbookResultsForm.LogbookResults;
import org.openelisglobal.result.form.StatusResultsForm;
import org.openelisglobal.result.service.LogbookResultsPersistService;
import org.openelisglobal.result.service.ResultInventoryService;
import org.openelisglobal.result.service.ResultSignatureService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.result.valueholder.ResultInventory;
import org.openelisglobal.result.valueholder.ResultSignature;
import org.openelisglobal.resultlimit.service.ResultLimitService;
import org.openelisglobal.resultlimits.valueholder.ResultLimit;
import org.openelisglobal.role.service.RoleService;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.OrderPriority;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.service.SampleItemService;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.search.service.SearchResultsService;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.statusofsample.util.StatusRules;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.service.UserService;
import org.openelisglobal.test.beanItems.TestResultItem;
import org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload;
import org.openelisglobal.testadditionalfield.service.TestAdditionalFieldService;
import org.openelisglobal.test.service.TestSectionService;
import org.openelisglobal.test.valueholder.TestSection;
import org.openelisglobal.testdependency.service.TestParentChildDependencyService;
import org.openelisglobal.testdependency.valueholder.TestParentChildDependency;
import org.openelisglobal.typeoftestresult.service.TypeOfTestResultServiceImpl;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Controller
@RequestMapping(value = "/rest/")
public class LogbookResultsRestController extends LogbookResultsBaseController {

    private String RESULT_EDIT_ROLE_ID;

    private final String[] ALLOWED_FIELDS = new String[] { "accessionNumber", "collectionDate", "recievedDate",
            "selectedTest", "selectedAnalysisStatus", "selectedSampleStatus", "testSectionId", "methodId", "type",
            "currentPageID", "testResult*.accessionNumber", "testResult*.isModified", "testResult*.analysisId",
            "testResult*.resultId", "testResult*.testId", "testResult*.technicianSignatureId", "testResult*.testKitId",
            "testResult*.resultLimitId", "testResult*.resultType", "testResult*.valid", "testResult*.referralId",
            "testResult*.referralCanceled", "testResult*.considerRejectReason", "testResult*.hasQualifiedResult",
            "testResult*.shadowResultValue", "testResult*.reflexJSONResult", "testResult*.testDate",
            "testResult*.analysisMethod", "testResult*.testMethod", "testResult*.testKitInventoryId",
            "testResult*.forceTechApproval", "testResult*.lowerNormalRange", "testResult*.upperNormalRange",
            "testResult*.significantDigits", "testResult*.resultValue", "testResult*.qualifiedResultValue",
            "testResult*.multiSelectResultValues", "testResult*.testMethod", "testResult*.multiSelectResultValues",
            "testResult*.qualifiedResultValue", "testResult*.qualifiedResultValue", "testResult*.shadowReferredOut",
            "testResult*.referredOut", "testResult*.referralReasonId", "testResult*.technician",
            "testResult*.shadowRejected", "testResult*.rejected", "testResult*.rejectReasonId", "testResult*.note",
            "testResult*.sampleUsageQuantity", "testResult*.parentSampleUsageQuantity",
            "paging.currentPage", "testResult*.resultFile", "testResult*.resultFile.fileName",
            "testResult*.resultFile.fileType", "testResult*.resultFile.base64Content", "testResult*.refer",
            "testResult*.referralItem.referralReasonId", "testResult*.referralItem.referredInstituteId",
            "testResult*.referralItem.referredTestId", "testResult*.referralItem.referredSendDate",
            "testResult*.additionalFieldValues", "testResult*.additionalFieldShadowValues" };

    @Autowired
    private DictionaryService dictionaryService;
    @Autowired
    private ResultSignatureService resultSigService;
    @Autowired
    private ResultInventoryService resultInventoryService;
    @Autowired
    private OrganizationService organizationService;
    @Autowired
    private ResultLimitService resultLimitService;
    @Autowired
    private TestSectionService testSectionService;
    @Autowired
    private LogbookResultsPersistService logbookPersistService;
    @Autowired
    private AnalysisService analysisService;
    @Autowired
    private NoteService noteService;
    @Autowired
    private FhirTransformService fhirTransformService;
    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private SampleService sampleService;
    @Autowired
    private OrderAdditionalFieldService orderAdditionalFieldService;
    @Autowired
    PatientService patientService;
    @Autowired
    SearchResultsService searchService;
    @Autowired
    SampleItemService sampleItemService;
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private SampleHumanService sampleHumanService;
    @Autowired
    private MethodService methodService;
    @Autowired
    private NotificationDAO notificationDAO;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private TestParentChildDependencyService testParentChildDependencyService;
    @Autowired
    private TestAdditionalFieldService testAdditionalFieldService;

    private final String RESULT_SUBJECT = "Result Note";
    private final String REFERRAL_CONFORMATION_ID;
    private static final String REFLEX_ACCESSIONS = "reflex_accessions";

    private LogbookResultsRestController(ReferralTypeService referralTypeService) {
        ReferralType referralType = referralTypeService.getReferralTypeByName("Confirmation");
        if (referralType != null) {
            REFERRAL_CONFORMATION_ID = referralType.getId();
        } else {
            REFERRAL_CONFORMATION_ID = null;
        }
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @GetMapping(value = "LogbookResults", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public LogbookResultsForm showRestLogbookResults(@RequestParam(required = false) String labNumber,
            @RequestParam(required = false) String patientPK, @RequestParam(required = false) String collectionDate,
            @RequestParam(required = false) String recievedDate, @RequestParam(required = false) String selectedTest,
            @RequestParam(required = false) String selectedSampleStatus,
            @RequestParam(required = false) String selectedAnalysisStatus,
            @RequestParam(required = false) String upperRangeAccessionNumber,
            @RequestParam(required = false) boolean doRange,
            @RequestParam(required = false, defaultValue = "false") boolean finished,
            @Validated(LogbookResults.class) @ModelAttribute("form") LogbookResultsForm form, BindingResult result)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        StatusResultsForm statusResultsForm = new StatusResultsForm();
        statusResultsForm.setCollectionDate(collectionDate);
        statusResultsForm.setRecievedDate(recievedDate);
        statusResultsForm.setSelectedTest(selectedTest);
        statusResultsForm.setSelectedSampleStatus(selectedSampleStatus);
        statusResultsForm.setSelectedAnalysisStatus(selectedAnalysisStatus);

        LogbookResultsForm newForm = new LogbookResultsForm();
        if (!(result.hasFieldErrors("type") || result.hasFieldErrors("testSectionId")
                || result.hasFieldErrors("methodId") || result.hasFieldErrors("accessionNumber"))) {
            newForm.setType(form.getType());
            newForm.setTestSectionId(form.getTestSectionId());

            String currentDate = getCurrentDate();
            newForm.setCurrentDate(currentDate);
            newForm.setAccessionNumber(labNumber);
        }
        newForm.setDisplayTestSections(true);
        newForm.setSearchByRange(false);

        return getLogbookResults(request, newForm, statusResultsForm, labNumber, patientPK, upperRangeAccessionNumber,
                doRange, finished);
    }

    private LogbookResultsForm getLogbookResults(HttpServletRequest request, LogbookResultsForm form,
            StatusResultsForm statusResultsForm, String labNumber, String patientPK, String upperRangeAccessionNumber,
            boolean doRange, boolean finished)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        String patientName = "";
        String patientInfo = "";
        Patient patient = null;

        request.getSession().setAttribute(SAVE_DISABLED, TRUE);

        List<TestResultItem> tests = new ArrayList<>();
        List<TestResultItem> filteredTests = new ArrayList<>();

        ResultsPaging paging = new ResultsPaging();
        // TODO: Re-enable after new inventory frontend integration
        // List<InventoryKitItem> inventoryList = new ArrayList<>();
        ResultsLoadUtility resultsLoadUtility = SpringContext.getBean(ResultsLoadUtility.class);
        resultsLoadUtility.setSysUser(getSysUserId(request));

        String requestedPage = request.getParameter("page");

        if (GenericValidator.isBlankOrNull(requestedPage)) {
            requestedPage = "1";
            resultsLoadUtility.addExcludedAnalysisStatus(AnalysisStatus.Canceled);
            resultsLoadUtility.addExcludedAnalysisStatus(AnalysisStatus.SampleRejected);
            new StatusRules().setAllowableStatusForLoadingResults(resultsLoadUtility);

            if (!GenericValidator.isBlankOrNull(form.getTestSectionId())) {
                tests = resultsLoadUtility.getUnfinishedTestResultItemsInTestSection(form.getTestSectionId());
                filteredTests = userService.filterResultsByLabUnitRoles(getSysUserId(request), tests,
                        Constants.ROLE_RESULTS);
                int count = resultsLoadUtility.getTotalCountAnalysisByTestSectionAndStatus(form.getTestSectionId());
                request.setAttribute("analysisCount", count);
                request.setAttribute("pageSize", filteredTests.size());

                TestSection ts = null;
                if (!GenericValidator.isBlankOrNull(form.getTestSectionId())) {
                    ts = testSectionService.get(form.getTestSectionId());
                }
                setRequestType(ts == null ? MessageUtil.getMessage("workplan.unit.types") : ts.getLocalizedName());

                if (ts != null) {
                    // this does not look right what happens after a new page!!!
                    boolean isHaitiClinical = ConfigurationProperties.getInstance()
                            .isPropertyValueEqual(Property.configurationName, "Haiti Clinical");
                    if (resultsLoadUtility.inventoryNeeded()
                            || (isHaitiClinical && ("VCT").equals(ts.getTestSectionName()))) {
                        // TODO: Re-enable after new inventory frontend integration
                        // InventoryUtility inventoryUtility =
                        // SpringContext.getBean(InventoryUtility.class);
                        // inventoryList = inventoryUtility.getExistingActiveInventory();

                        form.setDisplayTestKit(true);
                    }
                }
                form.setSearchFinished(true);
            } else if (!GenericValidator.isBlankOrNull(statusResultsForm.getCollectionDate())
                    || !GenericValidator.isBlankOrNull(statusResultsForm.getRecievedDate())
                    || !GenericValidator.isBlankOrNull(statusResultsForm.getSelectedTest())
                    || !GenericValidator.isBlankOrNull(statusResultsForm.getSelectedAnalysisStatus())
                    || !GenericValidator.isBlankOrNull(statusResultsForm.getSelectedSampleStatus())) {
                tests.clear();
                LogbookStatusResults reactLogbookStatusResults = new LogbookStatusResults(analysisService,
                        sampleService, sampleItemService);

                tests = reactLogbookStatusResults.setSearchResults(statusResultsForm, resultsLoadUtility);
                filteredTests = userService.filterResultsByLabUnitRoles(getSysUserId(request), tests,
                        Constants.ROLE_RESULTS);

                request.setAttribute("pageSize", filteredTests.size());

            } else if (!GenericValidator.isBlankOrNull(form.getAccessionNumber())
                    || !GenericValidator.isBlankOrNull(patientPK)) {
                tests.clear();
                String searchValue = StringUtils.trimToNull(labNumber);
                SampleItem cugSampleItem = resolveSampleItemByCugCode(searchValue);
                if (doRange) {
                    tests = resultsLoadUtility.getUnfinishedTestResultItemsByAccession(labNumber,
                            upperRangeAccessionNumber, doRange, finished);
                    if (tests.isEmpty() && StringUtils.isBlank(upperRangeAccessionNumber)
                            && StringUtils.isNotBlank(labNumber)) {
                        Sample sample = cugSampleItem != null ? cugSampleItem.getSample()
                                : resolveSampleByAccessionOrSearchableValue(labNumber);
                        if (sample != null && !GenericValidator.isBlankOrNull(sample.getId())) {
                            form.setAccessionNumber(sample.getAccessionNumber());
                            patient = getPatient(sample);
                            tests = resultsLoadUtility.getGroupedTestsForSample(sample, patient);
                            tests = filterTestsBySampleItem(tests, cugSampleItem);
                            if (patient != null) {
                                patientName = patientService.getLastFirstName(patient);
                                patientInfo = patient.getNationalId() + ", " + patient.getGender() + ", "
                                        + patient.getBirthDateForDisplay();
                            }
                        }
                    }
                    tests = filterTestsBySampleItem(tests, cugSampleItem);
                } else {
                    resultsLoadUtility.setLockCurrentResults(modifyResultsRoleBased() && userNotInRole(request));
                    LogEvent.logInfo(this.getClass().getSimpleName(), "getLogbookResults",
                            "Searching for sample with search value: " + labNumber);
                    Sample sample = cugSampleItem != null ? cugSampleItem.getSample()
                            : resolveSampleByAccessionOrSearchableValue(labNumber);
                    if (sample != null) {
                        LogEvent.logInfo(this.getClass().getSimpleName(), "getLogbookResults", "Found sample: id="
                                + sample.getId() + ", accessionNumber=" + sample.getAccessionNumber());
                        if (!GenericValidator.isBlankOrNull(sample.getId())) {
                            form.setAccessionNumber(sample.getAccessionNumber());
                            patient = getPatient(sample);

                            tests = resultsLoadUtility.getGroupedTestsForSample(sample, patient);
                            tests = filterTestsBySampleItem(tests, cugSampleItem);
                            LogEvent.logInfo(this.getClass().getSimpleName(), "getLogbookResults",
                                    "getGroupedTestsForSample returned " + tests.size() + " tests for sample "
                                            + sample.getId());
                            if (patient != null) {
                                patientName = patientService.getLastFirstName(patient);
                                patientInfo = patient.getNationalId() + ", " + patient.getGender() + ", "
                                        + patient.getBirthDateForDisplay();
                            }
                        }
                    } else {
                        LogEvent.logWarn(this.getClass().getSimpleName(), "getLogbookResults",
                                "No sample found for labNumber: " + labNumber);
                    }
                }

                // if no test try patientID
                if (tests.isEmpty()) {
                    String statusRules = ConfigurationProperties.getInstance()
                            .getPropertyValueUpperCase(Property.StatusRules);
                    if (statusRules.equals(STATUS_RULES_RETROCI)) {
                        resultsLoadUtility.addExcludedAnalysisStatus(AnalysisStatus.TechnicalRejected);
                    }

                    if (StringUtils.isBlank(patientPK)) {
                        return (form);
                    }
                    patient = patientService.get(patientPK);

                    tests = resultsLoadUtility.getGroupedTestsForPatient(patient);
                    patientName = patientService.getLastFirstName(patient);
                    patientInfo = patient.getNationalId() + ", " + patient.getGender() + ", "
                            + patient.getBirthDateForDisplay();
                }

                filteredTests = userService.filterResultsByLabUnitRoles(getSysUserId(request), tests,
                        Constants.ROLE_RESULTS);
                LogEvent.logInfo(this.getClass().getSimpleName(), "getLogbookResults",
                        "After filterResultsByLabUnitRoles: tests.size()=" + tests.size() + ", filteredTests.size()="
                                + filteredTests.size());

                int count = resultsLoadUtility.getTotalCountAnalysisByAccessionAndStatus(form.getAccessionNumber());

                request.setAttribute("analysisCount", count);
                request.setAttribute("pageSize", filteredTests.size());
                form.setSearchFinished(true);
            } else {
                tests = new ArrayList<>();
            }

            if (ConfigurationProperties.getInstance().isPropertyValueEqual(Property.PATIENT_DATA_ON_RESULTS_BY_ROLE,
                    "true") && !userHasPermissionForModule(request, "PatientResults")) {
                for (TestResultItem resultItem : filteredTests) {
                    resultItem.setPatientInfo("---");
                }
            }

            for (TestResultItem resultItem : filteredTests) {
                Result newResult = new Result();
                if (resultItem.getResult() != null) {
                    newResult.setId(resultItem.getResult().getId());
                    resultItem.setResult(newResult);
                }
            }

            paging.setDatabaseResults(request, form, filteredTests);
            LogEvent.logInfo(this.getClass().getSimpleName(), "getLogbookResults",
                    "After setDatabaseResults: form.getTestResult() size="
                            + (form.getTestResult() != null ? form.getTestResult().size() : 0));

        } else {
            int requestedPageNumber = Integer.parseInt(requestedPage);
            paging.page(request, form, requestedPageNumber);
        }
        form.setDisplayTestKit(false);
        // TODO: Re-enable after new inventory frontend integration
        // List<String> hivKits = new ArrayList<>();
        // List<String> syphilisKits = new ArrayList<>();
        // for (InventoryKitItem item : inventoryList) {
        // if (item.getType().equals("HIV")) {
        // hivKits.add(item.getInventoryLocationId());
        // } else {
        // syphilisKits.add(item.getInventoryLocationId());
        // }
        // }
        // form.setHivKits(hivKits);
        // form.setSyphilisKits(syphilisKits);

        // Temporary fix: Set empty lists
        form.setHivKits(new ArrayList<String>());
        form.setSyphilisKits(new ArrayList<String>());
        // TODO: Re-enable after new inventory frontend integration
        // form.setInventoryItems(inventoryList);

        addFlashMsgsToRequest(request);

        for (TestResultItem resultItem : filteredTests) {
            AddPatientIdToResult(patient, resultItem);
            if (patientName != "")
                resultItem.setPatientName(patientName);
            if (patientInfo != "")
                resultItem.setPatientInfo(patientInfo);
        }

        return (form);
    }

    private void AddPatientIdToResult(Patient patient, TestResultItem resultItem) {
        if (patient != null) {
            resultItem.setPatientId(patient.getId());
        } else if (resultItem.getAccessionNumber() != null) {
            // Si le patient n'est pas défini globalement, le récupérer via l'échantillon
            Sample sample = sampleService.getSampleByAccessionNumber(resultItem.getAccessionNumber());
            if (sample != null) {
                Patient itemPatient = sampleHumanService.getPatientForSample(sample);
                if (itemPatient != null) {
                    resultItem.setPatientId(itemPatient.getId());
                }
            }
        }
    }

    private String getCurrentDate() {
        Date today = Calendar.getInstance().getTime();
        return DateUtil.formatDateAsText(today);
    }

    @PostMapping(value = "LogbookResults", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, List<String>> showReactLogbookResultsUpdate(HttpServletRequest request,
            @Validated(LogbookResultsForm.LogbookResults.class) @RequestBody LogbookResultsForm form,
            BindingResult result) throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        boolean useTechnicianName = ConfigurationProperties.getInstance()
                .isPropertyValueEqual(Property.resultTechnicianName, "true");
        boolean alwaysValidate = ConfigurationProperties.getInstance()
                .isPropertyValueEqual(Property.ALWAYS_VALIDATE_RESULTS, "true");
        boolean supportReferrals = FormFields.getInstance().useField(Field.ResultsReferral);
        Map<String, List<String>> reflexMap = new HashMap<>();
        String statusRuleSet = ConfigurationProperties.getInstance().getPropertyValueUpperCase(Property.StatusRules);

        if ("true".equals(request.getParameter("pageResults"))) {
            getLogbookResults(request, form, null, "", "", null, true, true);
            return reflexMap;
        }

        if (result.hasErrors()) {
            saveErrors(result);
        }

        List<Result> checkPagedResults = (List<Result>) request.getSession()
                .getAttribute(IActionConstants.RESULTS_SESSION_CACHE);
        List<Result> checkResults = (List<Result>) checkPagedResults.get(0);
        if (checkResults.size() == 0) {
            LogEvent.logDebug(this.getClass().getSimpleName(), "LogbookResults()", "Attempted save of stale page.");

            List<TestResultItem> resultList = form.getTestResult();
            for (TestResultItem item : resultList) {
                item.setFailedValidation(true);
                item.setNote("Result has been saved by another user.");
            }

            ResultsUpdateDataSet actionDataSet = new ResultsUpdateDataSet(getSysUserId(request));
            actionDataSet.filterModifiedItems(form.getTestResult());

            Errors errors = actionDataSet.validateModifiedItems();

            if (true) {
                saveErrors(errors);
            }
        }

        List<IResultUpdate> updaters = ResultUpdateRegister.getRegisteredUpdaters();

        ResultsPaging paging = new ResultsPaging();
        paging.updatePagedResults(request, form);
        List<TestResultItem> tests = paging.getResults(request);

        ResultsUpdateDataSet actionDataSet = new ResultsUpdateDataSet(getSysUserId(request));
        actionDataSet.filterModifiedItems(tests);

        Errors errors = actionDataSet.validateModifiedItems();

        if (errors.hasErrors()) {
            saveErrors(errors);
        }

        createResultsFromItems(actionDataSet, supportReferrals, alwaysValidate, useTechnicianName, statusRuleSet);
        createAnalysisOnlyUpdates(actionDataSet);

        try {
            List<Analysis> reflexAnalysises = logbookPersistService.persistDataSet(actionDataSet, updaters,
                    getSysUserId(request));
            reflexMap.put("reflex", reflexAnalysises.stream().filter(e -> !e.getResultCalculated())
                    .map(e -> analysisService.getOrderAccessionNumber(e)).collect(Collectors.toList()));
            reflexMap.put("calculated", reflexAnalysises.stream().filter(e -> e.getResultCalculated())
                    .map(e -> analysisService.getOrderAccessionNumber(e)).collect(Collectors.toList()));
            try {
                fhirTransformService.transformPersistResultsEntryFhirObjects(actionDataSet);
            } catch (FhirTransformationException | FhirPersistanceException e) {
                LogEvent.logError(e);
            }
            List<Analysis> newResultAnalyses = actionDataSet.getNewResults().stream().map(a -> a.result.getAnalysis())
                    .collect(Collectors.toList());
            List<String> systemUserIds = userRoleService.getUserIdsForRole(Constants.ROLE_VALIDATION);
            String message = MessageUtil.getMessage("notification.result.stat");
            for (String userId : systemUserIds) {
                List<Analysis> userAnalyses = userService
                        .filterAnalysesByLabUnitRoles(userId, newResultAnalyses, Constants.ROLE_VALIDATION).stream()
                        .filter(a -> a.getSampleItem().getSample().getPriority().equals(OrderPriority.STAT))
                        .collect(Collectors.toList());

                if (userAnalyses != null && !userAnalyses.isEmpty()) {
                    List<String> userTests = userAnalyses.stream()
                            .map(a -> AlphanumAccessionValidator
                                    .convertAlphaNumLabNumForDisplay(a.getSampleItem().getSample().getAccessionNumber())
                                    + " - " + a.getTest().getLocalizedName())
                            .collect(Collectors.toList());
                    String testString = String.join(", ", userTests);
                    try {
                        Notification notification = new Notification();
                        String notificationMessage = message + testString;
                        if (notificationMessage.length() > 255) {
                            notificationMessage = notificationMessage.substring(0, 255);
                        }
                        notification.setMessage(notificationMessage);
                        notification.setUser(systemUserService.getUserById(userId));
                        notification.setCreatedDate(OffsetDateTime.now());
                        notification.setReadAt(null);
                        notificationDAO.save(notification);
                    } catch (Exception e) {
                        LogEvent.logError(e);
                    }
                }
            }
        } catch (LIMSRuntimeException e) {
            String errorMsg;
            if (e.getCause() instanceof StaleObjectStateException) {
                errorMsg = "errors.OptimisticLockException";
            } else {
                LogEvent.logDebug(e);
                errorMsg = "errors.UpdateException";
            }

            errors.reject(errorMsg, errorMsg);
            saveErrors(errors);
        }

        for (IResultUpdate updater : updaters) {
            try {
                updater.postTransactionalCommitUpdate(actionDataSet);
            } catch (Exception e) {
                LogEvent.logError(this.getClass().getSimpleName(), "showLogbookResultsUpdate",
                        "error doing a post transactional commit");
                LogEvent.logError(e);
            }
        }

        if (GenericValidator.isBlankOrNull(form.getType())) {
        } else {
            Map<String, String> params = new HashMap<>();
            params.put("type", form.getType());
        }
        return reflexMap;
    }

    private void createAnalysisOnlyUpdates(ResultsUpdateDataSet actionDataSet) {
        for (TestResultItem testResultItem : actionDataSet.getAnalysisOnlyChangeResults()) {

            Analysis analysis = analysisService.get(testResultItem.getAnalysisId());
            analysis.setSysUserId(getSysUserId(request));
            analysis.setCompletedDate(DateUtil.convertStringDateToSqlDate(testResultItem.getTestDate()));
            if (testResultItem.getAnalysisMethod() != null) {
                analysis.setAnalysisType(testResultItem.getAnalysisMethod());
            }
            if (!GenericValidator.isBlankOrNull(testResultItem.getTestMethod())) {
                analysis.setMethod(methodService.get(testResultItem.getTestMethod()));
            }
            if (testResultItem.getResultFile() != null) {
                ResultFile resultFile = createResultFile(testResultItem.getResultFile());
                if (resultFile != null) {
                    analysis.setResultFile(resultFile);
                }
            }
            actionDataSet.getModifiedAnalysis().add(analysis);
        }
    }

    private void createResultsFromItems(ResultsUpdateDataSet actionDataSet, boolean supportReferrals,
            boolean alwaysValidate, boolean useTechnicianName, String statusRuleSet) {
        Map<String, SampleItem> sampleItemsBeingUpdated = new HashMap<>();
        Map<String, Analysis> analysisById = new HashMap<>();

        for (TestResultItem testResultItem : actionDataSet.getModifiedItems()) {
            Analysis analysis = analysisService.get(testResultItem.getAnalysisId());
            applyDirectParentSampleUsage(testResultItem, analysis, actionDataSet, sampleItemsBeingUpdated);
            if (analysis != null && !GenericValidator.isBlankOrNull(analysis.getId())) {
                analysisById.put(analysis.getId(), analysis);
            }
        }

        for (TestResultItem testResultItem : actionDataSet.getModifiedItems()) {

            Analysis analysis = analysisById.computeIfAbsent(testResultItem.getAnalysisId(), analysisService::get);
            applyParentChildDependencyAndUsage(testResultItem, analysis, actionDataSet, sampleItemsBeingUpdated);
            analysis.setStatusId(getStatusForTestResult(testResultItem, alwaysValidate));
            analysis.setSysUserId(getSysUserId(request));
            if (!GenericValidator.isBlankOrNull(testResultItem.getTestMethod())) {
                analysis.setMethod(methodService.get(testResultItem.getTestMethod()));
            }
            actionDataSet.getModifiedAnalysis().add(analysis);

            actionDataSet.addToNoteList(noteService.createSavableNote(analysis, NoteType.INTERNAL,
                    testResultItem.getNote(), RESULT_SUBJECT, getSysUserId(request)));

            if (testResultItem.isShadowRejected()) {
                testResultItem.setResultValue("");
                testResultItem.setShadowResultValue("");
                String rejectedReasonId = testResultItem.getRejectReasonId();
                for (IdValuePair rejectReason : DisplayListService.getInstance().getList(ListType.REJECTION_REASONS)) {
                    if (rejectedReasonId.equals(rejectReason.getId())) {
                        actionDataSet.addToNoteList(noteService.createSavableNote(analysis, NoteType.REJECTION_REASON,
                                rejectReason.getValue(), RESULT_SUBJECT, getSysUserId(request)));
                        break;
                    }
                }
            }

            ResultSaveBean bean = ResultSaveBeanAdapter.fromTestResultItem(testResultItem);
            ResultSaveService resultSaveService = new ResultSaveService(analysis, getSysUserId(request));
            // deletable Results will be written to, not read
            List<Result> results = resultSaveService.createResultsFromTestResultItem(bean,
                    actionDataSet.getDeletableResults());

            analysis.setCorrectedSincePatientReport(
                    resultSaveService.isUpdatedResult() && analysisService.patientReportHasBeenDone(analysis));

            if (analysisService.hasBeenCorrectedSinceLastPatientReport(analysis)) {
                Note note = noteService.createSavableNote(analysis, NoteType.EXTERNAL,
                        MessageUtil.getMessage("note.corrected.result"), RESULT_SUBJECT, getSysUserId(request));
                if (!noteService.duplicateNoteExists(note)) {
                    actionDataSet.addToNoteList(noteService.createSavableNote(analysis, NoteType.EXTERNAL,
                            MessageUtil.getMessage("note.corrected.result"), RESULT_SUBJECT, getSysUserId(request)));
                }
            }

            // If there is more than one result then each user selected reflex gets mapped
            // to that result
            for (Result result : results) {
                addResult(result, testResultItem, analysis, results.size() > 1, actionDataSet, useTechnicianName);

                if (analysisShouldBeUpdated(testResultItem, result, supportReferrals)) {
                    updateAnalysis(testResultItem, testResultItem.getTestDate(), analysis, statusRuleSet);
                }
            }
            if (supportReferrals && testResultItem.isRefer()) {
                handleReferrals(testResultItem, testResultItem.getReferralItem(), results, analysis, actionDataSet);
            }
        }
    }

    private void applyParentChildDependencyAndUsage(TestResultItem testResultItem, Analysis analysis,
            ResultsUpdateDataSet actionDataSet, Map<String, SampleItem> sampleItemsBeingUpdated) {
        if (analysis == null || analysis.getTest() == null || analysis.getSampleItem() == null) {
            return;
        }

        TestParentChildDependency dependency = testParentChildDependencyService
                .getActiveByChildTestId(analysis.getTest().getId());
        if (dependency == null || dependency.getParentTest() == null) {
            return;
        }

        Analysis parentAnalysis = analysisService.getAnalysisBySampleItemAndTest(analysis.getSampleItem().getId(),
                dependency.getParentTest().getId());
        if (parentAnalysis == null) {
            throw new IllegalArgumentException("Parent analysis not found for dependent test configuration");
        }

        if (!isCompletedForDependency(parentAnalysis)) {
            throw new IllegalArgumentException("Parent test must be completed before entering child test results");
        }

        analysis.setParentAnalysis(parentAnalysis);

        if (analysis.getSampleUsedQuantity() != null) {
            if (!GenericValidator.isBlankOrNull(testResultItem.getSampleUsageQuantity())) {
                BigDecimal attemptedUsage = parseAndValidateUsageQuantity(testResultItem.getSampleUsageQuantity());
                if (analysis.getSampleUsedQuantity().compareTo(attemptedUsage) != 0) {
                    throw new IllegalArgumentException("Sample usage quantity cannot be changed after first save");
                }
            }
            return;
        }

        boolean requiresUsage = hasEnteredResult(testResultItem) || ResultUtil.isReferred(testResultItem)
                || ResultUtil.isRejected(testResultItem) || ResultUtil.isForcedToAcceptance(testResultItem);

        if (!requiresUsage) {
            return;
        }

        if (GenericValidator.isBlankOrNull(testResultItem.getSampleUsageQuantity())) {
            throw new IllegalArgumentException("Sample usage quantity is required for dependent child tests");
        }

        BigDecimal usageQuantity = parseAndValidateUsageQuantity(testResultItem.getSampleUsageQuantity());
        String sampleUsageSource = normalizeSampleUsageSource(dependency.getSampleUsageSource());
        if (TestParentChildDependency.SAMPLE_USAGE_SOURCE_PARENT_TEST_FIELD.equals(sampleUsageSource)) {
            validateParentFieldBasedUsageLimit(analysis, parentAnalysis, dependency.getParentResultFieldKey(),
                    usageQuantity);
        } else {
            applySampleItemBasedUsage(analysis, actionDataSet, sampleItemsBeingUpdated, usageQuantity);
        }
        analysis.setSampleUsedQuantity(usageQuantity);
    }

    private void applyDirectParentSampleUsage(TestResultItem testResultItem, Analysis analysis,
            ResultsUpdateDataSet actionDataSet, Map<String, SampleItem> sampleItemsBeingUpdated) {
        if (testResultItem == null || analysis == null || analysis.getTest() == null || analysis.getSampleItem() == null) {
            return;
        }

        boolean directSampleUsageEnabled = Boolean.TRUE.equals(analysis.getTest().getDirectSampleUsageEnabled());
        if (testResultItem.isDependentChild()
                || (!directSampleUsageEnabled && !hasActiveChildDependencies(analysis.getTest().getId()))) {
            return;
        }

        if (analysis.getSampleUsedQuantity() != null) {
            if (!GenericValidator.isBlankOrNull(testResultItem.getParentSampleUsageQuantity())) {
                BigDecimal attemptedUsage = parseAndValidateUsageQuantity(testResultItem.getParentSampleUsageQuantity());
                if (analysis.getSampleUsedQuantity().compareTo(attemptedUsage) != 0) {
                    throw new IllegalArgumentException(
                            "Parent sample usage quantity cannot be changed after first save");
                }
            }
            return;
        }

        boolean requiresUsage = hasEnteredResult(testResultItem) || ResultUtil.isReferred(testResultItem)
                || ResultUtil.isRejected(testResultItem) || ResultUtil.isForcedToAcceptance(testResultItem);
        if (!requiresUsage) {
            return;
        }

        if (GenericValidator.isBlankOrNull(testResultItem.getParentSampleUsageQuantity())) {
            throw new IllegalArgumentException("Sample usage quantity is required for tests that consume sample directly");
        }

        BigDecimal usageQuantity = parseAndValidateUsageQuantity(testResultItem.getParentSampleUsageQuantity());
        applySampleItemBasedUsage(analysis, actionDataSet, sampleItemsBeingUpdated, usageQuantity);
        analysis.setSampleUsedQuantity(usageQuantity);
    }

    private boolean hasEnteredResult(TestResultItem testResultItem) {
        String value = testResultItem.getShadowResultValue();
        if (TypeOfTestResultServiceImpl.ResultType.isMultiSelectVariant(testResultItem.getResultType())) {
            return !GenericValidator.isBlankOrNull(testResultItem.getMultiSelectResultValues())
                    && !"{}".equals(testResultItem.getMultiSelectResultValues());
        }

        if (GenericValidator.isBlankOrNull(value)) {
            return false;
        }

        return !(TypeOfTestResultServiceImpl.ResultType.DICTIONARY.matches(testResultItem.getResultType())
                && "0".equals(value));
    }

    private BigDecimal parseAndValidateUsageQuantity(String usageQuantity) {
        try {
            BigDecimal parsed = new BigDecimal(usageQuantity.trim()).setScale(3, RoundingMode.HALF_UP);
            if (parsed.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Sample usage quantity must be greater than zero");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Sample usage quantity must be a valid number");
        }
    }

    private boolean isCompletedForDependency(Analysis analysis) {
        if (analysis == null || GenericValidator.isBlankOrNull(analysis.getStatusId())) {
            return false;
        }

        IStatusService statusService = SpringContext.getBean(IStatusService.class);
        return statusService.matches(analysis.getStatusId(), AnalysisStatus.Finalized)
                || statusService.matches(analysis.getStatusId(), AnalysisStatus.TechnicalAcceptance);
    }

    private String normalizeSampleUsageSource(String sampleUsageSource) {
        if (GenericValidator.isBlankOrNull(sampleUsageSource)) {
            return TestParentChildDependency.SAMPLE_USAGE_SOURCE_SAMPLE_ITEM_REMAINING;
        }
        return sampleUsageSource.trim().toUpperCase();
    }

    private boolean hasActiveChildDependencies(String parentTestId) {
        if (GenericValidator.isBlankOrNull(parentTestId)) {
            return false;
        }

        List<TestParentChildDependency> dependencies = testParentChildDependencyService.getByParentTestId(parentTestId);
        return dependencies != null
                && dependencies.stream().anyMatch(dependency -> dependency != null && Boolean.TRUE.equals(dependency.getActive()));
    }

    private void applySampleItemBasedUsage(Analysis analysis, ResultsUpdateDataSet actionDataSet,
            Map<String, SampleItem> sampleItemsBeingUpdated, BigDecimal usageQuantity) {
        String sampleItemId = analysis.getSampleItem().getId();
        SampleItem sampleItem = sampleItemsBeingUpdated.get(sampleItemId);
        if (sampleItem == null) {
            sampleItem = sampleItemService.get(sampleItemId);
            if (sampleItem == null) {
                throw new IllegalArgumentException("Sample item not found for analysis");
            }
        }

        if (!sampleItem.canAliquot(usageQuantity)) {
            throw new IllegalArgumentException("Insufficient remaining quantity for dependent child test usage");
        }

        sampleItem.decrementRemainingQuantity(usageQuantity);
        sampleItem.setSysUserId(getSysUserId(request));
        sampleItemsBeingUpdated.put(sampleItemId, sampleItem);
        actionDataSet.addModifiedSampleItem(sampleItem);
        analysis.setSampleItem(sampleItem);
    }

    private void validateParentFieldBasedUsageLimit(Analysis analysis, Analysis parentAnalysis, String parentFieldKey,
            BigDecimal usageQuantity) {
        BigDecimal parentCapacity = resolveParentFieldCapacity(parentAnalysis, parentFieldKey);
        BigDecimal alreadyConsumed = getExistingParentChildConsumedUsage(analysis, parentAnalysis);
        BigDecimal remaining = parentCapacity.subtract(alreadyConsumed);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO;
        }
        if (usageQuantity.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Insufficient remaining quantity from parent test field");
        }
    }

    private BigDecimal resolveParentFieldCapacity(Analysis parentAnalysis, String parentFieldKey) {
        if (GenericValidator.isBlankOrNull(parentFieldKey)) {
            throw new IllegalArgumentException("Parent result field key is not configured for dependency");
        }

        List<TestAdditionalFieldPayload> definitions = testAdditionalFieldService
                .getFieldsForTest(parentAnalysis.getTest().getId(), false);
        TestAdditionalFieldPayload targetDefinition = definitions.stream()
                .filter(def -> parentFieldKey.equals(def.getFieldKey())).findFirst().orElse(null);
        if (targetDefinition == null) {
            throw new IllegalArgumentException("Configured parent result field was not found");
        }
        if (!"NUMBER".equalsIgnoreCase(targetDefinition.getFieldType())) {
            throw new IllegalArgumentException("Configured parent result field must be numeric");
        }

        Map<String, String> values = testAdditionalFieldService.getAnalysisValuesForFields(parentAnalysis.getId(),
                definitions);
        String raw = values == null ? null : values.get(parentFieldKey);
        if (GenericValidator.isBlankOrNull(raw)) {
            throw new IllegalArgumentException("Parent result field value is required to consume child sample usage");
        }

        try {
            BigDecimal parsed = new BigDecimal(raw.trim()).setScale(3, RoundingMode.HALF_UP);
            if (parsed.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("Parent result field value must be zero or positive");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parent result field value must be numeric");
        }
    }

    private BigDecimal getExistingParentChildConsumedUsage(Analysis currentAnalysis, Analysis parentAnalysis) {
        if (currentAnalysis == null || currentAnalysis.getSampleItem() == null || parentAnalysis == null) {
            return BigDecimal.ZERO;
        }
        List<Analysis> analyses = analysisService.getAnalysesBySampleItem(currentAnalysis.getSampleItem());
        BigDecimal consumed = BigDecimal.ZERO;
        for (Analysis analysis : analyses) {
            if (analysis == null || analysis.getId() == null || analysis.getSampleUsedQuantity() == null
                    || analysis.getParentAnalysis() == null || analysis.getParentAnalysis().getId() == null) {
                continue;
            }
            if (analysis.getId().equals(currentAnalysis.getId())) {
                continue;
            }
            if (parentAnalysis.getId().equals(analysis.getParentAnalysis().getId())) {
                consumed = consumed.add(analysis.getSampleUsedQuantity());
            }
        }
        return consumed;
    }

    private void handleReferrals(TestResultItem testResultItem, ReferralItem referralItem, List<Result> results,
            Analysis analysis, ResultsUpdateDataSet actionDataSet) {
        // List<Referral> referrals = new ArrayList<>();
        Referral referral = new Referral();
        referral.setFhirUuid(UUID.randomUUID());
        referral.setStatus(ReferralStatus.SENT);
        referral.setSysUserId(actionDataSet.getCurrentUserId());
        referral.setReferralTypeId(REFERRAL_CONFORMATION_ID);
        referral.setRequesterName(testResultItem.getTechnician());

        referral.setRequestDate(new Timestamp(new Date().getTime()));
        referral.setSentDate(DateUtil.convertStringDateToTruncatedTimestamp(referralItem.getReferredSendDate()));
        referral.setRequesterName(referralItem.getReferrer());
        referral.setOrganization(organizationService.get(referralItem.getReferredInstituteId()));
        referral.setAnalysis(analysis);

        referral.setReferralReasonId(referralItem.getReferralReasonId());

        ReferralResult referralResult = new ReferralResult();
        referralResult.setReferralId(referral.getId());
        referralResult.setSysUserId(actionDataSet.getCurrentUserId());
        referralResult.setTestId(referralItem.getReferredTestId());
        if (results.size() == 1) {
            referralResult.setResult(results.get(0));
        }

        ReferralSet referralSet = new ReferralSet();
        referralSet.setReferral(referral);
        referralSet.getExistingReferralResults().add(referralResult);
        actionDataSet.getSavableReferralSets().add(referralSet);

        String originalResultNote = MessageUtil.getMessage("referral.original.result") + ": ";
        if (TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(testResultItem.getResultType())
                || TypeOfTestResultServiceImpl.ResultType.isMultiSelectVariant(testResultItem.getResultType())) {
            if ("0".equals(testResultItem.getResultValue()) || StringUtils.isBlank(testResultItem.getResultValue())) {
                originalResultNote = originalResultNote + "";
            } else {
                Dictionary dictionary = dictionaryService.get(testResultItem.getResultValue());
                if (dictionary.getLocalizedDictionaryName() == null) {
                    originalResultNote = originalResultNote + dictionary.getDictEntry();
                } else {
                    originalResultNote = originalResultNote
                            + dictionary.getLocalizedDictionaryName().getLocalizedValue();
                }
            }
        } else {
            originalResultNote = originalResultNote + testResultItem.getResultValue();
        }

        actionDataSet.addToNoteList(noteService.createSavableNote(analysis, NoteType.INTERNAL, originalResultNote,
                RESULT_SUBJECT, this.getSysUserId(request)));
    }

    protected boolean analysisShouldBeUpdated(TestResultItem testResultItem, Result result, boolean supportReferrals) {
        return result != null && !GenericValidator.isBlankOrNull(result.getValue())
                || (supportReferrals && ResultUtil.isReferred(testResultItem))
                || ResultUtil.isForcedToAcceptance(testResultItem) || testResultItem.isShadowRejected();
    }

    private void addResult(Result result, TestResultItem testResultItem, Analysis analysis,
            boolean multipleResultsForAnalysis, ResultsUpdateDataSet actionDataSet, boolean useTechnicianName) {
        boolean newResult = result.getId() == null;
        boolean newAnalysisInLoop = analysis != actionDataSet.getPreviousAnalysis();

        ResultSignature technicianResultSignature = null;

        if (useTechnicianName && newAnalysisInLoop) {
            technicianResultSignature = createTechnicianSignatureFromResultItem(testResultItem);
        }

        ResultInventory testKit = createTestKitLinkIfNeeded(testResultItem, ResultsLoadUtility.TESTKIT);
        if (testResultItem.getResultFile() != null) {
            ResultFile resultFile = createResultFile(testResultItem.getResultFile());
            if (resultFile != null) {
                analysis.setResultFile(resultFile);
            }
        }

        analysis.setReferredOut(testResultItem.isReferredOut());
        analysis.setEnteredDate(DateUtil.getNowAsTimestamp());

        if (newResult) {
            analysis.setEnteredDate(DateUtil.getNowAsTimestamp());
            analysis.setRevision("1");
        } else if (newAnalysisInLoop) {
            analysis.setRevision(String.valueOf(Integer.parseInt(analysis.getRevision()) + 1));
        }

        SampleService sampleService = SpringContext.getBean(SampleService.class);
        Sample sample = sampleService.getSampleByAccessionNumber(testResultItem.getAccessionNumber());
        Patient patient = sampleService.getPatient(sample);

        Map<String, List<String>> triggersToReflexesMap = new HashMap<>();

        getSelectedReflexes(testResultItem.getReflexJSONResult(), triggersToReflexesMap);

        if (newResult) {
            actionDataSet.getNewResults().add(new ResultSet(result, technicianResultSignature, testKit, patient, sample,
                    triggersToReflexesMap, multipleResultsForAnalysis));
        } else {
            actionDataSet.getModifiedResults().add(new ResultSet(result, technicianResultSignature, testKit, patient,
                    sample, triggersToReflexesMap, multipleResultsForAnalysis));
        }

        actionDataSet.setPreviousAnalysis(analysis);
    }

    private void getSelectedReflexes(String reflexJSONResult, Map<String, List<String>> triggersToReflexesMap) {
        if (!GenericValidator.isBlankOrNull(reflexJSONResult)) {
            JSONParser parser = new JSONParser();
            try {
                JSONObject jsonResult = (JSONObject) parser.parse(reflexJSONResult.replaceAll("'", "\""));

                for (Object compoundReflexes : jsonResult.values()) {
                    if (compoundReflexes != null) {
                        String triggerIds = (String) ((JSONObject) compoundReflexes).get("triggerIds");
                        List<String> selectedReflexIds = new ArrayList<>();
                        JSONArray selectedReflexes = (JSONArray) ((JSONObject) compoundReflexes).get("selected");
                        for (Object selectedReflex : selectedReflexes) {
                            selectedReflexIds.add(((String) selectedReflex));
                        }
                        triggersToReflexesMap.put(triggerIds.trim(), selectedReflexIds);
                    }
                }
            } catch (ParseException e) {
                LogEvent.logDebug(e);
            }
        }
    }

    private String getStatusForTestResult(TestResultItem testResult, boolean alwaysValidate) {
        if (testResult.isShadowRejected() && ConfigurationProperties.getInstance()
                .isPropertyValueEqual(Property.VALIDATE_REJECTED_TESTS, "true")) {
            return SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.TechnicalRejected);
        } else if (testResult.isShadowRejected()) {
            return SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.Canceled);
        } else if (!noResults(testResult.getShadowResultValue(), testResult.getMultiSelectResultValues(),
                testResult.getResultType()) && !hasCompleteAdditionalResultFields(testResult)) {
            // Do not move to validation/finalized until all active additional result fields are filled.
            return SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.NotStarted);
        } else if (alwaysValidate || !testResult.isValid() || ResultUtil.isForcedToAcceptance(testResult)) {
            return SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.TechnicalAcceptance);
        } else if (noResults(testResult.getShadowResultValue(), testResult.getMultiSelectResultValues(),
                testResult.getResultType())) {
            return SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.NotStarted);
        } else {
            if (!GenericValidator.isBlankOrNull(testResult.getResultLimitId())) {
                ResultLimit resultLimit = resultLimitService.get(testResult.getResultLimitId());
                if (resultLimit.isAlwaysValidate()) {
                    return SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.TechnicalAcceptance);
                }
                if (TypeOfTestResultServiceImpl.ResultType.DICTIONARY.matches(testResult.getResultType())
                        && !testResult.getResultValue().equals(resultLimit.getDictionaryNormalId())) {
                    return SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.TechnicalAcceptance);
                }
            }

            return SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.Finalized);
        }
    }

    private boolean noResults(String value, String multiSelectValue, String type) {

        return (GenericValidator.isBlankOrNull(value) && GenericValidator.isBlankOrNull(multiSelectValue))
                || (TypeOfTestResultServiceImpl.ResultType.DICTIONARY.matches(type) && "0".equals(value));
    }

    private boolean hasCompleteAdditionalResultFields(TestResultItem testResultItem) {
        List<TestAdditionalFieldPayload> definitions = testResultItem.getAdditionalFieldDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return true;
        }

        Map<String, String> values = testResultItem.getAdditionalFieldValues();
        if (values == null) {
            values = new HashMap<>();
        }

        for (TestAdditionalFieldPayload definition : definitions) {
            if (definition == null || Boolean.FALSE.equals(definition.getActive())) {
                continue;
            }
            if (!shouldIncludeInValidation(definition)) {
                continue;
            }
            if (!Boolean.TRUE.equals(definition.getRequired())) {
                continue;
            }
            String fieldKey = definition.getFieldKey();
            if (GenericValidator.isBlankOrNull(fieldKey)) {
                continue;
            }

            if (GenericValidator.isBlankOrNull(values.get(fieldKey))) {
                return false;
            }
        }

        return true;
    }

    private boolean shouldIncludeInValidation(TestAdditionalFieldPayload definition) {
        if (definition == null || Boolean.FALSE.equals(definition.getActive())) {
            return false;
        }
        if (definition.getIncludeInValidation() != null) {
            return Boolean.TRUE.equals(definition.getIncludeInValidation());
        }
        String scope = definition.getEntryScope();
        return !"PRELIMINARY".equalsIgnoreCase(scope);
    }

    private ResultInventory createTestKitLinkIfNeeded(TestResultItem testResult, String testKitName) {
        ResultInventory testKit = null;

        if ((TestResultItem.ResultDisplayType.SYPHILIS.toString() == testResult.getResultDisplayType()
                || TestResultItem.ResultDisplayType.HIV.toString() == testResult.getResultDisplayType())
                && ResultsLoadUtility.TESTKIT.equals(testKitName)) {

            testKit = createTestKit(testResult, testKitName, testResult.getTestKitId());
        }

        return testKit;
    }

    private ResultInventory createTestKit(TestResultItem testResult, String testKitName, String testKitId)
            throws LIMSRuntimeException {
        ResultInventory testKit;
        testKit = new ResultInventory();

        if (!GenericValidator.isBlankOrNull(testKitId)) {
            testKit.setId(testKitId);
            testKit = resultInventoryService.get(testKitId);
        }

        testKit.setInventoryLocationId(testResult.getTestKitInventoryId());
        testKit.setDescription(testKitName);
        testKit.setSysUserId(getSysUserId(request));
        return testKit;
    }

    private void updateAnalysis(TestResultItem testResultItem, String testDate, Analysis analysis,
            String statusRuleSet) {
        if (testResultItem.getAnalysisMethod() != null) {
            analysis.setAnalysisType(testResultItem.getAnalysisMethod());
        }
        // analysis.setStartedDateForDisplay(testDate);

        // This needs to be refactored -- part of the logic is in
        // getStatusForTestResult. RetroCI over rides to whatever was set before
        if (statusRuleSet.equals(STATUS_RULES_RETROCI)) {
            if (!SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.Canceled)
                    .equals(analysis.getStatusId())) {
                analysis.setCompletedDate(DateUtil.convertStringDateToSqlDate(testDate));
                analysis.setStatusId(
                        SpringContext.getBean(IStatusService.class).getStatusID(AnalysisStatus.TechnicalAcceptance));
            }
        } else if (SpringContext.getBean(IStatusService.class).matches(analysis.getStatusId(), AnalysisStatus.Finalized)
                || SpringContext.getBean(IStatusService.class).matches(analysis.getStatusId(),
                        AnalysisStatus.TechnicalAcceptance)
                || (analysis.isReferredOut()
                        && !GenericValidator.isBlankOrNull(testResultItem.getShadowResultValue()))) {
            analysis.setCompletedDate(DateUtil.convertStringDateToSqlDate(testDate));
        }
    }

    private ResultSignature createTechnicianSignatureFromResultItem(TestResultItem testResult) {
        ResultSignature sig = null;

        // The technician signature may be blank if the user changed a
        // conclusion and then changed it back. It will be dirty
        // but will not need a signature
        if (!GenericValidator.isBlankOrNull(testResult.getTechnician())) {
            sig = new ResultSignature();

            if (!GenericValidator.isBlankOrNull(testResult.getTechnicianSignatureId())) {
                sig = resultSigService.get(testResult.getTechnicianSignatureId());
            }

            sig.setIsSupervisor(false);
            sig.setNonUserName(testResult.getTechnician());

            sig.setSysUserId(getSysUserId(request));
        }
        return sig;
    }

    private boolean modifyResultsRoleBased() {
        return "true"
                .equals(ConfigurationProperties.getInstance().getPropertyValue(Property.roleRequiredForModifyResults));
    }

    private boolean userNotInRole(HttpServletRequest request) {
        if (userModuleService.isUserAdmin(request)) {
            return false;
        }
        List<String> roleIds = userRoleService.getRoleIdsForUser(getSysUserId(request));
        return !roleIds.contains(RESULT_EDIT_ROLE_ID);
    }

    private Patient getPatient(Sample sample) {
        return sampleHumanService.getPatientForSample(sample);
    }

    private SampleItem resolveSampleItemByCugCode(String accessionOrSearchTerm) {
        String searchValue = accessionOrSearchTerm == null ? null : accessionOrSearchTerm.trim();
        if (StringUtils.isBlank(searchValue)) {
            return null;
        }
        return sampleItemService.findSampleItemByCugCode(searchValue);
    }

    private List<TestResultItem> filterTestsBySampleItem(List<TestResultItem> tests, SampleItem sampleItem) {
        if (sampleItem == null || GenericValidator.isBlankOrNull(sampleItem.getId()) || tests == null || tests.isEmpty()) {
            return tests;
        }

        List<TestResultItem> filtered = new ArrayList<>();
        boolean separatorAdded = false;
        for (TestResultItem item : tests) {
            if (item == null) {
                continue;
            }
            if (item.getIsGroupSeparator()) {
                if (!separatorAdded) {
                    filtered.add(item);
                    separatorAdded = true;
                }
                continue;
            }
            if (sampleItem.getId().equals(item.getSampleItemId())) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private Sample resolveSampleByAccessionOrSearchableValue(String accessionOrSearchTerm) {
        String searchValue = accessionOrSearchTerm == null ? null : accessionOrSearchTerm.trim();
        Sample sample = orderAdditionalFieldService.findSampleIdBySearchableFieldValue(searchValue)
                .map(sampleId -> sampleService.get(String.valueOf(sampleId))).orElse(null);
        if (sample != null) {
            return sample;
        }

        sample = sampleService.getSampleByAccessionNumber(searchValue);
        if (sample == null && searchValue != null && searchValue.contains("-")) {
            sample = sampleService.getSampleByAccessionNumber(searchValue.substring(0, searchValue.indexOf('-')));
        }
        return sample;
    }

    private ResultFile createResultFile(TestResultItem.ResultFileForm fileForm) {
        if (fileForm == null || GenericValidator.isBlankOrNull(fileForm.getFileName())
                || GenericValidator.isBlankOrNull(fileForm.getFileType()) || fileForm.getContent() == null
                || fileForm.getContent().length == 0) {
            return null;
        }
        ResultFile file = new ResultFile();
        file.setFileName(fileForm.getFileName());
        file.setFileType(fileForm.getFileType());
        file.setContent(fileForm.getContent());

        Timestamp now = new Timestamp(System.currentTimeMillis());
        file.setUploadedAt(now);
        file.setLastupdated(now);

        return file;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e) {
        Map<String, String> error = new HashMap<>();
        error.put("message", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    private String findLogBookForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "resultsLogbookDefinition";
        } else if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/LogbookResults";
        } else if (FWD_VALIDATION_ERROR.equals(forward)) {
            return "resultsLogbookDefinition";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "resultsLogbookDefinition";
        } else {
            return "PageNotFound";
        }
    }

    private String findAccessionForward(String forward) {
        if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/AccessionResults";
        } else if (FWD_VALIDATION_ERROR.equals(forward)) {
            return "accessionResultDefinition";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "accessionResultDefinition";
        } else {
            return "PageNotFound";
        }
    }

    private String findPatientForward(String forward) {
        if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/PatientResults";
        } else if (FWD_VALIDATION_ERROR.equals(forward)) {
            return "patientResultDefinition";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "patientResultDefinition";
        } else {
            return "PageNotFound";
        }
    }

    private String findStatusForward(String forward) {
        if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/StatusResults?blank=true";
        } else if (FWD_VALIDATION_ERROR.equals(forward)) {
            return "statusResultDefinition";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "statusResultDefinition";
        } else {
            return "PageNotFound";
        }
    }

    private String findRangeForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "resultsLogbookDefinition";
        } else if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/RangeResults";
        } else if (FWD_VALIDATION_ERROR.equals(forward)) {
            return "resultsLogbookDefinition";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "resultsLogbookDefinition";
        } else {
            return "PageNotFound";
        }
    }

    @Override
    protected String findLocalForward(String forward) {
        if (request.getRequestURL().indexOf("RangeResults") >= 0) {
            return findRangeForward(forward);
        } else if (request.getRequestURL().indexOf("LogbookResults") >= 0) {
            return findLogBookForward(forward);
        } else if (request.getRequestURL().indexOf("AccessionResults") >= 0) {
            return findAccessionForward(forward);
        } else if (request.getRequestURL().indexOf("PatientResults") >= 0) {
            return findPatientForward(forward);
        } else if (request.getRequestURL().indexOf("StatusResults") >= 0) {
            return findStatusForward(forward);
        } else {
            return "PageNotFound";
        }
    }

    private Patient getPatient(String patientID) {
        return patientService.get(patientID);
    }
}
