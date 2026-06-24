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
  postToOpenElisServerFullResponse,
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../utils/Utils";
import "./SampleResultsTable.css";

const ORDER_FIXED_FIELD_LABEL_MESSAGE_IDS = {
  priority: "sample.management.order.fixed.priority",
  requestDate: "sample.management.order.fixed.requestDate",
  requestTime: "sample.management.order.fixed.requestTime",
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
  cugCode: "sample.cug.label",
  quantity: "sample.quantity.label",
  uom: "sample.uom.label",
  collector: "collector.label",
  collectionDate: "sample.collection.date",
  collectionTime: "sample.collection.time",
};

const SAMPLE_MANAGEMENT_EDITABLE_FIXED_FIELD_KEYS = [
  "cugCode",
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
      const parts = value.split("/").map((part) => part.trim());
      if (parts.length >= 3) {
        const [first, second, year] = parts;
        const firstNum = Number(first);
        const secondNum = Number(second);
        const yearNum = Number(year);

        if (
          Number.isInteger(firstNum) &&
          Number.isInteger(secondNum) &&
          Number.isInteger(yearNum)
        ) {
          // Backend dates are typically dd/MM/yyyy in this locale, but we also
          // keep the legacy MM/dd/yyyy path working for older records.
          const day = firstNum > 12 || secondNum <= 12 ? firstNum : secondNum;
          const month = firstNum > 12 || secondNum <= 12 ? secondNum : firstNum;
          return `${String(yearNum).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
        }
      }
    }
    if (typeof value === "string") {
      const isoMatch = value
        .trim()
        .match(/^(\d{4})-(\d{2})-(\d{2})(?:[T\s].*)?$/);
      if (isoMatch) {
        const [, year, month, day] = isoMatch;
        return `${year}-${month}-${day}`;
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

      const isoMatch = normalized.match(
        /^\d{4}-\d{2}-\d{2}[t\s](\d{2}):(\d{2})(?::\d{2})?/,
      );
      if (isoMatch) {
        return `${isoMatch[1]}:${isoMatch[2]}`;
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

  const parseAdditionalFieldMetadata = (field) => {
    const rawMetadata = field?.metadataJson || field?.metadata;
    if (!rawMetadata) {
      return {};
    }
    if (typeof rawMetadata === "object") {
      return rawMetadata;
    }
    try {
      return JSON.parse(rawMetadata);
    } catch (e) {
      return {};
    }
  };

  const isLabelAdditionalField = (field) => {
    if (parseAdditionalFieldMetadata(field)?.tubeLabel?.enabled === true) {
      return true;
    }

    const normalizedFieldKey = normalizeAdditionalFieldKey(field?.fieldKey);
    const normalizedDisplayName = normalizeAdditionalFieldKey(
      field?.displayName,
    );
    const resolvedKey = resolveAdditionalFieldKey(field);

    return [normalizedFieldKey, normalizedDisplayName, resolvedKey].includes(
      "label",
    );
  };

  const buildTubeSequenceBySampleId = (items) => {
    const groupedItems = new Map();

    items.forEach((item) => {
      const familyKey = item?.parentExternalId || item?.externalId || item?.id;
      if (!familyKey) {
        return;
      }

      if (!groupedItems.has(familyKey)) {
        groupedItems.set(familyKey, []);
      }
      groupedItems.get(familyKey).push(item);
    });

    const sequenceBySampleId = {};

    groupedItems.forEach((groupedSampleItems) => {
      groupedSampleItems
        .slice()
        .sort((left, right) => {
          const leftIsParent =
            !left?.parentExternalId ||
            String(left.parentExternalId) === String(left.externalId);
          const rightIsParent =
            !right?.parentExternalId ||
            String(right.parentExternalId) === String(right.externalId);

          if (leftIsParent !== rightIsParent) {
            return leftIsParent ? -1 : 1;
          }

          return String(left?.externalId || left?.id || "").localeCompare(
            String(right?.externalId || right?.id || ""),
            undefined,
            { numeric: true, sensitivity: "base" },
          );
        })
        .forEach((item, index) => {
          if (item?.id) {
            sequenceBySampleId[item.id] = index + 1;
          }
        });
    });

    return sequenceBySampleId;
  };

  const getInitialLabelValue = useCallback(
    (sampleItem, tubeSequenceBySampleId) => {
      const cugCode = String(sampleItem?.cugCode || "").trim().replace(/\.+$/, "");
      if (!cugCode) {
        return "";
      }

      const hasResultBlocks = Array.isArray(sampleItem?.orderedTests)
        && sampleItem.orderedTests.length > 0;
      if (!hasResultBlocks) {
        return `${cugCode}.`;
      }

      const tubeNumber = tubeSequenceBySampleId?.[sampleItem?.id] || 1;
      return `${cugCode}.${tubeNumber}`;
    },
    [],
  );

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
        )}?activeOnly=true&requireEmail=false`,
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

  useEffect(() => {
    if (!Array.isArray(sampleItems) || sampleItems.length === 0) {
      return;
    }

    const tubeSequenceBySampleId = buildTubeSequenceBySampleId(sampleItems);

    setAdditionalFieldValuesBySampleId((prev) => {
      let nextState = prev;

      sampleItems.forEach((item) => {
        if (!item?.id) {
          return;
        }

        const configuredFields = Array.isArray(item.additionalFields)
          ? item.additionalFields
          : Array.isArray(additionalFieldsBySampleId[item.id])
            ? additionalFieldsBySampleId[item.id]
            : [];

        if (configuredFields.length === 0) {
          return;
        }

        const labelField = configuredFields.find(isLabelAdditionalField);
        if (!labelField) {
          return;
        }

        const fieldKey = resolveAdditionalFieldKey(labelField);
        if (!fieldKey) {
          return;
        }

        const existingStateValue = prev?.[item.id]?.[fieldKey];
        const existingBackendValue =
          item.additionalFieldValues?.[fieldKey] ??
          item.additionalFieldValues?.[
            normalizeAdditionalFieldKey(labelField?.displayName)
          ];

        if (
          String(existingStateValue || "").trim() ||
          String(existingBackendValue || "").trim()
        ) {
          return;
        }

        const initialLabelValue = getInitialLabelValue(
          item,
          tubeSequenceBySampleId,
        );
        if (!initialLabelValue) {
          return;
        }

        nextState = {
          ...nextState,
          [item.id]: {
            ...(nextState[item.id] || {}),
            [fieldKey]: initialLabelValue,
          },
        };
      });

      return nextState;
    });
  }, [additionalFieldsBySampleId, getInitialLabelValue, sampleItems]);

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
        cugCodeRaw: item.cugCode || "",
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
          d.cugCode !== undefined ||
          d.quantity !== undefined ||
          d.unitOfMeasureId !== undefined ||
          d.collector !== undefined ||
          d.collectionDate !== undefined ||
          d.collectionTime !== undefined ||
          d.removeSample !== undefined,
      ) ||
      {};

    const sampleLevel = {
      cugCode:
        rowLevelDetails.cugCode !== undefined
          ? rowLevelDetails.cugCode
          : originalRow?.cugCode || originalRow?.cugCodeRaw || "",
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

  const handleSaveSampleChanges = (sampleId, originalRow, additionalFields) => {
    const payload = buildSampleSavePayload(
      sampleId,
      originalRow,
      additionalFields,
    );
    setSavingBySampleId((prev) => ({ ...prev, [sampleId]: true }));
    const sampleUpdate = payload?.sampleUpdates?.[0];
    if (!sampleUpdate) {
      setSavingBySampleId((prev) => ({ ...prev, [sampleId]: false }));
      if (onPersistResult) {
        onPersistResult({
          success: false,
          message: "Missing sample data for save",
        });
      }
      return;
    }

    postToOpenElisServerJsonResponse(
      "/rest/sample-management/save-changes",
      JSON.stringify(payload),
      (response) => {
        setSavingBySampleId((prev) => ({ ...prev, [sampleId]: false }));
        if (onPersistResult) {
          onPersistResult({
            success:
              !response?.error &&
              !response?.statusCode &&
              response?.updatedSamplesCount !== undefined,
            message:
              response?.message ||
              intl.formatMessage({ id: "sample.management.success.title" }),
          });
        }
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

  const getPrimarySampleRowId = useCallback((rowId, originalRow) => {
    const firstTestId = originalRow?.orderedTests?.[0]?.analysisId;
    return firstTestId ? `${rowId}-${firstTestId}` : `${rowId}__sample`;
  }, []);

  const getEditableSampleCugValue = useCallback(
    (rowId, originalRow) => {
      const primarySampleRowId = getPrimarySampleRowId(rowId, originalRow);
      const sampleDetails =
        currentTestDetailsByKey[`${rowId}__sample`] ||
        currentTestDetailsByKey[primarySampleRowId] ||
        {};
      return sampleDetails.cugCode !== undefined
        ? sampleDetails.cugCode
        : originalRow?.cugCodeRaw || originalRow?.cugCode || "";
    },
    [currentTestDetailsByKey, getPrimarySampleRowId],
  );

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
      <div className="sample-mgmt-expanded-content">
        <div className="sample-mgmt-ordered-tests-header">
          <div className="sample-mgmt-section-title-row">
            <Chemistry size={20} />
            <FormattedMessage
              id="sample.management.table.orderedTests"
              values={{ count: originalRow.orderedTests.length }}
            />
          </div>
        </div>
        <div className="sample-mgmt-tests-grid">
          {originalRow.orderedTests.map((test) => (
            <div key={test.analysisId} className="sample-mgmt-test-card">
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
            const primarySampleRowId = getPrimarySampleRowId(
              row.id,
              originalRow,
            );
            const sampleDetails =
              currentTestDetailsByKey[`${row.id}__sample`] ||
              currentTestDetailsByKey[primarySampleRowId] ||
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
                  <Tile className="sample-mgmt-section-card">
                    <div className="sample-mgmt-section-header">
                      <h4 className="sample-mgmt-section-title">
                        {intl.formatMessage({
                          id: "sample.management.current.sample.fields.heading",
                        })}
                      </h4>
                      <Tag type="cool-gray">
                        {originalRow.sampleType || "-"}
                      </Tag>
                    </div>
                    <div className="sample-mgmt-grid">
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
                        const fieldType = (
                          field.fieldType || "TEXT"
                        ).toUpperCase();
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
                                text={intl.formatMessage({
                                  id: "label.select",
                                })}
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
                              <div
                                style={{
                                  marginBottom: "0.5rem",
                                  fontWeight: 500,
                                }}
                              >
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
                ) : null}
              </div>
            );
          })()}
        {shouldShowCurrentTests && orderReceptionFields.length > 0 && (
          <Tile className="sample-mgmt-section-card" style={{ marginTop: "1.5rem" }}>
            <h4 className="sample-mgmt-section-title">
              <FormattedMessage id="sample.management.order.fields.heading" />
            </h4>
            <div className="sample-mgmt-grid">
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
          <Tile className="sample-mgmt-section-card" style={{ marginTop: "1.5rem" }}>
            <h4 style={{ marginTop: 0, marginBottom: "0.75rem" }}>
              <FormattedMessage id="sample.additional.fields.heading" />
            </h4>
            <div style={{ marginBottom: "0.75rem", fontWeight: 500 }}>
              {(originalRow.cugCode || originalRow.externalId || "-") +
                " - " +
                (originalRow.sampleType || "")}
            </div>
            <div className="sample-mgmt-grid">
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
          <div className="sample-mgmt-save-bar">
            <Button
              kind="primary"
              size="md"
              onClick={() =>
                handleSaveSampleChanges(row.id, originalRow, additionalFields)
              }
              disabled={Boolean(savingBySampleId[row.id])}
            >
              {savingBySampleId[row.id]
                ? intl.formatMessage({ id: "sample.management.search.loading" })
                : intl.formatMessage({
                    id: "sample.management.action.save",
                    defaultMessage: "Save",
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
                                : cell.info.header === "cugCode"
                                  ? (() => {
                                      const editableCugValue =
                                        getEditableSampleCugValue(
                                          row.id,
                                          originalRow,
                                        );
                                      const primarySampleRowId =
                                        getPrimarySampleRowId(
                                          row.id,
                                          originalRow,
                                        );
                                      return (
                                        <div
                                          style={{
                                            minWidth: "120px",
                                          }}
                                          onClick={(e) => e.stopPropagation()}
                                        >
                                          <TextInput
                                            id={`${row.id}-table-cugCode`}
                                            labelText=""
                                            hideLabel
                                            size="sm"
                                            value={editableCugValue}
                                            onChange={(e) =>
                                              handleCurrentTestFieldChange(
                                                primarySampleRowId,
                                                "cugCode",
                                                e.target.value,
                                              )
                                            }
                                          />
                                        </div>
                                      );
                                    })()
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
