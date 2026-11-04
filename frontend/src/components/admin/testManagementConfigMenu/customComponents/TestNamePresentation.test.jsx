/* eslint-env jest */
import React from "react";
import { act, fireEvent, render, screen, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { ConfigurationContext } from "../../../layout/Layout";
import {
  StepOneTestNameAndTestSection,
  StepSevenFinalDisplayAndSaveConfirmation,
} from "./TestStepForm";
import { TestFormData } from "./TestFormData";
import { TestNameValue } from "./TestNamePresentation";
import { CustomTestDataDisplay } from "./CustomTestDataDisplay";
import messages from "../../../../languages/es.json";

jest.mock("../../../layout/Layout", () => ({
  NotificationContext: require("react").createContext({}),
  ConfigurationContext: require("react").createContext(null),
}));
jest.mock("../../../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));
jest.mock("../sortableListComponent/SortableList", () => ({
  CustomCommonSortableOrderList: () => null,
}));

const names = (name, report = name) => ({
  testNameEnglish: name,
  testNameFrench: name,
  testReportNameEnglish: report,
  testReportNameFrench: report,
});
const wrap = (children, enabled = "true") => (
  <IntlProvider locale="es" messages={messages}>
    <ConfigurationContext.Provider
      value={{ configurationProperties: { SIMPLIFIED_TEST_NAMES: enabled } }}
    >
      {children}
    </ConfigurationContext.Provider>
  </IntlProvider>
);
const setup = (overrides = {}, enabled = "true") => {
  const saved = jest.fn();
  const formData = { ...TestFormData, testSection: "1", ...overrides };
  const step = (data) =>
    wrap(
      <StepOneTestNameAndTestSection
        formData={data}
        handleNextStep={saved}
        labUnitList={[{ id: "1", value: "Genética" }]}
        setSelectedLabUnitList={jest.fn()}
      />,
      enabled,
    );
  const view = render(step(formData));
  return {
    ...view,
    saved,
    formData,
    revisit: (data) => {
      view.rerender(wrap(<div />));
      view.rerender(step(data));
    },
  };
};
const change = async (element, value) =>
  act(async () => {
    fireEvent.change(element, { target: { value } });
  });
const submit = async (view) => {
  await act(async () => {
    fireEvent.submit(view.container.querySelector("form"));
  });
};

test("new Spanish name supplies all four required legacy values without changing unrelated data", async () => {
  const view = setup({ loinc: "123", additionalFields: [{ id: "77" }] });
  expect(screen.queryByText(messages["english.label"])).toBeNull();
  await change(
    screen.getByLabelText(messages["sample.entry.project.testName"]),
    "Extracción de ADN",
  );
  await submit(view);
  await wait(() =>
    expect(view.saved).toHaveBeenCalledWith(
      { ...view.formData, ...names("Extracción de ADN") },
      true,
    ),
  );
});

test("renaming a synchronized test updates both names and the linked report name", async () => {
  const view = setup(names("Anterior"));
  await change(
    screen.getByLabelText(messages["sample.entry.project.testName"]),
    "Nuevo nombre",
  );
  await submit(view);
  await wait(() =>
    expect(view.saved.mock.calls[0][0]).toMatchObject(names("Nuevo nombre")),
  );
});

test("an existing distinct report name remains visible and is preserved on rename and revisit", async () => {
  const view = setup(names("Interno", "Para el paciente"));
  expect(
    screen.getByLabelText(messages["reporting.label.testName"]).value,
  ).toBe("Para el paciente");
  await change(
    screen.getByLabelText(messages["sample.entry.project.testName"]),
    "Interno nuevo",
  );
  await submit(view);
  await wait(() =>
    expect(view.saved.mock.calls[0][0]).toMatchObject(
      names("Interno nuevo", "Para el paciente"),
    ),
  );
  view.revisit(view.saved.mock.calls[0][0]);
  expect(
    screen.getByLabelText(messages["reporting.label.testName"]).value,
  ).toBe("Para el paciente");
});

test("the optional report name synchronizes its pair and can explicitly return to the test name", async () => {
  const view = setup(names("ADN"));
  await act(async () =>
    fireEvent.click(
      screen.getByLabelText(messages["test.names.customReportName"]),
    ),
  );
  await change(
    screen.getByLabelText(messages["reporting.label.testName"]),
    "Estudio de ADN",
  );
  await submit(view);
  await wait(() =>
    expect(view.saved.mock.calls[0][0]).toMatchObject(
      names("ADN", "Estudio de ADN"),
    ),
  );
  await act(async () =>
    fireEvent.click(
      screen.getByLabelText(messages["test.names.customReportName"]),
    ),
  );
  await submit(view);
  await wait(() =>
    expect(view.saved.mock.calls[1][0]).toMatchObject(names("ADN")),
  );
});

test.each(["testNameFrench", "testReportNameFrench"])(
  "different existing %s retains bilingual editing without overwriting the other name",
  async (key) => {
    const view = setup({ ...names("English name"), [key]: "Nom français" });
    expect(view.container.querySelector("#testNameFr")).not.toBeNull();
    expect(
      screen.queryByLabelText(messages["test.names.customReportName"]),
    ).toBeNull();
    await change(view.container.querySelector("#testNameEn"), "Edited name");
    await submit(view);
    await wait(() =>
      expect(view.saved.mock.calls[0][0]).toMatchObject({
        ...view.formData,
        testNameEnglish: "Edited name",
      }),
    );
  },
);

test("disabled installation setting keeps the original four independent inputs", async () => {
  const view = setup(names("ADN"), "false");
  await change(view.container.querySelector("#testNameEn"), "Only English");
  await submit(view);
  await wait(() =>
    expect(view.saved.mock.calls[0][0]).toMatchObject({
      ...names("ADN"),
      testNameEnglish: "Only English",
    }),
  );
});

test("blank test names and blank optional report names cannot advance", async () => {
  const view = setup();
  await submit(view);
  expect(view.saved).not.toHaveBeenCalled();
  await change(
    screen.getByLabelText(messages["sample.entry.project.testName"]),
    "ADN",
  );
  await act(async () =>
    fireEvent.click(
      screen.getByLabelText(messages["test.names.customReportName"]),
    ),
  );
  await change(screen.getByLabelText(messages["reporting.label.testName"]), "");
  await submit(view);
  expect(view.saved).not.toHaveBeenCalled();
  expect(screen.getByText(messages["test.names.reportRequired"])).toBeTruthy();
});

test("summary name rendering simplifies equal values but preserves distinct translations", () => {
  const view = render(wrap(<TestNameValue english="ADN" french="ADN" />));
  expect(screen.getAllByText("ADN")).toHaveLength(1);
  expect(screen.queryByText(messages["english.label"])).toBeNull();
  view.rerender(wrap(<TestNameValue english="DNA" french="ADN" />));
  expect(view.container.textContent).toContain(messages["english.label"]);
  expect(view.container.textContent).toContain(messages["french.label"]);
});

test("the compact test header shows the test identity without duplicating report details", () => {
  const view = render(
    wrap(
      <CustomTestDataDisplay
        testToDisplay={{
          localization: { english: "ADN", french: "ADN" },
          reportLocalization: { english: "Estudio", french: "Estudio" },
          sampleType: "Sangre",
        }}
      />,
    ),
  );
  expect(view.container.textContent).not.toContain("en :");
  expect(screen.queryByText("Estudio")).toBeNull();
  expect(screen.getByText("ADN")).toBeTruthy();
  expect(screen.getByText("Sangre")).toBeTruthy();
});

test("the final review keeps the legacy save payload and shows a single name per pair", async () => {
  const saved = jest.fn();
  const previous = jest.fn();
  const formData = { ...TestFormData, ...names("ADN", "Informe ADN") };
  const view = render(
    wrap(
      <StepSevenFinalDisplayAndSaveConfirmation
        formData={formData}
        currentStep={6}
        handleNextStep={saved}
        handlePreviousStep={previous}
        panelListTag={[]}
        selectedSampleTypeList={[]}
        selectedSampleTypeResp={[]}
        selectedResultTypeList={{ value: "Texto libre" }}
      />,
    ),
  );
  expect(screen.queryByText(messages["english.label"])).toBeNull();
  expect(view.container.textContent.match(/Informe ADN/g)).toHaveLength(1);
  await act(async () =>
    fireEvent.click(
      screen.getByRole("button", { name: messages["back.action.button"] }),
    ),
  );
  expect(previous).toHaveBeenCalledWith(formData);
  await submit(view);
  await wait(() => expect(saved).toHaveBeenCalledWith(formData, false));
});
