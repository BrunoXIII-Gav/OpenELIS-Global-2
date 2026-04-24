import React from "react";
import { fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import SampleType from "./SampleType";
import messages from "../../languages/en.json";
import { getFromOpenElisServer } from "../utils/Utils";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
}));

jest.mock("../addOrder/OrderReferralRequest", () => () => null);

jest.mock("../storage/StorageLocationSelector", () => () => (
  <div data-testid="storage-location-selector" />
));

jest.mock("./GpsCoordinatesCapture", () => () => (
  <div data-testid="gps-capture" />
));

jest.mock("../common/CustomCheckBox", () => ({ id, onChange, label }) => (
  <label htmlFor={id}>
    {label}
    <input
      id={id}
      type="checkbox"
      onChange={(event) => onChange(event.target.checked)}
    />
  </label>
));

jest.mock("../common/CustomDatePicker", () => ({ id, onChange, value }) => (
  <input
    id={id}
    data-testid={id}
    value={value || ""}
    onChange={(event) => onChange(event.target.value)}
  />
));

jest.mock("../common/CustomTimePicker", () => ({ id, onChange, value }) => (
  <input
    id={id}
    data-testid={id}
    value={value || ""}
    onChange={(event) => onChange(event.target.value)}
  />
));

jest.mock("../common/CustomTextInput", () => ({ id, onChange, value }) => (
  <input
    id={id}
    data-testid={id}
    value={value || ""}
    onChange={(event) => onChange(event.target.value)}
  />
));

jest.mock("../common/CustomSelect", () => ({ id, options = [], value, onChange }) => (
  <select id={id} data-testid={id} value={value || ""} onChange={(event) => onChange(event.target.value)}>
    <option value="">Select</option>
    {options.map((option, index) => (
      <option key={`${id}_${index}`} value={option.id}>
        {option.value}
      </option>
    ))}
  </select>
));

const userSessionDetailsContextValue = {
  userSessionDetails: {
    firstName: "Test",
    lastName: "User",
  },
};

const configurationContextValue = {
  configurationProperties: {
    AUTOFILL_COLLECTION_DATE: "false",
    GPS_ENABLED: "false",
    currentDateAsText: "2026-03-21",
    currentTimeAsText: "10:30",
  },
};

const notificationContextValue = {
  setNotificationVisible: jest.fn(),
  addNotification: jest.fn(),
};

const baseSample = {
  index: 1,
  sampleRejected: false,
  rejectionReason: "",
  requestReferralEnabled: false,
  referralItems: [],
  sampleTypeId: "",
  sampleXML: null,
  panels: [],
  tests: [],
  additionalFields: [],
};

const renderComponent = (overrideProps = {}) => {
  const props = {
    index: 0,
    rejectSampleReasons: [],
    sample: baseSample,
    setSample: jest.fn(),
    sampleTypeObject: jest.fn(),
    error: null,
    ...overrideProps,
  };

  const rendered = render(
    <IntlProvider locale="en" messages={messages}>
      <UserSessionDetailsContext.Provider value={userSessionDetailsContextValue}>
        <ConfigurationContext.Provider value={configurationContextValue}>
          <NotificationContext.Provider value={notificationContextValue}>
            <SampleType {...props} />
          </NotificationContext.Provider>
        </ConfigurationContext.Provider>
      </UserSessionDetailsContext.Provider>
    </IntlProvider>,
  );

  return {
    ...rendered,
    props,
  };
};

describe("SampleType additional fields", () => {
  beforeEach(() => {
    jest.clearAllMocks();

    getFromOpenElisServer.mockImplementation((url, callback) => {
      if (url === "/rest/referral-reasons") {
        callback([]);
        return;
      }
      if (url === "/rest/referral-organizations") {
        callback([]);
        return;
      }
      if (url === "/rest/user-sample-types") {
        callback([{ id: "1", value: "Blood" }]);
        return;
      }
      if (url === "/rest/UomCreate") {
        callback({ existingUomList: [] });
        return;
      }
      if (url === "/rest/sample-type-tests?sampleType=1") {
        callback({
          sampleTypeId: "1",
          tests: [],
          panels: [],
          additionalFields: [
            {
              id: 10,
              sampleTypeId: "1",
              fieldKey: "batch_code",
              displayName: "Batch Code",
              fieldType: "TEXT",
              required: true,
              active: true,
              defaultValue: "B-01",
              options: [],
            },
          ],
        });
        return;
      }

      callback([]);
    });
  });

  test("loads and applies additional fields defaults for selected sample type", async () => {
    const { container, props } = renderComponent();

    await wait(() => {
      expect(getFromOpenElisServer).toHaveBeenCalledWith(
        "/rest/user-sample-types",
        expect.any(Function),
      );
    });

    fireEvent.change(container.querySelector("#sampleId_0"), {
      target: { value: "1" },
    });

    await wait(() => {
      expect(getFromOpenElisServer).toHaveBeenCalledWith(
        "/rest/sample-type-tests?sampleType=1",
        expect.any(Function),
      );
    });

    await wait(() => {
      const additionalFieldInput = container.querySelector(
        "#additional_field_0_batch_code",
      );
      expect(additionalFieldInput).toBeTruthy();
      expect(additionalFieldInput.value).toBe("B-01");
    });

    const hasAdditionalFieldCallback = props.sampleTypeObject.mock.calls.some(
      ([arg]) =>
        Object.prototype.hasOwnProperty.call(arg, "additionalFields") &&
        Array.isArray(arg.additionalFields) &&
        arg.additionalFields.length === 1,
    );
    expect(hasAdditionalFieldCallback).toBe(true);

    const sampleXmlCalls = props.sampleTypeObject.mock.calls.filter(([arg]) =>
      Object.prototype.hasOwnProperty.call(arg, "sampleXML"),
    );
    const lastSampleXmlCall = sampleXmlCalls[sampleXmlCalls.length - 1][0];
    expect(lastSampleXmlCall.sampleXML.additionalFieldValues.batch_code).toBe(
      "B-01",
    );
  });

  test("updates additional field value and emits updated sampleXML", async () => {
    const { container, props } = renderComponent();

    fireEvent.change(container.querySelector("#sampleId_0"), {
      target: { value: "1" },
    });

    await wait(() => {
      const additionalFieldInput = container.querySelector(
        "#additional_field_0_batch_code",
      );
      expect(additionalFieldInput).toBeTruthy();
    });

    fireEvent.change(container.querySelector("#additional_field_0_batch_code"), {
      target: { value: "B-77" },
    });

    await wait(() => {
      const sampleXmlCalls = props.sampleTypeObject.mock.calls.filter(([arg]) =>
        Object.prototype.hasOwnProperty.call(arg, "sampleXML"),
      );
      const lastSampleXmlCall = sampleXmlCalls[sampleXmlCalls.length - 1][0];
      expect(lastSampleXmlCall.sampleXML.additionalFieldValues.batch_code).toBe(
        "B-77",
      );
    });
  });
});
