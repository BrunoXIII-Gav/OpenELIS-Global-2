// Presentation only: never use these helpers to normalize the save payload.
export const hasSummaryValue = (value) => {
  if (value === null || value === undefined) return false;
  if (Array.isArray(value)) return value.some(hasSummaryValue);
  return !["", "none", "n/a"].includes(String(value).trim().toLowerCase());
};

export const summaryBoolean = (value, intl) => {
  if (!hasSummaryValue(value)) return null;
  const normalized = String(value).trim().toLowerCase();
  if (["true", "y", "yes", "active", "orderable"].includes(normalized)) {
    return intl.formatMessage({ id: "label.yes" });
  }
  if (["false", "n", "no", "inactive", "not orderable"].includes(normalized)) {
    return intl.formatMessage({ id: "label.no" });
  }
  return value;
};
