package org.openelisglobal.sample.controller.rest;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.services.IStatusService;
import org.openelisglobal.common.services.StatusService.AnalysisStatus;
import org.openelisglobal.common.services.StatusService.SampleStatus;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DefaultConfigurationProperties;
import org.openelisglobal.config.AppConfig;
import org.openelisglobal.config.ControllerSetup;
import org.openelisglobal.dataexchange.fhir.service.FhirTransformService;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.reportdefinition.controller.ConsentTemplateRestController;
import org.openelisglobal.reports.service.ConsentTemplateService;
import org.openelisglobal.sample.exception.ConsentDownloadRequiredException;
import org.openelisglobal.sample.form.SampleEditForm;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.sample.service.OrderAuthorizationService;
import org.openelisglobal.sample.service.OrderConsentDownloadServiceImpl;
import org.openelisglobal.sample.service.SampleEditService;
import org.openelisglobal.sample.service.SamplePatientEntryService;
import org.openelisglobal.sample.validator.SampleEditFormValidator;
import org.openelisglobal.sample.validator.SamplePatientEntryFormValidator;
import org.openelisglobal.spring.util.SpringContext;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

public class OrderConsentDownloadRestControllerTest {
    private OrderConsentDownloadServiceImpl receipts;
    private DefaultConfigurationProperties configuration;
    private ConsentTemplateService templates;
    private MockHttpSession session;
    private MockMvc reports;

    @Before
    public void setup() {
        receipts = spy(new OrderConsentDownloadServiceImpl());
        configuration = mock(DefaultConfigurationProperties.class);
        when(configuration.isPropertyValueEqual(Property.ORDER_CONSENT_DOWNLOAD_ENABLED, "true")).thenReturn(true);
        ReflectionTestUtils.setField(receipts, "configurationProperties", configuration);
        templates = mock(ConsentTemplateService.class);
        when(templates.generateConsentPdf(any())).thenReturn(new byte[] { 37, 80, 68, 70 });
        var controller = new ConsentTemplateRestController();
        ReflectionTestUtils.setField(controller, "consentTemplateService", templates);
        ReflectionTestUtils.setField(controller, "orderConsentDownloadService", receipts);
        reports = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ControllerSetup()).build();
        session = new MockHttpSession();
        var user = new UserSessionData();
        user.setSytemUserId(42);
        session.setAttribute(IActionConstants.USER_SESSION_DATA, user);
    }

    @Test
    public void onlySuccessfulOrderDownloadsIssueServerReceipts() throws Exception {
        var response = reports
                .perform(post("/rest/reports/consent-template/download").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderPatientId\":\"10\",\"selectedTests\":[\"Test\"]}"))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().exists("X-OpenELIS-Consent-Token")).andReturn().getResponse();
        var request = new MockHttpServletRequest();
        request.setSession(session);
        receipts.requireDownload(request, "42", response.getHeader("X-OpenELIS-Consent-Token"), "10", null);
        reports.perform(post("/rest/reports/consent-template/preview").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"selectedTests\":[\"Test\"]}"))
                .andExpect(status().isOk()).andExpect(header().doesNotExist("X-OpenELIS-Consent-Token"));
        verify(receipts, times(1)).recordDownload(any(), any(), any(), any());
    }

    @Test
    public void emptyOrFailedPdfAndMissingOrderContextNeverIssueReceipts() throws Exception {
        reports.perform(post("/rest/reports/consent-template/download").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"selectedTests\":[\"Test\"]}"))
                .andExpect(status().isBadRequest());
        when(templates.generateConsentPdf(any())).thenReturn(new byte[0]);
        reports.perform(
                post("/rest/reports/consent-template/download").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderPatientId\":\"10\",\"selectedTests\":[\"Test\"]}"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().doesNotExist("X-OpenELIS-Consent-Token"));
        when(templates.generateConsentPdf(any())).thenThrow(new IllegalStateException("PDF failed"));
        reports.perform(
                post("/rest/reports/consent-template/download").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderPatientId\":\"10\",\"selectedTests\":[\"Test\"]}"))
                .andExpect(status().isInternalServerError());
        verify(receipts, never()).recordDownload(any(), any(), any(), any());
    }

    @Test
    public void directCreateApiRejectsMissingAndForgedProofBeforeCallingPersistence() throws Exception {
        var controller = new SamplePatientEntryRestController();
        var authorization = mock(OrderAuthorizationService.class);
        when(authorization.hasPermission("42", "create")).thenReturn(true);
        var persistence = mock(SamplePatientEntryService.class);
        ReflectionTestUtils.setField(controller, "orderAuthorizationService", authorization);
        ReflectionTestUtils.setField(controller, "orderConsentDownloadService", receipts);
        ReflectionTestUtils.setField(controller, "samplePatientService", persistence);
        var mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new ControllerSetup())
                .setMessageConverters(new AppConfig().jacksonMessageConverter()).setValidator(new Validator() {
                    public boolean supports(Class<?> type) {
                        return true;
                    }

                    public void validate(Object target, Errors errors) {
                    }
                }).build();
        for (String token : new String[] { "", "true", "invented-token" }) {
            mvc.perform(post("/rest/SamplePatientEntry").session(session).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"patientProperties\":{\"patientPK\":\"10\"},\"consentDownloadToken\":\"" + token
                            + "\"}"))
                    .andExpect(status().is(428))
                    .andExpect(header().string("X-OpenELIS-Error-Code", ConsentDownloadRequiredException.CODE));
        }
        verifyZeroInteractions(persistence);
    }

    @Test
    public void disabledDownloadsDoNotBypassCreateAuthorizationOrNormalValidation() throws Exception {
        Object previousFactory = ReflectionTestUtils.getField(SpringContext.class, "factory");
        var factory = mock(AutowireCapableBeanFactory.class);
        when(factory.getBean(DefaultConfigurationProperties.class)).thenReturn(configuration);
        ReflectionTestUtils.setField(SpringContext.class, "factory", factory);
        try {
            when(configuration.isPropertyValueEqual(Property.ORDER_CONSENT_DOWNLOAD_ENABLED, "true")).thenReturn(false);
            var controller = new SamplePatientEntryRestController();
            var authorization = mock(OrderAuthorizationService.class);
            when(authorization.hasPermission("42", "create")).thenReturn(true);
            var persistence = mock(SamplePatientEntryService.class);
            var validator = mock(SamplePatientEntryFormValidator.class);
            ReflectionTestUtils.setField(controller, "orderAuthorizationService", authorization);
            ReflectionTestUtils.setField(controller, "orderConsentDownloadService", receipts);
            ReflectionTestUtils.setField(controller, "samplePatientService", persistence);
            ReflectionTestUtils.setField(controller, "formValidator", validator);
            var request = new MockHttpServletRequest();
            request.setSession(session);
            var form = new SamplePatientEntryForm();
            var errors = new BeanPropertyBindingResult(form, "form");

            // No receipt is needed, but normal validation and laboratory access still run.
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> controller.samplePatientEntrySave(request, form, errors, new RedirectAttributesModelMap()));
            verify(validator).validate(form, errors);
            verify(authorization).canAccessSampleXml("42", form.getSampleXML(), "create");
            verifyZeroInteractions(persistence);

            when(authorization.hasPermission("42", "create")).thenReturn(false);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> controller.samplePatientEntrySave(request, form, errors, new RedirectAttributesModelMap()));
            verify(validator, times(1)).validate(form, errors);
            verifyZeroInteractions(persistence);
        } finally {
            ReflectionTestUtils.setField(SpringContext.class, "factory", previousFactory);
        }
    }

    @Test
    public void directEditApiAllowsAuthorizedUpdatesWithoutAConsentDownload() throws Exception {
        // Legacy controller initializes status IDs statically; provide only that
        // collaborator.
        Object previousFactory = ReflectionTestUtils.getField(SpringContext.class, "factory");
        var factory = mock(AutowireCapableBeanFactory.class);
        var statuses = mock(IStatusService.class);
        when(statuses.getStatusID(any(AnalysisStatus.class))).thenReturn("1");
        when(statuses.getStatusID(any(SampleStatus.class))).thenReturn("1");
        when(factory.getBean(IStatusService.class)).thenReturn(statuses);
        ReflectionTestUtils.setField(SpringContext.class, "factory", factory);
        try {
            var controller = new SampleEditRestController();
            var authorization = mock(OrderAuthorizationService.class);
            when(authorization.hasPermission("42", "update")).thenReturn(true);
            when(authorization.canAccessAllTests(eq("42"), anyList(), eq("update"))).thenReturn(true);
            var persistence = mock(SampleEditService.class);
            ReflectionTestUtils.setField(controller, "orderAuthorizationService", authorization);
            ReflectionTestUtils.setField(controller, "sampleEditService", persistence);
            ReflectionTestUtils.setField(controller, "formValidator", mock(SampleEditFormValidator.class));
            ReflectionTestUtils.setField(controller, "fhirTransformService", mock(FhirTransformService.class));
            var request = new MockHttpServletRequest();
            request.setSession(session);
            var form = mock(SampleEditForm.class);
            when(form.getAccessionNumber()).thenReturn("LAB2");
            controller.saveSampleEdit(request, form, new BeanPropertyBindingResult(form, "form"));
            verify(persistence).editSample(form, request, null, false, "42");
            verify(receipts, never()).claim(any(), any(), any(), any(), any());
            // Removing the download requirement must preserve update authorization.
            when(authorization.hasPermission("42", "update")).thenReturn(false);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> controller.saveSampleEdit(request, form, new BeanPropertyBindingResult(form, "form")));
            verify(persistence, times(1)).editSample(any(), any(), any(), anyBoolean(), any());
        } finally {
            ReflectionTestUtils.setField(SpringContext.class, "factory", previousFactory);
        }
    }
}
