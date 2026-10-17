import { createIntl, createIntlCache } from "react-intl";
import { buildConsentSourceValues } from "./consentSourceValues";
import messages from "../../languages/es.json";

const intl = createIntl({ locale: "es", messages }, createIntlCache());
const resolve = (sources, form, samples = []) =>
  buildConsentSourceValues(sources, form, samples, intl);

test("a newly defined order field works without adding a named resolver", () => {
  const sources = [
    {
      id: "orderAdditional.future",
      path: "sampleOrderItems.additionalFieldValues.future",
      format: "additional",
      fieldType: "TEXT",
    },
  ];
  expect(
    resolve(sources, {
      sampleOrderItems: { additionalFieldValues: { future: "Dato nuevo" } },
    }),
  ).toEqual({ "orderAdditional.future": "Dato nuevo" });
});

test("selects, multiselects and users print displayed labels, never option IDs", () => {
  const sources = [
    {
      id: "user",
      path: "sampleOrderItems.additionalFieldValues.responsable",
      format: "additional",
      fieldType: "USER",
      options: [
        { optionKey: "173", optionLabel: "MC - Responsable de prueba" },
      ],
    },
    {
      id: "choices",
      path: "sampleOrderItems.additionalFieldValues.choices",
      format: "additional",
      fieldType: "MULTISELECT",
      options: [
        { optionKey: "a", optionLabel: "Primero" },
        { optionKey: "b", optionLabel: "Segundo" },
      ],
    },
    {
      id: "removed",
      path: "sampleOrderItems.additionalFieldValues.removed",
      format: "additional",
      fieldType: "SELECT",
      options: [],
    },
  ];
  const values = resolve(sources, {
    sampleOrderItems: {
      additionalFieldValues: {
        responsable: "173",
        choices: "a,b",
        removed: "9876",
      },
    },
  });
  expect(values).toEqual({
    user: "MC - Responsable de prueba",
    choices: "Primero, Segundo",
    removed: "",
  });
});

test("the current user selector options take precedence over generic options", () => {
  const source = {
    id: "user",
    path: "sampleOrderItems.additionalFieldValues.responsable",
    format: "additional",
    fieldType: "USER",
    fieldKey: "responsable",
    definitionPath: "sampleOrderItems.additionalFields",
    options: [{ optionKey: "173", optionLabel: "Old label" }],
  };
  const form = {
    sampleOrderItems: {
      additionalFieldValues: { responsable: "173" },
      additionalFields: [
        {
          fieldKey: "responsable",
          fieldType: "USER",
          options: [{ optionKey: "173", optionLabel: "Etiqueta visible" }],
        },
      ],
    },
  };
  expect(resolve([source], form).user).toBe("Etiqueta visible");
});

test("only advertised public fields are serialized", () => {
  const result = resolve(
    [{ id: "name", path: "patientProperties.firstName" }],
    {
      patientProperties: {
        patientPK: "9876",
        guid: "secret",
        firstName: "Paciente",
      },
      sampleOrderItems: { providerPersonId: "345" },
    },
  );
  expect(result).toEqual({ name: "Paciente" });
});

test("booleans print localized labels and false is preserved", () => {
  const sources = [
    {
      id: "consent",
      path: "sampleOrderItems.additionalFieldValues.consent",
      format: "additional",
      fieldType: "BOOLEAN",
    },
  ];
  expect(
    resolve(sources, {
      sampleOrderItems: { additionalFieldValues: { consent: "false" } },
    }).consent,
  ).toBe(messages["no.option"]);
});

test("each sample keeps its own type and values", () => {
  const source = {
    id: "sampleAdditional.2.future",
    path: "sampleXML.additionalFieldValues.future",
    scope: "samples",
    sampleTypeId: "2",
    format: "additional",
    fieldType: "TEXT",
  };
  const samples = [
    {
      sampleTypeId: "1",
      sampleXML: { additionalFieldValues: { future: "No corresponde" } },
    },
    {
      sampleTypeId: "2",
      sampleXML: { additionalFieldValues: { future: "Sangre" } },
    },
  ];
  expect(resolve([source], {}, samples)[source.id]).toBe("Sangre");
});

test("keeps all selected test names without internal test IDs", () => {
  const samples = [
    {
      tests: Array.from({ length: 12 }, (_, i) => ({
        id: i + 100,
        name: `Prueba ${i + 1}`,
      })),
    },
  ];
  const result = resolve(
    [{ id: "tests.selectedNames", format: "tests" }],
    {},
    samples,
  );
  expect(result["tests.selectedNames"].split("\n")).toHaveLength(12);
  expect(result["tests.selectedNames"]).toContain("Prueba 12");
});

test("questionnaire mappings use answers and display text, not coding IDs", () => {
  const form = {
    sampleOrderItems: {
      programId: "4",
      additionalQuestions: {
        item: [
          {
            linkId: "group",
            item: [
              {
                linkId: "answer",
                answer: [
                  {
                    valueCoding: {
                      code: "internal-code",
                      display: "Respuesta visible",
                    },
                  },
                ],
              },
            ],
          },
        ],
      },
    },
  };
  const result = resolve(
    [
      {
        id: "questionnaire.4.answer",
        format: "questionnaire",
        programId: "4",
        linkId: "answer",
      },
    ],
    form,
  );
  expect(result["questionnaire.4.answer"]).toBe("Respuesta visible");
});

test("conditionally hidden order fields do not print stale values", () => {
  const source = {
    id: "hidden",
    path: "sampleOrderItems.additionalFieldValues.detail",
    format: "additional",
    fieldType: "TEXT",
    metadataJson: JSON.stringify({
      rules: { visibleWhen: [{ fieldKey: "consent", operator: "istrue" }] },
    }),
  };
  expect(
    resolve([source], {
      sampleOrderItems: {
        additionalFieldValues: { consent: "false", detail: "Stale data" },
      },
    }).hidden,
  ).toBe("");
});

test("age uses the same configured birth-date format as the patient screen", () => {
  jest.useFakeTimers("modern");
  jest.setSystemTime(new Date(2026, 9, 16, 12));
  try {
    const sources = ["years", "months", "days"].map((part) => ({
      id: part,
      format: "age",
      agePart: part,
    }));
    expect(
      buildConsentSourceValues(
        sources,
        { patientProperties: { birthDateForDisplay: "12/08/1993" } },
        [],
        intl,
        { DEFAULT_DATE_LOCALE: "fr-FR" },
      ),
    ).toEqual({ years: "33", months: "2", days: "4" });
  } finally {
    jest.useRealTimers();
  }
});
