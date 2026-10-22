package org.openelisglobal.patient.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.patient.valueholder.Patient;
import org.openelisglobal.patientadditionalfield.service.PatientAdditionalFieldService;
import org.openelisglobal.person.service.PersonService;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.openelisglobal.sample.service.SampleCugService;
import org.openelisglobal.validation.annotations.ValidPatientDocuments;
import org.springframework.test.util.ReflectionTestUtils;

public class PatientDocumentValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeClass
    public static void setup() {
        factory = Validation.byDefaultProvider().configure().messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterClass
    public static void cleanup() {
        factory.close();
    }

    @Test
    public void eachSelectedDocumentIsRequiredEvenWhenAnotherIsThePrimaryIdentifier() {
        var info = new PatientManagementInfo();
        info.setDniEnabled(true);
        info.setDni("00123456");
        info.setPrimaryPatientIdentifierType("DNI");
        info.setPassportEnabled(true);
        info.setForeignIdEnabled(true);
        assertEquals(Set.of("passportNumber", "foreignId"), invalidFields(info));
        info.setPassportEnabled(false);
        info.setForeignIdEnabled(false);
        assertTrue(invalidFields(info).isEmpty());
        info.setDni("");
        assertEquals(Set.of("dni"), invalidFields(info));
    }

    @Test
    public void dniAcceptsOnlyAsciiDigitsUpToEightAndPreservesLeadingZeros() {
        var info = new PatientManagementInfo();
        for (String valid : List.of("0", "00123456", "12345678")) {
            info.setDni(valid);
            assertTrue(invalidFields(info).isEmpty());
            assertEquals(valid, info.getDni());
        }
        for (String invalid : List.of("123456789", "12A45678", "12-34567", "12 34567", "１２３４")) {
            info.setDni(invalid);
            assertEquals(Set.of("dni"), invalidFields(info));
        }
    }

    @Test
    public void passportAndForeignIdRequireAlphanumericValuesWithinTheirRespectiveLimits() {
        var info = new PatientManagementInfo();
        info.setPassportNumber("Ab1234567");
        info.setForeignId("AB123456789012345678");
        assertTrue(invalidFields(info).isEmpty());
        info.setPassportNumber("Ab12345678");
        info.setForeignId("AB1234567890123456789");
        assertEquals(Set.of("passportNumber", "foreignId"), invalidFields(info));
        for (String invalid : List.of("A/B", "A-B", "A B", "A_B")) {
            info.setPassportNumber(invalid);
            info.setForeignId(invalid);
            assertEquals(Set.of("passportNumber", "foreignId"), invalidFields(info));
        }
    }

    @Test
    public void omittedFlagsAndUnselectedEmptyDocumentsRemainCompatibleWithExistingClients() {
        var info = new PatientManagementInfo();
        info.setNationalId("legacy-id");
        assertTrue(invalidFields(info).isEmpty());
        info.setDniEnabled(false);
        info.setPassportEnabled(false);
        info.setForeignIdEnabled(false);
        assertTrue(invalidFields(info).isEmpty());
        info.setDni("invalid");
        assertEquals(Set.of("dni"), invalidFields(info));
    }

    @Test
    public void linkingAnExistingPatientDoesNotForceAnUnrelatedDemographicCorrection() {
        var info = new PatientManagementInfo();
        info.setPatientPK("11");
        info.setPatientUpdateStatus(PatientUpdateStatus.NO_ACTION);
        info.setDni("legacy-id");
        assertTrue(invalidFields(info).isEmpty());
        info.setPatientUpdateStatus(PatientUpdateStatus.UPDATE);
        assertEquals(Set.of("dni"), invalidFields(info));
    }

    @Test
    public void invalidDocumentsCannotPersistOrAllocateAPatientCugPrefix() {
        var service = new PatientServiceImpl();
        var fields = mock(PatientAdditionalFieldService.class);
        var persons = mock(PersonService.class);
        var cugs = mock(SampleCugService.class);
        when(fields.getFixedFieldConfigs()).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "patientAdditionalFieldService", fields);
        ReflectionTestUtils.setField(service, "personService", persons);
        ReflectionTestUtils.setField(service, "sampleCugService", cugs);
        var info = new PatientManagementInfo();
        info.setPatientUpdateStatus(PatientUpdateStatus.ADD);
        info.setDniEnabled(true);
        try {
            service.persistPatientData(info, new Patient(), "42");
            fail("Selected empty document must be rejected before persistence");
        } catch (IllegalArgumentException expected) {
            assertEquals("error.patient.document.required", expected.getMessage());
        }
        verifyZeroInteractions(persons, cugs);
    }

    @Test
    public void documentConstraintIsActiveForOrderAndBatchRegistrationGroups() {
        var groups = validator.getConstraintsForClass(PatientManagementInfo.class).getConstraintDescriptors().stream()
                .filter(c -> c.getAnnotation() instanceof ValidPatientDocuments).findFirst().orElseThrow().getGroups();
        assertTrue(groups.contains(SamplePatientEntryForm.SamplePatientEntry.class));
        assertTrue(groups.contains(SamplePatientEntryForm.SamplePatientEntryBatch.class));
    }

    private Set<String> invalidFields(PatientManagementInfo info) {
        return validator.validate(info).stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
