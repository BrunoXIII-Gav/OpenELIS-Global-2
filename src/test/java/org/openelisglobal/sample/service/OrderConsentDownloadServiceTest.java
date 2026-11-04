package org.openelisglobal.sample.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.util.ConfigurationProperties.Property;
import org.openelisglobal.common.util.DefaultConfigurationProperties;
import org.openelisglobal.common.util.DefaultConfigurationProperties.OEProperties;
import org.openelisglobal.sample.exception.ConsentDownloadRequiredException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

public class OrderConsentDownloadServiceTest {
    private OrderConsentDownloadServiceImpl service;
    private MockHttpServletRequest request;
    private long time;
    private DefaultConfigurationProperties configuration;

    @Before
    public void setup() {
        time = 1000;
        service = new OrderConsentDownloadServiceImpl() {
            @Override
            protected long now() {
                return time;
            }
        };
        configuration = mock(DefaultConfigurationProperties.class);
        when(configuration.isPropertyValueEqual(Property.ORDER_CONSENT_DOWNLOAD_ENABLED, "true")).thenReturn(true);
        ReflectionTestUtils.setField(service, "configurationProperties", configuration);
        request = new MockHttpServletRequest();
    }

    @Test
    public void clientFlagsOrForgedTokensCannotAuthorizeSaving() {
        rejected(() -> service.claim(request, "42", null, "10", null));
        rejected(() -> service.claim(request, "42", "true", "10", null));
        assertNull(request.getSession(false));
    }

    @Test
    public void receiptsAreBoundToUserSessionPatientAndOperation() {
        String token = service.recordDownload(request, "42", "10", null);
        service.requireDownload(request, "42", token, "10", null);
        rejected(() -> service.claim(new MockHttpServletRequest(), "42", token, "10", null));
        rejected(() -> service.claim(request, "43", token, "10", null));
        rejected(() -> service.claim(request, "42", token, "11", null));
        rejected(() -> service.claim(request, "42", token, null, "LAB1"));
    }

    @Test
    public void editingRequiresAReceiptForThatSpecificOrder() {
        String token = service.recordDownload(request, "42", "10", "LAB1");
        service.requireDownload(request, "42", token, null, "LAB1");
        rejected(() -> service.claim(request, "42", token, null, "LAB2"));
        rejected(() -> service.claim(request, "42", token, "10", null));
    }

    @Test
    public void successfulSaveConsumesReceiptAndPreventsReplay() {
        String token = service.recordDownload(request, "42", "10", null);
        try (var permit = service.claim(request, "42", token, "10", null)) {
            permit.complete();
        }
        rejected(() -> service.claim(request, "42", token, "10", null));
    }

    @Test
    public void failedSaveAllowsRetryButParallelSubmissionsCannotShareReceipt() {
        String token = service.recordDownload(request, "42", "10", null);
        try (var permit = service.claim(request, "42", token, "10", null)) {
            rejected(() -> service.claim(request, "42", token, "10", null));
        }
        try (var retry = service.claim(request, "42", token, "10", null)) {
            retry.complete();
        }
        rejected(() -> service.claim(request, "42", token, "10", null));
    }

    @Test
    public void receiptsExpireAndRegistryIsBounded() {
        String oldest = service.recordDownload(request, "42", "10", null);
        for (int i = 0; i < 32; i++)
            service.recordDownload(request, "42", "10", null);
        rejected(() -> service.claim(request, "42", oldest, "10", null));
        String latest = service.recordDownload(request, "42", "10", null);
        time += Duration.ofHours(2).toMillis();
        rejected(() -> service.claim(request, "42", latest, "10", null));
    }

    @Test
    public void distinctTabsCanSaveIndependentlyAndLogoutInvalidatesReceipts() {
        String first = service.recordDownload(request, "42", "10", null);
        String second = service.recordDownload(request, "42", "10", null);
        try (var permit = service.claim(request, "42", first, "10", null)) {
            permit.complete();
        }
        service.requireDownload(request, "42", second, "10", null);
        request.getSession().invalidate();
        rejected(() -> service.claim(request, "42", second, "10", null));
    }

    @Test
    public void ucdDefaultsDisableTheDownloadRequirement() {
        var defaults = new DefaultConfigurationProperties();
        OEProperties properties = ReflectionTestUtils.invokeMethod(defaults, "loadHardcodedProperties");
        assertEquals("false", properties.getProperty(Property.ORDER_CONSENT_DOWNLOAD_ENABLED.name()));
    }

    @Test
    public void disabledDownloadsAllowSavingWithoutProofOrCreatingASession() {
        when(configuration.isPropertyValueEqual(Property.ORDER_CONSENT_DOWNLOAD_ENABLED, "true")).thenReturn(false);
        service.requireDownload(request, "42", null, "10", null);
        try (var permit = service.claim(request, "42", null, "10", null)) {
            permit.complete();
        }
        assertNull(request.getSession(false));
    }

    @Test
    public void disablingDoesNotConsumeReceiptsAndReenablingRestoresEnforcement() {
        String token = service.recordDownload(request, "42", "10", null);
        when(configuration.isPropertyValueEqual(Property.ORDER_CONSENT_DOWNLOAD_ENABLED, "true")).thenReturn(false);
        try (var permit = service.claim(request, "42", token, "10", null)) {
            permit.complete();
        }
        when(configuration.isPropertyValueEqual(Property.ORDER_CONSENT_DOWNLOAD_ENABLED, "true")).thenReturn(true);
        rejected(() -> service.requireDownload(request, "42", null, "10", null));
        service.requireDownload(request, "42", token, "10", null);
    }

    private void rejected(Runnable action) {
        try {
            action.run();
            fail("Expected consent download requirement");
        } catch (ConsentDownloadRequiredException expected) {
            assertEquals("consent.download.requiredBeforeSubmit", expected.getMessage());
        }
    }
}
