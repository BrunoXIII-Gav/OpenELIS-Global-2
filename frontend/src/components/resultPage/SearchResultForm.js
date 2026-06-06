import React, { useContext, useEffect, useState, useRef } from "react";
import { FormattedMessage, injectIntl, useIntl } from "react-intl";
import "../Style.css";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
  Roles,
} from "../utils/Utils";
import {
  Form,
  TextInput,
  TextArea,
  Checkbox,
  Button,
  Grid,
  Column,
  Stack,
  Pagination,
  Select,
  SelectItem,
  Loading,
  Link,
  FileUploader,
  RadioButtonGroup,
  RadioButton,
} from "@carbon/react";
import { Copy, ArrowLeft, ArrowRight } from "@carbon/icons-react";
import CustomLabNumberInput from "../common/CustomLabNumberInput";
import DataTable from "react-data-table-component";
import { Formik, Field } from "formik";
import SearchResultFormValues from "../formModel/innitialValues/SearchResultFormValues";
import { AlertDialog, NotificationKinds } from "../common/CustomNotification";
import { NotificationContext } from "../layout/Layout";
import SearchPatientForm from "../patient/SearchPatientForm";
import ReferredOutTests from "./resultsReferredOut/ReferredOutTests";
import { ConfigurationContext } from "../layout/Layout";
import config from "../../config.json";
import CustomDatePicker from "../common/CustomDatePicker";
import CompactFileInput from "./fileUpload/FileInput";
import StorageLocationSelector from "../storage/StorageLocationSelector";
import ResultMultiSelect from "../common/multiSelect";
import CascadingMultiSelect from "../common/cascadingMultiSelect";

const parseAdditionalFieldMetadata = (fieldDefinition) => {
  const metadataJson = fieldDefinition?.metadataJson;
  if (!metadataJson || typeof metadataJson !== "string") {
    return {};
  }
  try {
    const parsed = JSON.parse(metadataJson);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (e) {
    return {};
  }
};

const parseDocumentFieldValue = (rawValue) => {
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
    const base64Content = String(parsed.base64Content || "").trim();
    if (!fileName || !base64Content) {
      return null;
    }
    return { fileName, fileType, base64Content };
  } catch (e) {
    return null;
  }
};

const encodeDocumentFieldValue = (filePayload) => {
  if (!filePayload) {
    return "";
  }
  return JSON.stringify({
    fileName: filePayload.fileName || "",
    fileType: filePayload.fileType || "",
    base64Content: filePayload.base64Content || "",
  });
};

const readFileAsBase64 = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const base64 = String(reader.result || "");
      const [, content = ""] = base64.split(",", 2);
      resolve(content);
    };
    reader.onerror = () =>
      reject(new Error("Failed to convert selected file to base64"));
    reader.readAsDataURL(file);
  });

const openDocumentFieldValue = (filePayload) => {
  const fileType = String(filePayload?.fileType || "").trim();
  const base64Content = String(filePayload?.base64Content || "").trim();
  if (!base64Content) {
    return;
  }
  const safeFileType = fileType || "application/octet-stream";
  const source = `data:${safeFileType};base64,${base64Content}`;
  window.open(source, "_blank", "noopener,noreferrer");
};

const AdditionalFieldEditor = ({
  inputId,
  fieldLabel,
  fieldType,
  value,
  activeOptions,
  fieldMetadata,
  onCommit,
}) => {
  const [draftValue, setDraftValue] = useState(value || "");

  useEffect(() => {
    setDraftValue(value || "");
  }, [value, inputId, fieldType]);

  const commitValue = (nextValue = draftValue) => {
    onCommit(nextValue == null ? "" : nextValue);
  };

  switch (fieldType) {
    case "DOCUMENT": {
      const currentDocument = parseDocumentFieldValue(draftValue);
      const acceptedMimeTypes = Array.isArray(fieldMetadata?.document?.accept)
        ? fieldMetadata.document.accept
        : [];
      const maxSizeMb = Number.parseInt(fieldMetadata?.document?.maxSizeMb, 10);
      const maxBytes =
        Number.isFinite(maxSizeMb) && maxSizeMb > 0
          ? maxSizeMb * 1024 * 1024
          : null;

      return (
        <div>
          <label
            htmlFor={inputId}
            style={{ display: "block", marginBottom: "0.25rem" }}
          >
            {fieldLabel}
          </label>
          <FileUploader
            id={inputId}
            buttonLabel={<FormattedMessage id="label.button.uploadfile" />}
            filenameStatus={currentDocument ? "complete" : ""}
            accept={
              acceptedMimeTypes.length > 0 ? acceptedMimeTypes : undefined
            }
            multiple={false}
            filename={currentDocument?.fileName}
            onChange={async (event) => {
              const file = event.target.files?.[0];
              if (!file) {
                return;
              }
              if (maxBytes != null && file.size > maxBytes) {
                return;
              }
              try {
                const base64Content = await readFileAsBase64(file);
                const nextPayload = {
                  fileName: file.name,
                  fileType: file.type,
                  base64Content,
                };
                const encoded = encodeDocumentFieldValue(nextPayload);
                setDraftValue(encoded);
                commitValue(encoded);
              } catch (error) {
                console.error(error);
              }
            }}
          />
          {currentDocument?.fileName ? (
            <Link
              style={{ cursor: "pointer" }}
              onClick={() => openDocumentFieldValue(currentDocument)}
            >
              {currentDocument.fileName}
            </Link>
          ) : null}
        </div>
      );
    }
    case "TEXTAREA":
      return (
        <TextArea
          id={inputId}
          labelText={fieldLabel}
          rows={2}
          value={draftValue}
          onChange={(event) => {
            const nextValue = event.target.value;
            setDraftValue(nextValue);
          }}
          onBlur={() => commitValue()}
        />
      );
    case "NUMBER":
      return (
        <TextInput
          id={inputId}
          labelText={fieldLabel}
          type="number"
          value={draftValue}
          onChange={(event) => {
            const nextValue = event.target.value;
            setDraftValue(nextValue);
          }}
          onBlur={() => commitValue()}
        />
      );
    case "DATE":
      return (
        <TextInput
          id={inputId}
          labelText={fieldLabel}
          type="date"
          value={draftValue}
          onChange={(event) => {
            const nextValue = event.target.value;
            setDraftValue(nextValue);
          }}
          onBlur={() => commitValue()}
        />
      );
    case "TIME":
      return (
        <TextInput
          id={inputId}
          labelText={fieldLabel}
          type="time"
          value={draftValue}
          onChange={(event) => {
            const nextValue = event.target.value;
            setDraftValue(nextValue);
          }}
          onBlur={() => commitValue()}
        />
      );
    case "DATETIME":
      return (
        <TextInput
          id={inputId}
          labelText={fieldLabel}
          type="datetime-local"
          value={draftValue}
          onChange={(event) => {
            const nextValue = event.target.value;
            setDraftValue(nextValue);
          }}
          onBlur={() => commitValue()}
        />
      );
    case "BOOLEAN":
      return (
        <Checkbox
          id={inputId}
          labelText={fieldLabel}
          checked={draftValue === "true"}
          onChange={(event) => {
            const nextValue = event.target.checked ? "true" : "false";
            setDraftValue(nextValue);
            commitValue(nextValue);
          }}
        />
      );
    case "SELECT":
    case "SYSTEM_USER_BIOLOGIST_SELECT":
      return (
        <Select
          id={inputId}
          labelText={fieldLabel}
          value={draftValue}
          onChange={(event) => {
            const nextValue = event.target.value;
            setDraftValue(nextValue);
            commitValue(nextValue);
          }}
        >
          <SelectItem value="" text="" />
          {activeOptions.map((option) => (
            <SelectItem
              key={`${inputId}-${option.optionKey}`}
              value={option.optionKey}
              text={option.optionLabel || option.optionKey}
            />
          ))}
        </Select>
      );
    case "RADIO":
      return (
        <div>
          <label htmlFor={inputId} style={{ display: "block" }}>
            {fieldLabel}
          </label>
          <RadioButtonGroup
            id={inputId}
            legendText=""
            name={inputId}
            valueSelected={draftValue}
            onChange={(valueSelected) => {
              setDraftValue(valueSelected);
              commitValue(valueSelected);
            }}
          >
            {activeOptions.map((option) => (
              <RadioButton
                key={`${inputId}-${option.optionKey}`}
                id={`${inputId}-${option.optionKey}`}
                labelText={option.optionLabel || option.optionKey}
                value={option.optionKey}
              />
            ))}
          </RadioButtonGroup>
        </div>
      );
    case "MULTISELECT": {
      const selectedValues = (draftValue || "")
        .split(",")
        .map((item) => item.trim())
        .filter((item) => item.length > 0);

      return (
        <div>
          <label
            htmlFor={inputId}
            style={{ display: "block", marginBottom: "0.25rem" }}
          >
            {fieldLabel}
          </label>
          <select
            id={inputId}
            multiple
            value={selectedValues}
            onChange={(event) => {
              const values = Array.from(event.target.selectedOptions).map(
                (option) => option.value,
              );
              const nextValue = values.join(",");
              setDraftValue(nextValue);
              commitValue(nextValue);
            }}
            style={{ width: "100%", minHeight: "5rem" }}
          >
            {activeOptions.map((option) => (
              <option
                key={`${inputId}-${option.optionKey}`}
                value={option.optionKey}
              >
                {option.optionLabel || option.optionKey}
              </option>
            ))}
          </select>
        </div>
      );
    }
    default:
      return (
        <TextInput
          id={inputId}
          labelText={fieldLabel}
          value={draftValue}
          onChange={(event) => {
            const nextValue = event.target.value;
            setDraftValue(nextValue);
          }}
          onBlur={() => commitValue()}
        />
      );
  }
};

const InlineResultEditor = ({
  inputId,
  fieldName,
  fieldType,
  value,
  style,
  onCommit,
  onPostCommit,
}) => {
  const [draftValue, setDraftValue] = useState(value || "");

  useEffect(() => {
    setDraftValue(value || "");
  }, [value, inputId, fieldType]);

  const commitValue = (nextValue = draftValue) => {
    const normalizedValue = nextValue == null ? "" : nextValue;
    onCommit(normalizedValue);
    if (onPostCommit) {
      onPostCommit(normalizedValue);
    }
  };

  if (fieldType === "N") {
    return (
      <TextInput
        id={inputId}
        name={fieldName}
        labelText=""
        type="number"
        value={draftValue}
        style={style}
        onChange={(event) => {
          setDraftValue(event.target.value);
        }}
        onBlur={() => commitValue()}
      />
    );
  }

  return (
    <TextArea
      id={inputId}
      name={fieldName}
      rows={1}
      labelText=""
      value={draftValue}
      onChange={(event) => {
        setDraftValue(event.target.value);
      }}
      onBlur={() => commitValue()}
    />
  );
};

const normalizeResultEntryScope = (scopeValue) =>
  String(scopeValue || "").toUpperCase() === "PRELIMINARY"
    ? "PRELIMINARY"
    : "OFFICIAL";

const parsePositiveSortOrder = (value, fallbackValue) => {
  const parsedValue = Number.parseInt(value, 10);
  return Number.isFinite(parsedValue) && parsedValue > 0
    ? parsedValue
    : fallbackValue;
};

const getFieldBlockAndScope = (fieldDefinition) => {
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
      fieldDefinition?.blockSortOrder ?? metadata.blockSortOrder,
      0,
    ),
    fieldSortOrder: parsePositiveSortOrder(
      fieldDefinition?.fieldSortOrder ??
        fieldDefinition?.sortOrder ??
        metadata.fieldSortOrder,
      1,
    ),
  };
};

const getPrimaryResultLayout = (data, intl) => {
  const metadata = parseAdditionalFieldMetadata({
    metadataJson: data?.resultDisplayConfigJson,
  });
  const entryScope = normalizeResultEntryScope(metadata.entryScope);
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

const isPrimaryResultActive = (data) =>
  parseAdditionalFieldMetadata({
    metadataJson: data?.resultDisplayConfigJson,
  })?.active !== false;

const isTubeSelectorFieldDefinition = (fieldDefinition) =>
  parseAdditionalFieldMetadata(fieldDefinition)?.tubeSelector?.enabled === true;

const getTubeActivationCount = (fieldDefinition) => {
  const activationCount =
    parseAdditionalFieldMetadata(fieldDefinition)?.tubeBlock?.activationCount;
  const parsedValue = Number.parseInt(activationCount, 10);
  return Number.isFinite(parsedValue) && parsedValue > 0 ? parsedValue : 0;
};

const getVisibleAdditionalFields = (data, fieldDefinitions) => {
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
  if (
    !primaryMetadata?.tubeSelector?.enabled &&
    !selectorField?.fieldKey
  ) {
    return definitions;
  }

  const selectedTubeCount = Number.parseInt(selectorRawValue, 10);
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

const isPrimaryResultVisible = (data) => {
  if (!isPrimaryResultActive(data)) {
    return false;
  }
  const metadata = parseAdditionalFieldMetadata({
    metadataJson: data?.resultDisplayConfigJson,
  });
  const activationCount = Number.parseInt(
    metadata?.tubeBlock?.activationCount,
    10,
  );
  if (!Number.isFinite(activationCount) || activationCount <= 0) {
    return true;
  }

  let selectorRawValue = null;
  if (metadata?.tubeSelector?.enabled) {
    selectorRawValue = data?.resultValue;
  } else {
    const activeAdditionalFields = Array.isArray(data?.additionalFieldDefinitions)
      ? data.additionalFieldDefinitions.filter(
          (fieldDefinition) => fieldDefinition?.active !== false,
        )
      : [];
    const selectorField = activeAdditionalFields.find(isTubeSelectorFieldDefinition);
    if (selectorField?.fieldKey) {
      selectorRawValue = data?.additionalFieldValues?.[selectorField.fieldKey];
    }
  }

  const selectedTubeCount = Number.parseInt(selectorRawValue, 10);
  return Number.isFinite(selectedTubeCount) && selectedTubeCount >= activationCount;
};

const isChildTubeUsageBlockEnabled = (fieldDefinition) =>
  parseAdditionalFieldMetadata(fieldDefinition)?.tubeUsage?.childBlockEnabled ===
  true;

const isPrimaryChildTubeUsageBlockEnabled = (data) =>
  parseAdditionalFieldMetadata({
    metadataJson: data?.resultDisplayConfigJson,
  })?.tubeUsage?.childBlockEnabled === true;

const normalizeBlockIdentifier = (value) =>
  String(value == null ? "" : value)
    .trim()
    .toUpperCase();

const getEnteredChildTubeUsageBlocks = (data, intl) => {
  const activeAdditionalFields = Array.isArray(data?.additionalFieldDefinitions)
    ? data.additionalFieldDefinitions.filter(
        (fieldDefinition) => fieldDefinition?.active !== false,
      )
    : [];
  const visibleAdditionalFields = getVisibleAdditionalFields(
    data,
    activeAdditionalFields,
  );
  const enteredBlocks = new Set();

  visibleAdditionalFields.forEach((fieldDefinition) => {
    if (!isChildTubeUsageBlockEnabled(fieldDefinition)) {
      return;
    }
    const { blockName } = getFieldBlockAndScope(fieldDefinition);
    const value = data?.additionalFieldValues?.[fieldDefinition?.fieldKey];
    if (value != null && `${value}`.trim() !== "") {
      enteredBlocks.add(normalizeBlockIdentifier(blockName));
    }
  });

  if (isPrimaryChildTubeUsageBlockEnabled(data)) {
    const primaryLayout = getPrimaryResultLayout(data, intl);
    const primaryBlockName = String(primaryLayout?.blockName || "").trim();
    const primaryValue = data?.resultValue;
    if (
      isPrimaryResultActive(data) &&
      primaryBlockName &&
      primaryValue != null &&
      `${primaryValue}`.trim() !== ""
    ) {
      enteredBlocks.add(normalizeBlockIdentifier(primaryBlockName));
    }
  }

  return Array.from(enteredBlocks);
};

const getConfiguredChildTubeUsageBlocks = (data, intl) => {
  const configuredBlocks = new Set();
  const activeAdditionalFields = Array.isArray(data?.additionalFieldDefinitions)
    ? data.additionalFieldDefinitions.filter(
        (fieldDefinition) => fieldDefinition?.active !== false,
      )
    : [];
  const visibleAdditionalFields = getVisibleAdditionalFields(
    data,
    activeAdditionalFields,
  );

  visibleAdditionalFields.forEach((fieldDefinition) => {
    if (!isChildTubeUsageBlockEnabled(fieldDefinition)) {
      return;
    }
    const { blockName } = getFieldBlockAndScope(fieldDefinition);
    if (blockName) {
      configuredBlocks.add(normalizeBlockIdentifier(blockName));
    }
  });

  if (isPrimaryChildTubeUsageBlockEnabled(data)) {
    const primaryLayout = getPrimaryResultLayout(data, intl);
    if (isPrimaryResultActive(data) && primaryLayout?.blockName) {
      configuredBlocks.add(normalizeBlockIdentifier(primaryLayout.blockName));
    }
  }

  return Array.from(configuredBlocks);
};

const getTubeUsageTotalRemaining = (row) => {
  const remainingByTube = row?.parentTubeRemainingQuantities || {};
  const totalBaseRemaining = Object.values(remainingByTube).reduce(
    (sum, rawValue) => {
      const parsed = Number(rawValue);
      return Number.isFinite(parsed) ? sum + parsed : sum;
    },
    0,
  );

  if (totalBaseRemaining <= 0) {
    return "";
  }

  const totalUsed = Array.isArray(row?.blockSampleUsages)
    ? row.blockSampleUsages.reduce((sum, usage) => {
        const parsedUsage = Number(usage?.usedQuantity);
        return Number.isFinite(parsedUsage) && parsedUsage > 0
          ? sum + parsedUsage
          : sum;
      }, 0)
    : Number(row?.sampleUsageQuantity) || 0;

  return Math.max(0, totalBaseRemaining - totalUsed).toFixed(3);
};

function ResultSearchPage() {
  const [originalResultForm, setOriginalResultForm] = useState({
    testResult: [],
  });
  const [resultForm, setResultForm] = useState(originalResultForm);
  const [searchBy, setSearchBy] = useState({ type: "", doRange: false });
  const [param, setParam] = useState("&accessionNumber=");

  const setResults = (resultForm) => {
    setOriginalResultForm(resultForm);
    setResultForm(resultForm);
  };

  return (
    <>
      <SearchResultForm
        setParam={setParam}
        setSearchBy={setSearchBy}
        setResults={setResults}
      />
      <SearchResults
        extraParams={param}
        searchBy={searchBy}
        results={resultForm}
        setResultForm={setResultForm}
        refreshOnSubmit={true}
      />
    </>
  );
}

export function SearchResultForm(props) {
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [tests, setTests] = useState([]);
  const [analysisStatusTypes, setAnalysisStatusTypes] = useState([]);
  const [sampleStatusTypes, setSampleStatusTypes] = useState([]);
  const [loading, setLoading] = useState(false);
  const [searchBy, setSearchBy] = useState({ type: "", doRange: false });
  const [patient, setPatient] = useState({ patientPK: "" });
  const [testSections, setTestSections] = useState([]);
  const [defaultTestSectionId, setDefaultTestSectionId] = useState("");
  const [defaultTestSectionLabel, setDefaultTestSectionLabel] = useState("");
  const [defaultTestId, setDefaultTestId] = useState("");
  const [defaultTestLabel, setDefaultTestLabel] = useState("");
  const [defaultSampleStatusId, setDefaultSampleStatusId] = useState("");
  const [defaultSampleStatusLabel, setDefaultSampleStatusLabel] = useState("");
  const [defaultAnalysisStatusId, setDefaultAnalysisStatusId] = useState("");
  const [defaultAnalysisStatusLabel, setDefaultAnalysisStatusLabel] =
    useState("");
  const [searchFormValues, setSearchFormValues] = useState(
    SearchResultFormValues,
  );
  const [nextPage, setNextPage] = useState(null);
  const [previousPage, setPreviousPage] = useState(null);
  const [pagination, setPagination] = useState(false);
  const [currentApiPage, setCurrentApiPage] = useState(null);
  const [totalApiPages, setTotalApiPages] = useState(null);
  const [url, setUrl] = useState("");
  const componentMounted = useRef(false);

  const setResultsWithId = (results) => {
    if (results?.testResult) {
      var i = 0;
      results.testResult.forEach((item) => (item.id = "" + i++));
      props.setResults?.(results);
      setLoading(false);
      if (results.paging) {
        var { totalPages, currentPage } = results.paging;
        if (totalPages > 1) {
          setPagination(true);
          setCurrentApiPage(currentPage);
          setTotalApiPages(totalPages);
          if (parseInt(currentPage) < parseInt(totalPages)) {
            setNextPage(parseInt(currentPage) + 1);
          } else {
            setNextPage(null);
          }
          if (parseInt(currentPage) > 1) {
            setPreviousPage(parseInt(currentPage) - 1);
          } else {
            setPreviousPage(null);
          }
        }
      }
    } else {
      props.setResults?.({ testResult: [] });
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "patient.search.nopatient" }),
        kind: NotificationKinds.warning,
      });
      setNotificationVisible(true);
      setLoading(false);
    }
  };

  const intl = useIntl();

  const loadNextResultsPage = () => {
    setLoading(true);
    getFromOpenElisServer(url + "&page=" + nextPage, setResultsWithId);
  };

  const loadPreviousResultsPage = () => {
    setLoading(true);
    getFromOpenElisServer(url + "&page=" + previousPage, setResultsWithId);
  };

  const getSelectedPatient = (patient) => {
    setNextPage(null);
    setPreviousPage(null);
    setPagination(false);
    setPatient(patient);
  };
  useEffect(() => {
    querySearch(searchFormValues);
  }, [patient]);

  const querySearch = (values) => {
    setLoading(true);
    props.setResults({ testResult: [] });

    let accessionNumber =
      values.accessionNumber !== ""
        ? values.accessionNumber
        : values.startLabNo;
    let labNo = accessionNumber ? accessionNumber.trim() : "";
    const endLabNo = values.endLabNo ? values.endLabNo : "";
    values.unitType = values.unitType ? values.unitType : "";

    let searchEndPoint =
      "/rest/LogbookResults?" +
      "labNumber=" +
      labNo +
      "&upperRangeAccessionNumber=" +
      endLabNo +
      "&patientPK=" +
      patient.patientPK +
      "&testSectionId=" +
      values.unitType +
      "&collectionDate=" +
      values.collectionDate +
      "&recievedDate=" +
      values.recievedDate +
      "&selectedTest=" +
      values.testName +
      "&selectedSampleStatus=" +
      values.sampleStatusType +
      "&selectedAnalysisStatus=" +
      values.analysisStatus +
      "&doRange=" +
      searchBy.doRange +
      "&finished=" +
      false;
    setUrl(searchEndPoint);
    props.setSearchBy?.(searchBy);
    switch (searchBy.type) {
      case "unit":
        props.setParam("&testSectionId=" + values.unitType);
        break;
      case "patient":
        props.setParam("&patientId=" + patient.patientPK);
        break;
      case "order":
        props.setParam("&accessionNumber=" + labNo);
        break;
      case "date":
        props.setParam(
          "&selectedTest=" +
            values.testName +
            "&selectedSampleStatus=" +
            values.sampleStatusType +
            "&selectedAnalysisStatus=" +
            values.analysisStatus +
            "&collectionDate=" +
            values.collectionDate +
            "&recievedDate=" +
            values.recievedDate,
        );
        break;
      case "range":
        props.setParam(
          "&accessionNumber=" + labNo + "&upperAccessionNumber=" + endLabNo,
        );
        break;
    }

    getFromOpenElisServer(searchEndPoint, setResultsWithId);
  };

  const handleSubmit = (values) => {
    setNextPage(null);
    setPreviousPage(null);
    setPagination(false);
    querySearch(values);
  };

  const getTests = (tests) => {
    if (componentMounted.current) {
      setTests(tests);
    }
  };

  const getAnalysisStatusTypes = (analysisStatusTypes) => {
    if (componentMounted.current) {
      setAnalysisStatusTypes(analysisStatusTypes);
    }
  };

  const getSampleStatusTypes = (sampleStatusTypes) => {
    if (componentMounted.current) {
      setSampleStatusTypes(sampleStatusTypes);
    }
  };

  const fetchTestSections = (response) => {
    setTestSections(response);
  };

  const submitOnSelect = (e) => {
    setNextPage(null);
    setPreviousPage(null);
    setPagination(false);
    var values = { unitType: e.target.value };
    handleSubmit(values);
  };

  useEffect(() => {
    componentMounted.current = true;
    let testId = new URLSearchParams(window.location.search).get(
      "selectedTest",
    );
    testId = testId ? testId : "";
    getFromOpenElisServer("/rest/test-list", (fetchedTests) => {
      let test = fetchedTests.find((test) => test.id === testId);
      let testLabel = test ? test.value : "";
      setDefaultTestId(testId);
      setDefaultTestLabel(testLabel);
      getTests(fetchedTests);
    });

    let sampleStatusId = new URLSearchParams(window.location.search).get(
      "selectedSampleStatus",
    );
    sampleStatusId = sampleStatusId ? sampleStatusId : "";
    getFromOpenElisServer(
      "/rest/sample-status-types",
      (fetchedSampleStatusTypes) => {
        let sampleStatus = fetchedSampleStatusTypes.find(
          (sampleStatus) => sampleStatus.id === sampleStatusId,
        );
        let sampleStatusLabel = sampleStatus ? sampleStatus.value : "";
        setDefaultSampleStatusId(sampleStatusId);
        setDefaultSampleStatusLabel(sampleStatusLabel);
        getSampleStatusTypes(fetchedSampleStatusTypes);
      },
    );

    let analysisStatusId = new URLSearchParams(window.location.search).get(
      "selectedAnalysisStatus",
    );
    analysisStatusId = analysisStatusId ? analysisStatusId : "";
    getFromOpenElisServer(
      "/rest/analysis-status-types",
      (fetchedAnalysisStatusTypes) => {
        let analysisStatus = fetchedAnalysisStatusTypes.find(
          (analysisStatus) => analysisStatus.id === analysisStatusId,
        );
        let analysisStatusLabel = analysisStatus ? analysisStatus.value : "";
        setDefaultAnalysisStatusId(analysisStatusId);
        setDefaultAnalysisStatusLabel(analysisStatusLabel);
        getAnalysisStatusTypes(fetchedAnalysisStatusTypes);
      },
    );

    let testSectionId = new URLSearchParams(window.location.search).get(
      "testSectionId",
    );
    testSectionId = testSectionId ? testSectionId : "";
    getFromOpenElisServer(
      "/rest/user-test-sections/" + Roles.RESULTS,
      (fetchedTestSections) => {
        let testSection = fetchedTestSections.find(
          (testSection) => testSection.id === testSectionId,
        );
        let testSectionLabel = testSection ? testSection.value : "";
        setDefaultTestSectionId(testSectionId);
        setDefaultTestSectionLabel(testSectionLabel);
        fetchTestSections(fetchedTestSections);
      },
    );
    if (testSectionId) {
      let values = { unitType: testSectionId };
      querySearch(values);
    }

    var displayFormType = "";
    var doRange = "";
    if (window.location.pathname == "/result") {
      displayFormType = new URLSearchParams(window.location.search).get("type");
      doRange = new URLSearchParams(window.location.search).get("doRange");
    } else if (window.location.pathname == "/LogbookResults") {
      displayFormType = "unit";
      doRange = "false";
    } else if (window.location.pathname == "/PatientResults") {
      displayFormType = "patient";
      doRange = "false";
    } else if (window.location.pathname == "/AccessionResults") {
      displayFormType = "order";
      doRange = "false";
    } else if (window.location.pathname == "/StatusResults") {
      displayFormType = "date";
      doRange = "false";
    } else if (window.location.pathname == "/RangeResults") {
      displayFormType = "range";
      doRange = "true";
    }
    setSearchBy({
      type: displayFormType,
      doRange: doRange,
    });
  }, []);

  useEffect(() => {
    let accessionNumber = new URLSearchParams(window.location.search).get(
      "accessionNumber",
    );
    let upperAccessionNumber = new URLSearchParams(window.location.search).get(
      "upperAccessionNumber",
    );
    if (accessionNumber) {
      let searchValues = {
        ...searchFormValues,
        accessionNumber: accessionNumber,
      };
      setSearchFormValues(searchValues);
      querySearch(searchValues);
    }
    if (accessionNumber || upperAccessionNumber) {
      let searchValues = {
        ...searchFormValues,
        accessionNumber: accessionNumber,
        endLabNo: upperAccessionNumber,
      };
      setSearchFormValues(searchValues);
      querySearch(searchValues);
    }
    let collectionDate = new URLSearchParams(window.location.search).get(
      "collectionDate",
    );
    let recievedDate = new URLSearchParams(window.location.search).get(
      "recievedDate",
    );
    let selectedTest = new URLSearchParams(window.location.search).get(
      "selectedTest",
    );
    let selectedSampleStatus = new URLSearchParams(window.location.search).get(
      "selectedSampleStatus",
    );
    let selectedAnalysisStatus = new URLSearchParams(
      window.location.search,
    ).get("selectedAnalysisStatus");

    if (
      collectionDate ||
      recievedDate ||
      selectedTest ||
      selectedSampleStatus ||
      selectedAnalysisStatus
    ) {
      let searchValues = {
        ...searchFormValues,
        collectionDate: collectionDate ? collectionDate : "",
        recievedDate: recievedDate ? recievedDate : "",
        testName: selectedTest ? selectedTest : "",
        sampleStatusType: selectedSampleStatus ? selectedSampleStatus : "",
        analysisStatus: selectedAnalysisStatus ? selectedAnalysisStatus : "",
      };
      setSearchFormValues(searchValues);
      querySearch(searchValues);
    }
    setNextPage(null);
    setPreviousPage(null);
    setPagination(false);
  }, [searchBy]);

  return (
    <>
      {notificationVisible === true ? <AlertDialog /> : ""}
      {loading && <Loading></Loading>}
      <Formik
        initialValues={searchFormValues}
        //validationSchema={}
        onSubmit={handleSubmit}
        onChange
        enableReinitialize={true}
      >
        {({
          values,
          //   errors,
          //   touched,
          handleChange,
          setFieldValue,
          //   handleBlur,
          handleSubmit,
        }) => (
          <Form
            onSubmit={handleSubmit}
            onChange={handleChange}
            //onBlur={handleBlur}
          >
            <Stack gap={2}>
              <Grid>
                <Column lg={16} md={8} sm={4}>
                  <h4>
                    <FormattedMessage id="label.button.search" />
                  </h4>
                </Column>
                {searchBy.type === "order" && (
                  <>
                    <Column lg={6} md={4} sm={4}>
                      <Field name="accessionNumber">
                        {({ field }) => (
                          <CustomLabNumberInput
                            placeholder="Enter Accession No."
                            name={field.name}
                            id={field.name}
                            data-cy="enterAccession"
                            value={values[field.name]}
                            labelText={
                              <FormattedMessage id="search.label.accession" />
                            }
                            onChange={(e, rawValue) => {
                              setFieldValue(field.name, rawValue);
                            }}
                          />
                        )}
                      </Field>
                    </Column>
                    <Column lg={10} />
                  </>
                )}

                {searchBy.type === "range" && (
                  <>
                    <Column lg={6} sm={4}>
                      <Field name="startLabNo">
                        {({ field }) => (
                          <CustomLabNumberInput
                            placeholder="Enter Accession No."
                            name={field.name}
                            id={field.name}
                            data-cy="startAccession"
                            value={values[field.name]}
                            labelText={
                              <FormattedMessage id="search.label.fromaccession" />
                            }
                            onChange={(e, rawValue) => {
                              setFieldValue(field.name, rawValue);
                            }}
                          />
                        )}
                      </Field>
                    </Column>
                    <Column lg={6} sm={4}>
                      <Field name="endLabNo">
                        {({ field }) => (
                          <CustomLabNumberInput
                            placeholder="Enter Accession No."
                            name={field.name}
                            id={field.name}
                            data-cy="endAccession"
                            value={values[field.name]}
                            labelText={
                              <FormattedMessage id="search.label.toaccession" />
                            }
                            onChange={(e, rawValue) => {
                              setFieldValue(field.name, rawValue);
                            }}
                          />
                        )}
                      </Field>
                    </Column>
                    <Column lg={4} />
                  </>
                )}

                {searchBy.type === "date" && (
                  <>
                    <Column lg={3} md={4} sm={4}>
                      <Field name="collectionDate">
                        {({ field, form }) => (
                          <CustomDatePicker
                            id={field.name}
                            labelText={intl.formatMessage({
                              id: "search.label.collectiondate",
                            })}
                            value={values[field.name]}
                            onChange={(date) =>
                              form.setFieldValue(field.name, date)
                            }
                            name={field.name}
                          />
                        )}
                      </Field>
                    </Column>
                    <Column lg={3} md={4} sm={4}>
                      <Field name="recievedDate">
                        {({ field, form }) => (
                          <CustomDatePicker
                            id={field.name}
                            labelText={intl.formatMessage({
                              id: "search.label.recieveddate",
                            })}
                            value={values[field.name]}
                            onChange={(date) =>
                              form.setFieldValue(field.name, date)
                            }
                            name={field.name}
                          />
                        )}
                      </Field>
                    </Column>
                    <Column lg={3} md={4} sm={4}>
                      <Field name="testName">
                        {({ field }) => (
                          <Select
                            labelText={
                              <FormattedMessage id="search.label.test" />
                            }
                            name={field.name}
                            id={field.name}
                          >
                            <SelectItem
                              text={defaultTestLabel}
                              value={defaultTestId}
                            />
                            {tests
                              .filter((item) => item.id !== defaultTestId)
                              .map((test, index) => {
                                return (
                                  <SelectItem
                                    key={index}
                                    text={test.value}
                                    value={test.id}
                                  />
                                );
                              })}
                          </Select>
                        )}
                      </Field>
                    </Column>
                    <Column lg={3} md={4} sm={4}>
                      <Field name="analysisStatus">
                        {({ field }) => (
                          <Select
                            labelText={
                              <FormattedMessage id="search.label.analysis" />
                            }
                            name={field.name}
                            id={field.name}
                          >
                            <SelectItem
                              text={defaultAnalysisStatusLabel}
                              value={defaultAnalysisStatusId}
                            />
                            {analysisStatusTypes
                              .filter(
                                (item) => item.id !== defaultAnalysisStatusId,
                              )
                              .map((test, index) => {
                                return (
                                  <SelectItem
                                    key={index}
                                    text={test.value}
                                    value={test.id}
                                  />
                                );
                              })}
                          </Select>
                        )}
                      </Field>
                    </Column>
                    <Column lg={3} md={4} sm={4}>
                      <Field name="sampleStatusType">
                        {({ field }) => (
                          <Select
                            labelText={
                              <FormattedMessage id="search.label.sample" />
                            }
                            name={field.name}
                            id={field.name}
                          >
                            <SelectItem
                              text={defaultSampleStatusLabel}
                              value={defaultSampleStatusId}
                            />
                            {sampleStatusTypes
                              .filter(
                                (item) => item.id !== defaultSampleStatusId,
                              )
                              .map((test, index) => {
                                return (
                                  <SelectItem
                                    key={index}
                                    text={test.value}
                                    value={test.id}
                                  />
                                );
                              })}
                          </Select>
                        )}
                      </Field>
                    </Column>
                    <Column lg={1} />
                  </>
                )}

                {searchBy.type !== "patient" && searchBy.type !== "unit" && (
                  <Column lg={16} md={8} sm={4}>
                    <Button
                      style={{ marginTop: "16px" }}
                      type="submit"
                      id="searchResults"
                    >
                      <FormattedMessage id="label.button.search" />
                    </Button>
                  </Column>
                )}
              </Grid>
            </Stack>
          </Form>
        )}
      </Formik>
      {searchBy.type === "patient" && (
        <Grid>
          <Column lg={16} md={8} sm={4}>
            <SearchPatientForm
              getSelectedPatient={getSelectedPatient}
            ></SearchPatientForm>
          </Column>
        </Grid>
      )}

      {searchBy.type === "unit" && (
        <>
          <Grid>
            <Column lg={6} md={4} sm={4}>
              <Select
                labelText={intl.formatMessage({ id: "search.label.testunit" })}
                name="unitType"
                id="unitType"
                onChange={submitOnSelect}
              >
                <SelectItem
                  text={defaultTestSectionLabel}
                  value={defaultTestSectionId}
                />
                {testSections
                  .filter((item) => item.id !== defaultTestSectionId)
                  .map((test, index) => {
                    return (
                      <SelectItem
                        key={index}
                        text={test.value}
                        value={test.id}
                      />
                    );
                  })}
              </Select>
            </Column>
            <Column lg={10} />
          </Grid>
        </>
      )}

      {searchBy.type === "ReferredOutTests" && <ReferredOutTests />}

      <>
        {pagination && (
          <Grid>
            <Column lg={16}>
              {" "}
              <br /> <br />
            </Column>
            <Column lg={14} />
            <Column
              lg={2}
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                gap: "10px",
                width: "110%",
              }}
            >
              <Link>
                {currentApiPage} / {totalApiPages}
              </Link>
              <div style={{ display: "flex", gap: "10px" }}>
                <Button
                  hasIconOnly
                  id="loadpreviousresults"
                  onClick={loadPreviousResultsPage}
                  disabled={previousPage != null ? false : true}
                  renderIcon={ArrowLeft}
                  iconDescription="previous"
                ></Button>
                <Button
                  hasIconOnly
                  id="loadnextresults"
                  onClick={loadNextResultsPage}
                  disabled={nextPage != null ? false : true}
                  renderIcon={ArrowRight}
                  iconDescription="next"
                ></Button>
              </div>
            </Column>
          </Grid>
        )}
      </>
    </>
  );
}

export function SearchResults(props) {
  const { notificationVisible, addNotification, setNotificationVisible } =
    useContext(NotificationContext);
  const { configurationProperties } = useContext(ConfigurationContext);

  const intl = useIntl();

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(100);
  const [acceptAsIs, setAcceptAsIs] = useState([]);
  const [referalOrganizations, setReferalOrganizations] = useState([]);
  const [referralReasons, setReferralReasons] = useState([]);
  const [rejectReasons, setRejectReasons] = useState([]);
  const [rejectedItems, setRejectedItems] = useState({});
  const [validationState, setValidationState] = useState({});
  const saveStatus = "";
  const [referTest, setReferTest] = useState({});
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [sampleLocations, setSampleLocations] = useState({}); // Track location by analysisId

  const componentMounted = useRef(false);

  useEffect(() => {
    componentMounted.current = true;

    getFromOpenElisServer(
      "/rest/displayList/REFERRAL_ORGANIZATIONS",
      loadReferalOrganizations,
    );
    getFromOpenElisServer(
      "/rest/displayList/REFERRAL_REASONS",
      loadReferalReasons,
    );
    getFromOpenElisServer(
      "/rest/displayList/REJECTION_REASONS",
      loadRejectReasons,
    );
    if (props.results.testResult.length > 0) {
      var defaultRejectedItems = {};
      props.results.testResult.forEach((result) => {
        defaultRejectedItems[result.id] = false;
      });
      setRejectedItems(defaultRejectedItems);
    }
    return () => {
      componentMounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (props.results.testResult) {
      let newValidationState = { ...validationState };
      props.results.testResult.forEach((row) => {
        if (row.resultType === "N") {
          let value = row.resultValue;
          if (!value) {
            return;
          }
          let validation = (newValidationState[row.id] = validateNumericResults(
            value,
            row,
          ));

          row.resultValue = validation.newValue;
          validation.style = {
            ...validation?.style,
            borderColor: validation.isCritical
              ? "orange"
              : validation.isInvalid
                ? "red"
                : "",
            background: validation.outsideValid
              ? "#ffa0a0"
              : validation.outsideNormal
                ? "#ffffa0"
                : "var(--cds-field)",
          };
        }
      });
      setValidationState(newValidationState);
    }
  }, [props.results]);

  const loadReferalOrganizations = (values) => {
    if (componentMounted.current) {
      setReferalOrganizations(values);
    }
  };

  const loadReferalReasons = (values) => {
    if (componentMounted.current) {
      setReferralReasons(values);
    }
  };

  const loadRejectReasons = (values) => {
    if (componentMounted.current) {
      setRejectReasons(values);
    }
  };

  const isResultsReferralEnabled =
    configurationProperties.RESULTS_REFERRAL_ENABLED === "true";
  const showResultLevelFileUpload =
    configurationProperties.ENABLE_RESULT_LEVEL_FILE_UPLOAD !== "false";
  const showStorageLocationOnResultEntry =
    configurationProperties.showStorageLocationOnResultEntry !== "false";
  const hasReferralSelectionData =
    referalOrganizations.length > 0 && referralReasons.length > 0;
  const showReferralControls =
    isResultsReferralEnabled && hasReferralSelectionData;

  const downloadFile = (fileName, content, fileType) => {
    var win = window.open();
    win.document.write(
      '<iframe src="' +
        fileType +
        ";base64," +
        content +
        '" frameborder="0" style="border:0; top:0px; left:0px; bottom:0px; right:0px; width:100%; height:100%;" allowfullscreen></iframe>',
    );
  };

  const addRejectResult = () => {
    const resultColumn = {
      id: "reject",
      name: intl.formatMessage({ id: "column.name.reject" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "8rem",
    };

    if (configurationProperties.allowResultRejection == "true") {
      if (columns) {
        const notesIndex = columns.findIndex((column) => column.id === "notes");
        const insertAt = notesIndex >= 0 ? notesIndex : columns.length;
        columns = [
          ...columns.slice(0, insertAt),
          resultColumn,
          ...columns.slice(insertAt),
        ];
      }
    }
  };

  var columns = [
    {
      id: "sampleInfo",
      name: intl.formatMessage({ id: "column.name.sampleInfo" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      sortable: true,
      selector: (row) => row.accessionNumber,
      width: "16rem",
    },
    {
      id: "testDate",
      name: intl.formatMessage({ id: "column.name.testDate" }),
      selector: (row) => row.testDate,
      sortable: true,
      width: "7rem",
    },

    {
      id: "analyzerResult",
      name: intl.formatMessage({ id: "column.name.analyzerResult" }),
      selector: (row) => row.analysisMethod,
      sortable: true,
      width: "7rem",
    },
    {
      id: "testName",
      name: intl.formatMessage({ id: "column.name.testName" }),
      selector: (row) => row.testName,
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      sortable: true,
      width: "18rem",
    },
    {
      id: "normalRange",
      name: intl.formatMessage({ id: "column.name.normalRange" }),
      selector: (row) => row.normalRange,
      sortable: true,
      width: "8rem",
    },
    {
      id: "accept",
      name: intl.formatMessage({ id: "column.name.accept" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "5rem",
    },
    {
      width: "12rem",
    },
    {
      id: "parentSampleUsage",
      name: intl.formatMessage({
        id: "result.entry.parentSampleUsage",
        defaultMessage: "Cantidad muestra usada",
      }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "12rem",
    },
    {
      id: "sampleUsage",
      name: intl.formatMessage({
        id: "result.entry.sampleUsage",
        defaultMessage: "Cantidad usada de la extracción",
      }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "12rem",
    },
    {
      id: "currentResult",
      name: intl.formatMessage({ id: "column.name.currentResult" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "10rem",
    },
    {
      id: "notes",
      name: intl.formatMessage({ id: "column.name.notes" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "30rem",
    },
  ];

  // Display-only hide: keep logic/data in code but do not render these columns.
  const hiddenColumnIds = new Set([
    "analyzerResult",
    "normalRange",
    "currentResult",
    "accept",
  ]);
  const visibleColumns = columns.filter(
    (column) => !hiddenColumnIds.has(column.id),
  );

  columns = columns.filter((column) => column?.id !== "result");

  const renderCell = (row, index, column, id) => {
    const fullTestName = row.testName;
    const splitIndex = fullTestName.lastIndexOf("(");
    const testName = fullTestName.substring(0, splitIndex);
    const sampleType = fullTestName.substring(splitIndex);

    console.debug("renderCell: index: " + index + ", id: " + id);
    switch (column.id) {
      case "sampleInfo": {
        const sampleCode = row.cugCode || row.accessionNumber;
        // return <input id={"results_" + id} type="text" size="6"></input>
        return (
          <>
            <div>
              <Button
                onClick={async () => {
                  if ("clipboard" in navigator) {
                    return await navigator.clipboard.writeText(sampleCode);
                  } else {
                    return document.execCommand("copy", true, sampleCode);
                  }
                }}
                kind="ghost"
                iconDescription={intl.formatMessage({
                  id: "instructions.copy.labnum",
                })}
                hasIconOnly
                renderIcon={Copy}
              />
            </div>
            <div className="sampleInfo">
              <br></br>
              {sampleCode}
              <br></br>
              {row.receivedDate || ""}
              <br></br>
              <br></br>
            </div>
            {row.nonconforming && (
              <picture>
                <img
                  src={config.serverBaseUrl + "/images/nonconforming.gif"}
                  alt="nonconforming"
                  width="20"
                  height="15"
                />
              </picture>
            )}
          </>
        );
      }
      case "testName":
        return (
          <div className="sampleInfo">
            <br></br>
            {testName}
            <br></br>
            {sampleType}
            {row.dependentChild && (
              <>
                <br></br>
                <small>
                  {intl.formatMessage(
                    {
                      id: "result.entry.dependentChild",
                      defaultMessage: "Child of {parentTest}",
                    },
                    { parentTest: row.dependencyParentTestName || "Parent" },
                  )}
                </small>
              </>
            )}
          </div>
        );

      case "accept":
        return (
          <>
            <Field name="forceTechApproval">
              {() => (
                <Checkbox
                  data-cy="checkTestResult"
                  id={"testResult" + row.id + ".forceTechApproval"}
                  name={"testResult[" + row.id + "].forceTechApproval"}
                  labelText=""
                  //defaultChecked={acceptAsIs}
                  onChange={(e) => handleAcceptAsIsChange(e, row.id)}
                />
              )}
            </Field>
          </>
        );

      case "reject":
        return (
          <div>
            <Field name="reject">
              {() => (
                <Checkbox
                  id={"testResult" + row.id + ".rejected"}
                  name={"testResult[" + row.id + "].rejected"}
                  labelText=""
                  onChange={(e) => handleRejectCheckBoxChange(e, row.id)}
                />
              )}
            </Field>
            <br></br>
            {rejectedItems[row.id] == true && (
              <Select
                id={"rejectReasonId" + row.id}
                name={"testResult[" + row.id + "].rejectReasonId"}
                //noLabel={true}
                labelText={"Reason"}
                onChange={(e) => handleChange(e, row.id)}
              >
                {/* {...updateShadowResult(e, this, param.rowId)} */}
                <SelectItem text="" value="" />
                {rejectReasons.map((reason, reason_index) => (
                  <SelectItem
                    text={reason.value}
                    value={reason.id}
                    key={reason_index}
                  />
                ))}
              </Select>
            )}
          </div>
        );

      case "notes":
        return (
          <>
            <div className="note">
              <TextArea
                id={"testResult" + row.id + ".note"}
                name={"testResult[" + row.id + "].note"}
                //value={props.results.testResult[row.id]?.pastNotes}
                disabled={false}
                type="text"
                labelText=""
                rows={1}
                onChange={(e) => handleChange(e, row.id)}
              ></TextArea>
              <div
                className="note"
                dangerouslySetInnerHTML={{ __html: row.pastNotes }}
              />
            </div>
          </>
        );

      case "result":
        switch (row.resultType) {
          case "D":
            return (
              <Select
                className="result"
                id={"resultValue" + row.id}
                name={"testResult[" + row.id + "].resultValue"}
                noLabel={true}
                onChange={(e) => validateResults(e, row.id)}
                value={row.resultValue}
              >
                {/* {...updateShadowResult(e, this, param.rowId)} */}
                <SelectItem text="" value="" />
                {row.dictionaryResults.map(
                  (dictionaryResult, dictionaryResult_index) => (
                    <SelectItem
                      text={dictionaryResult.value}
                      value={dictionaryResult.id}
                      key={dictionaryResult_index}
                    />
                  ),
                )}
              </Select>
            );

          case "M":
            return (
              <ResultMultiSelect
                id={`multiResultValue${row.id}`}
                name={`testResult[${row.id}].multiSelectResultValues`}
                dictionaryValues={row.dictionaryResults}
                value={row.multiSelectResultValues}
                onChange={(e) => handleChange(e, row.id)}
              />
            );

          case "C":
            return (
              <CascadingMultiSelect
                id={`multiResult${row.id}`}
                name={`testResult[${row.id}].multiSelectResultValues`}
                dictionaryValues={row.dictionaryResults}
                value={row.multiSelectResultValues}
                onChange={(e) => handleChange(e, row.id)}
              />
            );

          case "N":
            return (
              <InlineResultEditor
                inputId={"ResultValue" + row.id}
                fieldName={"testResult[" + row.id + "].resultValue"}
                fieldType={row.resultType}
                value={row.resultValue}
                style={validationState[row.id]?.style}
                onCommit={(nextValue) =>
                  commitRowFieldValue(
                    row.id,
                    "testResult[" + row.id + "].resultValue",
                    nextValue,
                  )
                }
                onPostCommit={(nextValue) => {
                  const validation = validateNumericResults(nextValue, row);
                  setValidationState((previousState) => ({
                    ...previousState,
                    [row.id]: {
                      ...validation,
                      style: {
                        ...validation?.style,
                        borderColor: validation.isCritical
                          ? "orange"
                          : validation.isInvalid
                            ? "red"
                            : "",
                        background: validation.outsideValid
                          ? "#ffa0a0"
                          : validation.outsideNormal
                            ? "#ffffa0"
                            : "var(--cds-field)",
                      },
                    },
                  }));
                  if (
                    validation.isInvalid &&
                    configurationProperties.ALERT_FOR_INVALID_RESULTS
                  ) {
                    addNotification({
                      title: intl.formatMessage({ id: "notification.title" }),
                      message:
                        intl.formatMessage({
                          id: "result.outOfValidRange.msg",
                        }) +
                        " " +
                        row.testName +
                        " : " +
                        nextValue,
                      kind: NotificationKinds.error,
                    });
                    setNotificationVisible(true);
                  }
                }}
              />
            );

          case "R":
            return (
              <InlineResultEditor
                inputId={"ResultValue" + row.id}
                fieldName={"testResult[" + row.id + "].resultValue"}
                fieldType={row.resultType}
                value={row.resultValue}
                onCommit={(nextValue) =>
                  commitRowFieldValue(
                    row.id,
                    "testResult[" + row.id + "].resultValue",
                    nextValue,
                  )
                }
              />
            );

          case "A":
            return (
              <InlineResultEditor
                inputId={"ResultValue" + row.id}
                fieldName={"testResult[" + row.id + "].resultValue"}
                fieldType={row.resultType}
                value={row.resultValue}
                onCommit={(nextValue) =>
                  commitRowFieldValue(
                    row.id,
                    "testResult[" + row.id + "].resultValue",
                    nextValue,
                  )
                }
              />
            );

          default:
            return row.resultValue;
        }

      case "currentResult":
        switch (row.resultType) {
          case "M":
          case "C":
          case "D":
            return (
              <>
                {
                  row.dictionaryResults.find(
                    (result) => result.id == row.shadowResultValue,
                  )?.value
                }
              </>
            );

          default:
            return row.shadowResultValue;
        }
      case "sampleUsage":
        if (!row.dependentChild) {
          return <></>;
        }

        if (row.blockTubeUsageEnabled === true) {
          return (
            <Stack gap={2}>
              <small>
                {intl.formatMessage(
                  {
                    id: "result.entry.remainingQuantity",
                    defaultMessage: "Restantes: {quantity}",
                  },
                  { quantity: row.sampleRemainingQuantity || "-" },
                )}
              </small>
              <TextInput
                id={"sampleUsageQuantity" + row.id}
                name={"testResult[" + row.id + "].sampleUsageQuantity"}
                labelText={intl.formatMessage({
                  id: "result.entry.totalUsage.label",
                  defaultMessage: "Cantidad total usada",
                })}
                type="number"
                step="0.001"
                min="0"
                value={row.sampleUsageQuantity || ""}
                disabled
              />
            </Stack>
          );
        }

        return (
          <Stack gap={2}>
            {row.parentTubeSelectionEnabled === true && (
              <Select
                id={"parentUsageBlockName" + row.id}
                name={"testResult[" + row.id + "].parentUsageBlockName"}
                labelText={intl.formatMessage({
                  id: "result.entry.parentTube.label",
                  defaultMessage: "Tube",
                })}
                value={row.parentUsageBlockName || ""}
                disabled={row.sampleUsageLocked === true}
                onChange={(event) =>
                  handleParentTubeSelectionChange(row.id, event.target.value)
                }
              >
                <SelectItem text="" value="" />
                {Object.entries(row.parentTubeOptions || {}).map(
                  ([blockName, label]) => (
                    <SelectItem
                      key={`parent-tube-option-${row.id}-${blockName}`}
                      value={blockName}
                      text={label}
                    />
                  ),
                )}
              </Select>
            )}
            <small>
              {intl.formatMessage(
                {
                  id: "result.entry.remainingQuantity",
                  defaultMessage: "Restantes: {quantity}",
                },
                { quantity: row.sampleRemainingQuantity || "-" },
              )}
            </small>
            <TextInput
              id={"sampleUsageQuantity" + row.id}
              name={"testResult[" + row.id + "].sampleUsageQuantity"}
              labelText=""
              type="number"
              step="0.001"
              min="0"
              value={row.sampleUsageQuantity || ""}
              disabled={row.sampleUsageLocked === true}
              onChange={(e) => handleChange(e, row.id)}
            />
          </Stack>
        );
      case "parentSampleUsage":
        if (!row.parentSampleUsageEnabled || row.dependentChild) {
          return <></>;
        }

        return (
          <Stack gap={2}>
            <small>
              {intl.formatMessage(
                {
                  id: "result.entry.remainingQuantity",
                  defaultMessage: "Restantes: {quantity}",
                },
                { quantity: row.parentSampleRemainingQuantity || "-" },
              )}
            </small>
            <TextInput
              id={"parentSampleUsageQuantity" + row.id}
              name={"testResult[" + row.id + "].parentSampleUsageQuantity"}
              labelText=""
              type="number"
              step="0.001"
              min="0"
              value={row.parentSampleUsageQuantity || ""}
              disabled={row.parentSampleUsageLocked === true}
              onChange={(e) => handleChange(e, row.id)}
            />
          </Stack>
        );
      default:
        return;
    }
  };

  // Fetch location for a SampleItem when analysis row is expanded
  const fetchSampleLocation = (analysisId, sampleItemId) => {
    // Skip if already fetched or no sampleItemId
    if (!sampleItemId || sampleLocations[analysisId]) {
      return;
    }

    getFromOpenElisServer(
      `/rest/storage/sample-items/${encodeURIComponent(sampleItemId)}`,
      (response) => {
        if (response) {
          const locationPath =
            response.hierarchicalPath || response.location || "";
          setSampleLocations((prev) => ({
            ...prev,
            [analysisId]: {
              locationPath,
              sampleItemId: sampleItemId,
              sampleItemExternalId: response.sampleItemExternalId || null,
              sampleAccessionNumber: response.sampleAccessionNumber || "",
            },
          }));
        }
      },
      (error) => {
        // SampleItem may not have location assigned yet
        console.debug("No location found for SampleItem:", sampleItemId);
        // Store empty location to prevent repeated calls
        setSampleLocations((prev) => ({
          ...prev,
          [analysisId]: { locationPath: "", sampleItemId: sampleItemId },
        }));
      },
    );
  };

  // Handle location assignment
  // Uses SampleItem ID from stored location data or from locationData
  const handleLocationAssignment = async (
    locationData,
    analysisId,
    sampleItemId,
  ) => {
    // locationData format: { sample, newLocation, reason?, conditionNotes?, positionCoordinate? }
    const newLocation = locationData?.newLocation || locationData;

    // Use sampleItemId from parameter or stored location data
    const actualSampleItemId =
      locationData?.sample?.sampleItemId ||
      locationData?.sample?.id ||
      sampleItemId ||
      (sampleLocations[analysisId] &&
      typeof sampleLocations[analysisId] === "object"
        ? sampleLocations[analysisId].sampleItemId
        : null);

    if (!actualSampleItemId || !newLocation) {
      console.error("Missing SampleItem ID or location for assignment", {
        sampleItemId: actualSampleItemId,
        newLocation,
      });
      return;
    }

    try {
      // Call assignment API with SampleItem ID
      const assignmentData = {
        sampleItemId: actualSampleItemId,
        locationId:
          newLocation.rack?.id ||
          newLocation.shelf?.id ||
          newLocation.device?.id,
        locationType: newLocation.rack
          ? "rack"
          : newLocation.shelf
            ? "shelf"
            : "device",
        positionCoordinate:
          locationData.positionCoordinate ||
          newLocation.position?.coordinate ||
          "",
        notes: locationData.conditionNotes || "", // Assignment form uses "notes" field
      };

      postToOpenElisServerJsonResponse(
        "/rest/storage/sample-items/assign",
        JSON.stringify(assignmentData),
        (response) => {
          if (response && response.success) {
            // Update local state with location path
            const locationPath = response.hierarchicalPath || "";
            const storedData = sampleLocations[analysisId];
            setSampleLocations((prev) => ({
              ...prev,
              [analysisId]:
                storedData && typeof storedData === "object"
                  ? { ...storedData, locationPath }
                  : locationPath,
            }));
            addNotification({
              title: intl.formatMessage({ id: "notification.title" }),
              message: intl.formatMessage({
                id: "storage.location.assigned.success",
                defaultMessage: "Location assigned successfully",
              }),
              kind: NotificationKinds.success,
            });
            setNotificationVisible(true);
          }
        },
        (error) => {
          addNotification({
            title: intl.formatMessage({ id: "notification.title" }),
            message: intl.formatMessage({
              id: "storage.location.assigned.error",
              defaultMessage: "Failed to assign location",
            }),
            kind: NotificationKinds.error,
          });
          setNotificationVisible(true);
        },
      );
    } catch (error) {
      console.error("Error assigning location:", error);
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "storage.location.assigned.error",
          defaultMessage: "Failed to assign location",
        }),
        kind: NotificationKinds.error,
      });
      setNotificationVisible(true);
    }
  };

  const handleAdditionalFieldChange = (
    rowId,
    fieldKey,
    fieldType,
    nextValue,
  ) => {
    const form = {
      ...props.results,
      testResult: [...props.results.testResult],
    };
    const row = { ...(form.testResult[rowId] || {}) };
    const additionalFieldValues = { ...(row.additionalFieldValues || {}) };

    if (fieldType === "MULTISELECT" && Array.isArray(nextValue)) {
      additionalFieldValues[fieldKey] = nextValue.join(",");
    } else if (fieldType === "BOOLEAN") {
      additionalFieldValues[fieldKey] =
        nextValue === true || nextValue === "true" ? "true" : "false";
    } else {
      additionalFieldValues[fieldKey] = nextValue == null ? "" : `${nextValue}`;
    }

    row.additionalFieldValues = additionalFieldValues;
    row.additionalFieldShadowValues = { ...additionalFieldValues };
    if (fieldType === "NUMBER") {
      const fieldDefinitions = Array.isArray(row.additionalFieldDefinitions)
        ? row.additionalFieldDefinitions
        : [];
      const changedDefinition = fieldDefinitions.find(
        (definition) => definition?.fieldKey === fieldKey,
      );
      if (isTubeSelectorFieldDefinition(changedDefinition)) {
        const selectedTubeCount = Number.parseInt(nextValue, 10);
        fieldDefinitions.forEach((definition) => {
          const activationCount = getTubeActivationCount(definition);
          if (
            activationCount > 0 &&
            (!Number.isFinite(selectedTubeCount) ||
              selectedTubeCount <= 0 ||
              activationCount > selectedTubeCount)
          ) {
            delete additionalFieldValues[definition.fieldKey];
          }
        });
        row.additionalFieldValues = additionalFieldValues;
        row.additionalFieldShadowValues = { ...additionalFieldValues };
      }
    }
    row.isModified = "true";
    form.testResult[rowId] = row;
    props.setResultForm(form);
  };

  const handleParentTubeSelectionChange = (rowId, nextBlockName) => {
    const form = {
      ...props.results,
      testResult: [...props.results.testResult],
    };
    const row = { ...(form.testResult[rowId] || {}) };
    row.parentUsageBlockName = nextBlockName || "";
    const remainingByBlock = row.parentTubeRemainingQuantities || {};
    row.sampleRemainingQuantity =
      nextBlockName &&
      Object.prototype.hasOwnProperty.call(remainingByBlock, nextBlockName)
        ? remainingByBlock[nextBlockName]
        : getTubeUsageTotalRemaining(row);
    row.isModified = "true";
    form.testResult[rowId] = row;
    props.setResultForm(form);
  };

  const recalculateBlockTubeUsageState = (row) => {
    const blockSampleUsages = Array.isArray(row?.blockSampleUsages)
      ? row.blockSampleUsages.map((blockUsage) => ({ ...blockUsage }))
      : [];
    const remainingByTube = row?.parentTubeRemainingQuantities || {};

    const totalBaseRemaining = Object.values(remainingByTube).reduce(
      (sum, rawValue) => {
        const parsed = Number(rawValue);
        return Number.isFinite(parsed) ? sum + parsed : sum;
      },
      0,
    );

    const usedByTube = {};
    let totalUsed = 0;
    blockSampleUsages.forEach((blockUsage) => {
      const selectedTube = String(blockUsage?.parentTubeBlockName || "").trim();
      const parsedUsage = Number(blockUsage?.usedQuantity);
      if (!selectedTube || !Number.isFinite(parsedUsage) || parsedUsage <= 0) {
        return;
      }
      usedByTube[selectedTube] = (usedByTube[selectedTube] || 0) + parsedUsage;
      totalUsed += parsedUsage;
    });

    blockSampleUsages.forEach((blockUsage) => {
      const selectedTube = String(blockUsage?.parentTubeBlockName || "").trim();
      if (!selectedTube) {
        blockUsage.remainingQuantity = "";
        return;
      }
      const baseRemaining = Number(remainingByTube[selectedTube]);
      if (!Number.isFinite(baseRemaining)) {
        blockUsage.remainingQuantity = "";
        return;
      }
      const ownUsage = Number(blockUsage?.usedQuantity);
      const otherUsage = (usedByTube[selectedTube] || 0) -
        (Number.isFinite(ownUsage) && ownUsage > 0 ? ownUsage : 0);
      const effectiveRemaining = Math.max(0, baseRemaining - otherUsage);
      blockUsage.remainingQuantity = effectiveRemaining.toFixed(3);
    });

    row.blockSampleUsages = blockSampleUsages;
    row.sampleUsageQuantity =
      totalUsed > 0 ? totalUsed.toFixed(3).replace(/\.?0+$/, "") : "";
    const effectiveTotalRemaining = Math.max(0, totalBaseRemaining - totalUsed);
    row.sampleRemainingQuantity =
      totalBaseRemaining > 0 ? effectiveTotalRemaining.toFixed(3) : "";
    return row;
  };

  const updateBlockTubeUsageRow = (rowId, updater) => {
    const form = {
      ...props.results,
      testResult: [...props.results.testResult],
    };
    const row = { ...(form.testResult[rowId] || {}) };
    row.blockSampleUsages = Array.isArray(row.blockSampleUsages)
      ? row.blockSampleUsages.map((blockUsage) => ({ ...blockUsage }))
      : [];
    updater(row);
    recalculateBlockTubeUsageState(row);
    row.isModified = "true";
    form.testResult[rowId] = row;
    props.setResultForm(form);
  };

  const handleBlockTubeSelectionChange = (
    rowId,
    childBlockName,
    nextTubeBlockName,
  ) => {
    updateBlockTubeUsageRow(rowId, (row) => {
      const nextUsages = Array.isArray(row.blockSampleUsages)
        ? [...row.blockSampleUsages]
        : [];
      const existingIndex = nextUsages.findIndex(
        (blockUsage) => blockUsage?.childBlockName === childBlockName,
      );
      const existing =
        existingIndex >= 0
          ? { ...nextUsages[existingIndex] }
          : { childBlockName, usedQuantity: "", remainingQuantity: "" };
      existing.parentTubeBlockName = nextTubeBlockName || "";
      if (existingIndex >= 0) {
        nextUsages[existingIndex] = existing;
      } else {
        nextUsages.push(existing);
      }
      row.blockSampleUsages = nextUsages;
    });
  };

  const handleBlockTubeUsageQuantityChange = (
    rowId,
    childBlockName,
    nextValue,
  ) => {
    updateBlockTubeUsageRow(rowId, (row) => {
      const nextUsages = Array.isArray(row.blockSampleUsages)
        ? [...row.blockSampleUsages]
        : [];
      const existingIndex = nextUsages.findIndex(
        (blockUsage) => blockUsage?.childBlockName === childBlockName,
      );
      const existing =
        existingIndex >= 0
          ? { ...nextUsages[existingIndex] }
          : {
              childBlockName,
              parentTubeBlockName: "",
              remainingQuantity: "",
            };
      existing.usedQuantity = nextValue == null ? "" : `${nextValue}`;
      if (existingIndex >= 0) {
        nextUsages[existingIndex] = existing;
      } else {
        nextUsages.push(existing);
      }
      row.blockSampleUsages = nextUsages;
    });
  };

  const renderBlockTubeUsageControl = (data, blockTitle) => {
    if (!data?.blockTubeUsageEnabled) {
      return null;
    }
    const configuredBlocks = getConfiguredChildTubeUsageBlocks(data, intl);
    if (!configuredBlocks.includes(normalizeBlockIdentifier(blockTitle))) {
      return null;
    }
    const blockUsage = Array.isArray(data.blockSampleUsages)
      ? data.blockSampleUsages.find(
          (usage) =>
            normalizeBlockIdentifier(usage?.childBlockName) ===
            normalizeBlockIdentifier(blockTitle),
        )
      : null;
    const resolvedBlockUsage = blockUsage || {
      childBlockName: blockTitle,
      parentTubeBlockName: "",
      usedQuantity: "",
      remainingQuantity: "",
      locked: false,
    };

    return (
      <Column
        lg={4}
        md={4}
        sm={4}
        key={`tube-usage-${data.id}-${blockTitle}`}
      >
        <Stack gap={2}>
          <Select
            id={`block-parent-tube-${data.id}-${blockTitle}`}
            labelText={intl.formatMessage({
              id: "result.entry.parentTube.label",
              defaultMessage: "Tube",
            })}
            value={resolvedBlockUsage.parentTubeBlockName || ""}
            disabled={resolvedBlockUsage.locked === true}
            onChange={(event) =>
              handleBlockTubeSelectionChange(
                data.id,
                blockTitle,
                event.target.value,
              )
            }
          >
            <SelectItem text="" value="" />
            {Object.entries(data.parentTubeOptions || {}).map(
              ([blockName, label]) => (
                <SelectItem
                  key={`block-parent-tube-option-${data.id}-${blockTitle}-${blockName}`}
                  value={blockName}
                  text={label}
                />
              ),
            )}
          </Select>
          <small>
            {intl.formatMessage(
              {
                id: "result.entry.remainingQuantity",
                defaultMessage: "Restantes: {quantity}",
              },
              { quantity: resolvedBlockUsage.remainingQuantity || "-" },
            )}
          </small>
          <TextInput
            id={`block-sample-usage-${data.id}-${blockTitle}`}
            labelText={intl.formatMessage({
              id: "result.entry.sampleUsage.label",
              defaultMessage: "Cantidad usada",
            })}
            type="number"
            step="0.001"
            min="0"
            value={resolvedBlockUsage.usedQuantity || ""}
            disabled={resolvedBlockUsage.locked === true}
            onChange={(event) =>
              handleBlockTubeUsageQuantityChange(
                data.id,
                blockTitle,
                event.target.value,
              )
            }
          />
        </Stack>
      </Column>
    );
  };

  const renderAdditionalFieldInput = (data, fieldDefinition) => {
    const fieldType = fieldDefinition?.fieldType || "TEXT";
    const fieldKey = fieldDefinition?.fieldKey;
    const activeOptions = Array.isArray(fieldDefinition?.options)
      ? fieldDefinition.options.filter((option) => option?.active !== false)
      : [];
    const fieldValue =
      data?.additionalFieldValues &&
      Object.prototype.hasOwnProperty.call(data.additionalFieldValues, fieldKey)
        ? data.additionalFieldValues[fieldKey]
        : "";
    const fieldLabel = fieldDefinition?.displayName || fieldKey;
    const fieldMetadata = parseAdditionalFieldMetadata(fieldDefinition);
    const inputId = `additional-field-${data.id}-${fieldKey}`;

    if (!fieldKey) {
      return null;
    }

    return (
      <AdditionalFieldEditor
        inputId={inputId}
        fieldLabel={fieldLabel}
        fieldType={fieldType}
        value={fieldValue || ""}
        activeOptions={activeOptions}
        fieldMetadata={fieldMetadata}
        onCommit={(nextValue) =>
          handleAdditionalFieldChange(data.id, fieldKey, fieldType, nextValue)
        }
      />
    );
  };

  const renderReferral = ({ data }) => {
    // Fetch location when row is expanded using sampleItemId
    const analysisId = data.id;
    const sampleItemId = data.sampleItemId;

    if (
      showStorageLocationOnResultEntry &&
      sampleItemId &&
      !sampleLocations[analysisId]
    ) {
      fetchSampleLocation(analysisId, sampleItemId);
    }

    // Get location path from stored data (keyed by analysisId)
    const locationData = sampleLocations[analysisId];
    const currentLocationPath =
      typeof locationData === "object"
        ? locationData.locationPath || ""
        : locationData || "";
    const rowMethods = Array.isArray(data.methods) ? data.methods : [];
    const showMethodSelector = rowMethods.length > 0;

    return (
      <>
        <Grid>
          {showMethodSelector && (
            <Column lg={2}>
              <Select
                id={"testMethod" + data.id}
                name={"testResult[" + data.id + "].testMethod"}
                labelText={intl.formatMessage({
                  id: "referral.label.testmethod",
                })}
                onChange={(e) => handleChange(e, data.id)}
                value={data.testMethod}
              >
                <SelectItem text="" value="" />
                {rowMethods.map((method, method_index) => (
                  <SelectItem
                    text={method.value}
                    value={method.id}
                    key={method_index}
                  />
                ))}
              </Select>
            </Column>
          )}
          {showResultLevelFileUpload && (
            <Column lg={2}>
              <CompactFileInput
                data={data}
                results={props.results}
                setResultForm={props.setResultForm}
              />

              {data.resultFile && data.resultFile.fileName && (
                <Link
                  onClick={() =>
                    downloadFile(
                      data.resultFile.fileName,
                      data.resultFile.content,
                      data.resultFile.fileType,
                    )
                  }
                  style={{ fontSize: "12px" }}
                >
                  {data.resultFile.fileName}
                </Link>
              )}
            </Column>
          )}
          {showReferralControls && (
            <>
              <Column lg={2}>
                <Checkbox
                  labelText={intl.formatMessage({ id: "results.label.refer" })}
                  name={"testResult[" + data.id + "].refer"}
                  id={"testResult[" + data.id + "].refer"}
                  checked={data.refer === "true"}
                  disabled={data.referredOut}
                  data-cy="referalcheckbox"
                  onChange={(e) => {
                    e.target.value = e.target.checked;
                    handleChange(e, data.id);
                  }}
                />
              </Column>
              <Column lg={2}>
                <Select
                  id={"referralReason" + data.id}
                  name={
                    "testResult[" + data.id + "].referralItem.referralReasonId"
                  }
                  // noLabel={true}
                  labelText={intl.formatMessage({
                    id: "referral.label.reason",
                  })}
                  onChange={(e) => handleChange(e, data.id)}
                  value={data?.referralItem?.referralReasonId}
                  disabled={!referTest[data.id]}
                >
                  {/* {...updateShadowResult(e, this, param.rowId)} */}
                  <SelectItem text="" value="" />
                  {referralReasons.map((reason, reason_index) => (
                    <SelectItem
                      text={reason.value}
                      value={reason.id}
                      key={reason_index}
                    />
                  ))}
                </Select>
              </Column>
              <Column lg={2}>
                <Select
                  id={"institute" + data.id}
                  name={
                    "testResult[" +
                    data.id +
                    "].referralItem.referredInstituteId"
                  }
                  // noLabel={true}
                  labelText={intl.formatMessage({
                    id: "referral.label.institute",
                  })}
                  onChange={(e) => handleChange(e, data.id)}
                  value={data?.referralItem?.referredInstituteId}
                  disabled={!referTest[data.id]}
                >
                  {/* {...updateShadowResult(e, this, param.rowId)} */}

                  <SelectItem text="" value="" />
                  {referalOrganizations.map((org, org_index) => (
                    <SelectItem
                      text={org.value}
                      value={org.id}
                      key={org_index}
                    />
                  ))}
                </Select>
              </Column>
              <Column lg={3}>
                <Select
                  id={"testToPerform" + data.id}
                  name={
                    "testResult[" + data.id + "].referralItem.referredTestId"
                  }
                  // noLabel={true}
                  labelText={intl.formatMessage({
                    id: "referral.label.testtoperform",
                  })}
                  onChange={(e) => handleChange(e, data.id)}
                  value={data?.referralItem?.referredTestId}
                  disabled={!referTest[data.id]}
                >
                  {/* {...updateShadowResult(e, this, param.rowId)} */}

                  <SelectItem text={data.testName} value={data.id} />
                </Select>
              </Column>
              <Column lg={2}>
                <CustomDatePicker
                  id={"sentDate_" + data.id}
                  labelText={intl.formatMessage({
                    id: "referral.label.sentdate",
                  })}
                  onChange={(date) => handleDatePickerChange(date, data.id)}
                  name={
                    "testResult[" + data.id + "].referralItem.referredSendDate"
                  }
                  value={data?.referralItem?.referredSendDate}
                  disabled={!referTest[data.id]}
                  disallowFutureDate={true}
                />
              </Column>
            </>
          )}
        </Grid>
        {true && (
          <Grid style={{ marginTop: "1rem" }}>
            {(() => {
              const activeAdditionalFields = Array.isArray(
                data.additionalFieldDefinitions,
              )
                ? data.additionalFieldDefinitions.filter(
                    (fieldDefinition) => fieldDefinition?.active !== false,
                  )
                : [];
              const visibleAdditionalFields = getVisibleAdditionalFields(
                data,
                activeAdditionalFields,
              );
              const primaryResultVisible = isPrimaryResultVisible(data);
              const primaryLayout = getPrimaryResultLayout(data, intl);
              const groupedBlocks = [];
              const groupedByName = new Map();
              const blockSortOrderByKey = new Map();
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
                      blockSortOrder: blockSortOrderByKey.get(blockKey),
                    },
                  };
                },
              );
              const sortedAdditionalFields = [...resolvedAdditionalFields].sort(
                (leftField, rightField) => {
                  const leftLayout = leftField.layout;
                  const rightLayout = rightField.layout;
                  if (
                    leftLayout.blockSortOrder !== rightLayout.blockSortOrder
                  ) {
                    return (
                      leftLayout.blockSortOrder - rightLayout.blockSortOrder
                    );
                  }
                  const blockNameComparison =
                    leftLayout.blockName.localeCompare(rightLayout.blockName);
                  if (blockNameComparison !== 0) {
                    return blockNameComparison;
                  }
                  if (
                    leftLayout.fieldSortOrder !== rightLayout.fieldSortOrder
                  ) {
                    return (
                      leftLayout.fieldSortOrder - rightLayout.fieldSortOrder
                    );
                  }
                  return (
                    (leftField?.fieldDefinition?.sortOrder || 0) -
                    (rightField?.fieldDefinition?.sortOrder || 0)
                  );
                },
              );

              sortedAdditionalFields.forEach(({ fieldDefinition, layout }) => {
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
                groupedByName.get(blockKey).items.push({
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
                typeof data?.resultName === "string" &&
                data.resultName.trim().length > 0
                  ? data.resultName.trim()
                  : intl.formatMessage({ id: "column.name.result" });
              const primaryBlockName =
                primaryLayout.blockName || officialBlockName;
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
                groupedByName.get(primaryBlockKey).items.push({
                  type: "primary",
                  fieldSortOrder: primaryLayout.fieldSortOrder,
                });
              }

              return groupedBlocks
                .sort((leftBlock, rightBlock) => {
                  if (leftBlock.blockSortOrder !== rightBlock.blockSortOrder) {
                    return leftBlock.blockSortOrder - rightBlock.blockSortOrder;
                  }
                  return leftBlock.blockName.localeCompare(
                    rightBlock.blockName,
                  );
                })
                .map((block, blockIndex) => {
                  const sortedItems = [...(block.items || [])].sort(
                    (leftItem, rightItem) => {
                      if (
                        leftItem.fieldSortOrder !== rightItem.fieldSortOrder
                      ) {
                        return (
                          leftItem.fieldSortOrder - rightItem.fieldSortOrder
                        );
                      }
                      if (leftItem.type !== rightItem.type) {
                        return leftItem.type === "primary" ? -1 : 1;
                      }
                      return 0;
                    },
                  );
                  const blockTitle = block.blockName || officialBlockName;
                  return (
                    <React.Fragment
                      key={`result-block-${data.id}-${blockIndex}-${blockTitle}`}
                    >
                      <Column lg={16} md={8} sm={4}>
                        <h6
                          style={{
                            marginBottom: "0.5rem",
                            marginTop: "0.25rem",
                          }}
                        >
                          {blockTitle}
                        </h6>
                      </Column>
                      {renderBlockTubeUsageControl(data, blockTitle)}
                      {sortedItems.map((item, fieldIndex) => {
                        if (item.type === "primary") {
                          return (
                            <Column
                              lg={4}
                              md={4}
                              sm={4}
                              key={`primary-result-render-${data.id}-${blockIndex}-${fieldIndex}`}
                            >
                              <Field
                                name={"testResult[" + data.id + "].resultValue"}
                              >
                                {() => (
                                  <>
                                    <p style={{ marginBottom: "0.5rem" }}>
                                      {primaryResultLabel}
                                    </p>
                                    {renderCell(
                                      data,
                                      0,
                                      { id: "result" },
                                      data.id,
                                    )}
                                  </>
                                )}
                              </Field>
                            </Column>
                          );
                        }
                        const fieldDefinition = item.fieldDefinition;
                        return (
                          <Column
                            lg={4}
                            md={4}
                            sm={4}
                            key={`additional-field-render-${data.id}-${fieldDefinition.fieldKey || fieldIndex}`}
                          >
                            {renderAdditionalFieldInput(data, fieldDefinition)}
                          </Column>
                        );
                      })}
                    </React.Fragment>
                  );
                });
            })()}
          </Grid>
        )}
        {showStorageLocationOnResultEntry && (
          <>
            {/* Storage Location Widget - INT-002: Integration point */}
            <Grid style={{ marginTop: "1rem" }}>
              <Column lg={16}>
                <StorageLocationSelector
                  workflow="results"
                  showQuickFind={true}
                  sampleInfo={{
                    sampleItemId: sampleItemId || null,
                    sampleItemExternalId:
                      locationData && typeof locationData === "object"
                        ? locationData.sampleItemExternalId
                        : null,
                    sampleAccessionNumber: data.accessionNumber,
                    sampleId: sampleItemId || data.accessionNumber, // Use sampleItemId
                    type: data.sampleType || "",
                    status: data.sampleStatus || "Active",
                  }}
                  hierarchicalPath={currentLocationPath}
                  onLocationChange={(locationData) => {
                    handleLocationAssignment(
                      locationData,
                      analysisId,
                      sampleItemId,
                    );
                  }}
                />
              </Column>
            </Grid>
          </>
        )}
      </>
    );
  };
  const validateResults = (e, rowId) => {
    console.debug("validateResults:" + e.target.value);
    // e.target.value;
    handleChange(e, rowId);
  };

  const commitRowFieldValue = (rowId, fieldName, nextValue) => {
    const form = { ...props.results };
    const jp = require("jsonpath");
    jp.value(form, fieldName, nextValue);
    if (fieldName === "testResult[" + rowId + "].resultValue") {
      jp.value(form, "testResult[" + rowId + "].shadowResultValue", nextValue);
    }
    jp.value(form, "testResult[" + rowId + "].isModified", "true");
    props.setResultForm(form);
  };

  const validateNumericResults = (value, row) => {
    //ignore < or > from the analyser on validation
    var greaterThanOrLessThan = "";
    if (("" + value).startsWith("<") || ("" + value).startsWith(">")) {
      greaterThanOrLessThan = value.charAt(0);
    }
    var actualValue = ("" + value).replace(/[<>]/g, "");
    let validation = {
      isInvalid: false,
      outsideNormal: false,
      isCritical: false,
      isBlank: false,
      isNaN: false,
      outsideValid: false,
      newValue: value,
    };
    //commented out for now
    let isSpecialCase = "XXXX" == actualValue.toUpperCase();
    validation = { ...validation, ...validateNumberFormat(value, row) };

    // resultBox.style.borderColor = validFormat ? "" : "red";

    // if( isSpecialCase ){
    //   resultBox.title = "";
    //   value = greaterThanOrLessThan + actualValue.toUpperCase();
    //   resultBox.style.borderColor = "";
    //   resultBox.style.background = "#ffffff";
    //   $("valid_" + row).value = true;
    //   return;
    // }
    if (validation.isNaN) {
      return { ...validation };
    } else if (
      row.lowCritical != row.highCritical &&
      actualValue > row.lowCritical &&
      actualValue < row.highCritical
    ) {
      return { ...validation, isCritical: true };
    } else if (
      row.lowerAbnormalRange != row.upperAbnormalRange &&
      (actualValue < row.lowerAbnormalRange ||
        actualValue > row.upperAbnormalRange)
    ) {
      return { ...validation, isInvalid: true, outsideValid: true };
      // resultBox.style.background = "#ffa0a0";
      // resultBox.title = "En dehors de la plage valide"; //FIXME: Uses hardcoded French labels. Switch to refer to resource file.
      // $("valid_" + row).value = false;
      // if( outOfValidRangeMsg ){
      //   alert( outOfValidRangeMsg);
      // }
    } else if (
      row.lowerNormalRange != row.upperNormalRange &&
      (actualValue < row.lowerNormalRange || actualValue > row.upperNormalRange)
    ) {
      return { ...validation, outsideNormal: true };
      // resultBox.style.background = "#ffffa0";
      // resultBox.title = "En dehors de la plage normale"; //FIXME: Uses hardcoded French labels. Switch to refer to resource file.
      // $("valid_" + row).value = true;
    } else {
      return { ...validation, outsideNormal: false };
      // resultBox.style.background = "#ffffff";
      // resultBox.title = "";
      // $("valid_" + row).value = true;
    }
  };

  const validateNumberFormat = (value, row) => {
    //ignore < or > from the analyser on validation
    var greaterThanOrLessThan = "";
    if (("" + value).startsWith("<") || ("" + value).startsWith(">")) {
      greaterThanOrLessThan = value.charAt(0);
    }
    var actualValue = ("" + value).replace(/[<>]/g, "");

    let validation = { isInvalid: false };
    if (!actualValue) {
      return { ...validation, isInvalid: true, isBlank: true };
      // resultBox.title = "";
      // resultBox.style.background = "#ffffff";
      // $("valid_" + row).value = false;
      // return true;
    }

    if (actualValue.trim() == ".") {
      validation = {
        ...validation,
        newValue: greaterThanOrLessThan + "0.0",
      };
    }

    if (isNaN(actualValue)) {
      return { ...validation, isInvalid: true, isNaN: true };
      // $("valid_" + row).value = false;
      // return false;
    }

    if (!isNaN(row.significantDigits)) {
      const valueStr = actualValue.toString();
      if (valueStr.includes(".")) {
        const decimalPlaces = valueStr.split(".")[1].length;
        if (decimalPlaces > row.significantDigits) {
          actualValue = parseFloat(actualValue).toFixed(row.significantDigits);
        }
      }
      validation = {
        ...validation,
        newValue: greaterThanOrLessThan + actualValue,
      };
    }

    return validation;
  };

  const handleChange = (e, rowId) => {
    const { name, id, value } = e.target;
    console.debug(
      "handleChange:" + id + ":" + name + ":" + value + ":" + rowId,
    );
    // setState({value: e.target.value})
    console.debug("State updated to ", e.target.value);
    var form = { ...props.results };
    var jp = require("jsonpath");
    jp.value(form, name, value);
    if (name === "testResult[" + rowId + "].resultValue") {
      jp.value(form, "testResult[" + rowId + "].shadowResultValue", value);
    }
    var refer = jp.query(form, "testResult[" + rowId + "].refer")[0];
    var testId = jp.query(form, "testResult[" + rowId + "].testId")[0];
    var referList = { ...referTest };
    referList[rowId] = refer === "true" ? true : false;
    setReferTest(referList);
    if (refer == "true") {
      jp.value(
        form,
        "testResult[" + rowId + "].referralItem.referredTestId",
        testId,
      );
      jp.value(
        form,
        "testResult[" + rowId + "].referralItem.referredSendDate",
        configurationProperties.currentDateAsText,
      );
    } else {
      jp.value(
        form,
        "testResult[" + rowId + "].referralItem.referredTestId",
        "",
      );
      jp.value(
        form,
        "testResult[" + rowId + "].referralItem.referredSendDate",
        "",
      );
    }
    var isModified = "testResult[" + rowId + "].isModified";
    jp.value(form, isModified, "true");
    props.setResultForm(form);
  };

  const handleRejectCheckBoxChange = (e, rowId) => {
    const { name, checked } = e.target;
    var form = props.results;
    var jp = require("jsonpath");
    jp.value(form, name, checked);
    var shadowRejected = "testResult[" + rowId + "].shadowRejected";
    jp.value(form, shadowRejected, checked);
    var isModified = "testResult[" + rowId + "].isModified";
    jp.value(form, isModified, "true");

    var allrejectedItems = { ...rejectedItems };
    allrejectedItems[rowId] = checked;
    setRejectedItems(allrejectedItems);

    addNotification({
      title: intl.formatMessage({ id: "notification.title" }),
      message: intl.formatMessage({ id: "result.reject.warning" }),
      kind: NotificationKinds.warning,
    });
    if (checked) {
      setNotificationVisible(true);
    }
  };

  const handleDatePickerChange = (date, rowId) => {
    var form = { ...props.results };
    if (form.testResult[rowId].referralItem) {
      if (form.testResult[rowId].referralItem.referredSendDate != date) {
        console.debug("handleDatePickerChange:" + date);
        var jp = require("jsonpath");
        jp.value(
          form,
          "testResult[" + rowId + "].referralItem.referredSendDate",
          date,
        );
        var isModified = "testResult[" + rowId + "].isModified";
        jp.value(form, isModified, "true");
        props.setResultForm(form);
      }
    }
  };

  const handleAcceptAsIsChange = (e, rowId) => {
    console.debug("handleAcceptAsIsChange:" + acceptAsIs[rowId]);
    handleChange(e, rowId);
    if (acceptAsIs[rowId] == undefined) {
      alert(intl.formatMessage({ id: "result.acceptasis.warning" }));
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "result.acceptasis.warning" }),
        kind: NotificationKinds.warning,
      });
      setNotificationVisible(true);
    }
    var newAcceptAsIs = acceptAsIs;
    newAcceptAsIs[rowId] = !acceptAsIs[rowId];
    setAcceptAsIs(newAcceptAsIs);
  };

  const handleSave = (values) => {
    console.debug("handleSave:" + values);
    if (isSubmitting) {
      return;
    }

    const dependentChildMissingUsage = props.results.testResult.find((item) => {
      if (
        !item.dependentChild ||
        item.sampleUsageLocked === true ||
        item.blockTubeUsageEnabled === true
      ) {
        return false;
      }

      const hasResult =
        (item.resultType === "M" || item.resultType === "C"
          ? item.multiSelectResultValues &&
            item.multiSelectResultValues !== "{}"
          : item.shadowResultValue &&
            !(item.resultType === "D" && item.shadowResultValue === "0")) ||
        (item.resultType !== "M" &&
          item.resultType !== "C" &&
          item.resultValue &&
          !(item.resultType === "D" && item.resultValue === "0")) ||
        item.refer === true ||
        item.refer === "true" ||
        item.shadowRejected === true ||
        item.shadowRejected === "true";

      if (!hasResult) {
        return false;
      }

      if (!item.sampleUsageQuantity) {
        return true;
      }

      const parsed = Number(item.sampleUsageQuantity);
      return Number.isNaN(parsed) || parsed <= 0;
    });

    const dependentChildMissingTubeSelection = props.results.testResult.find(
      (item) => {
        if (
          !item.dependentChild ||
          item.sampleUsageLocked === true ||
          item.blockTubeUsageEnabled === true ||
          item.parentTubeSelectionEnabled !== true
        ) {
          return false;
        }

        const hasResult =
          (item.resultType === "M" || item.resultType === "C"
            ? item.multiSelectResultValues &&
              item.multiSelectResultValues !== "{}"
            : item.shadowResultValue &&
              !(item.resultType === "D" && item.shadowResultValue === "0")) ||
          (item.resultType !== "M" &&
            item.resultType !== "C" &&
            item.resultValue &&
            !(item.resultType === "D" && item.resultValue === "0")) ||
          item.refer === true ||
          item.refer === "true" ||
          item.shadowRejected === true ||
          item.shadowRejected === "true";

        if (!hasResult) {
          return false;
        }

        return !item.parentUsageBlockName;
      },
    );

    const parentMissingUsage = props.results.testResult.find((item) => {
      if (
        !item.parentSampleUsageEnabled ||
        item.dependentChild === true ||
        item.parentSampleUsageLocked === true
      ) {
        return false;
      }

      const hasResult =
        (item.resultType === "M" || item.resultType === "C"
          ? item.multiSelectResultValues &&
            item.multiSelectResultValues !== "{}"
          : item.shadowResultValue &&
            !(item.resultType === "D" && item.shadowResultValue === "0")) ||
        (item.resultType !== "M" &&
          item.resultType !== "C" &&
          item.resultValue &&
          !(item.resultType === "D" && item.resultValue === "0")) ||
        item.refer === true ||
        item.refer === "true" ||
        item.shadowRejected === true ||
        item.shadowRejected === "true";

      if (!hasResult) {
        return false;
      }

      if (!item.parentSampleUsageQuantity) {
        return true;
      }

      const parsed = Number(item.parentSampleUsageQuantity);
      return Number.isNaN(parsed) || parsed <= 0;
    });

    const dependentChildMissingRemaining = props.results.testResult.find(
      (item) => {
        if (
          !item.dependentChild ||
          item.sampleUsageLocked === true ||
          item.blockTubeUsageEnabled === true
        ) {
          return false;
        }

        const hasResult =
          (item.resultType === "M" || item.resultType === "C"
            ? item.multiSelectResultValues &&
              item.multiSelectResultValues !== "{}"
            : item.shadowResultValue &&
              !(item.resultType === "D" && item.shadowResultValue === "0")) ||
          (item.resultType !== "M" &&
            item.resultType !== "C" &&
            item.resultValue &&
            !(item.resultType === "D" && item.resultValue === "0")) ||
          item.refer === true ||
          item.refer === "true" ||
          item.shadowRejected === true ||
          item.shadowRejected === "true";

        if (!hasResult) {
          return false;
        }

        if (
          item.sampleRemainingQuantity === null ||
          item.sampleRemainingQuantity === undefined ||
          item.sampleRemainingQuantity === ""
        ) {
          return true;
        }

        const remaining = Number(item.sampleRemainingQuantity);
        return Number.isNaN(remaining);
      },
    );

    const dependentChildBlockUsageError = props.results.testResult.find((item) => {
      if (
        !item.dependentChild ||
        item.sampleUsageLocked === true ||
        item.blockTubeUsageEnabled !== true
      ) {
        return false;
      }

      const enteredBlocks = getEnteredChildTubeUsageBlocks(item, intl);
      if (enteredBlocks.length === 0) {
        return false;
      }

      const usagesByBlock = Array.isArray(item.blockSampleUsages)
        ? item.blockSampleUsages.reduce((acc, usage) => {
            if (usage?.childBlockName) {
              acc[normalizeBlockIdentifier(usage.childBlockName)] = usage;
            }
            return acc;
          }, {})
        : {};

      return enteredBlocks.some((blockName) => {
        const usage = usagesByBlock[blockName];
        if (!usage) {
          return true;
        }
        if (usage.locked === true) {
          return false;
        }
        const selectedTube = String(usage.parentTubeBlockName || "").trim();
        const parsedUsage = Number(usage.usedQuantity);
        const parsedRemaining = Number(usage.remainingQuantity);
        return (
          !selectedTube ||
          !Number.isFinite(parsedUsage) ||
          parsedUsage <= 0 ||
          !Number.isFinite(parsedRemaining) ||
          parsedUsage > parsedRemaining
        );
      });
    });

    if (dependentChildMissingTubeSelection) {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "result.entry.parentTube.required",
          defaultMessage:
            "Dependent child tests require selecting a parent tube before saving.",
        }),
        kind: NotificationKinds.error,
      });
      setNotificationVisible(true);
      return;
    }

    if (dependentChildBlockUsageError) {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "result.entry.blockTubeUsage.required",
          defaultMessage:
            "Each child block with entered results requires a tube selection and a valid usage quantity.",
        }),
        kind: NotificationKinds.error,
      });
      setNotificationVisible(true);
      return;
    }

    if (dependentChildMissingRemaining) {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "result.entry.sampleUsage.remaining.missing",
          defaultMessage:
            "Dependent child tests require a valid remaining quantity from the configured parent source.",
        }),
        kind: NotificationKinds.error,
      });
      setNotificationVisible(true);
      return;
    }

    if (parentMissingUsage) {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "result.entry.parentSampleUsage.required",
          defaultMessage: "Parent tests require a valid sample usage quantity.",
        }),
        kind: NotificationKinds.error,
      });
      setNotificationVisible(true);
      return;
    }

    if (dependentChildMissingUsage) {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "result.entry.sampleUsage.required",
          defaultMessage:
            "Dependent child tests require a valid sample usage quantity.",
        }),
        kind: NotificationKinds.error,
      });
      setNotificationVisible(true);
      return;
    }

    setIsSubmitting(true);
    values.status = saveStatus;
    var searchEndPoint = "/rest/LogbookResults";
    props.results.testResult.forEach((result) => {
      result.reportable = result.reportable === "N" ? false : true;
      delete result.result;
    });
    postToOpenElisServerJsonResponse(
      searchEndPoint,
      JSON.stringify(props.results),
      setResponse,
    );
  };

  const setResponse = (resp) => {
    console.debug("setStatus" + JSON.stringify(resp));
    setIsSubmitting(false);
    if (resp && !resp.error && !(resp.status && resp.status >= 400)) {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: createMesssage(resp),
        kind: NotificationKinds.success,
      });
      if (props.refreshOnSubmit) {
        window.location.href =
          "/result?type=" +
          props.searchBy.type +
          "&doRange=" +
          props.searchBy.doRange +
          props.extraParams;
      }
    } else {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message:
          resp?.message ||
          resp?.statusText ||
          intl.formatMessage({ id: "error.save.msg" }),
        kind: NotificationKinds.error,
      });
    }
    setNotificationVisible(true);
  };

  const createMesssage = (resp) => {
    var message = "";
    if (resp.reflex?.length > 0) {
      message +=
        intl.formatMessage({ id: "reflexTests" }) +
        ": " +
        resp.reflex.join(", ");
    }
    if (resp.calculated?.length > 0) {
      message +=
        intl.formatMessage({ id: "calculatedTests" }) +
        ": " +
        resp.calculated.join(", ");
    }
    if (message === "") {
      message += intl.formatMessage({ id: "success.save.msg" });
    }
    return message;
  };

  const handlePageChange = (pageInfo) => {
    if (page != pageInfo.page) {
      setPage(pageInfo.page);
    }
    if (pageSize != pageInfo.pageSize) {
      setPageSize(pageInfo.pageSize);
    }
  };

  return (
    <>
      {notificationVisible === true ? <AlertDialog /> : ""}
      {addRejectResult()}
      <>
        <Formik
          initialValues={SearchResultFormValues}
          //validationSchema={}
          onSubmit
        >
          {() => (
            <Form>
              <DataTable
                keyField="id"
                data={props.results?.testResult?.slice(
                  (page - 1) * pageSize,
                  page * pageSize,
                )}
                columns={visibleColumns}
                isSortable
                expandableRows
                expandableRowsComponent={renderReferral}
              ></DataTable>
              <Pagination
                onChange={handlePageChange}
                page={page}
                pageSize={pageSize}
                pageSizes={[10, 20, 30, 50, 100]}
                totalItems={props.results?.testResult?.length}
                forwardText={intl.formatMessage({ id: "pagination.forward" })}
                backwardText={intl.formatMessage({ id: "pagination.backward" })}
                itemRangeText={(min, max, total) =>
                  intl.formatMessage(
                    { id: "pagination.item-range" },
                    { min: min, max: max, total: total },
                  )
                }
                itemsPerPageText={intl.formatMessage({
                  id: "pagination.items-per-page",
                })}
                itemText={(min, max) =>
                  intl.formatMessage(
                    { id: "pagination.item" },
                    { min: min, max: max },
                  )
                }
                pageNumberText={intl.formatMessage({
                  id: "pagination.page-number",
                })}
                pageRangeText={(_current, total) =>
                  intl.formatMessage(
                    { id: "pagination.page-range" },
                    { total: total },
                  )
                }
                pageText={(page, pagesUnknown) =>
                  intl.formatMessage(
                    { id: "pagination.page" },
                    { page: pagesUnknown ? "" : page },
                  )
                }
              />

              <Button
                type="button"
                id="saveResults"
                onClick={handleSave}
                style={{ marginTop: "16px" }}
                disabled={isSubmitting}
              >
                <FormattedMessage id="label.button.save" />
              </Button>
            </Form>
          )}
        </Formik>
      </>
    </>
  );
}

export default injectIntl(ResultSearchPage);
