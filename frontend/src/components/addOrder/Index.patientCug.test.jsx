import React from "react";
import { fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import Index from "./Index";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import messages from "../../languages/en.json";
import {
  getFromOpenElisServer,
  postToOpenElisServerFullResponse,
} from "../utils/Utils";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerFullResponse: jest.fn(),
}));
jest.mock("../common/PageBreadCrumb", () => () => null);
jest.mock("./ConsentDownloadButton", () => () => null);
jest.mock("../formModel/validationSchema/OrderEntryValidationSchema", () => ({
  validate: () => Promise.resolve({}),
}));
jest.mock("./PatientInfo", () => ({ setOrderFormValues }) => (
  <button
    onClick={() =>
      setOrderFormValues((previous) => ({
        ...previous,
        patientUpdateStatus: "UPDATE",
        patientProperties: {
          ...previous.patientProperties,
          patientPK: "10",
          cugPrefix: "5001",
          patientUpdateStatus: "UPDATE",
        },
      }))
    }
  >
    Save test patient
  </button>
));
jest.mock("./AddSample", () => ({ setSamples }) => (
  <button
    onClick={() =>
      setSamples([
        {
          index: 0,
          sampleTypeId: "1",
          panels: [],
          tests: [{ id: "7" }],
          referralItems: [],
          sampleXML: {
            cug: "5001.1",
            cugProvisional: true,
            collectionDate: "",
            collectionTime: "",
          },
        },
      ])
    }
  >
    Select sample
  </button>
));
jest.mock("./AddOrder", () => () => <div>Confirm order</div>);

beforeEach(() => {
  jest.clearAllMocks();
  window.scrollTo = jest.fn();
  window.open = jest.fn();
  getFromOpenElisServer.mockImplementation((url, callback) =>
    callback({ allowed: true }),
  );
});

const notifications = {
  setNotificationVisible: jest.fn(),
  addNotification: jest.fn(),
};

const renderOrder = () =>
  render(
    <IntlProvider locale="en" messages={messages}>
      <ConfigurationContext.Provider
        value={{
          configurationProperties: {
            SHOW_ORDER_PROGRAM_ON_ORDER_ENTRY: "false",
          },
        }}
      >
        <NotificationContext.Provider value={notifications}>
          <Index />
        </NotificationContext.Provider>
      </ConfigurationContext.Provider>
    </IntlProvider>,
  );

test("requires saving patient, submits the displayed CUG and displays that same saved suffix", async () => {
  postToOpenElisServerFullResponse.mockImplementation(
    (url, payload, callback) =>
      callback({
        status: 200,
        json: async () => ({
          assignedCugs: ["5001.1"],
          sampleOrderItems: { labNo: "LAB0000000000002" },
        }),
      }),
  );
  const view = renderOrder();
  const next = () =>
    view.getByText(messages["next.action.button"]).closest("button");
  expect(next().disabled).toBe(true);
  fireEvent.click(view.getByText(messages["order.step.add.request"]));
  expect(view.queryByText("Select sample")).toBeNull();
  fireEvent.click(view.getByText("Save test patient"));
  await wait(() => expect(next().disabled).toBe(false));
  fireEvent.click(next());
  fireEvent.click(view.getByText("Select sample"));
  fireEvent.click(next());
  await wait(() =>
    expect(
      view.getByText(messages["label.button.submit"]).closest("button")
        .disabled,
    ).toBe(false),
  );
  fireEvent.click(view.getByText(messages["label.button.submit"]));
  await wait(() => expect(view.getByText("CUG: 5001.1")).toBeTruthy());
  expect(view.getByText(/LAB0000000000002/)).toBeTruthy();
  fireEvent.click(view.getByText(messages["print.barcode"]));
  expect(window.open).toHaveBeenCalledWith(
    expect.stringContaining("labNo=LAB0000000000002"),
  );
  const payload = JSON.parse(postToOpenElisServerFullResponse.mock.calls[0][1]);
  expect(payload.patientProperties.patientPK).toBe("10");
  expect(payload.sampleXML).toContain("cug='5001.1' cugProvisional='true'");
});

test("a manual laboratory number conflict preserves the form and shows its translated message", async () => {
  postToOpenElisServerFullResponse.mockImplementation(
    (url, payload, callback) =>
      callback({
        status: 409,
        headers: new Headers({
          "X-OpenELIS-Error-Code": "order.labnumber.conflict",
        }),
      }),
  );
  const view = renderOrder();
  fireEvent.click(view.getByText("Save test patient"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  fireEvent.click(view.getByText("Select sample"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  const submit = view
    .getByText(messages["label.button.submit"])
    .closest("button");
  await wait(() => expect(submit.disabled).toBe(false));
  fireEvent.click(submit);
  await wait(() =>
    expect(notifications.addNotification).toHaveBeenCalledWith(
      expect.objectContaining({
        message: messages["order.labnumber.conflict"],
      }),
    ),
  );
  expect(view.getByText("Confirm order")).toBeTruthy();
});

test("a conflicting CUG preserves the order and never retries with another suffix", async () => {
  postToOpenElisServerFullResponse.mockImplementation(
    (url, payload, callback) =>
      callback({
        status: 409,
        headers: new Headers({
          "X-OpenELIS-Error-Code": "sample.cug.conflict",
        }),
      }),
  );
  const view = renderOrder();
  const next = () => view.getByText(messages["next.action.button"]);
  fireEvent.click(view.getByText("Save test patient"));
  fireEvent.click(next());
  fireEvent.click(view.getByText("Select sample"));
  fireEvent.click(next());
  const submit = () =>
    view.getByText(messages["label.button.submit"]).closest("button");
  await wait(() => expect(submit().disabled).toBe(false));
  fireEvent.click(submit());
  await wait(() =>
    expect(notifications.addNotification).toHaveBeenCalledWith(
      expect.objectContaining({ message: messages["sample.cug.conflict"] }),
    ),
  );
  expect(view.getByText("Confirm order")).toBeTruthy();
  expect(postToOpenElisServerFullResponse).toHaveBeenCalledTimes(1);
  // A deliberate retry retains both the patient and exactly the displayed CUG.
  await wait(() => expect(submit().disabled).toBe(false));
  fireEvent.click(submit());
  await wait(() =>
    expect(postToOpenElisServerFullResponse).toHaveBeenCalledTimes(2),
  );
  for (const call of postToOpenElisServerFullResponse.mock.calls) {
    const payload = JSON.parse(call[1]);
    expect(payload.patientProperties.patientPK).toBe("10");
    expect(payload.sampleXML).toContain("cug='5001.1' cugProvisional='true'");
    expect(payload.sampleXML).not.toContain("5001.2");
  }
});
