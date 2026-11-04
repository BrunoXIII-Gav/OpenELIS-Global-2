import React, { useState } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import messages from "../../../../languages/en.json";
import AdditionalFieldBlockSelector from "./AdditionalFieldBlockSelector";

function Harness({ onChange = () => {} }) {
  const [value, setValue] = useState("Tubo 1");
  return (
    <IntlProvider locale="en" messages={messages}>
      <AdditionalFieldBlockSelector
        id="block"
        value={value}
        blocks={[...new Set(["Tubo 1", "Tubo 2", value])]}
        onChange={(name) => {
          setValue(name);
          onChange(name);
        }}
      />
    </IntlProvider>
  );
}

test("search selects an existing block without creating one", () => {
  const onChange = jest.fn();
  render(<Harness onChange={onChange} />);
  const input = screen.getByRole("combobox");
  fireEvent.change(input, { target: { value: "Tubo 2" } });
  fireEvent.keyDown(input, { key: "ArrowDown", code: "ArrowDown" });
  fireEvent.click(
    screen.getByText("Tubo 2", {
      selector: ".cds--list-box__menu-item__option",
    }),
  );
  expect(onChange).toHaveBeenCalledWith("Tubo 2");
});

test("new block keeps spaces while typing and only applies on Create", () => {
  const onChange = jest.fn();
  render(<Harness onChange={onChange} />);
  fireEvent.click(screen.getByText("Create block"));
  const input = screen.getByLabelText("New block name");
  fireEvent.change(input, { target: { value: "Nuevo " } });
  expect(input.value).toBe("Nuevo ");
  expect(onChange).not.toHaveBeenCalled();
  fireEvent.change(input, { target: { value: "Nuevo bloque 3 " } });
  fireEvent.click(screen.getByText("Create", { selector: "button" }));
  expect(onChange).toHaveBeenCalledWith("Nuevo bloque 3");
  expect(screen.getByRole("combobox").value).toBe("Nuevo bloque 3");
});

test("empty and duplicate names are rejected; cancel preserves assignment", () => {
  const onChange = jest.fn();
  render(<Harness onChange={onChange} />);
  fireEvent.click(screen.getByText("Create block"));
  fireEvent.click(screen.getByText("Create", { selector: "button" }));
  expect(screen.getByText("Enter a block name.")).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("New block name"), {
    target: { value: " tubo 2 " },
  });
  fireEvent.click(screen.getByText("Create", { selector: "button" }));
  expect(
    screen.getByText("This block already exists. Select it from the list."),
  ).toBeInTheDocument();
  fireEvent.click(screen.getByText("Cancel"));
  expect(onChange).not.toHaveBeenCalled();
  expect(screen.getByRole("combobox").value).toBe("Tubo 1");
});
