export const auditModules = [
  "sample",
  "sampleitem",
  "patient",
  "result",
  "resultvalidation",
  "storage",
  "provider",
  "systemuser",
  "reports",
  "reportdefinition",
  "sampleadditionalfield",
  "testconfiguration",
  "siteinformation",
];

const message = (intl, key, fallback) =>
  intl.messages[key] ? intl.formatMessage({ id: key }) : fallback;

export const auditFieldLabel = (intl, item) => {
  if (
    item.fieldLabel &&
    item.fieldLabel !== item.field &&
    !item.fieldLabel.startsWith("fieldValue [")
  ) {
    return item.fieldLabel;
  }
  return message(
    intl,
    `audit.events.field.${item.entityName}.${item.field}`,
    message(
      intl,
      `audit.events.field.${item.field}`,
      intl.formatMessage({ id: "audit.events.field.unknown" }),
    ),
  );
};

export const auditRecordLabel = (intl, item) => {
  const type = message(
    intl,
    `audit.events.entity.${item.entityName}`,
    intl.formatMessage({ id: `audit.events.module.${item.module}` }),
  );
  return item.recordLabel ? `${type}: ${item.recordLabel}` : type;
};

export const auditValue = (intl, item, display, raw) => {
  const value = display ?? raw;
  if (value == null) return intl.formatMessage({ id: "audit.events.absent" });
  if (value === "") return intl.formatMessage({ id: "audit.events.empty" });
  const markers = {
    "[REDACTED]": "protectedValue",
    "[DOCUMENT_CHANGED]": "documentChanged",
    "[SIGNATURE_CHANGED]": "signatureChanged",
    "[REFERENCE_UNAVAILABLE]": "referenceUnavailable",
  };
  if (markers[value])
    return intl.formatMessage({ id: `audit.events.${markers[value]}` });
  if (value === true || value === "true")
    return intl.formatMessage({ id: "audit.events.yes" });
  if (value === false || value === "false")
    return intl.formatMessage({ id: "audit.events.no" });
  if (
    typeof value === "string" &&
    /(Date|Timestamp|At)$/.test(item.field) &&
    /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)
  ) {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime()))
      return `${intl.formatDate(date)} ${intl.formatTime(date)}`;
  }
  return message(
    intl,
    `audit.events.value.${item.field}.${value}`,
    String(value),
  );
};
