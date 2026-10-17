import {
  buildConsentBooleanValues,
  buildConsentSourceValues,
} from "./consentSourceValues";

const text = (value) => String(value ?? "").trim();
const fullName = (first, last, full) =>
  text(full) || [text(last), text(first)].filter(Boolean).join(", ");

export const buildConsentPayload = (
  sources,
  form,
  samples,
  intl,
  configuration = {},
) => {
  // Editing orders supplies header values; creation supplies patientProperties.
  // Patient details, when loaded, retain the same shape used by source mappings.
  const details = form.patientProperties || {};
  const patient = {
    nationalId: form.nationalId || "",
    gender: form.gender || "",
    birthDateForDisplay: form.dob || "",
    ...details,
    fullName: fullName(
      details.firstName,
      details.lastName,
      details.fullName ||
        (!details.firstName && !details.lastName ? form.patientName : ""),
    ),
  };
  const normalizedForm = { ...form, patientProperties: patient };
  const order = form.sampleOrderItems || {};
  const selectedTests = [
    ...new Set(
      samples
        .flatMap((sample) =>
          (sample.tests || []).map((test) => text(test.name)),
        )
        .filter(Boolean),
    ),
  ];
  return {
    sourceValues: buildConsentSourceValues(
      sources,
      normalizedForm,
      samples,
      intl,
      configuration,
    ),
    booleanValues: buildConsentBooleanValues(sources, normalizedForm, samples),
    patient: {
      firstName: patient.firstName || "",
      lastName: patient.lastName || "",
      fullName: patient.fullName,
      nationalId: patient.nationalId || "",
    },
    provider: {
      firstName: order.providerFirstName || "",
      lastName: order.providerLastName || "",
      fullName: fullName(order.providerFirstName, order.providerLastName),
      dni: order.providerDni || "",
    },
    selectedTests,
    orderAdditionalFieldValues: order.additionalFieldValues || {},
    orderDate: order.requestDate || configuration.currentDateAsText,
    cug:
      samples.find((sample) => text(sample.sampleXML?.cug))?.sampleXML.cug ||
      "",
  };
};
