package org.openelisglobal.notification.service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.validator.GenericValidator;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.dictionary.service.DictionaryService;
import org.openelisglobal.dictionary.valueholder.Dictionary;
import org.openelisglobal.notification.service.sender.ClientNotificationSender;
import org.openelisglobal.notification.valueholder.AnalysisNotificationConfig;
import org.openelisglobal.notification.valueholder.EmailNotification;
import org.openelisglobal.notification.valueholder.NotificationConfig;
import org.openelisglobal.notification.valueholder.NotificationConfigOption;
import org.openelisglobal.notification.valueholder.NotificationConfigOption.NotificationMethod;
import org.openelisglobal.notification.valueholder.NotificationConfigOption.NotificationNature;
import org.openelisglobal.notification.valueholder.NotificationConfigOption.NotificationPersonType;
import org.openelisglobal.notification.valueholder.NotificationPayloadTemplate;
import org.openelisglobal.notification.valueholder.NotificationPayloadTemplate.NotificationPayloadType;
import org.openelisglobal.notification.valueholder.PatientResultsViewNotificationPayload;
import org.openelisglobal.notification.valueholder.RemoteNotification;
import org.openelisglobal.notification.valueholder.SMSNotification;
import org.openelisglobal.notification.valueholder.TestNotificationConfig;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.result.valueholder.Result;
import org.openelisglobal.systemuser.service.ProfessionalProfileRecipientService;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.openelisglobal.samplehuman.service.SampleHumanService;
import org.openelisglobal.testresultsview.service.ClientResultsViewInfoService;
import org.openelisglobal.testresultsview.valueholder.ClientResultsViewBean;
import org.openelisglobal.typeoftestresult.service.TypeOfTestResultServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestNotificationServiceImpl implements TestNotificationService {

    @Autowired
    private ClientResultsViewInfoService clientResultsViewInfoService;
    @Autowired
    private NotificationPayloadTemplateService notificationPayloadTemplateService;
    @Autowired
    private SampleHumanService sampleHumanService;
    @Autowired
    private DictionaryService dictionaryService;
    @Autowired
    private TestNotificationConfigService testNotificationConfigService;
    @Autowired
    private AnalysisNotificationConfigService analysisNotificationConfigService;
    @Autowired
    private SystemUserService systemUserService;
    @Autowired
    private ProviderService providerService;
    @Autowired
    private ProfessionalProfileRecipientService professionalProfileRecipientService;

    @Value("${org.openelisglobal.ozeki.active:false}")
    private Boolean ozekiActive;

    @SuppressWarnings("rawtypes")
    @Autowired
    private List<ClientNotificationSender> notificationSenders;

    @PostConstruct
    public void init() {
        ensureNotificationsPayloadTemplatesExist(NotificationPayloadType.TEST_RESULT);
    }

    private void ensureNotificationsPayloadTemplatesExist(NotificationPayloadType testResult) {
        if (notificationPayloadTemplateService.getSystemDefaultPayloadTemplateForType(testResult) == null) {
            createSystemDefaultNotificationPayloadTemplate(NotificationPayloadType.TEST_RESULT);
        }
    }

    private NotificationPayloadTemplate createSystemDefaultNotificationPayloadTemplate(NotificationPayloadType type) {
        NotificationPayloadTemplate template = new NotificationPayloadTemplate();
        template.setMessageTemplate(
                "[testName] testing results have been finalized. If you are not awaiting test results"
                        + " please call XXXXXXXXXXX and delete this notice.\n\n"
                        + "[patientFirstName] [patientLastNameInitial]: [testResult]");
        template.setSubjectTemplate("[testName] Testing Results");
        template.setSysUserId("1");
        template.setType(type);
        notificationPayloadTemplateService.save(template);
        return template;
    }

    private NotificationPayloadTemplate createPendingValidationDefaultTemplate() {
        NotificationPayloadTemplate template = new NotificationPayloadTemplate();
        template.setMessageTemplate(
                "[testName] results have been entered and are pending validation.\n\n"
                        + "[patientFirstName] [patientLastNameInitial]: [testResult]");
        template.setSubjectTemplate("[testName] Pending Validation");
        return template;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void sendNotification(RemoteNotification clientNotification) {
        for (ClientNotificationSender notificationSender : notificationSenders) {
            if (clientNotification.getClass().isAssignableFrom(notificationSender.forClass())) {
                notificationSender.send(clientNotification);
            }
        }
    }

    @Override
    @Async
    @Transactional(readOnly = true)
    public void createAndSendNotificationsToConfiguredSources(NotificationNature nature, Result result) {
        Optional<AnalysisNotificationConfig> analysisNotificationConfig = analysisNotificationConfigService
                .getAnalysisNotificationConfigForAnalysisId(result.getAnalysis().getId());
        Optional<TestNotificationConfig> testNotificationConfig = testNotificationConfigService
                .getTestNotificationConfigForTestId(result.getAnalysis().getTest().getId());
        LogEvent.logInfo(this.getClass().getSimpleName(), "createAndSendNotificationsToConfiguredSources",
                "nature=" + nature + ", testId=" + result.getAnalysis().getTest().getId() + ", analysisId="
                        + result.getAnalysis().getId() + ", smtpEnabled=" + emailEnabledForSystem()
                        + ", hasAnalysisConfig=" + analysisNotificationConfig.isPresent() + ", hasTestConfig="
                        + testNotificationConfig.isPresent());
        if (analysisNotificationConfig.isEmpty() && testNotificationConfig.isEmpty()) {
            return;
        }

        switch (nature) {
        case RESULT_PENDING_VALIDATION:
        case RESULT_VALIDATION:
            createAndSendResultsNotificationsToConfiguredSources(nature, result, analysisNotificationConfig,
                    testNotificationConfig);
            break;
        default:
            break;
        }
    }

    private void createAndSendResultsNotificationsToConfiguredSources(NotificationNature nature, Result result,
            Optional<AnalysisNotificationConfig> analysisNotificationConfig,
            Optional<TestNotificationConfig> testNotificationConfig) {
        ClientResultsViewBean resultsViewInfo = new ClientResultsViewBean(result);
        resultsViewInfo.setSysUserId("1");
        resultsViewInfo = clientResultsViewInfoService.save(resultsViewInfo);

        String resultForDisplay = "";

        if (TypeOfTestResultServiceImpl.ResultType.isMultiSelectVariant(result.getResultType())) {
            // TODO
        } else if (TypeOfTestResultServiceImpl.ResultType.isDictionaryVariant(result.getResultType())) {
            Dictionary dictionary = dictionaryService.getDataForId(result.getValue());
            resultForDisplay = dictionary.getLocalizedName();

            if ("unknown".equals(resultForDisplay)) {
                resultForDisplay = GenericValidator.isBlankOrNull(dictionary.getLocalAbbreviation())
                        ? dictionary.getDictEntry()
                        : dictionary.getLocalAbbreviation();
            }
            // resultForDisplay =
            // dictionaryService.getDataForId(result.getValue()).getDictEntry();
        } else if (TypeOfTestResultServiceImpl.ResultType.isNumeric(result.getResultType())) {
            resultForDisplay = result.getValue();
        } else if (TypeOfTestResultServiceImpl.ResultType.isTextOnlyVariant(result.getResultType())) {
            resultForDisplay = result.getValue();
        }
        for (NotificationMethod methodType : NotificationMethod.values()) {
            if (systemEnabledForMethod(methodType)) {
                if (analysisNotificationConfig.isPresent()) {
                    createAndSendNotificationsConfiguredForTest(nature, methodType, analysisNotificationConfig.get(),
                            resultForDisplay, resultsViewInfo, false);
                } else if (testNotificationConfig.isPresent()) {
                    createAndSendNotificationsConfiguredForTest(nature, methodType, testNotificationConfig.get(),
                            resultForDisplay, resultsViewInfo, false);
                }
                if (testNotificationConfig.isPresent()) {
                    createAndSendNotificationsConfiguredForTest(nature, methodType, testNotificationConfig.get(),
                            resultForDisplay, resultsViewInfo, true);
                }
            }
        }
    }

    private void createAndSendNotificationsConfiguredForTest(NotificationNature nature, NotificationMethod methodType,
            NotificationConfig<?> notificationConfig, String resultForDisplay, ClientResultsViewBean resultsViewInfo,
            boolean internalProfileOnly) {
        notificationConfig.getOptions().stream()
                .filter(NotificationConfigOption::getActive)
                .filter(option -> option.getNotificationNature() == nature)
                .filter(option -> option.getNotificationMethod() == methodType)
                .filter(option -> internalProfileOnly
                        ? NotificationPersonType.INTERNAL_PROFILE.equals(option.getNotificationPersonType())
                        : !NotificationPersonType.INTERNAL_PROFILE.equals(option.getNotificationPersonType()))
                .forEach(option -> createAndSendNotificationToPerson(nature, methodType,
                        option.getNotificationPersonType(), option, resultForDisplay, resultsViewInfo));
    }

    private void createAndSendNotificationToPerson(NotificationNature nature, NotificationMethod methodType,
            NotificationPersonType personType, NotificationConfigOption option, String resultForDisplay,
            ClientResultsViewBean resultsViewInfo) {
        Person testPerson = sampleHumanService
                .getPatientForSample(resultsViewInfo.getResult().getAnalysis().getSampleItem().getSample()).getPerson();
        Person receiverPerson = null;
        if (NotificationPersonType.PATIENT.equals(personType)) {
            receiverPerson = testPerson;
        } else if (NotificationPersonType.PROVIDER.equals(personType)) {
            receiverPerson = sampleHumanService
                    .getProviderForSample(resultsViewInfo.getResult().getAnalysis().getSampleItem().getSample())
                    .getPerson();
        }
        if (NotificationMethod.EMAIL.equals(methodType) && canSendEmail(receiverPerson)) {
            createAndSendResultsNotificationEmail(testPerson, receiverPerson, option, resultForDisplay,
                    resultsViewInfo);
        } else if (NotificationMethod.SMS.equals(methodType) && canSendSMS(receiverPerson)) {
            createAndSendResultsNotificationSMS(testPerson, receiverPerson, option, resultForDisplay, resultsViewInfo);
        } else if (NotificationPersonType.INTERNAL_PROFILE.equals(personType) && NotificationMethod.EMAIL.equals(methodType)) {
            createAndSendResultsNotificationEmailsToInternalProfile(testPerson, option, resultForDisplay, resultsViewInfo);
        }
    }

    private void createAndSendResultsNotificationEmailsToInternalProfile(Person testPerson,
            NotificationConfigOption option, String resultForDisplay, ClientResultsViewBean resultsViewInfo) {
        String profileCode = professionalProfileRecipientService
                .normalizeProfessionalProfileCode(option.getProfessionalProfileCode());
        if (profileCode.isBlank()) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "createAndSendResultsNotificationEmailsToInternalProfile",
                    "professional profile code is blank for internal profile notification");
            return;
        }

        Set<String> explicitlySelectedUserIds = option.getSelectedUserIds().stream().map(StringUtils::trim)
                .filter(StringUtils::isNotBlank).collect(Collectors.toSet());

        Map<String, Provider> providersByPersonId = providerService.getAllActiveProviders().stream()
                .filter(provider -> provider.getPerson() != null && StringUtils.isNotBlank(provider.getPerson().getId()))
                .collect(Collectors.toMap(provider -> provider.getPerson().getId(), provider -> provider, (left, right) -> left));

        Set<String> deliveredEmails = professionalProfileRecipientService
                .getEligibleUsersForProfessionalProfile(profileCode, true).stream()
                .filter(user -> explicitlySelectedUserIds.isEmpty() || explicitlySelectedUserIds.contains(user.getId()))
                .map(SystemUser::getLinkedProviderPersonId)
                .filter(StringUtils::isNotBlank)
                .map(providersByPersonId::get)
                .filter(provider -> provider != null && canSendEmail(provider.getPerson()))
                .map(Provider::getPerson)
                .map(Person::getEmail)
                .filter(StringUtils::isNotBlank)
                .map(StringUtils::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (deliveredEmails.isEmpty()) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "createAndSendResultsNotificationEmailsToInternalProfile",
                    "no active internal profile recipients found for profileCode=" + profileCode
                            + ", selectedUsers=" + explicitlySelectedUserIds.size());
        } else {
            LogEvent.logInfo(this.getClass().getSimpleName(), "createAndSendResultsNotificationEmailsToInternalProfile",
                    "sending internal profile notifications to " + deliveredEmails.size() + " recipient(s) for profileCode="
                            + profileCode + ", selectedUsers=" + explicitlySelectedUserIds.size());
        }

        for (String email : deliveredEmails) {
            Person receiverPerson = new Person();
            receiverPerson.setEmail(email);
            createAndSendResultsNotificationEmail(testPerson, receiverPerson, option, resultForDisplay, resultsViewInfo);
        }
    }

    private void createAndSendResultsNotificationSMS(Person testPerson, Person receiverPerson,
            NotificationConfigOption option, String resultForDisplay, ClientResultsViewBean resultsViewInfo) {
        try {
            SMSNotification smsNotification = new SMSNotification();
            String phoneNumber = "";
            for (char ch : receiverPerson.getPrimaryPhone().toCharArray()) {
                // 5
                if (Character.isDigit(ch)) {
                    phoneNumber = phoneNumber + ch;
                }
            }
            smsNotification.setReceiverPhoneNumber(phoneNumber);

            NotificationPayloadTemplate template = findTemplate(option);
            // TODO figure out where to store address and how to retrieve
            smsNotification.setPayload(new PatientResultsViewNotificationPayload(resultsViewInfo.getPassword(),
                    "someAddress", resultsViewInfo.getResult().getAnalysis().getTest().getName(), resultForDisplay,
                    testPerson.getFirstName(), testPerson.getLastName().substring(0, 1), template));

            sendNotification(smsNotification);
            // getSenderForNotification(smsNotification).send(smsNotification);
        } catch (RuntimeException e) {
            LogEvent.logError(this.getClass().getSimpleName(), "createAndSendResultsNotificationSMS",
                    "could not send sms notification");
            LogEvent.logError(e);
        }
    }

    private void createAndSendResultsNotificationEmail(Person testPerson, Person receiverPerson,
            NotificationConfigOption option, String resultForDisplay, ClientResultsViewBean resultsViewInfo) {
        try {
            EmailNotification emailNotification = new EmailNotification();
            emailNotification.setRecipientEmailAddress(receiverPerson.getEmail());
            emailNotification.setBccs(option.getAdditionalContacts());

            NotificationPayloadTemplate template = findTemplate(option);
            // TODO figure out where to store address and how to retrieve
            emailNotification.setPayload(new PatientResultsViewNotificationPayload(resultsViewInfo.getPassword(),
                    "someAddress", resultsViewInfo.getResult().getAnalysis().getTest().getName(), resultForDisplay,
                    testPerson.getFirstName(), testPerson.getLastName().substring(0, 1), template));

            sendNotification(emailNotification);
        } catch (RuntimeException e) {
            // TODO add redundancy mechanism in case can't reach SMTP server
            LogEvent.logError(this.getClass().getSimpleName(), "createAndSendResultsNotificationEmail",
                    "could not send email notification");
            LogEvent.logError(e);
        }
    }

    private NotificationPayloadTemplate findTemplate(NotificationConfigOption option) {
        if (option.getNotificationNature() == NotificationNature.RESULT_PENDING_VALIDATION) {
            if (option.getPayloadTemplate() != null) {
                return option.getPayloadTemplate();
            }
            return createPendingValidationDefaultTemplate();
        }

        NotificationPayloadTemplate template;
        TestNotificationConfig testNotificationConfig = testNotificationConfigService
                .getForConfigOption(option.getId());
        AnalysisNotificationConfig analyzerNotificationConfig = analysisNotificationConfigService
                .getForConfigOption(option.getId());
        // default to system default for type
        if (option.getPayloadTemplate() == null
                && (testNotificationConfig == null || testNotificationConfig.getDefaultPayloadTemplate() == null)
                && (analyzerNotificationConfig == null
                        || analyzerNotificationConfig.getDefaultPayloadTemplate() == null)) {
            template = notificationPayloadTemplateService
                    .getSystemDefaultPayloadTemplateForType(NotificationPayloadType.TEST_RESULT);
            // ... unless this option is for a test and it has a test default
        } else if (option.getPayloadTemplate() == null && (analyzerNotificationConfig == null
                || analyzerNotificationConfig.getDefaultPayloadTemplate() == null)) {
            template = testNotificationConfig.getDefaultPayloadTemplate();
            // ... unless this option is for an analysis and it has an analysis default
        } else if (option.getPayloadTemplate() == null) {
            template = analyzerNotificationConfig.getDefaultPayloadTemplate();
            // ...unless there is one configured for this option
        } else {
            template = option.getPayloadTemplate();
        }
        return template;
    }

    private boolean systemEnabledForMethod(NotificationMethod methodType) {
        switch (methodType) {
        case EMAIL:
            return emailEnabledForSystem();
        case SMS:
            return smsEnabledForSystem();
        default:
            return false;
        }
    }

    private boolean smsEnabledForSystem() {
        return ConfigurationProperties.getInstance().getPropertyValue(Property.PATIENT_RESULTS_BMP_SMS_ENABLED)
                .equals(Boolean.TRUE.toString())
                || ConfigurationProperties.getInstance().getPropertyValue(Property.PATIENT_RESULTS_SMPP_SMS_ENABLED)
                        .equals(Boolean.TRUE.toString())
                || ozekiActive;
    }

    private boolean emailEnabledForSystem() {
        return ConfigurationProperties.getInstance().getPropertyValue(Property.PATIENT_RESULTS_SMTP_ENABLED)
                .equals(Boolean.TRUE.toString());
    }

    private boolean canSendSMS(Person person) {
        boolean canSend = person != null && !GenericValidator.isBlankOrNull(person.getPrimaryPhone());
        if (!canSend) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "canSendSMS",
                    "can't send SMS to person as they have no phone on file");
        }
        return canSend;
    }

    private boolean canSendEmail(Person person) {
        boolean canSend = person != null && !GenericValidator.isBlankOrNull(person.getEmail());
        if (!canSend) {
            LogEvent.logWarn(this.getClass().getSimpleName(), "canSendEmail",
                    "can't send email to person as they have no email on file");
        }
        return canSend;
    }
}
