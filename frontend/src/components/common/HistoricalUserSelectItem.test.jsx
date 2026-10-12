import React from "react";
import { render, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import HistoricalUserSelectItem from "./HistoricalUserSelectItem";

const renderSelection = (options) =>
  render(
    <IntlProvider
      locale="en"
      messages={{
        "userField.historical": "Previously assigned user (ID {id})",
      }}
    >
      <select aria-label="Participant" value="17" onChange={() => {}}>
        <option value="">Choose</option>
        <HistoricalUserSelectItem
          fieldType="USER"
          value="17"
          options={options}
        />
        {options.map((option) => (
          <option key={option.optionKey} value={option.optionKey}>
            {option.optionLabel}
          </option>
        ))}
      </select>
    </IntlProvider>,
  );

test("a user who lost permission remains visible as a disabled historical selection", () => {
  renderSelection([{ optionKey: "19", optionLabel: "Eligible colleague" }]);
  const historical = screen.getByRole("option", {
    name: "Previously assigned user (ID 17)",
  });
  expect(historical.disabled).toBe(true);
  expect(historical.selected).toBe(true);
  expect(screen.getByRole("combobox").value).toBe("17");
});

test("eligible users keep their current name without a duplicate historical option", () => {
  renderSelection([{ optionKey: "17", optionLabel: "Ana Perez" }]);
  expect(screen.queryByText("Previously assigned user (ID 17)")).toBeNull();
  expect(screen.getByRole("option", { name: "Ana Perez" }).selected).toBe(true);
});
