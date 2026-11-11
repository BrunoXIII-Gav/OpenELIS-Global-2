import orderSchema from "./OrderEntryValidationSchema";
import modifySchema from "./ModifyOrderEntryValidationSchema";

const consentField = {
  fieldKey: "consent",
  displayName: "Consent",
  fieldType: "BOOLEAN",
  active: true,
  required: false,
  metadataJson: JSON.stringify({
    rules: {
      requiredWhen: [{ fieldKey: "genomic", operator: "isTrue" }],
    },
  }),
};

const validate = (schema, genomic, consent, fieldOverrides = {}) =>
  schema.validateAt("sampleOrderItems", {
    sampleOrderItems: {
      labNo: "LAB001",
      providerId: "7",
      additionalFields: [{ ...consentField, ...fieldOverrides }],
      additionalFieldValues: { genomic, consent },
    },
  });

describe.each([
  ["create order", orderSchema],
  ["modify order", modifySchema],
])("required checkbox: %s", (_name, schema) => {
  test.each(["false", false, "", undefined, "   "])(
    "rejects unchecked consent (%p) when genomic diagnosis is selected",
    async (consent) => {
      await expect(validate(schema, "true", consent)).rejects.toMatchObject({
        path: "sampleOrderItems.additionalFieldValues.consent",
      });
    },
  );

  test.each(["true", true, " TRUE "])(
    "accepts checked consent (%p) when required",
    async (consent) => {
      await expect(validate(schema, "true", consent)).resolves.toBeDefined();
    },
  );

  test("allows unchecked consent when genomic diagnosis is not selected", async () => {
    await expect(validate(schema, "false", "false")).resolves.toBeDefined();
  });

  test("requires a check for an unconditionally required checkbox", async () => {
    await expect(
      validate(schema, "false", "false", { required: true }),
    ).rejects.toMatchObject({
      path: "sampleOrderItems.additionalFieldValues.consent",
    });
  });

  test("does not require consent while the field is hidden", async () => {
    await expect(
      validate(schema, "true", "false", {
        required: true,
        metadataJson: JSON.stringify({
          rules: {
            visibleWhen: [{ fieldKey: "genomic", operator: "isFalse" }],
          },
        }),
      }),
    ).resolves.toBeDefined();
  });

  test("uses a checked default but respects an explicitly unchecked value", async () => {
    await expect(
      validate(schema, "true", undefined, { defaultValue: "true" }),
    ).resolves.toBeDefined();
    await expect(
      validate(schema, "true", "false", { defaultValue: "true" }),
    ).rejects.toThrow();
  });
});
