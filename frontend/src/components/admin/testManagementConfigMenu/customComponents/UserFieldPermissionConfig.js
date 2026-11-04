import React, { useEffect, useState } from "react";
import { Select, SelectItem, MultiSelect } from "@carbon/react";
import { useIntl } from "react-intl";
import { getFromOpenElisServer } from "../../../utils/Utils.js";

export const defaultUserTask = (module) => ({
  userPermissionModule: module,
  userPermissionActions:
    {
      orders: ["create", "update"],
      patients: ["create", "update"],
      results: ["enter", "update", "correct"],
      validation: ["validate"],
      "sample-management": ["receive", "update"],
      storage: ["update", "manage"],
      administration: ["manage"],
    }[module] || [],
});
export const userTaskFromMetadata = (metadata, module) => ({
  userPermissionModule: metadata?.userPermissionModule || module,
  userPermissionActions:
    metadata?.userPermissionActions ||
    defaultUserTask(metadata?.userPermissionModule || module)
      .userPermissionActions,
});
const taskActions = new Set([
  "create",
  "update",
  "receive",
  "enter",
  "correct",
  "validate",
  "manage",
]);

export default function UserFieldPermissionConfig({
  id,
  value,
  module,
  onChange,
  disabled = false,
}) {
  const intl = useIntl();
  const [modules, setModules] = useState([]);
  useEffect(() => {
    getFromOpenElisServer("/rest/custom-roles/catalog", (catalog) =>
      setModules(
        (catalog?.moduleCatalog?.modules || []).filter((item) =>
          item.actions.some((action) => taskActions.has(action.key)),
        ),
      ),
    );
  }, []);
  const task = value || defaultUserTask(module);
  const actions = (
    modules.find((item) => item.key === task.userPermissionModule)?.actions ||
    []
  ).filter((action) => taskActions.has(action.key));
  return (
    <>
      <Select
        id={`${id}-module`}
        labelText={intl.formatMessage({ id: "userField.task.module" })}
        value={task.userPermissionModule}
        disabled={disabled}
        onChange={(event) => onChange(defaultUserTask(event.target.value))}
      >
        {modules.map((item) => (
          <SelectItem
            key={item.key}
            value={item.key}
            text={intl.formatMessage({ id: item.labelKey })}
          />
        ))}
      </Select>
      <MultiSelect
        id={`${id}-actions`}
        titleText={intl.formatMessage({ id: "userField.task.actions" })}
        label={intl.formatMessage({ id: "userField.task.choose" })}
        items={actions}
        disabled={disabled}
        itemToString={(action) =>
          action ? intl.formatMessage({ id: action.labelKey }) : ""
        }
        selectedItems={actions.filter((action) =>
          task.userPermissionActions.includes(action.key),
        )}
        onChange={({ selectedItems }) =>
          onChange({
            ...task,
            userPermissionActions: selectedItems.map((item) => item.key),
          })
        }
      />
      <p>{intl.formatMessage({ id: "userField.task.help" })}</p>
    </>
  );
}
