import {
  parse,
  isValid,
  differenceInYears,
  differenceInMonths,
  differenceInDays,
  addYears,
  addMonths,
} from "date-fns";
import { evaluateConditions } from "../utils/fieldConditions";
import { nationalityList } from "../data/countries";
// Resolve only the public sources advertised by the server. Do not serialize the
// order form wholesale: it also contains primary keys, codes and workflow state.
const read = (object, path) => {
  if (!path) return "";
  return path.split(".").reduce((value, key) => value?.[key], object);
};
const text = (value) => (value == null ? "" : String(value).trim());
const fullName = (person = {}) =>
  person.fullName ||
  [person.lastName, person.firstName].filter(Boolean).join(", ");
const uniqueLines = (values) =>
  [...new Set(values.map(text).filter(Boolean))].join("\n");
const findQuestion = (items, linkId) => {
  for (const item of items || []) {
    if (item.linkId === linkId) return item;
    const nested = findQuestion(item.item, linkId);
    if (nested) return nested;
  }
};

export const buildConsentSourceValues = (
  sources,
  form,
  samples,
  intl,
  configuration = {},
) => {
  const patient = form.patientProperties || {};
  const order = form.sampleOrderItems || {};
  const yesNo = (value) =>
    intl.formatMessage({ id: value ? "yes.option" : "no.option" });
  const resolve = (source, root) => {
    if (
      source.profileCode &&
      source.profileCode !== order.providerProfessionalProfileCode
    )
      return "";
    if (
      source.sampleTypeId &&
      String(source.sampleTypeId) !== String(root.sampleTypeId)
    )
      return "";
    const value = read(root, source.path);
    // Order selects are still mounted when consent is downloaded. Prefer their
    // exact visible label, including configured specialties and priorities.
    if (
      source.scope === "order" &&
      source.fieldKey &&
      typeof document !== "undefined"
    ) {
      const control =
        document.getElementById(source.fieldKey + "Id") ||
        document.getElementById(source.fieldKey);
      if (source.format === "specialty" && control?.tagName === "INPUT")
        return text(value);
      if (control?.tagName === "SELECT" && control.value === text(value)) {
        return value ? control.selectedOptions?.[0]?.textContent || "" : "";
      }
    }
    switch (source.format) {
      case "age": {
        const birthDate = parse(
          patient.birthDateForDisplay || "",
          configuration.DEFAULT_DATE_LOCALE === "fr-FR"
            ? "dd/MM/yyyy"
            : "MM/dd/yyyy",
          new Date(),
        );
        if (!isValid(birthDate)) return "";
        const now = new Date();
        const years = differenceInYears(now, birthDate);
        const months = differenceInMonths(now, addYears(birthDate, years));
        const days = differenceInDays(
          now,
          addMonths(addYears(birthDate, years), months),
        );
        return String({ years, months, days }[source.agePart]);
      }
      case "identifierType": {
        const labels = {
          DNI: "patient.identifier.dni",
          PASSPORT: "patient.identifier.passport",
          FOREIGN_ID: "patient.identifier.foreign.card",
        };
        return labels[value] ? intl.formatMessage({ id: labels[value] }) : "";
      }
      case "nationality":
        return (
          nationalityList.find((option) => option.value === value)?.label || ""
        );
      case "specialty": {
        const profileField = (order.providerProfileFields || []).find(
          (field) => field.legacyBinding === "providerSpecialty",
        );
        return (
          profileField?.options?.find((option) => option.optionKey === value)
            ?.optionLabel || ""
        );
      }
      case "storage":
        return (
          value?.displayPath ||
          value?.hierarchicalPath ||
          value?.name ||
          [
            value?.device?.name,
            value?.shelf?.name,
            value?.rack?.name,
            value?.box?.name,
            value?.positionCoordinate,
          ]
            .filter(Boolean)
            .join(" / ")
        );
      case "patientName":
        return fullName(patient);
      case "providerName":
        return fullName({
          firstName: order.providerFirstName,
          lastName: order.providerLastName,
        });
      case "tests":
        return uniqueLines(
          samples.flatMap((sample) =>
            (sample.tests || []).map((test) => test.name),
          ),
        );
      case "panels":
        return uniqueLines(
          samples.flatMap((sample) =>
            (sample.panels || []).map((panel) => panel.name),
          ),
        );
      case "gender":
        return value
          ? intl.formatMessage({
              id:
                value === "M"
                  ? "patient.male"
                  : value === "F"
                    ? "patient.female"
                    : "patient.other",
            })
          : "";
      case "priority":
        return value
          ? intl.formatMessage({ id: `config.display.value.${value}` })
          : "";
      case "sampleType":
        return root.sampleType || root.sampleTypeName || "";
      case "questionnaire": {
        if (String(source.programId) !== String(order.programId)) return "";
        const item = findQuestion(
          order.additionalQuestions?.item,
          source.linkId,
        );
        return (item?.answer || [])
          .map((answer) => {
            if (answer.valueBoolean !== undefined)
              return yesNo(answer.valueBoolean);
            if (answer.valueCoding) return answer.valueCoding.display || "";
            if (answer.valueReference)
              return answer.valueReference.display || "";
            if (answer.valueQuantity)
              return [answer.valueQuantity.value, answer.valueQuantity.unit]
                .filter((v) => v != null)
                .join(" ");
            return (
              answer.valueString ??
              answer.valueInteger ??
              answer.valueDecimal ??
              answer.valueDate ??
              answer.valueDateTime ??
              answer.valueTime ??
              ""
            );
          })
          .map(text)
          .filter(Boolean)
          .join("\n");
      }
      default:
        break;
    }
    if (source.format === "additional" || source.format === "option") {
      const currentDefinition = (read(root, source.definitionPath) || []).find(
        (field) => field.fieldKey === source.fieldKey,
      );
      const definition = currentDefinition || source;
      const type = definition.fieldType || source.fieldType;
      if (currentDefinition?.active === false) return "";
      let metadata = {};
      try {
        metadata = JSON.parse(
          definition.metadataJson || source.metadataJson || "{}",
        );
      } catch (_) {
        /* No configured rules. */
      }
      const context = {
        ...patient,
        ...order,
        ...(order.additionalFieldValues || {}),
        ...(order.providerProfileFieldValues || {}),
        ...(root.sampleXML || {}),
        ...(root.sampleXML?.additionalFieldValues || {}),
      };
      if (
        !evaluateConditions(
          metadata.rules?.visibleWhen,
          metadata.rules?.logic,
          context,
        )
      )
        return "";
      if (type === "DOCUMENT") return text(read(root, source.filePath));
      if (type === "BOOLEAN")
        return value === "" || value == null
          ? ""
          : yesNo(value === true || value === "true");
      const options =
        definition.options || read(root, source.optionsPath) || [];
      if (
        source.format === "option" ||
        options.length ||
        [
          "SELECT",
          "MULTISELECT",
          "RADIO",
          "USER",
          "SYSTEM_USER_BIOLOGIST_SELECT",
        ].includes(type)
      ) {
        const entries = Array.isArray(value) ? value : text(value).split(",");
        return entries
          .map((entry) => {
            const option = options.find(
              (option) => String(option.optionKey ?? option.id) === text(entry),
            );
            // Unknown/stale selections stay empty; never print a database identifier.
            return option?.optionLabel ?? option?.value ?? option?.name ?? "";
          })
          .filter(Boolean)
          .join(", ");
      }
    }
    return typeof value === "object" ? "" : text(value);
  };
  return Object.fromEntries(
    sources.map((source) => [
      source.id,
      source.scope === "samples"
        ? uniqueLines(samples.map((sample) => resolve(source, sample)))
        : text(resolve(source, form)),
    ]),
  );
};

// Keep typed booleans separate from their translated display labels.
export const buildConsentBooleanValues = (sources, form, samples) =>
  Object.fromEntries(
    sources
      .filter((source) => source.fieldType === "BOOLEAN")
      .flatMap((source) => {
        const roots =
          source.scope === "samples"
            ? samples.filter(
                (sample) =>
                  !source.sampleTypeId ||
                  String(sample.sampleTypeId) === String(source.sampleTypeId),
              )
            : [form];
        const values = [
          ...new Set(
            roots
              .map((root) => read(root, source.path))
              .filter((value) => value !== "" && value != null),
          ),
        ];
        return values.length === 1
          ? [[source.id, values[0] === true || values[0] === "true"]]
          : [];
      }),
  );
