package org.openelisglobal.reports.controller;

import com.itextpdf.text.DocumentException;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.sf.jasperreports.engine.JRException;
import org.apache.commons.validator.GenericValidator;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.form.BaseForm;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.services.IReportTrackingService;
import org.openelisglobal.common.services.ReportTrackingService.ReportType;
import org.openelisglobal.orderadditionalfield.service.OrderAdditionalFieldService;
import org.openelisglobal.reportdefinition.service.ReportDefinitionService;
import org.openelisglobal.reportdefinition.valueholder.ReportDefinition;
import org.openelisglobal.reports.action.implementation.IReportCreator;
import org.openelisglobal.reports.action.implementation.IReportParameterSetter;
import org.openelisglobal.reports.action.implementation.ReportImplementationFactory;
import org.openelisglobal.reports.form.ReportForm;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.spring.util.SpringContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.servlet.ModelAndView;

@Controller
@SessionAttributes("form")
public class ReportController extends BaseController {
    private static final String VALIDATION_REPORT_OVERRIDE_CATEGORY = "validation_template_override";
    private static final String REPORT_KEY = "report";
    private static final String TEST_ID_KEY = "testId";
    private static final String TEST_IDS_KEY = "testIds";
    private static final String TEST_CODE_KEY = "testCode";
    private static final String TEST_CODES_KEY = "testCodes";
    private static final String OVERRIDE_CONFIG_KEY = "config";

    private static final String[] ALLOWED_FIELDS = new String[] { "report", "reportType", "type", "accessionDirect",
            "highAccessionDirect", "patientNumberDirect", "patientUpperNumberDirect", "lowerDateRange",
            "upperDateRange", "locationCode", "projectCode", "datePeriod", "lowerMonth", "lowerYear", "upperMonth",
            "upperYear", "selectList.selection", "experimentId", "reportName", "selPatient", "analysisIds",
            "previewValidated", "previewAnalysisIds", "previewValidationDates", "referringSiteId",
            "referringSiteDepartmentId", "onlyResults", "dateType", "labSections", "priority", "receptionTime",
            "vlStudyType" };

    @Autowired
    private ServletContext context;
    @Autowired
    private SampleService sampleService;
    @Autowired
    private OrderAdditionalFieldService orderAdditionalFieldService;
    @Autowired
    private AnalysisService analysisService;
    @Autowired
    private ReportDefinitionService reportDefinitionService;

    private String reportPath = null;
    private String imagesPath = null;

    @ModelAttribute("form")
    public BaseForm form() {
        return new ReportForm();
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @RequestMapping(value = "/Report", method = RequestMethod.GET)
    public ModelAndView showReport(HttpServletRequest request, @ModelAttribute("form") BaseForm oldForm)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        ReportForm newForm = resetSessionFormToType(oldForm, ReportForm.class);
        newForm.setFormMethod(RequestMethod.GET);

        newForm.setType(request.getParameter("type"));
        newForm.setReport(request.getParameter("report"));
        IReportParameterSetter setter = ReportImplementationFactory.getParameterSetter(request.getParameter("report"));

        if (setter != null) {
            setter.setRequestParameters(newForm);
        }

        return findForward(FWD_SUCCESS, newForm);
    }

    @RequestMapping(value = "/ReportPrint", method = RequestMethod.GET)
    public ModelAndView showReportPrint(HttpServletRequest request, HttpServletResponse response,
            @ModelAttribute("ReportPrintForm") @Valid ReportForm form, BindingResult result, SessionStatus status)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        if (result.hasErrors()) {
            saveErrors(result);
            return findForward(FWD_FAIL, form);
        }

        normalizeAccessionSearchInputs(form);
        LogEvent.logTrace("ReportController", "Log GET ", request.getParameter("report"));
        printReport(request, response, form);

        // signal to remove from from session
        status.setComplete();
        return null;
    }

    private void printReport(HttpServletRequest request, HttpServletResponse response, ReportForm form) {
        String requestedReportName = request.getParameter("report");
        ResolvedValidationReport resolvedReport = resolveReportForRequest(request, form, requestedReportName);
        String effectiveReportName = resolvedReport.reportName;
        form.setValidationTemplateConfigJson(resolvedReport.configJson);
        IReportCreator reportCreator = ReportImplementationFactory.getReportCreator(effectiveReportName);

        if (reportCreator == null) {
            LogEvent.logError("ReportController", "printReport",
                    "Unable to find report creator for report: " + effectiveReportName);
            return;
        }

        reportCreator.setSystemUserId(getSysUserId(request));
        reportCreator.setRequestedReport(effectiveReportName);
        reportCreator.initializeReport(form);
        reportCreator.setReportPath(getReportPath());

        HashMap<String, String> parameterMap = (HashMap<String, String>) reportCreator.getReportParameters();
        parameterMap.put("SUBREPORT_DIR", getReportPath());
        parameterMap.put("imagesPath", getImagesPath());

        try {
            response.setContentType(reportCreator.getContentType());
            String responseHeaderName = reportCreator.getResponseHeaderName();
            String responseHeaderContent = reportCreator.getResponseHeaderContent();
            if (!GenericValidator.isBlankOrNull(responseHeaderName)
                    && !GenericValidator.isBlankOrNull(responseHeaderContent)) {
                response.setHeader(responseHeaderName, responseHeaderContent);
            }
            if ("true".equalsIgnoreCase(request.getParameter("download"))) {
                response.setHeader("Content-Disposition", "attachment; filename=\"validated-report.pdf\"");
            }
            response.setHeader("X-OpenELIS-Effective-Report", effectiveReportName == null ? "" : effectiveReportName);

            byte[] bytes = reportCreator.runReport();

            response.setContentLength(bytes.length);

            ServletOutputStream servletOutputStream = response.getOutputStream();

            servletOutputStream.write(bytes, 0, bytes.length);
            servletOutputStream.flush();
            servletOutputStream.close();
        } catch (IOException | SQLException | JRException | DocumentException | ParseException e) {
            LogEvent.logError(e);
        }

        if ("patient".equals(request.getParameter("type")) && !form.isPreviewValidated()) {
            trackReports(reportCreator, effectiveReportName, ReportType.PATIENT);
        }
    }

    private ResolvedValidationReport resolveReportForRequest(HttpServletRequest request, ReportForm form,
            String fallbackReportName) {
        if (!"patient".equals(request.getParameter("type")) || form == null || form.getAnalysisIds() == null
                || form.getAnalysisIds().isEmpty()) {
            return new ResolvedValidationReport(fallbackReportName, null);
        }

        Map<String, ValidationTemplateOverride> overrideByTestKey = getValidationReportOverridesByTestKey();
        if (overrideByTestKey.isEmpty()) {
            return new ResolvedValidationReport(fallbackReportName, null);
        }

        Set<String> analysisIds = parseDelimitedIds(form.getAnalysisIds());
        if (analysisIds.isEmpty()) {
            return new ResolvedValidationReport(fallbackReportName, null);
        }

        Map<String, String> testIdToCode = new HashMap<>();
        Set<String> selectedTestNames = new HashSet<>();
        for (String analysisId : analysisIds) {
            Analysis analysis = analysisService.get(analysisId);
            if (analysis == null || analysis.getTest() == null
                    || GenericValidator.isBlankOrNull(analysis.getTest().getId())) {
                continue;
            }
            testIdToCode.put(analysis.getTest().getId(), analysis.getTest().getSortOrder());
            if (!GenericValidator.isBlankOrNull(analysis.getTestName())) {
                selectedTestNames.add(analysis.getTestName().toLowerCase());
            }
            if (!GenericValidator.isBlankOrNull(analysis.getTest().getName())) {
                selectedTestNames.add(analysis.getTest().getName().toLowerCase());
            }
            if (!GenericValidator.isBlankOrNull(analysis.getTest().getDescription())) {
                selectedTestNames.add(analysis.getTest().getDescription().toLowerCase());
            }
        }

        if (testIdToCode.isEmpty()) {
            return new ResolvedValidationReport(fallbackReportName, null);
        }

        List<ValidationTemplateOverride> overrides = new ArrayList<>();
        for (Map.Entry<String, String> test : testIdToCode.entrySet()) {
            String testId = test.getKey();
            String testCode = test.getValue();

            ValidationTemplateOverride override = overrideByTestKey.get(makeTestIdKey(testId));
            if (override == null && !GenericValidator.isBlankOrNull(testCode)) {
                override = overrideByTestKey.get(makeTestCodeKey(testCode));
            }
            if (override != null && !GenericValidator.isBlankOrNull(override.reportName)) {
                overrides.add(override);
            }
        }

        Set<String> candidateReports = overrides.stream().map(override -> override.reportName)
                .collect(Collectors.toSet());
        Set<String> candidateConfig = overrides.stream()
                .map(override -> GenericValidator.isBlankOrNull(override.configJson) ? "" : override.configJson)
                .collect(Collectors.toSet());

        if (overrides.size() != testIdToCode.size() || candidateReports.size() != 1 || candidateConfig.size() != 1) {
            // Safety fallback: when selection is clearly DMPK, prefer dedicated template
            // even if DB override is incomplete/misaligned.
            boolean isDmpkSelection = selectedTestNames.stream().anyMatch(name -> name.contains("dmpk"));
            if (isDmpkSelection) {
                return new ResolvedValidationReport("patientDMPK", null);
            }
            return new ResolvedValidationReport(fallbackReportName, null);
        }

        ValidationTemplateOverride selected = overrides.get(0);
        return new ResolvedValidationReport(selected.reportName, selected.configJson);
    }

    private Map<String, ValidationTemplateOverride> getValidationReportOverridesByTestKey() {
        List<ReportDefinition> activeDefinitions = reportDefinitionService
                .getDefinitionsByCategory(VALIDATION_REPORT_OVERRIDE_CATEGORY);
        if (activeDefinitions == null || activeDefinitions.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, ValidationTemplateOverride> overrides = new HashMap<>();
        for (ReportDefinition definition : activeDefinitions) {
            if (!Boolean.TRUE.equals(definition.getIsActive())
                    || GenericValidator.isBlankOrNull(definition.getDefinitionJson())) {
                continue;
            }
            try {
                JSONObject parsed = new JSONObject(definition.getDefinitionJson());
                String reportName = parsed.optString(REPORT_KEY, "").trim();
                if (reportName.isEmpty()) {
                    continue;
                }
                String configJson = extractOverrideConfigJson(parsed);
                ValidationTemplateOverride override = new ValidationTemplateOverride(reportName, configJson,
                        resolveDefinitionTime(definition));

                String singleTestId = parsed.optString(TEST_ID_KEY, "").trim();
                if (!singleTestId.isEmpty()) {
                    putIfNewer(overrides, makeTestIdKey(singleTestId), override);
                }

                JSONArray testIds = parsed.optJSONArray(TEST_IDS_KEY);
                if (testIds != null) {
                    for (int i = 0; i < testIds.length(); i++) {
                        String testId = testIds.optString(i, "").trim();
                        if (!testId.isEmpty()) {
                            putIfNewer(overrides, makeTestIdKey(testId), override);
                        }
                    }
                }

                String singleTestCode = parsed.optString(TEST_CODE_KEY, "").trim();
                if (!singleTestCode.isEmpty()) {
                    putIfNewer(overrides, makeTestCodeKey(singleTestCode), override);
                }

                JSONArray testCodes = parsed.optJSONArray(TEST_CODES_KEY);
                if (testCodes != null) {
                    for (int i = 0; i < testCodes.length(); i++) {
                        String testCode = testCodes.optString(i, "").trim();
                        if (!testCode.isEmpty()) {
                            putIfNewer(overrides, makeTestCodeKey(testCode), override);
                        }
                    }
                }
            } catch (Exception e) {
                LogEvent.logError("ReportController", "getValidationReportOverridesByTestKey",
                        "Invalid report_definition JSON for id=" + definition.getId() + ": " + e.getMessage());
            }
        }
        return overrides;
    }

    private void putIfNewer(Map<String, ValidationTemplateOverride> overrides, String key,
            ValidationTemplateOverride candidate) {
        ValidationTemplateOverride current = overrides.get(key);
        if (current == null || candidate.updatedAt >= current.updatedAt) {
            overrides.put(key, candidate);
        }
    }

    private long resolveDefinitionTime(ReportDefinition definition) {
        Timestamp lastUpdated = definition.getLastupdated();
        if (lastUpdated != null) {
            return lastUpdated.getTime();
        }
        Timestamp created = definition.getCreatedDate();
        return created == null ? 0L : created.getTime();
    }

    private String makeTestIdKey(String testId) {
        return "id:" + testId;
    }

    private String makeTestCodeKey(String testCode) {
        return "code:" + testCode;
    }

    private String extractOverrideConfigJson(JSONObject parsed) {
        JSONObject configObject = parsed.optJSONObject(OVERRIDE_CONFIG_KEY);
        if (configObject != null) {
            return configObject.toString();
        }
        return parsed.toString();
    }

    private Set<String> parseDelimitedIds(List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> ids = new HashSet<>();
        for (String rawValue : rawValues) {
            if (GenericValidator.isBlankOrNull(rawValue)) {
                continue;
            }
            Arrays.stream(rawValue.split(",")).map(String::trim).filter(id -> !GenericValidator.isBlankOrNull(id))
                    .forEach(ids::add);
        }
        return ids;
    }

    private static class ValidationTemplateOverride {
        private final String reportName;
        private final String configJson;
        private final long updatedAt;

        private ValidationTemplateOverride(String reportName, String configJson, long updatedAt) {
            this.reportName = reportName;
            this.configJson = configJson;
            this.updatedAt = updatedAt;
        }
    }

    private static class ResolvedValidationReport {
        private final String reportName;
        private final String configJson;

        private ResolvedValidationReport(String reportName, String configJson) {
            this.reportName = reportName;
            this.configJson = configJson;
        }
    }

    private void trackReports(IReportCreator reportCreator, String reportName, ReportType type) {
        List<String> refIds = reportCreator.getReportedOrders() != null ? reportCreator.getReportedOrders()
                : new ArrayList<>();
        SpringContext.getBean(IReportTrackingService.class).addReports(refIds, type, reportName, getSysUserId(request));
    }

    private void normalizeAccessionSearchInputs(ReportForm form) {
        if (form == null) {
            return;
        }

        String lower = form.getAccessionDirect();
        if (!GenericValidator.isBlankOrNull(lower)) {
            Sample sample = resolveSampleByAccessionOrSearchableValue(lower);
            if (sample != null && !GenericValidator.isBlankOrNull(sample.getAccessionNumber())) {
                form.setAccessionDirect(sample.getAccessionNumber());
            }
        }

        String upper = form.getHighAccessionDirect();
        if (!GenericValidator.isBlankOrNull(upper)) {
            Sample sample = resolveSampleByAccessionOrSearchableValue(upper);
            if (sample != null && !GenericValidator.isBlankOrNull(sample.getAccessionNumber())) {
                form.setHighAccessionDirect(sample.getAccessionNumber());
            }
        }
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

    private String getReportPath() {
        String reportPath = getReportPathValue();
        if (reportPath.endsWith(File.separator)) {
            return reportPath;
        } else {
            return reportPath + File.separator;
        }
    }

    private String getReportPathValue() {

        if (reportPath == null) {
            // TODO csl this was added by external developer but it breaks the other reports
            // SiteInformation reportsPath =
            // siteInformationService.getSiteInformationByName("reportsDirectory");
            // if (reportsPath != null) {
            // reportPath = reportsPath.getValue();
            // return reportPath;
            // }
            ClassLoader classLoader = getClass().getClassLoader();
            reportPath = classLoader.getResource("reports").getPath();
            try {
                reportPath = URLDecoder.decode(reportPath, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LogEvent.logError(e);
                throw new LIMSRuntimeException(e);
            }
        }
        return reportPath;
    }

    public String getImagesPath() {
        if (imagesPath == null) {
            imagesPath = context.getRealPath("") + "static" + File.separator + "images" + File.separator;
            try {
                imagesPath = URLDecoder.decode(imagesPath, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                LogEvent.logError(e);
                throw new LIMSRuntimeException(e);
            }
        }
        return imagesPath;
    }

    @Override
    protected String findLocalForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "commonReportDefiniton";
        } else if (FWD_FAIL.equals(forward)) {
            return "commonReportDefiniton";
        } else {
            return "PageNotFound";
        }
    }

    @Override
    protected String getPageSubtitleKey() {
        return "reports.add.params";
    }

    @Override
    protected String getPageTitleKey() {
        return "reports.add.params";
    }
}
