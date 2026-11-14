import React from "react";
import { fireEvent, render, within } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { MemoryRouter } from "react-router-dom";
import PatientAdditionalFieldsManagement from "./PatientAdditionalFieldsManagement";
import SampleTypeAdditionalFields from "./SampleTypeAdditionalFields";
import { NotificationContext } from "../../layout/Layout";
import {
  getFromOpenElisServer,
  putToOpenElisServer,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils";
import messages from "../../../languages/en.json";

jest.mock("../../layout/Layout", () => ({
  NotificationContext: require("react").createContext({}),
}));
jest.mock("../../common/PageBreadCrumb", () => () => null);
jest.mock("../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  putToOpenElisServer: jest.fn(),
  putToOpenElisServerFullResponse: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
  deleteFromOpenElisServer: jest.fn(),
}));

test.each([
  [
    "patient",
    PatientAdditionalFieldsManagement,
    "patient-additional",
    putToOpenElisServerFullResponse,
  ],
  [
    "sample",
    SampleTypeAdditionalFields,
    "sample-additional",
    putToOpenElisServer,
  ],
])(
  "%s fields with data allow option management without changing saved identities",
  (module, Component, prefix, save) => {
    jest.clearAllMocks();
    const field = {
      id: 12,
      fieldKey: "procedure",
      displayName: "Procedure choices",
      fieldType: "SELECT",
      active: true,
      hasSavedValues: true,
      options: [
        {
          id: 10,
          optionKey: "old-choice",
          optionLabel: "Existing choice",
          active: true,
        },
        {
          id: 11,
          optionKey: "retired",
          optionLabel: "Retired choice",
          active: false,
        },
      ],
    };
    getFromOpenElisServer.mockImplementation((url, callback) => {
      if (url.endsWith("/sample-types"))
        callback([{ id: "1", value: "Blood" }]);
      else callback(url.includes("includeInactive=true") ? [field] : []);
    });
    const view = render(
      <MemoryRouter>
        <IntlProvider locale="en" messages={messages}>
          <NotificationContext.Provider
            value={{
              addNotification: jest.fn(),
              setNotificationVisible: jest.fn(),
            }}
          >
            <Component />
          </NotificationContext.Provider>
        </IntlProvider>
      </MemoryRouter>,
    );
    if (module === "sample")
      fireEvent.change(
        view.container.querySelector("#sampleTypeAdditionalFieldsSelector"),
        { target: { value: "1" } },
      );
    const row = view.getByText(field.displayName).closest("tr");
    fireEvent.click(within(row).getByText(messages["button.edit"]));
    expect(view.getByDisplayValue("Existing choice").readOnly).toBe(true);
    expect(view.getByDisplayValue("Retired choice")).toBeDefined();
    expect(
      view.getAllByText(messages["additional.fields.optionRemove"])[0].disabled,
    ).toBe(true);
    fireEvent.click(view.getByText(messages["additional.fields.optionAdd"]));
    const newOption = view.container.querySelector(`#${prefix}-option-label-2`);
    expect(newOption.readOnly).toBe(false);
    fireEvent.change(newOption, { target: { value: "New choice" } });
    fireEvent.click(view.container.querySelector(`#${prefix}-option-active-0`));
    fireEvent.click(view.container.querySelector(`#${prefix}-option-active-1`));
    fireEvent.click(
      view.container.querySelector(
        module === "patient"
          ? '[data-cy="create-patient-additional-field"]'
          : ".additional-field-config__actions button",
      ),
    );
    expect(save).toHaveBeenCalled();
    const payload = JSON.parse(save.mock.calls[0][1]);
    expect(payload.options).toEqual([
      expect.objectContaining({
        id: 10,
        optionKey: "old-choice",
        active: false,
      }),
      expect.objectContaining({ id: 11, optionKey: "retired", active: true }),
      expect.objectContaining({ optionLabel: "New choice", active: true }),
    ]);
  },
);
