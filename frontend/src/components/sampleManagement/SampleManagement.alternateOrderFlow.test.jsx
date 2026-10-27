import React from "react";
import { fireEvent, render } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import SampleManagement from "./SampleManagement";
import { NotificationContext } from "../layout/Layout";
import { getFromOpenElisServer } from "../utils/Utils";
import messages from "../../languages/es.json";

let mockSearchResponse;

jest.mock("../layout/Layout", () => ({
  NotificationContext: require("react").createContext({}),
}));
jest.mock("../common/CustomNotification", () => ({
  AlertDialog: () => null,
  NotificationKinds: { error: "error", success: "success" },
}));
jest.mock("../common/PageBreadCrumb", () => () => null);
jest.mock("./CreateAliquotModal", () => () => null);
jest.mock("./AddTestsModal", () => () => null);
jest.mock("../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
jest.mock("./SampleSearch", () => ({ onSearchResults }) => (
  <button onClick={() => onSearchResults(mockSearchResponse, null)}>
    Buscar
  </button>
));
jest.mock(
  "./SampleResultsTable",
  () =>
    ({ onSelectionChange, currentTestsVisibleBySampleId }) => (
      <>
        <button onClick={() => onSelectionChange(["54"])}>
          Seleccionar alterna
        </button>
        <button onClick={() => onSelectionChange(["54", "55"])}>
          Seleccionar ambas
        </button>
        <output data-testid="processing-visibility">
          {JSON.stringify(currentTestsVisibleBySampleId)}
        </output>
      </>
    ),
);

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((_url, callback) =>
    callback({ allowed: true }),
  );
  mockSearchResponse = {
    accessionNumber: "LNG81",
    sampleItems: [
      { id: "54", alternateOrderFlow: true },
      { id: "55", alternateOrderFlow: false },
    ],
  };
});

const show = () =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <NotificationContext.Provider
        value={{
          addNotification: jest.fn(),
          setNotificationVisible: jest.fn(),
          notificationVisible: false,
        }}
      >
        <SampleManagement />
      </NotificationContext.Provider>
    </IntlProvider>,
  );

test("hides the processing action when only alternate samples are selected and restores it after a new normal search", () => {
  const page = show();
  fireEvent.click(page.getByText("Buscar"));
  fireEvent.click(page.getByText("Seleccionar alterna"));
  expect(
    page.queryByRole("button", {
      name: messages["sample.management.action.currentSample"],
    }),
  ).toBeNull();

  mockSearchResponse.sampleItems[0].alternateOrderFlow = false;
  fireEvent.click(page.getByText("Buscar"));
  fireEvent.click(page.getByText("Seleccionar alterna"));
  expect(
    page.getByRole("button", {
      name: messages["sample.management.action.currentSample"],
    }),
  ).toBeTruthy();
});

test("a mixed selection opens processing only for normal samples", () => {
  const page = show();
  fireEvent.click(page.getByText("Buscar"));
  fireEvent.click(page.getByText("Seleccionar ambas"));
  fireEvent.click(
    page.getByRole("button", {
      name: messages["sample.management.action.currentSample"],
    }),
  );
  expect(
    JSON.parse(page.getByTestId("processing-visibility").textContent),
  ).toEqual({ 55: true });
});
