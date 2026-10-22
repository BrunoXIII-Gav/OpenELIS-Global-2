package org.openelisglobal.sample.validator;

import static org.junit.Assert.*;

import org.junit.Test;
import org.openelisglobal.sample.form.SamplePatientEntryForm;
import org.springframework.validation.BeanPropertyBindingResult;

public class SampleCugAssignmentValidatorTest {
    @Test
    public void previewRequiresItsDisplayedCode() {
        assertTrue(validate("cug='' cugProvisional='true'").hasErrors());
        assertFalse(validate("cug='5000.1' cugProvisional='true'").hasErrors());
    }

    @Test
    public void legacyRequestsStillRequireACugAndRejectMalformedCodes() {
        assertTrue(validate("cug=''").hasErrors());
        assertTrue(validate("cug='invalid' cugProvisional='true'").hasErrors());
        assertFalse(validate("cug='5000.1'").hasErrors());
    }

    private BeanPropertyBindingResult validate(String attributes) {
        var form = new SamplePatientEntryForm();
        form.setSampleXML(
                "<samples><sample sampleID='1' tests='1' panels='' date='' time='' " + attributes + "/></samples>");
        var errors = new BeanPropertyBindingResult(form, "form");
        new SamplePatientEntryFormValidator().validate(form, errors);
        return errors;
    }
}
