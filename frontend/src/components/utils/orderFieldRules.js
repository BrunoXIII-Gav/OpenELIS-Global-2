import { evaluateConditions } from "./fieldConditions";

export const parseOrderFieldMetadata = (field) => {
  try {
    return JSON.parse(field?.metadataJson || "{}");
  } catch (_error) {
    return {};
  }
};

export const getOrderFieldValue = (field, values = {}) =>
  values?.[field.fieldKey] ?? field.defaultValue ?? "";

export const buildOrderConditionContext = (items = {}, root = {}) => {
  const additionalValues = { ...items.additionalFieldValues };
  (items.additionalFields || []).forEach((field) => {
    if (!field?.fieldKey) return;
    const value = getOrderFieldValue(field, additionalValues);
    additionalValues[field.fieldKey] =
      String(field.fieldType).toUpperCase() === "BOOLEAN" && value === ""
        ? "false"
        : value;
  });
  return {
    ...items,
    ...items.providerProfileFieldValues,
    ...additionalValues,
    rememberSiteAndRequester: root.rememberSiteAndRequester ? "true" : "false",
  };
};

export const isOrderFieldVisible = (field, context) => {
  if (!field?.fieldKey || field.active === false) return false;
  const rules = parseOrderFieldMetadata(field)?.rules || {};
  return evaluateConditions(rules.visibleWhen, rules.logic, context);
};

export const isOrderFieldRequired = (field, context) => {
  if (field?.required) return true;
  const rules = parseOrderFieldMetadata(field)?.rules || {};
  return (
    Array.isArray(rules.requiredWhen) &&
    rules.requiredWhen.length > 0 &&
    evaluateConditions(rules.requiredWhen, rules.logic, context)
  );
};

export const hasRequiredOrderFieldValue = (field, items = {}) => {
  const type = String(field.fieldType || "TEXT").toUpperCase();
  if (type === "DOCUMENT") {
    const file = items.additionalFieldFiles?.[field.fieldKey];
    return !!(
      file &&
      file.deleteFile !== true &&
      (file.base64Content || file.uploadToken || file.fileName)
    );
  }
  const value = String(getOrderFieldValue(field, items.additionalFieldValues));
  if (type === "BOOLEAN") return value.trim().toLowerCase() === "true";
  if (type === "MULTISELECT") {
    return value.split(",").some((selection) => selection.trim() !== "");
  }
  return value.trim() !== "";
};
