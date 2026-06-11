package org.openelisglobal.common.rest.provider;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.rest.provider.bean.homedashboard.AverageTimeDisplayBean;
import org.openelisglobal.common.rest.provider.bean.homedashboard.DashBoardMetrics;
import org.openelisglobal.common.rest.provider.bean.homedashboard.DashBoardTile;
import org.openelisglobal.common.rest.provider.bean.homedashboard.OrderDisplayBean;
import org.openelisglobal.common.rest.provider.form.PatientDashBoardForm;
import org.openelisglobal.common.rest.util.PatientDashBoardPaging;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.StatusService.ExternalOrderStatus;
import org.openelisglobal.dataexchange.fhir.FhirConfig;
import org.openelisglobal.dataexchange.fhir.FhirUtil;
import org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder;
import org.openelisglobal.dataexchange.service.order.ElectronicOrderService;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.service.SampleTypeAdditionalFieldService;
import org.openelisglobal.sample.bean.SampleTypeAdditionalFieldPayload;
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.siteinformation.service.SiteInformationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.MediaType;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.patient.util.PatientUtil;
import org.openelisglobal.patientidentity.valueholder.PatientIdentity;
import org.openelisglobal.patientidentitytype.util.PatientIdentityTypeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping(value = "/rest/")
public class PatientDashBoardProvider {

    @Autowired
    AnalysisService analysisService;

    @Autowired
    IStatusService iStatusService;

    @Autowired
    ElectronicOrderService electronicOrderService;

    @Autowired
    SampleHumanService sampleHumanService;

    @Autowired
    private SampleService sampleService;

    @Autowired
    private SampleTypeAdditionalFieldService sampleTypeAdditionalFieldService;

    @Autowired
    private FhirUtil fhirUtil;

    @Autowired
    private FhirConfig fhirConfig;

    @Autowired
    private TestService testService;

    @Autowired
    SystemUserService systemUserService;

    @Autowired
    SiteInformationService siteInformationService;

    private Long toEpochMillis(java.sql.Date date) {
        if (date == null) {
            return null;
        }
        return date.toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Long toEpochMillis(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant().toEpochMilli();
    }

    private Double toDurationHours(Long startMillis, Long endMillis) {
        if (startMillis == null || endMillis == null || endMillis < startMillis) {
            return null;
        }
        return Duration.ofMillis(endMillis - startMillis).toMinutes() / 60.0;
    }

    private double averageHours(List<Double> hours) {
        return hours.isEmpty() ? 0.0 : hours.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private boolean isSampleItemCompleteForDashboard(SampleItem sampleItem,
            Map<String, List<SampleTypeAdditionalFieldPayload>> fieldsBySampleTypeId,
            Map<String, Map<String, String>> valuesBySampleItemId) {
        if (sampleItem == null) {
            return false;
        }

        boolean hasCollector = StringUtils.isNotBlank(sampleItem.getCollector());
        boolean hasCollectionDate = sampleItem.getCollectionDate() != null;
        if (!(hasCollector && hasCollectionDate)) {
            return false;
        }

        if (sampleItem.getTypeOfSample() == null || StringUtils.isBlank(sampleItem.getTypeOfSample().getId())) {
            return true;
        }

        String sampleTypeId = sampleItem.getTypeOfSample().getId();
        List<SampleTypeAdditionalFieldPayload> fieldDefinitions = fieldsBySampleTypeId.computeIfAbsent(sampleTypeId,
                id -> sampleTypeAdditionalFieldService.getFieldsForSampleType(id, false));
        if (fieldDefinitions == null || fieldDefinitions.isEmpty()) {
            return true;
        }

        String sampleItemId = sampleItem.getId();
        Map<String, String> additionalFieldValues = valuesBySampleItemId.computeIfAbsent(sampleItemId,
                id -> sampleTypeAdditionalFieldService.getFieldValuesForSampleItem(sampleTypeId, id));

        for (SampleTypeAdditionalFieldPayload fieldDefinition : fieldDefinitions) {
            if (!Boolean.TRUE.equals(fieldDefinition.getRequired())) {
                continue;
            }
            String value = additionalFieldValues == null ? null : additionalFieldValues.get(fieldDefinition.getFieldKey());
            if (StringUtils.isBlank(value)) {
                return false;
            }
        }

        return true;
    }

    private double calculateAverageReceptionToValidationTime(java.sql.Date start, java.sql.Date end) {
        List<Analysis> analyses = analysisService.getAnalysisStartedOrCompletedInDateRange(start, end);
        if (analyses == null || analyses.isEmpty()) {
            return 0.0;
        }
        String finalizedId = iStatusService.getStatusID(AnalysisStatus.Finalized);
        String technicalAcceptanceId = iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance);
        List<Double> hours = new ArrayList<>();

        for (Analysis analysis : analyses) {
            if (!(StringUtils.equals(analysis.getStatusId(), finalizedId)
                    || StringUtils.equals(analysis.getStatusId(), technicalAcceptanceId))) {
                continue;
            }
            Long startMillis = toEpochMillis(analysis.getStartedDate());
            if (startMillis == null && analysis.getSampleItem() != null) {
                startMillis = toEpochMillis(analysis.getSampleItem().getCollectionDate());
            }
            Long endMillis = toEpochMillis(analysis.getReleasedDate());
            if (endMillis == null) {
                endMillis = toEpochMillis(analysis.getLastupdated());
            }
            if (endMillis == null) {
                endMillis = toEpochMillis(analysis.getCompletedDate());
            }
            Double durationHours = toDurationHours(startMillis, endMillis);
            if (durationHours != null) {
                hours.add(durationHours);
            }
        }
        return averageHours(hours);
    }

    private double calculateAverageReceptionToResultTime(java.sql.Date start, java.sql.Date end) {
        List<Analysis> analyses = analysisService.getAnalysisStartedOrCompletedInDateRange(start, end);
        if (analyses == null || analyses.isEmpty()) {
            return 0.0;
        }
        String rejectedId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
        List<Double> hours = new ArrayList<>();

        for (Analysis analysis : analyses) {
            if (StringUtils.equals(analysis.getStatusId(), rejectedId)) {
                continue;
            }
            Long startMillis = toEpochMillis(analysis.getStartedDate());
            if (startMillis == null && analysis.getSampleItem() != null) {
                startMillis = toEpochMillis(analysis.getSampleItem().getCollectionDate());
            }
            Long endMillis = toEpochMillis(analysis.getEnteredDate());
            if (endMillis == null) {
                endMillis = toEpochMillis(analysis.getCompletedDate());
            }
            if (endMillis == null) {
                endMillis = toEpochMillis(analysis.getLastupdated());
            }
            Double durationHours = toDurationHours(startMillis, endMillis);
            if (durationHours != null) {
                hours.add(durationHours);
            }
        }
        return averageHours(hours);
    }

    private double calculateAverageResultToValidationTime(java.sql.Date start, java.sql.Date end) {
        List<Analysis> analyses = analysisService.getAnalysisStartedOrCompletedInDateRange(start, end);
        if (analyses == null || analyses.isEmpty()) {
            return 0.0;
        }
        String finalizedId = iStatusService.getStatusID(AnalysisStatus.Finalized);
        String technicalAcceptanceId = iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance);
        List<Double> hours = new ArrayList<>();

        for (Analysis analysis : analyses) {
            if (!(StringUtils.equals(analysis.getStatusId(), finalizedId)
                    || StringUtils.equals(analysis.getStatusId(), technicalAcceptanceId))) {
                continue;
            }
            Long startMillis = toEpochMillis(analysis.getEnteredDate());
            if (startMillis == null) {
                startMillis = toEpochMillis(analysis.getCompletedDate());
            }
            if (startMillis == null) {
                startMillis = toEpochMillis(analysis.getStartedDate());
            }
            if (startMillis == null && analysis.getSampleItem() != null) {
                startMillis = toEpochMillis(analysis.getSampleItem().getCollectionDate());
            }
            Long endMillis = toEpochMillis(analysis.getReleasedDate());
            if (endMillis == null) {
                endMillis = toEpochMillis(analysis.getLastupdated());
            }
            Double durationHours = toDurationHours(startMillis, endMillis);
            if (durationHours != null) {
                hours.add(durationHours);
            }
        }
        return averageHours(hours);
    }

    private List<Analysis> analysesWithDelayedTurnAroundTime(java.sql.Date start, java.sql.Date end) {
        List<Analysis> analyses = analysisService.getAnalysisStartedOrCompletedInDateRange(start, end);
        String finalizedId = iStatusService.getStatusID(AnalysisStatus.Finalized);
        List<Analysis> delayedAnalyses = new ArrayList<>();

        for (Analysis analysis : analyses) {
            if (analysis.getStatusId().equals(finalizedId) && analysis.getStartedDate() != null
                    && analysis.getReleasedDate() != null) {
                LocalDate localStartDate = analysis.getStartedDate().toLocalDate();
                LocalDate localEndDate = analysis.getReleasedDate().toLocalDate();
                if (Duration.between(localStartDate.atStartOfDay(), localEndDate.atStartOfDay()).toHours() > 96) {
                    delayedAnalyses.add(analysis);
                }
            }
        }
        return delayedAnalyses;
    }

    private List<Analysis> unprintedResults(java.sql.Date start, java.sql.Date end) {
        List<Analysis> analyses = analysisService.getAnalysisStartedOrCompletedInDateRange(start, end);
        String finalizedId = iStatusService.getStatusID(AnalysisStatus.Finalized);
        List<Analysis> unprintedAnalyses = new ArrayList<>();

        for (Analysis a : analyses) {
            if (a.getStatusId().equals(finalizedId) && !analysisService.patientReportHasBeenDone(a)) {
                unprintedAnalyses.add(a);
            }
        }
        return unprintedAnalyses;
    }

    private List<OrderDisplayBean> convertAnalysesToOrderBean(List<Analysis> analyses) {
        List<OrderDisplayBean> orderBeanList = new ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        if (analyses != null) {
            analyses.forEach(analysis -> {
                if (analysis != null) {
                    OrderDisplayBean orderBean = new OrderDisplayBean();
                    orderBean.setId(analysis.getId());
                    Sample sample = analysis.getSampleItem() != null ? analysis.getSampleItem().getSample() : null;

                    if (sample != null) {
                        orderBean.setPriority(sample.getPriority() != null ? sample.getPriority().toString() : "");
                        orderBean.setLabNumber(sample.getAccessionNumber() != null ? sample.getAccessionNumber() : "");
                        orderBean.setCugCode(
                                analysis.getSampleItem() != null ? analysis.getSampleItem().getCugCode() : "");

                        try {
                            orderBean.setPatientId(getDisplayPatientIdentifier(sampleHumanService.getPatientForSample(sample)));
                        } catch (Exception e) {
                            orderBean.setPatientId("");
                        }
                    } else {
                        orderBean.setPriority("");
                        orderBean.setLabNumber("");
                        orderBean.setCugCode("");
                        orderBean.setPatientId("");
                    }

                    if (analysis.getEnteredDate() != null) {
                        orderBean.setOrderDate(sdf.format(analysis.getEnteredDate()));
                    } else if (analysis.getLastupdated() != null) {
                        orderBean.setOrderDate(sdf.format(analysis.getLastupdated()));
                    } else if (sample != null && sample.getLastupdated() != null) {
                        orderBean.setOrderDate(sdf.format(sample.getLastupdated()));
                    } else {
                        orderBean.setOrderDate("");
                    }

                    orderBean.setTestName(analysis.getTest() != null ? analysis.getTest().getLocalizedName() : "");
                    orderBean
                            .setTestSection(analysis.getTestSection() != null ? analysis.getTestSection().getId() : "");
                    orderBeanList.add(orderBean);
                }
            });
        }
        return orderBeanList;
    }

    private List<OrderDisplayBean> convertAnalysesToGroupedOrderBean(List<Analysis> analyses) {
        List<OrderDisplayBean> orderBeanList = new ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Map<String, OrderDisplayBean> groupedByLabNumber = new LinkedHashMap<>();
        Map<String, Set<String>> testSectionsByLabNumber = new HashMap<>();

        if (analyses == null) {
            return orderBeanList;
        }

        analyses.forEach(analysis -> {
            if (analysis == null || analysis.getSampleItem() == null || analysis.getSampleItem().getSample() == null) {
                return;
            }

            Sample sample = analysis.getSampleItem().getSample();
            String labNumber = sample.getAccessionNumber() != null ? sample.getAccessionNumber() : "";
            if (labNumber.isEmpty()) {
                return;
            }

            OrderDisplayBean orderBean = groupedByLabNumber.get(labNumber);
            if (orderBean == null) {
                orderBean = new OrderDisplayBean();
                orderBean.setId(sample.getId());
                orderBean.setPriority(sample.getPriority() != null ? sample.getPriority().toString() : "");
                orderBean.setLabNumber(labNumber);
                orderBean.setCugCode(
                        analysis.getSampleItem().getCugCode() != null ? analysis.getSampleItem().getCugCode() : "");
                orderBean.setPatientId(getDisplayPatientIdentifier(sampleHumanService.getPatientForSample(sample)));
        
                if (analysis.getEnteredDate() != null) {
                    orderBean.setOrderDate(sdf.format(analysis.getEnteredDate()));
                } else {
                    orderBean.setOrderDate(sample.getLastupdated() != null ? sdf.format(sample.getLastupdated()) : "");
                }

                orderBean.setTestName("");
                groupedByLabNumber.put(labNumber, orderBean);
                testSectionsByLabNumber.put(labNumber, new LinkedHashSet<>());
            }

            String testSectionId = analysis.getTestSection() != null ? analysis.getTestSection().getId() : "";
            if (StringUtils.isNotBlank(testSectionId)) {
                testSectionsByLabNumber.get(labNumber).add(testSectionId);
            }
        });

        groupedByLabNumber.forEach((labNumber, bean) -> {
            Set<String> sectionSet = testSectionsByLabNumber.get(labNumber);
            bean.setTestSection(sectionSet == null || sectionSet.isEmpty() ? "" : String.join(",", sectionSet));
            orderBeanList.add(bean);
        });

        return orderBeanList;
    }

    private List<OrderDisplayBean> convertAnalysesToUserOrdersBean(List<Analysis> analyses) {
        List<OrderDisplayBean> userOrders = new ArrayList<>();
        Map<String, List<Analysis>> userOrdersMap = new HashMap<>();
        analyses.forEach(analysis -> {
            String systemUserId = analysis.getSampleItem().getSample().getSysUserId();
            if (userOrdersMap.containsKey(systemUserId)) {
                userOrdersMap.get(systemUserId).add(analysis);
            } else {
                List<Analysis> userAnalyses = new ArrayList<>();
                userAnalyses.add(analysis);
                userOrdersMap.put(systemUserId, userAnalyses);
            }
        });

        userOrdersMap.forEach((userId, analysisList) -> {
            OrderDisplayBean userOrderBean = new OrderDisplayBean();
            SystemUser user = systemUserService.get(userId);
            if (user != null) {
                userOrderBean.setId(userId);
                userOrderBean.setUserFirstName(user.getFirstName());
                userOrderBean.setUserLastName(user.getLastName());
                
                long uniqueOrdersCount = analysisList.stream()
                        .map(a -> a.getSampleItem().getSample().getId())
                        .distinct()
                        .count();
                
                userOrderBean.setCountOfOrdersEntered((int) uniqueOrdersCount);
                userOrders.add(userOrderBean);
            }
        });
        return userOrders;
    }

    private List<OrderDisplayBean> getUserOrderBeans(List<Analysis> analyses, String userId) {
        Map<String, List<Analysis>> userOrdersMap = new HashMap<>();
        analyses.forEach(analysis -> {
            String systemUserId = analysis.getSampleItem().getSample().getSysUserId();
            if (userOrdersMap.containsKey(systemUserId)) {
                userOrdersMap.get(systemUserId).add(analysis);
            } else {
                List<Analysis> userAnalyses = new ArrayList<>();
                userAnalyses.add(analysis);
                userOrdersMap.put(systemUserId, userAnalyses);
            }
        });

        if (userOrdersMap.get(userId) != null) {
            return convertAnalysesToGroupedOrderBean(userOrdersMap.get(userId));
        }
        return new ArrayList<>();
    }

    private List<OrderDisplayBean> convertElectronicToOrderBean(List<ElectronicOrder> eOrders) {
        List<OrderDisplayBean> orderBeanList = new ArrayList<>();
        eOrders.forEach(eOrder -> {
            OrderDisplayBean orderBean = new OrderDisplayBean();
            orderBean.setId(eOrder.getId());
            orderBean.setPriority(eOrder.getPriority().toString());
            orderBean.setOrderDate(eOrder.getOrderTimestamp() != null ? eOrder.getOrderTimestamp().toString() : "");
            Sample sample = sampleService.getSampleByReferringId(eOrder.getExternalId());
            if (sample != null) {
                orderBean.setLabNumber(sample.getAccessionNumber());
                orderBean.setCugCode("");
            }

            Test test = null;
            try {
                IGenericClient fhirClient = fhirUtil.getFhirClient(fhirConfig.getLocalFhirStorePath());
                ServiceRequest serviceRequest = fhirClient.read().resource(ServiceRequest.class)
                        .withId(eOrder.getExternalId()).execute();
                for (Coding coding : serviceRequest.getCode().getCoding()) {
                    if (coding.hasSystem()) {
                        if (coding.getSystem().equalsIgnoreCase("http://loinc.org")) {
                            List<Test> tests = testService.getActiveTestsByLoinc(coding.getCode());
                            if (tests.size() != 0) {
                                test = tests.get(0);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {

            }
            if (test != null) {
                orderBean.setTestName(test.getLocalizedTestName().getLocalizedValue());
            }

            orderBean.setPatientId(getDisplayPatientIdentifier(eOrder.getPatient()));
            orderBeanList.add(orderBean);
        });

        return orderBeanList;
    }

    private List<OrderDisplayBean> convertAnalysesToGroupedBySampleItemOrderBean(List<Analysis> analyses) {
        List<OrderDisplayBean> orderBeanList = new ArrayList<>();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Set<String> processedSampleItems = new HashSet<>();

        if (analyses != null) {
            analyses.forEach(analysis -> {
                if (analysis != null && analysis.getSampleItem() != null && analysis.getSampleItem().getSample() != null) {
                    SampleItem sampleItem = analysis.getSampleItem();
                    if (processedSampleItems.contains(sampleItem.getId())) {
                        return;
                    }
                    processedSampleItems.add(sampleItem.getId());

                    Sample sample = sampleItem.getSample();
                    OrderDisplayBean orderBean = new OrderDisplayBean();
                    orderBean.setId(sampleItem.getId());
                    orderBean.setPriority(sample.getPriority() != null ? sample.getPriority().toString() : "");
                    orderBean.setLabNumber(sample.getAccessionNumber() != null ? sample.getAccessionNumber() : "");
                    orderBean.setCugCode(sampleItem.getCugCode() != null ? sampleItem.getCugCode() : "");
                    orderBean.setPatientId(getDisplayPatientIdentifier(sampleHumanService.getPatientForSample(sample)));
                    
                    if (analysis.getEnteredDate() != null) {
                        orderBean.setOrderDate(sdf.format(analysis.getEnteredDate()));
                    } else {
                        orderBean.setOrderDate(sample.getLastupdated() != null ? sdf.format(sample.getLastupdated()) : "");
                    }

                    orderBean.setTestName("");
                    orderBean.setTestSection(analysis.getTestSection() != null ? analysis.getTestSection().getId() : "");
                    orderBeanList.add(orderBean);
                }
            });
        }
        return orderBeanList;
    }

    private String getDisplayPatientIdentifier(org.openelisglobal.patient.valueholder.Patient patient) {
        if (patient == null) {
            return "";
        }
        String nationalId = patient.getNationalId();
        if (!GenericValidator.isBlankOrNull(nationalId)) {
            return nationalId;
        }
        try {
            List<PatientIdentity> identityList = PatientUtil.getIdentityListForPatient(patient.getId());
            String subjectNumber = PatientIdentityTypeMap.getInstance().getIdentityValue(identityList, "SUBJECT");
            if (!GenericValidator.isBlankOrNull(subjectNumber)) {
                return subjectNumber;
            }
        } catch (Exception e) {
            // Keep dashboard resilient; fallback to nationalId below.
        }
        return "";
    }

    @GetMapping(value = "home-dashboard/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public DashBoardMetrics getDasBoardTiles(@RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        DashBoardMetrics metrics = new DashBoardMetrics();

        java.sql.Date sqlStartDate = (startDate != null && !startDate.isEmpty()) ? java.sql.Date.valueOf(startDate)
                : java.sql.Date.valueOf("2000-01-01");

        java.sql.Date sqlEndDate;
        if (endDate != null && !endDate.isEmpty()) {
            
            sqlEndDate = java.sql.Date.valueOf(LocalDate.parse(endDate).plusDays(1));
        } else {
            
            sqlEndDate = java.sql.Date.valueOf(LocalDate.now().plusDays(1));
        }

        List<Analysis> allAnalysesInRange = analysisService.getAnalysisStartedOrCompletedInDateRange(sqlStartDate,
                sqlEndDate);
        Map<String, List<SampleTypeAdditionalFieldPayload>> additionalFieldsBySampleType = new HashMap<>();
        Map<String, Map<String, String>> additionalFieldValuesBySampleItem = new HashMap<>();

        DashBoardTile.TileType.stream().forEach(type -> {
            switch (type) {
            case ORDERS_IN_PROGRESS:
                String notStartedId = iStatusService.getStatusID(AnalysisStatus.NotStarted);
                long uniqueOrders = allAnalysesInRange.stream().filter(a -> a.getStatusId().equals(notStartedId))
                        .map(a -> a.getSampleItem().getSample().getId()).distinct().count();
                metrics.setOrdersInProgress((int) uniqueOrders);
                break;
            case AWAITING_SAMPLE:
                String nsId = iStatusService.getStatusID(AnalysisStatus.NotStarted);
                long incompleteSamples = allAnalysesInRange.stream()
                        .filter(a -> a.getStatusId().equals(nsId) && a.getSampleItem() != null).filter(a -> {
                            return !isSampleItemCompleteForDashboard(a.getSampleItem(), additionalFieldsBySampleType,
                                    additionalFieldValuesBySampleItem);
                        }).map(a -> a.getSampleItem().getId()).distinct().count();

                metrics.setAwaitingSample((int) incompleteSamples);
                break;
            case AWAITING_RESULTS:
                String notStartedForSamplesId = iStatusService.getStatusID(AnalysisStatus.NotStarted);
                long awaitingResultRows = allAnalysesInRange.stream()
                        .filter(a -> a.getStatusId().equals(notStartedForSamplesId) && a.getSampleItem() != null)
                        .filter(a -> {
                            return isSampleItemCompleteForDashboard(a.getSampleItem(), additionalFieldsBySampleType,
                                    additionalFieldValuesBySampleItem);
                        }).count();
                metrics.setAwaitingResults((int) awaitingResultRows);
                break;
            case ORDERS_READY_FOR_VALIDATION:
                String readyId = iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance);
                long ready = allAnalysesInRange.stream().filter(a -> a.getStatusId().equals(readyId)).count();
                metrics.setOrdersReadyForValidation((int) ready);
                break;
            case ORDERS_COMPLETED_TODAY: {
                String finId = iStatusService.getStatusID(AnalysisStatus.Finalized);
                String rejId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);

                // 1. Identificar Órdenes que aún tienen tests en proceso
                Set<String> incompleteSampleIds = new HashSet<>();
                for (Analysis a : allAnalysesInRange) {
                    if (!a.getStatusId().equals(finId) && !a.getStatusId().equals(rejId)) {
                        if (a.getSampleItem() != null && a.getSampleItem().getSample() != null) {
                            incompleteSampleIds.add(a.getSampleItem().getSample().getId());
                        }
                    }
                }

                Set<String> completedOrderIds = new HashSet<>();
                for (Analysis a : allAnalysesInRange) {
                    if (a.getStatusId().equals(finId)) {
                        if (a.getSampleItem() != null && a.getSampleItem().getSample() != null) {
                            String sampleId = a.getSampleItem().getSample().getId();
                            
                            if (!incompleteSampleIds.contains(sampleId)) {
                                completedOrderIds.add(sampleId);
                            }
                        }
                    }
                }

                metrics.setOrdersCompletedToday(completedOrderIds.size());
                break;
            }
            case ORDERS_PATIALLY_COMPLETED_TODAY:
            case ORDERS_ENTERED_BY_USER_TODAY: {
                
                String rejId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
                String finId = iStatusService.getStatusID(AnalysisStatus.Finalized);
                long partial = allAnalysesInRange.stream()
                        .filter(a -> !a.getStatusId().equals(rejId) && !a.getStatusId().equals(finId))
                        .map(a -> a.getSampleItem().getSample().getId()).distinct().count();
                metrics.setPatiallyCompletedToday((int) partial);
                metrics.setOrderEnterdByUserToday((int) partial);
                break;
            }
            case ORDERS_REJECTED_TODAY:
                String rejectedId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
                long rejected = allAnalysesInRange.stream()
                        .filter(a -> a.getStatusId().equals(rejectedId))
                        .map(a -> a.getSampleItem().getSample().getId()).distinct().count();
                metrics.setOrdersRejectedToday((int) rejected);
                break;
            case UN_PRINTED_RESULTS:
                long unprintedOrders = unprintedResults(sqlStartDate, sqlEndDate).stream()
                        .map(a -> a.getSampleItem().getSample().getId())
                        .distinct()
                        .count();
                metrics.setUnPritendResults((int) unprintedOrders);
                break;
            case INCOMING_ORDERS:
                List<Integer> estausIds = new ArrayList<>();
                estausIds.add(Integer.parseInt(iStatusService.getStatusID(ExternalOrderStatus.Entered)));
                estausIds.add(Integer.parseInt(iStatusService.getStatusID(ExternalOrderStatus.NonConforming)));
                metrics.setIncomigOrders(electronicOrderService.getCountOfElectronicOrdersByStatusList(estausIds));
                break;
            case AVERAGE_TURN_AROUND_TIME:
                java.sql.Date allTimeStart = java.sql.Date.valueOf("2000-01-01");
                java.sql.Date allTimeEnd = new java.sql.Date(System.currentTimeMillis());
                metrics.setAverageTurnAroudTime(calculateAverageReceptionToValidationTime(allTimeStart, allTimeEnd));
                break;
            case DELAYED_TURN_AROUND:
                long delayedOrders = analysesWithDelayedTurnAroundTime(sqlStartDate, sqlEndDate).stream()
                        .map(a -> a.getSampleItem().getSample().getId())
                        .distinct()
                        .count();
                metrics.setDelayedTurnAround((int) delayedOrders);
                break;
            default:
                break;
            }
        });

        return metrics;
    }

    /**
     * Get the list of orders to be displayed on the dashboard. It will return a
     * list of orders based on the type of the list in paginated manner.
     */
    @GetMapping(value = "home-dashboard/{listType}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PatientDashBoardForm getDashBoardDisplayList(HttpServletRequest request,
            @PathVariable DashBoardTile.TileType listType, @RequestParam(required = false) String systemUserId,
            @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String testType)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        PatientDashBoardForm response = new PatientDashBoardForm();
        PatientDashBoardPaging paging = new PatientDashBoardPaging();
        List<OrderDisplayBean> orderDisplayBeans = new ArrayList<>();

        String requestedPage = request.getParameter("page");
        if (GenericValidator.isBlankOrNull(requestedPage)) {
            
            orderDisplayBeans = retreiveOrders(listType, systemUserId, startDate, endDate, testType);

            if (orderDisplayBeans != null) {
                orderDisplayBeans.sort((bean1, bean2) -> {
                    String date1 = bean1.getOrderDate() != null ? bean1.getOrderDate() : "";
                    String date2 = bean2.getOrderDate() != null ? bean2.getOrderDate() : "";
                    
                    return date2.compareTo(date1);
                });
            }

            paging.setDatabaseResults(request, response, orderDisplayBeans);
        } else {
            int requestedPageNumber = Integer.parseInt(requestedPage);
            paging.page(request, response, requestedPageNumber);
        }

        return response;
    }

    /**
     * Returns the list of orders based on the type of the list provided by the
     * getdashBoardDisplayList method.
     */
    private List<OrderDisplayBean> retreiveOrders(DashBoardTile.TileType listType, String systemUserId,
            String startDate, String endDate, String testType) {
        java.sql.Date sqlStartDate = (startDate != null && !startDate.isEmpty()) ? java.sql.Date.valueOf(startDate)
                : java.sql.Date.valueOf("2000-01-01");

        java.sql.Date sqlEndDate;
        if (endDate != null && !endDate.isEmpty()) {
            
            sqlEndDate = java.sql.Date.valueOf(LocalDate.parse(endDate).plusDays(1));
        } else {
            
            sqlEndDate = java.sql.Date.valueOf(LocalDate.now().plusDays(1));
        }

        List<Analysis> allAnalysesInRange = analysisService.getAnalysisStartedOrCompletedInDateRange(sqlStartDate,
                sqlEndDate);
        List<Analysis> filteredAnalyses = new ArrayList<>();
        Map<String, List<SampleTypeAdditionalFieldPayload>> additionalFieldsBySampleType = new HashMap<>();
        Map<String, Map<String, String>> additionalFieldValuesBySampleItem = new HashMap<>();

        switch (listType) {
        case ORDERS_IN_PROGRESS:
            String notStartedId = iStatusService.getStatusID(AnalysisStatus.NotStarted);
            allAnalysesInRange.forEach(a -> {
                if (a.getStatusId().equals(notStartedId))
                    filteredAnalyses.add(a);
            });

            return convertAnalysesToGroupedOrderBean(filteredAnalyses);

        case AWAITING_SAMPLE:
            String nsIdId = iStatusService.getStatusID(AnalysisStatus.NotStarted);
            allAnalysesInRange.forEach(a -> {
                SampleItem si = a.getSampleItem();
                if (a.getStatusId().equals(nsIdId) && si != null) {
                    if (!isSampleItemCompleteForDashboard(si, additionalFieldsBySampleType,
                            additionalFieldValuesBySampleItem)) {
                        filteredAnalyses.add(a);
                    }
                }
            });
            return convertAnalysesToGroupedBySampleItemOrderBean(filteredAnalyses);

        case ORDERS_READY_FOR_VALIDATION:
            String readyId = iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance);
            allAnalysesInRange.forEach(a -> {
                if (a.getStatusId().equals(readyId))
                    filteredAnalyses.add(a);
            });
            return convertAnalysesToOrderBean(filterByTestType(filteredAnalyses, testType));

        case AWAITING_RESULTS:
            String awaitingId = iStatusService.getStatusID(AnalysisStatus.NotStarted);
            allAnalysesInRange.forEach(a -> {
                SampleItem si = a.getSampleItem();
                if (a.getStatusId().equals(awaitingId) && si != null) {
                    if (isSampleItemCompleteForDashboard(si, additionalFieldsBySampleType,
                            additionalFieldValuesBySampleItem)) {
                        filteredAnalyses.add(a);
                    }
                }
            });

            return convertAnalysesToOrderBean(filterByTestType(filteredAnalyses, testType));

        case ORDERS_COMPLETED_TODAY: {
            String finId = iStatusService.getStatusID(AnalysisStatus.Finalized);
            String rejId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);

            Set<String> incompleteSampleIds = new HashSet<>();
            for (Analysis a : allAnalysesInRange) {
                if (!a.getStatusId().equals(finId) && !a.getStatusId().equals(rejId)) {
                    if (a.getSampleItem() != null && a.getSampleItem().getSample() != null) {
                        incompleteSampleIds.add(a.getSampleItem().getSample().getId());
                    }
                }
            }

            allAnalysesInRange.forEach(a -> {
                if (a.getStatusId().equals(finId)) {
                    if (a.getSampleItem() != null && a.getSampleItem().getSample() != null) {
                        String sampleId = a.getSampleItem().getSample().getId();
                        if (!incompleteSampleIds.contains(sampleId)) {
                            filteredAnalyses.add(a);
                        }
                    }
                }
            });
            return convertAnalysesToGroupedOrderBean(filteredAnalyses);
        }

        case ORDERS_PATIALLY_COMPLETED_TODAY:
            String rejId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
            String finId = iStatusService.getStatusID(AnalysisStatus.Finalized);
            allAnalysesInRange.forEach(a -> {
                if (!a.getStatusId().equals(rejId) && !a.getStatusId().equals(finId))
                    filteredAnalyses.add(a);
            });
            return convertAnalysesToGroupedOrderBean(filteredAnalyses);

        case ORDERS_ENTERED_BY_USER_TODAY:
            String rejectedOnly = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
            allAnalysesInRange.forEach(a -> {
                if (!a.getStatusId().equals(rejectedOnly))
                    filteredAnalyses.add(a);
            });
            return convertAnalysesToUserOrdersBean(filteredAnalyses);

        case ORDERS_REJECTED_TODAY:
            String rejectedId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
            allAnalysesInRange.forEach(a -> {
                if (a.getStatusId().equals(rejectedId))
                    filteredAnalyses.add(a);
            });
            return convertAnalysesToGroupedOrderBean(filteredAnalyses);

        case UN_PRINTED_RESULTS:
            return convertAnalysesToGroupedOrderBean(unprintedResults(sqlStartDate, sqlEndDate));

        case INCOMING_ORDERS:
            List<Integer> estausIds = new ArrayList<>();
            estausIds.add(Integer.parseInt(iStatusService.getStatusID(ExternalOrderStatus.Entered)));
            estausIds.add(Integer.parseInt(iStatusService.getStatusID(ExternalOrderStatus.NonConforming)));
            List<ElectronicOrder> eOrders = electronicOrderService.getAllElectronicOrdersByStatusList(estausIds,
                    ElectronicOrder.SortOrder.STATUS_ID);
            return convertElectronicToOrderBean(eOrders);

        case AVERAGE_TURN_AROUND_TIME:
            return new ArrayList<>();

        case DELAYED_TURN_AROUND:
            return convertAnalysesToGroupedOrderBean(analysesWithDelayedTurnAroundTime(sqlStartDate, sqlEndDate));

        case ORDERS_FOR_USER:
            if (StringUtils.isNotBlank(systemUserId)) {
                String rejUser = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
                allAnalysesInRange.forEach(a -> {
                    if (!a.getStatusId().equals(rejUser))
                        filteredAnalyses.add(a);
                });
                return getUserOrderBeans(filteredAnalyses, systemUserId);
            }
        }
        return new ArrayList<>();
    }

    private List<Analysis> filterByTestType(List<Analysis> analyses, String testType) {
        if (analyses == null || analyses.isEmpty() || StringUtils.isBlank(testType)
                || StringUtils.equalsIgnoreCase(testType, "all")) {
            return analyses;
        }

        List<Analysis> filtered = new ArrayList<>();
        for (Analysis analysis : analyses) {
            if (analysis == null || analysis.getTest() == null) {
                continue;
            }
            String localizedName = analysis.getTest().getLocalizedName();
            if (StringUtils.isNotBlank(localizedName) && StringUtils.equalsIgnoreCase(localizedName, testType)) {
                filtered.add(analysis);
            }
        }
        return filtered;
    }

    @GetMapping(value = "home-dashboard/turn-around-time-metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public AverageTimeDisplayBean getDasBoardAverageTurnAroundTime() {

        java.sql.Date sqlStartDate = java.sql.Date.valueOf("2000-01-01");
        java.sql.Date sqlEndDate = new java.sql.Date(System.currentTimeMillis());

        AverageTimeDisplayBean timeBean = new AverageTimeDisplayBean();
        timeBean.setReceptionToResult(calculateAverageReceptionToResultTime(sqlStartDate, sqlEndDate));
        timeBean.setReceptionToValidation(calculateAverageReceptionToValidationTime(sqlStartDate, sqlEndDate));
        timeBean.setResultToValidation(calculateAverageResultToValidationTime(sqlStartDate, sqlEndDate));
        return timeBean;
    }

    @GetMapping(value = "home-dashboard/visibility-config", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Boolean> getDashboardVisibilityConfig() {
        Map<String, Boolean> visibilityMap = new HashMap<>();
        String[][] configMapping = {
            {"dash_show_partial_comp", "ORDERS_PATIALLY_COMPLETED_TODAY"},
            {"dash_show_user_orders", "ORDERS_ENTERED_BY_USER_TODAY"},
            {"dash_show_rejected", "ORDERS_REJECTED_TODAY"},
            {"dash_show_unprinted", "UN_PRINTED_RESULTS"},
            {"dash_show_incoming", "INCOMING_ORDERS"}
        };
        for (String[] mapping : configMapping) {
            try {
                String dbValue = siteInformationService.getSiteInformationByName(mapping[0]).getValue();
                visibilityMap.put(mapping[1], Boolean.parseBoolean(dbValue));
            } catch (Exception e) {
                visibilityMap.put(mapping[1], true);
            }
        }
        return visibilityMap;
    }
}
