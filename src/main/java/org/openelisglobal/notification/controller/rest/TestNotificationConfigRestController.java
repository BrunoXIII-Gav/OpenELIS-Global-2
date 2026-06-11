package org.openelisglobal.notification.controller.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.notification.form.TestNotificationConfigForm;
import org.openelisglobal.notification.service.NotificationPayloadTemplateService;
import org.openelisglobal.notification.service.TestNotificationConfigService;
import org.openelisglobal.notification.valueholder.NotificationConfigOption;
import org.openelisglobal.notification.valueholder.NotificationPayloadTemplate.NotificationPayloadType;
import org.openelisglobal.notification.valueholder.TestNotificationConfig;
import org.openelisglobal.test.service.TestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest")
public class TestNotificationConfigRestController extends BaseController {

    private static final ObjectMapper DEBUG_OBJECT_MAPPER = new ObjectMapper();

    private static final String[] ALLOWED_FIELDS = new String[] { "config*", "editSystemDefaultPayloadTemplate",
            "systemDefaultPayloadTemplate*" };

    @Autowired
    private TestNotificationConfigService testNotificationConfigService;

    @Autowired
    private NotificationPayloadTemplateService payloadTemplateService;
    @Autowired
    private TestService testService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @GetMapping("/TestNotificationConfig")
    public TestNotificationConfigForm displayNotificationConfig(
            @RequestParam(name = "testId", required = false) String testId) {
        TestNotificationConfigForm form = new TestNotificationConfigForm();

        form.setFormName("TestNotificationConfigForm");
        form.setSystemDefaultPayloadTemplate(
                payloadTemplateService.getSystemDefaultPayloadTemplateForType(NotificationPayloadType.TEST_RESULT));
        form.setConfig(testNotificationConfigService.getTestNotificationConfigForTestId(testId)
                .orElse(new TestNotificationConfig()));
        if (form.getConfig().getTest() == null || GenericValidator.isBlankOrNull(form.getConfig().getTest().getId())) {
            form.getConfig().setTest(testService.get(testId));
        }
        LogEvent.logInfo(this.getClass().getSimpleName(), "displayNotificationConfig",
                "GET testId=" + testId + " internalRules=" + summarizeInternalRules(form.getConfig()));
        LogEvent.logInfo(this.getClass().getSimpleName(), "displayNotificationConfig",
                "GET rawConfig=" + safeJson(form.getConfig()));
        // return findForward(FWD_SUCCESS, form);
        return form;
    }

    @GetMapping(value = "/TestNotificationConfig/raw/list", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<TestNotificationConfig> getNotificationConfigs(
            @RequestParam(name = "testIds", required = false) List<String> testIds) {
        List<TestNotificationConfig> configs = testNotificationConfigService
                .getTestNotificationConfigsForTestId(testIds);
        return configs;
    }

    @PostMapping("/TestNotificationConfig")
    public TestNotificationConfigForm updateNotificationConfig(@RequestBody @Valid TestNotificationConfigForm form,
            BindingResult result) {
        if (result.hasErrors()) {
            // saveErrors(result);
            // return displayNotificationConfig(form.getConfig().getTest().getId());
            throw new RuntimeException("Validation errors occurred");
        }
        String sysUserId = this.getSysUserId(request);
        LogEvent.logInfo(this.getClass().getSimpleName(), "updateNotificationConfig",
                "POST testId=" + form.getConfig().getTestId() + " internalRules=" + summarizeInternalRules(form.getConfig()));
        LogEvent.logInfo(this.getClass().getSimpleName(), "updateNotificationConfig",
                "POST rawConfig=" + safeJson(form.getConfig()));
        if (form.getConfig().getDefaultPayloadTemplate() == null) {
            form.getConfig().setDefaultPayloadTemplate(form.getSystemDefaultPayloadTemplate());
        }
        testNotificationConfigService.saveStatusAndMessages(form.getConfig(), sysUserId);
        if (form.getEditSystemDefaultPayloadTemplate()) {
            payloadTemplateService.updatePayloadTemplateMessagesAndSubject(form.getSystemDefaultPayloadTemplate(),
                    sysUserId);
        }
        testNotificationConfigService.removeEmptyPayloadTemplates(form.getConfig(), sysUserId);
        // redirectAttributes.addFlashAttribute(FWD_SUCCESS, true);
        // return findForward(FWD_SUCCESS_INSERT, form);
        return form;
    }

    private String summarizeInternalRules(TestNotificationConfig config) {
        return config.getInternalProfileEmailNotifications().stream()
                .map(this::summarizeRule)
                .collect(Collectors.joining(" || "));
    }

    private String summarizeRule(NotificationConfigOption option) {
        return "nature=" + option.getNotificationNature() + ",profile=" + option.getProfessionalProfileCode() + ",active="
                + option.getActive() + ",subject=" + option.getSubjectTemplate() + ",message=" + option.getMessageTemplate();
    }

    private String safeJson(Object value) {
        try {
            return DEBUG_OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "<json-error:" + e.getMessage() + ">";
        }
    }

    @Override
    protected String findLocalForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "testNotificationConfigDefinition";
        } else if (FWD_FAIL.equals(forward)) {
            return "redirect:/TestNotificationConfigMenu";
        } else if (FWD_SUCCESS_INSERT.equals(forward)) {
            return "redirect:/TestNotificationConfigMenu";
        } else if (FWD_FAIL_INSERT.equals(forward)) {
            return "testNotificationConfigDefinition";
        } else {
            return "PageNotFound";
        }
    }

    @Override
    protected String getPageTitleKey() {
        return "testnotificationconfig.browse.title";
    }

    @Override
    protected String getPageSubtitleKey() {
        return "testnotificationconfig.browse.title";
    }
}
