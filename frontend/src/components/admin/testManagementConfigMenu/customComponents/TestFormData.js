export const TestFormData = {
  testNameEnglish: "",
  testNameFrench: "",
  testReportNameEnglish: "",
  testReportNameFrench: "",
  testSection: "",
  panels: [],
  uom: "",
  loinc: "",
  resultName: "",
  resultActive: true,
  resultEntryScope: "OFFICIAL",
  resultBlockName: "Official",
  resultBlockSortOrder: "1",
  resultFieldSortOrder: "1",
  resultTubeSelectorEnabled: false,
  resultTubeSelectorMin: "1",
  resultTubeSelectorMax: "2",
  resultTubeActivationCount: "",
  resultTubeQuantitySource: false,
  resultTubeLabelEnabled: false,
  resultChildTubeUsageBlockEnabled: false,
  resultDisplayConfigJson: "",
  resultType: "",
  additionalFields: [],
  orderable: "Y",
  directSampleUsageEnabled: "N",
  activeChildDependency: false,
  activeParentDependency: false,
  skipValidationWhenParentComplete: "N",
  notifyResults: "N",
  inLabOnly: "N",
  antimicrobialResistance: "N",
  active: "Y",
  dictionary: [],
  dictionaryReference: "",
  defaultTestResult: "",
  sampleTypes: [],
  sampleTypeIds: [],
  panelIds: [],
  lowValid: "-Infinity",
  highValid: "Infinity",
  lowReportingRange: "-Infinity",
  highReportingRange: "Infinity",
  lowCritical: "-Infinity",
  highCritical: "Infinity",
  significantDigits: "0",
  resultLimits: [
    {
      ageRange: "0",
      highAgeRange: "Infinity",
      gender: false,
      lowNormal: "-Infinity",
      highNormal: "Infinity",
      lowNormalFemale: "-Infinity",
      highNormalFemale: "Infinity",
    },
  ],
};

const ENTRY_SCOPE_OFFICIAL = "OFFICIAL";
const ENTRY_SCOPE_PRELIMINARY = "PRELIMINARY";
const DEFAULT_OFFICIAL_BLOCK = "Official";
const DEFAULT_PRELIMINARY_BLOCK = "Preliminary";

const parseResultFieldMetadata = (metadataJson) => {
  const defaults = {
    blockName: DEFAULT_OFFICIAL_BLOCK,
    entryScope: ENTRY_SCOPE_OFFICIAL,
    includeInValidation: true,
    active: true,
    blockSortOrder: 1,
    fieldSortOrder: 1,
    tubeSelectorEnabled: false,
    tubeSelectorMin: "1",
    tubeSelectorMax: "2",
    tubeActivationCount: "",
    tubeQuantitySource: false,
    tubeLabelEnabled: false,
    childTubeUsageBlockEnabled: false,
  };
  if (!metadataJson || typeof metadataJson !== "string") {
    return defaults;
  }
  try {
    const parsed = JSON.parse(metadataJson);
    const scope =
      String(parsed?.entryScope || "").toUpperCase() === ENTRY_SCOPE_PRELIMINARY
        ? ENTRY_SCOPE_PRELIMINARY
        : ENTRY_SCOPE_OFFICIAL;
    const blockName =
      typeof parsed?.resultBlock === "string" && parsed.resultBlock.trim()
        ? parsed.resultBlock.trim()
        : scope === ENTRY_SCOPE_PRELIMINARY
          ? DEFAULT_PRELIMINARY_BLOCK
          : DEFAULT_OFFICIAL_BLOCK;
    const includeInValidation =
      typeof parsed?.includeInValidation === "boolean"
        ? parsed.includeInValidation
        : scope === ENTRY_SCOPE_PRELIMINARY
          ? false
          : true;
    const blockSortOrder = Number.parseInt(parsed?.blockSortOrder, 10);
    const fieldSortOrder = Number.parseInt(parsed?.fieldSortOrder, 10);
    const tubeSelectorNode =
      parsed?.tubeSelector && typeof parsed.tubeSelector === "object"
        ? parsed.tubeSelector
        : {};
    const tubeBlockNode =
      parsed?.tubeBlock && typeof parsed.tubeBlock === "object"
        ? parsed.tubeBlock
        : {};
    const tubeSelectorMin = Number.parseInt(tubeSelectorNode?.min, 10);
    const tubeSelectorMax = Number.parseInt(tubeSelectorNode?.max, 10);
    const tubeActivationCount = Number.parseInt(
      tubeBlockNode?.activationCount,
      10,
    );
    return {
      blockName,
      entryScope: scope,
      includeInValidation,
      blockSortOrder:
        Number.isFinite(blockSortOrder) && blockSortOrder > 0
          ? blockSortOrder
          : 1,
      fieldSortOrder:
        Number.isFinite(fieldSortOrder) && fieldSortOrder > 0
          ? fieldSortOrder
          : 1,
      active: parsed?.active !== false,
      tubeSelectorEnabled: tubeSelectorNode?.enabled === true,
      tubeSelectorMin:
        Number.isFinite(tubeSelectorMin) && tubeSelectorMin > 0
          ? String(tubeSelectorMin)
          : "1",
      tubeSelectorMax:
        Number.isFinite(tubeSelectorMax) && tubeSelectorMax > 0
          ? String(tubeSelectorMax)
          : "2",
      tubeActivationCount:
        Number.isFinite(tubeActivationCount) && tubeActivationCount > 0
          ? String(tubeActivationCount)
          : "",
      tubeQuantitySource: parsed?.tubeQuantitySource === true,
      tubeLabelEnabled: parsed?.tubeLabel?.enabled === true,
      childTubeUsageBlockEnabled: parsed?.tubeUsage?.childBlockEnabled === true,
    };
  } catch (e) {
    return defaults;
  }
};

export const extractAgeRangeParts = (rangeStr) => {
  const [start, end] = rangeStr.split("-");

  const parseAge = (ageStr) => {
    const parts = ageStr.split("/");

    let d = 0,
      m = 0,
      y = 0;

    for (let part of parts) {
      part = part.trim().toUpperCase();
      if (part.endsWith("D")) d = parseInt(part.replace("D", ""), 10);
      if (part.endsWith("M")) m = parseInt(part.replace("M", ""), 10);
      if (part.endsWith("Y")) y = parseInt(part.replace("Y", ""), 10);
    }

    if (y > 0) return { raw: y, unit: "Y" };
    if (m > 0) return { raw: m, unit: "M" };
    if (d > 0) return { raw: d, unit: "D" };

    return { raw: 0, unit: "Y" };
  };

  const low = start ? parseAge(start) : { raw: "0", unit: "Y" };
  const high = end ? parseAge(end) : { raw: "Infinity", unit: "Y" };

  return { low, high };
};

const isNumericRange = (str) => {
  if (typeof str !== "string") {
    return false;
  }
  const rangeRegex = /^\s*\d+(\.\d+)?\s*-\s*\d+(\.\d+)?\s*$/;
  return rangeRegex.test(str);
};

const extractRange = (rangeStr) => {
  if (!isNumericRange(rangeStr)) {
    return ["-Infinity", "Infinity"];
  }

  const parts = rangeStr?.split("-") || [];
  const low = parts[0]?.trim() || "-Infinity";
  const high = parts[1]?.trim() || "Infinity";

  return [low, high];
};

export const mapTestCatBeanToFormData = (test) => {
  const blockSortOrderByKey = new Map();
  let nextBlockSortOrder = 1;
  const mappedDictionary = Array.isArray(test.dictionaryIds)
    ? test.dictionaryIds
        .map((rawId, index) => {
          if (!rawId) return null;
          const idToken = String(rawId).trim().split(" ")[0];
          if (!idToken) return null;
          const rawValue = Array.isArray(test.dictionaryValues)
            ? String(test.dictionaryValues[index] ?? "")
            : "";
          const isQualified =
            String(rawId).toLowerCase().includes("qualifiable") ||
            rawValue.toLowerCase().includes("qualifiable");
          return {
            id: idToken,
            qualified: isQualified ? "Y" : "N",
          };
        })
        .filter(Boolean)
    : [];

  const resultDisplayMetadata = parseResultFieldMetadata(
    test.resultDisplayConfigJson,
  );

  return {
    testId: test.id,
    testNameEnglish: test.localization?.english || "",
    testNameFrench: test.localization?.french || "",
    testReportNameEnglish: test.reportLocalization?.english || "",
    testReportNameFrench: test.reportLocalization?.french || "",
    testSection: test.testUnit || "",
    panels:
      Array.isArray(test.panelIds) && test.panelIds.length > 0
        ? []
        : typeof test.panel === "string" && test.panel !== "None"
          ? test.panel.split(",").map((p) => p.trim())
          : [],
    panelIds: Array.isArray(test.panelIds) ? test.panelIds : [],
    uom: test.uom || "",
    loinc: test.loinc || "",
    resultName: test.resultName || "",
    resultActive: resultDisplayMetadata.active !== false,
    resultEntryScope: resultDisplayMetadata.entryScope,
    resultBlockName: resultDisplayMetadata.blockName,
    resultBlockSortOrder: String(resultDisplayMetadata.blockSortOrder || 1),
    resultFieldSortOrder: String(resultDisplayMetadata.fieldSortOrder || 1),
    resultTubeSelectorEnabled: resultDisplayMetadata.tubeSelectorEnabled,
    resultTubeSelectorMin: resultDisplayMetadata.tubeSelectorMin,
    resultTubeSelectorMax: resultDisplayMetadata.tubeSelectorMax,
    resultTubeActivationCount: resultDisplayMetadata.tubeActivationCount,
    resultTubeQuantitySource: resultDisplayMetadata.tubeQuantitySource,
    resultTubeLabelEnabled: resultDisplayMetadata.tubeLabelEnabled,
    resultChildTubeUsageBlockEnabled:
      resultDisplayMetadata.childTubeUsageBlockEnabled,
    resultDisplayConfigJson: test.resultDisplayConfigJson || "",
    resultType: test.resultType || "",
    additionalFields: Array.isArray(test.additionalFields)
      ? test.additionalFields
          .filter((field) => field?.displayName && field?.fieldType)
          .map((field, index) => {
            const parsedMetadata = parseResultFieldMetadata(field.metadataJson);
            let rawMetadata = {};
            if (field.metadataJson && typeof field.metadataJson === "string") {
              try {
                rawMetadata = JSON.parse(field.metadataJson) || {};
              } catch (e) {
                rawMetadata = {};
              }
            }
            const blockKey = String(parsedMetadata.blockName || "").trim();
            const explicitBlockSortOrder = Number.parseInt(
              rawMetadata?.blockSortOrder,
              10,
            );
            if (!blockSortOrderByKey.has(blockKey)) {
              blockSortOrderByKey.set(
                blockKey,
                Number.isFinite(explicitBlockSortOrder) &&
                  explicitBlockSortOrder > 0
                  ? explicitBlockSortOrder
                  : nextBlockSortOrder,
              );
              nextBlockSortOrder += 1;
            }
            const fallbackFieldSortOrder =
              typeof field.sortOrder === "number" ? field.sortOrder : index + 1;
            return {
              id: field.id ?? undefined,
              fieldKey: field.fieldKey || "",
              displayName: field.displayName || "",
              fieldType: field.fieldType || "TEXT",
              required: !!field.required,
              active: field.active !== false && parsedMetadata.active !== false,
              sortOrder: fallbackFieldSortOrder,
              fieldSortOrder:
                parsedMetadata.fieldSortOrder || fallbackFieldSortOrder,
              blockSortOrder: blockSortOrderByKey.get(blockKey) || 1,
              defaultValue: field.defaultValue || "",
              maxLength:
                field.maxLength === null || field.maxLength === undefined
                  ? ""
                  : String(field.maxLength),
              metadataJson: field.metadataJson || "",
              ...parsedMetadata,
              options: Array.isArray(field.options)
                ? field.options
                    .filter((option) => option?.optionLabel)
                    .map((option, optionIndex) => ({
                      id: option.id ?? undefined,
                      optionKey: option.optionKey || "",
                      optionLabel: option.optionLabel || "",
                      sortOrder:
                        typeof option.sortOrder === "number"
                          ? option.sortOrder
                          : optionIndex + 1,
                      active: option.active !== false,
                    }))
                : [],
            };
          })
      : [],
    orderable: test.orderable === "Orderable" ? "Y" : "N",
    directSampleUsageEnabled: test.activeChildDependency
      ? "N"
      : test.directSampleUsageEnabled
        ? "Y"
        : "N",
    activeChildDependency: !!test.activeChildDependency,
    activeParentDependency: !!test.activeParentDependency,
    skipValidationWhenParentComplete: test.activeParentDependency
      ? test.skipValidationWhenParentComplete
        ? "Y"
        : "N"
      : "N",
    notifyResults: test.notifyResults ? "Y" : "N",
    inLabOnly: test.inLabOnly ? "Y" : "N",
    antimicrobialResistance: test.antimicrobialResistance ? "Y" : "N",
    active: test.active === "Active" ? "Y" : "N",
    dictionary: mappedDictionary,
    dictionaryReference:
      test.referenceId && test.referenceId !== "n/a"
        ? String(test.referenceId).split(" ")[0]
        : "",
    defaultTestResult: "",
    sampleTypes: test.sampleType ? [test.sampleType] : [],
    sampleTypeIds: test.sampleTypeId ? [String(test.sampleTypeId)] : [],
    lowValid: extractRange(test.resultLimits?.[0]?.validRange)[0],
    highValid: extractRange(test.resultLimits?.[0]?.validRange)[1],
    lowReportingRange: extractRange(test.resultLimits?.[0]?.reportingRange)[0],
    highReportingRange: extractRange(test.resultLimits?.[0]?.reportingRange)[1],
    lowCritical: extractRange(test.resultLimits?.[0]?.criticalRange)[0],
    highCritical: extractRange(test.resultLimits?.[0]?.criticalRange)[1],
    significantDigits: test.significantDigits
      ? test.significantDigits !== "n/a"
        ? test.significantDigits
        : "0"
      : "0",
    resultLimits:
      (test.resultLimits || []).length === 0
        ? [
            {
              ageRange: "0",
              highAgeRange: "Infinity",
              gender: false,
              lowNormal: "-Infinity",
              highNormal: "Infinity",
            },
          ]
        : Object.entries(
            (test.resultLimits || []).reduce((acc, limit) => {
              const key = limit.ageRange;
              if (!acc[key]) acc[key] = [];
              acc[key].push(limit);
              return acc;
            }, {}),
          ).map(([ageRange, limits]) => {
            const result = {
              ageRange,
              highAgeRange: "Infinity",
              gender: false,
              lowNormal: "-Infinity",
              highNormal: "Infinity",
              lowNormalFemale: "-Infinity",
              highNormalFemale: "Infinity",
            };

            limits.forEach((limit) => {
              let low = "-Infinity",
                high = "Infinity";

              if (isNumericRange(limit.normalRange)) {
                const parts = limit.normalRange.split("-");
                low = parts[0]?.trim() || "-Infinity";
                high = parts[1]?.trim() || "Infinity";
              }

              if (limit.gender === "M") {
                result.gender = true;
                result.lowNormal = low || "-Infinity";
                result.highNormal = high || "Infinity";
              } else if (limit.gender === "F") {
                result.gender = true;
                result.lowNormalFemale = low || "-Infinity";
                result.highNormalFemale = high || "Infinity";
              } else if (limit.gender === "n/a") {
                result.lowNormal = low || "-Infinity";
                result.highNormal = high || "Infinity";
              }
            });

            return result;
          }),
  };
};
