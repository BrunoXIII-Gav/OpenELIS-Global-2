import React from "react";
import { fireEvent, render } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import SampleResultsTable from "./SampleResultsTable";
import messages from "../../languages/es.json";
import { getFromOpenElisServer } from "../utils/Utils";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerFullResponse: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
}));

const sample = {
  id: "54",
  externalId: "LNG81-1",
  cugCode: "5008.1",
  sampleTypeId: "1",
  sampleType: "Sangre",
  editable: true,
  canRead: true,
  canComplete: true,
  canUpdate: true,
  orderedTests: [
    { analysisId: "a-54", testName: "ADN", status: "Not Started" },
  ],
  additionalFields: [],
  additionalFieldValues: {},
};

const table = (overrides = {}) => (
  <IntlProvider locale="es" messages={messages}>
    <SampleResultsTable
      sampleItems={[{ ...sample, ...overrides }]}
      currentTestsVisibleBySampleId={{ 54: true }}
    />
  </IntlProvider>
);

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((_url, callback) => callback([]));
});

const expand = (view) =>
  fireEvent.click(
    view.container.querySelector("tbody .cds--table-expand__button"),
  );

test("changing a normal order to alternate flow preserves tests as read-only history and hides processing", () => {
  const view = render(table());
  expand(view);
  expect(
    view.getByText(messages["sample.management.current.sample.fields.heading"]),
  ).toBeTruthy();
  expect(
    view.getByRole("button", {
      name: messages["sample.management.action.save"],
    }),
  ).toBeTruthy();
  fireEvent.change(view.getByLabelText(messages["sample.quantity.label"]), {
    target: { value: "7" },
  });

  view.rerender(table({ alternateOrderFlow: true }));
  expect(view.getByText("ADN")).toBeTruthy();
  expect(
    view.getByText(messages["sample.management.alternateOrderFlow.title"]),
  ).toBeTruthy();
  expect(view.getByText("Pruebas anteriores (historial): 1")).toBeTruthy();
  expect(
    view.queryByText(
      messages["sample.management.current.sample.fields.heading"],
    ),
  ).toBeNull();
  expect(
    view.queryByRole("button", {
      name: messages["sample.management.action.save"],
    }),
  ).toBeNull();
  expect(
    view.queryByRole("button", {
      name: messages["sample.management.table.cancelTest"],
    }),
  ).toBeNull();
  expect(
    view.getByLabelText(messages["sample.cug.suffix.label"]).readOnly,
  ).toBe(true);

  view.rerender(table({ alternateOrderFlow: false }));
  expect(view.getByText("ADN")).toBeTruthy();
  expect(view.getByLabelText(messages["sample.quantity.label"]).value).toBe("");
  expect(
    view.getByRole("button", {
      name: messages["sample.management.action.save"],
    }),
  ).toBeTruthy();
});

test("an order created in alternate flow also has no processing controls", () => {
  const view = render(table({ alternateOrderFlow: true, orderedTests: [] }));
  expand(view);
  expect(
    view.getByText(messages["sample.management.alternateOrderFlow.title"]),
  ).toBeTruthy();
  expect(
    view.queryByText(
      messages["sample.management.current.sample.fields.heading"],
    ),
  ).toBeNull();
  expect(
    view.queryByRole("button", {
      name: messages["sample.management.action.save"],
    }),
  ).toBeNull();
  expect(
    view.getByLabelText(messages["sample.cug.suffix.label"]).readOnly,
  ).toBe(true);
});

test("alternate flow does not expose historical tests hidden by role restrictions", () => {
  const view = render(
    table({ alternateOrderFlow: true, restrictedFieldTagKeys: ["tests"] }),
  );
  expand(view);
  expect(view.queryByText("ADN")).toBeNull();
  expect(
    view.queryByRole("button", {
      name: messages["sample.management.action.save"],
    }),
  ).toBeNull();
});
