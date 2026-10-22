import React, { useState } from "react";
import { render, fireEvent, wait } from "@testing-library/react";
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

const renderOrder = (isModifyOrder = false) => {
  const Wrapper = () => {
    const [values, setValues] = useState(() => ({
      ...createSampleOrderFormValues(),
      newAccessionNumber: "",
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

test("Generate proposes a number without allocation; manual editing switches off automatic replacement", async () => {
  const view = renderOrder();
  fireEvent.click(view.getByText(messages["sample.label.labnumber.generate"]));
  await wait(() => expect(view.getByTestId("mode").textContent).toBe("true"));
  expect(getFromOpenElisServer).toHaveBeenCalledWith(
    "/rest/orders/laboratory-number/suggest",
    expect.any(Function),
  );
  expect(
    getFromOpenElisServer.mock.calls.some(
      ([url]) => url === "/rest/SampleEntryGenerateScanProvider",
    ),
  ).toBe(false);
  const field = view.container.querySelector("#labNo");
  expect(field.value).toBe("LAB0000000000001");
  fireEvent.change(field, { target: { value: "LAB0000000000025" } });
  expect(view.getByTestId("mode").textContent).toBe("false");
  expect(field.value).toBe("LAB0000000000025");
  expect(
    getFromOpenElisServer.mock.calls.some(([url]) =>
      url.includes("ignoreUsage=true"),
    ),
  ).toBe(true);
});

test("the existing modify-order generation path remains available", () => {
  const view = renderOrder(true);
  fireEvent.click(view.getByText(messages["sample.label.labnumber.generate"]));
  expect(getFromOpenElisServer).toHaveBeenCalledWith(
    "/rest/SampleEntryGenerateScanProvider",
    expect.any(Function),
  );
});
