const STATIC_OPTION_TYPES = new Set(["SELECT", "RADIO", "MULTISELECT"]);

// Include retired choices only when displaying a value already stored on a record.
export const getAdditionalFieldOptions = (field, persistedValue = "") => {
  const selections = STATIC_OPTION_TYPES.has(
    String(field?.fieldType || "").toUpperCase(),
  )
    ? new Set(
        String(persistedValue || "")
          .split(",")
          .map((value) => value.trim())
          .filter(Boolean),
      )
    : new Set();
  return (field?.options || []).filter(
    (option) =>
      option && (option.active !== false || selections.has(option.optionKey)),
  );
};
