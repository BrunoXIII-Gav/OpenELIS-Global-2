import React from "react";
import { Formik } from "formik";
import { IntlProvider } from "react-intl";
import { render, wait, act, fireEvent } from "@testing-library/react";
import PatientCugPrefixField from "./PatientCugPrefixField";
import messages from "../../languages/en.json";
import { getFromOpenElisServer } from "../utils/Utils";

jest.mock("../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
const validate = (values) =>
  values.firstName ? {} : { firstName: "required" };
const page = (values, onReadyChange, props = {}) => (
  <IntlProvider locale="en" messages={messages}>
    <Formik initialValues={values} enableReinitialize onSubmit={() => {}}>
      <PatientCugPrefixField
        ready
        validate={validate}
        onReadyChange={onReadyChange}
        {...props}
      />
    </Formik>
  </IntlProvider>
);
beforeEach(() => jest.clearAllMocks());

test("patient-module editing keeps the original prefix as the concurrency snapshot", async () => {
  const ready = jest.fn();
  const view = render(
    page({ firstName: "Ana", patientPK: "10", cugPrefix: "5004" }, ready, {
      allowAssignedEdit: true,
    }),
  );
  const input = view.getByLabelText(messages["patient.cug.prefix.label"]);
  await wait(() => expect(input.readOnly).toBe(false));
  fireEvent.change(input, { target: { value: "8000" } });
  await wait(() => expect(input.value).toBe("8000"));
  expect(
    view.getByText(messages["patient.cug.prefix.change.help"]),
  ).toBeTruthy();
  fireEvent.click(view.getByText(messages["patient.cug.prefix.cancel"]));
  await wait(() => expect(input.value).toBe("5004"));
});

test("assigned prefix remains read-only in order entry even if editing is requested", async () => {
  const view = render(
    page({ firstName: "Ana", patientPK: "10", cugPrefix: "5004" }, jest.fn(), {
      allowAssignedEdit: true,
      forOrderEntry: true,
    }),
  );
  const input = view.getByLabelText(messages["patient.cug.prefix.label"]);
  await wait(() => expect(input.value).toBe("5004"));
  fireEvent.change(input, { target: { value: "8000" } });
  expect(input.readOnly).toBe(true);
  expect(input.value).toBe("5004");
});

test("keeps prefix empty until required fields are completed, then reads a nonallocating preview", async () => {
  getFromOpenElisServer.mockImplementation((url, callback) =>
    callback({ prefix: "5000" }),
  );
  const onReadyChange = jest.fn();
  const view = render(page({ firstName: "", patientPK: "" }, onReadyChange));
  await wait(() => expect(onReadyChange).toHaveBeenCalledWith(false));
  expect(view.getByLabelText(messages["patient.cug.prefix.label"]).value).toBe(
    "",
  );
  expect(getFromOpenElisServer).not.toHaveBeenCalled();
  expect(
    view.getByLabelText(messages["patient.cug.prefix.label"]).readOnly,
  ).toBe(true);
  view.rerender(page({ firstName: "Ana", patientPK: "" }, onReadyChange));
  await wait(() =>
    expect(
      view.getByLabelText(messages["patient.cug.prefix.label"]).value,
    ).toBe("5000"),
  );
  expect(getFromOpenElisServer).toHaveBeenCalledWith(
    "/rest/sample-cug/patient-prefix",
    expect.any(Function),
  );
});

test("a manual choice survives an older automatic preview response", async () => {
  let reply;
  getFromOpenElisServer.mockImplementation((url, callback) => {
    reply = callback;
  });
  const onReadyChange = jest.fn();
  const view = render(page({ firstName: "Ana", patientPK: "" }, onReadyChange));
  await wait(() => expect(reply).toBeDefined());
  const field = view.getByLabelText(messages["patient.cug.prefix.label"]);
  fireEvent.change(field, { target: { value: "5006" } });
  await wait(() => expect(onReadyChange).toHaveBeenLastCalledWith(true));
  act(() => reply({ prefix: "5005" }));
  expect(field.value).toBe("5006");
});

test.each(["", "0", "5006.1", "05006", "abc", "9223372036854775807"])(
  "a manual prefix with invalid format cannot enable saving: %j",
  async (requestedCugPrefix) => {
    const onReadyChange = jest.fn();
    const view = render(
      page(
        { firstName: "Ana", patientPK: "", requestedCugPrefix },
        onReadyChange,
      ),
    );
    await wait(() =>
      expect(
        view
          .getByLabelText(messages["patient.cug.prefix.label"])
          .getAttribute("aria-invalid"),
      ).toBe("true"),
    );
    expect(onReadyChange).toHaveBeenLastCalledWith(false);
  },
);

test("can return from a manual choice to a fresh automatic preview", async () => {
  getFromOpenElisServer.mockImplementation((url, callback) =>
    callback({ prefix: "5007" }),
  );
  const onReadyChange = jest.fn();
  const view = render(
    page(
      { firstName: "Ana", patientPK: "", requestedCugPrefix: "5006" },
      onReadyChange,
    ),
  );
  await wait(() =>
    expect(
      view.getByText(messages["patient.cug.prefix.automatic"]),
    ).toBeTruthy(),
  );
  fireEvent.click(view.getByText(messages["patient.cug.prefix.automatic"]));
  await wait(() =>
    expect(
      view.getByLabelText(messages["patient.cug.prefix.label"]).value,
    ).toBe("5007"),
  );
  expect(onReadyChange).toHaveBeenLastCalledWith(true);
});

test("replaces preview with saved prefix and ignores an older pending response", async () => {
  let reply;
  getFromOpenElisServer.mockImplementation((url, callback) => {
    reply = callback;
  });
  const onReadyChange = jest.fn();
  const view = render(page({ firstName: "Ana", patientPK: "" }, onReadyChange));
  await wait(() => expect(reply).toBeDefined());
  view.rerender(
    page(
      { firstName: "Ana", patientPK: "10", cugPrefix: "5001" },
      onReadyChange,
    ),
  );
  await wait(() =>
    expect(
      view.getByLabelText(messages["patient.cug.prefix.label"]).value,
    ).toBe("5001"),
  );
  act(() => reply({ prefix: "5000" }));
  expect(view.getByLabelText(messages["patient.cug.prefix.label"]).value).toBe(
    "5001",
  );
  expect(
    view.getByLabelText(messages["patient.cug.prefix.label"]).readOnly,
  ).toBe(true);
});
