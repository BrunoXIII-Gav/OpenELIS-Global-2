/**
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License");
 * you may not use this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.mozilla.org/MPL/
 *
 * <p>Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF
 * ANY KIND, either express or implied. See the License for the specific language governing rights
 * and limitations under the License.
 *
 * <p>The Original Code is OpenELIS code.
 *
 * <p>Copyright (C) The Minnesota Department of Health. All Rights Reserved.
 *
 * <p>Contributor(s): CIRG, University of Washington, Seattle WA.
 */
package org.openelisglobal.common.provider.query;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.apache.commons.validator.GenericValidator;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.provider.query.workerObjects.PatientSearchLocalAndExternalWorker;
import org.openelisglobal.common.provider.query.workerObjects.PatientSearchLocalWorker;
import org.openelisglobal.common.provider.query.workerObjects.PatientSearchWorker;
import org.openelisglobal.common.servlet.validation.AjaxServlet;
import org.openelisglobal.common.util.ConfigurationProperties;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.patient.service.PatientIdentifierSearchService;
import org.openelisglobal.spring.util.SpringContext;

public class PatientSearchProvider extends BaseQueryProvider {

    protected AjaxServlet ajaxServlet = null;

    PatientIdentifierSearchService patientIdentifierSearchService = SpringContext
            .getBean(PatientIdentifierSearchService.class);

    @Override
    public void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String lastName = request.getParameter("lastName");
        String firstName = request.getParameter("firstName");
        String STNumber = request.getParameter("STNumber");
        // N.B. This is a bad name, it is other than STnumber
        String subjectNumber = request.getParameter("subjectNumber");
        String nationalID = request.getParameter("nationalID");
        String labNumber = request.getParameter("labNumber");
        String guid = request.getParameter("guid");
        String dateOfBirth = request.getParameter("dateOfBirth");
        String gender = request.getParameter("gender");
        String suppressExternalSearch = request.getParameter("suppressExternalSearch");
        String patientID = null;

        String result = VALID;
        StringBuilder xml = new StringBuilder();
        // Prefix lookups also find patients who do not yet have an order.
        if (!GenericValidator.isBlankOrNull(labNumber)) {
            List<PatientSearchResults> patients = patientIdentifierSearchService
                    .findByCugPrefixOrOrderNumber(labNumber);
            if (patients.isEmpty()) {
                result = IActionConstants.INVALID;
                xml.append("No results were found for search.  Check spelling or remove some of the fields");
            } else {
                PatientSearchWorker localWorker = new PatientSearchLocalWorker();
                patients.forEach(patient -> localWorker.appendSearchResultRow(patient, xml));
            }
        } else {

            PatientSearchWorker worker = getAppropriateWorker(request, "true".equals(suppressExternalSearch));

            if (worker != null) {
                result = worker.createSearchResultXML(lastName, firstName, STNumber, subjectNumber, nationalID,
                        patientID, guid, dateOfBirth, gender, xml);
            } else {
                result = INVALID;
                xml.append("System is not configured correctly for searching for patients. Contact Administrator");
            }
        }
        ajaxServlet.sendData(xml.toString(), result, request, response);
    }

    private PatientSearchWorker getAppropriateWorker(HttpServletRequest request, boolean suppressExternalSearch) {

        if (ConfigurationProperties.getInstance().isCaseInsensitivePropertyValueEqual(Property.UseExternalPatientInfo,
                "false") || suppressExternalSearch) {
            return new PatientSearchLocalWorker();
        } else {
            UserSessionData usd = (UserSessionData) request.getSession().getAttribute(USER_SESSION_DATA);
            return new PatientSearchLocalAndExternalWorker(String.valueOf(usd.getSystemUserId()));
        }
    }

    @Override
    public void setServlet(AjaxServlet as) {
        ajaxServlet = as;
    }

    @Override
    public AjaxServlet getServlet() {
        return ajaxServlet;
    }
}
