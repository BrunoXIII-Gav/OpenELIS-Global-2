import React from "react";
import { Formik } from "formik";
import { IntlProvider } from "react-intl";
import { render, wait, act } from "@testing-library/react";
import PatientCugPrefixField from "./PatientCugPrefixField";
import messages from "../../languages/en.json";
import { getFromOpenElisServer } from "../utils/Utils";

jest.mock("../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
const validate = (values) =>
  values.firstName ? {} : { firstName: "required" };
const page = (values, onReadyChange) => (
  <IntlProvider locale="en" messages={messages}>
    <Formik initialValues={values} enableReinitialize onSubmit={() => {}}>
      <PatientCugPrefixField
        ready
        validate={validate}
        onReadyChange={onReadyChange}
      />
    </Formik>
  </IntlProvider>
);
beforeEach(() => jest.clearAllMocks());

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
