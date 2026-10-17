package org.openelisglobal.sample.controller.rest;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openelisglobal.common.action.IActionConstants;
import org.openelisglobal.common.constants.SystemPermission;
import org.openelisglobal.login.valueholder.UserSessionData;
import org.openelisglobal.sample.form.CugPrefixSettingsForm;
import org.openelisglobal.sample.service.CugPrefixService;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

public class CugPrefixSettingsSecurityTest {
    @Configuration
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class SecurityConfig {
        @Bean
        public PermissionStub accessControl() {
            return new PermissionStub();
        }

        @Bean
        public CugPrefixService prefixes() {
            return mock(CugPrefixService.class);
        }

        @Bean
        public CugPrefixSettingsRestController controller(CugPrefixService prefixes) {
            return new CugPrefixSettingsRestController(prefixes);
        }
    }

    public static class PermissionStub {
        public boolean allowed;

        public boolean hasPermission(SystemPermission permission) {
            return allowed;
        }
    }

    private AnnotationConfigApplicationContext context;
    private CugPrefixSettingsRestController controller;
    private CugPrefixService prefixes;
    private PermissionStub access;

    @Before
    public void setup() {
        context = new AnnotationConfigApplicationContext(SecurityConfig.class);
        controller = context.getBean(CugPrefixSettingsRestController.class);
        prefixes = context.getBean(CugPrefixService.class);
        access = context.getBean(PermissionStub.class);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("user", "", java.util.List.of()));
    }

    @After
    public void teardown() {
        SecurityContextHolder.clearContext();
        if (context != null)
            context.close();
    }

    @Test
    public void nonAdministratorsCannotReadOrChangeTheCounter() {
        try {
            controller.get();
            fail();
        } catch (AccessDeniedException expected) {
        }
        try {
            controller.set(new CugPrefixSettingsForm(), new MockHttpServletRequest());
            fail();
        } catch (AccessDeniedException expected) {
        }
        verifyZeroInteractions(prefixes);
    }

    @Test
    public void administratorsSaveWithTheirSessionUserForAuditing() {
        access.allowed = true;
        var request = new MockHttpServletRequest();
        var user = new UserSessionData();
        user.setSytemUserId(42);
        request.getSession().setAttribute(IActionConstants.USER_SESSION_DATA, user);
        var form = new CugPrefixSettingsForm();
        form.setStartPrefix("4000");
        when(prefixes.setStart("4000", "42")).thenReturn(form);
        assertSame(form, controller.set(form, request).getBody());
        verify(prefixes).setStart("4000", "42");
    }

    @Test
    public void invalidStartsReturnBadRequestInsteadOfSaving() {
        access.allowed = true;
        when(prefixes.setStart(any(), any())).thenThrow(new IllegalArgumentException("cug.prefix.error.invalid"));
        var form = new CugPrefixSettingsForm();
        form.setStartPrefix("0");
        assertEquals(400, controller.set(form, new MockHttpServletRequest()).getStatusCode().value());
    }
}
