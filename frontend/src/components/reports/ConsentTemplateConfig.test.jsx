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
  getConsentTemplates,
  saveConsentTemplate,
} from "../../services/consentTemplateService";

jest.mock("../../services/consentTemplateService", () => ({
  getConsentTemplateConfig: jest.fn(),
  getConsentTemplateOptions: jest.fn(),
  parseConsentTemplate: jest.fn(),
  saveConsentTemplateConfig: jest.fn(),
  downloadConsentTemplate: jest.fn(),
  getConsentTemplates: jest.fn(),
  saveConsentTemplate: jest.fn(),
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
    TextInput: ({ id, labelText, value, onChange, disabled }) => (
      <label>
        {labelText}
        <input id={id} value={value} onChange={onChange} disabled={disabled} />
      </label>
    ),
    Checkbox: ({ id, labelText, checked, onChange, disabled }) => (
      <label>
        {labelText}
        <input
          id={id}
          type="checkbox"
          checked={checked}
          disabled={disabled}
          onChange={(e) => onChange(e, { checked: e.target.checked })}
        />
      </label>
    ),
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
  getConsentTemplates.mockImplementation((callback) =>
    callback([{ id: "RPT-CONSENT-TEMPLATE", name: "General", isActive: true }]),
  );
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

test("signature parameters offer only image sources and save the mapping independently", () => {
  getConsentTemplateConfig.mockImplementation((callback) =>
    callback({
      ...existing,
      parameters: [
        ...existing.parameters,
        {
          id: "CI_INVESTIGADOR_FIRMA",
          name: "CI_INVESTIGADOR_FIRMA",
          kind: "parameter",
          valueType: "image",
        },
        {
          id: "CI_SUJETO_FIRMA",
          name: "CI_SUJETO_FIRMA",
          kind: "parameter",
          valueType: "image",
        },
      ],
    }),
  );
  getConsentTemplateOptions.mockImplementation((callback) =>
    callback({
      sources: [
        ...sources,
        {
          id: "provider.signature",
          valueType: "image",
          labelKey: "consent.template.source.providerSignature",
        },
        {
          id: "orderDocumentImage.patient",
          valueType: "image",
          labelKey: "consent.template.source.documentImage",
          label: "Firma del paciente",
        },
      ],
    }),
  );
  const view = open();
  const text = view.getByLabelText("consent-source-patientName");
  const image = view.getByLabelText("consent-source-CI_INVESTIGADOR_FIRMA");
  expect([...text.options].map((option) => option.value)).toEqual([
    "",
    "patient.fullName",
  ]);
  expect([...image.options].map((option) => option.value)).toEqual([
    "",
    "provider.signature",
    "orderDocumentImage.patient",
  ]);
  fireEvent.change(image, { target: { value: "provider.signature" } });
  fireEvent.change(view.getByLabelText("consent-source-CI_SUJETO_FIRMA"), {
    target: { value: "orderDocumentImage.patient" },
  });
  fireEvent.click(view.getByText(messages["consent.template.config.save"]));
  expect(saveConsentTemplateConfig).toHaveBeenCalledWith(
    expect.objectContaining({
      fields: {
        patientName: "patient.fullName",
        CI_INVESTIGADOR_FIRMA: "provider.signature",
        CI_SUJETO_FIRMA: "orderDocumentImage.patient",
      },
    }),
    expect.any(Function),
  );
  expect(view.getByLabelText("consent-source-CI_SUJETO_FIRMA").value).toBe(
    "orderDocumentImage.patient",
  );
});

test("creates an independent named template and preserves its identity when replacing its JRXML", async () => {
  const bank = {
    ...existing,
    id: "CONSENT-bank",
    name: "Banco de ADN",
    fields: { patientName: "patient.fullName" },
  };
  getConsentTemplates.mockImplementation((callback) =>
    callback([
      { id: "RPT-CONSENT-TEMPLATE", name: "General", isActive: true },
      { id: bank.id, name: bank.name, isActive: true },
    ]),
  );
  getConsentTemplateConfig.mockImplementation((callback, id) =>
    callback(id === bank.id ? bank : existing),
  );
  const view = open();
  fireEvent.change(view.getByLabelText("consent-template-selector"), {
    target: { value: bank.id },
  });
  parseConsentTemplate.mockResolvedValue({
    id: null,
    templateFilename: "bank.jrxml",
    templateContent: "updated-bank",
    parameters: existing.parameters,
  });
  fireEvent.change(view.getByLabelText(messages["consent.template.upload"]), {
    target: { files: [new File(["updated-bank"], "bank.jrxml")] },
  });
  await waitForElement(() => view.getByText("bank.jrxml"));
  fireEvent.click(view.getByText(messages["consent.template.config.save"]));
  expect(saveConsentTemplate).toHaveBeenCalledWith(
    expect.objectContaining({
      id: bank.id,
      name: bank.name,
      templateContent: "updated-bank",
    }),
    expect.any(Function),
  );
  expect(saveConsentTemplateConfig).not.toHaveBeenCalled();
});

test("a new template starts with separate mappings and supports deactivation", async () => {
  parseConsentTemplate.mockResolvedValue({
    templateFilename: "new.jrxml",
    templateContent: "new",
    parameters: existing.parameters,
  });
  const view = open();
  fireEvent.click(
    view.getByText(messages["consent.template.new"], { selector: "button" }),
  );
  fireEvent.change(view.getByLabelText(messages["consent.template.name"]), {
    target: { value: "Investigación" },
  });
  fireEvent.change(view.getByLabelText(messages["consent.template.upload"]), {
    target: { files: [new File(["new"], "new.jrxml")] },
  });
  await waitForElement(() => view.getByText("new.jrxml"));
  expect(view.getByLabelText("consent-source-patientName").value).toBe("");
  fireEvent.click(view.getByLabelText(messages["label.active"]));
  fireEvent.click(view.getByText(messages["consent.template.config.save"]));
  expect(saveConsentTemplate).toHaveBeenCalledWith(
    expect.objectContaining({
      name: "Investigación",
      isActive: false,
      fields: { patientName: "" },
    }),
    expect.any(Function),
  );
});

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
