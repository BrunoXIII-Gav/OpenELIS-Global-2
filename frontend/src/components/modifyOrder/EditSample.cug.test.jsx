import React, { useState } from "react";
import { fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import EditSample from "./EditSample";
import ConsentDownloadButton from "../addOrder/ConsentDownloadButton";
import { buildSamplesFromOrder } from "./modifyOrderSamples";
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
    cugCode: "5010.1",
    testId: "30",
    testName: "Extracción de ADN",
    canRemoveSample: true,
  },
  {
    sampleItemId: "21",
    sampleTypeId: "1",
    cugCode: "5010.2",
    testId: "31",
    testName: "ATXN10",
    canRemoveSample: true,
  },
];
function Harness({ rows }) {
  const [form, setForm] = useState({
    accessionNumber: "LNG83",
    existingTests: rows,
    possibleTests: [],
    sampleTypes: [{ id: "1", value: "Sangre" }],
    sampleOrderItems: { labNo: "LNG83" },
  });
  const [samples, setSamples] = useState(() => buildSamplesFromOrder(form));
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
          setSamples((previous) =>
            previous.filter((sample) => sample.existingSampleItemId !== id),
          )
        }
      />
      <ConsentDownloadButton
        orderFormValues={form}
        samples={samples}
        patientId="10"
      />
    </>
  );
}
const open = (rows = tests) =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <UserSessionDetailsContext.Provider value={{ userSessionDetails: {} }}>
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
            <Harness rows={rows} />
          </NotificationContext.Provider>
        </ConfigurationContext.Provider>
      </UserSessionDetailsContext.Provider>
    </IntlProvider>,
  );

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url === "/rest/user-sample-types")
      return callback([{ id: "1", value: "Sangre" }]);
    if (url.startsWith("/rest/sample-type-tests"))
      return callback({
        sampleTypeId: "1",
        tests: tests.map((row) => ({ id: row.testId, name: row.testName })),
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
      sources: [{ id: "sample.cug", path: "sampleXML.cug", scope: "samples" }],
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

test("real EditSample preserves the saved CUG for both the field and the consent PDF request", async () => {
  const page = open([tests[0]]);
  await wait(() =>
    expect(page.getByLabelText(messages["sample.cug.label"]).value).toBe(
      "5010.1",
    ),
  );
  expect(page.getByLabelText(messages["sample.cug.label"]).readOnly).toBe(true);
  fireEvent.click(page.getByText(messages["consent.download.button"]));
  await wait(() =>
    expect(postToOpenElisServerForBlob).toHaveBeenCalledTimes(1),
  );
  const payload = JSON.parse(postToOpenElisServerForBlob.mock.calls[0][1]);
  expect(payload.cug).toBe("5010.1");
  expect(payload.sourceValues["sample.cug"]).toBe("5010.1");
  expect(payload.selectedTests).toEqual(["Extracción de ADN"]);
  expect(postToOpenElisServerJsonResponse).not.toHaveBeenCalled();
});

test("removing an earlier sample keeps the remaining sample CUG on screen and in the consent", async () => {
  const page = open();
  await wait(() =>
    expect(
      page
        .getAllByLabelText(messages["sample.cug.label"])
        .map((input) => input.value),
    ).toEqual(["5010.1", "5010.2"]),
  );
  fireEvent.click(page.getAllByText(messages["sample.remove.action"])[0]);
  await wait(() =>
    expect(page.getByLabelText(messages["sample.cug.label"]).value).toBe(
      "5010.2",
    ),
  );
  fireEvent.click(page.getByText(messages["consent.download.button"]));
  await wait(() =>
    expect(postToOpenElisServerForBlob).toHaveBeenCalledTimes(1),
  );
  const payload = JSON.parse(postToOpenElisServerForBlob.mock.calls[0][1]);
  expect(payload.cug).toBe("5010.2");
  expect(payload.sourceValues["sample.cug"]).toBe("5010.2");
  expect(payload.selectedTests).toEqual(["ATXN10"]);
});
