import React, { useState } from "react";
import { fireEvent, render } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import AddOrder from "./AddOrder";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import { createSampleOrderFormValues } from "../formModel/innitialValues/OrderEntryFormValues";
import { getFromOpenElisServer } from "../utils/Utils";
import messages from "../../languages/en.json";

jest.mock("../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
jest.mock("../common/AutoComplete", () => (props) => (
  <select
    id={props.id}
    aria-label="Requester"
    onChange={(event) => props.onSelect(event.target.value)}
  >
    <option value="">Choose</option>
    <option value="10">Doctor</option>
    <option value="20">Other professional</option>
  </select>
));
jest.mock("../common/CustomDatePicker", () => () => null);
jest.mock("../common/CustomTimePicker", () => () => null);

const profiles = {
  10: [
    { fieldKey: "RNE", displayName: "RNE", sortOrder: 2 },
    { fieldKey: "CMP", displayName: "CMP", sortOrder: 1 },
  ],
  20: [{ fieldKey: "LICENSE", displayName: "License", sortOrder: 1 }],
};

beforeEach(() => {
  jest.clearAllMocks();
  window.scrollTo = jest.fn();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.startsWith("/rest/practitioner?")) {
      const id = url.split("=")[1];
      callback({
        id,
        person: { id, firstName: "Professional" },
        professionalProfileCode: `PROFILE_${id}`,
      });
    } else if (url.startsWith("/rest/providers/form-config?")) {
      const id = new URLSearchParams(url.split("?")[1]).get("providerId");
      callback({
        fields: profiles[id].map((field) => ({
          ...field,
          fieldType: "TEXT",
          active: true,
          showInOrderEntry: true,
          currentValue: `value-${field.fieldKey}`,
        })),
      });
    } else if (url === "/rest/SamplePatientEntry")
      callback({
        sampleOrderItems: {
          additionalFields: [
            {
              id: 1,
              fieldKey: "diagnosis",
              displayName: "Diagnosis",
              fieldType: "TEXT",
              active: true,
              sortOrder: 2,
            },
          ],
        },
      });
    else callback([]);
  });
});

const open = (modify = false, visible = true) => {
  const Form = () => {
    const [values, setValues] = useState(() => ({
      ...createSampleOrderFormValues(),
      sampleOrderItems: {
        ...createSampleOrderFormValues().sampleOrderItems,
        fixedFieldConfigs: [
          { fieldKey: "requesterSearch", sortOrder: 1, visible: true },
          { fieldKey: "requesterProfileFields", sortOrder: 2, visible },
          { fieldKey: "providerFirstName", sortOrder: 4, visible: true },
        ],
        additionalFields: [
          {
            id: 1,
            fieldKey: "diagnosis",
            displayName: "Diagnosis",
            fieldType: "TEXT",
            active: true,
            sortOrder: 2,
          },
        ],
      },
    }));
    return (
      <AddOrder
        orderFormValues={values}
        setOrderFormValues={setValues}
        samples={[]}
        error={() => null}
        changed={{}}
        setChanged={() => {}}
        isModifyOrder={modify}
      />
    );
  };
  return render(
    <IntlProvider locale="en" messages={messages}>
      <ConfigurationContext.Provider value={{ configurationProperties: {} }}>
        <NotificationContext.Provider
          value={{
            setNotificationVisible: jest.fn(),
            addNotification: jest.fn(),
          }}
        >
          <Form />
        </NotificationContext.Provider>
      </ConfigurationContext.Provider>
    </IntlProvider>,
  );
};

test.each([false, true])(
  "the configured slot keeps profile fields together in profile order and replaces them when the requester changes (modify: %p)",
  (modify) => {
    const view = open(modify);
    expect(
      view.container.querySelector('[id^="requester-profile-"]'),
    ).toBeNull();
    fireEvent.change(view.container.querySelector("#requesterId"), {
      target: { value: "10" },
    });
    const orderedIds = () =>
      Array.from(view.container.querySelectorAll("input[id], select[id]"))
        .map((element) => element.id)
        .filter((id) =>
          [
            "requesterId",
            "requester-profile-CMP",
            "requester-profile-RNE",
            "requester-profile-LICENSE",
            "order-dynamic-diagnosis",
            "requesterFirstName",
          ].includes(id),
        );
    expect(orderedIds()).toEqual([
      "requesterId",
      "requester-profile-CMP",
      "requester-profile-RNE",
      "order-dynamic-diagnosis",
      "requesterFirstName",
    ]);
    expect(view.container.querySelector("#requester-profile-CMP").value).toBe(
      "value-CMP",
    );
    fireEvent.change(view.container.querySelector("#requesterId"), {
      target: { value: "20" },
    });
    expect(orderedIds()).toEqual([
      "requesterId",
      "requester-profile-LICENSE",
      "order-dynamic-diagnosis",
      "requesterFirstName",
    ]);
    expect(view.container.querySelector("#requester-profile-CMP")).toBeNull();
  },
);

test("the profile slot obeys its visibility setting", () => {
  const view = open(false, false);
  fireEvent.change(view.container.querySelector("#requesterId"), {
    target: { value: "10" },
  });
  expect(view.container.querySelector('[id^="requester-profile-"]')).toBeNull();
});
