package org.openelisglobal.sample.validator;

import static org.junit.Assert.*;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Arrays;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openelisglobal.sample.bean.SampleOrderItem;
import org.openelisglobal.sample.form.SampleEditForm;
import org.openelisglobal.sample.form.SamplePatientEntryForm;

public class AutomaticOrderBeanValidationTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeClass
    public static void setupValidator() {
        factory = Validation.byDefaultProvider().configure().messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterClass
    public static void closeValidator() {
        factory.close();
    }

    @Test
    public void newOrderAcceptsAnUnassignedNumberAtTheRequestValidationBoundary() {
        var form = form();
        form.getSampleOrderItems().setLabNoGenerated(true);
        for (String value : Arrays.asList(null, "", "   ")) {
            form.getSampleOrderItems().setLabNo(value);
            assertTrue(
                    validator.validateProperty(form, "sampleOrderItems.labNo",
                            SamplePatientEntryForm.SamplePatientEntry.class).toString(),
                    validator.validateProperty(form, "sampleOrderItems.labNo",
                            SamplePatientEntryForm.SamplePatientEntry.class).isEmpty());
        }
    }

    @Test
    public void oldClientsWithoutTheGeneratedFlagAlsoReachAutomaticAssignment() {
        var form = form();
        form.getSampleOrderItems().setLabNo("");
        assertTrue(validator
                .validateProperty(form, "sampleOrderItems.labNo", SamplePatientEntryForm.SamplePatientEntry.class)
                .isEmpty());
    }

    @Test
    public void batchEntryStillRequiresAnExplicitNumber() {
        var form = form();
        for (String value : Arrays.asList(null, "", "   ")) {
            form.getSampleOrderItems().setLabNo(value);
            var errors = validator.validateProperty(form, "sampleOrderItems.labNo",
                    SamplePatientEntryForm.SamplePatientEntryBatch.class);
            assertEquals(1, errors.size());
            assertEquals("sampleOrderItems.labNo", errors.iterator().next().getPropertyPath().toString());
        }
    }

    @Test
    public void automaticEntryStillRequiresTheReceptionDate() {
        var form = form();
        form.getSampleOrderItems().setLabNoGenerated(true);
        var errors = validator.validateProperty(form, "sampleOrderItems.receivedDateForDisplay",
                SamplePatientEntryForm.SamplePatientEntry.class);
        assertEquals(1, errors.size());
        assertEquals("sampleOrderItems.receivedDateForDisplay", errors.iterator().next().getPropertyPath().toString());
    }

    @Test
    public void editingWithoutRenumberingKeepsItsExistingValidationBehavior() {
        assertTrue(
                validator.validateProperty(new SampleOrderItem(), "labNo", SampleEditForm.SampleEdit.class).isEmpty());
    }

    private SamplePatientEntryForm form() {
        var form = new SamplePatientEntryForm();
        form.setSampleOrderItems(new SampleOrderItem());
        return form;
    }
}
