import React from "react";
import { TextInput } from "@carbon/react";
import { useIntl } from "react-intl";

export default function CugSuffixInput({
  id,
  originalCug,
  patientPrefix,
  value,
  onChange,
  readOnly = false,
}) {
  const intl = useIntl();
  const original = String(originalCug || "").trim();
  const separator = original.lastIndexOf(".");
  const prefix =
    separator > 0 ? original.substring(0, separator) : patientPrefix || "";
  const start = `${prefix}.`;
  const current = String(value || "");
  const suffix = current.startsWith(start)
    ? current.substring(start.length)
    : "";

  return (
    <div style={{ display: "flex", alignItems: "center", gap: "0.25rem" }}>
      <span aria-label={intl.formatMessage({ id: "patient.cug.prefix.label" })}>
        {prefix ? start : "—"}
      </span>
      <TextInput
        id={id}
        labelText={intl.formatMessage({ id: "sample.cug.suffix.label" })}
        hideLabel
        size="sm"
        value={suffix}
        inputMode="numeric"
        pattern="[1-9][0-9]*"
        maxLength={19}
        readOnly={readOnly || !prefix}
        onChange={(event) => {
          const next = event.target.value;
          if (!readOnly && prefix && /^[0-9]*$/.test(next)) {
            onChange(`${prefix}.${next}`);
          }
        }}
      />
    </div>
  );
}
