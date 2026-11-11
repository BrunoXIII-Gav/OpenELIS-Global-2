import React, { useState } from "react";
import { render, fireEvent } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import AddOrder from "./AddOrder";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import { createSampleOrderFormValues } from "../formModel/innitialValues/OrderEntryFormValues";
import messages from "../../languages/en.json";
import { getFromOpenElisServer } from "../utils/Utils";

jest.mock("../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
jest.mock("../common/AutoComplete", () => () => null);
jest.mock("../common/CustomDatePicker", () => () => null);
jest.mock("../common/CustomTimePicker", () => () => null);

const renderOrder = (isModifyOrder = false, sampleOrderItems = {}) => {
  const Wrapper = () => {
    const [values, setValues] = useState(() => ({
      ...createSampleOrderFormValues(),
      newAccessionNumber: "",
      sampleOrderItems: {
        ...createSampleOrderFormValues().sampleOrderItems,
        ...sampleOrderItems,
      },
    }));
    return (
      <IntlProvider locale="en" messages={messages}>
        <ConfigurationContext.Provider
          value={{ configurationProperties: { AccessionFormat: "PREFIXNUM" } }}
        >
          <NotificationContext.Provider
            value={{
              setNotificationVisible: jest.fn(),
              addNotification: jest.fn(),
            }}
          >
            <AddOrder
              orderFormValues={values}
              setOrderFormValues={setValues}
              samples={[]}
              error={() => null}
              changed={{}}
              setChanged={() => {}}
              isModifyOrder={isModifyOrder}
            />
            <output data-testid="mode">
              {String(values.sampleOrderItems.labNoGenerated)}
            </output>
          </NotificationContext.Provider>
        </ConfigurationContext.Provider>
      </IntlProvider>
    );
  };
  return render(<Wrapper />);
};

beforeEach(() => {
  jest.clearAllMocks();
  window.scrollTo = jest.fn();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url === "/rest/SamplePatientEntry") callback({ sampleOrderItems: {} });
    else if (
      url === "/rest/orders/laboratory-number/suggest" ||
      url === "/rest/SampleEntryGenerateScanProvider"
    )
      callback({ status: true, body: "LAB0000000000001" });
    else if (url.includes("Validation")) callback({ status: true });
    else callback([]);
  });
});

test("new orders hide the laboratory number and Generate without requesting a proposal", () => {
  const view = renderOrder();
  expect(view.container.querySelector("#labNo")).toBeNull();
  expect(
    view.queryByText(messages["sample.label.labnumber.generate"]),
  ).toBeNull();
  expect(
    getFromOpenElisServer.mock.calls.some(
      ([url]) =>
        url === "/rest/SampleEntryGenerateScanProvider" ||
        url === "/rest/orders/laboratory-number/suggest",
    ),
  ).toBe(false);
});

test("the existing modify-order generation path remains available", () => {
  const view = renderOrder(true);
  fireEvent.click(view.getByText(messages["sample.label.labnumber.generate"]));
  expect(getFromOpenElisServer).toHaveBeenCalledWith(
    "/rest/SampleEntryGenerateScanProvider",
    expect.any(Function),
  );
});

test.each([false, true])(
  "checkbox required state follows rules (modify: %p)",
  (isModifyOrder) => {
    const sampleOrderItems = {
      additionalFields: [
        { fieldKey: "genomic", displayName: "Genomic", fieldType: "BOOLEAN" },
        {
          fieldKey: "consent",
          displayName: "Consent",
          fieldType: "BOOLEAN",
          metadataJson: JSON.stringify({
            rules: {
              requiredWhen: [{ fieldKey: "genomic", operator: "isTrue" }],
            },
          }),
        },
      ],
      additionalFieldValues: { genomic: "false", consent: "false" },
    };
    getFromOpenElisServer.mockImplementation((url, callback) => {
      callback(url === "/rest/SamplePatientEntry" ? { sampleOrderItems } : []);
    });
    const view = renderOrder(isModifyOrder, sampleOrderItems);
    const consent = view.container.querySelector("#order-dynamic-consent");
    const label = view.container.querySelector(
      'label[for="order-dynamic-consent"]',
    );
    expect(consent.required).toBe(false);
    expect(label.textContent).toBe("Consent");
    fireEvent.click(view.getByLabelText("Genomic"));
    expect(consent.required).toBe(true);
    expect(label.textContent).toBe("Consent*");
    fireEvent.click(view.getByLabelText("Genomic"));
    expect(consent.required).toBe(false);
    expect(label.textContent).toBe("Consent");
  },
);
