import { createIntl, createIntlCache } from "react-intl";
import { buildConsentPayload } from "./consentPayload";
import messages from "../../languages/es.json";

const intl = createIntl({ locale: "es", messages }, createIntlCache());

test("creation and editing send the selected requester identity without sending signature bytes", () => {
  const sources = [
    { id: "provider.signature", valueType: "image", format: "serverImage" },
  ];
  for (const header of [
    { patientProperties: { firstName: "Ana" } },
    { patientName: "Ana", accessionNumber: "123" },
  ]) {
    const form = {
      ...header,
      sampleOrderItems: {
        providerId: "provider-2",
        providerPersonId: "person-2",
        providerFirstName: "Pedro",
        providerLastName: "Alcacer",
        signatureImageData: "must-not-send",
      },
    };
    const payload = buildConsentPayload(sources, form, [], intl);
    expect(payload.provider).toEqual(
      expect.objectContaining({
        providerId: "provider-2",
        personId: "person-2",
        fullName: "Alcacer, Pedro",
      }),
    );
    expect(payload.sourceValues).not.toHaveProperty("provider.signature");
    expect(JSON.stringify(payload)).not.toContain("must-not-send");
  }
});

test("document image mappings send only upload references, including replacement and deletion on edit", () => {
  const sources = [
    {
      id: "orderDocumentImage.patient",
      valueType: "image",
      format: "orderDocumentImage",
      fieldKey: "patient",
    },
    {
      id: "orderDocumentImage.representative",
      valueType: "image",
      format: "orderDocumentImage",
      fieldKey: "representative",
    },
  ];
  const form = {
    sampleOrderItems: {
      requestDate: "16/11/2026",
      fixedFieldConfigs: [{ fieldKey: "requestDate" }],
      additionalFieldFiles: {
        patient: {
          uploadToken: "session-token",
          fileName: "patient.png",
          base64Content: "do-not-send",
          fileType: "image/png",
        },
        representative: { fileName: "saved.jpg" },
        unrelated: { uploadToken: "unrelated-token" },
      },
    },
  };
  const created = buildConsentPayload(sources, form, [], intl);
  expect(created.orderDocumentFiles).toEqual({
    patient: { uploadToken: "session-token", deleteFile: false },
    representative: { uploadToken: "", deleteFile: false },
  });
  expect(created.sourceValues).toEqual({});
  expect(JSON.stringify(created)).not.toContain("do-not-send");
  expect(created.orderFixedFieldValues.requestDate).toBe("16/11/2026");
  form.sampleOrderItems.additionalFieldFiles.representative = {
    deleteFile: true,
  };
  const edited = buildConsentPayload(sources, form, [], intl);
  expect(edited.orderDocumentFiles.representative).toEqual({
    uploadToken: "",
    deleteFile: true,
  });
});
