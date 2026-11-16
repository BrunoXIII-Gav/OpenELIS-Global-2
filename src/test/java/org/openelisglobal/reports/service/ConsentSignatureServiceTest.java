package org.openelisglobal.reports.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.systemuser.service.SystemUserService;
import org.openelisglobal.systemuser.valueholder.SystemUser;
import org.springframework.test.util.ReflectionTestUtils;

public class ConsentSignatureServiceTest {
    private ConsentSignatureServiceImpl service;
    private ProviderService providers;
    private SystemUserService users;
    private SystemUser requester;
    private Provider provider;
    private byte[] signature;

    @Before
    public void setup() throws Exception {
        service = new ConsentSignatureServiceImpl();
        providers = mock(ProviderService.class);
        users = mock(SystemUserService.class);
        ReflectionTestUtils.setField(service, "providers", providers);
        ReflectionTestUtils.setField(service, "users", users);
        Person person = new Person();
        person.setId("person-requester");
        provider = new Provider();
        provider.setId("provider-requester");
        provider.setPerson(person);
        requester = new SystemUser();
        requester.setId("requester-account");
        signature = ConsentJasperServiceTest.signaturePng();
        requester.setSignatureImageData("data:image/png;base64," + Base64.getEncoder().encodeToString(signature));
        when(providers.get("provider-requester")).thenReturn(provider);
        when(providers.getAllMatching("person.id", "person-requester")).thenReturn(List.of(provider));
        when(users.getAllMatching(Map.of("linkedProviderPersonId", "person-requester", "isActive", "Y")))
                .thenReturn(List.of(requester));
    }

    @Test
    public void resolvesOnlyTheAccountLinkedToTheSelectedRequester() {
        assertArrayEquals(signature, service.getProviderSignature("provider-requester", "person-requester"));
        verify(users).getAllMatching(Map.of("linkedProviderPersonId", "person-requester", "isActive", "Y"));
        verifyNoMoreInteractions(users);
    }

    @Test
    public void supportsPersonSelectionAndStoredRawBase64() {
        requester.setSignatureImageData(Base64.getEncoder().encodeToString(signature));
        assertArrayEquals(signature, service.getProviderSignature(null, "person-requester"));
    }

    @Test
    public void mismatchedOrUnknownRequesterNeverFallsBackToAnotherUser() {
        assertNull(service.getProviderSignature("provider-requester", "someone-else"));
        assertNull(service.getProviderSignature("missing", "person-requester"));
        assertNull(service.getProviderSignature(null, null));
        verifyZeroInteractions(users);
    }

    @Test
    public void absentOrAmbiguousLinkedAccountLeavesSignatureEmpty() {
        var match = Map.<String, Object>of("linkedProviderPersonId", "person-requester", "isActive", "Y");
        when(users.getAllMatching(match)).thenReturn(List.of());
        assertNull(service.getProviderSignature("provider-requester", null));
        when(users.getAllMatching(match)).thenReturn(List.of(requester, new SystemUser()));
        assertNull(service.getProviderSignature("provider-requester", null));
    }

    @Test
    public void missingMalformedAndNonImageSignaturesLeaveSpaceEmpty() {
        for (String invalid : List.of("", "not-base64", "https://example.org/signature.png",
                "data:image/png;base64," + Base64.getEncoder().encodeToString("not an image".getBytes()),
                "x".repeat(3 * 1024 * 1024))) {
            requester.setSignatureImageData(invalid);
            assertNull(service.getProviderSignature("provider-requester", null));
        }
    }
}
