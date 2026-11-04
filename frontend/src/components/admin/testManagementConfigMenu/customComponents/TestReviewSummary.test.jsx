/* eslint-env jest */
import React from "react";
import { render, screen, within } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { ConfigurationContext } from "../../../layout/Layout";
import { TestReviewSummary } from "./TestReviewSummary";
import { CustomTestDataDisplay } from "./CustomTestDataDisplay";
import messages from "../../../../languages/es.json";

jest.mock("../../../layout/Layout", () => ({
  ConfigurationContext: require("react").createContext(null),
}));

const wrap = (element) => (
  <IntlProvider locale="es" messages={messages}>
    <ConfigurationContext.Provider
      value={{ configurationProperties: { SIMPLIFIED_TEST_NAMES: "true" } }}
    >
      {element}
    </ConfigurationContext.Provider>
  </IntlProvider>
);
const baseValues = Object.freeze({
  testNameEnglish: "ADN",
  testNameFrench: "ADN",
  testReportNameEnglish: "ADN",
  testReportNameFrench: "ADN",
});
const label = (id) => messages[id].trim();

test("omits empty optional rows and duplicate report names, but reflects adding and removing LOINC", () => {
  const props = {
    selectedUomList: { value: "n/a" },
    panelListTag: [{ value: "None" }],
  };
  const summary = (loinc) =>
    wrap(
      <TestReviewSummary
        {...props}
        values={{
          ...baseValues,
          loinc,
          dictionaryReference: "",
          defaultTestResult: null,
        }}
      />,
    );
  const view = render(summary(""));
  for (const key of [
    "label.loinc",
    "field.uom",
    "field.panel",
    "field.referenceValue",
    "label.default.result",
    "reporting.label.testName",
  ]) {
    expect(screen.queryByText(label(key))).toBeNull();
  }
  expect(screen.getAllByText("ADN")).toHaveLength(1);
  view.rerender(summary("1234-5"));
  expect(screen.getByText("1234-5")).toBeTruthy();
  expect(screen.getByText(label("label.loinc"))).toBeTruthy();
  view.rerender(summary("   "));
  expect(screen.queryByText(label("label.loinc"))).toBeNull();
});

test("keeps false and numeric zero and translates states without modifying values", () => {
  const values = Object.freeze({
    ...baseValues,
    active: "N",
    orderable: "Y",
    directSampleUsageEnabled: false,
    resultActive: true,
    defaultTestResult: 0,
  });
  const { container } = render(wrap(<TestReviewSummary values={values} />));
  const rowFor = (key) => screen.getByText(label(key)).parentElement;
  expect(
    within(rowFor("dictionary.category.isActive")).getByText("No"),
  ).toBeTruthy();
  expect(within(rowFor("label.orderable")).getByText("Sí")).toBeTruthy();
  expect(within(rowFor("test.directSampleUsage")).getByText("No")).toBeTruthy();
  expect(within(rowFor("label.default.result")).getByText("0")).toBeTruthy();
  expect(container.textContent).not.toContain("false");
  expect(values.active).toBe("N");
  expect(values.directSampleUsageEnabled).toBe(false);
});

test("translates result type by code while keeping the API ID and label untouched", () => {
  const values = Object.freeze({ ...baseValues, resultType: "17" });
  const selected = Object.freeze({ id: "17", value: "Free text" });
  render(
    wrap(
      <TestReviewSummary
        values={values}
        selectedResultTypeList={selected}
        resultTypeCodes={[{ id: "17", value: "R" }]}
      />,
    ),
  );
  expect(screen.getByText("Texto libre")).toBeTruthy();
  expect(screen.queryByText("Free text")).toBeNull();
  expect(values.resultType).toBe("17");
  expect(selected.value).toBe("Free text");
});

test("keeps different report names and bilingual translations visible", () => {
  render(
    wrap(
      <TestReviewSummary
        values={{
          ...baseValues,
          testReportNameEnglish: "DNA report",
          testReportNameFrench: "Rapport ADN",
        }}
      />,
    ),
  );
  const row = screen.getByText(label("reporting.label.testName")).parentElement;
  expect(row.textContent).toContain("DNA report");
  expect(row.textContent).toContain("Rapport ADN");
  expect(row.textContent).toContain(messages["english.label"]);
  expect(row.textContent).toContain(messages["french.label"]);
});

test("preserves sample test ordering in the read-only review", () => {
  const tests = Object.freeze([
    Object.freeze({ id: "2", name: "Segundo" }),
    Object.freeze({ id: "1", name: "Primero" }),
  ]);
  render(
    wrap(
      <TestReviewSummary
        values={baseValues}
        selectedSampleTypeList={[{ id: "s", value: "Sangre" }]}
        selectedSampleTypeResp={[{ sampleTypeId: "s", tests }]}
      />,
    ),
  );
  expect(
    screen.getAllByRole("listitem").map((item) => item.textContent),
  ).toEqual(["Segundo", "Primero"]);
  expect(tests[0].id).toBe("2");
});

test("new-test header starts empty and follows the current completed wizard values", () => {
  const header = (testToDisplay) =>
    wrap(<CustomTestDataDisplay testToDisplay={testToDisplay} />);
  const view = render(header({ localization: { english: "", french: "" } }));
  expect(screen.getByRole("heading").textContent).toBe("Nueva prueba");
  expect(view.container.querySelector("dl")).toBeNull();
  view.rerender(
    header({
      localization: { english: "ADN", french: "ADN" },
      testUnit: "Genética",
      sampleType: "Sangre",
      loinc: "1234-5",
    }),
  );
  expect(screen.getByRole("heading").textContent).toBe("ADN");
  expect(screen.getByText("Genética")).toBeTruthy();
  expect(screen.getByText("Sangre")).toBeTruthy();
  expect(screen.queryByText("1234-5")).toBeNull();
  view.rerender(
    header({
      localization: { english: "ADN modificado", french: "ADN modificado" },
      sampleType: "Plasma",
    }),
  );
  expect(screen.getByRole("heading").textContent).toBe("ADN modificado");
  expect(screen.queryByText("Sangre")).toBeNull();
  expect(screen.getByText("Plasma")).toBeTruthy();
});
