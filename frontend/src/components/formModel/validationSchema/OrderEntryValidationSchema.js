import * as Yup from "yup";
import CreatePatientValidationSchema from "./CreatePatientValidationShema";

const DEFAULT_CONDITION_OPERATOR = "equals";
const DEFAULT_CONDITION_LOGIC = "ALL";

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

const parseFieldMetadata = (field) => {
  if (!field?.metadataJson) {
    return {};
  }
  try {
    return JSON.parse(field.metadataJson);
  } catch (_error) {
    return {};
  }
};

const evaluateSingleCondition = (condition, context) => {
  if (!condition || !condition.fieldKey) {
    return false;
  }

  const operator = (
    condition.operator || DEFAULT_CONDITION_OPERATOR
  ).toLowerCase();
  const leftValue = String(context?.[condition.fieldKey] ?? "").trim();
  const rightValue = String(condition.value ?? "").trim();
  const rightValues = Array.isArray(condition.values)
    ? condition.values.map((value) => String(value ?? "").trim())
    : rightValue
      ? [rightValue]
      : [];

  switch (operator) {
    case "equals":
      return leftValue.toLowerCase() === rightValue.toLowerCase();
    case "notequals":
      return leftValue.toLowerCase() !== rightValue.toLowerCase();
    case "in":
      return rightValues.some(
        (value) => leftValue.toLowerCase() === value.toLowerCase(),
      );
    case "notin":
      return rightValues.every(
        (value) => leftValue.toLowerCase() !== value.toLowerCase(),
      );
    case "hasvalue":
      return !!leftValue;
    case "istrue":
      return ["true", "yes", "1"].includes(leftValue.toLowerCase());
    case "isfalse":
      return ["false", "no", "0"].includes(leftValue.toLowerCase());
    default:
      return false;
  }
};

const evaluateConditions = (conditions, logic, context) => {
  if (!Array.isArray(conditions) || conditions.length === 0) {
    return true;
  }

  const useAny =
    String(logic || DEFAULT_CONDITION_LOGIC).toUpperCase() === "ANY";
  if (useAny) {
    return conditions.some((condition) =>
      evaluateSingleCondition(condition, context),
    );
  }
  return conditions.every((condition) =>
    evaluateSingleCondition(condition, context),
  );
};

const buildDynamicConditionContext = (sampleOrderItems, rootValues) => ({
  ...(sampleOrderItems?.additionalFieldValues || {}),
  priority: sampleOrderItems?.priority,
  requestDate: sampleOrderItems?.requestDate,
  requestTime: sampleOrderItems?.requestTime,
  receivedDateForDisplay: sampleOrderItems?.receivedDateForDisplay,
  receivedTime: sampleOrderItems?.receivedTime,
  nextVisitDate: sampleOrderItems?.nextVisitDate,
  referringSiteName: sampleOrderItems?.referringSiteName,
  referringSiteDepartmentId: sampleOrderItems?.referringSiteDepartmentId,
  provisionalClinicalDiagnosis: sampleOrderItems?.provisionalClinicalDiagnosis,
  providerFirstName: sampleOrderItems?.providerFirstName,
  providerLastName: sampleOrderItems?.providerLastName,
  providerWorkPhone: sampleOrderItems?.providerWorkPhone,
  providerFax: sampleOrderItems?.providerFax,
  providerEmail: sampleOrderItems?.providerEmail,
  providerCmp: sampleOrderItems?.providerCmp,
  providerRne: sampleOrderItems?.providerRne,
  providerDni: sampleOrderItems?.providerDni,
  providerSpecialty: sampleOrderItems?.providerSpecialty,
  paymentOptionSelection: sampleOrderItems?.paymentOptionSelection,
  testLocationCode: sampleOrderItems?.testLocationCode,
  otherLocationCode: sampleOrderItems?.otherLocationCode,
  rememberSiteAndRequester: rootValues?.rememberSiteAndRequester
    ? "true"
    : "false",
});

const isDynamicFieldVisible = (field, context) => {
  if (!field || !field.fieldKey || field.active === false) {
    return false;
  }
  const metadata = parseFieldMetadata(field);
  const rules = metadata?.rules || {};
  return evaluateConditions(
    rules.visibleWhen,
    rules.logic || DEFAULT_CONDITION_LOGIC,
    context,
  );
};

const isDynamicFieldRequired = (field, context) => {
  if (field?.required) {
    return true;
  }

  const metadata = parseFieldMetadata(field);
  const rules = metadata?.rules || {};
  if (!Array.isArray(rules.requiredWhen) || rules.requiredWhen.length === 0) {
    return false;
  }

  return evaluateConditions(
    rules.requiredWhen,
    rules.logic || DEFAULT_CONDITION_LOGIC,
    context,
  );
};

const hasRequiredDynamicFieldValue = (field, sampleOrderItems) => {
  const fieldType = String(field?.fieldType || "TEXT").toUpperCase();
  const values = sampleOrderItems?.additionalFieldValues || {};
  const files = sampleOrderItems?.additionalFieldFiles || {};

  if (fieldType === "DOCUMENT") {
    const filePayload = files[field.fieldKey];
    if (!filePayload || filePayload.deleteFile === true) {
      return false;
    }
    return !!(
      filePayload.base64Content ||
      filePayload.uploadToken ||
      filePayload.fileName
    );
  }

  const rawValue = values[field.fieldKey];
  const value =
    rawValue == null || rawValue === ""
      ? field?.defaultValue || ""
      : String(rawValue);
  return value.trim() !== "";
};

const OrderEntryValidationSchema = Yup.object().shape({
  sampleXML: Yup.string().required("Sample is required"),
  patientProperties: CreatePatientValidationSchema,
  sampleOrderItems: Yup.object()
    .shape({
      labNo: Yup.string().required("Sample Lab Number is required"),
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

        const fieldType = String(
          missingField?.fieldType || "TEXT",
        ).toUpperCase();
        const path =
          fieldType === "DOCUMENT"
            ? `sampleOrderItems.additionalFieldFiles.${missingField.fieldKey}`
            : `sampleOrderItems.additionalFieldValues.${missingField.fieldKey}`;

        return this.createError({
          path,
          message: `${missingField.displayName || "Field"} is required`,
        });
      },
    ),
});

export default OrderEntryValidationSchema;
