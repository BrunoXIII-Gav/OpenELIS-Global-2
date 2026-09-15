package org.openelisglobal.referral.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.common.constants.Constants;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.common.services.DisplayListService;
import org.openelisglobal.common.util.IdValuePair;
import org.openelisglobal.referral.form.ReferredOutTestsForm;
import org.openelisglobal.referral.service.ReferralService;
import org.openelisglobal.result.service.ResultAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/")
public class ReferredOutTestsRestController extends BaseRestController {

    private static final String[] ALLOWED_FIELDS = new String[] { "labNumber", "testIds", "testUnitIds", "endDate",
            "startDate", "dateType", "searchType", "selPatient" };

    @Autowired
    private ReferralService referralService;

    @Autowired
    private ModuleAuthorizationService moduleAuthorizationService;

    @Autowired
    private ResultAuthorizationService resultAuthorizationService;

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @GetMapping(value = "ReferredOutTests")
    public ReferredOutTestsForm showReferredOutTests(HttpServletRequest request, @Valid ReferredOutTestsForm form)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        String userId = getSysUserId(request);
        if (!moduleAuthorizationService.hasPermission(userId, "results", "read")) {
            throw new AccessDeniedException("The user does not have permission to read results");
        }
        setupPageForDisplay(form, userId);
        return form;
    }

    private void setupPageForDisplay(ReferredOutTestsForm form, String userId)
            throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        if (form.getSearchType() != null) {
            form.setReferralDisplayItems(referralService.getReferralItems(form));
            Set<String> accessibleAnalysisIds = Set.copyOf(resultAuthorizationService.filterAccessibleAnalysisIds(userId,
                    form.getReferralDisplayItems().stream().map(item -> item.getAnalysisId()).toList(), "read",
                    Constants.ROLE_RESULTS));
            form.setReferralDisplayItems(form.getReferralDisplayItems().stream()
                    .filter(item -> accessibleAnalysisIds.contains(item.getAnalysisId())).toList());
            form.setSearchFinished(true);
        }
        form.setTestSelectionList(DisplayListService.getInstance().getList(DisplayListService.ListType.ALL_TESTS));
        form.setTestUnitSelectionList(
                DisplayListService.getInstance().getList(DisplayListService.ListType.TEST_SECTION_BY_NAME));
    }

    public class NonNumericTests {
        public String testId;
        public String testType;
        public List<IdValuePair> dictionaryValues;
    }
}
