package org.openelisglobal.common.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.openelisglobal.common.form.BaseForm;
import org.openelisglobal.common.service.DatabaseCleanService;
import org.openelisglobal.patient.util.PatientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class DeletePatientTestDataController extends BaseController {

    @Autowired
    private DatabaseCleanService databaseCleanService;

    @GetMapping(value = "/DatabaseCleaningRequest")
    public ModelAndView confirmCleanSamplePatientDatabaseEntries(HttpServletRequest request) {
        databaseCleanService.requireCleanupAccess(getSysUserId(request));
        return findForward(FWD_SUCCESS, new BaseForm());
    }

    @PostMapping(value = "/DatabaseCleaningRequest")
    public String cleanSamplePatientDatabaseEntries(HttpServletRequest request) {

        databaseCleanService.cleanDatabase(getSysUserId(request));
        PatientUtil.invalidateUnknownPatients();

        return findForward(FWD_SUCCESS_DELETE);
    }

    @Override
    protected String findLocalForward(String forward) {

        if (FWD_SUCCESS_DELETE.equals(forward)) {
            return "redirect:/MasterListsPage";
        } else if (FWD_SUCCESS.equals(forward)) {
            return "databaseCleaningDefinition";
        }
        return "redirect:/Home";
    }

    @Override
    protected String getPageTitleKey() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected String getPageSubtitleKey() {
        // TODO Auto-generated method stub
        return null;
    }
}
