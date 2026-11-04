package org.openelisglobal.validation.annotations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.openelisglobal.patient.validator.PatientDocumentValidator;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PatientDocumentValidator.class)
@Documented
public @interface ValidPatientDocuments {
    String message() default "{error.patient.document.required}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
