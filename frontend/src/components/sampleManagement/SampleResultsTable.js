import React, {
  useMemo,
  useState,
  useCallback,
  useEffect,
  useRef,
} from "react";
import {
  DataTable,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  TableSelectRow,
  TableSelectAll,
  TableExpandRow,
  TableExpandedRow,
  TableExpandHeader,
  Tag,
  Button,
  InlineLoading,
  TextInput,
  Checkbox,
  Select,
  SelectItem,
  TextArea,
  Tile,
} from "@carbon/react";
import { useIntl, FormattedMessage } from "react-intl";
import { Folder, Document, TrashCan, Chemistry } from "@carbon/icons-react";
import {
  postToOpenElisServer,
  postToOpenElisServerFullResponse,
  getFromOpenElisServer,
} from "../utils/Utils";

const ORDER_FIXED_FIELD_LABEL_MESSAGE_IDS = {
  priority: "sample.management.order.fixed.priority",
  requestDate: "sample.management.order.fixed.requestDate",
  receivedDateForDisplay:
    "sample.management.order.fixed.receivedDateForDisplay",
  receivedTime: "sample.management.order.fixed.receivedTime",
  nextVisitDate: "sample.management.order.fixed.nextVisitDate",
  referringSiteName: "sample.management.order.fixed.referringSiteName",
  referringSiteDepartmentId:
    "sample.management.order.fixed.referringSiteDepartmentId",
  provisionalClinicalDiagnosis:
    "sample.management.order.fixed.provisionalClinicalDiagnosis",
  providerFirstName: "sample.management.order.fixed.providerFirstName",
  providerLastName: "sample.management.order.fixed.providerLastName",
  providerCmp: "sample.management.order.fixed.providerCmp",
  providerRne: "sample.management.order.fixed.providerRne",
  providerDni: "sample.management.order.fixed.providerDni",
  providerSpecialty: "sample.management.order.fixed.providerSpecialty",
  providerWorkPhone: "sample.management.order.fixed.providerWorkPhone",
  providerFax: "sample.management.order.fixed.providerFax",
  providerEmail: "sample.management.order.fixed.providerEmail",
  paymentOptionSelection:
    "sample.management.order.fixed.paymentOptionSelection",
  testLocationCode: "sample.management.order.fixed.testLocationCode",
  otherLocationCode: "sample.management.order.fixed.otherLocationCode",
  rememberSiteAndRequester:
    "sample.management.order.fixed.rememberSiteAndRequester",
};

const SAMPLE_FIXED_FIELD_LABEL_MESSAGE_IDS = {
  quantity: "sample.quantity.label",
  uom: "sample.uom.label",
  collector: "collector.label",
  collectionDate: "sample.collection.date",
  collectionTime: "sample.collection.time",
};

const SAMPLE_MANAGEMENT_EDITABLE_FIXED_FIELD_KEYS = [
  "quantity",
  "uom",
  "collector",
  "collectionDate",
  "collectionTime",
];

/**
 * SampleResultsTable - Display search results for sample items in a data table.
 *
 * Features:
 * - Carbon DataTable with multi-select capability
 * - Expandable rows to show ordered tests
 * - Parent-child hierarchy indicators
 * - Quantity display with unit of measure
 * - Status visualization with tags
 * - Nesting level indicators
 * - Test cancellation/removal functionality
 * - React Intl for internationalization
 *
 * Props:
 * - sampleItems: Array<SampleItemDTO> - array of sample items to display
 * - onSelectionChange: (selectedIds) => void - callback when selection changes
 * - onTestRemoved: (sampleItemId, testId, testName) => void - callback when a test is removed
 *
 * Related: Feature 001-sample-management, User Story 1, Task T034
 */
function SampleResultsTable({
  sampleItems = [],
  onSelectionChange,
  onTestRemoved,
  currentTestsVisibleBySampleId = {},
  onPersistResult,
}) {
  const intl = useIntl();

  const normalizeProfileCode = (rawValue) => {
    const normalized = String(rawValue || "")
      .trim()
      .toUpperCase();
    if (!normalized) return "";
    if (normalized === "BIOLOGO" || normalized === "BIOLOGISTA") {
      return "BIOLOGIST";
    }
    if (
      normalized === "MEDICO" ||
      normalized === "MÉDICO" ||
      normalized === "DOCTOR"
    ) {
      return "MEDICAL_DOCTOR";
    }
    return normalized;
  };

  // Track which tests are being cancelled (loading state)
  const [cancellingTests, setCancellingTests] = useState({});
  const [currentTestDetailsByKey, setCurrentTestDetailsByKey] = useState({});
  const [uomList, setUomList] = useState([]);
  const [uomAssignmentsBySampleType, setUomAssignmentsBySampleType] = useState(
    {},
  );
  const [sampleFixedConfigs, setSampleFixedConfigs] = useState([]);
  const [collectorUsers, setCollectorUsers] = useState([]);
  const [additionalFieldValuesBySampleId, setAdditionalFieldValuesBySampleId] =
    useState({});
  const [additionalFieldsBySampleId, setAdditionalFieldsBySampleId] = useState(
    {},
  );
  const [testNamesBySampleTypeId, setTestNamesBySampleTypeId] = useState({});
  const [savingBySampleId, setSavingBySampleId] = useState({});
  const componentMounted = useRef(false);
  const lastSampleSignatureRef = useRef("");

  const getCollectorOptionsWithCurrentValue = useCallback(
    (currentValue) => {
      const options = [...collectorUsers];
      const normalizedCurrentValue = String(currentValue || "").trim();
      if (
        normalizedCurrentValue &&
        !options.some(
          (option) =>
            String(option.value || "").trim() === normalizedCurrentValue,
        )
      ) {
        options.push({
          id: `legacy-${normalizedCurrentValue}`,
          value: normalizedCurrentValue,
        });
      }
      return options;
    },
    [collectorUsers],
  );

  const toDateInputValue = (value) => {
    if (!value) return "";
    if (typeof value === "string" && value.includes("/")) {
      const [month, day, year] = value.split("/");
      if (month && day && year) {
        return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
      }
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  };

  const toTimeInputValue = (value) => {
    if (!value) return "";
    if (typeof value === "string") {
      const normalized = value
        .trim()
        .toLowerCase()
        .replace(/\./g, "")
        .replace(/\s+/g, " ");
      const match = normalized.match(
        /^(\d{1,2}):(\d{2})(?::\d{2})?\s*([ap]m)?$/,
      );
      if (match) {
        const hourRaw = Number(match[1]);
        const minuteRaw = Number(match[2]);
        if (!Number.isNaN(hourRaw) && !Number.isNaN(minuteRaw)) {
          let hour24 = hourRaw;
          if (match[3]) {
            hour24 = hourRaw % 12;
            if (match[3] === "pm") {
              hour24 += 12;
            }
          }
          if (
            hour24 >= 0 &&
            hour24 <= 23 &&
            minuteRaw >= 0 &&
            minuteRaw <= 59
          ) {
            return `${String(hour24).padStart(2, "0")}:${String(minuteRaw).padStart(2, "0")}`;
          }
        }
      }
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "";
    const hour = String(date.getHours()).padStart(2, "0");
    const minute = String(date.getMinutes()).padStart(2, "0");
    return `${hour}:${minute}`;
  };

  const normalizeAdditionalFieldKey = (value) =>
    String(value || "")
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9_-]/g, "_")
      .replace(/_+/g, "_")
      .replace(/^_+/, "")
      .slice(0, 80);

  const resolveAdditionalFieldKey = (field) => {
    const candidate = field?.fieldKey || field?.key || field?.displayName || "";
    return normalizeAdditionalFieldKey(candidate);
  };

  const resolveOrderFieldLabel = (field) => {
    if (field?.source === "fixed") {
      const messageId = ORDER_FIXED_FIELD_LABEL_MESSAGE_IDS[field?.fieldKey];
      if (messageId) {
        return intl.formatMessage({ id: messageId });
      }
    }
    return field?.displayName || field?.fieldKey || "";
  };

  const getSampleFixedFieldConfig = useCallback(
    (fieldKey) =>
      sampleFixedConfigs.find(
        (config) =>
          String(config?.fieldKey || "").toLowerCase() ===
          String(fieldKey || "").toLowerCase(),
      ) || null,
    [sampleFixedConfigs],
  );

  const editableSampleFieldDefinitions = useMemo(() => {
    return SAMPLE_MANAGEMENT_EDITABLE_FIXED_FIELD_KEYS.map(
      (fieldKey, index) => {
        const config = getSampleFixedFieldConfig(fieldKey);
        return {
          fieldKey,
          readonly: Boolean(config?.readonly),
          sortOrder: config?.sortOrder ?? (index + 1) * 10,
          label: intl.formatMessage({
            id: SAMPLE_FIXED_FIELD_LABEL_MESSAGE_IDS[fieldKey],
          }),
        };
      },
    ).sort((left, right) => left.sortOrder - right.sortOrder);
  }, [getSampleFixedFieldConfig, intl]);

  const getAvailableUomsForSampleType = useCallback(
    (sampleTypeId) => {
      const normalizedSampleTypeId = String(sampleTypeId || "");
      const assignedIds =
        uomAssignmentsBySampleType[normalizedSampleTypeId] || [];
      if (!normalizedSampleTypeId || assignedIds.length === 0) {
        return uomList;
      }
      return uomList.filter((uom) => assignedIds.includes(String(uom.id)));
    },
    [uomAssignmentsBySampleType, uomList],
  );

  useEffect(() => {
    componentMounted.current = true;
    const fetchUoms = (res) => {
      if (!componentMounted.current) return;
      if (Array.isArray(res)) {
        setUomList(res);
        return;
      }
      setUomList(
        Array.isArray(res?.existingUomList) ? res.existingUomList : [],
      );
    };
    getFromOpenElisServer("/rest/displayList/UNIT_OF_MEASURE", fetchUoms);
    getFromOpenElisServer("/rest/sample-additional-fields/fixed", (res) => {
      if (!componentMounted.current) return;
      setSampleFixedConfigs(Array.isArray(res) ? res : []);
    });
    getFromOpenElisServer("/rest/sample-type-uoms/assignments", (res) => {
      if (!componentMounted.current) return;
      setUomAssignmentsBySampleType(res || {});
    });

    const fetchCollectorUsers = (profileCode) => {
      const normalizedCode = normalizeProfileCode(profileCode);
      if (!normalizedCode) {
        if (componentMounted.current) {
          setCollectorUsers([]);
        }
        return;
      }
      getFromOpenElisServer(
        `/rest/users/professional-profile/${encodeURIComponent(
          normalizedCode,
        )}?activeOnly=true`,
        (usersResponse) => {
          if (!componentMounted.current) return;
          const options = Array.isArray(usersResponse)
            ? usersResponse.map((item) => ({
                id: item.id,
                value: item.value,
              }))
            : [];
          setCollectorUsers(options);
        },
      );
    };

    getFromOpenElisServer("/rest/open-configuration-properties", (config) => {
      if (!componentMounted.current) return;
      const collectorProfileCode =
        config?.sampleCollectorProfessionalProfileCode || "BIOLOGIST";
      fetchCollectorUsers(normalizeProfileCode(collectorProfileCode));
    });

    return () => {
      componentMounted.current = false;
    };
  }, []);

  useEffect(() => {
    const signature = JSON.stringify(
      (sampleItems || []).map((item) => `${item.id}:${item.lastupdated || ""}`),
    );
    if (lastSampleSignatureRef.current !== signature) {
      lastSampleSignatureRef.current = signature;
      setCurrentTestDetailsByKey({});
      setAdditionalFieldValuesBySampleId({});
    }
  }, [sampleItems]);

  useEffect(() => {
    const sampleTypeIds = Array.from(
      new Set(
        sampleItems
          .map((item) => item.sampleTypeId)
          .filter(
            (sampleTypeId) => sampleTypeId && String(sampleTypeId) !== "",
          ),
      ),
    );

    if (sampleTypeIds.length === 0) {
      return;
    }

    sampleTypeIds.forEach((sampleTypeId) => {
      getFromOpenElisServer(
        `/rest/sample-type-tests?sampleType=${sampleTypeId}`,
        (response) => {
          if (!componentMounted.current) {
            return;
          }
          const fields = Array.isArray(response?.additionalFields)
            ? response.additionalFields
            : [];
          const tests = Array.isArray(response?.tests) ? response.tests : [];

          if (tests.length > 0) {
            const testNamesMap = {};
            tests.forEach((test) => {
              const testId = String(
                test?.id !== undefined && test?.id !== null
                  ? test.id
                  : test?.testId || "",
              ).trim();
              const testName = String(
                test?.name || test?.testName || "",
              ).trim();
              if (testId && testName) {
                testNamesMap[testId] = testName;
              }
            });
            if (Object.keys(testNamesMap).length > 0) {
              setTestNamesBySampleTypeId((prev) => ({
                ...prev,
                [String(sampleTypeId)]: testNamesMap,
              }));
            }
          }

          if (fields.length === 0) {
            return;
          }

          const sampleIdsForType = sampleItems
            .filter(
              (item) => String(item.sampleTypeId) === String(sampleTypeId),
            )
            .map((item) => item.id);

          setAdditionalFieldsBySampleId((prev) => {
            const next = { ...prev };
            sampleIdsForType.forEach((sampleId) => {
              next[sampleId] = fields;
            });
            return next;
          });
        },
      );
    });
  }, [sampleItems]);

  useEffect(() => {
    sampleItems.forEach((item) => {
      if (!item?.id || !item?.sampleTypeId) {
        return;
      }
      getFromOpenElisServer(
        `/rest/sample-type-additional-fields/values?sampleTypeId=${item.sampleTypeId}&sampleItemId=${item.id}`,
        (response) => {
          if (!componentMounted.current) {
            return;
          }
          if (!response || typeof response !== "object") {
            return;
          }
          setAdditionalFieldValuesBySampleId((prev) => ({
            ...prev,
            [item.id]: {
              ...(prev[item.id] || {}),
              ...response,
            },
          }));
        },
      );
    });
  }, [sampleItems]);

  const handleCurrentTestFieldChange = useCallback((key, field, value) => {
    setCurrentTestDetailsByKey((prev) => ({
      ...prev,
      [key]: {
        ...(prev[key] || {}),
        [field]: value,
      },
    }));
  }, []);

  /**
   * Table headers configuration.
   */
  const headers = useMemo(
    () => [
      {
        key: "externalId",
        header: intl.formatMessage({
          id: "sample.management.table.header.externalId",
        }),
      },
      {
        key: "cugCode",
        header: intl.formatMessage({
          id: "sample.management.table.header.cug",
          defaultMessage: "CUG",
        }),
      },
      {
        key: "sampleType",
        header: intl.formatMessage({
          id: "sample.management.table.header.sampleType",
        }),
      },
      {
        key: "quantity",
        header: intl.formatMessage({
          id: "sample.management.table.header.quantity",
        }),
      },
      {
        key: "remainingQuantity",
        header: intl.formatMessage({
          id: "sample.management.table.header.remainingQuantity",
        }),
      },
      {
        key: "status",
        header: intl.formatMessage({
          id: "sample.management.table.header.status",
        }),
      },
      {
        key: "tests",
        header: intl.formatMessage({
          id: "sample.management.table.header.tests",
        }),
      },
      {
        key: "hierarchy",
        header: intl.formatMessage({
          id: "sample.management.table.header.hierarchy",
        }),
      },
    ],
    [intl],
  );

  /**
   * Transform sample items to table rows.
   * Uses effectiveRemainingQuantity from backend (already calculated with fallback logic).
   */
  const rows = useMemo(() => {
    return sampleItems.map((item) => {
      // Backend sends effectiveRemainingQuantity which handles null remainingQuantity
      const displayRemaining = item.effectiveRemainingQuantity;
      const testCount = item.orderedTests ? item.orderedTests.length : 0;

      let additionalFields = Array.isArray(item.additionalFields)
        ? [...item.additionalFields]
        : [];
      let additionalFieldValues = item.additionalFieldValues || {};

      if (additionalFields.length === 0) {
        (item.orderedTests || []).forEach((test) => {
          if (
            additionalFields.length === 0 &&
            Array.isArray(test.additionalFields) &&
            test.additionalFields.length > 0
          ) {
            additionalFields = [...test.additionalFields].sort(
              (a, b) => (a?.sortOrder || 0) - (b?.sortOrder || 0),
            );
          }
          if (
            test.additionalFieldValues &&
            Object.keys(test.additionalFieldValues).length > 0
          ) {
            additionalFieldValues = {
              ...additionalFieldValues,
              ...test.additionalFieldValues,
            };
          }
        });
      }

      return {
        id: item.id,
        externalId: item.externalId || "-",
        sampleAccessionNumber: item.sampleAccessionNumber || "",
        cugCode: item.cugCode || "-",
        sampleType: item.sampleType || "-",
        sampleTypeId: item.sampleTypeId ? String(item.sampleTypeId) : "",
        quantityRaw: item.quantityDisplay ?? item.quantity ?? "",
        unitOfMeasureIdRaw: item.unitOfMeasureId
          ? String(item.unitOfMeasureId)
          : "",
        collectorRaw: item.collector || "",
        collectionDateRaw: toDateInputValue(item.collectionDate),
        collectionTimeRaw: toTimeInputValue(
          item.collectionTime || item.collectionDate,
        ),
        quantity: item.quantity
          ? `${item.quantity} ${item.unitOfMeasure || ""}`
          : "-",
        remainingQuantity: displayRemaining
          ? `${displayRemaining} ${item.unitOfMeasure || ""}`
          : "-",
        statusId: item.statusId,
        isAliquot: item.isAliquot,
        nestingLevel: item.nestingLevel || 0,
        hasRemainingQuantity: item.hasRemainingQuantity,
        childAliquotCount: item.childAliquots ? item.childAliquots.length : 0,
        parentExternalId: item.parentExternalId,
        orderedTests: item.orderedTests || [],
        additionalFields,
        additionalFieldValues,
        orderReceptionFields: Array.isArray(item.orderReceptionFields)
          ? item.orderReceptionFields
          : [],
        testCount: testCount,
        tests: testCount > 0 ? `${testCount}` : "-",
      };
    });
  }, [sampleItems]);

  const resolveTestName = useCallback(
    (test, sampleTypeId) => {
      if (!test) {
        return "-";
      }

      const rawName = String(test.testName || test.name || "").trim();
      const isNumericOnlyName = /^\d+$/.test(rawName);
      if (rawName && !isNumericOnlyName) {
        return rawName;
      }

      const testId = String(
        test.testId !== undefined && test.testId !== null
          ? test.testId
          : test.id || "",
      ).trim();
      const typeId = String(sampleTypeId || "").trim();
      if (typeId && testId) {
        const mappedName = testNamesBySampleTypeId?.[typeId]?.[testId];
        if (mappedName) {
          return mappedName;
        }
      }

      if (rawName) {
        return rawName;
      }
      return testId || "-";
    },
    [testNamesBySampleTypeId],
  );

  const updateAdditionalFieldValue = (sampleId, fieldKey, value) => {
    setAdditionalFieldValuesBySampleId((prev) => ({
      ...prev,
      [sampleId]: {
        ...(prev[sampleId] || {}),
        [fieldKey]: value,
      },
    }));
  };

  const updateAdditionalMultiSelectOption = (
    sampleId,
    fieldKey,
    optionKey,
    checked,
  ) => {
    const currentValues = additionalFieldValuesBySampleId[sampleId] || {};
    const selectedValues = new Set(
      String(currentValues?.[fieldKey] || "")
        .split(",")
        .map((entry) => entry.trim())
        .filter((entry) => entry !== ""),
    );
    if (checked) {
      selectedValues.add(optionKey);
    } else {
      selectedValues.delete(optionKey);
    }
    updateAdditionalFieldValue(
      sampleId,
      fieldKey,
      Array.from(selectedValues).join(","),
    );
  };

  const buildSampleSavePayload = (sampleId, originalRow, additionalFields) => {
    const rawMergedValues = {
      ...(originalRow?.additionalFieldValues || {}),
      ...(additionalFieldValuesBySampleId[sampleId] || {}),
    };
    const additionalValues = { ...rawMergedValues };
    (additionalFields || []).forEach((field) => {
      const fieldKey = resolveAdditionalFieldKey(field);
      if (!fieldKey) return;
      if (Object.prototype.hasOwnProperty.call(rawMergedValues, fieldKey)) {
        additionalValues[fieldKey] = rawMergedValues[fieldKey];
        return;
      }
      const normalizedFieldKey = normalizeAdditionalFieldKey(field?.fieldKey);
      if (
        normalizedFieldKey &&
        Object.prototype.hasOwnProperty.call(
          rawMergedValues,
          normalizedFieldKey,
        )
      ) {
        additionalValues[fieldKey] = rawMergedValues[normalizedFieldKey];
        return;
      }
      const normalizedDisplayName = normalizeAdditionalFieldKey(
        field?.displayName,
      );
      if (
        normalizedDisplayName &&
        Object.prototype.hasOwnProperty.call(
          rawMergedValues,
          normalizedDisplayName,
        )
      ) {
        additionalValues[fieldKey] = rawMergedValues[normalizedDisplayName];
      }
    });

    const currentTests = (originalRow?.orderedTests || []).map((test) => {
      const key = `${sampleId}-${test.analysisId}`;
      const details = currentTestDetailsByKey[key] || {};
      return {
        analysisId: String(test.analysisId),
        canceled: Boolean(
          typeof details.cancelTest === "boolean"
            ? details.cancelTest
            : test.canceled,
        ),
      };
    });

    const rowLevelDetailsFromCurrentRow =
      currentTestDetailsByKey[`${sampleId}__sample`] ||
      currentTestDetailsByKey[`${sampleId}__current`] ||
      null;

    const rowDetailsCandidates = (originalRow?.orderedTests || [])
      .map((test) => currentTestDetailsByKey[`${sampleId}-${test.analysisId}`])
      .filter(Boolean);
    const rowLevelDetails =
      rowLevelDetailsFromCurrentRow ||
      rowDetailsCandidates.find(
        (d) =>
          d.quantity !== undefined ||
          d.unitOfMeasureId !== undefined ||
          d.collector !== undefined ||
          d.collectionDate !== undefined ||
          d.collectionTime !== undefined ||
          d.removeSample !== undefined,
      ) ||
      {};

    const sampleLevel = {
      quantity:
        rowLevelDetails.quantity !== undefined
          ? rowLevelDetails.quantity
          : originalRow?.quantityRaw || "",
      unitOfMeasureId:
        rowLevelDetails.unitOfMeasureId !== undefined
          ? rowLevelDetails.unitOfMeasureId
          : originalRow?.unitOfMeasureIdRaw || "",
      collector:
        rowLevelDetails.collector !== undefined
          ? rowLevelDetails.collector
          : originalRow?.collectorRaw || "",
      collectionDate:
        rowLevelDetails.collectionDate !== undefined
          ? rowLevelDetails.collectionDate
          : originalRow?.collectionDateRaw || "",
      collectionTime:
        rowLevelDetails.collectionTime !== undefined
          ? rowLevelDetails.collectionTime
          : originalRow?.collectionTimeRaw || "",
      removeSample: Boolean(rowLevelDetails.removeSample),
    };

    if (!additionalFields || additionalFields.length === 0) {
      return {
        sampleUpdates: [
          {
            sampleItemId: sampleId,
            ...sampleLevel,
            currentTests,
            additionalFieldValues: {},
          },
        ],
      };
    }

    return {
      sampleUpdates: [
        {
          sampleItemId: sampleId,
          ...sampleLevel,
          currentTests,
          additionalFieldValues: additionalValues,
        },
      ],
    };
  };

  const toDisplayDate = (dateValue) => {
    if (!dateValue) return "";
    if (dateValue.includes("/")) return dateValue;
    const [year, month, day] = dateValue.split("-");
    if (!year || !month || !day) return dateValue;
    return `${month}/${day}/${year}`;
  };

  const toDisplayTime = (timeValue) => {
    if (!timeValue) return "";
    if (
      timeValue.toUpperCase().includes("AM") ||
      timeValue.toUpperCase().includes("PM")
    ) {
      return timeValue;
    }
    const [hourText, minuteText] = String(timeValue).split(":");
    const hour = Number(hourText);
    if (Number.isNaN(hour) || minuteText === undefined) return timeValue;
    const minute = minuteText.slice(0, 2);
    const suffix = hour >= 12 ? "PM" : "AM";
    const displayHour = hour % 12 === 0 ? 12 : hour % 12;
    return `${displayHour}:${minute} ${suffix}`;
  };

  const applySampleUpdateToSampleEditForm = (form, sampleUpdate) => {
    if (!form || !Array.isArray(form.existingTests)) {
      return false;
    }

    const sampleTests = form.existingTests.filter(
      (test) => String(test.sampleItemId) === String(sampleUpdate.sampleItemId),
    );
    if (sampleTests.length === 0) {
      return false;
    }

    const currentTestByAnalysisId = new Map(
      (sampleUpdate.currentTests || []).map((testUpdate) => [
        String(testUpdate.analysisId),
        Boolean(testUpdate.canceled),
      ]),
    );

    const collectionDateDisplay = toDisplayDate(sampleUpdate.collectionDate);
    const collectionTimeDisplay = toDisplayTime(sampleUpdate.collectionTime);

    sampleTests.forEach((test) => {
      test.sampleItemChanged = true;
      test.quantity = sampleUpdate.quantity ?? "";
      test.unitOfMeasureId = sampleUpdate.unitOfMeasureId ?? "";
      test.collector = sampleUpdate.collector ?? "";
      test.collectionDate = collectionDateDisplay;
      test.collectionTime = collectionTimeDisplay;
      test.additionalFieldValues = sampleUpdate.additionalFieldValues || {};
      test.canceled =
        Boolean(sampleUpdate.removeSample) ||
        Boolean(currentTestByAnalysisId.get(String(test.analysisId)));
    });

    return true;
  };

  const prepareSampleEditFormForSubmit = (form) => {
    if (!form?.sampleOrderItems) return form;
    form.sampleOrderItems.modified = true;
    form.sampleOrderItems.priorityList = [];
    form.sampleOrderItems.programList = [];
    form.sampleOrderItems.referringSiteList = [];
    form.sampleOrderItems.providersList = [];
    form.sampleOrderItems.paymentOptions = [];
    form.sampleOrderItems.testLocationCodeList = [];
    form.initialSampleConditionList = [];
    form.testSectionList = [];
    return form;
  };

  const handleSaveSampleChanges = (sampleId, originalRow, additionalFields) => {
    const payload = buildSampleSavePayload(
      sampleId,
      originalRow,
      additionalFields,
    );
    setSavingBySampleId((prev) => ({ ...prev, [sampleId]: true }));
    const sampleUpdate = payload?.sampleUpdates?.[0];
    const accessionNumber = originalRow?.sampleAccessionNumber || "";
    if (!sampleUpdate || !accessionNumber) {
      setSavingBySampleId((prev) => ({ ...prev, [sampleId]: false }));
      if (onPersistResult) {
        onPersistResult({
          success: false,
          message: "Missing accession number for save",
        });
      }
      return;
    }

    getFromOpenElisServer(
      `/rest/SampleEdit?accessionNumber=${encodeURIComponent(accessionNumber)}`,
      (sampleEditForm) => {
        if (!sampleEditForm || !Array.isArray(sampleEditForm.existingTests)) {
          setSavingBySampleId((prev) => ({ ...prev, [sampleId]: false }));
          if (onPersistResult) {
            onPersistResult({
              success: false,
              message: "Unable to load SampleEdit form for save",
            });
          }
          return;
        }

        const updated = applySampleUpdateToSampleEditForm(
          sampleEditForm,
          sampleUpdate,
        );
        if (!updated) {
          setSavingBySampleId((prev) => ({ ...prev, [sampleId]: false }));
          if (onPersistResult) {
            onPersistResult({
              success: false,
              message: "Selected sample was not found in SampleEdit flow",
            });
          }
          return;
        }

        const formToSubmit = prepareSampleEditFormForSubmit(sampleEditForm);
        postToOpenElisServer(
          "/rest/SampleEdit",
          JSON.stringify(formToSubmit),
          (status) => {
            setSavingBySampleId((prev) => ({ ...prev, [sampleId]: false }));
            if (onPersistResult) {
              onPersistResult({
                success: status === 200,
                message:
                  status === 200
                    ? intl.formatMessage({
                        id: "sample.management.success.title",
                      })
                    : "Failed to save sample changes",
              });
            }
          },
        );
      },
    );
  };

  /**
   * Handle test cancellation/removal.
   */
  const handleCancelTest = useCallback(
    (sampleItemId, analysisId, testName) => {
      // Set loading state for this specific test
      setCancellingTests((prev) => ({ ...prev, [analysisId]: true }));

      const payload = JSON.stringify({
        analysisId: analysisId,
        sampleItemId: sampleItemId,
      });

      postToOpenElisServerFullResponse(
        "/rest/sample-management/cancel-test",
        payload,
        (response) => {
          setCancellingTests((prev) => ({ ...prev, [analysisId]: false }));

          if (response.ok) {
            // Notify parent to refresh data
            if (onTestRemoved) {
              onTestRemoved(sampleItemId, analysisId, testName);
            }
          } else {
            // Handle error - could show notification
            console.error("Failed to cancel test");
          }
        },
      );
    },
    [onTestRemoved],
  );

  /**
   * Render status tag based on statusId and remaining quantity.
   * Finds the original row data to access all properties.
   */
  const renderStatusTag = (dataTableRow) => {
    // Find the original row data by ID
    const originalRow = rows.find((r) => r.id === dataTableRow.id);
    if (!originalRow) return null;

    // Only show a tag if there's no remaining quantity
    if (!originalRow.hasRemainingQuantity) {
      return (
        <Tag type="red">
          {intl.formatMessage({
            id: "sample.management.status.allVolumeDispensed",
          })}
        </Tag>
      );
    }

    // If there's remaining quantity, don't show a status tag
    return null;
  };

  /**
   * Render tests count with icon.
   */
  const renderTestsCount = (dataTableRow) => {
    const originalRow = rows.find((r) => r.id === dataTableRow.id);
    if (!originalRow) return null;

    if (originalRow.testCount > 0) {
      return (
        <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
          <Chemistry size={16} />
          <span>{originalRow.testCount}</span>
        </div>
      );
    }
    return <span style={{ color: "#6f6f6f" }}>-</span>;
  };

  /**
   * Render hierarchy indicator showing parent-child relationships.
   * Finds the original row data to access all properties.
   */
  const renderHierarchyIndicator = (dataTableRow) => {
    // Find the original row data by ID
    const originalRow = rows.find((r) => r.id === dataTableRow.id);
    if (!originalRow) return null;

    const nestingIndent = originalRow.nestingLevel * 16; // 16px per level

    return (
      <div style={{ display: "flex", alignItems: "center" }}>
        {originalRow.nestingLevel > 0 && (
          <span
            style={{ marginLeft: `${nestingIndent}px`, marginRight: "4px" }}
          >
            {"└─"}
          </span>
        )}
        {originalRow.childAliquotCount > 0 ? (
          <Folder size={16} style={{ marginRight: "4px" }} />
        ) : (
          <Document size={16} style={{ marginRight: "4px" }} />
        )}
        {originalRow.isAliquot && originalRow.parentExternalId && (
          <span
            style={{ fontSize: "0.75rem", color: "#6f6f6f", marginLeft: "4px" }}
          >
            {intl.formatMessage(
              { id: "sample.management.hierarchy.aliquotOf" },
              { parent: originalRow.parentExternalId },
            )}
          </span>
        )}
        {originalRow.childAliquotCount > 0 && (
          <span
            style={{ fontSize: "0.75rem", color: "#6f6f6f", marginLeft: "4px" }}
          >
            ({originalRow.childAliquotCount}{" "}
            {intl.formatMessage({
              id: "sample.management.hierarchy.aliquots",
            })}
            )
          </span>
        )}
      </div>
    );
  };

  /**
   * Render expanded row content with test details.
   */
  const renderExpandedContent = (row) => {
    const originalRow = rows.find((r) => r.id === row.id);
    const shouldShowCurrentTests = Boolean(
      currentTestsVisibleBySampleId[row.id],
    );
    const additionalFields = Array.isArray(additionalFieldsBySampleId[row.id])
      ? additionalFieldsBySampleId[row.id]
      : Array.isArray(originalRow?.additionalFields)
        ? originalRow.additionalFields
        : [];
    const effectiveAdditionalFieldValues = {
      ...(originalRow?.additionalFieldValues || {}),
      ...(additionalFieldValuesBySampleId[row.id] || {}),
    };
    const getAdditionalFieldDisplaySection = (field) =>
      String(field?.displaySection || "RECEPTION")
        .trim()
        .toUpperCase();

    const collectionAdditionalFields = additionalFields.filter(
      (field) => getAdditionalFieldDisplaySection(field) === "COLLECTION",
    );

    const receptionAdditionalFields = additionalFields.filter(
      (field) => getAdditionalFieldDisplaySection(field) !== "COLLECTION",
    );
    const orderReceptionFields = Array.isArray(
      originalRow?.orderReceptionFields,
    )
      ? originalRow.orderReceptionFields
      : [];
    if (!originalRow || originalRow.orderedTests.length === 0) {
      return (
        <div
          style={{
            padding: "1rem",
            color: "#6f6f6f",
            fontStyle: "italic",
          }}
        >
          <FormattedMessage id="sample.management.table.noTests" />
        </div>
      );
    }

    return (
      <div style={{ padding: "1rem" }}>
        <div
          style={{
            fontWeight: "600",
            marginBottom: "0.75rem",
            display: "flex",
            alignItems: "center",
            gap: "0.5rem",
          }}
        >
          <Chemistry size={20} />
          <FormattedMessage
            id="sample.management.table.orderedTests"
            values={{ count: originalRow.orderedTests.length }}
          />
        </div>
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))",
            gap: "0.5rem",
          }}
        >
          {originalRow.orderedTests.map((test) => (
            <div
              key={test.analysisId}
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                padding: "0.5rem 0.75rem",
                backgroundColor: "#f4f4f4",
                borderRadius: "4px",
                border: "1px solid #e0e0e0",
              }}
            >
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: "500" }}>
                  {resolveTestName(test, originalRow.sampleTypeId)}
                </div>
                <div
                  style={{
                    fontSize: "0.75rem",
                    color: "#6f6f6f",
                    display: "flex",
                    gap: "0.75rem",
                    marginTop: "0.25rem",
                  }}
                >
                  {shouldRenderStatusTag(test.status) && (
                    <Tag type={getTestStatusType(test.status)} size="sm">
                      {test.status}
                    </Tag>
                  )}
                  {test.orderedDate && (
                    <span>
                      <FormattedMessage id="sample.management.table.orderedDate" />
                      : {new Date(test.orderedDate).toLocaleDateString()}
                    </span>
                  )}
                </div>
              </div>
              <div style={{ marginLeft: "0.5rem" }}>
                {cancellingTests[test.analysisId] ? (
                  <InlineLoading
                    description={intl.formatMessage({
                      id: "sample.management.table.cancelling",
                    })}
                    status="active"
                  />
                ) : (
                  <Button
                    kind="ghost"
                    size="sm"
                    renderIcon={TrashCan}
                    iconDescription={intl.formatMessage({
                      id: "sample.management.table.cancelTest",
                    })}
                    hasIconOnly
                    onClick={() =>
                      handleCancelTest(
                        row.id,
                        test.analysisId,
                        resolveTestName(test, originalRow.sampleTypeId),
                      )
                    }
                    disabled={!canCancelTest(test.status)}
                    tooltipPosition="left"
                  />
                )}
              </div>
            </div>
          ))}
        </div>
        {shouldShowCurrentTests &&
          (() => {
            const orderedTests = originalRow.orderedTests || [];
            const firstTestId = orderedTests[0]?.analysisId;
            const primarySampleRowId = firstTestId
              ? `${row.id}-${firstTestId}`
              : `${row.id}__sample`;
            const sampleDetails =
              currentTestDetailsByKey[`${row.id}__sample`] ||
              (firstTestId
                ? currentTestDetailsByKey[`${row.id}-${firstTestId}`]
                : {}) ||
              {};
            const sampleQuantity =
              sampleDetails.quantity !== undefined
                ? sampleDetails.quantity
                : originalRow.quantityRaw || "";
            const sampleUom =
              sampleDetails.unitOfMeasureId !== undefined
                ? sampleDetails.unitOfMeasureId
                : originalRow.unitOfMeasureIdRaw || "";
            const sampleCollector =
              sampleDetails.collector !== undefined
                ? sampleDetails.collector
                : originalRow.collectorRaw || "";
            const sampleDate =
              sampleDetails.collectionDate !== undefined
                ? sampleDetails.collectionDate
                : originalRow.collectionDateRaw || "";
            const sampleTime =
              sampleDetails.collectionTime !== undefined
                ? sampleDetails.collectionTime
                : originalRow.collectionTimeRaw || "";
            const availableUoms = getAvailableUomsForSampleType(
              originalRow.sampleTypeId,
            );
            const availableUomsWithCurrent =
              sampleUom &&
              !availableUoms.some((uom) => String(uom.id) === String(sampleUom))
                ? [
                    ...availableUoms,
                    ...uomList.filter(
                      (uom) => String(uom.id) === String(sampleUom),
                    ),
                  ]
                : availableUoms;

            return (
              <div style={{ marginTop: "1rem", display: "grid", gap: "1rem" }}>
                {editableSampleFieldDefinitions.length > 0 ? (
                  <Tile style={{ padding: "1rem" }}>
                    <div
                      style={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        marginBottom: "1rem",
                        gap: "0.75rem",
                        flexWrap: "wrap",
                      }}
                    >
                      <h4 style={{ margin: 0 }}>
                        {intl.formatMessage({
                          id: "sample.management.current.sample.fields.heading",
                        })}
                      </h4>
                      <Tag type="cool-gray">
                        {originalRow.sampleType || "-"}
                      </Tag>
                    </div>
                    <div
                      style={{
                        display: "grid",
                        gridTemplateColumns:
                          "repeat(auto-fit, minmax(220px, 1fr))",
                        gap: "1rem",
                      }}
                    >
                      {editableSampleFieldDefinitions.map((field) => {
                        const disabled = field.readonly;
                        switch (field.fieldKey) {
                          case "quantity":
                            return (
                              <TextInput
                                key={`${row.id}-sample-${field.fieldKey}`}
                                id={`${row.id}-sample-${field.fieldKey}`}
                                labelText={field.label}
                                value={sampleQuantity}
                                disabled={disabled}
                                onChange={(e) =>
                                  handleCurrentTestFieldChange(
                                    primarySampleRowId,
                                    "quantity",
                                    e.target.value,
                                  )
                                }
                              />
                            );
                          case "uom":
                            return (
                              <Select
                                key={`${row.id}-sample-${field.fieldKey}`}
                                id={`${row.id}-sample-${field.fieldKey}`}
                                labelText={field.label}
                                value={sampleUom}
                                disabled={disabled}
                                onChange={(e) =>
                                  handleCurrentTestFieldChange(
                                    primarySampleRowId,
                                    "unitOfMeasureId",
                                    e.target.value,
                                  )
                                }
                              >
                                <SelectItem
                                  value=""
                                  text={intl.formatMessage({
                                    id: "label.select",
                                  })}
                                />
                                {availableUomsWithCurrent.map((uom) => (
                                  <SelectItem
                                    key={uom.id}
                                    value={String(uom.id)}
                                    text={uom.value}
                                  />
                                ))}
                              </Select>
                            );
                          case "collector":
                            return (
                              <Select
                                key={`${row.id}-sample-${field.fieldKey}`}
                                id={`${row.id}-sample-${field.fieldKey}`}
                                labelText={field.label}
                                value={sampleCollector || ""}
                                disabled={disabled}
                                onChange={(e) =>
                                  handleCurrentTestFieldChange(
                                    primarySampleRowId,
                                    "collector",
                                    e.target.value,
                                  )
                                }
                              >
                                <SelectItem
                                  value=""
                                  text={intl.formatMessage({
                                    id: "collector.select.placeholder",
                                  })}
                                />
                                {getCollectorOptionsWithCurrentValue(
                                  sampleCollector,
                                ).map((collectorOption) => (
                                  <SelectItem
                                    key={collectorOption.id}
                                    value={collectorOption.value}
                                    text={collectorOption.value}
                                  />
                                ))}
                              </Select>
                            );
                          case "collectionDate":
                            return (
                              <TextInput
                                key={`${row.id}-sample-${field.fieldKey}`}
                                id={`${row.id}-sample-${field.fieldKey}`}
                                type="date"
                                labelText={field.label}
                                value={sampleDate}
                                disabled={disabled}
                                onChange={(e) =>
                                  handleCurrentTestFieldChange(
                                    primarySampleRowId,
                                    "collectionDate",
                                    e.target.value,
                                  )
                                }
                              />
                            );
                          case "collectionTime":
                            return (
                              <TextInput
                                key={`${row.id}-sample-${field.fieldKey}`}
                                id={`${row.id}-sample-${field.fieldKey}`}
                                type="time"
                                labelText={field.label}
                                value={sampleTime}
                                disabled={disabled}
                                onChange={(e) =>
                                  handleCurrentTestFieldChange(
                                    primarySampleRowId,
                                    "collectionTime",
                                    e.target.value,
                                  )
                                }
                              />
                            );
                          default:
                            return null;
                        }
                      })}
                      {collectionAdditionalFields.map((field, idx) => {
                        const fieldType = (field.fieldType || "TEXT").toUpperCase();
                        const fieldKey = resolveAdditionalFieldKey(field);
                        const fieldLabel = field.displayName || field.fieldKey;
                        const fieldValue =
                          effectiveAdditionalFieldValues?.[fieldKey] ??
                          effectiveAdditionalFieldValues?.[
                            normalizeAdditionalFieldKey(field?.displayName)
                          ] ??
                          "";
                        const fieldId = `sample_mgmt_collection_additional_${row.id}_${fieldKey}_${idx}`;
                        const options = field.options || [];

                        if (fieldType === "BOOLEAN") {
                          return (
                            <Checkbox
                              key={fieldId}
                              id={fieldId}
                              labelText={fieldLabel}
                              checked={fieldValue === "true"}
                              onChange={(e) =>
                                updateAdditionalFieldValue(
                                  row.id,
                                  fieldKey,
                                  e.target.checked ? "true" : "false",
                                )
                              }
                            />
                          );
                        }

                        if (
                          fieldType === "SELECT" ||
                          fieldType === "RADIO" ||
                          fieldType === "SYSTEM_USER_BIOLOGIST_SELECT"
                        ) {
                          return (
                            <Select
                              key={fieldId}
                              id={fieldId}
                              labelText={fieldLabel}
                              value={fieldValue}
                              onChange={(e) =>
                                updateAdditionalFieldValue(
                                  row.id,
                                  fieldKey,
                                  e.target.value,
                                )
                              }
                            >
                              <SelectItem
                                text={intl.formatMessage({ id: "label.select" })}
                                value=""
                              />
                              {options.map((option, optionIdx) => (
                                <SelectItem
                                  key={`${fieldId}_option_${optionIdx}`}
                                  text={option.optionLabel}
                                  value={option.optionKey}
                                />
                              ))}
                            </Select>
                          );
                        }

                        if (fieldType === "MULTISELECT") {
                          const selectedValues = new Set(
                            String(fieldValue)
                              .split(",")
                              .map((entry) => entry.trim())
                              .filter((entry) => entry !== ""),
                          );
                          return (
                            <div key={fieldId} style={{ gridColumn: "1 / -1" }}>
                              <div style={{ marginBottom: "0.5rem", fontWeight: 500 }}>
                                {fieldLabel}
                              </div>
                              {options.map((option, optionIdx) => (
                                <Checkbox
                                  key={`${fieldId}_multi_${optionIdx}`}
                                  id={`${fieldId}_multi_${optionIdx}`}
                                  labelText={option.optionLabel}
                                  checked={selectedValues.has(option.optionKey)}
                                  onChange={(e) =>
                                    updateAdditionalMultiSelectOption(
                                      row.id,
                                      fieldKey,
                                      option.optionKey,
                                      e.target.checked,
                                    )
                                  }
                                />
                              ))}
                            </div>
                          );
                        }

                        if (fieldType === "TEXTAREA") {
                          return (
                            <TextArea
                              key={fieldId}
                              id={fieldId}
                              labelText={fieldLabel}
                              style={{ gridColumn: "1 / -1" }}
                              value={fieldValue}
                              onChange={(e) =>
                                updateAdditionalFieldValue(
                                  row.id,
                                  fieldKey,
                                  e.target.value
                                )
                              }
                            />
                          );
                        }

                        const htmlInputType =
                          fieldType === "NUMBER"
                            ? "number"
                            : fieldType === "DATE"
                              ? "date"
                              : fieldType === "TIME"
                                ? "time"
                                : fieldType === "DATETIME"
                                  ? "datetime-local"
                                  : "text";

                        return (
                          <TextInput
                            key={fieldId}
                            id={fieldId}
                            labelText={fieldLabel}
                            type={htmlInputType}
                            size="lg"
                            style={{ minHeight: "52px" }}
                            value={fieldValue}
                            onChange={(e) =>
                              updateAdditionalFieldValue(
                                row.id,
                                fieldKey,
                                e.target.value,
                              )
                            }
                          />
                        );
                      })}
                    </div>
                  </Tile>
                ) : null}
              </div>
            );
          })()}
        {shouldShowCurrentTests && orderReceptionFields.length > 0 && (
          <Tile style={{ marginTop: "1.5rem", padding: "1rem" }}>
            <h4 style={{ marginTop: 0, marginBottom: "1rem" }}>
              <FormattedMessage id="sample.management.order.fields.heading" />
            </h4>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))",
                gap: "1rem",
              }}
            >
              {orderReceptionFields.map((field, index) => {
                const fieldId = `sample_mgmt_order_readonly_${row.id}_${field.fieldKey}_${index}`;
                const rawValue =
                  field?.value === undefined || field?.value === null
                    ? ""
                    : String(field.value);
                const displayValue =
                  field?.fieldType === "BOOLEAN"
                    ? rawValue.toLowerCase() === "true"
                      ? intl.formatMessage({ id: "yes.option" })
                      : rawValue.toLowerCase() === "false"
                        ? intl.formatMessage({ id: "no.option" })
                        : rawValue
                    : rawValue;
                const label = resolveOrderFieldLabel(field);

                if (displayValue.length > 120) {
                  return (
                    <TextArea
                      key={fieldId}
                      id={fieldId}
                      labelText={label}
                      style={{ gridColumn: "1 / -1" }}
                      value={displayValue}
                      readOnly
                    />
                  );
                }

                return (
                  <TextInput
                    key={fieldId}
                    id={fieldId}
                    labelText={label}
                    value={displayValue}
                    readOnly
                  />
                );
              })}
            </div>
          </Tile>
        )}
        {shouldShowCurrentTests && receptionAdditionalFields.length > 0 && (
          <Tile style={{ marginTop: "1.5rem", padding: "1rem" }}>
            <h4 style={{ marginTop: 0, marginBottom: "0.75rem" }}>
              <FormattedMessage id="sample.additional.fields.heading" />
            </h4>
            <div style={{ marginBottom: "0.75rem", fontWeight: 500 }}>
              {(originalRow.cugCode || originalRow.externalId || "-") +
                " - " +
                (originalRow.sampleType || "")}
            </div>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(240px, 1fr))",
                gap: "1rem",
              }}
            >
              {receptionAdditionalFields.map((field, idx) => {
                const fieldType = (field.fieldType || "TEXT").toUpperCase();
                const fieldKey = resolveAdditionalFieldKey(field);
                const fieldLabel = field.displayName || field.fieldKey;
                const fieldValue =
                  effectiveAdditionalFieldValues?.[fieldKey] ??
                  effectiveAdditionalFieldValues?.[
                    normalizeAdditionalFieldKey(field?.displayName)
                  ] ??
                  "";
                const fieldId = `sample_mgmt_additional_${row.id}_${fieldKey}_${idx}`;
                const options = field.options || [];

                if (fieldType === "BOOLEAN") {
                  return (
                    <Checkbox
                      key={fieldId}
                      id={fieldId}
                      labelText={fieldLabel}
                      checked={fieldValue === "true"}
                      onChange={(e) =>
                        updateAdditionalFieldValue(
                          row.id,
                          fieldKey,
                          e.target.checked ? "true" : "false",
                        )
                      }
                    />
                  );
                }

                if (
                  fieldType === "SELECT" ||
                  fieldType === "RADIO" ||
                  fieldType === "SYSTEM_USER_BIOLOGIST_SELECT"
                ) {
                  return (
                    <Select
                      key={fieldId}
                      id={fieldId}
                      labelText={fieldLabel}
                      value={fieldValue}
                      onChange={(e) =>
                        updateAdditionalFieldValue(
                          row.id,
                          fieldKey,
                          e.target.value,
                        )
                      }
                    >
                      <SelectItem
                        text={intl.formatMessage({ id: "label.select" })}
                        value=""
                      />
                      {options.map((option, optionIdx) => (
                        <SelectItem
                          key={`${fieldId}_option_${optionIdx}`}
                          text={option.optionLabel}
                          value={option.optionKey}
                        />
                      ))}
                    </Select>
                  );
                }

                if (fieldType === "MULTISELECT") {
                  const selectedValues = new Set(
                    String(fieldValue)
                      .split(",")
                      .map((entry) => entry.trim())
                      .filter((entry) => entry !== ""),
                  );
                  return (
                    <div key={fieldId} style={{ gridColumn: "1 / -1" }}>
                      <div style={{ marginBottom: "0.5rem", fontWeight: 500 }}>
                        {fieldLabel}
                      </div>
                      {options.map((option, optionIdx) => (
                        <Checkbox
                          key={`${fieldId}_multi_${optionIdx}`}
                          id={`${fieldId}_multi_${optionIdx}`}
                          labelText={option.optionLabel}
                          checked={selectedValues.has(option.optionKey)}
                          onChange={(e) =>
                            updateAdditionalMultiSelectOption(
                              row.id,
                              fieldKey,
                              option.optionKey,
                              e.target.checked,
                            )
                          }
                        />
                      ))}
                    </div>
                  );
                }

                if (fieldType === "TEXTAREA") {
                  return (
                    <TextArea
                      key={fieldId}
                      id={fieldId}
                      labelText={fieldLabel}
                      style={{ gridColumn: "1 / -1" }}
                      value={fieldValue}
                      onChange={(e) =>
                        updateAdditionalFieldValue(
                          row.id,
                          fieldKey,
                          e.target.value,
                        )
                      }
                    />
                  );
                }

                const htmlInputType =
                  fieldType === "NUMBER"
                    ? "number"
                    : fieldType === "DATE"
                      ? "date"
                      : fieldType === "TIME"
                        ? "time"
                        : fieldType === "DATETIME"
                          ? "datetime-local"
                          : "text";

                return (
                  <TextInput
                    key={fieldId}
                    id={fieldId}
                    labelText={fieldLabel}
                    type={htmlInputType}
                    size="lg"
                    style={{ minHeight: "52px" }}
                    value={fieldValue}
                    onChange={(e) =>
                      updateAdditionalFieldValue(
                        row.id,
                        fieldKey,
                        e.target.value,
                      )
                    }
                  />
                );
              })}
            </div>
          </Tile>
        )}
        {shouldShowCurrentTests && (
          <div
            style={{
              marginTop: "1rem",
              display: "flex",
              justifyContent: "flex-end",
            }}
          >
            <Button
              kind="primary"
              onClick={() =>
                handleSaveSampleChanges(row.id, originalRow, additionalFields)
              }
              disabled={Boolean(savingBySampleId[row.id])}
            >
              {savingBySampleId[row.id]
                ? intl.formatMessage({ id: "sample.management.search.loading" })
                : intl.formatMessage({
                    id: "sample.management.action.saveChanges",
                    defaultMessage: "Save Changes",
                  })}
            </Button>
          </div>
        )}
      </div>
    );
  };

  /**
   * Get tag type based on test status.
   */
  const getTestStatusType = (status) => {
    if (!status) return "gray";
    const statusLower = status.toLowerCase();
    if (
      statusLower.includes("complete") ||
      statusLower.includes("final") ||
      statusLower.includes("validated")
    ) {
      return "green";
    }
    if (
      statusLower.includes("cancel") ||
      statusLower.includes("rejected") ||
      statusLower.includes("void")
    ) {
      return "red";
    }
    if (statusLower.includes("pending") || statusLower.includes("waiting")) {
      return "blue";
    }
    if (statusLower.includes("in progress") || statusLower.includes("active")) {
      return "cyan";
    }
    return "gray";
  };

  const shouldRenderStatusTag = (status) => {
    if (!status) return false;
    return !/^\d+$/.test(String(status).trim());
  };

  /**
   * Check if a test can be cancelled based on its status.
   * Tests that are already completed or validated cannot be cancelled.
   */
  const canCancelTest = (status) => {
    if (!status) return true;
    const statusLower = status.toLowerCase();
    // Cannot cancel tests that are already completed, validated, or cancelled
    return !(
      statusLower.includes("complete") ||
      statusLower.includes("final") ||
      statusLower.includes("validated") ||
      statusLower.includes("cancel") ||
      statusLower.includes("rejected") ||
      statusLower.includes("void")
    );
  };

  if (sampleItems.length === 0) {
    return (
      <div
        style={{
          padding: "2rem",
          textAlign: "center",
          color: "#6f6f6f",
        }}
      >
        {intl.formatMessage({ id: "sample.management.table.noResults" })}
      </div>
    );
  }

  return (
    <DataTable
      rows={rows}
      headers={headers}
      isSortable
      render={({
        rows,
        headers,
        getHeaderProps,
        getRowProps,
        getSelectionProps,
        getTableProps,
        getExpandHeaderProps,
        selectedRows,
        selectRow,
      }) => {
        // Notify parent of selection changes
        const notifySelectionChange = (newSelectedRows) => {
          if (onSelectionChange) {
            onSelectionChange(newSelectedRows.map((r) => r.id));
          }
        };

        return (
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                <TableExpandHeader
                  aria-label="expand row"
                  {...getExpandHeaderProps()}
                />
                <TableSelectAll
                  {...getSelectionProps()}
                  onSelect={() => {
                    // Toggle select all
                    if (selectedRows.length === rows.length) {
                      // Deselect all
                      rows.forEach((row) => {
                        if (selectedRows.some((r) => r.id === row.id)) {
                          selectRow(row.id);
                        }
                      });
                      notifySelectionChange([]);
                    } else {
                      // Select all
                      rows.forEach((row) => {
                        if (!selectedRows.some((r) => r.id === row.id)) {
                          selectRow(row.id);
                        }
                      });
                      notifySelectionChange(rows);
                    }
                  }}
                />
                {headers.map((header) => (
                  <TableHeader key={header.key} {...getHeaderProps({ header })}>
                    {header.header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => {
                // Find original row data for styling and expansion
                const originalRow = sampleItems.find(
                  (item) => item.id === row.id,
                );
                const isAliquotRow = originalRow?.isAliquot;
                const hasTests =
                  originalRow?.orderedTests &&
                  originalRow.orderedTests.length > 0;

                return (
                  <React.Fragment key={row.id}>
                    <TableExpandRow
                      {...getRowProps({ row })}
                      style={{
                        // Add subtle left border for aliquots
                        borderLeft: isAliquotRow ? "3px solid #0f62fe" : "none",
                        backgroundColor: isAliquotRow ? "#f0f7ff" : "inherit",
                      }}
                    >
                      <TableSelectRow
                        {...getSelectionProps({ row })}
                        onSelect={() => {
                          selectRow(row.id);
                          // Calculate new selection after toggle
                          const isCurrentlySelected = selectedRows.some(
                            (r) => r.id === row.id,
                          );
                          const newSelection = isCurrentlySelected
                            ? selectedRows.filter((r) => r.id !== row.id)
                            : [...selectedRows, row];
                          notifySelectionChange(newSelection);
                        }}
                      />
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>
                          {cell.info.header === "status"
                            ? renderStatusTag(row)
                            : cell.info.header === "hierarchy"
                              ? renderHierarchyIndicator(row)
                              : cell.info.header === "tests"
                                ? renderTestsCount(row)
                                : cell.value}
                        </TableCell>
                      ))}
                    </TableExpandRow>
                    <TableExpandedRow
                      colSpan={headers.length + 2}
                      className="sample-expanded-row"
                      style={{
                        backgroundColor: hasTests ? "#fafafa" : "#fff",
                      }}
                    >
                      {renderExpandedContent(row)}
                    </TableExpandedRow>
                  </React.Fragment>
                );
              })}
            </TableBody>
          </Table>
        );
      }}
    />
  );
}

export default SampleResultsTable;
