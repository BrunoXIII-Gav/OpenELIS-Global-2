import React, { useState } from "react";
import { act, fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import AddOrder from "./AddOrder";
import ConsentDownloadButton from "./ConsentDownloadButton";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import { createSampleOrderFormValues } from "../formModel/innitialValues/OrderEntryFormValues";
import {
  getFromOpenElisServer,
  postToOpenElisServerForBlob,
} from "../utils/Utils";
import messages from "../../languages/es.json";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerForBlob: jest.fn(),
}));
jest.mock("../common/AutoComplete", () => () => null);
jest.mock("../common/CustomDatePicker", () => () => null);
jest.mock("../common/CustomTimePicker", () => () => null);

const fields = [
  {
    id: 1,
    fieldKey: "bankChecked",
    displayName: "Solicita banco",
    fieldType: "BOOLEAN",
    active: true,
  },
  {
    id: 2,
    fieldKey: "bank",
    displayName: "Banco ADN",
    fieldType: "DOCUMENT",
    active: true,
    required: true,
    metadataJson: JSON.stringify({
      document: {
        mode: "CONSENT",
        templateId: "CONSENT-bank",
        buttonLabel: "Descargar banco",
      },
      rules: { visibleWhen: [{ fieldKey: "bankChecked", operator: "istrue" }] },
    }),
  },
  {
    id: 3,
    fieldKey: "research",
    displayName: "Investigación",
    fieldType: "DOCUMENT",
    active: true,
    metadataJson: JSON.stringify({
      document: {
        mode: "CONSENT",
        templateId: "CONSENT-research",
        buttonLabel: "Descargar investigación",
      },
    }),
  },
  {
    id: 4,
    fieldKey: "signed",
    displayName: "Adjunto firmado",
    fieldType: "DOCUMENT",
    active: true,
  },
];
const items = {
  ...createSampleOrderFormValues().sampleOrderItems,
  additionalFields: fields,
  additionalFieldValues: { bankChecked: "false" },
};
const samples = [
  { tests: [{ id: "20", name: "ADN" }], sampleXML: { cug: "5000.1" } },
];

beforeEach(() => {
  jest.clearAllMocks();
  window.scrollTo = jest.fn();
  window.URL.createObjectURL = jest.fn(() => "blob:consent");
  window.URL.revokeObjectURL = jest.fn();
  jest.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url === "/rest/reports/consent-template/options")
      callback({
        sources: [
          {
            id: "orderAdditional.bankChecked",
            path: "sampleOrderItems.additionalFieldValues.bankChecked",
            fieldType: "BOOLEAN",
            format: "additional",
          },
        ],
      });
    else if (url === "/rest/SamplePatientEntry")
      callback({ sampleOrderItems: items });
    else if (url.startsWith("/rest/patient-details"))
      callback({ patientPK: "10", firstName: "Ana", lastName: "Pérez" });
    else callback([]);
  });
  postToOpenElisServerForBlob.mockImplementation((_url, body, callback) => {
    const payload = JSON.parse(body);
    callback(new Blob(["pdf"]), {
      headers: new Headers({
        "X-OpenELIS-Consent-Token": payload.orderFieldKey || "general",
      }),
    });
  });
});
afterEach(() => jest.restoreAllMocks());

const open = (modify = false) => {
  const Form = () => {
    const [values, setValues] = useState({
      ...createSampleOrderFormValues(),
      ...(modify ? { accessionNumber: "LAB1" } : {}),
      patientProperties: {
        patientPK: "10",
        firstName: "Ana",
        lastName: "Pérez",
      },
      sampleOrderItems: items,
    });
    const [general, setGeneral] = useState("");
    return (
      <>
        <AddOrder
          orderFormValues={values}
          setOrderFormValues={setValues}
          samples={samples}
          isModifyOrder={modify}
          patientId="10"
          error={() => null}
          changed={{}}
          setChanged={() => {}}
        />
        <ConsentDownloadButton
          orderFormValues={values}
          samples={samples}
          onDownloaded={setGeneral}
        />
        <output data-testid="receipts">
          {JSON.stringify(
            values.sampleOrderItems.additionalFieldConsentTokens || {},
          )}
        </output>
        <output data-testid="general">{general}</output>
      </>
    );
  };
  return render(
    <IntlProvider locale="es" messages={messages}>
      <ConfigurationContext.Provider value={{ configurationProperties: {} }}>
        <NotificationContext.Provider
          value={{
            addNotification: jest.fn(),
            setNotificationVisible: jest.fn(),
          }}
        >
          <Form />
        </NotificationContext.Provider>
      </ConfigurationContext.Provider>
    </IntlProvider>,
  );
};

test.each([false, true])(
  "multiple consents use their own template, conditions and receipt (editing: %s)",
  async (modify) => {
    const view = open(modify);
    expect(view.queryByText("Descargar banco")).toBeNull();
    expect(view.getByText(messages["label.button.uploadfile"])).toBeTruthy();
    fireEvent.click(view.getByLabelText("Solicita banco"));
    await act(async () => fireEvent.click(view.getByText("Descargar banco")));
    await wait(() =>
      expect(JSON.parse(view.getByTestId("receipts").textContent)).toEqual({
        bank: "bank",
      }),
    );
    const bank = JSON.parse(postToOpenElisServerForBlob.mock.calls[0][1]);
    expect(bank).toMatchObject({
      templateId: "CONSENT-bank",
      orderFieldKey: "bank",
      orderPatientId: "10",
      orderAdditionalFieldValues: { bankChecked: "true" },
      selectedTests: ["ADN"],
      cug: "5000.1",
    });
    expect(bank.orderAccessionNumber).toBe(modify ? "LAB1" : "");
    expect(view.getByTestId("general").textContent).toBe("");
    await act(async () =>
      fireEvent.click(view.getByText("Descargar investigación")),
    );
    expect(
      JSON.parse(postToOpenElisServerForBlob.mock.calls[1][1]).templateId,
    ).toBe("CONSENT-research");
    await act(async () =>
      fireEvent.click(view.getByText(messages["consent.download.button"])),
    );
    expect(view.getByTestId("general").textContent).toBe("general");
    expect(
      JSON.parse(postToOpenElisServerForBlob.mock.calls[2][1]).templateId,
    ).toBeUndefined();
    expect(JSON.parse(view.getByTestId("receipts").textContent)).toEqual({
      bank: "bank",
      research: "research",
    });
  },
);
