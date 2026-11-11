import * as Yup from "yup";

import {
  buildOrderConditionContext as buildDynamicConditionContext,
  hasRequiredOrderFieldValue as hasRequiredDynamicFieldValue,
  isOrderFieldRequired as isDynamicFieldRequired,
  isOrderFieldVisible as isDynamicFieldVisible,
} from "../../utils/orderFieldRules";

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

const sampleOrderItemsSchema = Yup.object()
  .shape({
    labNo: Yup.string().required("El número de laboratorio es obligatorio"),
    referringSiteName: Yup.string(),
    referringSiteId: Yup.string(),
    providerId: Yup.string(),
    providerPersonId: Yup.string(),
    providerLastName: Yup.string().test(
      "providerLastNameRequired",
      "Requester Last Name is required",
      function (value) {
        if (!isFixedFieldRequired(this.parent, "providerLastName", false)) {
          return true;
        }
        return !!(value || "").trim();
      },
    ),
    providerFirstName: Yup.string().test(
      "providerFirstNameRequired",
      "Requester First Name is required",
      function (value) {
        if (!isFixedFieldRequired(this.parent, "providerFirstName", false)) {
          return true;
        }
        return !!(value || "").trim();
      },
    ),
    providerEmail: Yup.string().email("Invalid Email"),
    providerCmp: Yup.string(),
    providerRne: Yup.string(),
    providerDni: Yup.string(),
    providerSpecialty: Yup.string(),
  })
  .test("referringSiteName", "Referring Site is required", function (value) {
    if (!isFixedFieldRequired(value, "referringSiteName", false)) {
      return true;
    }
    const { referringSiteName, referringSiteId } = value || {};
    return !!referringSiteName || !!referringSiteId;
  })
  .test(
    "providerSelectionRequired",
    "Search Requester is required",
    function (value) {
      const selectedProviderId = value?.providerId || value?.providerPersonId;
      if (String(selectedProviderId || "").trim()) {
        return true;
      }
      const providerFirstName = String(value?.providerFirstName || "").trim();
      const providerLastName = String(value?.providerLastName || "").trim();
      return !!providerFirstName && !!providerLastName;
    },
  )
  .test(
    "requiredOrderAdditionalFields",
    "Order additional field is required",
    function (sampleOrderItems) {
      const fields = sampleOrderItems?.additionalFields || [];
      if (!Array.isArray(fields) || fields.length === 0) {
        return true;
      }

      const context = buildDynamicConditionContext(
        sampleOrderItems,
        this?.from?.[1]?.value,
      );
      const missingField = fields.find((field) => {
        if (!isDynamicFieldVisible(field, context)) {
          return false;
        }
        if (!isDynamicFieldRequired(field, context)) {
          return false;
        }
        return !hasRequiredDynamicFieldValue(field, sampleOrderItems);
      });

      if (!missingField) {
        return true;
      }

      const fieldType = String(missingField?.fieldType || "TEXT").toUpperCase();
      const path =
        fieldType === "DOCUMENT"
          ? `sampleOrderItems.additionalFieldFiles.${missingField.fieldKey}`
          : `sampleOrderItems.additionalFieldValues.${missingField.fieldKey}`;

      return this.createError({
        path,
        message: `${missingField.displayName || "Field"} is required`,
      });
    },
  );

const ModifyOrderEntryValidationSchema = Yup.object().shape({
  sampleOrderItems: sampleOrderItemsSchema,
});

export default ModifyOrderEntryValidationSchema;
