import React from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import SearchPatientForm from "./SearchPatientForm";
import messages from "../../languages/es.json";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import { getFromOpenElisServer } from "../utils/Utils";

jest.mock("../layout/Layout", () => {
  const React = require("react");
  return {
    ConfigurationContext: React.createContext({}),
    NotificationContext: React.createContext({}),
  };
});
jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServer: jest.fn(),
}));
jest.mock("../common/CustomDatePicker", () => () => null);
jest.mock("./photoManagement/photoAvatar/AyncAvatar", () => () => null);

const patient = {
  patientID: "17",
  firstName: "Ana",
  lastName: "Perez",
  gender: "F",
  dob: "01/01/1990",
  nationalId: "TEST-17",
  dataSourceName: "OpenElis",
};
let detailsResponse;
let searchResponse;
let addNotification;
let setNotificationVisible;
let getSelectedPatient;
let restrictedCriteria;

beforeEach(() => {
  jest.clearAllMocks();
  detailsResponse = { patientPK: "17", firstName: "Ana", lastName: "Perez" };
  searchResponse = { patientSearchResults: [patient] };
  addNotification = jest.fn();
  setNotificationVisible = jest.fn();
  getSelectedPatient = jest.fn();
  restrictedCriteria = [];
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.startsWith("/rest/patient-search-results?"))
      callback(searchResponse);
    else if (url.startsWith("/rest/patient-details?"))
      callback(detailsResponse);
    else if (url === "/rest/patient-search/access")
      callback({ restrictedCriteria });
    else callback(undefined);
  });
});

const show = (props = {}, configuration = {}) =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <ConfigurationContext.Provider
        value={{
          configurationProperties: {
            UseExternalPatientInfo: "false",
            ...configuration,
          },
        }}
      >
        <NotificationContext.Provider
          value={{
            notificationVisible: false,
            setNotificationVisible,
            addNotification,
          }}
        >
          <SearchPatientForm
            {...props}
            getSelectedPatient={getSelectedPatient}
          />
        </NotificationContext.Provider>
      </ConfigurationContext.Provider>
    </IntlProvider>,
  );

const search = async () => {
  await act(async () => {
    fireEvent.click(document.getElementById("local_search"));
  });
};

test.each([undefined, null, { error: "Access denied" }])(
  "a failed or invalid details response (%p) keeps the search usable without selecting a patient",
  async (response) => {
    detailsResponse = response;
    show();
    await search();
    expect(() => fireEvent.click(document.getElementById("17"))).not.toThrow();
    expect(getSelectedPatient).not.toHaveBeenCalled();
    expect(
      getFromOpenElisServer.mock.calls.some(([url]) =>
        url.startsWith("/rest/patient-photos/"),
      ),
    ).toBe(false);
    expect(addNotification).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: "error",
        message: messages["patient.details.unavailable"],
      }),
    );
    expect(setNotificationVisible).toHaveBeenCalledWith(true);
    expect(screen.getByText("Ana")).toBeTruthy();
  },
);

test("valid details select the patient even when the optional photo request fails", async () => {
  show();
  await search();
  fireEvent.click(document.getElementById("17"));
  expect(getSelectedPatient).toHaveBeenCalledWith(detailsResponse);
  expect(getFromOpenElisServer).toHaveBeenCalledWith(
    "/rest/patient-photos/17/false",
    expect.any(Function),
  );
  expect(addNotification).not.toHaveBeenCalled();
});

test("a failed details request can be retried successfully", async () => {
  detailsResponse = undefined;
  show();
  await search();
  fireEvent.click(document.getElementById("17"));
  detailsResponse = { patientPK: "17", firstName: "Ana" };
  fireEvent.click(document.getElementById("17"));
  expect(getSelectedPatient).toHaveBeenCalledTimes(1);
  expect(getSelectedPatient).toHaveBeenCalledWith(detailsResponse);
});

test("a failed search stops loading and reports failure rather than no matching patients", async () => {
  searchResponse = undefined;
  show();
  await search();
  expect(document.querySelector(".cds--loading-overlay")).toBeNull();
  expect(document.querySelector(".cds--loading")).toBeNull();
  expect(addNotification).toHaveBeenCalledWith(
    expect.objectContaining({
      kind: "error",
      message: messages["patient.search.unavailable"],
    }),
  );
  expect(getSelectedPatient).not.toHaveBeenCalled();
});

test("order creation uses the authorized order lookup for search, selection and photograph", async () => {
  detailsResponse = {
    patientPK: "17",
    firstName: "Ana",
    readOnly: true,
    patientUpdateStatus: "NO_ACTION",
  };
  show({ forOrderEntry: true });
  await search();
  expect(
    getFromOpenElisServer.mock.calls.some(
      ([url]) =>
        url.startsWith("/rest/patient-search-results?") &&
        url.includes("forOrderEntry=true"),
    ),
  ).toBe(true);
  fireEvent.click(document.getElementById("17"));
  expect(getFromOpenElisServer).toHaveBeenCalledWith(
    "/rest/patient-details?patientID=17&forOrderEntry=true",
    expect.any(Function),
  );
  expect(getFromOpenElisServer).toHaveBeenCalledWith(
    "/rest/patient-photos/17/false?forOrderEntry=true",
    expect.any(Function),
  );
  expect(getSelectedPatient).toHaveBeenCalledWith(detailsResponse);
  expect(addNotification).not.toHaveBeenCalled();
});

test.each([undefined, "ALPHANUM", "NUMERIC"])(
  "the shared CUG field submits an exact patient prefix without accession formatting (%p)",
  async (AccessionFormat) => {
    show({}, { AccessionFormat });
    const input = screen.getByLabelText("CUG");
    expect(input.placeholder).toBe(messages["patient.search.cug.placeholder"]);
    expect(
      screen.getByText(messages["patient.search.cug.helper"]),
    ).toBeTruthy();
    fireEvent.change(input, { target: { value: "5415" } });
    await search();
    const [url] = getFromOpenElisServer.mock.calls.find(([value]) =>
      value.startsWith("/rest/patient-search-results?"),
    );
    expect(
      new URL(url, "https://test.local").searchParams.get("labNumber"),
    ).toBe("5415");
    expect(screen.getByText("Ana")).toBeTruthy();
  },
);

test("the same CUG field still submits an order number unchanged", async () => {
  show({ forOrderEntry: true }, { AccessionFormat: "ALPHANUM" });
  fireEvent.change(screen.getByLabelText("CUG"), {
    target: { value: " LNG0000000000078 " },
  });
  await search();
  const [url] = getFromOpenElisServer.mock.calls.find(([value]) =>
    value.startsWith("/rest/patient-search-results?"),
  );
  const params = new URL(url, "https://test.local").searchParams;
  expect(params.get("labNumber")).toBe("LNG0000000000078");
  expect(params.get("forOrderEntry")).toBe("true");
});

test("the renamed CUG field keeps the existing search-criterion restriction", () => {
  restrictedCriteria = ["lab-number"];
  show();
  expect(screen.queryByLabelText("CUG")).toBeNull();
});
