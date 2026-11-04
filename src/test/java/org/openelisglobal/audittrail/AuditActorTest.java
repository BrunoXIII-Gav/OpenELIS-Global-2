package org.openelisglobal.audittrail;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.openelisglobal.audittrail.persistence.AuditActor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class AuditActorTest {
    @After
    public void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void identifiesAuthenticatedSsoProvisioningWithoutInventingLocalUserId() {
        RequestContextHolder
                .setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("POST", "/sso")));
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("verified-principal", "unused", List.of()));
        AuditActor actor = AuditActor.current(null);
        assertEquals("IDENTITY_PROVIDER", actor.kind());
        assertEquals("verified-principal", actor.name());
        assertNull(actor.id());
        assertEquals(actor.requestId(), AuditActor.current(null).requestId());
    }

    @Test(expected = IllegalStateException.class)
    public void rejectsUnattributedWebMutations() {
        RequestContextHolder
                .setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("POST", "/rest/sample")));
        AuditActor.current(null);
    }

    @Test
    public void identifiesBackgroundTasksExplicitly() {
        assertEquals("SYSTEM", AuditActor.current(null).kind());
        assertNull(AuditActor.current(null).id());
    }

    @Test
    public void emptyServletContextIsNotTreatedAsAnAuthenticatedHttpAction() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        var entity = new org.openelisglobal.testadditionalfield.valueholder.TestAdditionalFieldDefinition();
        entity.setSysUserId("7");
        assertEquals("SERVICE", AuditActor.current(entity).kind());
        assertEquals("7", AuditActor.current(entity).id());
    }

}
