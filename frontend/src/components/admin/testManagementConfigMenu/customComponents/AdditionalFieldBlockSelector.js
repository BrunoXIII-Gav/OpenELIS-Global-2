import React, { useState } from "react";
import { Button, ComboBox, TextInput } from "@carbon/react";
import { Add } from "@carbon/icons-react";
import { useIntl } from "react-intl";
import { blockKey } from "../additionalFieldLayoutUtils";

export default function AdditionalFieldBlockSelector({
  id,
  value,
  blocks,
  disabled,
  onChange,
}) {
  const intl = useIntl();
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const message = (key) =>
    intl.formatMessage({ id: `test.additionalFields.block.${key}` });
  const cancel = () => {
    setCreating(false);
    setName("");
    setError("");
  };
  const create = () => {
    const next = name.trim();
    if (!next) {
      setError(message("nameRequired"));
      return;
    }
    if (blocks.some((block) => blockKey(block) === blockKey(next))) {
      setError(message("alreadyExists"));
      return;
    }
    onChange(next);
    cancel();
  };
  return (
    <div className="test-additional-fields__block-selector">
      <ComboBox
        id={id}
        titleText={intl.formatMessage({
          id: "test.additionalFields.blockName",
        })}
        items={blocks}
        itemToString={(item) => item || ""}
        selectedItem={value || null}
        shouldFilterItem={({ item, inputValue }) =>
          blockKey(item).includes(blockKey(inputValue))
        }
        onChange={({ selectedItem }) => {
          if (selectedItem) onChange(selectedItem);
        }}
        disabled={disabled || creating}
      />
      {!disabled && !creating && (
        <Button
          type="button"
          kind="ghost"
          size="sm"
          renderIcon={Add}
          onClick={() => setCreating(true)}
        >
          {message("create")}
        </Button>
      )}
      {!disabled && creating && (
        <div className="test-additional-fields__new-block">
          <TextInput
            id={`${id}-new`}
            labelText={message("newName")}
            value={name}
            autoFocus
            invalid={Boolean(error)}
            invalidText={error}
            onChange={(event) => {
              setName(event.target.value);
              setError("");
            }}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                create();
              }
              if (event.key === "Escape") {
                event.preventDefault();
                cancel();
              }
            }}
          />
          <Button type="button" kind="secondary" size="sm" onClick={create}>
            {message("confirmCreate")}
          </Button>
          <Button type="button" kind="ghost" size="sm" onClick={cancel}>
            {intl.formatMessage({ id: "label.button.cancel" })}
          </Button>
        </div>
      )}
    </div>
  );
}
