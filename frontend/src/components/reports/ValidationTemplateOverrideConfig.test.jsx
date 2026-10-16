/* eslint-env jest */
import React from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import ValidationTemplateOverrideConfig from "./ValidationTemplateOverrideConfig";
import messages from "../../languages/en.json";
import {
  getAllTests,
  getValidationFieldOptions,
  getUserFieldOptions,
  getValidationTemplateOverrides,
  saveValidationTemplateOverride,
} from "../../services/reportTemplateOverrideService";

jest.mock("../../services/reportTemplateOverrideService");

const parameter = "CONCLUSION_DIAGNOSTICO";
const source = "analysisResult1";
let storedTemplate;

const mappingFixture = () => ({
  type: "conditional",
  defaultType: "source",
  defaultValue: "",
  conditionSource: source,
  rules: [
    {
      operator: "lt",
      compareTo: "30",
      resultType: "constant",
      resultValue: "CONFIRMA",
    },
    {
      operator: "gt",
      compareTo: "56",
      resultType: "constant",
      resultValue: "DESCARTA",
    },
  ],
  fallbackType: "constant",
  fallbackValue: "INDETERMINADO",
});

const openTemplate = (mapping) => {
  storedTemplate = {
    id: "template-id",
    name: "Conditional template",
    testIds: ["test-id"],
    config: {
      mode: "jasper_dynamic",
      templateName: "report",
      templateContent: `<jasperReport name="report"><parameter name="${parameter}" class="java.lang.String"/></jasperReport>`,
      parameterDefinitions: [
        { name: parameter, className: "java.lang.String" },
      ],
      mappings: { [parameter]: mapping },
    },
  };
  const view = render(
    <IntlProvider locale="en" messages={messages}>
      <ValidationTemplateOverrideConfig />
    </IntlProvider>,
  );
  fireEvent.click(screen.getByText("Conditional template"));
  return view;
};

const input = (container, suffix) =>
  container.querySelector(
    `[id="mapping-conditional-${suffix}-${parameter}-0"]`,
  );

const saveAndReopen = async () => {
  await act(async () => fireEvent.click(screen.getByText("Save")));
  expect(saveValidationTemplateOverride).toHaveBeenCalledTimes(1);
  fireEvent.click(screen.getByText("New Template"));
  fireEvent.click(screen.getByText("Conditional template"));
};

beforeEach(() => {
  jest.clearAllMocks();
  getAllTests.mockImplementation((callback) =>
    callback([{ id: "test-id", name: "Test" }]),
  );
  getValidationFieldOptions.mockImplementation((ids, callback) =>
    callback({
      sources: [
        { id: source, label: "Analysis Result #1", dataType: "number" },
      ],
    }),
  );
  getUserFieldOptions.mockImplementation((ids, callback) => callback({}));
  getValidationTemplateOverrides.mockImplementation((callback) =>
    callback([storedTemplate]),
  );
  saveValidationTemplateOverride.mockImplementation(async (payload) => {
    storedTemplate = JSON.parse(JSON.stringify(payload));
    return storedTemplate;
  });
});

test("restores all range rules and the fallback, and preserves edits after saving and reopening", async () => {
  const mapping = mappingFixture();
  const { container } = openTemplate(mapping);
  expect(input(container, "operator").value).toBe("lt");
  expect(input(container, "compareTo").value).toBe("30");
  expect(
    container.querySelector(
      `[id="mapping-conditional-compareTo-${parameter}-1"]`,
    ).value,
  ).toBe("56");
  expect(screen.getByDisplayValue("CONFIRMA")).toBeTruthy();
  expect(screen.getByDisplayValue("DESCARTA")).toBeTruthy();
  expect(screen.getByDisplayValue("INDETERMINADO")).toBeTruthy();

  fireEvent.change(input(container, "compareTo"), { target: { value: "31" } });
  await saveAndReopen();
  expect(storedTemplate.config.mappings[parameter]).toEqual({
    ...mapping,
    rules: [{ ...mapping.rules[0], compareTo: "31" }, mapping.rules[1]],
  });
  expect(input(container, "compareTo").value).toBe("31");
  expect(screen.getByDisplayValue("DESCARTA")).toBeTruthy();
});

test.each(["source", "empty"])(
  "retains zero bounds and source/empty outcomes with a %s fallback",
  async (fallbackType) => {
    const mapping = {
      ...mappingFixture(),
      rules: [
        {
          operator: "lte",
          compareTo: "0",
          resultType: "source",
          resultValue: source,
        },
        {
          operator: "gte",
          compareTo: "56",
          resultType: "empty",
          resultValue: "",
        },
      ],
      fallbackType,
      fallbackValue: fallbackType === "source" ? source : "",
    };
    const { container } = openTemplate(mapping);
    expect(input(container, "compareTo").value).toBe("0");
    expect(input(container, "operator").value).toBe("lte");
    expect(
      container.querySelector(`[id="${parameter}-fallbackType"]`).value,
    ).toBe(fallbackType);
    await saveAndReopen();
    expect(storedTemplate.config.mappings[parameter]).toEqual(mapping);
    expect(input(container, "compareTo").value).toBe("0");
  },
);

test("keeps older single-rule configurations editable and saves them in the current format", async () => {
  const { container } = openTemplate({
    type: "conditional",
    conditionSource: source,
    operator: "gt",
    compareTo: "56",
    trueType: "constant",
    trueValue: "CONFIRMA",
    falseType: "constant",
    falseValue: "DESCARTA",
  });
  expect(input(container, "operator").value).toBe("gt");
  expect(input(container, "compareTo").value).toBe("56");
  expect(screen.getByDisplayValue("DESCARTA")).toBeTruthy();
  await saveAndReopen();
  expect(storedTemplate.config.mappings[parameter].rules).toEqual([
    {
      operator: "gt",
      compareTo: "56",
      resultType: "constant",
      resultValue: "CONFIRMA",
    },
  ]);
  expect(storedTemplate.config.mappings[parameter].fallbackValue).toBe(
    "DESCARTA",
  );
  expect(input(container, "compareTo").value).toBe("56");
});

const setupTwoTemplates = () => {
  let secondTemplate;
  getValidationTemplateOverrides.mockImplementation((callback) => {
    if (!secondTemplate) {
      secondTemplate = JSON.parse(JSON.stringify(storedTemplate));
      secondTemplate.id = "second-template-id";
      secondTemplate.name = "Ataxia template";
      secondTemplate.testIds = ["other-test-id"];
      secondTemplate.config.mappings[parameter].rules[0].compareTo = "43";
    }
    callback([storedTemplate, secondTemplate]);
  });
  saveValidationTemplateOverride.mockImplementation(async (payload) => {
    if (payload.id === storedTemplate.id) {
      storedTemplate = JSON.parse(JSON.stringify(payload));
    } else {
      secondTemplate = JSON.parse(JSON.stringify(payload));
    }
    return payload;
  });
  return openTemplate(mappingFixture());
};

test("isolates edits and saves between templates using the same parameter name", async () => {
  const { container } = setupTwoTemplates();
  fireEvent.change(input(container, "compareTo"), { target: { value: "40" } });
  fireEvent.click(screen.getByText("Ataxia template"));
  expect(input(container, "compareTo").value).toBe("43");
  fireEvent.click(screen.getByText("Conditional template"));
  expect(input(container, "compareTo").value).toBe("30");
  fireEvent.change(input(container, "compareTo"), { target: { value: "40" } });
  await act(async () => fireEvent.click(screen.getByText("Save")));
  fireEvent.click(screen.getByText("Ataxia template"));
  expect(input(container, "compareTo").value).toBe("43");
  expect(saveValidationTemplateOverride.mock.calls[0][0].id).toBe(
    "template-id",
  );
});

test("a delayed save does not replace the template selected while it was pending", async () => {
  const { container } = setupTwoTemplates();
  let finishSave;
  saveValidationTemplateOverride.mockImplementation((payload) => {
    storedTemplate = JSON.parse(JSON.stringify(payload));
    return new Promise((resolve) => {
      finishSave = () => resolve(storedTemplate);
    });
  });
  fireEvent.change(input(container, "compareTo"), { target: { value: "40" } });
  fireEvent.click(screen.getByText("Save"));
  fireEvent.click(screen.getByText("Ataxia template"));
  fireEvent.change(input(container, "compareTo"), { target: { value: "44" } });
  await act(async () => finishSave());
  expect(container.querySelector(".is-selected strong").textContent).toBe(
    "Ataxia template",
  );
  expect(input(container, "compareTo").value).toBe("44");
});

test("ignores field options from a previously selected template when responses arrive out of order", () => {
  const pendingOptions = {};
  getValidationFieldOptions.mockImplementation((ids, callback) => {
    if (ids.length === 0) {
      callback({
        sources: [{ id: source, label: source, dataType: "number" }],
      });
    } else {
      pendingOptions[ids[0]] = callback;
    }
  });
  const { container } = setupTwoTemplates();
  fireEvent.click(screen.getByText("Ataxia template"));
  act(() =>
    pendingOptions["other-test-id"]({
      sources: [{ id: source, label: "Ataxia result", dataType: "number" }],
    }),
  );
  act(() =>
    pendingOptions["test-id"]({
      sources: [{ id: source, label: "HTT result", dataType: "number" }],
    }),
  );
  const selection = container.querySelector(
    `[id="mapping-conditional-source-${parameter}"]`,
  );
  expect(selection.selectedOptions[0].textContent).toBe("Ataxia result");
  expect(input(container, "compareTo").value).toBe("43");
});
