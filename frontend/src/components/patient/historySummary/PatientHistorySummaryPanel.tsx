import React, { useEffect, useMemo, useState } from "react";
import {
  Button,
  DataTableSkeleton,
  Tab,
  TabList,
  TabPanel,
  TabPanels,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tabs,
  TextInput,
} from "@carbon/react";
import { useIntl } from "react-intl";
import { getFromOpenElisServer } from "../../utils/Utils";
import config from "../../../config.json";
import "./patient-history-summary.scss";

type PatientHistoryMetrics = {
  totalOrders: number;
  totalSamples: number;
  storedSamples: number;
  totalTests: number;
  completedTests: number;
  pendingTests: number;
};

type DetailField = {
  key: string;
  label: string;
  value: string;
  fieldType: string;
  source: string;
};

type TestAdditionalFieldOption = {
  id?: number;
  optionKey: string;
  optionLabel: string;
  sortOrder?: number;
  active?: boolean;
};

type TestAdditionalFieldDefinition = {
  id?: number;
  testId?: string;
  fieldKey: string;
  displayName: string;
  fieldType: string;
  required?: boolean;
  active?: boolean;
  sortOrder?: number;
  defaultValue?: string;
  maxLength?: number;
  metadataJson?: string;
  blockName?: string;
  blockSortOrder?: number;
  entryScope?: string;
  fieldSortOrder?: number;
  includeInValidation?: boolean;
  options?: TestAdditionalFieldOption[];
};

type PatientHistoryOrder = {
  id: string;
  accessionNumber: string;
  clinicalOrderId: string;
  clientReference: string;
  priority: string;
  requestDate: string;
  receivedDate: string;
  status: string;
  referringSiteName: string;
  requesterName: string;
  orderFields: DetailField[];
};

type PatientHistorySample = {
  id: string;
  accessionNumber: string;
  clinicalOrderId: string;
  sampleItemExternalId: string;
  cugCode: string;
  sampleType: string;
  collectionDate: string;
  status: string;
  parentSampleItemExternalId: string;
  storageLocation: string;
  storageAssignedDate: string;
  storagePositionCoordinate: string;
  storageNotes: string;
  totalTests: number;
  completedTests: number;
  pendingTests: number;
  stored: boolean;
  collectionFields: DetailField[];
  receptionFields: DetailField[];
  fixedFields: DetailField[];
  orderFields: DetailField[];
  additionalFields: DetailField[];
};

type PatientHistoryResult = {
  id: string;
  accessionNumber: string;
  clinicalOrderId: string;
  cugCode: string;
  sampleType: string;
  collectionDate: string;
  sampleStatus: string;
  testName: string;
  testStatus: string;
  resultDate: string;
  resultName: string;
  resultValue: string;
  resultType: string;
  resultDisplayConfigJson: string;
  fixedFields: DetailField[];
  sampleOrderFields: DetailField[];
  sampleAdditionalFields: DetailField[];
  resultValues: DetailField[];
  additionalFieldDefinitions: TestAdditionalFieldDefinition[];
  additionalFieldValues: Record<string, string>;
};

type PatientHistorySummary = {
  patientId: string;
  metrics: PatientHistoryMetrics;
  orders: PatientHistoryOrder[];
  samples: PatientHistorySample[];
  results: PatientHistoryResult[];
};

type FilterState = {
  search: string;
  fromDate: string;
  toDate: string;
};

type Column<T> = {
  key: string;
  header: string;
  render: (row: T) => React.ReactNode;
};

const ORDER_FIXED_FIELD_LABEL_MESSAGE_IDS: Record<string, string> = {
  externalOrderNumber: "patientHistory.table.clinicalOrderId",
  labNo: "patientHistory.table.accessionNumber",
  priority: "sample.management.order.fixed.priority",
  requestDate: "sample.management.order.fixed.requestDate",
  requestTime: "sample.management.order.fixed.requestTime",
  receivedDateForDisplay: "sample.management.order.fixed.receivedDateForDisplay",
  receivedTime: "sample.management.order.fixed.receivedTime",
  nextVisitDate: "sample.management.order.fixed.nextVisitDate",
  requesterSampleID: "patientHistory.table.sampleIdentifier",
  referringPatientNumber: "patientHistory.table.clinicalOrderId",
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
  billingReferenceNumber: "patientHistory.table.clinicalOrderId",
  paymentOptionSelection: "sample.management.order.fixed.paymentOptionSelection",
  testLocationCode: "sample.management.order.fixed.testLocationCode",
  otherLocationCode: "sample.management.order.fixed.otherLocationCode",
  program: "label.program",
  rememberSiteAndRequester:
    "sample.management.order.fixed.rememberSiteAndRequester",
};

const SAMPLE_FIXED_FIELD_LABEL_MESSAGE_IDS: Record<string, string> = {
  cugCode: "sample.cug.label",
  quantity: "sample.quantity.label",
  uom: "sample.uom.label",
  collector: "collector.label",
  collectionDate: "sample.collection.date",
  collectionTime: "sample.collection.time",
};

const DETAIL_LABEL_MESSAGE_IDS: Record<string, string> = {
  accessionNumber: "patientHistory.table.accessionNumber",
  clinicalOrderId: "patientHistory.table.clinicalOrderId",
  sampleItemExternalId: "patientHistory.table.sampleIdentifier",
  cugCode: "patientHistory.table.cugCode",
  sampleType: "patientHistory.table.sampleType",
  collectionDate: "patientHistory.table.collectionDate",
  status: "patientHistory.table.status",
  parentSample: "patientHistory.table.parentSample",
  totalTests: "patientHistory.table.totalTests",
  completedTests: "patientHistory.table.completedTests",
  pendingTests: "patientHistory.table.pendingTests",
  location: "patientHistory.table.location",
  assignedDate: "patientHistory.table.assignedDate",
  position: "patientHistory.table.position",
  notes: "patientHistory.table.notes",
  testName: "patientHistory.table.testName",
  resultStatus: "patientHistory.table.resultStatus",
  sampleStatus: "patientHistory.table.sampleStatus",
  resultDate: "patientHistory.table.resultDate",
};

const emptySummary: PatientHistorySummary = {
  patientId: "",
  metrics: {
    totalOrders: 0,
    totalSamples: 0,
    storedSamples: 0,
    totalTests: 0,
    completedTests: 0,
    pendingTests: 0,
  },
  orders: [],
  samples: [],
  results: [],
};

const emptyFilters: FilterState = {
  search: "",
  fromDate: "",
  toDate: "",
};

const parseAdditionalFieldMetadata = (
  fieldDefinition:
    | TestAdditionalFieldDefinition
    | { metadataJson?: string }
    | null
    | undefined,
): Record<string, any> => {
  const metadataJson = fieldDefinition?.metadataJson;
  if (!metadataJson || typeof metadataJson !== "string") {
    return {};
  }

  try {
    const parsed = JSON.parse(metadataJson);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (error) {
    return {};
  }
};

const parseDocumentFieldValue = (rawValue: string | null | undefined) => {
  if (!rawValue || typeof rawValue !== "string") {
    return null;
  }

  try {
    const parsed = JSON.parse(rawValue);
    if (!parsed || typeof parsed !== "object") {
      return null;
    }

    const fileName = String(parsed.fileName || "").trim();
    const fileType = String(parsed.fileType || "").trim();
    return fileName ? { fileName, fileType } : null;
  } catch (error) {
    return null;
  }
};

const buildOrderDocumentPreviewHref = (
  sampleId: string | null | undefined,
  fieldKey: string | null | undefined,
) => {
  const normalizedSampleId = String(sampleId || "").trim();
  const normalizedFieldKey = String(fieldKey || "").trim();
  if (!normalizedSampleId || !normalizedFieldKey) {
    return "";
  }

  return `${config.serverBaseUrl}/rest/order-additional-fields/files/${normalizedSampleId}/${normalizedFieldKey}?download=false`;
};

const buildResultDocumentPreviewHref = (
  analysisId: string | null | undefined,
  fieldKey: string | null | undefined,
) => {
  const normalizedAnalysisId = String(analysisId || "").trim();
  const normalizedFieldKey = String(fieldKey || "").trim();
  if (!normalizedAnalysisId || !normalizedFieldKey) {
    return "";
  }

  return `${config.serverBaseUrl}/rest/test-additional-fields/files/${normalizedAnalysisId}/${normalizedFieldKey}?download=false`;
};

const normalizeResultEntryScope = (scopeValue: string | null | undefined) =>
  String(scopeValue || "").toUpperCase() === "PRELIMINARY"
    ? "PRELIMINARY"
    : "OFFICIAL";

const parsePositiveSortOrder = (
  value: number | string | null | undefined,
  fallbackValue: number,
) => {
  const parsedValue = Number.parseInt(String(value ?? ""), 10);
  return Number.isFinite(parsedValue) && parsedValue > 0
    ? parsedValue
    : fallbackValue;
};

const getFieldBlockAndScope = (
  fieldDefinition: TestAdditionalFieldDefinition,
): {
  blockName: string;
  entryScope: string;
  blockSortOrder: number;
  fieldSortOrder: number;
} => {
  const metadata = parseAdditionalFieldMetadata(fieldDefinition);
  const entryScope = normalizeResultEntryScope(
    fieldDefinition?.entryScope || metadata.entryScope,
  );
  const fallbackBlock =
    entryScope === "PRELIMINARY" ? "Preliminary" : "Official";
  const blockNameRaw =
    fieldDefinition?.blockName || metadata.resultBlock || metadata.blockName;
  const blockName =
    typeof blockNameRaw === "string" && blockNameRaw.trim().length > 0
      ? blockNameRaw.trim()
      : fallbackBlock;

  return {
    blockName,
    entryScope,
    blockSortOrder: parsePositiveSortOrder(
      fieldDefinition?.blockSortOrder,
      parsePositiveSortOrder(metadata.blockSortOrder, 0),
    ),
    fieldSortOrder: parsePositiveSortOrder(
      fieldDefinition?.fieldSortOrder ?? fieldDefinition?.sortOrder,
      parsePositiveSortOrder(metadata.fieldSortOrder, 1),
    ),
  };
};

const getPrimaryResultLayout = (
  data: PatientHistoryResult,
  intl: ReturnType<typeof useIntl>,
) => {
  const metadata = parseAdditionalFieldMetadata({
    metadataJson: data?.resultDisplayConfigJson,
  });
  const entryScope = normalizeResultEntryScope(
    metadata.entryScope as string | undefined,
  );
  const fallbackBlock =
    entryScope === "PRELIMINARY"
      ? intl.formatMessage({
          id: "results.block.preliminary",
          defaultMessage: "Preliminary",
        })
      : intl.formatMessage({
          id: "results.block.official",
          defaultMessage: "Official",
        });
  const blockName =
    typeof metadata.resultBlock === "string" && metadata.resultBlock.trim()
      ? metadata.resultBlock.trim()
      : fallbackBlock;

  return {
    blockName,
    entryScope,
    blockSortOrder: parsePositiveSortOrder(metadata.blockSortOrder, 1),
    fieldSortOrder: parsePositiveSortOrder(metadata.fieldSortOrder, 1),
  };
};

const isPrimaryResultActive = (data: PatientHistoryResult) =>
  parseAdditionalFieldMetadata({
    metadataJson: data?.resultDisplayConfigJson,
  })?.active !== false;

const isTubeSelectorFieldDefinition = (
  fieldDefinition: TestAdditionalFieldDefinition,
) => parseAdditionalFieldMetadata(fieldDefinition)?.tubeSelector?.enabled === true;

const getTubeActivationCount = (
  fieldDefinition: TestAdditionalFieldDefinition,
) => {
  const activationCount =
    parseAdditionalFieldMetadata(fieldDefinition)?.tubeBlock?.activationCount;
  const parsedValue = Number.parseInt(String(activationCount ?? ""), 10);
  return Number.isFinite(parsedValue) && parsedValue > 0 ? parsedValue : 0;
};

const getVisibleAdditionalFields = (
  data: PatientHistoryResult,
  fieldDefinitions: TestAdditionalFieldDefinition[],
) => {
  const definitions = Array.isArray(fieldDefinitions) ? fieldDefinitions : [];
  const primaryMetadata = parseAdditionalFieldMetadata({
    metadataJson: data?.resultDisplayConfigJson,
  });
  const selectorField = definitions.find(isTubeSelectorFieldDefinition);
  const selectorRawValue = primaryMetadata?.tubeSelector?.enabled
    ? data?.resultValue
    : selectorField?.fieldKey
      ? data?.additionalFieldValues?.[selectorField.fieldKey]
      : null;

  if (!primaryMetadata?.tubeSelector?.enabled && !selectorField?.fieldKey) {
    return definitions;
  }

  const selectedTubeCount = Number.parseInt(String(selectorRawValue ?? ""), 10);
  if (!Number.isFinite(selectedTubeCount) || selectedTubeCount <= 0) {
    return definitions.filter(
      (fieldDefinition) => getTubeActivationCount(fieldDefinition) <= 0,
    );
  }

  return definitions.filter((fieldDefinition) => {
    const activationCount = getTubeActivationCount(fieldDefinition);
    return activationCount <= 0 || activationCount <= selectedTubeCount;
  });
};

const isPrimaryResultVisible = (data: PatientHistoryResult) => {
  if (!isPrimaryResultActive(data)) {
    return false;
  }

  const metadata = parseAdditionalFieldMetadata({
    metadataJson: data?.resultDisplayConfigJson,
  });
  const activationCount = Number.parseInt(
    String(metadata?.tubeBlock?.activationCount ?? ""),
    10,
  );

  if (!Number.isFinite(activationCount) || activationCount <= 0) {
    return true;
  }

  let selectorRawValue: string | null | undefined = null;
  if (metadata?.tubeSelector?.enabled) {
    selectorRawValue = data?.resultValue;
  } else {
    const activeAdditionalFields = Array.isArray(data?.additionalFieldDefinitions)
      ? data.additionalFieldDefinitions.filter(
          (fieldDefinition) => fieldDefinition?.active !== false,
        )
      : [];
    const selectorField = activeAdditionalFields.find(
      isTubeSelectorFieldDefinition,
    );
    if (selectorField?.fieldKey) {
      selectorRawValue = data?.additionalFieldValues?.[selectorField.fieldKey];
    }
  }

  const selectedTubeCount = Number.parseInt(String(selectorRawValue ?? ""), 10);
  return (
    Number.isFinite(selectedTubeCount) && selectedTubeCount >= activationCount
  );
};

const resolveAdditionalFieldOptionLabel = (
  fieldDefinition: TestAdditionalFieldDefinition,
  rawValue: string,
) => {
  const options = Array.isArray(fieldDefinition?.options)
    ? fieldDefinition.options
    : [];
  return (
    options.find((option) => option?.optionKey === rawValue)?.optionLabel ||
    rawValue
  );
};

const formatConfiguredFieldValue = (
  intl: ReturnType<typeof useIntl>,
  fieldDefinition: TestAdditionalFieldDefinition,
  rawValue: string | null | undefined,
) => {
  const normalizedValue = String(rawValue || "").trim();
  if (!normalizedValue) {
    return "-";
  }

  const fieldType = String(fieldDefinition?.fieldType || "TEXT").toUpperCase();
  if (fieldType === "DOCUMENT") {
    return parseDocumentFieldValue(normalizedValue)?.fileName || "-";
  }
  if (fieldType === "BOOLEAN") {
    if (normalizedValue.toLowerCase() === "true") {
      return intl.formatMessage({ id: "yes.option" });
    }
    if (normalizedValue.toLowerCase() === "false") {
      return intl.formatMessage({ id: "no.option" });
    }
  }
  if (
    fieldType === "SELECT" ||
    fieldType === "RADIO" ||
    fieldType === "SYSTEM_USER_BIOLOGIST_SELECT"
  ) {
    return resolveAdditionalFieldOptionLabel(fieldDefinition, normalizedValue);
  }
  if (fieldType === "MULTISELECT") {
    return normalizedValue
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean)
      .map((value) => resolveAdditionalFieldOptionLabel(fieldDefinition, value))
      .join(", ");
  }

  return normalizedValue;
};

const PatientHistorySummaryPanel: React.FC<{ patientId: string }> = ({
  patientId,
}) => {
  const intl = useIntl();
  const [summary, setSummary] = useState<PatientHistorySummary>(emptySummary);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [filters, setFilters] = useState<FilterState>(emptyFilters);

  useEffect(() => {
    let mounted = true;
    const controller = new AbortController();
    setLoading(true);
    setLoadError(false);

    getFromOpenElisServer(
      `/rest/patient-history/${patientId}/summary`,
      (response) => {
        if (!mounted) {
          return;
        }

        if (!response) {
          setLoadError(true);
          setSummary(emptySummary);
        } else {
          setSummary(response);
        }
        setLoading(false);
      },
      controller.signal,
    );

    return () => {
      mounted = false;
      controller.abort();
    };
  }, [patientId]);

  const filteredOrders = useMemo(
    () =>
      summary.orders.filter((order) =>
        matchesFilters(
          filters,
          [
            order.accessionNumber,
            order.clinicalOrderId,
            order.clientReference,
            order.requesterName,
            order.referringSiteName,
            ...(order.orderFields || []).map((field) => field.value),
          ],
          [order.requestDate, order.receivedDate],
        ),
      ),
    [filters, summary.orders],
  );

  const filteredSamples = useMemo(
    () =>
      summary.samples.filter((sample) =>
        matchesFilters(
          filters,
          [
            sample.accessionNumber,
            sample.clinicalOrderId,
            sample.sampleItemExternalId,
            sample.cugCode,
            sample.sampleType,
            sample.status,
          ],
          [sample.collectionDate],
        ),
      ),
    [filters, summary.samples],
  );

  const filteredStoredSamples = useMemo(
    () =>
      summary.samples.filter(
        (sample) =>
          sample.stored &&
          matchesFilters(
            filters,
            [
              sample.accessionNumber,
              sample.clinicalOrderId,
              sample.sampleItemExternalId,
              sample.cugCode,
              sample.sampleType,
              sample.storageLocation,
              sample.storagePositionCoordinate,
              sample.storageNotes,
            ],
            [sample.storageAssignedDate || sample.collectionDate],
          ),
      ),
    [filters, summary.samples],
  );

  const filteredResults = useMemo(
    () =>
      summary.results.filter((result) =>
        matchesFilters(
          filters,
          [
            result.accessionNumber,
            result.clinicalOrderId,
            result.cugCode,
            result.sampleType,
            result.sampleStatus,
            result.testName,
            result.testStatus,
            result.resultValue,
            ...(result.resultValues || []).map((field) => field.value),
          ],
          [result.resultDate || result.collectionDate],
        ),
      ),
    [filters, summary.results],
  );

  const filteredMetrics = useMemo(
    () => ({
      totalOrders: filteredOrders.length,
      totalSamples: filteredSamples.length,
      storedSamples: filteredStoredSamples.length,
      totalTests: filteredResults.length,
      completedTests: filteredResults.filter((result) =>
        isCompletedStatus(result.testStatus),
      ).length,
      pendingTests: filteredResults.filter(
        (result) => !isCompletedStatus(result.testStatus),
      ).length,
    }),
    [filteredOrders, filteredResults, filteredSamples, filteredStoredSamples],
  );

  const metricCards = useMemo(
    () => [
      {
        key: "orders",
        label: intl.formatMessage({ id: "patientHistory.metrics.orders" }),
        value: filteredMetrics.totalOrders,
      },
      {
        key: "samples",
        label: intl.formatMessage({ id: "patientHistory.metrics.samples" }),
        value: filteredMetrics.totalSamples,
      },
      {
        key: "storedSamples",
        label: intl.formatMessage({
          id: "patientHistory.metrics.storedSamples",
        }),
        value: filteredMetrics.storedSamples,
      },
      {
        key: "totalTests",
        label: intl.formatMessage({ id: "patientHistory.metrics.totalTests" }),
        value: filteredMetrics.totalTests,
      },
      {
        key: "completedTests",
        label: intl.formatMessage({
          id: "patientHistory.metrics.completedTests",
        }),
        value: filteredMetrics.completedTests,
      },
      {
        key: "pendingTests",
        label: intl.formatMessage({ id: "patientHistory.metrics.pendingTests" }),
        value: filteredMetrics.pendingTests,
      },
    ],
    [filteredMetrics, intl],
  );

  const orderColumns = useMemo<Column<PatientHistoryOrder>[]>(
    () => [
      {
        key: "accessionNumber",
        header: intl.formatMessage({
          id: "patientHistory.table.accessionNumber",
        }),
        render: (row) => row.accessionNumber || "-",
      },
      {
        key: "clinicalOrderId",
        header: intl.formatMessage({
          id: "patientHistory.table.clinicalOrderId",
        }),
        render: (row) => row.clinicalOrderId || row.clientReference || "-",
      },
      {
        key: "requestDate",
        header: intl.formatMessage({ id: "patientHistory.table.requestDate" }),
        render: (row) => row.requestDate || "-",
      },
      {
        key: "priority",
        header: intl.formatMessage({ id: "patientHistory.table.priority" }),
        render: (row) => row.priority || "-",
      },
      {
        key: "receivedDate",
        header: intl.formatMessage({ id: "patientHistory.table.receivedDate" }),
        render: (row) => row.receivedDate || "-",
      },
      {
        key: "status",
        header: intl.formatMessage({ id: "patientHistory.table.status" }),
        render: (row) => row.status || "-",
      },
      {
        key: "requesterName",
        header: intl.formatMessage({ id: "patientHistory.table.requester" }),
        render: (row) => row.requesterName || "-",
      },
    ],
    [intl],
  );

  const sampleColumns = useMemo<Column<PatientHistorySample>[]>(
    () => [
      {
        key: "cugCode",
        header: intl.formatMessage({ id: "patientHistory.table.cugCode" }),
        render: (row) => row.cugCode || "-",
      },
      {
        key: "accessionNumber",
        header: intl.formatMessage({
          id: "patientHistory.table.accessionNumber",
        }),
        render: (row) => row.accessionNumber || "-",
      },
      {
        key: "sampleType",
        header: intl.formatMessage({ id: "patientHistory.table.sampleType" }),
        render: (row) => row.sampleType || "-",
      },
      {
        key: "collectionDate",
        header: intl.formatMessage({
          id: "patientHistory.table.collectionDate",
        }),
        render: (row) => row.collectionDate || "-",
      },
      {
        key: "status",
        header: intl.formatMessage({ id: "patientHistory.table.status" }),
        render: (row) => row.status || "-",
      },
      {
        key: "totalTests",
        header: intl.formatMessage({ id: "patientHistory.table.totalTests" }),
        render: (row) => String(row.totalTests ?? 0),
      },
    ],
    [intl],
  );

  const resultColumns = useMemo<Column<PatientHistoryResult>[]>(
    () => [
      {
        key: "accessionNumber",
        header: intl.formatMessage({
          id: "patientHistory.table.accessionNumber",
        }),
        render: (row) => row.accessionNumber || "-",
      },
      {
        key: "cugCode",
        header: intl.formatMessage({ id: "patientHistory.table.cugCode" }),
        render: (row) => row.cugCode || "-",
      },
      {
        key: "testName",
        header: intl.formatMessage({ id: "patientHistory.table.testName" }),
        render: (row) => row.testName || "-",
      },
      {
        key: "testStatus",
        header: intl.formatMessage({ id: "patientHistory.table.resultStatus" }),
        render: (row) => row.testStatus || "-",
      },
      {
        key: "resultDate",
        header: intl.formatMessage({ id: "patientHistory.table.resultDate" }),
        render: (row) => row.resultDate || row.collectionDate || "-",
      },
    ],
    [intl],
  );

  const storageColumns = useMemo<Column<PatientHistorySample>[]>(
    () => [
      {
        key: "cugCode",
        header: intl.formatMessage({ id: "patientHistory.table.cugCode" }),
        render: (row) => row.cugCode || "-",
      },
      {
        key: "accessionNumber",
        header: intl.formatMessage({
          id: "patientHistory.table.accessionNumber",
        }),
        render: (row) => row.accessionNumber || "-",
      },
      {
        key: "sampleType",
        header: intl.formatMessage({ id: "patientHistory.table.sampleType" }),
        render: (row) => row.sampleType || "-",
      },
      {
        key: "storageLocation",
        header: intl.formatMessage({ id: "patientHistory.table.location" }),
        render: (row) => row.storageLocation || "-",
      },
      {
        key: "storagePositionCoordinate",
        header: intl.formatMessage({ id: "patientHistory.table.position" }),
        render: (row) => row.storagePositionCoordinate || "-",
      },
      {
        key: "storageAssignedDate",
        header: intl.formatMessage({ id: "patientHistory.table.assignedDate" }),
        render: (row) => row.storageAssignedDate || "-",
      },
    ],
    [intl],
  );

  if (loading) {
    return <DataTableSkeleton columnCount={6} rowCount={6} />;
  }

  return (
    <div className="patient-history-summary">
      <div className="patient-history-summary__header">
        <div className="patient-history-summary__title">
          {intl.formatMessage({ id: "patientHistory.summary.title" })}
        </div>
        <div className="patient-history-summary__subtitle">
          {loadError
            ? intl.formatMessage({ id: "patientHistory.summary.unavailable" })
            : intl.formatMessage({ id: "patientHistory.summary.subtitle" })}
        </div>
      </div>

      <div className="patient-history-summary__filters">
        <TextInput
          id="patient-history-search"
          labelText={intl.formatMessage({ id: "patientHistory.filters.search" })}
          placeholder={intl.formatMessage({
            id: "patientHistory.filters.searchPlaceholder",
          })}
          value={filters.search}
          onChange={(event) =>
            setFilters((current) => ({
              ...current,
              search: event.target.value,
            }))
          }
        />
        <TextInput
          id="patient-history-from-date"
          type="date"
          labelText={intl.formatMessage({
            id: "patientHistory.filters.fromDate",
          })}
          value={filters.fromDate}
          onChange={(event) =>
            setFilters((current) => ({
              ...current,
              fromDate: event.target.value,
            }))
          }
        />
        <TextInput
          id="patient-history-to-date"
          type="date"
          labelText={intl.formatMessage({
            id: "patientHistory.filters.toDate",
          })}
          value={filters.toDate}
          onChange={(event) =>
            setFilters((current) => ({
              ...current,
              toDate: event.target.value,
            }))
          }
        />
        <Button
          kind="secondary"
          className="patient-history-summary__clear-button"
          onClick={() => setFilters(emptyFilters)}
        >
          {intl.formatMessage({ id: "label.clear" })}
        </Button>
      </div>

      <div className="patient-history-summary__metrics">
        {metricCards.map((metric) => (
          <div className="patient-history-summary__metric" key={metric.key}>
            <div className="patient-history-summary__metric-label">
              {metric.label}
            </div>
            <div className="patient-history-summary__metric-value">
              {metric.value}
            </div>
          </div>
        ))}
      </div>

      <Tabs>
        <TabList
          aria-label={intl.formatMessage({ id: "label.page.patientHistory" })}
        >
          <Tab>{intl.formatMessage({ id: "patientHistory.tabs.orders" })}</Tab>
          <Tab>{intl.formatMessage({ id: "patientHistory.tabs.samples" })}</Tab>
          <Tab>{intl.formatMessage({ id: "patientHistory.tabs.results" })}</Tab>
          <Tab>{intl.formatMessage({ id: "patientHistory.tabs.storage" })}</Tab>
        </TabList>
        <TabPanels>
          <TabPanel>
            <ExpandableHistoryTable
              title={intl.formatMessage({ id: "patientHistory.tabs.orders" })}
              columns={orderColumns}
              rows={filteredOrders}
              emptyMessage={intl.formatMessage({
                id: "patientHistory.empty.orders",
              })}
              rowId={(row) => row.id}
              renderDetails={(row) => (
                <OrderDetails
                  row={row}
                  resolveFieldLabel={(field) => resolveFieldLabel(intl, field)}
                  getFieldDisplayText={(field) =>
                    getFieldDisplayText(intl, field)
                  }
                  renderFieldValue={(field) =>
                    renderFieldValue(
                      intl,
                      field,
                      String(field?.fieldType || "").toUpperCase() ===
                        "DOCUMENT"
                        ? buildOrderDocumentPreviewHref(row.id, field.key)
                        : "",
                    )
                  }
                />
              )}
            />
          </TabPanel>
          <TabPanel>
            <ExpandableHistoryTable
              title={intl.formatMessage({ id: "patientHistory.tabs.samples" })}
              columns={sampleColumns}
              rows={filteredSamples}
              emptyMessage={intl.formatMessage({
                id: "patientHistory.empty.samples",
              })}
              rowId={(row) => row.id}
              renderDetails={(row) => (
                <SampleDetails
                  row={row}
                  resolveFieldLabel={(field) => resolveFieldLabel(intl, field)}
                  getFieldDisplayText={(field) =>
                    getFieldDisplayText(intl, field)
                  }
                  renderFieldValue={(field) =>
                    renderFieldValue(intl, field)
                  }
                />
              )}
            />
          </TabPanel>
          <TabPanel>
            <ExpandableHistoryTable
              title={intl.formatMessage({ id: "patientHistory.tabs.results" })}
              columns={resultColumns}
              rows={filteredResults}
              emptyMessage={intl.formatMessage({
                id: "patientHistory.empty.results",
              })}
              rowId={(row) => row.id}
              renderDetails={(row) => <ResultDetails row={row} />}
            />
          </TabPanel>
          <TabPanel>
            <SimpleHistoryTable
              title={intl.formatMessage({ id: "patientHistory.tabs.storage" })}
              columns={storageColumns}
              rows={filteredStoredSamples}
              emptyMessage={intl.formatMessage({
                id: "patientHistory.empty.storage",
              })}
            />
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>
  );
};

const SimpleHistoryTable = <T extends object>({
  title,
  columns,
  rows,
  emptyMessage,
}: {
  title: string;
  columns: Column<T>[];
  rows: T[];
  emptyMessage: string;
}) => {
  if (!rows.length) {
    return <div className="patient-history-summary__empty">{emptyMessage}</div>;
  }

  return (
    <div className="patient-history-summary__table">
      <TableContainer title={title}>
        <Table size="sm" useZebraStyles>
          <TableHead>
            <TableRow>
              {columns.map((column) => (
                <TableHeader key={column.key}>{column.header}</TableHeader>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row, index) => (
              <TableRow key={index}>
                {columns.map((column) => (
                  <TableCell key={column.key}>
                    {column.render(row) || "-"}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </div>
  );
};

const ExpandableHistoryTable = <T extends object>({
  title,
  columns,
  rows,
  emptyMessage,
  rowId,
  renderDetails,
}: {
  title: string;
  columns: Column<T>[];
  rows: T[];
  emptyMessage: string;
  rowId: (row: T) => string;
  renderDetails: (row: T) => React.ReactNode;
}) => {
  const intl = useIntl();
  const [expandedRowId, setExpandedRowId] = useState<string | null>(null);

  useEffect(() => {
    setExpandedRowId((current) =>
      current && rows.some((row) => rowId(row) === current) ? current : null,
    );
  }, [rowId, rows]);

  if (!rows.length) {
    return <div className="patient-history-summary__empty">{emptyMessage}</div>;
  }

  return (
    <div className="patient-history-summary__table">
      <TableContainer title={title}>
        <Table size="sm" useZebraStyles>
          <TableHead>
            <TableRow>
              <TableHeader className="patient-history-summary__expand-column">
                {intl.formatMessage({ id: "patientHistory.table.details" })}
              </TableHeader>
              {columns.map((column) => (
                <TableHeader key={column.key}>{column.header}</TableHeader>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => {
              const currentRowId = rowId(row);
              const isExpanded = expandedRowId === currentRowId;

              return (
                <React.Fragment key={currentRowId}>
                  <TableRow>
                    <TableCell className="patient-history-summary__expand-cell">
                      <button
                        type="button"
                        className="patient-history-summary__expand-button"
                        onClick={() =>
                          setExpandedRowId((current) =>
                            current === currentRowId ? null : currentRowId,
                          )
                        }
                      >
                        {isExpanded
                          ? intl.formatMessage({
                              id: "patientHistory.table.hideDetails",
                            })
                          : intl.formatMessage({
                              id: "patientHistory.table.viewDetails",
                            })}
                      </button>
                    </TableCell>
                    {columns.map((column) => (
                      <TableCell key={column.key}>
                        {column.render(row) || "-"}
                      </TableCell>
                    ))}
                  </TableRow>
                  {isExpanded ? (
                    <TableRow className="patient-history-summary__expanded-row">
                      <TableCell colSpan={columns.length + 1}>
                        <div className="patient-history-summary__expanded-content">
                          {renderDetails(row)}
                        </div>
                      </TableCell>
                    </TableRow>
                  ) : null}
                </React.Fragment>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>
    </div>
  );
};

const SampleDetails: React.FC<{
  row: PatientHistorySample;
  resolveFieldLabel: (field: DetailField) => string;
  getFieldDisplayText: (field: DetailField) => string;
  renderFieldValue: (field: DetailField) => React.ReactNode;
}> = ({ row, resolveFieldLabel, getFieldDisplayText, renderFieldValue }) => (
  <div className="patient-history-summary__details">
    <DetailSection
      titleId="patientHistory.detail.collectionData"
      fields={row.collectionFields || []}
      resolveFieldLabel={resolveFieldLabel}
      getFieldDisplayText={getFieldDisplayText}
      renderFieldValue={renderFieldValue}
      emptyId="patientHistory.detail.empty.collectionData"
    />
    <DetailSection
      titleId="patientHistory.detail.receptionData"
      fields={row.receptionFields || []}
      resolveFieldLabel={resolveFieldLabel}
      getFieldDisplayText={getFieldDisplayText}
      renderFieldValue={renderFieldValue}
      emptyId="patientHistory.detail.empty.receptionData"
    />
    <DetailSection
      titleId="patientHistory.detail.orderInfo"
      fields={row.orderFields || []}
      resolveFieldLabel={resolveFieldLabel}
      getFieldDisplayText={getFieldDisplayText}
      renderFieldValue={renderFieldValue}
      emptyId="patientHistory.detail.empty.orderData"
    />
  </div>
);

const OrderDetails: React.FC<{
  row: PatientHistoryOrder;
  resolveFieldLabel: (field: DetailField) => string;
  getFieldDisplayText: (field: DetailField) => string;
  renderFieldValue: (field: DetailField) => React.ReactNode;
}> = ({ row, resolveFieldLabel, getFieldDisplayText, renderFieldValue }) => (
  <div className="patient-history-summary__details">
    <DetailSection
      titleId="patientHistory.detail.orderInfo"
      fields={row.orderFields || []}
      resolveFieldLabel={resolveFieldLabel}
      getFieldDisplayText={getFieldDisplayText}
      renderFieldValue={renderFieldValue}
      emptyId="patientHistory.detail.empty.orderData"
    />
  </div>
);

const ResultDetails: React.FC<{
  row: PatientHistoryResult;
}> = ({ row }) => {
  const intl = useIntl();
  const activeAdditionalFields = Array.isArray(row?.additionalFieldDefinitions)
    ? row.additionalFieldDefinitions.filter(
        (fieldDefinition) => fieldDefinition?.active !== false,
      )
    : [];
  const visibleAdditionalFields = getVisibleAdditionalFields(
    row,
    activeAdditionalFields,
  );
  const primaryResultVisible = isPrimaryResultVisible(row);
  const primaryLayout = getPrimaryResultLayout(row, intl);
  const groupedBlocks: Array<{
    blockName: string;
    blockSortOrder: number;
    items: Array<
      | {
          type: "primary";
          fieldSortOrder: number;
        }
      | {
          type: "additional";
          fieldSortOrder: number;
          fieldDefinition: TestAdditionalFieldDefinition;
        }
    >;
  }> = [];
  const groupedByName = new Map<
    string,
    {
      blockName: string;
      blockSortOrder: number;
      items: Array<
        | {
            type: "primary";
            fieldSortOrder: number;
          }
        | {
            type: "additional";
            fieldSortOrder: number;
            fieldDefinition: TestAdditionalFieldDefinition;
          }
      >;
    }
  >();
  const blockSortOrderByKey = new Map<string, number>();
  let nextBlockSortOrder = 1;

  const resolvedAdditionalFields = visibleAdditionalFields.map(
    (fieldDefinition) => {
      const layout = getFieldBlockAndScope(fieldDefinition);
      const blockKey = String(layout.blockName || "").trim();
      if (!blockSortOrderByKey.has(blockKey)) {
        blockSortOrderByKey.set(
          blockKey,
          layout.blockSortOrder || nextBlockSortOrder,
        );
        nextBlockSortOrder += 1;
      }

      return {
        fieldDefinition,
        layout: {
          ...layout,
          blockSortOrder: blockSortOrderByKey.get(blockKey) || 1,
        },
      };
    },
  );

  [...resolvedAdditionalFields]
    .sort((leftField, rightField) => {
      const leftLayout = leftField.layout;
      const rightLayout = rightField.layout;
      if (leftLayout.blockSortOrder !== rightLayout.blockSortOrder) {
        return leftLayout.blockSortOrder - rightLayout.blockSortOrder;
      }
      const blockNameComparison = leftLayout.blockName.localeCompare(
        rightLayout.blockName,
      );
      if (blockNameComparison !== 0) {
        return blockNameComparison;
      }
      if (leftLayout.fieldSortOrder !== rightLayout.fieldSortOrder) {
        return leftLayout.fieldSortOrder - rightLayout.fieldSortOrder;
      }
      return (leftField.fieldDefinition?.sortOrder || 0) -
        (rightField.fieldDefinition?.sortOrder || 0);
    })
    .forEach(({ fieldDefinition, layout }) => {
      const { blockName, blockSortOrder, fieldSortOrder } = layout;
      const blockKey = String(blockName || "").trim();
      if (!groupedByName.has(blockKey)) {
        const group = {
          blockName,
          blockSortOrder,
          items: [],
        };
        groupedByName.set(blockKey, group);
        groupedBlocks.push(group);
      }
      groupedByName.get(blockKey)?.items.push({
        type: "additional",
        fieldDefinition,
        fieldSortOrder,
      });
    });

  const officialBlockName = intl.formatMessage({
    id: "results.block.official",
    defaultMessage: "Official",
  });
  const primaryResultLabel =
    typeof row?.resultName === "string" && row.resultName.trim().length > 0
      ? row.resultName.trim()
      : intl.formatMessage({
          id: "column.name.result",
          defaultMessage: "Result",
        });
  const primaryBlockName = primaryLayout.blockName || officialBlockName;
  const primaryBlockKey = String(primaryBlockName || "").trim();

  if (primaryResultVisible && !groupedByName.has(primaryBlockKey)) {
    const group = {
      blockName: primaryBlockName,
      blockSortOrder: primaryLayout.blockSortOrder,
      items: [],
    };
    groupedByName.set(primaryBlockKey, group);
    groupedBlocks.push(group);
  }

  if (primaryResultVisible) {
    groupedByName.get(primaryBlockKey)?.items.push({
      type: "primary",
      fieldSortOrder: primaryLayout.fieldSortOrder,
    });
  }

  const sortedBlocks = groupedBlocks
    .map((block) => ({
      ...block,
      items: [...(block.items || [])].sort((leftItem, rightItem) => {
        if (leftItem.fieldSortOrder !== rightItem.fieldSortOrder) {
          return leftItem.fieldSortOrder - rightItem.fieldSortOrder;
        }
        if (leftItem.type !== rightItem.type) {
          return leftItem.type === "primary" ? -1 : 1;
        }
        return 0;
      }),
    }))
    .filter((block) => block.items.length > 0)
    .sort((leftBlock, rightBlock) => {
      if (leftBlock.blockSortOrder !== rightBlock.blockSortOrder) {
        return leftBlock.blockSortOrder - rightBlock.blockSortOrder;
      }
      return leftBlock.blockName.localeCompare(rightBlock.blockName);
    });

  if (!sortedBlocks.length) {
    return (
      <div className="patient-history-summary__details">
        <section className="patient-history-summary__detail-section">
          <h4 className="patient-history-summary__detail-title">
            {intl.formatMessage({ id: "patientHistory.detail.testFields" })}
          </h4>
          <div className="patient-history-summary__detail-empty">
            {intl.formatMessage({
              id: "patientHistory.detail.empty.resultValues",
            })}
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="patient-history-summary__details">
      {sortedBlocks.map((block, blockIndex) => (
        <section
          key={`${row.id}-${block.blockName}-${blockIndex}`}
          className="patient-history-summary__detail-section"
        >
          <h4 className="patient-history-summary__detail-title">
            {block.blockName || officialBlockName}
          </h4>
          <div className="patient-history-summary__detail-grid">
            {block.items.map((item, itemIndex) => {
              if (item.type === "primary") {
                return (
                  <div
                    key={`primary-${row.id}-${blockIndex}-${itemIndex}`}
                    className="patient-history-summary__detail-item"
                  >
                    <div className="patient-history-summary__detail-label">
                      {primaryResultLabel}
                    </div>
                    <div className="patient-history-summary__detail-value">
                      {row.resultValue || "-"}
                    </div>
                  </div>
                );
              }

              const value = row?.additionalFieldValues?.[
                item.fieldDefinition.fieldKey
              ];
              return (
                <div
                  key={`${item.fieldDefinition.fieldKey}-${row.id}-${blockIndex}-${itemIndex}`}
                  className="patient-history-summary__detail-item"
                >
                  <div className="patient-history-summary__detail-label">
                    {item.fieldDefinition.displayName ||
                      item.fieldDefinition.fieldKey}
                  </div>
                  <div className="patient-history-summary__detail-value">
                    {String(
                      item.fieldDefinition?.fieldType || "",
                    ).toUpperCase() === "DOCUMENT" ? (
                      (() => {
                        const documentValue = parseDocumentFieldValue(value);
                        const previewHref = buildResultDocumentPreviewHref(
                          row.id,
                          item.fieldDefinition.fieldKey,
                        );

                        if (!documentValue?.fileName) {
                          return "-";
                        }

                        return previewHref ? (
                          <a
                            href={previewHref}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            {documentValue.fileName}
                          </a>
                        ) : (
                          documentValue.fileName
                        );
                      })()
                    ) : (
                      formatConfiguredFieldValue(
                        intl,
                        item.fieldDefinition,
                        value,
                      )
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
};

const DetailSection: React.FC<{
  titleId: string;
  fields: DetailField[];
  resolveFieldLabel: (field: DetailField) => string;
  getFieldDisplayText: (field: DetailField) => string;
  renderFieldValue: (field: DetailField) => React.ReactNode;
  emptyId?: string;
}> = ({
  titleId,
  fields,
  resolveFieldLabel,
  getFieldDisplayText,
  renderFieldValue,
  emptyId = "patientHistory.detail.empty.generic",
}) => {
  const intl = useIntl();

  return (
    <section className="patient-history-summary__detail-section">
      <h4 className="patient-history-summary__detail-title">
        {intl.formatMessage({ id: titleId })}
      </h4>
      {fields.length ? (
        <div className="patient-history-summary__detail-grid">
          {fields.map((field, index) => (
            <div
              key={`${field.source}-${field.key}-${index}`}
              className={`patient-history-summary__detail-item ${
                getFieldDisplayText(field).length > 120
                  ? "patient-history-summary__detail-item--wide"
                  : ""
              }`}
            >
              <div className="patient-history-summary__detail-label">
                {resolveFieldLabel(field)}
              </div>
              <div className="patient-history-summary__detail-value">
                {renderFieldValue(field)}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <div className="patient-history-summary__detail-empty">
          {intl.formatMessage({ id: emptyId })}
        </div>
      )}
    </section>
  );
};

const resolveFieldLabel = (
  intl: ReturnType<typeof useIntl>,
  field: DetailField,
) => {
  const sampleMessageId = SAMPLE_FIXED_FIELD_LABEL_MESSAGE_IDS[field?.key];
  if (sampleMessageId) {
    return intl.formatMessage({
      id: sampleMessageId,
      defaultMessage: field.label || field.key,
    });
  }

  if (field?.source === "orderFixed") {
    const messageId = ORDER_FIXED_FIELD_LABEL_MESSAGE_IDS[field.key];
    if (messageId) {
      return intl.formatMessage({
        id: messageId,
        defaultMessage: field.label || field.key,
      });
    }
  }

  const messageId = DETAIL_LABEL_MESSAGE_IDS[field?.key];
  if (messageId) {
    return intl.formatMessage({
      id: messageId,
      defaultMessage: field.label || field.key,
    });
  }

  return field?.label || field?.key || "-";
};

const getFieldDisplayText = (
  intl: ReturnType<typeof useIntl>,
  field: DetailField,
) => {
  const rawValue = String(field?.value || "").trim();
  if (!rawValue) {
    return "-";
  }
  if (rawValue.toLowerCase() === "true") {
    return intl.formatMessage({ id: "yes.option" });
  }
  if (rawValue.toLowerCase() === "false") {
    return intl.formatMessage({ id: "no.option" });
  }
  if (String(field?.fieldType || "").toUpperCase() === "DOCUMENT") {
    return parseDocumentFieldValue(rawValue)?.fileName || "-";
  }
  return rawValue;
};

const renderFieldValue = (
  intl: ReturnType<typeof useIntl>,
  field: DetailField,
  previewHref?: string,
) => {
  const displayText = getFieldDisplayText(intl, field);
  if (displayText === "-") {
    return displayText;
  }

  if (String(field?.fieldType || "").toUpperCase() === "DOCUMENT") {
    return previewHref ? (
      <a href={previewHref} target="_blank" rel="noopener noreferrer">
        {displayText}
      </a>
    ) : (
      displayText
    );
  }

  return displayText;
};

const matchesFilters = (
  filters: FilterState,
  searchableFields: Array<string | null | undefined>,
  dateFields: Array<string | null | undefined>,
) => {
  const normalizedSearch = filters.search.trim().toLowerCase();
  const matchesSearch =
    !normalizedSearch ||
    searchableFields.some((field) =>
      String(field || "")
        .toLowerCase()
        .includes(normalizedSearch),
    );

  if (!matchesSearch) {
    return false;
  }

  if (!filters.fromDate && !filters.toDate) {
    return true;
  }

  const comparableDates = dateFields
    .map((value) => parseComparableDate(value))
    .filter((value): value is string => Boolean(value));

  if (!comparableDates.length) {
    return false;
  }

  return comparableDates.some((value) => {
    if (filters.fromDate && value < filters.fromDate) {
      return false;
    }
    if (filters.toDate && value > filters.toDate) {
      return false;
    }
    return true;
  });
};

const parseComparableDate = (value: string | null | undefined) => {
  if (!value) {
    return "";
  }

  const normalizedValue = value.trim();
  if (!normalizedValue) {
    return "";
  }

  const isoMatch = normalizedValue.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (isoMatch) {
    return `${isoMatch[1]}-${isoMatch[2]}-${isoMatch[3]}`;
  }

  const slashMatch = normalizedValue.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
  if (slashMatch) {
    const first = Number(slashMatch[1]);
    const second = Number(slashMatch[2]);
    if (first > 12) {
      return `${slashMatch[3]}-${slashMatch[2]}-${slashMatch[1]}`;
    }
    if (second > 12) {
      return `${slashMatch[3]}-${slashMatch[1]}-${slashMatch[2]}`;
    }
    return `${slashMatch[3]}-${slashMatch[1]}-${slashMatch[2]}`;
  }

  const parsedDate = new Date(normalizedValue);
  if (!Number.isNaN(parsedDate.getTime())) {
    return parsedDate.toISOString().slice(0, 10);
  }

  return "";
};

const isCompletedStatus = (status: string | null | undefined) =>
  String(status || "").toLowerCase().includes("final") ||
  String(status || "").toLowerCase().includes("complete");

export default PatientHistorySummaryPanel;
