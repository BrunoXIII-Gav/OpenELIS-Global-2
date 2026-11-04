package org.openelisglobal.systemuser.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.login.valueholder.LoginUser;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.person.valueholder.Person;
import org.openelisglobal.provider.form.ProviderUpsertForm;
import org.openelisglobal.provider.service.ProviderProfileFieldService;
import org.openelisglobal.provider.service.ProviderService;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.systemuser.valueholder.SystemUser;

@RunWith(MockitoJUnitRunner.class)
public class UserProfessionalServiceTest {
    @Mock
    private UserService userService;
    @Mock
    private PersonService personService;
    @Mock
    private ProviderService providerService;
    @Mock
    private ProviderProfileFieldService profileFieldService;
    @InjectMocks
    private UserProfessionalService service;
    private SystemUser user;
    private LoginUser login;
    private ProviderUpsertForm form;

    @Before
    public void setup() {
        user = new SystemUser();
        user.setFirstName("Ana");
        user.setLastName("Perez");
        login = new LoginUser();
        form = new ProviderUpsertForm();
        form.setProfessionalProfileCode("BIOLOGIST");
        form.setDni("12345678");
        form.setProfessionalInitials("AP");
        form.setProfileFieldValues(Map.of("LICENSE", "123"));
    }

    @Test
    public void accountCanBeSavedWithoutAProfessionalRecord() {
        service.save(login, true, user, true, List.of("3"), Map.of(), null, "1");
        verify(userService).updateLoginUser(login, true, user, true, List.of("3"), "1");
        verifyZeroInteractions(providerService);
    }

    @Test public void newProfessionalIsLinkedAndItsCustomFieldsAreSaved() {
        when(profileFieldService.normalizeAndValidateProfileFieldValues("BIOLOGIST", form.getProfileFieldValues())).thenReturn(form.getProfileFieldValues());
        when(providerService.insertOrUpdateProviderByFhirUuid(any(UUID.class), any(Provider.class), eq("1"), eq(form.getProfileFieldValues())))
            .thenAnswer(invocation -> { Provider provider = invocation.getArgument(1); provider.getPerson().setId("22"); return provider; });
        service.save(login, true, user, true, List.of("3"), Map.of(), form, "1");
        assertEquals("22", user.getLinkedProviderPersonId()); assertEquals("BIOLOGIST", user.getProfessionalProfileCode());
        ArgumentCaptor<Provider> saved = ArgumentCaptor.forClass(Provider.class);
        verify(providerService).insertOrUpdateProviderByFhirUuid(any(UUID.class), saved.capture(), eq("1"), eq(form.getProfileFieldValues()));
        assertEquals("Ana", saved.getValue().getPerson().getFirstName());
        verify(userService).saveUserLabUnitRoles(user, Map.of(), "1");
    }

    @Test
    public void existingProfessionalKeepsIdentityAndOtherCredentials() {
        Provider existing = new Provider();
        existing.setId("8");
        existing.setFhirUuid(UUID.randomUUID());
        existing.setNpi("NPI-77");
        Person person = new Person();
        person.setId("22");
        person.setCellPhone("555");
        existing.setPerson(person);
        user.setLinkedProviderPersonId("22");
        form.setProviderId("8");
        when(personService.getPersonById("22")).thenReturn(person);
        when(providerService.getProviderByPerson(person)).thenReturn(existing);
        when(profileFieldService.normalizeAndValidateProfileFieldValues("BIOLOGIST", form.getProfileFieldValues()))
                .thenReturn(form.getProfileFieldValues());
        when(providerService.insertOrUpdateProviderByFhirUuid(existing.getFhirUuid(), existing, "1",
                form.getProfileFieldValues())).thenReturn(existing);
        service.save(login, false, user, false, List.of("3"), Map.of(), form, "1");
        assertEquals("NPI-77", existing.getNpi());
        assertEquals("555", existing.getPerson().getCellPhone());
        assertEquals("22", user.getLinkedProviderPersonId());
    }

    @Test public void invalidProfessionalFieldsPreventSavingTheAccount() {
        when(profileFieldService.normalizeAndValidateProfileFieldValues("BIOLOGIST", form.getProfileFieldValues())).thenThrow(new IllegalArgumentException("Missing license"));
        assertThrows(IllegalArgumentException.class, () -> service.save(login, true, user, true, List.of("3"), Map.of(), form, "1"));
        verifyZeroInteractions(userService); verifyZeroInteractions(providerService);
    }

    @Test
    public void forgedProfessionalIdCannotUpdateAnotherRecord() {
        user.setLinkedProviderPersonId("22");
        form.setProviderId("99");
        Person person = new Person();
        person.setId("22");
        Provider provider = new Provider();
        provider.setId("8");
        provider.setPerson(person);
        when(personService.getPersonById("22")).thenReturn(person);
        when(providerService.getProviderByPerson(person)).thenReturn(provider);
        assertThrows(IllegalArgumentException.class,
                () -> service.save(login, false, user, false, List.of(), Map.of(), form, "1"));
        verifyZeroInteractions(userService);
    }
}
