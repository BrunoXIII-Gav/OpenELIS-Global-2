const normalized = (value) => String(value ?? "").trim();

// Initial values stay in browser memory; they are never added to the request.
export const captureAdditionalFieldBaseline = (row) => ({
  ...row,
  additionalFieldInitialValues: { ...(row.additionalFieldValues || {}) },
});

export const buildAdditionalFieldPatch = (row) => {
  const { additionalFieldInitialValues: initial, ...payload } = row;
  if (!initial) return payload;
  const values = row.additionalFieldValues || {};
  const keys = new Set([...Object.keys(initial), ...Object.keys(values)]);
  payload.additionalFieldValues = Object.fromEntries(
    [...keys]
      .filter((key) => normalized(values[key]) !== normalized(initial[key]))
      .map((key) => [key, values[key] ?? ""]),
  );
  payload.additionalFieldShadowValues = {};
  return payload;
};

export const resolveAdditionalFieldConflict = (row, conflict, keepDraft) => {
  const values = { ...(row.additionalFieldValues || {}) };
  if (!keepDraft) values[conflict.fieldKey] = conflict.currentValue ?? "";
  return {
    ...row,
    additionalFieldValues: values,
    additionalFieldShadowValues: { ...values },
    additionalFieldVersions: {
      ...(row.additionalFieldVersions || {}),
      [conflict.fieldKey]: conflict.currentVersion,
    },
    additionalFieldInitialValues: {
      ...(row.additionalFieldInitialValues || {}),
      [conflict.fieldKey]: conflict.currentValue ?? "",
    },
  };
};
