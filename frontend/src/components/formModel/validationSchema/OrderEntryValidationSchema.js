import * as Yup from "yup";
import CreatePatientValidationSchema from "./CreatePatientValidationShema";

const getFixedFieldConfig = (sampleOrderItems, fieldKey) => {
  const configs = sampleOrderItems?.fixedFieldConfigs || [];
  return (
    configs.find(
      (config) => config?.fieldKey?.toLowerCase() === fieldKey?.toLowerCase(),
    ) || null
  );
};

const isFixedFieldRequired = (sampleOrderItems, fieldKey, fallback = false) => {
  const config = getFixedFieldConfig(sampleOrderItems, fieldKey);
  if (!config) {
    return fallback;
  }
  if (config.visible === false) {
    return false;
  }
  if (config.required != null) {
    return !!config.required;
  }
  return fallback;
};

const OrderEntryValidationSchema = Yup.object().shape({
  sampleXML: Yup.string().required("Sample is required"),
  patientProperties: CreatePatientValidationSchema,
  sampleOrderItems: Yup.object()
    .shape({
      labNo: Yup.string().required("Sample Lab Number is required"),
      referringSiteName: Yup.string(),
      referringSiteId: Yup.string(),
      providerLastName: Yup.string().test(
        "providerLastNameRequired",
        "Requester Last Name is required",
        function (value) {
          if (!isFixedFieldRequired(this.parent, "providerLastName", true)) {
            return true;
          }
          return !!(value || "").trim();
        },
      ),
      providerFirstName: Yup.string().test(
        "providerFirstNameRequired",
        "Requester First Name is required",
        function (value) {
          if (!isFixedFieldRequired(this.parent, "providerFirstName", true)) {
            return true;
          }
          return !!(value || "").trim();
        },
      ),
      providerEmail: Yup.string().email("Invalid Email"),
    })
    .test("referringSiteName", "Referring Site is required", function (value) {
      if (!isFixedFieldRequired(value, "referringSiteName", true)) {
        return true;
      }
      const { referringSiteName, referringSiteId } = value || {};
      return !!referringSiteName || !!referringSiteId;
    }),
});

export default OrderEntryValidationSchema;
