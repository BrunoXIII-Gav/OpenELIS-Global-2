package org.openelisglobal.sample.service;

import static org.junit.Assert.*;

import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.sample.exception.ConsentDownloadRequiredException;
import org.springframework.mock.web.MockHttpServletRequest;

public class OrderConsentDownloadServiceTest {
    private OrderConsentDownloadServiceImpl service;
    private MockHttpServletRequest request;
    private long time;

    @Before
    public void setup() {
        time = 1000;
        service = new OrderConsentDownloadServiceImpl() {
            @Override
            protected long now() {
                return time;
            }
        };
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

    private void rejected(Runnable action) {
        try {
            action.run();
            fail("Expected consent download requirement");
        } catch (ConsentDownloadRequiredException expected) {
            assertEquals("consent.download.requiredBeforeSubmit", expected.getMessage());
        }
    }
}
