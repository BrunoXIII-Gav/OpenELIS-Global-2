import React from "react";
import {
  render,
  fireEvent,
  wait,
  waitForElement,
} from "@testing-library/react";
import { IntlProvider } from "react-intl";
import ModifyOrder from "./ModifyOrder";
import ConsentDownloadButton from "../addOrder/ConsentDownloadButton";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import { getConsentTemplateOptions } from "../../services/consentTemplateService";
import {
  getFromOpenElisServer,
  postToOpenElisServerForBlob,
  postToOpenElisServerFullResponse,
} from "../utils/Utils";
import messages from "../../languages/es.json";
import ModifyOrderEntryValidationSchema from "../formModel/validationSchema/ModifyOrderEntryValidationSchema";

jest.mock("../layout/Layout", () => {
  const React = require("react");
  return {
    ConfigurationContext: React.createContext({}),
    NotificationContext: React.createContext({}),
  };
});
jest.mock("../common/CustomNotification", () => ({
  AlertDialog: () => null,
  NotificationKinds: { error: "error" },
}));
jest.mock("../common/PatientHeader", () => () => null);
jest.mock("../common/PageBreadCrumb", () => () => null);
jest.mock("./EditSample", () => () => null);
jest.mock("./EditOrderEntryAdditionalQuestions", () => () => null);
jest.mock("../addOrder/OrderSuccessMessage", () => () => null);
jest.mock(
  "../formModel/validationSchema/ModifyOrderEntryValidationSchema",
  () => ({ validate: jest.fn().mockResolvedValue({}) }),
);
jest.mock(
  "../addOrder/AddOrder",
  () =>
    ({ orderFormValues, setOrderFormValues }) => (
      <input
        aria-label="Nombre del responsable"
        value={orderFormValues.sampleOrderItems.additionalFieldValues.guardian}
        onChange={(event) => {
          const guardian = event.target.value;
          setOrderFormValues((previous) => ({
            ...previous,
            sampleOrderItems: {
              ...previous.sampleOrderItems,
              additionalFieldValues: {
                ...previous.sampleOrderItems.additionalFieldValues,
                guardian,
              },
            },
          }));
        }}
      />
    ),
);
jest.mock("../../services/consentTemplateService", () => ({
  getConsentTemplateOptions: jest.fn(),
}));
jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerForBlob: jest.fn(),
  postToOpenElisServerFullResponse: jest.fn(),
}));
jest.mock("@carbon/react", () => {
  const React = require("react");
  const Div = ({ children }) => <div>{children}</div>;
  return {
    Grid: Div,
    Column: Div,
    Stack: Div,
    Button: ({ children, disabled, onClick, type }) => (
      <button type={type || "button"} disabled={disabled} onClick={onClick}>
        {children}
      </button>
    ),
    ProgressIndicator: ({ children, onChange }) => (
      <nav>
        {React.Children.map(children, (child, index) =>
          React.cloneElement(child, { onClick: () => onChange(index) }),
        )}
      </nav>
    ),
    ProgressStep: ({ label, onClick, disabled }) => (
      <button disabled={disabled} onClick={onClick}>
        {label}
      </button>
    ),
  };
});
const sources = [
  { id: "patient.fullName", format: "patientName" },
  { id: "patient.nationalId", path: "patientProperties.nationalId" },
  {
    id: "patientAdditional.future",
    path: "patientProperties.patientAdditionalFieldValues.future",
    format: "additional",
    fieldType: "TEXT",
  },
  { id: "tests.selectedNames", format: "tests" },
  { id: "sample.cug", path: "sampleXML.cug", scope: "samples" },
  {
    id: "orderAdditional.guardian",
    path: "sampleOrderItems.additionalFieldValues.guardian",
    format: "additional",
    fieldType: "TEXT",
  },
  {
    id: "orderAdditional.confirmed",
    path: "sampleOrderItems.additionalFieldValues.confirmed",
    format: "additional",
    fieldType: "BOOLEAN",
  },
];
const form = () => ({
  accessionNumber: "LNG71",
  patientName: "Miranda, Paloma",
  nationalId: "11155522",
  dob: "08/12/1993",
  gender: "F",
  sampleOrderItems: {
    labNo: "LNG71",
    requestDate: "10/17/2026",
    providerFirstName: "Mario",
    providerLastName: "Cornejo",
    providerDni: "77322841",
    additionalFieldValues: {
      guardian: "Responsable original",
      confirmed: "true",
    },
  },
  existingTests: [
    {
      sampleItemId: "20",
      testId: "30",
      testName: "83891.01 - Extracción de ADN",
      cugCode: "5000.2",
      sampleTypeId: "1",
    },
    {
      sampleItemId: "20",
      testId: "31",
      testName: "81183 - ATXN10 (ataxina 10)",
      cugCode: "5000.2",
      sampleTypeId: "1",
    },
  ],
  possibleTests: [],
  sampleTypes: [],
});
let allowed,
  patientAvailable,
  addNotification,
  setNotificationVisible,
  downloadedNames;
beforeEach(() => {
  jest.clearAllMocks();
  ModifyOrderEntryValidationSchema.validate.mockResolvedValue({});
  window.history.replaceState({}, "", "/ModifyOrder?accessionNumber=LNG71");
  allowed = true;
  patientAvailable = true;
  addNotification = jest.fn();
  setNotificationVisible = jest.fn();
  downloadedNames = [];
  window.URL.createObjectURL = jest.fn(() => "blob:consent");
  window.URL.revokeObjectURL = jest.fn();
  jest
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(function () {
      downloadedNames.push(this.download);
    });
  getConsentTemplateOptions.mockImplementation((callback) =>
    callback({ sources }),
  );
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.startsWith("/rest/patientByLabNumer"))
      return callback({ id: "10" });
    if (url.startsWith("/rest/SampleEdit")) return callback(form());
    if (url.startsWith("/rest/module-action-access"))
      return callback({ allowed });
    if (url.startsWith("/rest/patient-details"))
      return callback(
        patientAvailable
          ? {
              patientPK: "10",
              firstName: "Paloma",
              lastName: "Miranda",
              nationalId: "11155522",
              patientAdditionalFieldValues: {
                future: "Dato dinámico del paciente",
              },
            }
          : undefined,
      );
    throw new Error("Unexpected API: " + url);
  });
  postToOpenElisServerForBlob.mockImplementation((_url, _body, callback) =>
    callback(new Blob(["%PDF-1.4"], { type: "application/pdf" })),
  );
});
afterEach(() => jest.restoreAllMocks());
const open = (child = <ModifyOrder />) =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <ConfigurationContext.Provider
        value={{
          configurationProperties: {
            SHOW_ORDER_PROGRAM_ON_ORDER_ENTRY: "true",
          },
        }}
      >
        <NotificationContext.Provider
          value={{
            notificationVisible: false,
            addNotification,
            setNotificationVisible,
          }}
        >
          {child}
        </NotificationContext.Provider>
      </ConfigurationContext.Provider>
    </IntlProvider>,
  );
const orderStep = (page) =>
  fireEvent.click(page.getByText(messages["order.label.add"]));
const clickDownload = (page) =>
  fireEvent.click(page.getByText(messages["consent.download.button"]));
const awaitDownload = () =>
  waitForElement(() => {
    if (!downloadedNames.length) throw new Error("Waiting for PDF");
    return downloadedNames[0];
  });

test("editing an order downloads its existing CUG, tests, patient, provider and current mapped fields without saving or reserving another CUG", async () => {
  const page = open();
  orderStep(page);
  fireEvent.change(page.getByLabelText("Nombre del responsable"), {
    target: { value: "Responsable corregido" },
  });
  clickDownload(page);
  await awaitDownload();
  const [url, body] = postToOpenElisServerForBlob.mock.calls[0];
  const payload = JSON.parse(body);
  expect(url).toBe("/rest/reports/consent-template/preview");
  expect(payload.cug).toBe("5000.2");
  expect(payload.selectedTests).toEqual(
    form().existingTests.map((test) => test.testName),
  );
  expect(payload.patient.fullName).toBe("Miranda, Paloma");
  expect(payload.patient.nationalId).toBe("11155522");
  expect(payload.provider.fullName).toBe("Cornejo, Mario");
  expect(payload.provider.dni).toBe("77322841");
  expect(payload.orderDate).toBe("10/17/2026");
  expect(payload.sourceValues["orderAdditional.guardian"]).toBe(
    "Responsable corregido",
  );
  expect(payload.sourceValues["patientAdditional.future"]).toBe(
    "Dato dinámico del paciente",
  );
  expect(payload.booleanValues["orderAdditional.confirmed"]).toBe(true);
  expect(downloadedNames).toEqual(["consent-template.pdf"]);
  expect(window.URL.revokeObjectURL).toHaveBeenCalledWith("blob:consent");
  expect(postToOpenElisServerFullResponse).not.toHaveBeenCalled();
  expect(
    getFromOpenElisServer.mock.calls.some(([url]) =>
      url.includes("sample-cug"),
    ),
  ).toBe(false);
});
test("users viewing an order can download even when its submit button is disabled", async () => {
  allowed = false;
  const page = open();
  orderStep(page);
  expect(page.getByText(messages["label.button.submit"]).disabled).toBe(true);
  expect(page.getByText(messages["consent.download.button"]).disabled).toBe(
    false,
  );
  clickDownload(page);
  await awaitDownload();
  expect(postToOpenElisServerFullResponse).not.toHaveBeenCalled();
});
test("a failed PDF request shows the translated error and permits retrying", async () => {
  postToOpenElisServerForBlob.mockImplementationOnce(
    (_url, _body, _callback, error) => error(new Error("Failed")),
  );
  const page = open();
  orderStep(page);
  clickDownload(page);
  await waitForElement(() => {
    if (!addNotification.mock.calls.length)
      throw new Error("Waiting for error");
    return addNotification.mock.calls[0];
  });
  expect(addNotification).toHaveBeenCalledWith(
    expect.objectContaining({ message: messages["consent.download.error"] }),
  );
  expect(page.getByText(messages["consent.download.button"]).disabled).toBe(
    false,
  );
  clickDownload(page);
  await awaitDownload();
});
test("unavailable patient details stop generation rather than downloading a consent with missing mapped patient values", async () => {
  patientAvailable = false;
  const page = open();
  orderStep(page);
  clickDownload(page);
  await waitForElement(() => {
    if (!addNotification.mock.calls.length)
      throw new Error("Waiting for error");
    return addNotification.mock.calls[0];
  });
  expect(postToOpenElisServerForBlob).not.toHaveBeenCalled();
  expect(downloadedNames).toEqual([]);
});
test("creating an order continues using its entered patient data without a saved-patient lookup and prevents duplicate downloads", async () => {
  const values = form();
  values.patientProperties = {
    firstName: "Paloma",
    lastName: "Miranda",
    nationalId: "11155522",
  };
  const samples = [
    { tests: [{ name: "Extracción de ADN" }], sampleXML: { cug: "14.1" } },
  ];
  let finish;
  postToOpenElisServerForBlob.mockImplementation((_url, _body, callback) => {
    finish = callback;
  });
  const page = open(
    <ConsentDownloadButton orderFormValues={values} samples={samples} />,
  );
  clickDownload(page);
  await wait(() => expect(typeof finish).toBe("function"));
  clickDownload(page);
  expect(postToOpenElisServerForBlob).toHaveBeenCalledTimes(1);
  expect(getFromOpenElisServer).not.toHaveBeenCalled();
  finish(new Blob(["pdf"]));
  await awaitDownload();
  expect(
    JSON.parse(postToOpenElisServerForBlob.mock.calls[0][1]).patient.fullName,
  ).toBe("Miranda, Paloma");
});
