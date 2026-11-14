import React from "react";
import { Button, Checkbox, Tag, TextInput } from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";

const AdditionalFieldOptionsEditor = ({
  idPrefix,
  options = [],
  locked = false,
  allowOptionManagement = false,
  onAddOption,
  onOptionLabelChange,
  onRemoveOption,
  onOptionActiveChange,
}) => {
  const intl = useIntl();

  return (
    <div
      style={{
        backgroundColor: "#f4f4f4",
        borderRadius: "0.25rem",
        padding: "1rem",
      }}
    >
      <div style={{ marginBottom: "0.75rem" }}>
        <div style={{ fontWeight: 600, marginBottom: "0.25rem" }}>
          <FormattedMessage
            id="additional.fields.options"
            defaultMessage="Options"
          />
        </div>
        <div style={{ color: "#525252", fontSize: "0.875rem" }}>
          <FormattedMessage
            id="additional.fields.options.helper"
            defaultMessage="Add each option separately. The key is generated automatically from the label."
          />
        </div>
      </div>

      {options.length === 0 ? (
        <p style={{ margin: "0 0 1rem 0" }}>
          <FormattedMessage
            id="additional.fields.options.empty"
            defaultMessage="No options added yet."
          />
        </p>
      ) : null}

      {options.map((option, optionIndex) => (
        <div
          key={`${idPrefix}-option-${optionIndex}`}
          style={{
            alignItems: "center",
            backgroundColor: "#ffffff",
            borderRadius: "0.25rem",
            display: "flex",
            gap: "1rem",
            marginBottom: "0.75rem",
            padding: "0.75rem",
          }}
        >
          <div style={{ flex: 1 }}>
            <TextInput
              id={`${idPrefix}-option-label-${optionIndex}`}
              labelText={intl.formatMessage({
                id: "additional.fields.optionLabel",
                defaultMessage: "Option label",
              })}
              placeholder={intl.formatMessage({
                id: "additional.fields.optionLabel.placeholder",
                defaultMessage: "Example: Option A",
              })}
              value={option?.optionLabel || ""}
              readOnly={
                locked && (!allowOptionManagement || option?.id != null)
              }
              onChange={(event) =>
                onOptionLabelChange(optionIndex, event.target.value)
              }
            />
            <div style={{ marginTop: "0.5rem" }}>
              <span
                style={{
                  color: "#525252",
                  fontSize: "0.875rem",
                  marginRight: "0.5rem",
                }}
              >
                <FormattedMessage
                  id="additional.fields.optionKeyPreview"
                  defaultMessage="Saved key"
                />
              </span>
              <Tag type="gray">{option?.optionKey || "..."}</Tag>
            </div>
          </div>
          {allowOptionManagement ? (
            <Checkbox
              id={`${idPrefix}-option-active-${optionIndex}`}
              labelText={intl.formatMessage({
                id: "additional.fields.optionActive",
                defaultMessage: "Active option",
              })}
              checked={option?.active !== false}
              onChange={(_event, { checked }) =>
                onOptionActiveChange(optionIndex, checked)
              }
            />
          ) : null}
          <Button
            type="button"
            kind="ghost"
            size="sm"
            disabled={locked && (!allowOptionManagement || option?.id != null)}
            onClick={() => onRemoveOption(optionIndex)}
          >
            <FormattedMessage
              id="additional.fields.optionRemove"
              defaultMessage="Remove option"
            />
          </Button>
        </div>
      ))}

      <Button
        type="button"
        kind="secondary"
        size="sm"
        disabled={locked && !allowOptionManagement}
        onClick={onAddOption}
      >
        <FormattedMessage
          id="additional.fields.optionAdd"
          defaultMessage="Add option"
        />
      </Button>
    </div>
  );
};

export default AdditionalFieldOptionsEditor;
