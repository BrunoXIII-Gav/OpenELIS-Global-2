import { evaluateConditions } from "./fieldConditions";
import {
  buildOrderConditionContext,
  hasRequiredOrderFieldValue,
  isOrderFieldRequired,
  isOrderFieldVisible,
} from "./orderFieldRules";

test.each([
  ["equals", " routine ", { value: "ROUTINE" }, true],
  ["notequals", "routine", { value: "urgent" }, true],
  ["in", "routine", { values: ["urgent", "ROUTINE"] }, true],
  ["in", "routine", { values: [], value: "ROUTINE" }, true],
  ["notin", "", { values: ["", "urgent"] }, true],
  ["notin", "routine", { values: ["urgent"] }, true],
  ["hasvalue", "0", {}, true],
  ["hasvalue", "   ", {}, false],
  ["istrue", true, {}, true],
  ["istrue", "false", {}, false],
  ["isfalse", false, {}, true],
  ["isfalse", "true", {}, false],
])(
  "%s evaluates a condition consistently",
  (operator, value, comparison, expected) => {
    expect(
      evaluateConditions(
        [{ fieldKey: "source", operator, ...comparison }],
        "ALL",
        {
          source: value,
        },
      ),
    ).toBe(expected);
  },
);

test.each(["ALL", "ANY"])(
  "%s applies to both visibility and required rules",
  (logic) => {
    const conditions = [
      { fieldKey: "priority", operator: "equals", value: "URGENT" },
      { fieldKey: "genomic", operator: "istrue" },
    ];
    const field = {
      fieldKey: "consent",
      metadataJson: JSON.stringify({
        rules: { logic, visibleWhen: conditions, requiredWhen: conditions },
      }),
    };
    const context = { priority: "ROUTINE", genomic: "true" };
    expect(isOrderFieldVisible(field, context)).toBe(logic === "ANY");
    expect(isOrderFieldRequired(field, context)).toBe(logic === "ANY");
  },
);

test("conditions use defaults and the initial unchecked state", () => {
  const context = buildOrderConditionContext({
    additionalFields: [
      { fieldKey: "genomic", fieldType: "BOOLEAN", defaultValue: "true" },
      { fieldKey: "unchecked", fieldType: "BOOLEAN" },
    ],
  });
  expect(context.genomic).toBe("true");
  expect(context.unchecked).toBe("false");
  expect(
    evaluateConditions(
      [{ fieldKey: "unchecked", operator: "isfalse" }],
      "ALL",
      context,
    ),
  ).toBe(true);
});

test("explicit values override defaults and fixed fields remain available", () => {
  const context = buildOrderConditionContext(
    {
      priority: "URGENT",
      additionalFields: [{ fieldKey: "genomic", defaultValue: "true" }],
      additionalFieldValues: { genomic: "false" },
      providerProfileFieldValues: { professional: "12" },
    },
    { rememberSiteAndRequester: true },
  );
  expect(context).toMatchObject({
    genomic: "false",
    priority: "URGENT",
    professional: "12",
    rememberSiteAndRequester: "true",
  });
});

test.each([
  ["TEXT", "", false],
  ["TEXT", "   ", false],
  ["TEXT", "false", true],
  ["NUMBER", "0", true],
  ["BOOLEAN", "false", false],
  ["BOOLEAN", "true", true],
  ["MULTISELECT", ", , ", false],
  ["MULTISELECT", "a,b", true],
])("required %s handles %p", (fieldType, value, expected) => {
  expect(
    hasRequiredOrderFieldValue(
      { fieldKey: "field", fieldType },
      { additionalFieldValues: { field: value } },
    ),
  ).toBe(expected);
});

test("clearing a defaulted field does not satisfy a required rule", () => {
  const field = { fieldKey: "field", defaultValue: "default" };
  expect(
    hasRequiredOrderFieldValue(field, { additionalFieldValues: { field: "" } }),
  ).toBe(false);
});

test.each([
  [undefined, false],
  [{ deleteFile: true, fileName: "consent.pdf" }, false],
  [{ fileName: "consent.pdf" }, true],
  [{ uploadToken: "upload" }, true],
])("document requirement handles %p", (file, expected) => {
  expect(
    hasRequiredOrderFieldValue(
      { fieldKey: "file", fieldType: "DOCUMENT" },
      { additionalFieldFiles: { file } },
    ),
  ).toBe(expected);
});

test("inactive fields stay hidden even if their conditions match", () => {
  expect(isOrderFieldVisible({ fieldKey: "field", active: false }, {})).toBe(
    false,
  );
});
