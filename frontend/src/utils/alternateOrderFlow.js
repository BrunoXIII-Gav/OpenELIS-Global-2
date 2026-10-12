const WORKFLOW_NODE = "workflow";
const ALTERNATE_ORDER_FLOW_NODE = "alternateOrderFlow";
const TRIGGER_VALUE_NODE = "triggerValue";

const parseMetadataJson = (metadataJson) => {
  if (!metadataJson) {
    return {};
  }

  try {
    return JSON.parse(metadataJson);
  } catch (_error) {
    return {};
  }
};

const isTruthyValue = (value) => {
  const normalized = String(value || "").trim().toLowerCase();
  return normalized === "true" || normalized === "yes" || normalized === "1";
};

const getTriggerFields = (sampleOrderItems) => {
  const fields = sampleOrderItems?.additionalFields || [];
  return fields.filter((field) => {
    const metadata = parseMetadataJson(field?.metadataJson);
    return metadata?.[WORKFLOW_NODE]?.[ALTERNATE_ORDER_FLOW_NODE] === true;
  });
};

const resolveFieldValue = (sampleOrderItems, field) => {
  const currentValue =
    sampleOrderItems?.additionalFieldValues?.[field?.fieldKey] ?? null;
  if (
    currentValue !== null &&
    currentValue !== undefined &&
    String(currentValue).trim() !== ""
  ) {
    return currentValue;
  }
  return field?.defaultValue ?? "";
};

const doesFieldActivateAlternateOrderFlow = (sampleOrderItems, field) => {
  const metadata = parseMetadataJson(field?.metadataJson);
  const configuredTriggerValue =
    metadata?.[WORKFLOW_NODE]?.[TRIGGER_VALUE_NODE] ?? null;
  const currentValue = resolveFieldValue(sampleOrderItems, field);

  if (
    configuredTriggerValue !== null &&
    configuredTriggerValue !== undefined &&
    String(configuredTriggerValue).trim() !== ""
  ) {
    return (
      String(currentValue || "").trim().toLowerCase() ===
      String(configuredTriggerValue).trim().toLowerCase()
    );
  }

  if (String(field?.fieldType || "").toUpperCase() === "BOOLEAN") {
    return isTruthyValue(currentValue);
  }

  return String(currentValue || "").trim() !== "";
};

export const isAlternateOrderFlowSelected = (sampleOrderItems) => {
  const triggerFields = getTriggerFields(sampleOrderItems);
  return triggerFields.some((field) =>
    doesFieldActivateAlternateOrderFlow(sampleOrderItems, field),
  );
};

export const getAlternateOrderFlowTriggerFields = (sampleOrderItems) =>
  getTriggerFields(sampleOrderItems);
