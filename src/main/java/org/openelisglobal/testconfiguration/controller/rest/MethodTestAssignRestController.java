package org.openelisglobal.testconfiguration.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.method.service.MethodService;
import org.openelisglobal.method.valueholder.Method;
import org.openelisglobal.test.service.TbMethodTestService;
import org.openelisglobal.test.service.TestService;
import org.openelisglobal.test.service.TestServiceImpl;
import org.openelisglobal.test.valueholder.TbMethodTest;
import org.openelisglobal.test.valueholder.Test;
import org.openelisglobal.testconfiguration.action.MethodTests;
import org.openelisglobal.testconfiguration.form.MethodTestAssignForm;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RestController
@RequestMapping("/rest")
public class MethodTestAssignRestController extends BaseController {

    private static final String[] ALLOWED_FIELDS = new String[] { "methodId", "currentTests*", "availableTests*" };

    @Autowired
    private MethodService methodService;

    @Autowired
    private TestService testService;

    @Autowired
    private TbMethodTestService tbMethodTestService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @GetMapping(value = "/MethodTestAssign")
    public MethodTestAssignForm showMethodTestAssign(@RequestParam(name = "methodId", required = false) String methodId,
            HttpServletRequest request) {
        MethodTestAssignForm form = new MethodTestAssignForm();
        form.setMethodId(methodId == null ? "" : methodId);
        setupDisplayItems(form);
        return form;
    }

    private void setupDisplayItems(MethodTestAssignForm form) {
        List<IdValuePair> methods = new ArrayList<>();
        methods.addAll(DisplayListService.getInstance().getListWithLeadingBlank(DisplayListService.ListType.METHODS));
        methods.addAll(DisplayListService.getInstance().getList(DisplayListService.ListType.METHODS_INACTIVE));
        form.setMethodList(methods);

        if (GenericValidator.isBlankOrNull(form.getMethodId()) || "0".equals(form.getMethodId())) {
            return;
        }

        Method method = methodService.get(form.getMethodId());
        if (method == null || GenericValidator.isBlankOrNull(method.getId())) {
            return;
        }

        String methodLabel = StringUtils.isNotBlank(method.getMethodName()) ? method.getMethodName()
                : method.getLocalizedValue();
        MethodTests methodTests = new MethodTests(new IdValuePair(method.getId(), methodLabel));

        List<IdValuePair> assignedTests = new ArrayList<>();
        Set<String> assignedTestIds = new HashSet<>();

        List<TbMethodTest> methodLinks = tbMethodTestService.getAllMatching("methodId", form.getMethodId());
        for (TbMethodTest link : methodLinks) {
            if (!"Y".equals(link.getIsActive())) {
                continue;
            }

            Test test = testService.get(link.getTestId());
            if (test == null || !test.isActive() || GenericValidator.isBlankOrNull(test.getId())) {
                continue;
            }

            assignedTests.add(new IdValuePair(test.getId(), TestServiceImpl.getUserLocalizedTestName(test)));
            assignedTestIds.add(test.getId());
        }

        List<Test> allActiveTests = testService.getAllActiveTests(false);
        List<IdValuePair> availableTests = new ArrayList<>();
        for (Test test : allActiveTests) {
            if (assignedTestIds.contains(test.getId())) {
                continue;
            }
            availableTests.add(new IdValuePair(test.getId(), TestServiceImpl.getUserLocalizedTestName(test)));
        }

        Comparator<IdValuePair> byValue = Comparator.comparing(IdValuePair::getValue, String.CASE_INSENSITIVE_ORDER);
        Collections.sort(assignedTests, byValue);
        Collections.sort(availableTests, byValue);

        methodTests.setTests(assignedTests);
        methodTests.setAvailableTests(availableTests);
        form.setSelectedMethod(methodTests);
    }

    @PostMapping(value = "/MethodTestAssign")
    public MethodTestAssignForm postMethodTestAssign(HttpServletRequest request,
            @RequestBody @Valid MethodTestAssignForm form, BindingResult result,
            RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            saveErrors(result);
            setupDisplayItems(form);
            return form;
        }

        String methodId = form.getMethodId();
        String currentUser = getSysUserId(request);
        Set<String> requestedTestIds = new HashSet<>(
                form.getCurrentTests() == null ? List.of() : form.getCurrentTests());

        List<TbMethodTest> existingLinks = tbMethodTestService.getAllMatching("methodId", methodId);
        Set<String> matchedRequested = new HashSet<>();

        for (TbMethodTest link : existingLinks) {
            if (requestedTestIds.contains(link.getTestId())) {
                matchedRequested.add(link.getTestId());
                if (!"Y".equals(link.getIsActive())) {
                    link.setIsActive("Y");
                    link.setSysUserId(currentUser);
                    tbMethodTestService.update(link);
                }
            } else if ("Y".equals(link.getIsActive())) {
                link.setIsActive("N");
                link.setSysUserId(currentUser);
                tbMethodTestService.update(link);
            }
        }

        for (String testId : requestedTestIds) {
            if (matchedRequested.contains(testId)) {
                continue;
            }
            TbMethodTest link = new TbMethodTest();
            link.setMethodId(methodId);
            link.setTestId(testId);
            link.setIsActive("Y");
            link.setSysUserId(currentUser);
            tbMethodTestService.insert(link);
        }

        Method method = methodService.get(methodId);
        if (method != null) {
            String desiredState = requestedTestIds.isEmpty() ? "N" : "Y";
            if (!StringUtils.equals(method.getIsActive(), desiredState)) {
                method.setIsActive(desiredState);
                method.setSysUserId(currentUser);
                methodService.update(method);
            }
        }

        DisplayListService.getInstance().refreshList(DisplayListService.ListType.METHODS);
        DisplayListService.getInstance().refreshList(DisplayListService.ListType.METHODS_INACTIVE);

        setupDisplayItems(form);
        return form;
    }

    @Override
    protected String findLocalForward(String forward) {
        return "PageNotFound";
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
