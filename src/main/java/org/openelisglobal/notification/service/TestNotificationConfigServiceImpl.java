package org.openelisglobal.notification.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.dao.BaseDAO;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.service.AuditableBaseObjectServiceImpl;
import org.openelisglobal.notification.dao.TestNotificationConfigDAO;
import org.openelisglobal.notification.valueholder.NotificationConfigOption;
import org.openelisglobal.notification.valueholder.NotificationConfigOption.NotificationMethod;
import org.openelisglobal.notification.valueholder.NotificationConfigOption.NotificationNature;
import org.openelisglobal.notification.valueholder.NotificationConfigOption.NotificationPersonType;
import org.openelisglobal.notification.valueholder.NotificationPayloadTemplate;
import org.openelisglobal.notification.valueholder.NotificationPayloadTemplate.NotificationPayloadType;
import org.openelisglobal.notification.valueholder.TestNotificationConfig;
import org.openelisglobal.test.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TestNotificationConfigServiceImpl extends AuditableBaseObjectServiceImpl<TestNotificationConfig, Integer>
        implements TestNotificationConfigService {

    @Autowired
    private TestNotificationConfigDAO baseDAO;
    @Autowired
    private TestService testService;
    @Autowired
    private NotificationPayloadTemplateService notificationPayloadTemplateService;

    public TestNotificationConfigServiceImpl() {
        super(TestNotificationConfig.class);
        this.auditTrailLog = false;
    }

    @Override
    protected BaseDAO<TestNotificationConfig, Integer> getBaseObjectDAO() {
        return baseDAO;
    }

    @Override
    public Optional<TestNotificationConfig> getTestNotificationConfigForTestId(String testId) {
        return baseDAO.getTestNotificationConfigForTestId(testId);
    }

    @Override
    @Transactional
    public TestNotificationConfig saveTestNotificationConfigActiveStatuses(
            TestNotificationConfig targetTestNotificationConfig, String sysUserId) {
        TestNotificationConfig oldConfig;
        if (targetTestNotificationConfig.getId() != null) {
            oldConfig = get(targetTestNotificationConfig.getId());
        } else {
            oldConfig = new TestNotificationConfig();
            oldConfig.setTest(testService.get(targetTestNotificationConfig.getTest().getId()));
            Integer templateId = targetTestNotificationConfig.getDefaultPayloadTemplate().getId();
            if (templateId != null) {
                oldConfig.setDefaultPayloadTemplate(notificationPayloadTemplateService
                        .get(targetTestNotificationConfig.getDefaultPayloadTemplate().getId()));
            }
        }
        syncNotificationOptions(oldConfig, targetTestNotificationConfig, sysUserId);
        oldConfig.setSysUserId(sysUserId);
        return save(oldConfig);
    }

    @Override
    @Transactional
    public void saveTestNotificationConfigsActiveStatuses(List<TestNotificationConfig> targetTestNotificationConfigs,
            String sysUserId) {
        for (TestNotificationConfig targetTestNotificationConfig : targetTestNotificationConfigs) {
            saveTestNotificationConfigActiveStatuses(targetTestNotificationConfig, sysUserId);
        }
    }

    @Override
    @Transactional
    public void removeEmptyPayloadTemplates(TestNotificationConfig newTestNotificationConfig, String sysUserId) {
        TestNotificationConfig oldConfig;
        if (newTestNotificationConfig.getId() != null) {
            oldConfig = get(newTestNotificationConfig.getId());
            if (testDefaultEmpty(newTestNotificationConfig.getDefaultPayloadTemplate())) {
                oldConfig.setDefaultPayloadTemplate(null);
                oldConfig.setSysUserId(sysUserId);
            }
            for (NotificationConfigOption newOption : newTestNotificationConfig.getOptions()) {
                if (testDefaultEmpty(resolvePayloadTemplate(newOption))) {
                    NotificationConfigOption oldOption = findMatchingOption(oldConfig, newOption);
                    oldOption.setPayloadTemplate(null);
                    oldOption.setSysUserId(sysUserId);
                }
            }
            save(oldConfig);
        }
    }

    private boolean testDefaultEmpty(NotificationPayloadTemplate notificationPayloadTemplate) {
        if (notificationPayloadTemplate == null
                || GenericValidator.isBlankOrNull(notificationPayloadTemplate.getMessageTemplate())) {
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public void updatePayloadTemplatesMessageAndSubject(TestNotificationConfig newTestNotificationConfig,
            String sysUserId) {
        TestNotificationConfig oldConfig;

        if (newTestNotificationConfig.getId() != null) {
            oldConfig = get(newTestNotificationConfig.getId());

            // copy values from new to old
            NotificationPayloadTemplate newPayloadTemplate = newTestNotificationConfig.getDefaultPayloadTemplate();
            NotificationPayloadTemplate oldPayloadTemplate = oldConfig.getDefaultPayloadTemplate();
            if (oldPayloadTemplate == null) {
                oldPayloadTemplate = newPayloadTemplate;
                oldConfig.setDefaultPayloadTemplate(oldPayloadTemplate);
            } else {
                oldPayloadTemplate.setSubjectTemplate(newPayloadTemplate.getSubjectTemplate());
                oldPayloadTemplate.setMessageTemplate(newPayloadTemplate.getMessageTemplate());
            }
            oldPayloadTemplate.setSysUserId(sysUserId);

            for (NotificationConfigOption newOption : newTestNotificationConfig.getOptions()) {
                NotificationConfigOption oldOption = findMatchingOption(oldConfig, newOption);

                newPayloadTemplate = resolvePayloadTemplate(newOption);
                oldPayloadTemplate = oldOption.getPayloadTemplate();
                if (newPayloadTemplate == null
                        || (GenericValidator.isBlankOrNull(newPayloadTemplate.getSubjectTemplate())
                                && GenericValidator.isBlankOrNull(newPayloadTemplate.getMessageTemplate()))) {
                    oldOption.setPayloadTemplate(null);
                    continue;
                }
                if (oldPayloadTemplate == null) {
                    oldPayloadTemplate = new NotificationPayloadTemplate();
                    oldPayloadTemplate.setType(NotificationPayloadType.TEST_RESULT);
                } else {
                    if (oldPayloadTemplate.getType() == null) {
                        oldPayloadTemplate.setType(NotificationPayloadType.TEST_RESULT);
                    }
                    oldPayloadTemplate.setSubjectTemplate(newPayloadTemplate.getSubjectTemplate());
                    oldPayloadTemplate.setMessageTemplate(newPayloadTemplate.getMessageTemplate());
                }
                oldPayloadTemplate.setSubjectTemplate(newPayloadTemplate.getSubjectTemplate());
                oldPayloadTemplate.setMessageTemplate(newPayloadTemplate.getMessageTemplate());
                oldPayloadTemplate.setSysUserId(sysUserId);
                oldPayloadTemplate = notificationPayloadTemplateService.save(oldPayloadTemplate);
                oldOption.setPayloadTemplate(oldPayloadTemplate);
            }
        } else {
            oldConfig = newTestNotificationConfig;
            oldConfig.setTest(testService.get(newTestNotificationConfig.getTestId()));
        }
        save(oldConfig);
    }

    private void syncNotificationOptions(TestNotificationConfig oldConfig, TestNotificationConfig newConfig, String sysUserId) {
        if (oldConfig.getOptions() == null) {
            oldConfig.setOptions(new ArrayList<>());
        }
        if (newConfig.getOptions() == null) {
            return;
        }
        removeMissingInternalProfileOptions(oldConfig, newConfig);
        for (NotificationConfigOption newOption : newConfig.getOptions()) {
            NotificationConfigOption oldOption = findMatchingOption(oldConfig, newOption);
            oldOption.setActive(newOption.getActive());
            oldOption.setNotificationNature(newOption.getNotificationNature());
            oldOption.setProfessionalProfileCode(newOption.getProfessionalProfileCode());
            oldOption.setAdditionalContacts(newOption.getAdditionalContacts());
            NotificationPayloadTemplate incomingTemplate = resolvePayloadTemplate(newOption);
            if (incomingTemplate == null) {
                oldOption.setPayloadTemplate(null);
            } else {
                NotificationPayloadTemplate targetTemplate = oldOption.getPayloadTemplate();
                if (targetTemplate == null) {
                    targetTemplate = new NotificationPayloadTemplate();
                    targetTemplate.setType(NotificationPayloadType.TEST_RESULT);
                } else if (targetTemplate.getType() == null) {
                    targetTemplate.setType(NotificationPayloadType.TEST_RESULT);
                }
                targetTemplate.setSubjectTemplate(incomingTemplate.getSubjectTemplate());
                targetTemplate.setMessageTemplate(incomingTemplate.getMessageTemplate());
                targetTemplate.setSysUserId(sysUserId);
                targetTemplate = notificationPayloadTemplateService.save(targetTemplate);
                oldOption.setPayloadTemplate(targetTemplate);
            }
            oldOption.setSysUserId(sysUserId);
        }
    }

    private void removeMissingInternalProfileOptions(TestNotificationConfig oldConfig, TestNotificationConfig newConfig) {
        List<NotificationConfigOption> newInternalProfileOptions = newConfig.getOptions().stream()
                .filter(this::isInternalProfileEmailOption).toList();
        Iterator<NotificationConfigOption> iterator = oldConfig.getOptions().iterator();
        while (iterator.hasNext()) {
            NotificationConfigOption oldOption = iterator.next();
            if (!isInternalProfileEmailOption(oldOption)) {
                continue;
            }
            boolean stillPresent = newInternalProfileOptions.stream()
                    .anyMatch(newOption -> internalProfileOptionMatches(oldOption, newOption));
            if (!stillPresent) {
                iterator.remove();
            }
        }
    }

    private NotificationConfigOption findMatchingOption(TestNotificationConfig oldConfig, NotificationConfigOption newOption) {
        if (isInternalProfileEmailOption(newOption)) {
            return oldConfig.getOptions().stream()
                    .filter(opt -> internalProfileOptionMatches(opt, newOption))
                    .findFirst()
                    .orElseGet(() -> {
                        NotificationConfigOption created = new NotificationConfigOption(NotificationMethod.EMAIL,
                                NotificationPersonType.INTERNAL_PROFILE,
                                newOption.getNotificationNature() == null ? NotificationNature.RESULT_PENDING_VALIDATION
                                        : newOption.getNotificationNature(),
                                false);
                        oldConfig.getOptions().add(created);
                        return created;
                    });
        }
        return oldConfig.getOptionFor(newOption.getNotificationNature(), newOption.getNotificationMethod(),
                newOption.getNotificationPersonType());
    }

    private NotificationPayloadTemplate resolvePayloadTemplate(NotificationConfigOption option) {
        NotificationPayloadTemplate template = option.getPayloadTemplate();
        if (template != null) {
            return template;
        }
        String subject = option.getSubjectTemplate();
        String message = option.getMessageTemplate();
        if (GenericValidator.isBlankOrNull(subject) && GenericValidator.isBlankOrNull(message)) {
            return null;
        }
        NotificationPayloadTemplate created = new NotificationPayloadTemplate();
        created.setType(NotificationPayloadType.TEST_RESULT);
        created.setSubjectTemplate(subject);
        created.setMessageTemplate(message);
        return created;
    }

    private boolean isInternalProfileEmailOption(NotificationConfigOption option) {
        return option.getNotificationMethod() == NotificationMethod.EMAIL
                && option.getNotificationPersonType() == NotificationPersonType.INTERNAL_PROFILE;
    }

    private boolean internalProfileOptionMatches(NotificationConfigOption left, NotificationConfigOption right) {
        if (left.getId() != null && right.getId() != null) {
            return left.getId().equals(right.getId());
        }
        return left.getNotificationNature() == right.getNotificationNature()
                && StringUtils.equalsIgnoreCase(StringUtils.trimToEmpty(left.getProfessionalProfileCode()),
                        StringUtils.trimToEmpty(right.getProfessionalProfileCode()));
    }

    @Override
    public List<TestNotificationConfig> getTestNotificationConfigsForTestId(List<String> testIds) {
        return baseDAO.getTestNotificationConfigsForTestIds(testIds);
    }

    @Override
    public TestNotificationConfig getForConfigOption(Integer configOptionId) {
        return baseDAO.getForConfigOption(configOptionId);
    }

    @Override
    @Transactional
    public void saveStatusAndMessages(TestNotificationConfig config, String sysUserId) {
        TestNotificationConfig savedConfig = saveTestNotificationConfigActiveStatuses(config, sysUserId);
        config.setId(savedConfig.getId());
        updatePayloadTemplatesMessageAndSubject(config, sysUserId);
        TestNotificationConfig persisted = get(savedConfig.getId());
        LogEvent.logInfo(this.getClass().getSimpleName(), "saveStatusAndMessages",
                "saved testId=" + persisted.getTestId() + " internalRules=" + persisted.getInternalProfileEmailNotifications()
                        .stream()
                        .map(opt -> "nature=" + opt.getNotificationNature() + ",profile=" + opt.getProfessionalProfileCode()
                                + ",active=" + opt.getActive() + ",subject=" + opt.getSubjectTemplate() + ",message="
                                + opt.getMessageTemplate())
                        .reduce((left, right) -> left + " || " + right).orElse("<none>"));
    }
}
