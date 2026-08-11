package org.openelisglobal.patient.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.StatusService.OrderStatus;
import org.openelisglobal.common.services.StatusSet;
import org.openelisglobal.common.services.TableIdService;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldFilePayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldOptionPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderAdditionalFieldPayload;
import org.openelisglobal.orderadditionalfield.bean.OrderFixedFieldConfigPayload;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.observationhistory.service.ObservationHistoryService;
import org.openelisglobal.observationhistory.service.ObservationHistoryServiceImpl.ObservationType;
import org.openelisglobal.patient.form.PatientHistorySummary;
import org.openelisglobal.patient.form.PatientHistorySummary.FieldRecord;
import org.openelisglobal.patient.form.PatientHistorySummary.Metrics;
import org.openelisglobal.patient.form.PatientHistorySummary.OrderRecord;
import org.openelisglobal.patient.form.PatientHistorySummary.ResultRecord;
import org.openelisglobal.patient.form.PatientHistorySummary.SampleRecord;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.result.service.ResultService;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.common.services.SampleOrderService;
import org.openelisglobal.result.action.util.ResultsLoadUtility;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldOptionPayload;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.dao.SampleItemDAO;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.storage.service.SampleStorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.openelisglobal.test.beanItems.TestResultItem;

@Service
@Transactional(readOnly = true)
public class PatientHistorySummaryServiceImpl implements PatientHistorySummaryService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private PatientService patientService;

    @Autowired
    private SampleHumanService sampleHumanService;

    @Autowired
    private SampleService sampleService;

    @Autowired
    private SampleItemDAO sampleItemDAO;

    @Autowired
    private SampleStorageService sampleStorageService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private ResultService resultService;

    @Autowired
    private ObservationHistoryService observationHistoryService;

    @Autowired
    private IStatusService statusService;

    @Autowired
    private PersonService personService;

    @Autowired
    private ObjectProvider<SampleOrderService> sampleOrderServiceProvider;

    @Autowired
    private SampleTypeAdditionalFieldService sampleTypeAdditionalFieldService;

    @Autowired
    private ObjectProvider<ResultsLoadUtility> resultsLoadUtilityProvider;

    @Override
    public PatientHistorySummary getSummary(String patientId) {
        PatientHistorySummary summary = new PatientHistorySummary();
        summary.setPatientId(patientId);

        Patient patient = patientService.get(patientId);
        if (patient == null) {
            return summary;
        }

        List<Sample> samples = new ArrayList<>(sampleHumanService.getSamplesForPatient(patientId));
        samples.sort(Comparator.comparing(this::getSampleSortTimestamp, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Sample::getAccessionNumber, Comparator.nullsLast(Comparator.reverseOrder())));

        List<OrderRecord> orderRecords = new ArrayList<>();
        List<SampleRecord> sampleRecords = new ArrayList<>();
        List<ResultRecord> resultRecords = new ArrayList<>();
        Metrics metrics = new Metrics();

        for (Sample sample : samples) {
            List<FieldRecord> orderFields = buildOrderFields(sample);
            orderRecords.add(buildOrderRecord(sample, orderFields));
            List<SampleItem> sampleItems = loadSampleItems(sample);
            Map<String, TestResultItem> configuredResultsByAnalysisId = loadConfiguredResultsByAnalysisId(sample);

            for (SampleItem sampleItem : sampleItems) {
                List<Analysis> analyses = analysisService.getAnalysesBySampleItem(sampleItem);
                List<FieldRecord> sampleAdditionalFields = buildSampleAdditionalFields(sampleItem);
                SampleRecord record = buildSampleRecord(sample, sampleItem, analyses, orderFields, sampleAdditionalFields);
                sampleRecords.add(record);
                resultRecords.addAll(buildResultRecords(sample, sampleItem, analyses, orderFields, sampleAdditionalFields,
                        configuredResultsByAnalysisId));
                metrics.setTotalSamples(metrics.getTotalSamples() + 1);
                metrics.setTotalTests(metrics.getTotalTests() + record.getTotalTests());
                metrics.setCompletedTests(metrics.getCompletedTests() + record.getCompletedTests());
                metrics.setPendingTests(metrics.getPendingTests() + record.getPendingTests());
                if (record.isStored()) {
                    metrics.setStoredSamples(metrics.getStoredSamples() + 1);
                }
            }
        }

        metrics.setTotalOrders(orderRecords.size());
        summary.setMetrics(metrics);
        summary.setOrders(orderRecords);
        summary.setSamples(sampleRecords);
        summary.setResults(resultRecords);
        return summary;
    }

    private OrderRecord buildOrderRecord(Sample sample, List<FieldRecord> orderFields) {
        OrderRecord record = new OrderRecord();
        record.setId(sample.getId());
        record.setAccessionNumber(sample.getAccessionNumber());
        record.setClinicalOrderId(sample.getClinicalOrderId());
        record.setClientReference(sample.getClientReference());
        record.setPriority(sample.getPriority() == null ? "" : sample.getPriority().name());
        record.setRequestDate(defaultIfBlank(
                observationHistoryService.getValueForSample(ObservationType.REQUEST_DATE, sample.getId()),
                sample.getCollectionDateForDisplay()));
        record.setReceivedDate(sample.getReceivedDateForDisplay());
        record.setStatus(resolveOrderStatus(sample));
        record.setReferringSiteName(resolveReferringSite(sample));
        record.setRequesterName(resolveRequesterName(sample));
        record.setOrderFields(copyFields(orderFields));
        return record;
    }

    private SampleRecord buildSampleRecord(Sample sample, SampleItem sampleItem, List<Analysis> analyses,
            List<FieldRecord> orderFields, List<FieldRecord> sampleAdditionalFields) {
        SampleRecord record = new SampleRecord();
        record.setId(sampleItem.getId());
        record.setAccessionNumber(sample.getAccessionNumber());
        record.setClinicalOrderId(sample.getClinicalOrderId());
        record.setSampleItemExternalId(sampleItem.getExternalId());
        record.setSampleType(sampleItem.getTypeOfSample() == null ? "" : sampleItem.getTypeOfSample().getLocalizedName());
        record.setCollectionDate(resolveCollectionDate(sample, sampleItem));
        record.setStatus(statusService.getStatusNameFromId(sampleItem.getStatusId()));
        record.setParentSampleItemExternalId(sampleItem.getParentSampleItem() == null ? ""
                : sampleItem.getParentSampleItem().getExternalId());

        Map<String, Object> location = sampleStorageService.getSampleItemLocation(sampleItem.getId());
        record.setStorageLocation(asString(location.get("hierarchicalPath")));
        record.setStorageAssignedDate(asString(location.get("assignedDate")));
        record.setStoragePositionCoordinate(asString(location.get("positionCoordinate")));
        record.setStorageNotes(asString(location.get("notes")));
        record.setStored(StringUtils.isNotBlank(record.getStorageLocation()));

        record.setTotalTests(analyses.size());
        record.setCompletedTests((int) analyses.stream()
                .filter(analysis -> statusService.matches(analysis.getStatusId(), AnalysisStatus.Finalized)).count());
        record.setPendingTests((int) analyses.stream().filter(this::isPendingAnalysis).count());
        record.setCollectionFields(buildSampleCollectionFields(sampleItem, sampleAdditionalFields));
        record.setReceptionFields(buildSampleReceptionFields(record, sampleAdditionalFields));
        record.setFixedFields(buildSampleFixedFields(record));
        record.setOrderFields(copyFields(orderFields));
        record.setAdditionalFields(copyFields(sampleAdditionalFields));
        return record;
    }

    private List<ResultRecord> buildResultRecords(Sample sample, SampleItem sampleItem, List<Analysis> analyses,
            List<FieldRecord> orderFields, List<FieldRecord> sampleAdditionalFields,
            Map<String, TestResultItem> configuredResultsByAnalysisId) {
        List<ResultRecord> resultRecords = new ArrayList<>();

        for (Analysis analysis : analyses) {
            TestResultItem configuredResult = configuredResultsByAnalysisId.get(analysis.getId());
            ResultRecord resultRecord = new ResultRecord();
            resultRecord.setId(analysis.getId());
            resultRecord.setAccessionNumber(sample.getAccessionNumber());
            resultRecord.setClinicalOrderId(sample.getClinicalOrderId());
            resultRecord.setSampleType(
                    sampleItem.getTypeOfSample() == null ? "" : sampleItem.getTypeOfSample().getLocalizedName());
            resultRecord.setCollectionDate(resolveCollectionDate(sample, sampleItem));
            resultRecord.setSampleStatus(statusService.getStatusNameFromId(sampleItem.getStatusId()));
            resultRecord.setTestStatus(statusService.getStatusNameFromId(analysis.getStatusId()));
            resultRecord.setTestName(resolveTestName(analysis));
            resultRecord.setResultDate(resolveResultDate(analysis));
            resultRecord.setResultName(resolveResultName(configuredResult, analysis));
            resultRecord.setResultValue(resolveConfiguredPrimaryResultValue(configuredResult, analysis));
            resultRecord.setResultType(configuredResult == null ? "" : defaultString(configuredResult.getResultType()));
            resultRecord.setResultDisplayConfigJson(resolveResultDisplayConfigJson(configuredResult, analysis));
            resultRecord.setFixedFields(buildResultFixedFields(resultRecord));
            resultRecord.setSampleOrderFields(copyFields(orderFields));
            resultRecord.setSampleAdditionalFields(copyFields(sampleAdditionalFields));
            resultRecord.setResultValues(buildResultValueFields(analysis));
            resultRecord.setAdditionalFieldDefinitions(copyAdditionalFieldDefinitions(configuredResult));
            resultRecord.setAdditionalFieldValues(copyAdditionalFieldValues(configuredResult));
            resultRecords.add(resultRecord);
        }

        return resultRecords;
    }

    private Map<String, TestResultItem> loadConfiguredResultsByAnalysisId(Sample sample) {
        if (sample == null) {
            return new HashMap<>();
        }

        try {
            List<Analysis> analyses = analysisService.getAnalysesBySampleId(sample.getId());
            if (analyses == null || analyses.isEmpty()) {
                return new HashMap<>();
            }

            ResultsLoadUtility resultsLoadUtility = resultsLoadUtilityProvider.getObject();
            List<TestResultItem> configuredResults = resultsLoadUtility.getGroupedTestsForAnalysisList(analyses, true);

            return configuredResults.stream().filter(item -> item != null && StringUtils.isNotBlank(item.getAnalysisId()))
                    .collect(Collectors.toMap(TestResultItem::getAnalysisId, item -> item, (left, right) -> left,
                            HashMap::new));
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<SampleItem> loadSampleItems(Sample sample) {
        List<SampleItem> sampleItems = sampleItemDAO.getSampleItemsBySampleId(sample.getId());
        if (sampleItems == null || sampleItems.isEmpty()) {
            return new ArrayList<>();
        }
        return sampleItemDAO.getSampleItemsWithHierarchy(
                sampleItems.stream().map(SampleItem::getId).collect(Collectors.toList()));
    }

    private List<FieldRecord> buildOrderFields(Sample sample) {
        SampleOrderService sampleOrderService = sampleOrderServiceProvider.getObject();
        sampleOrderService.setSample(sample);
        SampleOrderItem sampleOrderItem = sampleOrderService.getSampleOrderItem();

        List<FieldRecord> fields = new ArrayList<>();
        if (sampleOrderItem == null) {
            return fields;
        }

        List<OrderFixedFieldConfigPayload> fixedConfigs = sampleOrderItem.getFixedFieldConfigs() == null
                ? new ArrayList<>()
                : sampleOrderItem.getFixedFieldConfigs();
        fixedConfigs.stream()
                .filter(config -> config != null && config.getFieldKey() != null
                        && !Boolean.FALSE.equals(config.getVisible()))
                .sorted(Comparator.comparing(config -> config.getSortOrder() == null ? 0 : config.getSortOrder()))
                .forEach(config -> {
                    String value = resolveFixedFieldDisplayValue(sampleOrderItem, config.getFieldKey());
                    fields.add(createField(config.getFieldKey(), config.getFieldKey(), value, "TEXT", "orderFixed"));
                });

        List<OrderAdditionalFieldPayload> additionalFields = sampleOrderItem.getAdditionalFields() == null
                ? new ArrayList<>()
                : sampleOrderItem.getAdditionalFields();
        additionalFields.stream()
                .filter(field -> field != null && field.getFieldKey() != null && !Boolean.FALSE.equals(field.getActive()))
                .sorted(Comparator.comparing(field -> field.getSortOrder() == null ? 0 : field.getSortOrder()))
                .forEach(field -> {
                    String value = resolveCustomFieldDisplayValue(field, sampleOrderItem);
                    fields.add(createField(field.getFieldKey(),
                            StringUtils.defaultIfBlank(field.getDisplayName(), field.getFieldKey()), value,
                            StringUtils.defaultIfBlank(field.getFieldType(), "TEXT"), "orderAdditional"));
                });

        return fields;
    }

    private List<FieldRecord> buildSampleAdditionalFields(SampleItem sampleItem) {
        if (sampleItem == null || StringUtils.isBlank(sampleItem.getTypeOfSampleId())) {
            return new ArrayList<>();
        }

        List<SampleTypeAdditionalFieldPayload> definitions = sampleTypeAdditionalFieldService
                .getFieldsForSampleType(sampleItem.getTypeOfSampleId(), false);
        if (definitions == null || definitions.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, String> valuesByKey = sampleTypeAdditionalFieldService
                .getFieldValuesForSampleItem(sampleItem.getTypeOfSampleId(), sampleItem.getId());

        return definitions.stream().filter(field -> field != null && field.getFieldKey() != null
                && !Boolean.FALSE.equals(field.getActive())).sorted(
                        Comparator.comparing(field -> field.getSortOrder() == null ? 0 : field.getSortOrder()))
                .map(field -> {
                    String value = resolveSampleAdditionalFieldDisplayValue(field, valuesByKey);
                    if (StringUtils.isBlank(value)) {
                        return null;
                    }
                    return createField(field.getFieldKey(),
                            StringUtils.defaultIfBlank(field.getDisplayName(), field.getFieldKey()), value,
                            StringUtils.defaultIfBlank(field.getFieldType(), "TEXT"),
                            isCollectionField(field) ? "sampleAdditionalCollection" : "sampleAdditionalReception");
                }).filter(field -> field != null).collect(Collectors.toList());
    }

    private List<FieldRecord> buildSampleCollectionFields(SampleItem sampleItem, List<FieldRecord> sampleAdditionalFields) {
        List<FieldRecord> fields = new ArrayList<>();
        addField(fields, "quantity", "quantity", formatQuantity(sampleItem), "NUMBER", "collection");
        addField(fields, "uom", "uom", resolveUnitOfMeasure(sampleItem), "TEXT", "collection");
        addField(fields, "collectionDate", "collectionDate",
                sampleItem == null || sampleItem.getCollectionDate() == null ? ""
                        : DateUtil.convertTimestampToStringDate(sampleItem.getCollectionDate()),
                "DATE", "collection");
        addField(fields, "collectionTime", "collectionTime",
                sampleItem == null || sampleItem.getCollectionDate() == null ? ""
                        : DateUtil.convertTimestampToStringHourTime(sampleItem.getCollectionDate()),
                "TIME", "collection");
        addField(fields, "collector", "collector", sampleItem == null ? "" : sampleItem.getCollector(), "TEXT",
                "collection");
        fields.addAll(filterFieldsBySource(sampleAdditionalFields, "sampleAdditionalCollection"));
        return fields;
    }

    private List<FieldRecord> buildSampleReceptionFields(SampleRecord record, List<FieldRecord> sampleAdditionalFields) {
        return filterFieldsBySource(sampleAdditionalFields, "sampleAdditionalReception");
    }

    private List<FieldRecord> buildSampleFixedFields(SampleRecord record) {
        List<FieldRecord> fields = new ArrayList<>();
        addField(fields, "accessionNumber", "accessionNumber", record.getAccessionNumber(), "TEXT", "fixed");
        addField(fields, "clinicalOrderId", "clinicalOrderId", record.getClinicalOrderId(), "TEXT", "fixed");
        addField(fields, "sampleItemExternalId", "sampleItemExternalId", record.getSampleItemExternalId(), "TEXT",
                "fixed");
        addField(fields, "sampleType", "sampleType", record.getSampleType(), "TEXT", "fixed");
        addField(fields, "collectionDate", "collectionDate", record.getCollectionDate(), "DATE", "fixed");
        addField(fields, "status", "status", record.getStatus(), "TEXT", "fixed");
        addField(fields, "parentSample", "parentSample", record.getParentSampleItemExternalId(), "TEXT", "fixed");
        addField(fields, "totalTests", "totalTests", String.valueOf(record.getTotalTests()), "NUMBER", "fixed");
        addField(fields, "completedTests", "completedTests", String.valueOf(record.getCompletedTests()), "NUMBER",
                "fixed");
        addField(fields, "pendingTests", "pendingTests", String.valueOf(record.getPendingTests()), "NUMBER", "fixed");
        addField(fields, "location", "location", record.getStorageLocation(), "TEXT", "fixed");
        addField(fields, "assignedDate", "assignedDate", record.getStorageAssignedDate(), "DATE", "fixed");
        addField(fields, "position", "position", record.getStoragePositionCoordinate(), "TEXT", "fixed");
        addField(fields, "notes", "notes", record.getStorageNotes(), "TEXTAREA", "fixed");
        return fields;
    }

    private boolean isCollectionField(SampleTypeAdditionalFieldPayload field) {
        return field != null && "COLLECTION".equalsIgnoreCase(StringUtils.defaultString(field.getDisplaySection()));
    }

    private List<FieldRecord> filterFieldsBySource(List<FieldRecord> fields, String source) {
        if (fields == null || fields.isEmpty()) {
            return new ArrayList<>();
        }
        return fields.stream().filter(field -> field != null && StringUtils.equals(field.getSource(), source))
                .collect(Collectors.toList());
    }

    private String formatQuantity(SampleItem sampleItem) {
        if (sampleItem == null || sampleItem.getQuantity() == null) {
            return "";
        }
        return String.valueOf(sampleItem.getQuantity());
    }

    private String resolveUnitOfMeasure(SampleItem sampleItem) {
        if (sampleItem == null) {
            return "";
        }
        if (StringUtils.isNotBlank(sampleItem.getUnitOfMeasureName())) {
            return sampleItem.getUnitOfMeasureName();
        }
        return sampleItem.getUnitOfMeasure() == null ? "" : defaultString(sampleItem.getUnitOfMeasure().getName());
    }

    private List<FieldRecord> buildResultFixedFields(ResultRecord record) {
        List<FieldRecord> fields = new ArrayList<>();
        addField(fields, "accessionNumber", "accessionNumber", record.getAccessionNumber(), "TEXT", "fixed");
        addField(fields, "clinicalOrderId", "clinicalOrderId", record.getClinicalOrderId(), "TEXT", "fixed");
        addField(fields, "testName", "testName", record.getTestName(), "TEXT", "fixed");
        addField(fields, "resultStatus", "resultStatus", record.getTestStatus(), "TEXT", "fixed");
        addField(fields, "sampleType", "sampleType", record.getSampleType(), "TEXT", "fixed");
        addField(fields, "sampleStatus", "sampleStatus", record.getSampleStatus(), "TEXT", "fixed");
        addField(fields, "collectionDate", "collectionDate", record.getCollectionDate(), "DATE", "fixed");
        addField(fields, "resultDate", "resultDate", record.getResultDate(), "DATE", "fixed");
        return fields;
    }

    private List<FieldRecord> buildResultValueFields(Analysis analysis) {
        List<Result> results = resultService.getResultsByAnalysis(analysis);
        if (results == null || results.isEmpty()) {
            return new ArrayList<>();
        }

        List<FieldRecord> fields = new ArrayList<>();
        int ordinal = 1;
        for (Result result : results) {
            String value = resultService.getResultValue(result, false);
            if (StringUtils.isBlank(value)) {
                continue;
            }

            String label = resolveResultValueLabel(result, ordinal);
            fields.add(createField("resultValue" + ordinal, label, value,
                    StringUtils.defaultIfBlank(result.getResultType(), "TEXT"), "resultValue"));
            ordinal++;
        }

        return fields;
    }

    private String resolveResultValueLabel(Result result, int ordinal) {
        if (result == null) {
            return "Result " + ordinal;
        }
        if (result.getAnalyte() != null && StringUtils.isNotBlank(result.getAnalyte().getAnalyteName())) {
            return result.getAnalyte().getAnalyteName();
        }
        if (result.getTestResult() != null && StringUtils.isNotBlank(result.getTestResult().getTestName())) {
            return result.getTestResult().getTestName();
        }
        return "Result " + ordinal;
    }

    private boolean isPendingAnalysis(Analysis analysis) {
        return analysis != null
                && !statusService.matches(analysis.getStatusId(), AnalysisStatus.Finalized)
                && !statusService.matches(analysis.getStatusId(), AnalysisStatus.Canceled)
                && !statusService.matches(analysis.getStatusId(), AnalysisStatus.SampleRejected)
                && !statusService.matches(analysis.getStatusId(), AnalysisStatus.TechnicalRejected)
                && !statusService.matches(analysis.getStatusId(), AnalysisStatus.BiologistRejected);
    }

    private String resolveOrderStatus(Sample sample) {
        StatusSet statusSet = statusService.getStatusSetForSampleId(sample.getId());
        OrderStatus orderStatus = statusSet == null ? null : statusSet.getSampleStatus();
        return orderStatus == null ? "" : statusService.getStatusName(orderStatus);
    }

    private String resolveReferringSite(Sample sample) {
        String referringOrgTypeId = StringUtils.defaultString(getReferringOrgTypeId());
        Organization organization = sampleService.getOrganizationRequester(sample, referringOrgTypeId);
        return organization == null ? "" : organization.getOrganizationName();
    }

    private String resolveRequesterName(Sample sample) {
        Person requester = sampleService.getPersonRequester(sample);
        return requester == null ? "" : personService.getLastFirstName(requester);
    }

    private String resolveTestName(Analysis analysis) {
        if (analysis == null || analysis.getTest() == null) {
            return "";
        }
        if (StringUtils.isNotBlank(analysis.getTest().getLocalizedName())) {
            return analysis.getTest().getLocalizedName();
        }
        if (StringUtils.isNotBlank(analysis.getTest().getName())) {
            return analysis.getTest().getName();
        }
        return defaultString(analysis.getTest().getDescription());
    }

    private String resolveCollectionDate(Sample sample, SampleItem sampleItem) {
        if (sampleItem.getCollectionDate() != null) {
            return DateUtil.convertTimestampToStringDate(sampleItem.getCollectionDate());
        }
        return sample.getCollectionDateForDisplay();
    }

    private String resolveResultDate(Analysis analysis) {
        if (analysis == null) {
            return "";
        }
        if (analysis.getCompletedDate() != null) {
            return DateUtil.convertSqlDateToStringDate(analysis.getCompletedDate());
        }
        if (analysis.getStartedDate() != null) {
            return DateUtil.convertSqlDateToStringDate(analysis.getStartedDate());
        }
        return "";
    }

    private String resolveResultName(TestResultItem configuredResult, Analysis analysis) {
        if (configuredResult != null && StringUtils.isNotBlank(configuredResult.getResultName())) {
            return configuredResult.getResultName();
        }
        return resolveTestName(analysis);
    }

    private String resolveConfiguredPrimaryResultValue(TestResultItem configuredResult, Analysis analysis) {
        if (configuredResult != null && StringUtils.isNotBlank(configuredResult.getResultValue())) {
            return configuredResult.getResultValue();
        }

        List<FieldRecord> resultValues = buildResultValueFields(analysis);
        if (resultValues.isEmpty()) {
            return "";
        }
        return defaultString(resultValues.get(0).getValue());
    }

    private String resolveResultDisplayConfigJson(TestResultItem configuredResult, Analysis analysis) {
        if (configuredResult != null && StringUtils.isNotBlank(configuredResult.getResultDisplayConfigJson())) {
            return configuredResult.getResultDisplayConfigJson();
        }
        return analysis == null || analysis.getTest() == null ? "" : defaultString(analysis.getTest().getResultDisplayConfigJson());
    }

    private List<org.openelisglobal.testadditionalfield.bean.TestAdditionalFieldPayload> copyAdditionalFieldDefinitions(
            TestResultItem configuredResult) {
        if (configuredResult == null || configuredResult.getAdditionalFieldDefinitions() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(configuredResult.getAdditionalFieldDefinitions());
    }

    private Map<String, String> copyAdditionalFieldValues(TestResultItem configuredResult) {
        if (configuredResult == null || configuredResult.getAdditionalFieldValues() == null) {
            return new HashMap<>();
        }
        return new HashMap<>(configuredResult.getAdditionalFieldValues());
    }

    private String resolveFixedFieldDisplayValue(SampleOrderItem sampleOrderItem, String fieldKey) {
        Object rawValue = readFieldValue(sampleOrderItem, fieldKey);
        if (rawValue == null || StringUtils.isBlank(String.valueOf(rawValue))) {
            return "";
        }

        if ("referringSiteDepartmentId".equals(fieldKey)) {
            return defaultIfBlank(mapValueFromIdList(sampleOrderItem.getReferringSiteDepartmentList(), rawValue),
                    String.valueOf(rawValue));
        }
        if ("paymentOptionSelection".equals(fieldKey)) {
            return defaultIfBlank(mapValueFromIdList(sampleOrderItem.getPaymentOptions(), rawValue),
                    String.valueOf(rawValue));
        }
        if ("testLocationCode".equals(fieldKey)) {
            return defaultIfBlank(mapValueFromIdList(sampleOrderItem.getTestLocationCodeList(), rawValue),
                    String.valueOf(rawValue));
        }
        return String.valueOf(rawValue);
    }

    private String resolveCustomFieldDisplayValue(OrderAdditionalFieldPayload field, SampleOrderItem sampleOrderItem) {
        Map<String, String> valuesByKey = sampleOrderItem.getAdditionalFieldValues();
        Map<String, OrderAdditionalFieldFilePayload> filesByKey = sampleOrderItem.getAdditionalFieldFiles();
        String normalizedKey = normalizeFieldKey(field.getFieldKey());
        String rawValue = valuesByKey == null ? null : valuesByKey.get(field.getFieldKey());
        if (rawValue == null && valuesByKey != null) {
            rawValue = valuesByKey.get(normalizedKey);
        }

        String fieldType = StringUtils.defaultIfBlank(field.getFieldType(), "TEXT").toUpperCase();
        if ("DOCUMENT".equals(fieldType)) {
            OrderAdditionalFieldFilePayload file = filesByKey == null ? null : filesByKey.get(field.getFieldKey());
            if (file == null || StringUtils.isBlank(file.getFileName())) {
                return "";
            }

            Map<String, String> documentPayload = new HashMap<>();
            documentPayload.put("fileName", defaultIfBlank(file.getFileName(), ""));
            documentPayload.put("fileType", defaultIfBlank(file.getFileType(), ""));
            try {
                return OBJECT_MAPPER.writeValueAsString(documentPayload);
            } catch (Exception e) {
                return defaultIfBlank(file.getFileName(), file.getFileType());
            }
        }
        if (StringUtils.isBlank(rawValue)) {
            return "";
        }
        if ("BOOLEAN".equals(fieldType)) {
            return String.valueOf(Boolean.parseBoolean(rawValue));
        }
        if ("SELECT".equals(fieldType) || "RADIO".equals(fieldType)
                || "SYSTEM_USER_BIOLOGIST_SELECT".equals(fieldType)) {
            return resolveOrderOptionLabel(field.getOptions(), rawValue);
        }
        if ("MULTISELECT".equals(fieldType)) {
            return resolveOrderMultiOptionLabels(field.getOptions(), rawValue);
        }
        return rawValue;
    }

    private String resolveSampleAdditionalFieldDisplayValue(SampleTypeAdditionalFieldPayload field,
            Map<String, String> valuesByKey) {
        String rawValue = valuesByKey == null ? null : valuesByKey.get(field.getFieldKey());
        if (StringUtils.isBlank(rawValue)) {
            return "";
        }

        String fieldType = StringUtils.defaultIfBlank(field.getFieldType(), "TEXT").toUpperCase();
        if ("BOOLEAN".equals(fieldType)) {
            return String.valueOf(Boolean.parseBoolean(rawValue));
        }
        if ("SELECT".equals(fieldType) || "RADIO".equals(fieldType)) {
            return resolveSampleOptionLabel(field.getOptions(), rawValue);
        }
        if ("MULTISELECT".equals(fieldType)) {
            return resolveSampleMultiOptionLabels(field.getOptions(), rawValue);
        }
        return rawValue;
    }

    private String resolveOrderOptionLabel(List<OrderAdditionalFieldOptionPayload> options, String rawValue) {
        if (options == null) {
            return rawValue;
        }
        return options.stream().filter(option -> option != null && StringUtils.equals(option.getOptionKey(), rawValue))
                .map(OrderAdditionalFieldOptionPayload::getOptionLabel).filter(StringUtils::isNotBlank).findFirst()
                .orElse(rawValue);
    }

    private String resolveOrderMultiOptionLabels(List<OrderAdditionalFieldOptionPayload> options, String rawValue) {
        return splitMultiValue(rawValue).stream().map(value -> resolveOrderOptionLabel(options, value))
                .collect(Collectors.joining(", "));
    }

    private String resolveSampleOptionLabel(List<SampleTypeAdditionalFieldOptionPayload> options, String rawValue) {
        if (options == null) {
            return rawValue;
        }
        return options.stream().filter(option -> option != null && StringUtils.equals(option.getOptionKey(), rawValue))
                .map(SampleTypeAdditionalFieldOptionPayload::getOptionLabel).filter(StringUtils::isNotBlank).findFirst()
                .orElse(rawValue);
    }

    private String resolveSampleMultiOptionLabels(List<SampleTypeAdditionalFieldOptionPayload> options,
            String rawValue) {
        return splitMultiValue(rawValue).stream().map(value -> resolveSampleOptionLabel(options, value))
                .collect(Collectors.joining(", "));
    }

    private List<String> splitMultiValue(String rawValue) {
        if (StringUtils.isBlank(rawValue)) {
            return new ArrayList<>();
        }
        return java.util.Arrays.stream(rawValue.split(",")).map(String::trim).filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
    }

    private boolean isCustomFieldShownInSampleReception(OrderAdditionalFieldPayload field) {
        if (field == null || StringUtils.isBlank(field.getMetadataJson())) {
            return false;
        }
        try {
            JsonNode metadata = OBJECT_MAPPER.readTree(field.getMetadataJson());
            return metadata.path("sampleReception").path("showInSampleReception").asBoolean(false)
                    || metadata.path("showInSampleReception").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    private Object readFieldValue(SampleOrderItem sampleOrderItem, String fieldKey) {
        if (sampleOrderItem == null || StringUtils.isBlank(fieldKey)) {
            return null;
        }
        try {
            String getterName = "get" + Character.toUpperCase(fieldKey.charAt(0)) + fieldKey.substring(1);
            return SampleOrderItem.class.getMethod(getterName).invoke(sampleOrderItem);
        } catch (Exception e) {
            return null;
        }
    }

    private String mapValueFromIdList(Collection<?> entries, Object rawValue) {
        if (entries == null || rawValue == null) {
            return "";
        }
        String normalizedRawValue = String.valueOf(rawValue);
        for (Object entry : entries) {
            if (entry instanceof IdValuePair) {
                IdValuePair pair = (IdValuePair) entry;
                if (StringUtils.equals(normalizedRawValue, String.valueOf(pair.getId()))) {
                    return pair.getValue();
                }
            }
        }
        return "";
    }

    private String normalizeFieldKey(String fieldKey) {
        return StringUtils.defaultString(fieldKey).trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_")
                .replaceAll("_+", "_").replaceAll("^_+", "");
    }

    private void addField(List<FieldRecord> fields, String key, String label, String value, String fieldType,
            String source) {
        if (StringUtils.isBlank(value)) {
            return;
        }
        fields.add(createField(key, label, value, fieldType, source));
    }

    private FieldRecord createField(String key, String label, String value, String fieldType, String source) {
        FieldRecord field = new FieldRecord();
        field.setKey(key);
        field.setLabel(label);
        field.setValue(value);
        field.setFieldType(fieldType);
        field.setSource(source);
        return field;
    }

    private List<FieldRecord> copyFields(List<FieldRecord> fields) {
        return fields == null ? new ArrayList<>() : new ArrayList<>(fields);
    }

    private Timestamp getSampleSortTimestamp(Sample sample) {
        if (sample == null) {
            return null;
        }
        if (sample.getReceivedTimestamp() != null) {
            return sample.getReceivedTimestamp();
        }
        return sample.getCollectionDate();
    }

    private String getReferringOrgTypeId() {
        TableIdService tableIdService = TableIdService.getInstance();
        return tableIdService == null ? null : tableIdService.REFERRING_ORG_TYPE_ID;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.isNotBlank(value) ? value : defaultString(fallback);
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
