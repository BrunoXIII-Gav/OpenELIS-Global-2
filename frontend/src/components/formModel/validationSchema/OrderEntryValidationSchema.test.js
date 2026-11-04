import schema from "./OrderEntryValidationSchema";

test("automatic order entry accepts an empty laboratory number", async () => {
  await expect(
    schema.validateAt(
      "sampleOrderItems.labNo",
      {
        sampleOrderItems: { labNo: "" },
      },
      { context: { automaticLabNumber: true } },
    ),
  ).resolves.toBe("");
});

test("other entry forms still require their laboratory number", async () => {
  await expect(
    schema.validateAt("sampleOrderItems.labNo", {
      sampleOrderItems: { labNo: "" },
    }),
  ).rejects.toThrow();
});

test("automatic order entry still requires a requester", async () => {
  await expect(
    schema.validateAt(
      "sampleOrderItems",
      {
        sampleOrderItems: { labNo: "" },
      },
      { context: { automaticLabNumber: true } },
    ),
  ).rejects.toThrow("Search Requester is required");
});

test("automatic order entry preserves configured mandatory fields", async () => {
  await expect(
    schema.validateAt(
      "sampleOrderItems",
      {
        sampleOrderItems: {
          labNo: "",
          providerId: "7",
          additionalFields: [
            {
              fieldKey: "required_question",
              displayName: "Question",
              active: true,
              required: true,
            },
          ],
        },
      },
      { context: { automaticLabNumber: true } },
    ),
  ).rejects.toThrow("Question is required");
});
