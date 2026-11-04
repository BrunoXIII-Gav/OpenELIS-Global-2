import React from "react";
import { SelectItem } from "@carbon/react";
import { useIntl } from "react-intl";

// Keep an existing assignment visible without adding it to the eligible candidates.
export default function HistoricalUserSelectItem({
  fieldType,
  value,
  options,
}) {
  const intl = useIntl();
  if (
    fieldType !== "USER" ||
    value === "" ||
    value == null ||
    options.some((option) => String(option.optionKey) === String(value))
  )
    return null;
  return (
    <SelectItem
      value={value}
      text={intl.formatMessage({ id: "userField.historical" }, { id: value })}
      disabled
    />
  );
}
