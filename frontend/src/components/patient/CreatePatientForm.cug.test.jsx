import React, { useState } from "react";
import { act, fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import CreatePatientForm from "./CreatePatientForm";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../utils/Utils";
import messages from "../../languages/en.json";
import { createSampleOrderFormValues } from "../formModel/innitialValues/OrderEntryFormValues";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
}));
jest.mock(
  "./photoManagement/uploadPhoto/PatientImageSelector",
  () => () => null,
);
jest.mock("./AddressSearch", () => () => null);

const fixed = [
  { fieldKey: "firstName", visible: true, required: true },
  { fieldKey: "lastName", visible: true, required: true },
  ...[
    "photo",
    "subjectNumber",
    "nationalId",
    "optionalIdentifiers",
    "gender",
    "birthDateAge",
    "primaryPhone",
    "email",
    "emergencyContact",
    "additionalInfo",
  ].map((fieldKey) => ({ fieldKey, visible: false, required: false })),
];

const notifications = {
  addNotification: jest.fn(),
  setNotificationVisible: jest.fn(),
};

const renderForm = (
  onPatientSaved,
  initialSelected = {},
  syncOrder = false,
  orderEntry = true,
  documentFields = false,
) => {
  const selectedValues = initialSelected.patientPK
    ? { birthDateForDisplay: "01/01/2000", ...initialSelected }
    : initialSelected;
  const PatientStep = () => {
    const [selected, setSelected] = useState(selectedValues);
    const [order, setOrder] = useState(() => {
      const values = createSampleOrderFormValues();
      return {
        ...values,
        patientProperties: { ...values.patientProperties, ...selectedValues },
      };
    });
    return (
      <IntlProvider locale="en" messages={messages}>
        <ConfigurationContext.Provider
          value={{
            configurationProperties: {
              SHOW_PATIENT_ADDITIONAL_INFO_ON_ORDER_ENTRY: "false",
              SHOW_PATIENT_NATIONAL_ID_FIELD: documentFields ? "true" : "false",
              FIRST_NAME_REGEX: ".*",
              LAST_NAME_REGEX: ".*",
            },
          }}
        >
          <NotificationContext.Provider value={notifications}>
            <CreatePatientForm
              selectedPatient={selected}
              orderFormValues={syncOrder ? order : undefined}
              setOrderFormValues={syncOrder ? setOrder : undefined}
              isOrderEntryPatientStep={orderEntry}
              showActionsButton={!orderEntry}
              onPatientSaved={(patient) => {
                setSelected(patient);
                onPatientSaved(patient);
              }}
            />
            {syncOrder && (
              <output data-testid="patient-order-action">
                {order.patientProperties.patientUpdateStatus}
              </output>
            )}
          </NotificationContext.Provider>
        </ConfigurationContext.Provider>
      </IntlProvider>
    );
  };
  return render(<PatientStep />);
};

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.includes("professional-profile-permissions"))
      callback({ hasOrderPermission: true });
    else if (url.includes("patient-management/access"))
      callback({ canRead: true, canCreate: true, canUpdate: true });
    else if (url.includes("patient-additional-fields/fixed")) callback(fixed);
    else if (url.includes("sample-cug/patient-prefix"))
      callback({ prefix: "5000" });
    else callback([]);
  });
});

test("an existing order patient without update permission is displayed without editable registration fields", async () => {
  const view = renderForm(
    jest.fn(),
    {
      patientPK: "11",
      firstName: "Ana",
      lastName: "Perez",
      readOnly: true,
      patientUpdateStatus: "NO_ACTION",
    },
    true,
  );
  await wait(() =>
    expect(view.container.querySelector('input[name="firstName"]').value).toBe(
      "Ana",
    ),
  );
  expect(view.container.querySelector('input[name="firstName"]').disabled).toBe(
    true,
  );
  expect(view.container.querySelector('input[name="lastName"]').disabled).toBe(
    true,
  );
  expect(postToOpenElisServerJsonResponse).not.toHaveBeenCalled();
  await wait(() =>
    expect(view.getByTestId("patient-order-action").textContent).toBe(
      "NO_ACTION",
    ),
  );
  await wait(() =>
    expect(
      getFromOpenElisServer.mock.calls.some(
        ([url]) =>
          url.includes("sample-cug/patient-prefix?") &&
          url.includes("forOrderEntry=true"),
      ),
    ).toBe(true),
  );
});

test("saves new order patient separately and replaces competing prefix with the server result", async () => {
  const saved = jest.fn();
  postToOpenElisServerJsonResponse.mockImplementation(
    (url, payload, callback) =>
      callback({
        ...JSON.parse(payload),
        patientPK: "10",
        cugPrefix: "5001",
        patientLastUpdated: "2026-10-21 10:00:00.1",
        personLastUpdated: "2026-10-21 10:00:00.1",
      }),
  );
  const view = renderForm(saved);
  const save = () =>
    view.getByText(messages["patient.cug.save"]).closest("button");
  expect(save().disabled).toBe(true);
  await wait(() =>
    expect(
      view.container.querySelector('input[name="firstName"]'),
    ).toBeTruthy(),
  );
  fireEvent.change(view.container.querySelector('input[name="firstName"]'), {
    target: { value: "Ana" },
  });
  fireEvent.change(view.container.querySelector('input[name="lastName"]'), {
    target: { value: "Perez" },
  });
  await wait(() =>
    expect(
      view.getByLabelText(messages["patient.cug.prefix.label"]).value,
    ).toBe("5000"),
  );
  await wait(() => expect(save().disabled).toBe(false));
  fireEvent.click(save());
  await wait(() =>
    expect(saved).toHaveBeenCalledWith(
      expect.objectContaining({
        patientPK: "10",
        cugPrefix: "5001",
        patientUpdateStatus: "UPDATE",
      }),
    ),
  );
  expect(view.getByLabelText(messages["patient.cug.prefix.label"]).value).toBe(
    "5001",
  );
  expect(postToOpenElisServerJsonResponse.mock.calls[0][0]).toBe(
    "/rest/orders/patient",
  );
  // Re-selecting the saved patient must not fetch a hidden, protected photo.
  expect(
    getFromOpenElisServer.mock.calls.some(([url]) =>
      url.includes("/patient-photos/"),
    ),
  ).toBe(false);
});

test("failed save preserves entered data and never reports a persisted patient", async () => {
  const saved = jest.fn();
  postToOpenElisServerJsonResponse.mockImplementation(
    (url, payload, callback) => callback({ status: 400 }),
  );
  const view = renderForm(saved);
  await wait(() =>
    expect(
      view.container.querySelector('input[name="firstName"]'),
    ).toBeTruthy(),
  );
  fireEvent.change(view.container.querySelector('input[name="firstName"]'), {
    target: { value: "Ana" },
  });
  fireEvent.change(view.container.querySelector('input[name="lastName"]'), {
    target: { value: "Perez" },
  });
  const save = view.getByText(messages["patient.cug.save"]).closest("button");
  await wait(() => expect(save.disabled).toBe(false));
  fireEvent.click(save);
  await wait(() => expect(postToOpenElisServerJsonResponse).toHaveBeenCalled());
  expect(saved).not.toHaveBeenCalled();
  expect(view.container.querySelector('input[name="firstName"]').value).toBe(
    "Ana",
  );
  expect(view.getByLabelText(messages["patient.cug.prefix.label"]).value).toBe(
    "5000",
  );
});

test("duplicate document conflict preserves entered values and explains how to continue", async () => {
  const saved = jest.fn();
  postToOpenElisServerJsonResponse.mockImplementation(
    (url, payload, callback) =>
      callback({ status: 409, code: "patient.identifier.duplicate" }),
  );
  const view = renderForm(saved);
  await wait(() =>
    expect(
      view.container.querySelector('input[name="firstName"]'),
    ).toBeTruthy(),
  );
  fireEvent.change(view.container.querySelector('input[name="firstName"]'), {
    target: { value: "Ana" },
  });
  fireEvent.change(view.container.querySelector('input[name="lastName"]'), {
    target: { value: "Perez" },
  });
  const save = view.getByText(messages["patient.cug.save"]).closest("button");
  await wait(() => expect(save.disabled).toBe(false));
  fireEvent.click(save);
  await wait(() =>
    expect(notifications.addNotification).toHaveBeenCalledWith(
      expect.objectContaining({
        kind: "error",
        message: messages["patient.identifier.duplicate"],
      }),
    ),
  );
  expect(saved).not.toHaveBeenCalled();
  expect(view.container.querySelector('input[name="firstName"]').value).toBe(
    "Ana",
  );
  expect(view.getByLabelText(messages["patient.cug.prefix.label"]).value).toBe(
    "5000",
  );
});

test.each([true, false])(
  "existing patient without orders is editable with patient-update permission (order entry: %s)",
  async (orderEntry) => {
    const view = renderForm(
      jest.fn(),
      { patientPK: "11", firstName: "Ana", lastName: "Perez" },
      orderEntry,
      orderEntry,
    );
    await wait(() =>
      expect(
        view.container.querySelector('input[name="firstName"]').disabled,
      ).toBe(false),
    );
    await act(async () => {
      fireEvent.change(
        view.container.querySelector('input[name="firstName"]'),
        {
          target: { value: "Ana Maria" },
        },
      );
    });
    expect(view.container.querySelector('input[name="firstName"]').value).toBe(
      "Ana Maria",
    );
  },
);

test.each([true, false])(
  "patient-update permission is required even if the selected DTO says editable (order entry: %s)",
  async (orderEntry) => {
    const original = getFromOpenElisServer.getMockImplementation();
    getFromOpenElisServer.mockImplementation((url, callback) => {
      if (url.includes("patient-management/access"))
        callback({ canRead: true, canCreate: false, canUpdate: false });
      else original(url, callback);
    });
    const view = renderForm(
      jest.fn(),
      { patientPK: "11", firstName: "Ana", lastName: "Perez", readOnly: false },
      orderEntry,
      orderEntry,
    );
    await wait(() =>
      expect(
        view.container.querySelector('input[name="firstName"]').value,
      ).toBe("Ana"),
    );
    expect(
      view.container.querySelector('input[name="firstName"]').disabled,
    ).toBe(true);
    if (orderEntry)
      await wait(() =>
        expect(view.getByTestId("patient-order-action").textContent).toBe(
          "NO_ACTION",
        ),
      );
    expect(postToOpenElisServerJsonResponse).not.toHaveBeenCalled();
  },
);

test.each([true, false])(
  "restricted fields remain hidden and configured readonly fields remain disabled (order entry: %s)",
  async (orderEntry) => {
    const original = getFromOpenElisServer.getMockImplementation();
    getFromOpenElisServer.mockImplementation((url, callback) => {
      if (url.includes("patient-management/access"))
        callback({
          canRead: true,
          canCreate: true,
          canUpdate: true,
          restrictedFieldTagKeys: ["patient-first-name"],
        });
      else if (url.includes("patient-additional-fields/fixed"))
        callback(
          fixed.map((field) =>
            field.fieldKey === "lastName"
              ? { ...field, readonly: true }
              : field,
          ),
        );
      else original(url, callback);
    });
    const view = renderForm(
      jest.fn(),
      { patientPK: "11", firstName: "Ana", lastName: "Perez" },
      orderEntry,
      orderEntry,
    );
    await wait(() =>
      expect(view.container.querySelector('input[name="lastName"]').value).toBe(
        "Perez",
      ),
    );
    expect(view.container.querySelector('input[name="firstName"]')).toBeNull();
    expect(
      view.container.querySelector('input[name="lastName"]').disabled,
    ).toBe(true);
  },
);

test("order creation alone can register a new patient but does not grant subsequent demographic editing", async () => {
  const original = getFromOpenElisServer.getMockImplementation();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.includes("patient-management/access"))
      callback({ canRead: false, canCreate: false, canUpdate: false });
    else original(url, callback);
  });
  postToOpenElisServerJsonResponse.mockImplementation(
    (url, payload, callback) =>
      callback({ patientPK: "11", cugPrefix: "5001" }),
  );
  const saved = jest.fn();
  const view = renderForm(saved, {}, true);
  await wait(() =>
    expect(
      view.container.querySelector('input[name="firstName"]').disabled,
    ).toBe(false),
  );
  fireEvent.change(view.container.querySelector('input[name="firstName"]'), {
    target: { value: "Ana" },
  });
  fireEvent.change(view.container.querySelector('input[name="lastName"]'), {
    target: { value: "Perez" },
  });
  const save = view.getByText(messages["patient.cug.save"]).closest("button");
  await wait(() => expect(save.disabled).toBe(false));
  fireEvent.click(save);
  await wait(() =>
    expect(saved).toHaveBeenCalledWith(
      expect.objectContaining({
        patientPK: "11",
        readOnly: true,
        patientUpdateStatus: "NO_ACTION",
      }),
    ),
  );
  await wait(() =>
    expect(view.getByTestId("patient-order-action").textContent).toBe(
      "NO_ACTION",
    ),
  );
  expect(view.container.querySelector('input[name="firstName"]').disabled).toBe(
    true,
  );
});

const enableDocumentFields = () => {
  const original = getFromOpenElisServer.getMockImplementation();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.includes("patient-additional-fields/fixed"))
      callback(
        fixed.map((field) =>
          ["nationalId", "optionalIdentifiers"].includes(field.fieldKey)
            ? { ...field, visible: true }
            : field,
        ),
      );
    else original(url, callback);
  });
};

test.each([true, false])(
  "saved document checkboxes and primary identifier load without any orders (order entry: %s)",
  async (orderEntry) => {
    enableDocumentFields();
    const view = renderForm(
      jest.fn(),
      {
        patientPK: "11",
        firstName: "Ana",
        lastName: "Perez",
        nationalId: "AB123456",
        dni: "77711133",
        passportNumber: "AB123456",
        foreignId: "001234567",
        primaryPatientIdentifierType: "PASSPORT",
      },
      orderEntry,
      orderEntry,
      true,
    );
    await wait(() =>
      expect(
        view.container.querySelector('input[name="passportNumber"]').value,
      ).toBe("AB123456"),
    );
    expect(view.container.querySelector("#patient-dni-enabled").checked).toBe(
      true,
    );
    expect(
      view.container.querySelector("#patient-passport-enabled").checked,
    ).toBe(true);
    expect(
      view.container.querySelector("#patient-foreign-id-enabled").checked,
    ).toBe(true);
    expect(
      view.container.querySelector(
        'select[name="primaryPatientIdentifierType"]',
      ).value,
    ).toBe("PASSPORT");
    expect(view.container.querySelector('input[name="nationalId"]').value).toBe(
      "AB123456",
    );
  },
);

test.each(["DNI", "PASSPORT", "FOREIGN_ID"])(
  "an untyped existing ID is kept when its confirmed document checkbox is toggled (%s)",
  async (type) => {
    enableDocumentFields();
    const view = renderForm(
      jest.fn(),
      {
        patientPK: "11",
        firstName: "Ana",
        lastName: "Perez",
        nationalId: "77711133",
      },
      true,
      true,
      true,
    );
    const checkboxes = {
      DNI: "patient-dni-enabled",
      PASSPORT: "patient-passport-enabled",
      FOREIGN_ID: "patient-foreign-id-enabled",
    };
    const fields = {
      DNI: "dni",
      PASSPORT: "passportNumber",
      FOREIGN_ID: "foreignId",
    };
    await wait(() =>
      expect(
        view.container.querySelector('input[name="nationalId"]').value,
      ).toBe("77711133"),
    );
    expect(view.container.querySelector("#patient-dni-enabled").checked).toBe(
      false,
    );
    expect(
      view.container.querySelector("#patient-passport-enabled").checked,
    ).toBe(false);
    expect(
      view.container.querySelector("#patient-foreign-id-enabled").checked,
    ).toBe(false);
    await act(async () =>
      fireEvent.click(view.container.querySelector("#" + checkboxes[type])),
    );
    expect(
      view.container.querySelector('input[name="' + fields[type] + '"]').value,
    ).toBe("77711133");
    await act(async () =>
      fireEvent.click(view.container.querySelector("#" + checkboxes[type])),
    );
    expect(view.container.querySelector('input[name="nationalId"]').value).toBe(
      "77711133",
    );
    expect(
      view.container.querySelector('input[name="' + fields[type] + '"]'),
    ).toBeNull();
  },
);

test("removing a known primary document switches the ID to the remaining saved document", async () => {
  enableDocumentFields();
  const view = renderForm(
    jest.fn(),
    {
      patientPK: "11",
      firstName: "Ana",
      lastName: "Perez",
      nationalId: "77711133",
      dni: "77711133",
      passportNumber: "AB123456",
      primaryPatientIdentifierType: "DNI",
    },
    true,
    true,
    true,
  );
  await wait(() =>
    expect(view.container.querySelector("#patient-dni-enabled").checked).toBe(
      true,
    ),
  );
  await act(async () =>
    fireEvent.click(view.container.querySelector("#patient-dni-enabled")),
  );
  expect(view.container.querySelector('input[name="nationalId"]').value).toBe(
    "AB123456",
  );
  expect(
    view.container.querySelector('input[name="passportNumber"]').value,
  ).toBe("AB123456");
  expect(
    view.container.querySelector('select[name="primaryPatientIdentifierType"]')
      .value,
  ).toBe("PASSPORT");
});

test("registering a patient submits the document value and selected type, and keeps its checkbox after saving", async () => {
  enableDocumentFields();
  const saved = jest.fn();
  postToOpenElisServerJsonResponse.mockImplementation(
    (url, payload, callback) =>
      callback({ patientPK: "11", cugPrefix: "5001" }),
  );
  const view = renderForm(saved, {}, true, true, true);
  await wait(() =>
    expect(
      view.container.querySelector('input[name="firstName"]').disabled,
    ).toBe(false),
  );
  await act(async () => {
    fireEvent.change(view.container.querySelector('input[name="firstName"]'), {
      target: { value: "Ana" },
    });
    fireEvent.change(view.container.querySelector('input[name="lastName"]'), {
      target: { value: "Perez" },
    });
  });
  await act(async () =>
    fireEvent.click(view.container.querySelector("#patient-dni-enabled")),
  );
  await act(async () =>
    fireEvent.change(view.container.querySelector('input[name="dni"]'), {
      target: { value: "77711133" },
    }),
  );
  const save = view.container.querySelector('button[type="submit"]');
  await wait(() => expect(save.disabled).toBe(false));
  fireEvent.click(save);
  await wait(() => expect(saved).toHaveBeenCalled());
  const sent = JSON.parse(postToOpenElisServerJsonResponse.mock.calls[0][1]);
  expect(sent.dniEnabled).toBe(true);
  expect(sent.passportEnabled).toBe(false);
  expect(sent).toEqual(
    expect.objectContaining({
      dni: "77711133",
      nationalId: "77711133",
      primaryPatientIdentifierType: "DNI",
    }),
  );
  expect(view.container.querySelector("#patient-dni-enabled").checked).toBe(
    true,
  );
  expect(view.container.querySelector('input[name="dni"]').value).toBe(
    "77711133",
  );
});

test("editing a legacy patient with hidden document fields preserves the saved untyped ID", async () => {
  const saved = jest.fn();
  postToOpenElisServerJsonResponse.mockImplementation(
    (url, payload, callback) =>
      callback({ patientPK: "11", cugPrefix: "5003" }),
  );
  const view = renderForm(
    saved,
    {
      patientPK: "11",
      firstName: "Ana",
      lastName: "Perez",
      nationalId: "77711133",
    },
    false,
    false,
  );
  const save = view.container.querySelector('button[type="submit"]');
  await wait(() => expect(save.disabled).toBe(false));
  fireEvent.click(save);
  await wait(() => expect(saved).toHaveBeenCalled());
  const sent = JSON.parse(postToOpenElisServerJsonResponse.mock.calls[0][1]);
  expect(sent.nationalId).toBe("77711133");
  expect(sent.primaryPatientIdentifierType).toBe("");
});

test("clearing a legacy registration does not carry its ID into a new document", async () => {
  enableDocumentFields();
  const view = renderForm(
    jest.fn(),
    {
      patientPK: "11",
      firstName: "Ana",
      lastName: "Perez",
      nationalId: "77711133",
    },
    false,
    false,
    true,
  );
  await wait(() =>
    expect(view.container.querySelector('input[name="nationalId"]').value).toBe(
      "77711133",
    ),
  );
  await act(async () =>
    fireEvent.click(view.container.querySelector("#clear")),
  );
  await act(async () =>
    fireEvent.click(view.container.querySelector("#patient-dni-enabled")),
  );
  expect(view.container.querySelector('input[name="dni"]').value).toBe("");
  expect(view.container.querySelector('input[name="nationalId"]').value).toBe(
    "",
  );
});

test.each(["DNI", "PASSPORT", "FOREIGN_ID"])(
  "a checked secondary document is required until filled or unchecked (%s)",
  async (type) => {
    enableDocumentFields();
    const view = renderForm(jest.fn(), {}, true, true, true);
    await wait(() =>
      expect(
        view.container.querySelector('input[name="firstName"]').disabled,
      ).toBe(false),
    );
    await act(async () => {
      fireEvent.change(
        view.container.querySelector('input[name="firstName"]'),
        { target: { value: "Ana" } },
      );
      fireEvent.change(view.container.querySelector('input[name="lastName"]'), {
        target: { value: "Perez" },
      });
    });
    const toggles = {
      DNI: "patient-dni-enabled",
      PASSPORT: "patient-passport-enabled",
      FOREIGN_ID: "patient-foreign-id-enabled",
    };
    const fields = {
      DNI: "dni",
      PASSPORT: "passportNumber",
      FOREIGN_ID: "foreignId",
    };
    const primary = type === "DNI" ? "PASSPORT" : "DNI";
    await act(async () =>
      fireEvent.click(view.container.querySelector("#" + toggles[primary])),
    );
    await act(async () =>
      fireEvent.change(
        view.container.querySelector('input[name="' + fields[primary] + '"]'),
        { target: { value: primary === "DNI" ? "00123456" : "AB1234567" } },
      ),
    );
    const save = view.container.querySelector('button[type="submit"]');
    await wait(() => expect(save.disabled).toBe(false));
    await act(async () =>
      fireEvent.click(view.container.querySelector("#" + toggles[type])),
    );
    await wait(() => expect(save.disabled).toBe(true));
    const field = view.container.querySelector(
      'input[name="' + fields[type] + '"]',
    );
    expect(field.required).toBe(true);
    expect(
      view.container.querySelector('label[for="' + fields[type] + '"]')
        .textContent,
    ).toContain("*");
    expect(
      view.container.querySelector('label[for="nationalId"]').textContent,
    ).toContain("*");
    await act(async () =>
      fireEvent.change(field, {
        target: { value: type === "DNI" ? "12345678" : "A12345" },
      }),
    );
    await wait(() => expect(save.disabled).toBe(false));
    await act(async () => fireEvent.change(field, { target: { value: "" } }));
    await wait(() => expect(save.disabled).toBe(true));
    await act(async () =>
      fireEvent.click(view.container.querySelector("#" + toggles[type])),
    );
    await wait(() => expect(save.disabled).toBe(false));
  },
);

test.each([
  ["patient-dni-enabled", "dni", 8, "00ab123456789", "00123456"],
  [
    "patient-passport-enabled",
    "passportNumber",
    9,
    "AB-123 4567890",
    "AB1234567",
  ],
  [
    "patient-foreign-id-enabled",
    "foreignId",
    20,
    "AB-1234567890123456789012",
    "AB123456789012345678",
  ],
])(
  "document input enforces allowed characters and maximum length (%s)",
  async (toggle, fieldName, limit, input, expected) => {
    enableDocumentFields();
    const view = renderForm(jest.fn(), {}, true, true, true);
    await wait(() =>
      expect(view.container.querySelector("#" + toggle).disabled).toBe(false),
    );
    await act(async () =>
      fireEvent.click(view.container.querySelector("#" + toggle)),
    );
    const field = view.container.querySelector(
      'input[name="' + fieldName + '"]',
    );
    expect(field.maxLength).toBe(limit);
    await act(async () =>
      fireEvent.change(field, { target: { value: input } }),
    );
    expect(field.value).toBe(expected);
    expect(view.container.querySelector('input[name="nationalId"]').value).toBe(
      expected,
    );
    if (fieldName === "dni") expect(field.inputMode).toBe("numeric");
  },
);

test("restoring a checked empty document preserves its required state", async () => {
  enableDocumentFields();
  const view = renderForm(
    jest.fn(),
    {
      patientPK: "11",
      firstName: "Ana",
      lastName: "Perez",
      nationalId: "00123456",
      dni: "00123456",
      primaryPatientIdentifierType: "DNI",
      passportEnabled: true,
      passportNumber: "",
    },
    false,
    false,
    true,
  );
  await wait(() =>
    expect(
      view.container.querySelector("#patient-passport-enabled").checked,
    ).toBe(true),
  );
  expect(
    view.container.querySelector('input[name="passportNumber"]').required,
  ).toBe(true);
  await wait(() =>
    expect(view.container.querySelector('button[type="submit"]').disabled).toBe(
      true,
    ),
  );
});
