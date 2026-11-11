import React from "react";
import { fireEvent, render, within } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import OrderAdditionalFieldsManagement from "./OrderAdditionalFieldsManagement";
import { NotificationContext } from "../../layout/Layout";
import {
  getFromOpenElisServer,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils";
import messages from "../../../languages/es.json";

jest.mock("../../layout/Layout", () => ({
  NotificationContext: require("react").createContext({}),
}));
jest.mock("../../common/PageBreadCrumb", () => () => null);
jest.mock("../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  putToOpenElisServerFullResponse: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
  deleteFromOpenElisServer: jest.fn(),
}));

const source = {
  id: 1,
  fieldKey: "genomic",
  displayName: "Diagnóstico genómico",
  fieldType: "BOOLEAN",
  active: true,
  options: [],
};
const dependent = {
  id: 2,
  fieldKey: "consent",
  displayName: "Consentimiento",
  fieldType: "BOOLEAN",
  active: true,
  options: [],
  metadataJson: JSON.stringify({
    rules: {
      logic: "ANY",
      requiredWhen: [{ fieldKey: "genomic", operator: "isTrue" }],
    },
  }),
};

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    callback(url.includes("?includeInactive") ? [source, dependent] : []);
  });
});

const openEditor = () => {
  const view = render(
    <IntlProvider locale="es" messages={messages}>
      <NotificationContext.Provider
        value={{
          notificationVisible: false,
          setNotificationVisible: jest.fn(),
          addNotification: jest.fn(),
        }}
      >
        <OrderAdditionalFieldsManagement />
      </NotificationContext.Provider>
    </IntlProvider>,
  );
  const row = view.getByText("Consentimiento").closest("tr");
  fireEvent.click(within(row).getByText(messages["button.edit"]));
  return view;
};

test("editing a boolean rule preserves its operator and does not require a comparison value", () => {
  const view = openEditor();
  expect(
    within(view.getByText("Consentimiento").closest("tr")).getByText(
      messages["order.additional.fields.rules.conditional"],
    ),
  ).toBeDefined();
  expect(view.container.querySelector("#requiredWhen-operator-0").value).toBe(
    "istrue",
  );
  expect(view.container.querySelector("#requiredWhen-value-0")).toBeNull();
  fireEvent.click(
    view.container.querySelector('[data-cy="create-order-additional-field"]'),
  );
  expect(putToOpenElisServerFullResponse).toHaveBeenCalledWith(
    "/rest/order-additional-fields/2",
    expect.any(String),
    expect.any(Function),
  );
  const payload = JSON.parse(putToOpenElisServerFullResponse.mock.calls[0][1]);
  expect(JSON.parse(payload.metadataJson).rules).toEqual({
    logic: "ANY",
    requiredWhen: [{ fieldKey: "genomic", operator: "istrue" }],
  });
});

test("visibility and required conditions are independently grouped and edited", () => {
  const view = openEditor();
  const visibility = view.getByRole("region", {
    name: messages["order.additional.fields.rules.visibleWhen"],
  });
  const required = view.getByRole("region", {
    name: messages["order.additional.fields.rules.requiredWhen"],
  });
  fireEvent.click(
    within(visibility).getByText(messages["order.additional.fields.rules.add"]),
  );
  expect(view.container.querySelector("#visibleWhen-field-0")).not.toBeNull();
  expect(view.container.querySelector("#requiredWhen-field-0").value).toBe(
    "genomic",
  );
  fireEvent.click(
    within(visibility).getByText(
      messages["order.additional.fields.rules.remove"],
    ),
  );
  expect(view.container.querySelector("#visibleWhen-field-0")).toBeNull();
  expect(
    within(required).getByText(
      messages["order.additional.fields.rules.remove"],
    ),
  ).toBeDefined();
});

test("changing operators shows only the value controls they need", () => {
  const view = openEditor();
  const operator = view.container.querySelector("#requiredWhen-operator-0");
  fireEvent.change(operator, { target: { value: "in" } });
  fireEvent.change(view.container.querySelector("#requiredWhen-value-0"), {
    target: { value: "true, false" },
  });
  fireEvent.click(
    view.container.querySelector('[data-cy="create-order-additional-field"]'),
  );
  const payload = JSON.parse(putToOpenElisServerFullResponse.mock.calls[0][1]);
  expect(JSON.parse(payload.metadataJson).rules.requiredWhen[0]).toEqual({
    fieldKey: "genomic",
    operator: "in",
    values: ["true", "false"],
  });
});

test("incomplete conditions prevent configuration from being saved", () => {
  const view = openEditor();
  const visibility = view.getByRole("region", {
    name: messages["order.additional.fields.rules.visibleWhen"],
  });
  fireEvent.click(
    within(visibility).getByText(messages["order.additional.fields.rules.add"]),
  );
  fireEvent.click(
    view.container.querySelector('[data-cy="create-order-additional-field"]'),
  );
  expect(putToOpenElisServerFullResponse).not.toHaveBeenCalled();
});
