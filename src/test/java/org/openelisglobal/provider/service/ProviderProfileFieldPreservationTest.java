package org.openelisglobal.provider.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openelisglobal.professionalprofile.form.ProfessionalProfileFieldDefinitionForm;
import org.openelisglobal.professionalprofile.service.ProfessionalProfileFieldConfigService;
import org.openelisglobal.provider.dao.ProviderProfileFieldValueDAO;
import org.openelisglobal.provider.valueholder.Provider;
import org.openelisglobal.provider.valueholder.ProviderProfileFieldValue;

@RunWith(MockitoJUnitRunner.class)
public class ProviderProfileFieldPreservationTest {
    @Mock
    private ProfessionalProfileFieldConfigService professionalProfileFieldConfigService;
    @Mock
    private ProviderProfileFieldValueDAO providerProfileFieldValueDAO;
    @InjectMocks
    private ProviderProfileFieldServiceImpl service;

    @Test
    public void editingActiveFieldsPreservesRetiredFieldsAndOtherProfessions() {
        Provider provider = new Provider();
        provider.setId("7");
        provider.setSysUserId("17");
        ProfessionalProfileFieldDefinitionForm active = new ProfessionalProfileFieldDefinitionForm();
        active.setFieldKey("license");
        active.setFieldType("TEXT");
        active.setActive(true);
        ProviderProfileFieldValue oldLicense = row("license", "old");
        ProviderProfileFieldValue retired = row("retired-license", "historical");
        when(professionalProfileFieldConfigService.getFieldsForProfile("MEDICAL_DOCTOR", false))
                .thenReturn(List.of(active));
        when(providerProfileFieldValueDAO.findByProviderIdAndProfessionalProfileCode(7, "MEDICAL_DOCTOR"))
                .thenReturn(List.of(oldLicense, retired));

        service.saveProfileFieldValues(provider, "medical_doctor", Map.of("license", "new"));

        verify(providerProfileFieldValueDAO).delete(oldLicense);
        verify(providerProfileFieldValueDAO, never()).delete(retired);
        verify(providerProfileFieldValueDAO, never()).deleteByProviderId(anyInt());
        verify(providerProfileFieldValueDAO, never()).findByProviderIdAndProfessionalProfileCode(7, "BIOLOGIST");
        ArgumentCaptor<ProviderProfileFieldValue> inserted = ArgumentCaptor.forClass(ProviderProfileFieldValue.class);
        verify(providerProfileFieldValueDAO).insert(inserted.capture());
        assertEquals("MEDICAL_DOCTOR", inserted.getValue().getProfessionalProfileCode());
        assertEquals("new", inserted.getValue().getFieldValue());
        assertEquals("17", inserted.getValue().getSysUserId());
    }

    private ProviderProfileFieldValue row(String key, String text) {
        ProviderProfileFieldValue value = new ProviderProfileFieldValue();
        value.setProviderId(7);
        value.setProfessionalProfileCode("MEDICAL_DOCTOR");
        value.setFieldKey(key);
        value.setFieldValue(text);
        return value;
    }
}
