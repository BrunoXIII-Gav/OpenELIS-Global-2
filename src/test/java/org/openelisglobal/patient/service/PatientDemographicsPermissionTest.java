package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.analysis.service.AnalysisService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationDecision;
import org.openelisglobal.authorization.service.ModuleAuthorizationService.AuthorizationSource;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.patient.controller.rest.PatientHistorySummaryRestController;
import org.openelisglobal.patient.controller.rest.PatientManagementRestController;
import org.openelisglobal.patientadditionalfield.controller.rest.PatientAdditionalFieldRestController;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldService;
import org.openelisglobal.sample.service.OrderPatientContextServiceImpl;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Exercises registration and clinical guards with the real patient permission
 * resolver.
 */
public class PatientDemographicsPermissionTest {
    private ModuleAuthorizationService modules;
    private AnalysisService analyses;
    private PatientAuthorizationServiceImpl patients;
    private PatientManagementRestController management;
    private OrderPatientContextServiceImpl orderContext;
    private MockHttpServletRequest request;

    @Before
    public void setup() {
        modules = mock(ModuleAuthorizationService.class);
        analyses = mock(AnalysisService.class);
        patients = new PatientAuthorizationServiceImpl();
        ReflectionTestUtils.setField(patients, "moduleAuthorizationService", modules);
        ReflectionTestUtils.setField(patients, "analysisService", analyses);
        management = new PatientManagementRestController();
        ReflectionTestUtils.setField(management, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(management, "moduleAuthorizationService", modules);
        orderContext = new OrderPatientContextServiceImpl();
        ReflectionTestUtils.setField(orderContext, "patientAuthorization", patients);
        request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(42);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        permission("read", true);
        permission("create", false);
        permission("update", true);
    }

    @Test
    public void patientWithoutAnalysesCanBeEditedFromPatientsAndOrdersButClinicalHistoryStaysDenied() {
        ReflectionTestUtils.invokeMethod(management, "requirePatientPermission", "42", "update");
        assertTrue(orderContext.canUpdatePatient(request, "42", "11"));
        verifyZeroInteractions(analyses);

        var history = new PatientHistorySummaryRestController();
        var summaries = mock(PatientHistorySummaryService.class);
        ReflectionTestUtils.setField(history, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(history, "patientHistorySummaryService", summaries);
        try {
            history.getPatientHistorySummary(request, "11");
            fail("Demographics permission must not grant clinical history access");
        } catch (AccessDeniedException expected) {
        }
        verify(analyses).hasAnalysisForPatientInTestSections("11", List.of(7));
        verifyZeroInteractions(summaries);
    }

    @Test
    public void readOnlyPermissionCannotModifyPatientsInEitherModule() {
        permission("update", false);
        assertFalse(orderContext.canUpdatePatient(request, "42", "11"));
        try {
            ReflectionTestUtils.invokeMethod(management, "requirePatientPermission", "42", "update");
            fail("Patient update permission is mandatory");
        } catch (AccessDeniedException expected) {
        }
        verifyZeroInteractions(analyses);
    }

    @Test
    public void standaloneCreationRequiresCreatePermissionAndNeverRequiresAnalyses() {
        try {
            ReflectionTestUtils.invokeMethod(management, "requirePatientPermission", "42", "create");
            fail("Patient create permission is mandatory");
        } catch (AccessDeniedException expected) {
        }
        permission("create", true);
        ReflectionTestUtils.invokeMethod(management, "requirePatientPermission", "42", "create");
        permission("update", false);
        assertFalse(orderContext.canUpdatePatient(request, "42", "11"));
        verifyZeroInteractions(analyses);
    }

    @Test
    public void fieldAndBlockRestrictionsAreReturnedUnchangedAlongsideDemographicPermissions() {
        when(modules.getRestrictedFieldGroupKeys("42", "patients")).thenReturn(Set.of("validation-summary"));
        when(modules.getRestrictedFieldTagKeys("42", "patients")).thenReturn(Set.of("patient-first-name"));
        var access = management.getPatientModuleAccess(request);
        assertTrue(access.canRead());
        assertTrue(access.canUpdate());
        assertFalse(access.canCreate());
        assertEquals(Set.of("validation-summary"), access.restrictedFieldGroupKeys());
        assertEquals(Set.of("patient-first-name"), access.restrictedFieldTagKeys());
        verifyZeroInteractions(analyses);
    }

    @Test
    public void additionalRegistrationFieldsLoadWithoutAnalysesButStillRequireModulePermission() {
        var controller = new PatientAdditionalFieldRestController();
        var fields = mock(PatientAdditionalFieldService.class);
        ReflectionTestUtils.setField(controller, "patientAuthorizationService", patients);
        ReflectionTestUtils.setField(controller, "patientAdditionalFieldService", fields);
        ReflectionTestUtils.setField(controller, "orderAuthorizationService",
                mock(org.openelisglobal.sample.service.OrderAuthorizationService.class));
        when(fields.getFields(false, true)).thenReturn(List.of());
        when(fields.getPatientValues("11", List.of())).thenReturn(Map.of("example", "value"));
        assertEquals(Map.of("example", "value"), controller.getValues(request, "11"));
        verifyZeroInteractions(analyses);
        permission("read", false);
        permission("update", false);
        try {
            controller.getValues(request, "11");
            fail("No module permission must still deny access");
        } catch (AccessDeniedException expected) {
        }
        verify(fields, times(1)).getPatientValues("11", List.of());
    }

    private void permission(String action, boolean allowed) {
        when(modules.getAuthorization("42", "patients", action))
                .thenReturn(new AuthorizationDecision(allowed, AuthorizationSource.MODULE_PERMISSION, false, Set.of("7")));
    }
}
