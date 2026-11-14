import React from "react";
import { render, fireEvent } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { AdditionalFieldEditor } from "./SearchResultForm";
import messages from "../../languages/en.json";

jest.mock("../layout/Layout", () => ({
  ConfigurationContext: require("react").createContext({}),
  NotificationContext: require("react").createContext({}),
}));

test.each(["SELECT", "RADIO", "MULTISELECT"])(
  "%s preserves a historical result and offers only active choices",
  (fieldType) => {
    const onCommit = jest.fn();
    const view = render(
      <IntlProvider locale="en" messages={messages}>
        <AdditionalFieldEditor
          inputId="test-choice"
          fieldLabel="Procedure"
          fieldType={fieldType}
          value="old"
          fieldMetadata={{}}
          onCommit={onCommit}
          activeOptions={[
            {
              optionKey: "old",
              optionLabel: "Historical choice",
              active: false,
            },
            { optionKey: "new", optionLabel: "New choice", active: true },
            {
              optionKey: "retired",
              optionLabel: "Other retired choice",
              active: false,
            },
          ]}
        />
      </IntlProvider>,
    );
    const historical =
      fieldType === "RADIO"
        ? view.getByLabelText("Historical choice")
        : view.getByRole("option", { name: "Historical choice" });
    expect(historical.disabled).toBe(true);
    expect(
      fieldType === "RADIO" ? historical.checked : historical.selected,
    ).toBe(true);
    expect(view.queryByText("Other retired choice")).toBeNull();
    if (fieldType === "SELECT") {
      fireEvent.change(view.getByLabelText("Procedure"), {
        target: { value: "new" },
      });
      expect(onCommit).toHaveBeenCalledWith("new");
    }
  },
);
