package org.openelisglobal.home.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.openelisglobal.common.controller.BaseController;
import org.openelisglobal.common.util.validator.GenericValidator;
import org.openelisglobal.home.form.HomeForm;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class HomeController extends BaseController {

    private static final String[] ALLOWED_FIELDS = new String[] {};

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.setAllowedFields(ALLOWED_FIELDS);
    }

    @RequestMapping(value = { "/Dashboard", "/Home" }, method = RequestMethod.GET)
    public ModelAndView showPanelManagement(HttpServletRequest request) {
        boolean samlSession = Boolean.TRUE.equals(request.getSession().getAttribute("samlSession"));
        boolean hasUserSessionData = request.getSession().getAttribute(USER_SESSION_DATA) != null;
        if (samlSession && hasUserSessionData) {
            return new ModelAndView("redirect:" + resolveFrontendRoot(request));
        }
        if (samlSession) {
            request.getSession().removeAttribute("samlSession");
            return new ModelAndView("redirect:/LoginPage");
        }

        HomeForm form = new HomeForm();
        form.setFormName("mainForm");

        return findForward(FWD_SUCCESS, form);
    }

    @Override
    protected String findLocalForward(String forward) {
        if (FWD_SUCCESS.equals(forward)) {
            return "homePageDefinition";
        } else {
            return "PageNotFound";
        }
    }

    @Override
    protected String getPageTitleKey() {
        return null;
    }

    @Override
    protected String getPageSubtitleKey() {
        return null;
    }

    private String resolveFrontendRoot(HttpServletRequest request) {
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (GenericValidator.isBlankOrNull(scheme)) {
            scheme = request.getScheme();
        } else {
            scheme = scheme.split(",")[0].trim();
        }

        String host = sanitizeHost(request.getHeader("Host"));
        if (GenericValidator.isBlankOrNull(host)) {
            host = sanitizeHost(request.getHeader("X-Forwarded-Host"));
        }

        if (GenericValidator.isBlankOrNull(host)) {
            host = request.getServerName();
            if ("_".equals(host) || "__".equals(host)) {
                return "/";
            }
            int serverPort = request.getServerPort();
            boolean includePort = serverPort > 0
                    && !((serverPort == 80 && "http".equalsIgnoreCase(scheme))
                            || (serverPort == 443 && "https".equalsIgnoreCase(scheme)));
            if (includePort) {
                host = host + ":" + serverPort;
            }
        }

        return scheme + "://" + host + "/";
    }

    private String sanitizeHost(String host) {
        if (GenericValidator.isBlankOrNull(host)) {
            return null;
        }
        String candidate = host.split(",")[0].trim();
        if ("_".equals(candidate) || "__".equals(candidate)) {
            return null;
        }
        return candidate;
    }
}
