import React from "react";
import { fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import AddTestsModal from "./AddTestsModal";
import messages from "../../languages/es.json";
import {
  getFromOpenElisServer,
  postToOpenElisServerFullResponse,
} from "../utils/Utils";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerFullResponse: jest.fn(),
}));

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    callback(
      url === "/rest/user-sample-types"
        ? [{ id: "1", value: "Sangre" }]
        : {
            tests: [
              {
                id: "30",
                name: "Extracción",
                allowProcessingInAlternateFlow: true,
              },
              {
                id: "31",
                name: "Prueba normal",
                allowProcessingInAlternateFlow: false,
              },
            ],
            panels: [{ id: "1", name: "Panel mixto", testIds: "30,31" }],
          },
    );
  });
});

const show = (overrides = {}) =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <AddTestsModal
        open
        onClose={jest.fn()}
        selectedSampleIds={["54"]}
        selectedSamples={[
          {
            id: "54",
            sampleTypeId: "1",
            alternateOrderFlow: true,
            alternateFlowTestAssociationAllowed: true,
            hasRemainingQuantity: false,
            ...overrides,
          },
        ]}
      />
    </IntlProvider>,
  );

test("only explicitly permitted tests of the existing specimen type can be added before volume is recorded", async () => {
  const view = show();
  expect(view.getByLabelText("Extracción")).toBeTruthy();
  expect(view.queryByLabelText("Prueba normal")).toBeNull();
  expect(view.queryByLabelText("Panel mixto")).toBeNull();
  expect(
    view.getByLabelText(
      messages["sample.management.addTests.modal.selectSampleType"],
    ).disabled,
  ).toBe(true);
  fireEvent.click(view.getByLabelText("Extracción"));
  const save = view.getByRole("button", { name: "Agregar 1 prueba(s)" });
  expect(save.disabled).toBe(false);
  fireEvent.click(save);
  await wait(() => expect(postToOpenElisServerFullResponse).toHaveBeenCalled());
  expect(JSON.parse(postToOpenElisServerFullResponse.mock.calls[0][1])).toEqual(
    { sampleItemIds: ["54"], testIds: ["30"] },
  );
});

test("an exhausted or unauthorized specimen cannot be associated just by opening the dialog", () => {
  const view = show({ alternateFlowTestAssociationAllowed: false });
  fireEvent.click(view.getByLabelText("Extracción"));
  expect(
    view.getByRole("button", {
      name: messages["sample.management.addTests.modal.noEligibleSamples"],
    }).disabled,
  ).toBe(true);
  expect(postToOpenElisServerFullResponse).not.toHaveBeenCalled();
});

test("normal specimens retain their ordinary test choices", () => {
  const view = show({ alternateOrderFlow: false, hasRemainingQuantity: true });
  expect(view.getByLabelText("Prueba normal")).toBeTruthy();
  expect(view.getByLabelText("Panel mixto")).toBeTruthy();
  expect(
    view.getByLabelText(
      messages["sample.management.addTests.modal.selectSampleType"],
    ).disabled,
  ).toBe(false);
});

test("a server rejection after the configuration changes is shown in the user's language", async () => {
  postToOpenElisServerFullResponse.mockImplementation((_url, _body, callback) =>
    callback({
      ok: false,
      json: () =>
        Promise.resolve({
          message: "sample.management.addTests.error.incompatibleTest",
        }),
    }),
  );
  const view = show();
  fireEvent.click(view.getByLabelText("Extracción"));
  fireEvent.click(view.getByRole("button", { name: "Agregar 1 prueba(s)" }));
  await wait(() =>
    expect(
      view.getByText(
        messages["sample.management.addTests.error.incompatibleTest"],
      ),
    ).toBeTruthy(),
  );
});
