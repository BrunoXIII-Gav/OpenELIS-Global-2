package org.openelisglobal.common.rest.provider;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import org.openelisglobal.sample.valueholder.Sample;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.sampleitem.valueholder.SampleItem;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.valueholder.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

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
    private FhirUtil fhirUtil;

    @Autowired
    private FhirConfig fhirConfig;

    @Autowired
    private TestService testService;

    @Autowired
    SystemUserService systemUserService;

    private double calculateAverageReceptionToValidationTime(java.sql.Date start, java.sql.Date end) {
        List<Analysis> analyses = analysisService.getAnalysisStartedOrCompletedInDateRange(start, end);
        String finalizedId = iStatusService.getStatusID(AnalysisStatus.Finalized);
        List<Long> hours = new ArrayList<>();

        for (Analysis analysis : analyses) {
            if (analysis.getStatusId().equals(finalizedId) && analysis.getStartedDate() != null
                    && analysis.getReleasedDate() != null) {
                LocalDate localStartDate = analysis.getStartedDate().toLocalDate();
                LocalDate localEndDate = analysis.getReleasedDate().toLocalDate();
                hours.add(Duration.between(localStartDate.atStartOfDay(), localEndDate.atStartOfDay()).toHours());
            }
        }
        return hours.isEmpty() ? 0.0 : hours.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private double calculateAverageReceptionToResultTime(java.sql.Date start, java.sql.Date end) {
        List<Analysis> analyses = analysisService.getAnalysisStartedOrCompletedInDateRange(start, end);
        String rejectedId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
        List<Long> hours = new ArrayList<>();

        for (Analysis analysis : analyses) {
            if (!analysis.getStatusId().equals(rejectedId) && analysis.getStartedDate() != null
                    && analysis.getCompletedDate() != null) {
                LocalDate localStartDate = analysis.getStartedDate().toLocalDate();
                LocalDate localEndDate = analysis.getCompletedDate().toLocalDate();
                hours.add(Duration.between(localStartDate.atStartOfDay(), localEndDate.atStartOfDay()).toHours());
            }
        }
        return hours.isEmpty() ? 0.0 : hours.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private double calculateAverageResultToValidationTime(java.sql.Date start, java.sql.Date end) {
        List<Analysis> analyses = analysisService.getAnalysisStartedOrCompletedInDateRange(start, end);
        String finalizedId = iStatusService.getStatusID(AnalysisStatus.Finalized);
        List<Long> hours = new ArrayList<>();

        for (Analysis analysis : analyses) {
            if (analysis.getStatusId().equals(finalizedId) && analysis.getCompletedDate() != null
                    && analysis.getReleasedDate() != null) {
                LocalDate localStartDate = analysis.getCompletedDate().toLocalDate();
                LocalDate localEndDate = analysis.getReleasedDate().toLocalDate();
                hours.add(Duration.between(localStartDate.atStartOfDay(), localEndDate.atStartOfDay()).toHours());
            }
        }
        return hours.isEmpty() ? 0.0 : hours.stream().mapToLong(Long::longValue).average().orElse(0.0);
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

                        try {
                            if (sampleHumanService.getPatientForSample(sample) != null) {
                                orderBean.setPatientId(sampleHumanService.getPatientForSample(sample).getNationalId());
                            }
                        } catch (Exception e) {
                            orderBean.setPatientId("");
                        }
                    } else {
                        orderBean.setPriority("");
                        orderBean.setLabNumber("");
                        orderBean.setPatientId("");
                    }

                    if (analysis.getLastupdated() != null) {
                        orderBean.setOrderDate(sdf.format(analysis.getLastupdated()));
                    } else if (analysis.getSampleItem() != null && analysis.getSampleItem().getLastupdated() != null) {
                        orderBean.setOrderDate(sdf.format(analysis.getSampleItem().getLastupdated()));
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
        Set<String> processedLabNumbers = new HashSet<>();

        if (analyses != null) {
            analyses.forEach(analysis -> {
                if (analysis != null && analysis.getSampleItem() != null
                        && analysis.getSampleItem().getSample() != null) {
                    Sample sample = analysis.getSampleItem().getSample();
                    String labNumber = sample.getAccessionNumber() != null ? sample.getAccessionNumber() : "";

                    if (!labNumber.isEmpty() && !processedLabNumbers.contains(labNumber)) {
                        processedLabNumbers.add(labNumber);

                        OrderDisplayBean orderBean = new OrderDisplayBean();
                        orderBean.setId(sample.getId());
                        orderBean.setPriority(sample.getPriority() != null ? sample.getPriority().toString() : "");
                        orderBean.setLabNumber(labNumber);
                        orderBean.setPatientId(sampleHumanService.getPatientForSample(sample).getNationalId());
                        orderBean.setOrderDate(
                                sample.getLastupdated() != null ? sdf.format(sample.getLastupdated()) : "");
                        orderBean.setTestName("");
                        orderBean.setTestSection("");

                        orderBeanList.add(orderBean);
                    }
                }
            });
        }
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
                userOrderBean.setCountOfOrdersEntered(userOrdersMap.get(userId).size());
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
            return convertAnalysesToOrderBean(userOrdersMap.get(userId));
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

            orderBean.setPatientId(eOrder.getPatient().getNationalId());
            orderBeanList.add(orderBean);
        });

        return orderBeanList;
    }

    @GetMapping(value = "home-dashboard/metrics", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public DashBoardMetrics getDasBoardTiles(@RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        DashBoardMetrics metrics = new DashBoardMetrics();

        java.sql.Date sqlStartDate = (startDate != null && !startDate.isEmpty()) ? java.sql.Date.valueOf(startDate)
                : java.sql.Date.valueOf("2000-01-01");

        java.sql.Date sqlEndDate = (endDate != null && !endDate.isEmpty()) ? java.sql.Date.valueOf(endDate)
                : new java.sql.Date(System.currentTimeMillis());

        List<Analysis> allAnalysesInRange = analysisService.getAnalysisStartedOrCompletedInDateRange(sqlStartDate,
                sqlEndDate);

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

                            SampleItem si = a.getSampleItem();
                            boolean hasCollector = si.getCollector() != null && !si.getCollector().trim().isEmpty();
                            boolean hasCollectionDate = si.getCollectionDate() != null;

                            return !(hasCollector && hasCollectionDate);
                        }).count();

                metrics.setAwaitingSample((int) incompleteSamples);
                break;
            case AWAITING_RESULTS:
                String notStartedForSamplesId = iStatusService.getStatusID(AnalysisStatus.NotStarted);
                long uniqueSamples = allAnalysesInRange.stream()
                        .filter(a -> a.getStatusId().equals(notStartedForSamplesId) && a.getSampleItem() != null)
                        .filter(a -> {

                            SampleItem si = a.getSampleItem();
                            boolean hasCollector = si.getCollector() != null && !si.getCollector().trim().isEmpty();
                            boolean hasCollectionDate = si.getCollectionDate() != null;

                            return hasCollector && hasCollectionDate;
                        }).map(a -> a.getSampleItem().getId()).distinct().count();
                metrics.setAwaitingResults((int) uniqueSamples);
                break;
            case ORDERS_READY_FOR_VALIDATION:
                String readyId = iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance);
                long ready = allAnalysesInRange.stream().filter(a -> a.getStatusId().equals(readyId)).count();
                metrics.setOrdersReadyForValidation((int) ready);
                break;
            case ORDERS_COMPLETED_TODAY:
                String finalizedId = iStatusService.getStatusID(AnalysisStatus.Finalized);
                long completed = allAnalysesInRange.stream().filter(a -> a.getStatusId().equals(finalizedId)).count();
                metrics.setOrdersCompletedToday((int) completed);
                break;
            case ORDERS_PATIALLY_COMPLETED_TODAY:
            case ORDERS_ENTERED_BY_USER_TODAY:
                String rejId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
                String finId = iStatusService.getStatusID(AnalysisStatus.Finalized);
                long partial = allAnalysesInRange.stream()
                        .filter(a -> !a.getStatusId().equals(rejId) && !a.getStatusId().equals(finId)).count();
                metrics.setPatiallyCompletedToday((int) partial);
                metrics.setOrderEnterdByUserToday((int) partial);
                break;
            case ORDERS_REJECTED_TODAY:
                String rejectedId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
                long rejected = allAnalysesInRange.stream().filter(a -> a.getStatusId().equals(rejectedId)).count();
                metrics.setOrdersRejectedToday((int) rejected);
                break;
            case UN_PRINTED_RESULTS:
                metrics.setUnPritendResults(unprintedResults(sqlStartDate, sqlEndDate).size());
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
                metrics.setDelayedTurnAround(analysesWithDelayedTurnAroundTime(sqlStartDate, sqlEndDate).size());
                break;
            default:
                break;
            }
        });

        return metrics;
    }

    /**
     * Get the list of orders to be displayed on the dashboard. It will returna a
     * list of orders based on the type of the list in paginated manner.
     */
    @GetMapping(value = "home-dashboard/{listType}", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PatientDashBoardForm getDashBoardDisplayList(HttpServletRequest request,
            @PathVariable DashBoardTile.TileType listType, @RequestParam(required = false) String systemUserId,
            @RequestParam(required = false) String startDate, @RequestParam(required = false) String endDate)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {

        PatientDashBoardForm response = new PatientDashBoardForm();
        PatientDashBoardPaging paging = new PatientDashBoardPaging();
        List<OrderDisplayBean> orderDisplayBeans = new ArrayList<>();

        String requestedPage = request.getParameter("page");
        if (GenericValidator.isBlankOrNull(requestedPage)) {
            orderDisplayBeans = retreiveOrders(listType, systemUserId, startDate, endDate);

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
            String startDate, String endDate) {
        java.sql.Date sqlStartDate = (startDate != null && !startDate.isEmpty()) ? java.sql.Date.valueOf(startDate)
                : java.sql.Date.valueOf("2000-01-01");

        java.sql.Date sqlEndDate = (endDate != null && !endDate.isEmpty()) ? java.sql.Date.valueOf(endDate)
                : new java.sql.Date(System.currentTimeMillis());

        List<Analysis> allAnalysesInRange = analysisService.getAnalysisStartedOrCompletedInDateRange(sqlStartDate,
                sqlEndDate);
        List<Analysis> filteredAnalyses = new ArrayList<>();

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
                    boolean hasCollector = si.getCollector() != null && !si.getCollector().trim().isEmpty();
                    boolean hasCollectionDate = si.getCollectionDate() != null;

                    if (!(hasCollector && hasCollectionDate)) {
                        filteredAnalyses.add(a);
                    }
                }
            });
            return convertAnalysesToOrderBean(filteredAnalyses);

        case ORDERS_READY_FOR_VALIDATION:
            String readyId = iStatusService.getStatusID(AnalysisStatus.TechnicalAcceptance);
            allAnalysesInRange.forEach(a -> {
                if (a.getStatusId().equals(readyId))
                    filteredAnalyses.add(a);
            });
            return convertAnalysesToOrderBean(filteredAnalyses);

        case AWAITING_RESULTS:
            String awaitingId = iStatusService.getStatusID(AnalysisStatus.NotStarted);
            allAnalysesInRange.forEach(a -> {
                SampleItem si = a.getSampleItem();
                if (a.getStatusId().equals(awaitingId) && si != null) {
                    // EL EMBUDO INVERSO: Solo agregamos a la tabla si están completas
                    boolean hasCollector = si.getCollector() != null && !si.getCollector().trim().isEmpty();
                    boolean hasCollectionDate = si.getCollectionDate() != null;

                    if (hasCollector && hasCollectionDate) {
                        filteredAnalyses.add(a);
                    }
                }
            });

            return convertAnalysesToOrderBean(filteredAnalyses);

        case ORDERS_COMPLETED_TODAY:
            String finalizedId = iStatusService.getStatusID(AnalysisStatus.Finalized);
            allAnalysesInRange.forEach(a -> {
                if (a.getStatusId().equals(finalizedId))
                    filteredAnalyses.add(a);
            });
            return convertAnalysesToOrderBean(filteredAnalyses);

        case ORDERS_PATIALLY_COMPLETED_TODAY:
            String rejId = iStatusService.getStatusID(AnalysisStatus.SampleRejected);
            String finId = iStatusService.getStatusID(AnalysisStatus.Finalized);
            allAnalysesInRange.forEach(a -> {
                if (!a.getStatusId().equals(rejId) && !a.getStatusId().equals(finId))
                    filteredAnalyses.add(a);
            });
            return convertAnalysesToOrderBean(filteredAnalyses);

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
            return convertAnalysesToOrderBean(filteredAnalyses);

        case UN_PRINTED_RESULTS:
            return convertAnalysesToOrderBean(unprintedResults(sqlStartDate, sqlEndDate));

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
            return convertAnalysesToOrderBean(analysesWithDelayedTurnAroundTime(sqlStartDate, sqlEndDate));

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
}
