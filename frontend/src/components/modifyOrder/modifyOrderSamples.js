const createSampleXmlDefaults = () => ({
  collectionDate: "",
  collector: "",
  quantity: "",
  uom: "",
  rejected: false,
  rejectionReason: "",
  collectionTime: "",
  cug: "",
  cugAutoReserved: "",
  cugValidationMessage: "",
  cugReservationToken: "",
  cugReservationContextId: "",
  additionalFieldValues: {},
});

export const buildEmptyModifySample = (index = 0) => ({
  index,
  existingSampleItemId: null,
  accessionNumber: "",
  canRemoveSample: true,
  lockedTestIds: [],
  sampleRejected: false,
  rejectionReason: "",
  requestReferralEnabled: false,
  referralItems: [],
  sampleTypeId: "",
  sampleXML: createSampleXmlDefaults(),
  panels: [],
  tests: [],
  additionalFields: [],
});

const normalizeValue = (value) => {
  if (value === undefined || value === null) {
    return "";
  }
  return String(value);
};

const cloneAdditionalFieldValues = (values) => ({
  ...(values || {}),
});

const createGroup = (sampleItemId) => ({
  ...buildEmptyModifySample(),
  existingSampleItemId: sampleItemId,
  canRemoveSample: false,
  tests: [],
  additionalFields: [],
});

const getOrCreateGroup = (groups, sampleItemId) => {
  const normalizedSampleItemId = normalizeValue(sampleItemId).trim();
  if (!normalizedSampleItemId) {
    return null;
  }

  if (!groups.has(normalizedSampleItemId)) {
    groups.set(normalizedSampleItemId, createGroup(normalizedSampleItemId));
  }

  return groups.get(normalizedSampleItemId);
};

const appendUniqueTest = (tests, test) => {
  if (!test?.id) {
    return tests;
  }

  if (tests.some((current) => String(current.id) === String(test.id))) {
    return tests;
  }

  return [...tests, test];
};

export const buildSamplesFromOrder = (orderFormValues) => {
  const existingTests = Array.isArray(orderFormValues?.existingTests)
    ? orderFormValues.existingTests
    : [];
  const possibleTests = Array.isArray(orderFormValues?.possibleTests)
    ? orderFormValues.possibleTests
    : [];

  const groups = new Map();
  const sampleTypes = Array.isArray(orderFormValues?.sampleTypes)
    ? orderFormValues.sampleTypes
    : [];

  const resolveSampleTypeId = (row) => {
    if (row?.sampleTypeId) {
      return normalizeValue(row.sampleTypeId);
    }

    const sampleTypeLabel = normalizeValue(row?.sampleType).trim().toLowerCase();
    if (!sampleTypeLabel) {
      return "";
    }

    const matchingSampleType = sampleTypes.find((sampleType) => {
      const options = [
        sampleType?.value,
        sampleType?.name,
        sampleType?.label,
        sampleType?.description,
      ]
        .map((value) => normalizeValue(value).trim().toLowerCase())
        .filter(Boolean);
      return options.includes(sampleTypeLabel);
    });

    return normalizeValue(matchingSampleType?.id);
  };

  const mergeRowIntoGroup = (row, options = {}) => {
    const group = getOrCreateGroup(groups, row?.sampleItemId);
    if (!group) {
      return;
    }

    if (!group.accessionNumber && row?.accessionNumber) {
      group.accessionNumber = row.accessionNumber;
    }
    if (!group.sampleTypeId) {
      group.sampleTypeId = resolveSampleTypeId(row);
    }
    if (!group.sampleXML.cug && row?.cugCode) {
      group.sampleXML.cug = normalizeValue(row.cugCode);
      group.sampleXML.cugAutoReserved = normalizeValue(row.cugCode);
    }
    if (!group.sampleXML.collectionDate && row?.collectionDate) {
      group.sampleXML.collectionDate = normalizeValue(row.collectionDate);
    }
    if (!group.sampleXML.collectionTime && row?.collectionTime) {
      group.sampleXML.collectionTime = normalizeValue(row.collectionTime);
    }
    if (!group.sampleXML.quantity && row?.quantity) {
      group.sampleXML.quantity = normalizeValue(row.quantity);
    }
    if (!group.sampleXML.uom && row?.unitOfMeasureId) {
      group.sampleXML.uom = normalizeValue(row.unitOfMeasureId);
    }
    if (!group.sampleXML.collector && row?.collector) {
      group.sampleXML.collector = normalizeValue(row.collector);
    }
    if (
      Array.isArray(row?.additionalFields) &&
      row.additionalFields.length > 0 &&
      group.additionalFields.length === 0
    ) {
      group.additionalFields = [...row.additionalFields];
    }
    if (
      row?.additionalFieldValues &&
      Object.keys(row.additionalFieldValues).length > 0
    ) {
      group.sampleXML.additionalFieldValues = {
        ...group.sampleXML.additionalFieldValues,
        ...cloneAdditionalFieldValues(row.additionalFieldValues),
      };
    }
    if (row?.canRemoveSample) {
      group.canRemoveSample = true;
    }
    if (row?.canCancel === false && row?.testId) {
      group.lockedTestIds = Array.from(
        new Set([...group.lockedTestIds, normalizeValue(row.testId)]),
      );
    }

    if (options.includeAsSelectedTest && row?.testId && row?.testName) {
      group.tests = appendUniqueTest(group.tests, {
        id: normalizeValue(row.testId),
        name: normalizeValue(row.testName),
      });
    }
  };

  existingTests.forEach((row) => {
    mergeRowIntoGroup(row, {
      includeAsSelectedTest: true,
    });
  });

  possibleTests.forEach((row) => {
    mergeRowIntoGroup(row, {
      includeAsSelectedTest: false,
    });
  });

  const builtSamples = Array.from(groups.values()).map((sample, index) => ({
    ...sample,
    index,
  }));

  if (builtSamples.length === 0) {
    return [buildEmptyModifySample(0)];
  }

  return builtSamples;
};

const escapeXmlAttribute = (value) => {
  if (value === undefined || value === null) {
    return "";
  }
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/'/g, "&apos;")
    .replace(/"/g, "&quot;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
};

const escapeXmlText = (value) => {
  if (value === undefined || value === null) {
    return "";
  }
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
};

const buildSampleXml = (samples) => {
  const newSamples = (samples || []).filter(
    (sample) =>
      !sample?.existingSampleItemId &&
      Array.isArray(sample?.tests) &&
      sample.tests.length > 0,
  );

  if (newSamples.length === 0) {
    return "";
  }

  let sampleXmlString = '<?xml version="1.0" encoding="utf-8"?>';
  sampleXmlString += "<samples>";

  newSamples.forEach((sampleItem) => {
    const tests = (sampleItem.tests || []).map((test) => test.id).join(",");
    const panels = (sampleItem.panels || [])
      .map((panel) => panel.id)
      .join(",");

    const storageLocation = sampleItem.sampleXML?.storageLocation;
    const storageLocationId =
      storageLocation?.locationId ||
      storageLocation?.box?.id ||
      storageLocation?.id ||
      "";
    const storageLocationType =
      storageLocation?.locationType ||
      (storageLocation?.box?.id ? "box" : storageLocation?.type || "");
    const storagePositionCoordinate =
      storageLocation?.positionCoordinate ||
      storageLocation?.position?.coordinate ||
      "";

    const gpsLatitude = sampleItem.sampleXML?.gpsLatitude || "";
    const gpsLongitude = sampleItem.sampleXML?.gpsLongitude || "";
    const gpsAccuracy = sampleItem.sampleXML?.gpsAccuracy || "";
    const gpsCaptureMethod = sampleItem.sampleXML?.gpsCaptureMethod || "";
    const cugCode = sampleItem.sampleXML?.cug || "";
    const cugReservationToken =
      sampleItem.sampleXML?.cugReservationToken || "";
    const cugReservationContextId =
      sampleItem.sampleXML?.cugReservationContextId || "";

    const additionalFieldValues =
      sampleItem.sampleXML?.additionalFieldValues || {};
    const additionalFieldEntries = Object.entries(additionalFieldValues)
      .filter(([key, value]) => {
        return (
          key !== undefined &&
          key !== null &&
          key !== "" &&
          value !== undefined &&
          value !== null &&
          String(value).trim() !== ""
        );
      })
      .map(([key, value]) => {
        return `<field key='${escapeXmlAttribute(key)}'>${escapeXmlText(
          value,
        )}</field>`;
      })
      .join("");

    sampleXmlString += `<sample sampleID='${escapeXmlAttribute(sampleItem.sampleTypeId)}' date='${escapeXmlAttribute(sampleItem.sampleXML?.collectionDate)}' time='${escapeXmlAttribute(sampleItem.sampleXML?.collectionTime)}' collector='${escapeXmlAttribute(sampleItem.sampleXML?.collector)}' quantity='${escapeXmlAttribute(sampleItem.sampleXML?.quantity)}' uom='${escapeXmlAttribute(sampleItem.sampleXML?.uom)}' tests='${escapeXmlAttribute(tests)}' testSectionMap='' testSampleTypeMap='' panels='${escapeXmlAttribute(panels)}' rejected='${escapeXmlAttribute(sampleItem.sampleXML?.rejected)}' rejectReasonId='${escapeXmlAttribute(sampleItem.sampleXML?.rejectionReason)}' cug='${escapeXmlAttribute(cugCode)}' cugReservationToken='${escapeXmlAttribute(cugReservationToken)}' cugReservationContextId='${escapeXmlAttribute(cugReservationContextId)}' initialConditionIds='' storageLocationId='${escapeXmlAttribute(storageLocationId)}' storageLocationType='${escapeXmlAttribute(storageLocationType)}' storagePositionCoordinate='${escapeXmlAttribute(storagePositionCoordinate)}' gpsLatitude='${escapeXmlAttribute(gpsLatitude)}' gpsLongitude='${escapeXmlAttribute(gpsLongitude)}' gpsAccuracy='${escapeXmlAttribute(gpsAccuracy)}' gpsCaptureMethod='${escapeXmlAttribute(gpsCaptureMethod)}'>`;
    if (additionalFieldEntries !== "") {
      sampleXmlString += `<additionalFields>${additionalFieldEntries}</additionalFields>`;
    }
    sampleXmlString += "</sample>";
  });

  sampleXmlString += "</samples>";
  return sampleXmlString;
};

const buildSelectedTestsBySampleItemId = (samples) => {
  const result = new Map();

  (samples || []).forEach((sample) => {
    const sampleItemId = normalizeValue(sample?.existingSampleItemId).trim();
    if (!sampleItemId) {
      return;
    }

    const lockedTestIds = Array.isArray(sample?.lockedTestIds)
      ? sample.lockedTestIds.map((value) => normalizeValue(value))
      : [];
    const selectedTestIds = Array.isArray(sample?.tests)
      ? sample.tests.map((test) => normalizeValue(test?.id))
      : [];

    result.set(sampleItemId, new Set([...lockedTestIds, ...selectedTestIds]));
  });

  return result;
};

const buildRemovedSampleItemIds = (samples, removedExistingSampleItemIds) => {
  const removedIds = new Set(
    (removedExistingSampleItemIds || [])
      .map((sampleItemId) => normalizeValue(sampleItemId).trim())
      .filter(Boolean),
  );

  (samples || []).forEach((sample) => {
    const sampleItemId = normalizeValue(sample?.existingSampleItemId).trim();
    if (!sampleItemId) {
      return;
    }
    const selectedTestIds = Array.isArray(sample?.tests)
      ? sample.tests.map((test) => normalizeValue(test?.id)).filter(Boolean)
      : [];
    const lockedTestIds = Array.isArray(sample?.lockedTestIds)
      ? sample.lockedTestIds.map((value) => normalizeValue(value)).filter(Boolean)
      : [];
    const effectiveSelectedTests = new Set([...selectedTestIds, ...lockedTestIds]);
    if (effectiveSelectedTests.size === 0 && sample?.canRemoveSample) {
      removedIds.add(sampleItemId);
    }
  });

  return removedIds;
};

const buildExistingTestsBySampleItemId = (existingTests) => {
  const grouped = new Map();

  (existingTests || []).forEach((row, index) => {
    const sampleItemId = normalizeValue(row?.sampleItemId).trim();
    if (!sampleItemId) {
      return;
    }
    if (!grouped.has(sampleItemId)) {
      grouped.set(sampleItemId, []);
    }
    grouped.get(sampleItemId).push({ row, index });
  });

  return grouped;
};

export const buildModifyOrderPayload = (
  orderFormValues,
  samples,
  removedExistingSampleItemIds = [],
) => {
  const payload = JSON.parse(JSON.stringify(orderFormValues || {}));
  const existingTests = Array.isArray(payload.existingTests)
    ? payload.existingTests
    : [];
  const possibleTests = Array.isArray(payload.possibleTests)
    ? payload.possibleTests
    : [];

  const selectedTestsBySampleItemId = buildSelectedTestsBySampleItemId(samples);
  const removedSampleItemIds = buildRemovedSampleItemIds(
    samples,
    removedExistingSampleItemIds,
  );
  const existingTestsBySampleItemId = buildExistingTestsBySampleItemId(
    existingTests,
  );
  const sampleCardsBySampleItemId = new Map(
    (samples || [])
      .filter((sample) => sample?.existingSampleItemId)
      .map((sample) => [normalizeValue(sample.existingSampleItemId).trim(), sample]),
  );

  payload.existingTests = existingTests.map((test) => {
    const sampleItemId = normalizeValue(test.sampleItemId).trim();
    const sampleCard = sampleCardsBySampleItemId.get(sampleItemId);
    const selectedTests = selectedTestsBySampleItemId.get(sampleItemId);
    const updatedTest = {
      ...test,
      canceled: false,
      removeSample: false,
      sampleItemChanged: false,
    };

    if (removedSampleItemIds.has(sampleItemId)) {
      return updatedTest;
    }

    if (!sampleCard) {
      return updatedTest;
    }

    updatedTest.collectionDate =
      normalizeValue(sampleCard.sampleXML?.collectionDate) || "";
    updatedTest.collectionTime =
      normalizeValue(sampleCard.sampleXML?.collectionTime) || "";
    updatedTest.quantity = normalizeValue(sampleCard.sampleXML?.quantity) || "";
    updatedTest.unitOfMeasureId =
      normalizeValue(sampleCard.sampleXML?.uom) || "";
    updatedTest.collector =
      normalizeValue(sampleCard.sampleXML?.collector) || "";
    updatedTest.additionalFieldValues = cloneAdditionalFieldValues(
      sampleCard.sampleXML?.additionalFieldValues,
    );
    updatedTest.sampleItemChanged = true;

    if (
      selectedTests &&
      !selectedTests.has(normalizeValue(test.testId)) &&
      test.canCancel
    ) {
      updatedTest.canceled = true;
    }

    return updatedTest;
  });

  removedSampleItemIds.forEach((sampleItemId) => {
    const groupedRows = existingTestsBySampleItemId.get(sampleItemId);
    if (!groupedRows || groupedRows.length === 0) {
      return;
    }

    const firstRowIndex = groupedRows[0].index;
    payload.existingTests[firstRowIndex] = {
      ...payload.existingTests[firstRowIndex],
      removeSample: true,
      sampleItemChanged: true,
    };
  });

  const currentExistingTestsBySampleItemId = new Map();
  payload.existingTests.forEach((test) => {
    const sampleItemId = normalizeValue(test?.sampleItemId).trim();
    const testId = normalizeValue(test?.testId).trim();
    if (!sampleItemId || !testId || test.canceled) {
      return;
    }
    if (!currentExistingTestsBySampleItemId.has(sampleItemId)) {
      currentExistingTestsBySampleItemId.set(sampleItemId, new Set());
    }
    currentExistingTestsBySampleItemId.get(sampleItemId).add(testId);
  });

  payload.possibleTests = possibleTests.map((test) => {
    const sampleItemId = normalizeValue(test.sampleItemId).trim();
    const testId = normalizeValue(test.testId).trim();
    const selectedTests = selectedTestsBySampleItemId.get(sampleItemId);
    const currentExistingTests =
      currentExistingTestsBySampleItemId.get(sampleItemId) || new Set();

    return {
      ...test,
      add:
        !removedSampleItemIds.has(sampleItemId) &&
        !!selectedTests &&
        selectedTests.has(testId) &&
        !currentExistingTests.has(testId),
    };
  });

  payload.sampleXML = buildSampleXml(samples);
  return payload;
};
