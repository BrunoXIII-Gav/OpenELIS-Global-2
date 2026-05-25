package org.openelisglobal.sample.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;
import java.lang.reflect.InvocationTargetException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.hibernate.StaleObjectStateException;
import org.hl7.fhir.r4.model.Enumerations.ResourceType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.openelisglobal.analysis.valueholder.Analysis;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.formfields.FormFields;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.provider.validation.AlphanumAccessionValidator;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.services.DisplayListService.ListType;
import org.openelisglobal.common.services.SampleOrderService;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DateUtil;
import org.openelisglobal.common.validator.BaseErrors;
import org.openelisglobal.dataexchange.fhir.FhirUtil;
import org.openelisglobal.dataexchange.fhir.service.FhirTransformService;
import org.openelisglobal.dataexchange.order.valueholder.ElectronicOrder;
import org.openelisglobal.dataexchange.service.order.ElectronicOrderService;
import org.openelisglobal.internationalization.MessageUtil;
import org.openelisglobal.notifications.dao.NotificationDAO;
import org.openelisglobal.notifications.entity.Notification;
import org.openelisglobal.organization.service.OrganizationService;
import org.openelisglobal.organization.valueholder.Organization;
import org.openelisglobal.patient.action.IPatientUpdate;
import org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.action.bean.PatientSearch;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.sample.action.util.SamplePatientUpdateData;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.controller.BaseSampleEntryController;
import org.openelisglobal.sample.event.SamplePatientUpdateDataCreatedEvent;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.sample.service.PatientManagementUpdate;
import org.openelisglobal.sample.service.SamplePatientEntryService;
import org.openelisglobal.sample.service.SampleService;
import org.openelisglobal.sample.validator.SamplePatientEntryFormValidator;
import org.openelisglobal.sample.valueholder.OrderPriority;
import org.openelisglobal.sample.valueholder.SampleAdditionalField;
import org.openelisglobal.sample.valueholder.SampleAdditionalField.AdditionalFieldName;
import org.openelisglobal.spring.util.SpringContext;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.service.UserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.userrole.service.UserRoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

@Controller
@RequestMapping(value = "/rest/")
public class SamplePatientEntryRestController extends BaseSampleEntryController {
    private static final String ERROR_MESSAGE_HEADER = "X-OpenELIS-Error-Message";
    private static final String PROP_PROVIDER_SELECTION_POLICY = "orderProviderSelectionPolicy";
    private static final String PROP_PROVIDER_SELECTION_OVERRIDE_ROLES = "orderProviderOverrideRoles";
    private static final String PROVIDER_SELECTION_POLICY_SELF_ONLY = "SELF_ONLY";

    @Value("${org.openelisglobal.requester.identifier:}")
    private String requestFhirUuid;

    private static final String[] ALLOWED_FIELDS = new String[] { "rememberSiteAndRequester", "customNotificationLogic",
            "patientEmailNotificationTestIds", "patientSMSNotificationTestIds", "providerEmailNotificationTestIds",
            "providerSMSNotificationTestIds", "patientProperties.currentDate", "patientProperties.patientLastUpdated",
            "patientProperties.personLastUpdated", "patientProperties.patientUpdateStatus",
            "patientProperties.patientPK", "patientProperties.guid", "patientProperties.fhirUuid",
            "patientProperties.STnumber", "patientProperties.subjectNumber", "patientProperties.nationalId",
            "patientProperties.dni", "patientProperties.passportNumber", "patientProperties.foreignId",
            "patientProperties.lastName", "patientProperties.firstName", "patientProperties.aka",
            "patientProperties.mothersName", "patientProperties.mothersInitial", "patientProperties.streetAddress",
            "patientProperties.commune", "patientProperties.city", "patientProperties.addressDepartment",
            "patientProperties.addressDepartment", "patientPhone", "patientProperties.primaryPhone",
            "patientProperties.email", "patientProperties.healthRegion", "patientProperties.healthDistrict",
            "patientProperties.birthDateForDisplay", "patientProperties.age", "patientProperties.gender",
            "patientProperties.patientType", "patientProperties.insuranceNumber", "patientProperties.occupation",
            "patientProperties.education", "patientProperties.maritialStatus", "patientProperties.nationality",
            "patientProperties.otherNationality", "patientClinicalProperties.stdOther",
            "patientClinicalProperties.tbDiarrhae", "patientClinicalProperties.stdZona",
            "patientClinicalProperties.tbPrurigol", "patientClinicalProperties.stdKaposi",
            "patientClinicalProperties.tbMenigitis", "patientClinicalProperties.stdCandidiasis",
            "patientClinicalProperties.tbCerebral", "patientClinicalProperties.stdColonCancer",
            "patientClinicalProperties.tbExtraPulmanary", "patientClinicalProperties.arvProphyaxixType",
            "patientClinicalProperties.arvTreatmentReceiving", "patientClinicalProperties.arvTreatmentRemembered",
            "patientClinicalProperties.arvTreatment1", "patientClinicalProperties.arvTreatment2",
            "patientClinicalProperties.arvTreatment3", "patientClinicalProperties.arvTreatment4",
            "patientClinicalProperties.cotrimoxazoleReceiving", "patientClinicalProperties.cotrimoxazoleType",
            "patientClinicalProperties.infectionExtraPulmanary", "patientClinicalProperties.stdInfectionColon",
            "patientClinicalProperties.infectionCerebral", "patientClinicalProperties.stdInfectionCandidiasis",
            "patientClinicalProperties.infectionMeningitis", "patientClinicalProperties.stdInfectionKaposi",
            "patientClinicalProperties.infectionPrurigol", "patientClinicalProperties.stdInfectionZona",
            "patientClinicalProperties.infectionOther", "patientClinicalProperties.infectionUnderTreatment",
            "patientClinicalProperties.weight", "patientClinicalProperties.karnofskyScore",
            //
            "initialSampleConditionList", "sampleXML",
            //
            "sampleOrderItems.newRequesterName", "sampleOrderItems.modified", "sampleOrderItems.sampleId",
            "sampleOrderItems.labNo", "sampleOrderItems.requestDate", "sampleOrderItems.receivedDateForDisplay",
            "sampleOrderItems.receivedTime", "sampleOrderItems.nextVisitDate", "sampleOrderItems.requesterSampleID",
            "sampleOrderItems.referringPatientNumber", "sampleOrderItems.referringSiteId",
            "referringSiteDepartmentName", "sampleOrderItems.referringSiteDepartmentId",
            "sampleOrderItems.referringSiteName", "sampleOrderItems.referringSiteCode", "sampleOrderItems.program",
            "sampleOrderItems.providerPersonId", "sampleOrderItems.providerLastName",
            "sampleOrderItems.providerFirstName", "sampleOrderItems.providerWorkPhone", "sampleOrderItems.providerFax",
            "sampleOrderItems.providerEmail", "sampleOrderItems.providerCmp", "sampleOrderItems.providerRne",
            "sampleOrderItems.providerDni", "sampleOrderItems.providerSpecialty",
            "sampleOrderItems.facilityAddressStreet", "sampleOrderItems.facilityAddressCommune",
            "sampleOrderItems.facilityPhone", "sampleOrderItems.facilityFax", "sampleOrderItems.paymentOptionSelection",
            "sampleOrderItems.billingReferenceNumber", "sampleOrderItems.testLocationCode",
            "sampleOrderItems.otherLocationCode", "sampleOrderItems.contactTracingIndexName",
            "sampleOrderItems.contactTracingIndexRecordNumber", "sampleOrderItems.priority",
            //
            "currentDate", "sampleOrderItems.newRequesterName", "sampleOrderItems.externalOrderNumber",
            // referral
            "referralItems*.additionalTestsXMLWad", "referralItems*.referralResultId", "referralItems*.referralId",
            "referralItems*.referredResultType", "referralItems*.modified", "referralItems*.inLabResultId",
            "referralItems*.referralReasonId", "referralItems*.referrer", "referralItems*.referredInstituteId",
            "referralItems*.referredSendDate", "referralItems*.referredTestId", "referralItems*.referredReportDate",
            "referralItems*.note", "useReferral", "sampleOrderItems.additionalQuestions", "sampleOrderItems.programId",
            "sampleOrderItems.additionalFieldValues*", "sampleOrderItems.additionalFieldFiles*",
            "sampleOrderItems.additionalFieldReservationTokens*" };

    @Autowired
    private SamplePatientEntryFormValidator formValidator;

    @Autowired
    private SamplePatientEntryService samplePatientService;

    @Autowired
    private FhirTransformService fhirTransformService;

    @Autowired
    private UserService userService;

    @Autowired
    private ProviderService providerService;
    @Autowired
    private PersonService personService;

    @Autowired
    private ElectronicOrderService electronicOrderService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private FhirUtil fhirUtil;
    @Autowired
    private NotificationDAO notificationDAO;
    @Autowired
    private UserRoleService userRoleService;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private SampleService sampleService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @GetMapping(value = "SamplePatientEntry", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public SamplePatientEntryForm showSamplePatientEntry(HttpServletRequest request,
            @RequestParam(value = ID, required = false) @Pattern(regexp = "[a-zA-Z0-9 -]*") String externalOrderNumber)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        SamplePatientEntryForm form = new SamplePatientEntryForm();

        request.getSession().setAttribute(SAVE_DISABLED, TRUE);
        setupForm(form, request, externalOrderNumber);
        Map<String, ?> inputFlashMap = RequestContextUtils.getInputFlashMap(request);
        if (inputFlashMap != null) {
            form.getSampleOrderItems().setProviderId((String) inputFlashMap.get("sampleOrderItems.providerId"));
            form.getSampleOrderItems()
                    .setProviderPersonId((String) inputFlashMap.get("sampleOrderItems.providerPersonId"));
            form.getSampleOrderItems().setProviderEmail((String) inputFlashMap.get("sampleOrderItems.providerEmail"));
            form.getSampleOrderItems().setProviderFax((String) inputFlashMap.get("sampleOrderItems.providerfax"));
            form.getSampleOrderItems()
                    .setProviderFirstName((String) inputFlashMap.get("sampleOrderItems.providerFirstName"));
            form.getSampleOrderItems()
                    .setProviderLastName((String) inputFlashMap.get("sampleOrderItems.providerLastName"));
            form.getSampleOrderItems()
                    .setProviderWorkPhone((String) inputFlashMap.get("sampleOrderItems.providerWorkPhone"));
            form.getSampleOrderItems().setProviderCmp((String) inputFlashMap.get("sampleOrderItems.providerCmp"));
            form.getSampleOrderItems().setProviderRne((String) inputFlashMap.get("sampleOrderItems.providerRne"));
            form.getSampleOrderItems().setProviderDni((String) inputFlashMap.get("sampleOrderItems.providerDni"));
            form.getSampleOrderItems()
                    .setProviderSpecialty((String) inputFlashMap.get("sampleOrderItems.providerSpecialty"));
            form.getSampleOrderItems()
                    .setReferringSiteId((String) inputFlashMap.get("sampleOrderItems.referringSiteId"));
            form.getSampleOrderItems()
                    .setReferringSiteCode((String) inputFlashMap.get("sampleOrderItems.referringSiteCode"));
            form.getSampleOrderItems()
                    .setReferringSiteName((String) inputFlashMap.get("sampleOrderItems.referringSiteName"));
            form.getSampleOrderItems().setReferringSiteDepartmentId(
                    (String) inputFlashMap.get("sampleOrderItems.referringSiteDepartmentId"));
            form.getSampleOrderItems().setReferringSiteDepartmentName(
                    (String) inputFlashMap.get("sampleOrderItems.referringSiteDepartmentName"));
        }
        applySelfOnlyProviderPolicyToForm(request, form.getSampleOrderItems());
        addFlashMsgsToRequest(request);
        return form;
    }

    private void setupReferralOption(SamplePatientEntryForm form) {
        form.setReferralOrganizations(DisplayListService.getInstance().getList(ListType.REFERRAL_ORGANIZATIONS));
        form.setReferralReasons(DisplayListService.getInstance().getList(ListType.REFERRAL_REASONS));
    }

    @PostMapping(value = "SamplePatientEntry", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<SamplePatientEntryForm> samplePatientEntrySave(HttpServletRequest request,
            @Validated(SamplePatientEntryForm.SamplePatientEntry.class) @RequestBody SamplePatientEntryForm form,
            BindingResult result, RedirectAttributes redirectAttributes)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        enforceSelfOnlyProviderPolicy(request, form, result);

        formValidator.validate(form, result);
        if (result.hasErrors()) {
            saveErrors(result);
            setupForm(form, request, "");
            return buildErrorResponse(form, result, HttpStatus.BAD_REQUEST);
        }
        SamplePatientUpdateData updateData = new SamplePatientUpdateData(getSysUserId(request));

        PatientManagementInfo patientInfo = form.getPatientProperties();
        SampleOrderItem sampleOrder = form.getSampleOrderItems();

        boolean trackPayments = ConfigurationProperties.getInstance()
                .isPropertyValueEqual(Property.TRACK_PATIENT_PAYMENT, "true");

        String receivedDateForDisplay = sampleOrder.getReceivedDateForDisplay();

        if (!GenericValidator.isBlankOrNull(sampleOrder.getReceivedTime())) {
            receivedDateForDisplay += " " + sampleOrder.getReceivedTime();
        } else {
            receivedDateForDisplay += " 00:00";
        }

        updateData.setCollectionDateFromRecieveDateIfNeeded(receivedDateForDisplay);
        updateData.initializeRequester(sampleOrder);

        PatientManagementUpdate patientUpdate = SpringContext.getBean(PatientManagementUpdate.class);
        patientUpdate.setSysUserIdFromRequest(request);
        testAndInitializePatientForSaving(request, patientInfo, patientUpdate, updateData);

        updateData.setAccessionNumber(sampleOrder.getLabNo());
        updateData.setReferringId(sampleOrder.getExternalOrderNumber());
        updateData.setPriority(sampleOrder.getPriority());
        updateData.initProvider(sampleOrder);
        if (!GenericValidator.isBlankOrNull(sampleOrder.getProgramId())) {
            updateData.initProgramQuestions(sampleOrder.getProgramId(), sampleOrder.getAdditionalQuestions());
        }
        updateData.initSampleData(form.getSampleXML(), receivedDateForDisplay, trackPayments, sampleOrder);
        updateData.setPatientEmailNotificationTestIds(form.getPatientEmailNotificationTestIds());
        updateData.setPatientSMSNotificationTestIds(form.getPatientSMSNotificationTestIds());
        updateData.setProviderEmailNotificationTestIds(form.getProviderEmailNotificationTestIds());
        updateData.setProviderSMSNotificationTestIds(form.getProviderSMSNotificationTestIds());
        updateData.setCustomNotificationLogic(form.getCustomNotificationLogic());
        if (Boolean.valueOf(ConfigurationProperties.getInstance().getPropertyValue(Property.CONTACT_TRACING))) {
            setContactTracingInfo(updateData, sampleOrder);
        }
        updateData.validateSample(result);
        if (result.hasErrors()) {
            saveErrors(result);
            setupForm(form, request, "");
            return buildErrorResponse(form, result, HttpStatus.BAD_REQUEST);
        }

        try {
            samplePatientService.persistData(updateData, patientUpdate, patientInfo, form, request);
            try {
                SamplePatientUpdateDataCreatedEvent event = new SamplePatientUpdateDataCreatedEvent(this, updateData,
                        patientInfo, form);
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                LogEvent.logError(e);
            }

            if (sampleOrder.getPriority().equals(OrderPriority.STAT)) {
                List<String> systemUserIds = userRoleService.getUserIdsForRole(Constants.ROLE_RESULTS);
                List<Analysis> analyses = sampleService
                        .getAnalysis(sampleService.getSampleByAccessionNumber(sampleOrder.getLabNo()));
                String message = MessageUtil.getMessage("notification.order.stat",
                        AlphanumAccessionValidator.convertAlphaNumLabNumForDisplay(sampleOrder.getLabNo()));
                for (String userId : systemUserIds) {
                    List<Analysis> userAnalyses = userService.filterAnalysesByLabUnitRoles(userId, analyses,
                            Constants.ROLE_RESULTS);
                    if (userAnalyses != null && !userAnalyses.isEmpty()) {
                        List<String> tests = userAnalyses.stream().map(a -> a.getTest().getLocalizedName())
                                .collect(Collectors.toList());
                        String testString = String.join(", ", tests);
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
            }

            // String fhir_json = fhirTransformService.CreateFhirFromOESample(updateData,
            // patientUpdate, patientInfo, form, request);
        } catch (IllegalArgumentException e) {
            LogEvent.logError(e);
            String detail = buildIllegalArgumentExceptionDetail(e);
            result.reject("errors.ValidationException", new Object[] { detail }, detail);
            saveErrors(result);
            setupForm(form, request, "");
            request.setAttribute(ALLOW_EDITS_KEY, "false");
            return buildErrorResponse(form, result, HttpStatus.BAD_REQUEST, detail);
        } catch (LIMSRuntimeException e) {
            // ActionError error;
            HttpStatus status = HttpStatus.BAD_REQUEST;
            if (e.getCause() instanceof StaleObjectStateException) {
                // error = new ActionError("errors.OptimisticLockException", null, null);
                result.reject("errors.OptimisticLockException", "errors.OptimisticLockException");
                status = HttpStatus.CONFLICT;
            } else {
                LogEvent.logError(e);
                // error = new ActionError("errors.UpdateException", null, null);
                result.reject("errors.UpdateException", "errors.UpdateException");
            }
            LogEvent.logInfo(this.getClass().getSimpleName(), "samplePatientEntrySave", result.toString());

            // errors.add(ActionMessages.GLOBAL_MESSAGE, error);
            saveErrors(result);// TODO theses errors are not communicated to the frontend return an error code
                               // if svae is not successful

            setupForm(form, request, "");
            request.setAttribute(ALLOW_EDITS_KEY, "false");
            return buildErrorResponse(form, result, status, null);
        }
        redirectAttributes.addFlashAttribute(FWD_SUCCESS, true);
        if (form.getRememberSiteAndRequester()) {
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerId",
                    form.getSampleOrderItems().getProviderId());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerPersonId",
                    form.getSampleOrderItems().getProviderPersonId());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerEmail",
                    form.getSampleOrderItems().getProviderEmail());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerfax",
                    form.getSampleOrderItems().getProviderFax());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerFirstName",
                    form.getSampleOrderItems().getProviderFirstName());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerLastName",
                    form.getSampleOrderItems().getProviderLastName());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerWorkPhone",
                    form.getSampleOrderItems().getProviderWorkPhone());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerCmp",
                    form.getSampleOrderItems().getProviderCmp());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerRne",
                    form.getSampleOrderItems().getProviderRne());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerDni",
                    form.getSampleOrderItems().getProviderDni());
            redirectAttributes.addFlashAttribute("sampleOrderItems.providerSpecialty",
                    form.getSampleOrderItems().getProviderSpecialty());

            redirectAttributes.addFlashAttribute("sampleOrderItems.referringSiteId",
                    form.getSampleOrderItems().getReferringSiteId());
            redirectAttributes.addFlashAttribute("sampleOrderItems.referringSiteCode",
                    form.getSampleOrderItems().getReferringSiteCode());
            redirectAttributes.addFlashAttribute("sampleOrderItems.referringSiteName",
                    form.getSampleOrderItems().getReferringSiteName());

            redirectAttributes.addFlashAttribute("sampleOrderItems.referringSiteDepartmentId",
                    form.getSampleOrderItems().getReferringSiteDepartmentId());
            redirectAttributes.addFlashAttribute("sampleOrderItems.referringSiteDepartmentName",
                    form.getSampleOrderItems().getReferringSiteDepartmentName());
        }

        return ResponseEntity.ok(form);
    }

    private void enforceSelfOnlyProviderPolicy(HttpServletRequest request, SamplePatientEntryForm form,
            BindingResult result) {
        Provider linkedProvider = getLinkedProviderIfSelfOnlyApplies(request);
        if (linkedProvider == null) {
            return;
        }

        SampleOrderItem sampleOrder = form.getSampleOrderItems();
        String submittedProviderPersonId = StringUtils.trimToEmpty(sampleOrder.getProviderPersonId());
        String linkedProviderPersonId = linkedProvider.getPerson() == null ? ""
                : StringUtils.trimToEmpty(linkedProvider.getPerson().getId());

        if (!linkedProviderPersonId.equals(submittedProviderPersonId)) {
            result.reject("errors.provider.self.only", "Provider selection is restricted to the linked account.");
            return;
        }

        applyLinkedProviderToOrderItem(sampleOrder, linkedProvider, true);
    }

    private void applySelfOnlyProviderPolicyToForm(HttpServletRequest request, SampleOrderItem sampleOrderItem) {
        Provider linkedProvider = getLinkedProviderIfSelfOnlyApplies(request);
        if (linkedProvider == null) {
            return;
        }
        applyLinkedProviderToOrderItem(sampleOrderItem, linkedProvider, true);
    }

    private Provider getLinkedProviderIfSelfOnlyApplies(HttpServletRequest request) {
        if (!isSelfOnlyProviderPolicyEnabled()) {
            return null;
        }

        String sysUserId = getSysUserId(request);
        if (isProviderPolicyOverrideUser(sysUserId)) {
            return null;
        }

        SystemUser currentUser = systemUserService.get(sysUserId);
        if (currentUser == null || GenericValidator.isBlankOrNull(currentUser.getLinkedProviderPersonId())) {
            return null;
        }

        Person person = personService.get(currentUser.getLinkedProviderPersonId());
        if (person == null) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "getLinkedProviderIfSelfOnlyApplies",
                    "Linked provider person not found for userId=" + sysUserId);
            return null;
        }

        Provider provider = providerService.getProviderByPerson(person);
        if (provider == null || provider.getPerson() == null) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "getLinkedProviderIfSelfOnlyApplies",
                    "No provider found for linked personId=" + person.getId() + ", userId=" + sysUserId);
            return null;
        }

        return provider;
    }

    private boolean isSelfOnlyProviderPolicyEnabled() {
        String policy = ConfigurationProperties.getInstance().getPropertyValue(PROP_PROVIDER_SELECTION_POLICY);
        return PROVIDER_SELECTION_POLICY_SELF_ONLY.equalsIgnoreCase(StringUtils.trimToEmpty(policy));
    }

    private boolean isProviderPolicyOverrideUser(String systemUserId) {
        if (GenericValidator.isBlankOrNull(systemUserId)) {
            return false;
        }

        String configuredRoles = ConfigurationProperties.getInstance()
                .getPropertyValue(PROP_PROVIDER_SELECTION_OVERRIDE_ROLES);
        String rolesToCheck = StringUtils.isBlank(configuredRoles) ? "Global Administrator,Admin" : configuredRoles;

        return Arrays.stream(rolesToCheck.split(",")).map(String::trim).filter(StringUtils::isNotBlank)
                .anyMatch(roleName -> userRoleService.userInRole(systemUserId, roleName));
    }

    private void applyLinkedProviderToOrderItem(SampleOrderItem sampleOrder, Provider linkedProvider, boolean locked) {
        Person person = linkedProvider.getPerson();
        sampleOrder.setProviderSelectionLocked(locked);
        sampleOrder.setLinkedProviderPersonId(person == null ? null : person.getId());
        sampleOrder.setProviderId(linkedProvider.getId());
        sampleOrder.setProviderPersonId(person == null ? null : person.getId());
        sampleOrder.setProviderFirstName(person == null ? null : person.getFirstName());
        sampleOrder.setProviderLastName(person == null ? null : person.getLastName());
        sampleOrder.setProviderWorkPhone(person == null ? null : person.getWorkPhone());
        sampleOrder.setProviderEmail(person == null ? null : person.getEmail());
        sampleOrder.setProviderFax(person == null ? null : person.getFax());
        sampleOrder.setProviderCmp(linkedProvider.getNpi());
        sampleOrder.setProviderRne(linkedProvider.getExternalId());
        sampleOrder.setProviderDni(linkedProvider.getDni());
        sampleOrder.setProviderSpecialty(linkedProvider.getSpecialty());
    }

    private ResponseEntity<SamplePatientEntryForm> buildErrorResponse(SamplePatientEntryForm form, BindingResult result,
            HttpStatus status) {
        return buildErrorResponse(form, result, status, null);
    }

    private ResponseEntity<SamplePatientEntryForm> buildErrorResponse(SamplePatientEntryForm form, BindingResult result,
            HttpStatus status, String fallbackDetail) {
        String errorSummary = summarizeErrors(result, fallbackDetail);
        if (!GenericValidator.isBlankOrNull(errorSummary)) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "samplePatientEntrySave",
                    "Validation/persistence failed with status " + status.value() + ": " + errorSummary);
        }
        return ResponseEntity.status(status).header(ERROR_MESSAGE_HEADER, errorSummary).body(form);
    }

    private String summarizeErrors(BindingResult result, String fallbackDetail) {
        StringBuilder errorMessage = new StringBuilder();
        for (ObjectError error : result.getGlobalErrors()) {
            String resolved = MessageUtil.getMessageOrDefault(error.getCode(), error.getArguments(),
                    error.getDefaultMessage());
            String defaultMessage = StringUtils.trimToNull(error.getDefaultMessage());
            if (!GenericValidator.isBlankOrNull(resolved)) {
                if (errorMessage.length() > 0) {
                    errorMessage.append(" | ");
                }
                errorMessage.append(resolved.trim());
                if (defaultMessage != null && !StringUtils.equalsIgnoreCase(defaultMessage, resolved)) {
                    errorMessage.append(": ").append(defaultMessage);
                }
            } else if (defaultMessage != null) {
                if (errorMessage.length() > 0) {
                    errorMessage.append(" | ");
                }
                errorMessage.append(defaultMessage);
            }
        }
        for (FieldError error : result.getFieldErrors()) {
            String resolved = MessageUtil.getMessageOrDefault(error.getCode(), error.getArguments(),
                    error.getDefaultMessage());
            String defaultMessage = StringUtils.trimToNull(error.getDefaultMessage());
            if (!GenericValidator.isBlankOrNull(resolved)) {
                if (errorMessage.length() > 0) {
                    errorMessage.append(" | ");
                }
                errorMessage.append(error.getField()).append(": ").append(resolved.trim());
                if (defaultMessage != null && !StringUtils.equalsIgnoreCase(defaultMessage, resolved)) {
                    errorMessage.append(": ").append(defaultMessage);
                }
            } else if (defaultMessage != null) {
                if (errorMessage.length() > 0) {
                    errorMessage.append(" | ");
                }
                errorMessage.append(error.getField()).append(": ").append(defaultMessage);
            }
        }

        if (errorMessage.length() == 0) {
            String trimmedFallbackDetail = StringUtils.trimToNull(fallbackDetail);
            if (trimmedFallbackDetail != null) {
                errorMessage.append(trimmedFallbackDetail);
            }
        }

        String summary = errorMessage.toString().trim();
        if (summary.length() > 900) {
            return summary.substring(0, 900);
        }
        return summary;
    }

    private String buildIllegalArgumentExceptionDetail(IllegalArgumentException exception) {
        if (exception == null) {
            return null;
        }

        String baseMessage = StringUtils.trimToNull(exception.getMessage());
        StackTraceElement[] stackTrace = exception.getStackTrace();
        if (stackTrace == null || stackTrace.length == 0) {
            return baseMessage;
        }

        StackTraceElement source = null;
        for (StackTraceElement element : stackTrace) {
            if (element == null) {
                continue;
            }
            String className = StringUtils.defaultString(element.getClassName());
            if (!className.startsWith("java.") && !className.startsWith("jdk.")) {
                source = element;
                break;
            }
        }

        if (source == null) {
            source = stackTrace[0];
        }

        String sourceInfo = source.getClassName() + "." + source.getMethodName() + ":" + source.getLineNumber();
        if (baseMessage == null) {
            return sourceInfo;
        }
        return baseMessage + " @ " + sourceInfo;
    }

    private void setupForm(SamplePatientEntryForm form, HttpServletRequest request, String externalOrderNumber)
            throws LIMSRuntimeException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        SampleOrderService sampleOrderService = new SampleOrderService();
        form.setSampleOrderItems(sampleOrderService.getSampleOrderItem());
        if (requestFhirUuid != null
                && requestFhirUuid.toUpperCase().startsWith(ResourceType.PRACTITIONER.toString().toUpperCase())) {
            Reference providerReference = new Reference(requestFhirUuid);
            Provider provider = providerService
                    .getProviderByFhirId(UUID.fromString(providerReference.getReferenceElement().getIdPart()));
            if (provider != null) {
                form.getSampleOrderItems().setProviderPersonId(provider.getPerson().getId());
            }
        }
        form.getSampleOrderItems().setExternalOrderNumber(externalOrderNumber);
        if (StringUtils.isNotBlank(externalOrderNumber)) {
            ElectronicOrder eOrder = electronicOrderService.getElectronicOrdersByExternalId(externalOrderNumber).get(0);
            if (eOrder != null) {
                form.getSampleOrderItems().setPriority(eOrder.getPriority());
                Task task = fhirUtil.getFhirParser().parseResource(Task.class, eOrder.getData());
                if (!task.getLocation().isEmpty()) {
                    Organization organization = organizationService
                            .getOrganizationByFhirId(task.getLocation().getReferenceElement().getIdPart());
                    if (organization != null) {
                        form.getSampleOrderItems().setReferringSiteName(organization.getOrganizationName());
                        form.getSampleOrderItems().setReferringSiteId(organization.getId());
                    }
                }
                if (!task.getOwner().isEmpty()) {
                    if (StringUtils.isBlank(form.getSampleOrderItems().getProviderPersonId())) {
                        Reference providerReference = task.getOwner();
                        Provider provider = providerService.getProviderByFhirId(
                                UUID.fromString(providerReference.getReferenceElement().getIdPart()));
                        if (provider != null) {
                            form.getSampleOrderItems().setProviderPersonId(provider.getPerson().getId());
                        }
                    }
                }
            }
        }
        form.setPatientProperties(new PatientManagementInfo());
        form.setPatientSearch(new PatientSearch());
        form.setSampleTypes(userService.getUserSampleTypes(getSysUserId(request), Constants.ROLE_RECEPTION));
        form.setTestSectionList(DisplayListService.getInstance().getList(ListType.TEST_SECTION_ACTIVE));
        form.setCurrentDate(DateUtil.getCurrentDateAsText());
        form.setRejectReasonList(DisplayListService.getInstance().getList(ListType.REJECTION_REASONS));

        setupReferralOption(form);
        // for (Object program : form.getSampleOrderItems().getProgramList()) {
        // LogEvent.logInfo(this.getClass().getSimpleName(), "method unkown",
        // ((IdValuePair)
        // program).getValue());
        // }

        addProjectList(form);
        addBillingLabel();

        if (FormFields.getInstance().useField(FormFields.Field.InitialSampleCondition)) {
            form.setInitialSampleConditionList(
                    DisplayListService.getInstance().getList(ListType.INITIAL_SAMPLE_CONDITION));
        }
        if (FormFields.getInstance().useField(FormFields.Field.SampleNature)) {
            form.setSampleNatureList(DisplayListService.getInstance().getList(ListType.SAMPLE_NATURE));
        }
    }

    private void setContactTracingInfo(SamplePatientUpdateData updateData, SampleOrderItem sampleOrder) {
        SampleAdditionalField field;
        if (!GenericValidator.isBlankOrNull(sampleOrder.getContactTracingIndexName())) {
            field = new SampleAdditionalField();
            field.setFieldName(AdditionalFieldName.CONTACT_TRACING_INDEX_NAME);
            field.setFieldValue(sampleOrder.getContactTracingIndexName());
            updateData.addSampleField(field);
        }
        if (!GenericValidator.isBlankOrNull(sampleOrder.getContactTracingIndexRecordNumber())) {
            field = new SampleAdditionalField();
            field.setFieldName(AdditionalFieldName.CONTACT_TRACING_INDEX_RECORD_NUMBER);
            field.setFieldValue(sampleOrder.getContactTracingIndexRecordNumber());
            updateData.addSampleField(field);
        }
    }

    private void testAndInitializePatientForSaving(HttpServletRequest request, PatientManagementInfo patientInfo,
            IPatientUpdate patientUpdate, SamplePatientUpdateData updateData)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

        patientUpdate.setPatientUpdateStatus(patientInfo);
        updateData.setSavePatient(patientUpdate.getPatientUpdateStatus() != PatientUpdateStatus.NO_ACTION);

        if (updateData.isSavePatient()) {
            updateData.setPatientErrors(patientUpdate.preparePatientData(request, patientInfo));
        } else {
            updateData.setPatientErrors(new BaseErrors());
        }
    }

    @Override
    protected String findLocalForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "samplePatientEntryDefinition";
        } else if (FWD_FAIL.equals(forward)) {
            return "homePageDefinition";
        } else if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/SamplePatientEntry";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "samplePatientEntryDefinition";
        } else {
            return "PageNotFound";
        }
    }
}
