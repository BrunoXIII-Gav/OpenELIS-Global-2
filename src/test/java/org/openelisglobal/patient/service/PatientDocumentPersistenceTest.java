package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.patientidentity.service.PatientIdentityService;
import org.openelisglobal.patientidentity.valueholder.PatientIdentity;
import org.openelisglobal.patientidentitytype.service.PatientIdentityTypeService;
import org.openelisglobal.patientidentitytype.util.PatientIdentityTypeMap;
import org.openelisglobal.patientidentitytype.valueholder.PatientIdentityType;
import org.openelisglobal.spring.util.SpringContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

public class PatientDocumentPersistenceTest {
    private PatientServiceImpl service;
    private PatientIdentityService identities;
    private PatientIdentityTypeService types;
    private GenericApplicationContext context;
    private Object previousContext;
    private Object previousFactory;

    @Before
    public void setup() {
        previousContext = ReflectionTestUtils.getField(SpringContext.class, "context");
        previousFactory = ReflectionTestUtils.getField(SpringContext.class, "factory");
        types = mock(PatientIdentityTypeService.class);
        var typeList = new ArrayList<PatientIdentityType>();
        for (String name : List.of("DNI", "PASSPORT", "FOREIGN_ID", "NATIONAL_ID_SOURCE", "GUID")) {
            var type = new PatientIdentityType();
            type.setId(Integer.toString(typeList.size() + 1));
            type.setIdentityType(name);
            typeList.add(type);
        }
        when(types.getAllPatientIdenityTypes()).thenReturn(typeList);
        context = new GenericApplicationContext();
        context.getBeanFactory().registerSingleton("patientIdentityTypeService", types);
        context.refresh();
        new SpringContext().setApplicationContext(context);
        PatientIdentityTypeMap.reset();
        identities = mock(PatientIdentityService.class);
        service = new PatientServiceImpl();
        ReflectionTestUtils.setField(service, "patientIdentityService", identities);
    }

    @After
    public void cleanup() {
        PatientIdentityTypeMap.reset();
        ReflectionTestUtils.setField(SpringContext.class, "context", previousContext);
        ReflectionTestUtils.setField(SpringContext.class, "factory", previousFactory);
        context.close();
    }

    @Test
    public void newPatientWithNoPreviousIdentitiesPersistsAllDocumentsAndPrimarySelection() {
        var info = information(PatientUpdateStatus.ADD);
        service.persistIdentityTypes(info, patient(), "42");
        var captured = ArgumentCaptor.forClass(PatientIdentity.class);
        verify(identities, times(5)).insert(captured.capture());
        var saved = captured.getAllValues();
        var map = PatientIdentityTypeMap.getInstance();
        assertEquals("77711133", map.getIdentityValue(saved, "DNI"));
        assertEquals("AB123456", map.getIdentityValue(saved, "PASSPORT"));
        assertEquals("001234567", map.getIdentityValue(saved, "FOREIGN_ID"));
        assertEquals("PASSPORT", map.getIdentityValue(saved, "NATIONAL_ID_SOURCE"));
        assertEquals("patient-guid", map.getIdentityValue(saved, "GUID"));
        for (var identity : saved) {
            assertEquals("11", identity.getPatientId());
            assertEquals("42", identity.getSysUserId());
        }
        verify(identities, never()).update(any());
    }

    @Test
    public void existingPatientWithNoIdentitiesCanBeRepairedBySavingConfirmedDocumentDetails() {
        var info = information(PatientUpdateStatus.UPDATE);
        service.persistIdentityType(info.getDni(), "DNI", info, patient(), "42");
        var captured = ArgumentCaptor.forClass(PatientIdentity.class);
        verify(identities).insert(captured.capture());
        assertEquals("77711133", captured.getValue().getIdentityData());
        assertEquals("11", captured.getValue().getPatientId());
    }

    @Test
    public void updatingAndRemovingAnExistingDocumentDoesNotCreateADuplicateIdentity() {
        var info = information(PatientUpdateStatus.UPDATE);
        var stored = new PatientIdentity();
        stored.setIdentityTypeId("1");
        stored.setIdentityData("77711133");
        info.setPatientIdentities(List.of(stored));
        service.persistIdentityType("88822244", "DNI", info, patient(), "42");
        assertEquals("88822244", stored.getIdentityData());
        service.persistIdentityType("", "DNI", info, patient(), "42");
        assertEquals("", stored.getIdentityData());
        verify(identities, times(2)).update(stored);
        verify(identities, never()).insert(any());
    }

    @Test
    public void blankDocumentsDoNotCreateIdentitiesOrTypes() {
        var info = information(PatientUpdateStatus.ADD);
        service.persistIdentityType("", "DNI", info, patient(), "42");
        verifyZeroInteractions(identities, types);
    }

    private PatientManagementInfo information(PatientUpdateStatus status) {
        var info = new PatientManagementInfo();
        info.setPatientUpdateStatus(status);
        info.setPatientIdentities(new ArrayList<>());
        info.setDni("77711133");
        info.setPassportNumber("AB123456");
        info.setForeignId("001234567");
        info.setPrimaryPatientIdentifierType("PASSPORT");
        info.setGuid("patient-guid");
        return info;
    }

    private Patient patient() {
        var patient = new Patient();
        patient.setId("11");
        return patient;
    }
}
