import * as Yup from "yup";

const getValidationMessage = (intl, id, defaultMessage) =>
  intl?.formatMessage
    ? intl.formatMessage({ id, defaultMessage })
    : defaultMessage;

const CreatePatientValidationSchema = (intl) =>
  Yup.object().shape({
    subjectNumber: Yup.string(),
    nationalId: Yup.string(),
    primaryPatientIdentifierType: Yup.string(),
    email: Yup.string().email(
      getValidationMessage(
        intl,
        "patient.validation.email.invalid",
        "Patient Email Must Be Valid",
      ),
    ),
    birthDateForDisplay: Yup.string().test(
      "valid-date",
      "Invalid date format",
      function (value) {
        if (!value) {
          return true;
        }
        const dateFormat = /^\d{2}\/\d{2}\/\d{4}$/;
        if (!value.match(dateFormat)) {
          return false;
        }
        const [day, month, year] = value.split("/");
        const date = new Date(`${year}-${month}-${day}`);
        const date2 = new Date(`${year}-${day}-${month}`);

        const validDate1 = date instanceof Date && !isNaN(date);
        const validDate2 = date2 instanceof Date && !isNaN(date2);

        return validDate1 || validDate2;
      },
    ),
    patientContact: Yup.object().shape({
      person: Yup.object().shape({
        email: Yup.string().email(
          getValidationMessage(
            intl,
            "patient.validation.contactEmail.invalid",
            "Contact Email Must Be Valid",
          ),
        ),
      }),
    }),
    gender: Yup.string(),
  });

export default CreatePatientValidationSchema;
