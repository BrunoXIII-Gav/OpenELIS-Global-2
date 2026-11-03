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

const table = (overrides = {}, props = {}) => (
  <IntlProvider locale="es" messages={messages}>
    <SampleResultsTable
      sampleItems={[{ ...sample, ...overrides }]}
      currentTestsVisibleBySampleId={{ 54: true }}
      {...props}
    />
  </IntlProvider>
);

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((_url, callback) => callback([]));
});

test("an existing alternate specimen can explicitly associate a permitted test without claiming its unknown volume is exhausted", () => {
  const associate = jest.fn();
  const view = render(
    table(
      {
        alternateOrderFlow: true,
        editable: false,
        alternateFlowTestAssociationAllowed: true,
        orderedTests: [],
      },
      { onAssociateAlternateTests: associate },
    ),
  );
  expand(view);
  expect(
    view.queryByText(messages["sample.management.status.allVolumeDispensed"]),
  ).toBeNull();
  fireEvent.click(
    view.getByRole("button", {
      name: messages["sample.management.alternateOrderFlow.associateTest"],
    }),
  );
  expect(associate).toHaveBeenCalledWith("54");
  expect(view.queryByLabelText(messages["sample.quantity.label"])).toBeNull();
  view.rerender(
    table(
      {
        alternateOrderFlow: true,
        alternateFlowTestAssociationAllowed: true,
        restrictedFieldTagKeys: ["tests"],
      },
      { onAssociateAlternateTests: associate },
    ),
  );
  expect(
    view.queryByRole("button", {
      name: messages["sample.management.alternateOrderFlow.associateTest"],
    }),
  ).toBeNull();
  view.rerender(
    table(
      { alternateOrderFlow: true, alternateFlowTestAssociationAllowed: true },
      { onAssociateAlternateTests: associate, isReadOnly: true },
    ),
  );
  expect(
    view.queryByRole("button", {
      name: messages["sample.management.alternateOrderFlow.associateTest"],
    }),
  ).toBeNull();
});

const expand = (view) =>
  fireEvent.click(
    view.container.querySelector("tbody .cds--table-expand__button"),
  );

test("an alternate-flow sample with a permitted test exposes its sample fields while other tests remain locked", () => {
  const view = render(
    table({
      alternateOrderFlow: true,
      alternateOrderFlowProcessingAllowed: true,
      orderedTests: [
        {
          analysisId: "allowed",
          testName: "Permitida",
          status: "Not Started",
          alternateOrderFlowProcessingAllowed: true,
        },
        {
          analysisId: "history",
          testName: "Historial",
          status: "Not Started",
          alternateOrderFlowProcessingAllowed: false,
        },
      ],
    }),
  );
  expand(view);
  expect(
    view.getByText(
      messages["sample.management.alternateOrderFlow.processingException"],
    ),
  ).toBeTruthy();
  expect(
    view.getByText(messages["sample.management.current.sample.fields.heading"]),
  ).toBeTruthy();
  expect(
    view.getByRole("button", {
      name: messages["sample.management.action.save"],
    }).disabled,
  ).toBe(false);
  const cancelButtons = view.getAllByRole("button", {
    name: messages["sample.management.table.cancelTest"],
  });
  expect(cancelButtons[0].disabled).toBe(false);
  expect(cancelButtons[1].disabled).toBe(true);
});

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
