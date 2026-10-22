import React from "react";
import { fireEvent, render } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import CugSuffixInput from "./CugSuffixInput";
import messages from "../../languages/es.json";

const show = (props = {}) => {
  const onChange = jest.fn();
  const view = render(
    <IntlProvider locale="es" messages={messages}>
      <CugSuffixInput
        id="sample-cug-suffix"
        originalCug="5004.2"
        value="5004.2"
        onChange={onChange}
        {...props}
      />
    </IntlProvider>,
  );
  return {
    ...view,
    onChange,
    input: view.getByLabelText(messages["sample.cug.suffix.label"]),
  };
};

test("shows the fixed prefix and emits the same CUG with only a new suffix", () => {
  const view = show();
  expect(view.getByText("5004.")).toBeTruthy();
  expect(view.input.value).toBe("2");
  fireEvent.change(view.input, { target: { value: "3" } });
  expect(view.onChange).toHaveBeenCalledWith("5004.3");
  expect(view.getByText("5004.")).toBeTruthy();
});

test("pasting a full CUG or letters cannot change the prefix", () => {
  const view = show();
  for (const value of ["8000.3", "ABC", "3.2", "-1"]) {
    fireEvent.change(view.input, { target: { value } });
  }
  expect(view.onChange).not.toHaveBeenCalled();
});

test("uses the patient prefix for a sample that does not yet have a CUG", () => {
  const view = show({ originalCug: null, value: "", patientPrefix: "5004" });
  fireEvent.change(view.input, { target: { value: "3" } });
  expect(view.onChange).toHaveBeenCalledWith("5004.3");
});

test.each([
  { readOnly: true },
  { originalCug: null, value: "", patientPrefix: null },
])(
  "does not allow editing without permission or a known prefix: %j",
  (props) => {
    const view = show(props);
    expect(view.input.readOnly).toBe(true);
    fireEvent.change(view.input, { target: { value: "3" } });
    expect(view.onChange).not.toHaveBeenCalled();
  },
);
