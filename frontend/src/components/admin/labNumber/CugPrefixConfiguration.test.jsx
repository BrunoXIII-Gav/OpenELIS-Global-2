import React from "react";
import { render, fireEvent, waitForElement } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import CugPrefixConfiguration, {
  validCugStart,
} from "./CugPrefixConfiguration";
import messages from "../../../languages/es.json";
import {
  getFromOpenElisServer,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils";

jest.mock("../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  putToOpenElisServerFullResponse: jest.fn(),
}));
jest.mock("@carbon/react", () => {
  const React = require("react");
  const Div = ({ children }) => <div>{children}</div>;
  return {
    Column: Div,
    Grid: Div,
    Section: Div,
    Stack: Div,
    Heading: ({ children }) => <h2>{children}</h2>,
    Form: ({ children, onSubmit }) => (
      <form onSubmit={onSubmit}>{children}</form>
    ),
    Button: ({ children, onClick, disabled, type }) => (
      <button type={type || "button"} onClick={onClick} disabled={disabled}>
        {children}
      </button>
    ),
    TextInput: ({
      id,
      labelText,
      value,
      onChange,
      disabled,
      invalid,
      invalidText,
    }) => (
      <label htmlFor={id}>
        {labelText}
        <input id={id} value={value} onChange={onChange} disabled={disabled} />
        {invalid && <span>{invalidText}</span>}
      </label>
    ),
    InlineNotification: ({ title }) => <div role="alert">{title}</div>,
    Loading: () => <span />,
  };
});
beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((_url, callback) =>
    callback({ startPrefix: "141", nextAvailablePrefix: "141" }),
  );
});
const open = () =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <CugPrefixConfiguration />
    </IntlProvider>,
  );

test("allows raising and lowering the start with independent saves and shows the server's first free prefix", async () => {
  putToOpenElisServerFullResponse.mockImplementation((_url, body, callback) => {
    const start = JSON.parse(body).startPrefix;
    return callback({
      ok: true,
      json: async () => ({
        startPrefix: start,
        nextAvailablePrefix: start === "5000" ? "5011" : start,
      }),
    });
  });
  const page = open();
  for (const start of ["5000", "7000", "4000"]) {
    fireEvent.change(page.getByLabelText(messages["cug.prefix.start"]), {
      target: { value: start },
    });
    fireEvent.click(page.getByText(messages["cug.prefix.apply"]));
    await waitForElement(() => page.getByText(messages["cug.prefix.saved"]));
    expect(putToOpenElisServerFullResponse).toHaveBeenLastCalledWith(
      "/rest/sample-cug/prefix-settings",
      JSON.stringify({ startPrefix: start }),
      expect.any(Function),
    );
    expect(page.getByText(start === "5000" ? "5011" : start)).toBeTruthy();
  }
});
test("viewing settings never sends a save or allocates a CUG", () => {
  open();
  expect(putToOpenElisServerFullResponse).not.toHaveBeenCalled();
  expect(getFromOpenElisServer).toHaveBeenCalledWith(
    "/rest/sample-cug/prefix-settings",
    expect.any(Function),
  );
});
test("rejects zero, decimals and overflow without precision loss", () => {
  for (const bad of [
    "0",
    "-1",
    "4.2",
    "1e3",
    "",
    "9223372036854775807",
    "99999999999999999999",
  ])
    expect(validCugStart(bad)).toBe(false);
  expect(validCugStart("9223372036854775806")).toBe(true);
  const page = open();
  fireEvent.change(page.getByLabelText(messages["cug.prefix.start"]), {
    target: { value: "4.2" },
  });
  expect(page.getByText(messages["cug.prefix.apply"]).disabled).toBe(true);
});
test("failed saves preserve the displayed settings and show a localized error", async () => {
  putToOpenElisServerFullResponse.mockImplementation((_url, _body, callback) =>
    callback({
      ok: false,
      json: async () => ({ error: "cug.prefix.error.save" }),
    }),
  );
  const page = open();
  fireEvent.change(page.getByLabelText(messages["cug.prefix.start"]), {
    target: { value: "4000" },
  });
  fireEvent.click(page.getByText(messages["cug.prefix.apply"]));
  await waitForElement(() => page.getByRole("alert"));
  expect(page.getByText("141")).toBeTruthy();
  expect(page.getByRole("alert").textContent).toBe(
    messages["cug.prefix.error.save"],
  );
});
