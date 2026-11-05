import React, { useState } from "react";
import { fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import EditSample from "./EditSample";
import ConsentDownloadButton from "../addOrder/ConsentDownloadButton";
import {
  buildModifyOrderPayload,
  buildSamplesFromOrder,
} from "./modifyOrderSamples";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import { getConsentTemplateOptions } from "../../services/consentTemplateService";
import {
  getFromOpenElisServer,
  postToOpenElisServerForBlob,
  postToOpenElisServerJsonResponse,
} from "../utils/Utils";
import messages from "../../languages/es.json";
jest.mock("../layout/Layout", () => {
  const React = require("react");
  return {
    ConfigurationContext: React.createContext({}),
    NotificationContext: React.createContext({}),
  };
});
jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerForBlob: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
}));
jest.mock("../../services/consentTemplateService", () => ({
  getConsentTemplateOptions: jest.fn(),
}));
jest.mock("../addOrder/OrderReferralRequest", () => () => null);
jest.mock("../addOrder/GpsCoordinatesCapture", () => () => null);
jest.mock("../storage/StorageLocationSelector", () => () => null);
const tests = [
  {
    sampleItemId: "20",
    sampleTypeId: "1",
    testId: "30",
    testName: "Extracción de ADN",
    canRemoveSample: true,
  },
  {
    sampleItemId: "21",
    sampleTypeId: "1",
    testId: "31",
    testName: "ATXN10",
    canRemoveSample: true,
  },
];
function Harness({ rows, possibleRows }) {
  const [form, setForm] = useState({
    accessionNumber: "LNG83",
    existingTests: rows,
    possibleTests: possibleRows,
    sampleTypes: [
      {
        id: "1",
        value: "Sangre",
      },
    ],
    sampleOrderItems: {
      labNo: "LNG83",
    },
  });
  const [samples, setSamples] = useState(() => buildSamplesFromOrder(form));
  const [removedIds, setRemovedIds] = useState([]);
  return (
    <>
      <EditSample
        samples={samples}
        setSamples={setSamples}
        orderFormValues={form}
        setOrderFormValues={setForm}
        hideExistingSamplesSections
        patientId="10"
        onRemoveExistingSample={(id) =>
          setRemovedIds((previous) => [...previous, id])
        }
      />
      <ConsentDownloadButton
        orderFormValues={form}
        samples={samples}
        patientId="10"
      />
      <output data-testid="order-payload">
        {JSON.stringify(buildModifyOrderPayload(form, samples, removedIds))}
      </output>
    </>
  );
}
const open = (rows = tests, possibleRows = []) =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <UserSessionDetailsContext.Provider
        value={{
          userSessionDetails: {},
        }}
      >
        <ConfigurationContext.Provider
          value={{
            configurationProperties: {
              GPS_ENABLED: "false",
              AUTOFILL_COLLECTION_DATE: "false",
            },
          }}
        >
          <NotificationContext.Provider
            value={{
              addNotification: jest.fn(),
              setNotificationVisible: jest.fn(),
            }}
          >
            <Harness rows={rows} possibleRows={possibleRows} />
          </NotificationContext.Provider>
        </ConfigurationContext.Provider>
      </UserSessionDetailsContext.Provider>
    </IntlProvider>,
  );
beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url === "/rest/user-sample-types")
      return callback([
        {
          id: "1",
          value: "Sangre",
        },
      ]);
    if (url.startsWith("/rest/sample-type-tests"))
      return callback({
        sampleTypeId: "1",
        tests: tests.map((row) => ({
          id: row.testId,
          name: row.testName,
        })),
        panels: [],
        additionalFields: [],
      });
    if (url.startsWith("/rest/patient-details"))
      return callback({
        patientPK: "10",
        firstName: "Paciente",
        lastName: "Prueba",
      });
    callback([]);
  });
  getConsentTemplateOptions.mockImplementation((callback) =>
    callback({
      sources: [
        {
          id: "sample.cug",
          path: "sampleXML.cug",
          scope: "samples",
        },
      ],
    }),
  );
  postToOpenElisServerForBlob.mockImplementation((_url, _body, callback) =>
    callback(new Blob(["pdf"])),
  );
  window.URL.createObjectURL = jest.fn(() => "blob:consent");
  window.URL.revokeObjectURL = jest.fn();
  jest.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
});
afterEach(() => jest.restoreAllMocks());

test("saved sample type stays locked and consent contains its selected tests", async () => {
  const page = open([tests[0]]);
  expect(page.container.querySelector('select[name="sampleId"]').disabled).toBe(
    true,
  );
  fireEvent.click(page.getByText(messages["consent.download.button"]));
  await wait(() =>
    expect(postToOpenElisServerForBlob).toHaveBeenCalledTimes(1),
  );
  const payload = JSON.parse(postToOpenElisServerForBlob.mock.calls[0][1]);
  expect(payload.selectedTests).toEqual(["Extracción de ADN"]);
  expect(postToOpenElisServerJsonResponse).not.toHaveBeenCalled();
});
test("removing the last saved sample preserves the first and sends its removal", () => {
  const page = open();
  fireEvent.click(page.getAllByText(messages["sample.remove.action"])[1]);
  const payload = JSON.parse(page.getByTestId("order-payload").textContent);
  expect(payload.removedSampleItemIds).toEqual(["21"]);
  expect(payload.existingTests[0].removeSample).toBe(false);
  expect(payload.existingTests[1].removeSample).toBe(true);
  expect(page.getAllByText(messages["sample.remove.action"])).toHaveLength(1);
});
test("removing the first sample preserves the remaining selected test in consent", async () => {
  const page = open();
  fireEvent.click(page.getAllByText(messages["sample.remove.action"])[0]);
  const payload = JSON.parse(page.getByTestId("order-payload").textContent);
  expect(payload.removedSampleItemIds).toEqual(["20"]);
  expect(payload.existingTests[1].removeSample).toBe(false);
  fireEvent.click(page.getByText(messages["consent.download.button"]));
  await wait(() =>
    expect(postToOpenElisServerForBlob).toHaveBeenCalledTimes(1),
  );
  expect(
    JSON.parse(postToOpenElisServerForBlob.mock.calls[0][1]).selectedTests,
  ).toEqual(["ATXN10"]);
});
test("an existing sample without analyses can be removed", () => {
  const page = open([], [{ ...tests[0] }]);
  fireEvent.click(page.getByText(messages["sample.remove.action"]));
  const payload = JSON.parse(page.getByTestId("order-payload").textContent);
  expect(payload.removedSampleItemIds).toEqual(["20"]);
  expect(payload.existingTests).toEqual([]);
  expect(payload.possibleTests.every((row) => row.add === false)).toBe(true);
});

test("deselecting an empty test submits cancellation while a test with results stays locked", async () => {
  const rows = [
    { ...tests[0], analysisId: "80", canCancel: true },
    { ...tests[1], sampleItemId: "20", analysisId: "81", canCancel: false },
  ];
  const possible = rows.map((row) => ({ ...row, canCancel: false }));
  const page = open(rows, possible);
  await wait(() =>
    expect(page.getByLabelText("Extracción de ADN")).toBeTruthy(),
  );
  const editable = page.getByLabelText("Extracción de ADN");
  const locked = page.getByLabelText("ATXN10");
  expect(editable.disabled).toBe(false);
  expect(locked.disabled).toBe(true);
  fireEvent.click(editable);
  await wait(() => {
    const payload = JSON.parse(page.getByTestId("order-payload").textContent);
    expect(payload.existingTests[0].canceled).toBe(true);
    expect(payload.existingTests[1].canceled).toBe(false);
    expect(payload.removedSampleItemIds).toEqual([]);
    expect(payload.possibleTests.every((row) => row.add === false)).toBe(true);
  });
});

test("selecting another test adds it to the saved sample without duplicating existing analyses", async () => {
  const rows = [{ ...tests[0], analysisId: "80", canCancel: true }];
  const possible = [
    { ...tests[0], canCancel: false },
    { ...tests[1], sampleItemId: "20", canCancel: false },
  ];
  const page = open(rows, possible);
  await wait(() => expect(page.getByLabelText("ATXN10")).toBeTruthy());
  const added = page.getByLabelText("ATXN10");
  expect(added.disabled).toBe(false);
  fireEvent.click(added);
  await wait(() => {
    const payload = JSON.parse(page.getByTestId("order-payload").textContent);
    expect(payload.possibleTests[0].add).toBe(false);
    expect(payload.possibleTests[1].add).toBe(true);
    expect(payload.possibleTests[1].sampleItemId).toBe("20");
    expect(payload.existingTests[0].canceled).toBe(false);
  });
});
