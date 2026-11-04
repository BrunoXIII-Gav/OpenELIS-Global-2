import React from "react";
import { fireEvent, render, waitForElement } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import ConsentTemplateConfig from "./ConsentTemplateConfig";
import messages from "../../languages/es.json";
import {
  getConsentTemplateConfig,
  getConsentTemplateOptions,
  parseConsentTemplate,
  saveConsentTemplateConfig,
} from "../../services/consentTemplateService";

jest.mock("../../services/consentTemplateService", () => ({
  getConsentTemplateConfig: jest.fn(),
  getConsentTemplateOptions: jest.fn(),
  parseConsentTemplate: jest.fn(),
  saveConsentTemplateConfig: jest.fn(),
  downloadConsentTemplate: jest.fn(),
}));
jest.mock("@carbon/react", () => {
  const React = require("react");
  return {
    Button: ({ children, onClick, disabled }) => (
      <button onClick={onClick} disabled={disabled}>
        {children}
      </button>
    ),
    FileUploaderButton: ({ onChange, disabled, labelText }) => (
      <input
        type="file"
        aria-label={labelText}
        onChange={onChange}
        disabled={disabled}
      />
    ),
    Stack: ({ children }) => <div>{children}</div>,
    Loading: () => <span>Loading</span>,
    InlineNotification: ({ title }) => <div role="alert">{title}</div>,
    Select: ({ children, id, value, onChange, disabled }) => (
      <select
        id={id}
        aria-label={id}
        value={value}
        onChange={onChange}
        disabled={disabled}
      >
        {children}
      </select>
    ),
    SelectItem: ({ value, text }) => <option value={value}>{text}</option>,
    Table: ({ children }) => <table>{children}</table>,
    TableHead: ({ children }) => <thead>{children}</thead>,
    TableBody: ({ children }) => <tbody>{children}</tbody>,
    TableRow: ({ children }) => <tr>{children}</tr>,
    TableHeader: ({ children }) => <th>{children}</th>,
    TableCell: ({ children }) => <td>{children}</td>,
  };
});
const existing = {
  templateFilename: "original.jrxml",
  templateContent: "original",
  parameters: [{ id: "patientName", name: "patientName", kind: "parameter" }],
  fields: { patientName: "patient.fullName" },
};
const sources = [
  {
    id: "patient.fullName",
    labelKey: "consent.template.source.patientFullName",
  },
];
beforeEach(() => {
  jest.clearAllMocks();
  getConsentTemplateConfig.mockImplementation((callback) => callback(existing));
  getConsentTemplateOptions.mockImplementation((callback) =>
    callback({ sources }),
  );
});
const open = () =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <ConsentTemplateConfig />
    </IntlProvider>,
  );

test("upload discovers the actual JRXML fields and saves their assigned data sources", async () => {
  parseConsentTemplate.mockResolvedValue({
    templateFilename: "nuevo.jrxml",
    templateContent: "new-template",
    parameters: [{ id: "field:NOMBRE", name: "NOMBRE", kind: "field" }],
  });
  const screen = open();
  fireEvent.change(screen.getByLabelText(messages["consent.template.upload"]), {
    target: { files: [new File(["new-template"], "nuevo.jrxml")] },
  });
  await waitForElement(() => screen.getByText("$F{NOMBRE}"));
  expect(screen.queryByText("$P{patientName}")).toBeNull();
  fireEvent.change(screen.getByLabelText("consent-source-field:NOMBRE"), {
    target: { value: "patient.fullName" },
  });
  fireEvent.click(screen.getByText(messages["consent.template.config.save"]));
  expect(saveConsentTemplateConfig).toHaveBeenCalledWith(
    expect.objectContaining({
      templateContent: "new-template",
      fields: { "field:NOMBRE": "patient.fullName" },
    }),
    expect.any(Function),
  );
});

test("invalid uploads leave the existing template and mappings available", async () => {
  parseConsentTemplate.mockRejectedValue(
    new Error("consent.template.error.invalid"),
  );
  const screen = open();
  fireEvent.change(screen.getByLabelText(messages["consent.template.upload"]), {
    target: { files: [new File(["invalid"], "bad.jrxml")] },
  });
  await waitForElement(() => screen.getByRole("alert"));
  expect(screen.getByText("$P{patientName}")).toBeTruthy();
  expect(screen.getByLabelText("consent-source-patientName").value).toBe(
    "patient.fullName",
  );
});

test("newly created additional fields appear after returning to the configuration", () => {
  const screen = open();
  getConsentTemplateOptions.mockImplementation((callback) =>
    callback({
      sources: [
        ...sources,
        {
          id: "orderAdditional.future",
          label: "Nuevo dato visible",
          labelKey: "consent.template.source.additional",
        },
      ],
    }),
  );
  fireEvent(window, new Event("focus"));
  expect(screen.getByText("Nuevo dato visible")).toBeTruthy();
  expect(screen.queryByText("orderAdditional.future")).toBeNull();
});
