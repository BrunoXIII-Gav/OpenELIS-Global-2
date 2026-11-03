import React from "react";
import { act, fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import Index from "./Index";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import messages from "../../languages/en.json";
import {
  getFromOpenElisServer,
  postToOpenElisServerForBlob,
  postToOpenElisServerFullResponse,
} from "../utils/Utils";
import { getConsentTemplateOptions } from "../../services/consentTemplateService";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerForBlob: jest.fn(),
  postToOpenElisServerFullResponse: jest.fn(),
}));
jest.mock("../../services/consentTemplateService", () => ({
  getConsentTemplateOptions: jest.fn(),
}));
jest.mock("../common/PageBreadCrumb", () => () => null);
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
          tests: [{ id: "7", name: "Test sample" }],
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
jest.mock("./AddOrder", () => ({ setOrderFormValues }) => (
  <div>
    Confirm order
    <button
      onClick={() =>
        setOrderFormValues((previous) => ({
          ...previous,
          rememberSiteAndRequester: true,
        }))
      }
    >
      Remember requester
    </button>
  </div>
));

beforeEach(() => {
  jest.clearAllMocks();
  postToOpenElisServerFullResponse.mockReset();
  window.scrollTo = jest.fn();
  window.open = jest.fn();
  window.URL.createObjectURL = jest.fn(() => "blob:consent");
  window.URL.revokeObjectURL = jest.fn();
  jest.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  getConsentTemplateOptions.mockImplementation((callback) =>
    callback({ sources: [] }),
  );
  postToOpenElisServerForBlob.mockImplementation((_url, _body, callback) =>
    callback(new Blob(["pdf"], { type: "application/pdf" }), {
      headers: new Headers({ "X-OpenELIS-Consent-Token": "server-receipt" }),
    }),
  );
  getFromOpenElisServer.mockImplementation((url, callback) =>
    callback({ allowed: true }),
  );
});
afterEach(() => jest.restoreAllMocks());

const downloadConsent = async (view) => {
  const previousDownloads = window.URL.createObjectURL.mock.calls.length;
  fireEvent.click(view.getByText(messages["consent.download.button"]));
  await wait(() =>
    expect(window.URL.createObjectURL).toHaveBeenCalledTimes(
      previousDownloads + 1,
    ),
  );
};

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
  // Completing the form enables Submit, but downloading is checked on click.
  fireEvent.click(view.getByText(messages["label.button.submit"]));
  expect(postToOpenElisServerFullResponse).not.toHaveBeenCalled();
  expect(notifications.addNotification).toHaveBeenCalledWith(
    expect.objectContaining({
      message: messages["consent.download.requiredBeforeSubmit"],
    }),
  );
  expect(
    view.getByText(messages["label.button.submit"]).closest("button").disabled,
  ).toBe(false);
  await downloadConsent(view);
  fireEvent.click(view.getByText(messages["label.button.submit"]));
  await wait(() => expect(view.getByText("CUG: 5001.1")).toBeTruthy());
  expect(view.getByText(/LAB0000000000002/)).toBeTruthy();
  fireEvent.click(view.getByText(messages["print.barcode"]));
  expect(window.open).toHaveBeenCalledWith(
    expect.stringContaining("labNo=LAB0000000000002"),
  );
  const payload = JSON.parse(postToOpenElisServerFullResponse.mock.calls[0][1]);
  expect(payload.consentDownloadToken).toBe("server-receipt");
  expect(payload.sampleOrderItems.labNo).toBe("");
  expect(payload.sampleOrderItems.labNoGenerated).toBe(true);
  expect(payload.patientProperties.patientPK).toBe("10");
  expect(payload.sampleXML).toContain("cug='5001.1' cugProvisional='true'");
});

test("blocks repeated clicks until the server response has been processed", async () => {
  let resolveResponse;
  postToOpenElisServerFullResponse.mockImplementation(
    (url, payload, callback) => {
      resolveResponse = callback;
    },
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
  await downloadConsent(view);
  fireEvent.click(submit);
  fireEvent.click(submit);
  expect(submit.disabled).toBe(true);
  expect(postToOpenElisServerFullResponse).toHaveBeenCalledTimes(1);
  await act(async () =>
    resolveResponse({
      status: 409,
      headers: new Headers({ "X-OpenELIS-Error-Code": "sample.cug.conflict" }),
    }),
  );
  await wait(() => expect(submit.disabled).toBe(false));
  fireEvent.click(submit);
  expect(postToOpenElisServerFullResponse).toHaveBeenCalledTimes(2);
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
  await downloadConsent(view);
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

test("failed and pending consent downloads do not satisfy the submit requirement; a successful retry does", async () => {
  let finish;
  postToOpenElisServerForBlob
    .mockImplementationOnce((_url, _body, _callback, error) =>
      error(new Error("Failed")),
    )
    .mockImplementationOnce((_url, _body, callback) => {
      finish = callback;
    });
  const view = renderOrder();
  fireEvent.click(view.getByText("Save test patient"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  fireEvent.click(view.getByText("Select sample"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  const submit = view
    .getByText(messages["label.button.submit"])
    .closest("button");
  await wait(() => expect(submit.disabled).toBe(false));
  fireEvent.click(view.getByText(messages["consent.download.button"]));
  await wait(() =>
    expect(notifications.addNotification).toHaveBeenCalledWith(
      expect.objectContaining({ message: messages["consent.download.error"] }),
    ),
  );
  fireEvent.click(submit);
  expect(postToOpenElisServerFullResponse).not.toHaveBeenCalled();
  expect(notifications.addNotification).toHaveBeenLastCalledWith(
    expect.objectContaining({
      message: messages["consent.download.requiredBeforeSubmit"],
    }),
  );
  fireEvent.click(view.getByText(messages["consent.download.button"]));
  await wait(() => expect(typeof finish).toBe("function"));
  fireEvent.click(submit);
  expect(postToOpenElisServerFullResponse).not.toHaveBeenCalled();
  expect(submit.disabled).toBe(false);
  await act(async () =>
    finish(new Blob(["pdf"]), {
      headers: new Headers({ "X-OpenELIS-Consent-Token": "server-receipt" }),
    }),
  );
  fireEvent.click(view.getByText(messages["back.action.button"]));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  await act(async () =>
    fireEvent.click(view.getByText(messages["label.button.submit"])),
  );
  expect(postToOpenElisServerFullResponse).toHaveBeenCalledTimes(1);
});

test("starting another order requires its own consent download", async () => {
  postToOpenElisServerFullResponse.mockImplementation((_url, _body, callback) =>
    callback({ status: 200, json: async () => ({ assignedCugs: ["5001.1"] }) }),
  );
  const view = renderOrder();
  fireEvent.click(view.getByText("Save test patient"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  fireEvent.click(view.getByText("Select sample"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  fireEvent.click(view.getByText("Remember requester"));
  await downloadConsent(view);
  fireEvent.click(view.getByText(messages["label.button.submit"]));
  await wait(() =>
    expect(view.getByText(messages["request.samesite.order"])).toBeTruthy(),
  );
  fireEvent.click(view.getByText(messages["request.samesite.order"]));
  fireEvent.click(view.getByText("Save test patient"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  fireEvent.click(view.getByText("Select sample"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  const submit = view
    .getByText(messages["label.button.submit"])
    .closest("button");
  await wait(() => expect(submit.disabled).toBe(false));
  fireEvent.click(submit);
  expect(postToOpenElisServerFullResponse).toHaveBeenCalledTimes(1);
  expect(notifications.addNotification).toHaveBeenLastCalledWith(
    expect.objectContaining({
      message: messages["consent.download.requiredBeforeSubmit"],
    }),
  );
});

test("a PDF response without a server receipt cannot authorize submission", async () => {
  postToOpenElisServerForBlob.mockImplementation((_url, _body, callback) =>
    callback(new Blob(["pdf"]), { headers: new Headers() }),
  );
  const view = renderOrder();
  fireEvent.click(view.getByText("Save test patient"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  fireEvent.click(view.getByText("Select sample"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  fireEvent.click(view.getByText(messages["consent.download.button"]));
  await wait(() =>
    expect(notifications.addNotification).toHaveBeenCalledWith(
      expect.objectContaining({ message: messages["consent.download.error"] }),
    ),
  );
  fireEvent.click(view.getByText(messages["label.button.submit"]));
  expect(postToOpenElisServerFullResponse).not.toHaveBeenCalled();
  expect(window.URL.createObjectURL).not.toHaveBeenCalled();
});

test("an expired server receipt shows the consent instruction and requires downloading again", async () => {
  postToOpenElisServerFullResponse.mockImplementation((_url, _body, callback) =>
    callback({
      status: 428,
      headers: new Headers({
        "X-OpenELIS-Error-Code": "consent.download.requiredBeforeSubmit",
      }),
    }),
  );
  const view = renderOrder();
  fireEvent.click(view.getByText("Save test patient"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  fireEvent.click(view.getByText("Select sample"));
  fireEvent.click(view.getByText(messages["next.action.button"]));
  await downloadConsent(view);
  fireEvent.click(view.getByText(messages["label.button.submit"]));
  await wait(() =>
    expect(notifications.addNotification).toHaveBeenLastCalledWith(
      expect.objectContaining({
        message: messages["consent.download.requiredBeforeSubmit"],
      }),
    ),
  );
  fireEvent.click(view.getByText(messages["label.button.submit"]));
  expect(postToOpenElisServerFullResponse).toHaveBeenCalledTimes(1);
  await downloadConsent(view);
  fireEvent.click(view.getByText(messages["label.button.submit"]));
  expect(postToOpenElisServerFullResponse).toHaveBeenCalledTimes(2);
});

test.each(["sample.cug.conflict", "sample.cug.patient.prefix.mismatch"])(
  "%s preserves the order and never retries with another suffix",
  async (code) => {
    postToOpenElisServerFullResponse.mockImplementation(
      (url, payload, callback) =>
        callback({
          status: 409,
          headers: new Headers({
            "X-OpenELIS-Error-Code": code,
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
    await downloadConsent(view);
    fireEvent.click(submit());
    await wait(() =>
      expect(notifications.addNotification).toHaveBeenCalledWith(
        expect.objectContaining({ message: messages[code] }),
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
  },
);
