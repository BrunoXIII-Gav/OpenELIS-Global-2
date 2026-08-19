import CreatePatientFormValues from "../formModel/innitialValues/CreatePatientFormValues";
import { createSampleOrderFormValues } from "../formModel/innitialValues/OrderEntryFormValues";

const ADDRESS_HIERARCHY_KEY_PATTERN = /^addressHierarchy_\d+$/;

const createBasePatientProperties = () => {
  const orderPatientDefaults = createSampleOrderFormValues().patientProperties;
  const patientFormDefaults = CreatePatientFormValues;

  return {
    ...orderPatientDefaults,
    ...patientFormDefaults,
    patientContact: {
      ...(orderPatientDefaults.patientContact || {}),
      ...(patientFormDefaults.patientContact || {}),
      person: {
        ...(orderPatientDefaults.patientContact?.person || {}),
        ...(patientFormDefaults.patientContact?.person || {}),
      },
    },
    patientAdditionalFieldValues: {},
  };
};

const BASE_PATIENT_PROPERTIES = createBasePatientProperties();

const ALLOWED_PATIENT_PROPERTY_KEYS = new Set([
  ...Object.keys(BASE_PATIENT_PROPERTIES),
  "addressHierarchy",
  "years",
  "months",
  "days",
]);

const cloneObject = (value) =>
  value && typeof value === "object" && !Array.isArray(value) ? { ...value } : {};

export const sanitizePatientProperties = (patientProperties = {}) => {
  const source = patientProperties || {};
  const sanitized = createBasePatientProperties();

  Object.entries(source).forEach(([key, value]) => {
    if (
      ALLOWED_PATIENT_PROPERTY_KEYS.has(key) ||
      ADDRESS_HIERARCHY_KEY_PATTERN.test(key)
    ) {
      sanitized[key] = value;
    }
  });

  sanitized.patientContact = {
    ...BASE_PATIENT_PROPERTIES.patientContact,
    ...cloneObject(source.patientContact),
    person: {
      ...BASE_PATIENT_PROPERTIES.patientContact.person,
      ...cloneObject(source.patientContact?.person),
    },
  };

  sanitized.patientAdditionalFieldValues = cloneObject(
    source.patientAdditionalFieldValues,
  );

  if (
    source.addressHierarchy &&
    typeof source.addressHierarchy === "object" &&
    !Array.isArray(source.addressHierarchy)
  ) {
    sanitized.addressHierarchy = { ...source.addressHierarchy };
  }

  return sanitized;
};
