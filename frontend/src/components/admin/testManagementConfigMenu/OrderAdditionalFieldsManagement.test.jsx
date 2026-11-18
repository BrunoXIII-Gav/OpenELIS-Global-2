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

const openManagement = () =>
  render(
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

const openEditor = (displayName = "Consentimiento") => {
  const view = openManagement();
  const row = view.getByText(displayName).closest("tr");
  fireEvent.click(within(row).getByText(messages["button.edit"]));
  return view;
};

test("the status filter defaults to active fields and inactive fields have no order controls", () => {
  getFromOpenElisServer.mockImplementation((url, callback) => {
    callback(
      url.includes("?includeInactive")
        ? [source, { ...dependent, active: false, sortOrder: 99 }]
        : [],
    );
  });
  const view = openManagement();
  expect(view.queryByText("Consentimiento")).toBeNull();
  fireEvent.change(view.container.querySelector("#order-field-status-filter"), {
    target: { value: "inactive" },
  });
  const row = view.getByText("Consentimiento").closest("tr");
  expect(view.queryByText("Diagnóstico genómico")).toBeNull();
  expect(row.querySelector('input[type="number"]')).toBeNull();
  expect(
    within(row).getByText(messages["button.save"]).closest("button").disabled,
  ).toBe(true);
  fireEvent.click(within(row).getByText(messages["button.edit"]));
  expect(
    view.container.querySelector("#order-additional-sort-order").disabled,
  ).toBe(true);
  expect(
    view.container.querySelector("#order-additional-sort-order").value,
  ).toBe("");
});

test("activation lets the server append the field instead of sending its old order", () => {
  getFromOpenElisServer.mockImplementation((url, callback) => {
    callback(
      url.includes("?includeInactive")
        ? [{ ...dependent, active: false, sortOrder: 2 }]
        : [],
    );
  });
  const view = openManagement();
  fireEvent.change(view.container.querySelector("#order-field-status-filter"), {
    target: { value: "inactive" },
  });
  fireEvent.click(view.getByText(messages["order.additional.fields.enable"]));
  expect(JSON.parse(putToOpenElisServerFullResponse.mock.calls[0][1])).toEqual({
    active: true,
    options: [],
  });
});

test("requester search and dynamic profile fields have configurable order entries", () => {
  getFromOpenElisServer.mockImplementation((url, callback) => {
    callback(
      url.endsWith("/order-additional-fields/fixed")
        ? [
            { fieldKey: "requesterSearch", visible: true, sortOrder: 2 },
            { fieldKey: "requesterProfileFields", visible: true, sortOrder: 3 },
          ]
        : [],
    );
  });
  const view = openManagement();
  expect(
    view.getByText(messages["order.additional.fields.fixed.requesterSearch"]),
  ).toBeDefined();
  expect(
    view.getByText(
      messages["order.additional.fields.fixed.requesterProfileFields"],
    ),
  ).toBeDefined();
  fireEvent.change(
    view.container.querySelector("#fixed-sort-requesterSearch"),
    { target: { value: "7" } },
  );
  fireEvent.click(
    view.container.querySelector('[data-cy="save-fixed-order-fields"]'),
  );
  expect(JSON.parse(putToOpenElisServerFullResponse.mock.calls[0][1])).toEqual(
    expect.arrayContaining([
      expect.objectContaining({ fieldKey: "requesterSearch", sortOrder: 7 }),
      expect.objectContaining({
        fieldKey: "requesterProfileFields",
        sortOrder: 3,
      }),
    ]),
  );
});

test("used selection fields allow new options and deactivation while preserving saved options", () => {
  const field = {
    ...source,
    displayName: "Selección de programa",
    fieldType: "SELECT",
    hasSavedValues: true,
    options: [
      {
        id: 10,
        optionKey: "old-option",
        optionLabel: "Anterior",
        active: true,
      },
      { id: 11, optionKey: "retired", optionLabel: "Retirada", active: false },
    ],
  };
  getFromOpenElisServer.mockImplementation((url, callback) => {
    callback(url.includes("?includeInactive") ? [field] : []);
  });
  const view = openEditor(field.displayName);
  const oldLabel = view.getByDisplayValue("Anterior");
  expect(oldLabel.readOnly).toBe(true);
  expect(view.getByDisplayValue("Retirada")).toBeDefined();
  expect(
    view.getAllByText(messages["additional.fields.optionRemove"])[0].disabled,
  ).toBe(true);

  fireEvent.click(view.getByText(messages["additional.fields.optionAdd"]));
  const newLabel = view.container.querySelector(
    "#order-additional-option-label-2",
  );
  expect(newLabel.readOnly).toBe(false);
  fireEvent.change(newLabel, { target: { value: "Nueva" } });
  fireEvent.click(
    view.container.querySelector("#order-additional-option-active-0"),
  );
  fireEvent.click(
    view.container.querySelector('[data-cy="create-order-additional-field"]'),
  );
  const payload = JSON.parse(putToOpenElisServerFullResponse.mock.calls[0][1]);
  expect(payload.options).toEqual([
    expect.objectContaining({
      id: 10,
      optionKey: "old-option",
      optionLabel: "Anterior",
      active: false,
    }),
    expect.objectContaining({ id: 11, optionLabel: "Retirada", active: false }),
    expect.objectContaining({
      optionKey: "NUEVA",
      optionLabel: "Nueva",
      active: true,
    }),
  ]);
});

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

test("document fields select a configured template without duplicating its Jasper mappings", () => {
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url === "/rest/reports/consent-template/options")
      callback({ templates: [{ id: "CONSENT-bank", name: "Banco ADN" }] });
    else
      callback(
        url.includes("?includeInactive")
          ? [{ ...dependent, fieldType: "DOCUMENT" }]
          : [],
      );
  });
  const view = openEditor();
  fireEvent.change(
    view.container.querySelector("#order-additional-document-mode"),
    { target: { value: "CONSENT" } },
  );
  expect(
    view.container.querySelector("#order-additional-document-accept"),
  ).toBeNull();
  fireEvent.click(
    view.container.querySelector('[data-cy="create-order-additional-field"]'),
  );
  expect(putToOpenElisServerFullResponse).not.toHaveBeenCalled();
  fireEvent.change(
    view.container.querySelector("#order-additional-document-template"),
    { target: { value: "CONSENT-bank" } },
  );
  fireEvent.change(
    view.container.querySelector("#order-additional-document-button-label"),
    { target: { value: "Descargar banco" } },
  );
  fireEvent.click(
    view.container.querySelector('[data-cy="create-order-additional-field"]'),
  );
  const metadata = JSON.parse(
    JSON.parse(putToOpenElisServerFullResponse.mock.calls[0][1]).metadataJson,
  );
  expect(metadata.document).toEqual({
    mode: "CONSENT",
    templateId: "CONSENT-bank",
    buttonLabel: "Descargar banco",
  });
  expect(metadata.rules.requiredWhen).toEqual([
    { fieldKey: "genomic", operator: "istrue" },
  ]);
});
