import React, { useState } from "react";
import { render, fireEvent, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import PatientInfo from "./PatientInfo";
import { createSampleOrderFormValues } from "../formModel/innitialValues/OrderEntryFormValues";
import messages from "../../languages/en.json";

jest.mock("../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
jest.mock(
  "../patient/SearchPatientForm",
  () =>
    ({ forOrderEntry, getSelectedPatient }) => (
      <button
        onClick={() =>
          getSelectedPatient({
            patientPK: "11",
            firstName: "Ana",
            readOnly: true,
            patientUpdateStatus: "NO_ACTION",
          })
        }
      >
        {forOrderEntry
          ? "Select patient without analyses"
          : "Wrong lookup context"}
      </button>
    ),
);
jest.mock("../patient/CreatePatientForm", () => ({ orderFormValues }) => (
  <output data-testid="patient-action">
    {orderFormValues.patientProperties.patientUpdateStatus}
  </output>
));

test("order patient selection preserves the server's no-update status when patient editing is restricted", async () => {
  window.scrollTo = jest.fn();
  const Wrapper = () => {
    const [values, setValues] = useState(createSampleOrderFormValues);
    return (
      <IntlProvider locale="en" messages={messages}>
        <PatientInfo
          orderFormValues={values}
          setOrderFormValues={setValues}
          error={() => null}
        />
      </IntlProvider>
    );
  };
  const view = render(<Wrapper />);
  fireEvent.click(view.getByText("Select patient without analyses"));
  await wait(() =>
    expect(view.getByTestId("patient-action").textContent).toBe("NO_ACTION"),
  );
});
