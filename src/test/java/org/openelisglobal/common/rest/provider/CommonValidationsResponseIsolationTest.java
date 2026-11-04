package org.openelisglobal.common.rest.provider;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import org.junit.Test;
import org.openelisglobal.spring.util.SpringContext;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;

public class CommonValidationsResponseIsolationTest {
    @Test
    public void laterRequestCannotMutateAnEarlierResponse() throws Exception {
        Field field = SpringContext.class.getDeclaredField("factory");
        field.setAccessible(true);
        Object previous = field.get(null);
        AutowireCapableBeanFactory factory = mock(AutowireCapableBeanFactory.class);
        org.openelisglobal.common.util.DefaultConfigurationProperties configuration = mock(
                org.openelisglobal.common.util.DefaultConfigurationProperties.class);
        when(factory.getBean(org.openelisglobal.common.util.DefaultConfigurationProperties.class))
                .thenReturn(configuration);
        when(configuration.isPropertyValueEqual(
                org.openelisglobal.common.util.ConfigurationProperties.Property.VALIDATE_PHONE_FORMAT, "false"))
                .thenReturn(true);
        field.set(null, factory);
        try {
            CommonValidationsRestController controller = new CommonValidationsRestController();
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setParameter("value", "invalid");
            CommonValidationsRestController.ResponseObject first = controller.getPhoneNumberValidation(request);
            String original = first.getBody();
            CommonValidationsRestController.ResponseObject second = controller.getPhoneNumberValidation(request);
            assertNotSame(first, second);
            second.setBody("A different request");
            assertEquals(original, first.getBody());
        } finally {
            field.set(null, previous);
        }
    }
}
