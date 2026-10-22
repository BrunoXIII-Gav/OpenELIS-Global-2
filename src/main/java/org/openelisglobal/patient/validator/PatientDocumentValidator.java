package org.openelisglobal.patient.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.LinkedHashMap;
import java.util.Map;
import org.openelisglobal.patient.action.IPatientUpdate.PatientUpdateStatus;
import org.openelisglobal.patient.action.bean.PatientManagementInfo;
import org.openelisglobal.validation.annotations.ValidPatientDocuments;

/**
 * Document selection is required only when enabled; nonempty values always
 * follow their format.
 */
public class PatientDocumentValidator implements ConstraintValidator<ValidPatientDocuments, PatientManagementInfo> {
    @Override
    public boolean isValid(PatientManagementInfo info, ConstraintValidatorContext context) {
        Map<String, String> errors = validateDocuments(info);
        if (errors.isEmpty())
            return true;
        context.disableDefaultConstraintViolation();
        errors.forEach((field, code) -> context.buildConstraintViolationWithTemplate("{" + code + "}")
                .addPropertyNode(field).addConstraintViolation());
        return false;
    }

    public static Map<String, String> validateDocuments(PatientManagementInfo info) {
        Map<String, String> errors = new LinkedHashMap<>();
        // Linking a patient must not require rewriting or correcting their
        // registration.
        if (info == null || info.getPatientUpdateStatus() == PatientUpdateStatus.NO_ACTION)
            return errors;
        validate(errors, "dni", info.getDni(), info.getDniEnabled(), "[0-9]{1,8}", "error.patient.document.dni.format");
        validate(errors, "passportNumber", info.getPassportNumber(), info.getPassportEnabled(), "[A-Za-z0-9]{1,9}",
                "error.patient.document.passport.format");
        validate(errors, "foreignId", info.getForeignId(), info.getForeignIdEnabled(), "[A-Za-z0-9]{1,20}",
                "error.patient.document.foreign.format");
        return errors;
    }

    private static void validate(Map<String, String> errors, String field, String value, Boolean enabled,
            String pattern, String formatCode) {
        if (value == null || value.isBlank()) {
            if (Boolean.TRUE.equals(enabled))
                errors.put(field, "error.patient.document.required");
        } else if (!value.matches(pattern)) {
            errors.put(field, formatCode);
        }
    }
}
