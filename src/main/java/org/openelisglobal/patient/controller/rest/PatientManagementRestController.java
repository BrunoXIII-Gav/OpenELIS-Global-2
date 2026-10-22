package org.openelisglobal.patient.controller.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.common.exception.LIMSRuntimeException;
import org.openelisglobal.common.log.LogEvent;
import org.openelisglobal.common.rest.BaseRestController;
import org.openelisglobal.dataexchange.fhir.exception.FhirPersistanceException;
import org.openelisglobal.dataexchange.fhir.exception.FhirTransformationException;
import org.openelisglobal.dataexchange.fhir.service.FhirTransformService;
import org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.exception.PatientIdentifierConflict;
import org.openelisglobal.patient.form.PatientModuleAccess;
import org.openelisglobal.patient.form.PatientRegistrationResponse;
import org.openelisglobal.patient.service.PatientAuthorizationService;
import org.openelisglobal.patient.service.PatientPhotoService;
import org.openelisglobal.patient.service.PatientService;
import org.openelisglobal.patient.util.PatientUtil;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.patientidentity.service.PatientIdentityService;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.sample.service.OrderPatientContextService;
import org.openelisglobal.search.service.SearchResultsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/rest/")
public class PatientManagementRestController extends BaseRestController {
    @Autowired
    SearchResultsService searchService;
    @Autowired
    PatientIdentityService patientIdentityService;
    @Autowired
    PatientService patientService;
    @Autowired
    FhirTransformService fhirTransformService;
    @Autowired
    PatientPhotoService photoService;
    @Autowired
    PatientAuthorizationService patientAuthorizationService;
    @Autowired
    ModuleAuthorizationService moduleAuthorizationService;
    @Autowired
    org.openelisglobal.sample.service.OrderAuthorizationService orderAuthorizationService;

    @Autowired
    OrderPatientContextService orderPatientContextService;

    @PostMapping(value = { "PatientManagement", "orders/patient" }, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<?> savepatient(HttpServletRequest request,
            @Validated(SamplePatientEntryForm.SamplePatientEntry.class) @RequestBody PatientManagementInfo patientInfo,
            BindingResult bindingResult) throws Exception {
        String sysUserId = getSysUserId(request);
        String actionKey = StringUtils.isNotBlank(patientInfo.getPatientPK()) ? "update" : "create";
        boolean orderRegistration = request.getRequestURI().endsWith("/orders/patient");
        if (orderRegistration) {
            if (!"create".equals(actionKey) || !orderAuthorizationService.hasPermission(sysUserId, "create")) {
                throw new AccessDeniedException("User cannot register a new order patient");
            }
        } else {
            requirePatientPermission(sysUserId, actionKey);
        }
        if (StringUtils.isNotBlank(patientInfo.getPatientPK())) {
            patientInfo.setPatientUpdateStatus(PatientUpdateStatus.UPDATE);
        } else {
            patientInfo.setPatientUpdateStatus(PatientUpdateStatus.ADD);
        }
        Patient patient = new Patient();

        if (patientInfo.getPatientUpdateStatus() != PatientUpdateStatus.NO_ACTION) {

            PatientUtil.preparePatientData(bindingResult, request, patientInfo, patient);
            if (bindingResult.hasErrors()) {
                if (bindingResult.getAllErrors().stream()
                        .anyMatch(error -> "error.duplicate.nationalId".equals(error.getCode()))) {
                    return duplicateIdentifierResponse();
                }
                throw new BindException(bindingResult);
            }
            try {
                patientService.persistPatientData(patientInfo, patient, sysUserId);
                fhirTransformService.transformPersistPatient(patientInfo,
                        (patientInfo.getPatientUpdateStatus() == PatientUpdateStatus.ADD));
                photoService.savePhoto(patient.getId(), patientInfo.getPhoto());
            } catch (IllegalArgumentException e) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, e.getMessage(), e);
            } catch (FhirTransformationException | FhirPersistanceException e) {
                LogEvent.logError(e);
            }
        }
        if (orderRegistration) {
            orderPatientContextService.rememberRegistration(request, sysUserId, patientInfo.getPatientPK());
        }
        return ResponseEntity.ok(new PatientRegistrationResponse(patientInfo.getPatientPK(), patientInfo.getCugPrefix(),
                patientInfo.getGuid(), patientInfo.getPatientLastUpdated(), patientInfo.getPersonLastUpdated()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRegistrationFailure(RuntimeException error) {
        if (PatientIdentifierConflict.isDuplicate(error))
            return duplicateIdentifierResponse();
        throw error;
    }

    private ResponseEntity<?> duplicateIdentifierResponse() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", PatientIdentifierConflict.CODE));
    }

    @GetMapping("patient-photos/{id}/{isThumbnail}")
    public ResponseEntity<Map<String, String>> getPhoto(HttpServletRequest request, @PathVariable String id,
            @PathVariable boolean isThumbnail) throws LIMSRuntimeException {
        String sysUserId = getSysUserId(request);
        if (!orderPatientContextService.isOrderEntryLookup(request, sysUserId)
                && !patientAuthorizationService.hasPermission(sysUserId, "read")
                && !patientAuthorizationService.hasPermission(sysUserId, "update")
                && !orderPatientContextService.canAccessRegisteredPatient(request, sysUserId, id)) {
            throw new AccessDeniedException("User does not have Patients module permission to view a patient photo");
        }
        String photo = photoService.getPhotoByPatientId(id, isThumbnail);
        // Map.of does not allow null values and throws Objects.requireNonNull.
        // Return empty string when no photo exists to keep response shape stable.
        return ResponseEntity.ok(Map.of("data", photo == null ? "" : photo));
    }

    @GetMapping(value = "patient-management/access", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public PatientModuleAccess getPatientModuleAccess(HttpServletRequest request) {
        String sysUserId = getSysUserId(request);
        return new PatientModuleAccess(patientAuthorizationService.hasPermission(sysUserId, "read"),
                patientAuthorizationService.hasPermission(sysUserId, "create"),
                patientAuthorizationService.hasPermission(sysUserId, "update"),
                moduleAuthorizationService.getRestrictedFieldGroupKeys(sysUserId, "patients"),
                moduleAuthorizationService.getRestrictedFieldTagKeys(sysUserId, "patients"));
    }

    private void requirePatientPermission(String sysUserId, String actionKey) {
        boolean permitted = patientAuthorizationService.hasPermission(sysUserId, actionKey);
        if (!permitted) {
            throw new AccessDeniedException("User does not have Patients module permission: " + actionKey);
        }
    }

}
