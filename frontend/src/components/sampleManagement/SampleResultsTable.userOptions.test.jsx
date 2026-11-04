import React from "react";
import { render } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import SampleResultsTable from "./SampleResultsTable";
import messages from "../../languages/es.json";
import { getFromOpenElisServer } from "../utils/Utils";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerFullResponse: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
}));

const option = (id, name) => ({
  optionKey: id,
  optionLabel: name,
  active: true,
});
const field = (options) => ({
  id: 2,
  fieldKey: "responsable",
  displayName: "Responsable",
  fieldType: "USER",
  displaySection: "RECEPTION",
  required: true,
  options,
});
const unfilteredOptions = [
  option("112", "KMN - Karina M"),
  option("117", "Carla Enciso Manrique"),
];
const sample = (id, options, values = {}) => ({
  id,
  externalId: `LNG63-${id}`,
  sampleTypeId: "47",
  sampleType: "Sangre",
  cugCode: `13.${id}`,
  editable: true,
  canRead: true,
  canComplete: true,
  canUpdate: true,
  orderedTests: [
    {
      id: "1",
      analysisId: `a-${id}`,
      testName: "Extracción de ADN",
      status: "Not Started",
    },
  ],
  additionalFields: [field(options)],
  additionalFieldValues: values,
});

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.startsWith("/rest/sample-type-tests?"))
      callback({
        additionalFields: [
          field(unfilteredOptions),
          {
            id: 1,
            fieldKey: "procedimiento",
            displayName: "Procedimiento",
            fieldType: "SELECT",
            displaySection: "RECEPTION",
            options: [option("2", "Procedimiento 2")],
          },
        ],
        tests: [],
      });
    else if (url.startsWith("/rest/sample-type-additional-fields/values"))
      callback({});
    else callback([]);
  });
});

const show = (items) =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <SampleResultsTable
        sampleItems={items}
        currentTestsVisibleBySampleId={{ 54: true, 55: true }}
      />
    </IntlProvider>,
  );
const selector = (container, id) =>
  container.querySelector(
    `select[id^="sample_mgmt_additional_${id}_responsable_"]`,
  );
const optionIds = (select) =>
  Array.from(select.options).map((entry) => entry.value);

test("sample-specific candidates are not replaced by the general directory", () => {
  const { container } = show([
    sample("54", [option("113", "Usuario habilitado")]),
  ]);
  expect(optionIds(selector(container, "54"))).toEqual(["", "113"]);
});

test("an empty eligible list remains empty even when the general directory contains users", () => {
  const { container } = show([sample("54", [])]);
  expect(optionIds(selector(container, "54"))).toEqual([""]);
});

test("two samples of the same type keep their own laboratory candidates", () => {
  const { container } = show([
    sample("54", [option("113", "Unidad A")]),
    sample("55", [option("120", "Unidad B")]),
  ]);
  expect(optionIds(selector(container, "54"))).toEqual(["", "113"]);
  expect(optionIds(selector(container, "55"))).toEqual(["", "120"]);
});

test("a previous ineligible assignment stays visible as a disabled historical option", () => {
  const { container } = show([sample("54", [], { responsable: "112" })]);
  const select = selector(container, "54");
  expect(select).toHaveValue("112");
  expect(select.querySelector('option[value="112"]')).toBeDisabled();
  expect(optionIds(select)).toEqual(["", "112"]);
});

test("a legacy response keeps static fields but cannot offer unverified USER candidates", () => {
  const item = sample("54", []);
  delete item.additionalFields;
  const { container } = show([item]);
  expect(optionIds(selector(container, "54"))).toEqual([""]);
  expect(container.querySelector('option[value="2"]')).toHaveTextContent(
    "Procedimiento 2",
  );
});
