import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import UserFieldPermissionConfig, {
  userTaskFromMetadata,
} from "./UserFieldPermissionConfig";
import messages from "../../../../languages/en.json";
import { getFromOpenElisServer } from "../../../utils/Utils";
jest.mock("../../../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));

const catalog = {
  moduleCatalog: {
    modules: [
      {
        key: "results",
        labelKey: "authorization.module.results",
        actions: ["read", "enter", "update", "correct", "export"].map(
          (key) => ({ key, labelKey: `authorization.action.${key}` }),
        ),
      },
      {
        key: "validation",
        labelKey: "authorization.module.validation",
        actions: ["read", "validate", "revoke"].map((key) => ({
          key,
          labelKey: `authorization.action.${key}`,
        })),
      },
    ],
  },
};
beforeEach(() =>
  getFromOpenElisServer.mockImplementation((_url, callback) =>
    callback(catalog),
  ),
);

test("task module uses the real role catalog and changing it selects its operational permission", () => {
  const onChange = jest.fn();
  render(
    <IntlProvider locale="en" messages={messages}>
      <UserFieldPermissionConfig
        id="analyst"
        module="results"
        onChange={onChange}
      />
    </IntlProvider>,
  );
  fireEvent.change(screen.getByLabelText("Task module"), {
    target: { value: "validation" },
  });
  expect(onChange).toHaveBeenCalledWith({
    userPermissionModule: "validation",
    userPermissionActions: ["validate"],
  });
  expect(screen.queryByText("Read")).not.toBeInTheDocument();
});

test("a legacy profile selection becomes task permissions without granting access by profession", () => {
  expect(
    userTaskFromMetadata({ userProfileCodes: ["BIOLOGIST"] }, "results"),
  ).toEqual({
    userPermissionModule: "results",
    userPermissionActions: ["enter", "update", "correct"],
  });
});

test("an explicit task in another module keeps that module's default actions", () => {
  expect(
    userTaskFromMetadata({ userPermissionModule: "validation" }, "orders"),
  ).toEqual({
    userPermissionModule: "validation",
    userPermissionActions: ["validate"],
  });
});
