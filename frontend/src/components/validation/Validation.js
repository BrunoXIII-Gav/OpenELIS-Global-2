import React, { useState, useContext, useEffect, useRef, useMemo } from "react";
import { Field, Formik } from "formik";
import {
  Button,
  Checkbox,
  Column,
  Form,
  Grid,
  Modal,
  Pagination,
  RadioButton,
  RadioButtonGroup,
  Link,
  TextArea,
} from "@carbon/react";
import { Copy } from "@carbon/icons-react";
import DataTable from "react-data-table-component";
import { FormattedMessage, useIntl } from "react-intl";
import ValidationSearchFormValues from "../formModel/innitialValues/ValidationSearchFormValues";
import { NotificationKinds } from "../common/CustomNotification";
import { postToOpenElisServer } from "../utils/Utils";
import { NotificationContext } from "../layout/Layout";
import { ConfigurationContext } from "../layout/Layout";
import config from "../../config.json";

const normalizeResultEntryScope = (scopeValue) =>
  String(scopeValue || "").toUpperCase() === "PRELIMINARY"
    ? "PRELIMINARY"
    : "OFFICIAL";

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
  return { blockName, entryScope };
};

const isPrimaryResultActive = (data) => {
  const metadata = parseAdditionalFieldMetadata({
    metadataJson: data?.resultDisplayConfigJson,
  });
  return metadata?.active !== false;
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
    const uploadToken = String(parsed.uploadToken || "").trim();
    const previewUrl = String(parsed.previewUrl || "").trim();
    if (!fileName || (!base64Content && !uploadToken && !previewUrl)) {
      return null;
    }
    return {
      fileName,
      fileType,
      base64Content,
      uploadToken,
      previewUrl,
    };
  } catch (e) {
    return null;
  }
};

const buildAdditionalFieldPreviewHref = (analysisId, fieldKey, filePayload) => {
  const previewUrl = String(filePayload?.previewUrl || "").trim();
  if (previewUrl) {
    return previewUrl;
  }

  const uploadToken = String(filePayload?.uploadToken || "").trim();
  if (uploadToken) {
    return `${config.serverBaseUrl}/rest/test-additional-fields/files/temp/${uploadToken}?download=false`;
  }

  if (analysisId && fieldKey) {
    return `${config.serverBaseUrl}/rest/test-additional-fields/files/${analysisId}/${fieldKey}?download=false`;
  }

  return "";
};

const openAdditionalFieldDocument = (analysisId, fieldKey, filePayload) => {
  const previewHref = buildAdditionalFieldPreviewHref(
    analysisId,
    fieldKey,
    filePayload,
  );
  if (previewHref) {
    window.open(previewHref, "_blank", "noopener,noreferrer");
    return;
  }

  const fileType = String(filePayload?.fileType || "").trim();
  const base64Content = String(filePayload?.base64Content || "").trim();
  if (!base64Content) {
    return;
  }
  const mime = fileType || "application/octet-stream";
  window.open(`data:${mime};base64,${base64Content}`, "_blank");
};

const Validation = (props) => {
  const componentMounted = useRef(false);
  const DEFAULT_VALIDATION_REPORT = "patientCILNSP_vreduit";

  const { setNotificationVisible, addNotification } =
    useContext(NotificationContext);
  const { configurationProperties } = useContext(ConfigurationContext);

  const intl = useIntl();

  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(100);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [hasOpenedPreview, setHasOpenedPreview] = useState(false);
  const [previewConfirmed, setPreviewConfirmed] = useState(false);
  const [savedAnalysisIds, setSavedAnalysisIds] = useState([]);
  const [previewModalOpen, setPreviewModalOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState("");
  const [previewPdfUrl, setPreviewPdfUrl] = useState("");
  const [previewSelectionModalOpen, setPreviewSelectionModalOpen] =
    useState(false);
  const [previewOptions, setPreviewOptions] = useState([]);
  const [selectedPreviewOption, setSelectedPreviewOption] = useState("");
  const [previewedOptionIds, setPreviewedOptionIds] = useState([]);
  const [downloadModalOpen, setDownloadModalOpen] = useState(false);
  const [downloadOptions, setDownloadOptions] = useState([]);
  const [selectedDownloadOption, setSelectedDownloadOption] = useState("");
  const [validationDatesByRowId, setValidationDatesByRowId] = useState({});

  const validationReportName =
    configurationProperties?.validationReportName || DEFAULT_VALIDATION_REPORT;
  const showResultLevelFileUpload =
    configurationProperties?.ENABLE_RESULT_LEVEL_FILE_UPLOAD !== "false";
  const currentUserIsMedicalValidator = Boolean(
    props?.results?.currentUserIsMedicalValidator,
  );
  const accessionNumberFromUrl = useMemo(() => {
    const urlAccessionValue = new URLSearchParams(window.location.search).get(
      "accessionNumber",
    );
    if (urlAccessionValue && urlAccessionValue.trim().length > 0) {
      return urlAccessionValue.trim();
    }

    const paramsAccessionValue = new URLSearchParams(
      (props?.params || "").replace(/^\?/, ""),
    ).get("accessionNumber");
    return paramsAccessionValue ? paramsAccessionValue.trim() : "";
  }, [props?.params]);
  const resetPreviewGate = (clearSavedDownloadState = true) => {
    setHasOpenedPreview(false);
    setPreviewConfirmed(false);
    setPreviewSelectionModalOpen(false);
    setPreviewOptions([]);
    setSelectedPreviewOption("");
    setPreviewedOptionIds([]);
    if (clearSavedDownloadState) {
      setSavedAnalysisIds([]);
    }
  };

  const getAcceptedAnalysisIds = () => {
    const acceptedRows =
      props?.results?.resultList?.filter(
        (result) =>
          result?.isAccepted &&
          result?.analysisId &&
          !result?.readOnly &&
          (!result?.approvedByCurrentUser || currentUserIsMedicalValidator),
      ) || [];
    return [...new Set(acceptedRows.map((result) => result.analysisId))];
  };

  const getRejectedAnalysisIds = () => {
    const rejectedRows =
      props?.results?.resultList?.filter(
        (result) =>
          result?.isRejected && result?.analysisId && !result?.readOnly,
      ) || [];
    return [...new Set(rejectedRows.map((result) => result.analysisId))];
  };

  const getReportUrl = (
    analysisIds,
    previewValidated = false,
    requestedReport = validationReportName,
  ) => {
    const query = new URLSearchParams();
    query.set("report", requestedReport);
    query.set("type", "patient");
    query.set("analysisIds", analysisIds.join(","));
    if (previewValidated) {
      query.set("previewValidated", "true");
      query.set("previewAnalysisIds", analysisIds.join(","));
    }
    return `${config.serverBaseUrl}/ReportPrint?${query.toString()}`;
  };

  useEffect(() => {
    componentMounted.current = true;
    return () => {
      clearPreviewBlobUrl();
      componentMounted.current = false;
    };
  }, []);

  const clearPreviewBlobUrl = () => {
    if (previewPdfUrl) {
      window.URL.revokeObjectURL(previewPdfUrl);
    }
    setPreviewPdfUrl("");
  };

  const closePreviewModal = () => {
    setPreviewModalOpen(false);
    setPreviewLoading(false);
    setPreviewError("");
    clearPreviewBlobUrl();
  };

  useEffect(() => {
    resetPreviewGate();
  }, [props.results?.resultList]);

  useEffect(() => {
    setSavedAnalysisIds([]);
  }, [accessionNumberFromUrl]);

  useEffect(() => {
    const nextDates = {};
    (props?.results?.resultList || []).forEach((row) => {
      const rawDateValue =
        row?.validationDate || row?.sentDate_ || row?.completeDate || "";
      const dateValue = String(rawDateValue || "")
        .trim()
        .slice(0, 10);
      if (dateValue) {
        nextDates[row.id] = dateValue;
      }
    });
    setValidationDatesByRowId(nextDates);
  }, [props?.results?.resultList, accessionNumberFromUrl]);

  const columns = [
    {
      id: "sampleInfo",
      name: intl.formatMessage({ id: "column.name.sampleInfo" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      selector: (row) => row.accessionNumber,
      sortable: true,
      width: "16rem",
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
      id: "save",
      name: intl.formatMessage({ id: "column.name.save" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "8rem",
    },
    {
      id: "validationDate",
      name: intl.formatMessage({
        id: "column.name.validationDate",
        defaultMessage: "Validation Date",
      }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "12rem",
    },
    {
      id: "approvals",
      name: intl.formatMessage({
        id: "column.name.approvals",
        defaultMessage: "Approvals",
      }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "8rem",
    },
    {
      id: "retest",
      name: intl.formatMessage({ id: "column.name.retest" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "8rem",
    },
    {
      id: "notes",
      name: intl.formatMessage({ id: "column.name.notes" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "15rem",
    },
    {
      id: "pastNotes",
      name: intl.formatMessage({ id: "column.name.pastNotes" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
      width: "28rem",
    },
  ];

  // Hide only in UI (keep column definition/data intact)
  const hiddenColumnIds = new Set(["normalRange"]);
  const visibleColumns = columns.filter(
    (column) => !hiddenColumnIds.has(column.id),
  );

  const markPreviewOptionAsReviewed = (optionId, optionsSnapshot) => {
    setPreviewedOptionIds((previous) => {
      const next = previous.includes(optionId)
        ? previous
        : [...previous, optionId];
      const allReviewed =
        optionsSnapshot.length > 0 &&
        optionsSnapshot.every((option) => next.includes(option.id));
      setHasOpenedPreview(allReviewed);
      return next;
    });
    setPreviewConfirmed(false);
  };

  const openPreviewByChoice = async (
    choice,
    optionsSnapshot = previewOptions,
  ) => {
    if (!choice?.analysisIds?.length) {
      return;
    }

    setPreviewModalOpen(true);
    setPreviewLoading(true);
    setPreviewError("");
    clearPreviewBlobUrl();
    try {
      const response = await fetch(
        getReportUrl(choice.analysisIds, true, choice.preferredReport),
        {
          credentials: "include",
          method: "GET",
          headers: {
            Accept: "application/pdf",
          },
        },
      );
      if (!response.ok) {
        throw new Error(`Preview request failed: ${response.status}`);
      }
      const pdfBlob = await response.blob();
      const objectUrl = window.URL.createObjectURL(pdfBlob);
      setPreviewPdfUrl(objectUrl);
      markPreviewOptionAsReviewed(choice.id, optionsSnapshot);
    } catch (error) {
      setPreviewError(
        intl.formatMessage({
          id: "validation.preview.load.error",
          defaultMessage: "Unable to render report preview. Please try again.",
        }),
      );
    } finally {
      setPreviewLoading(false);
    }
  };

  const handlePreview = async () => {
    if (validationLocked) {
      return;
    }
    const acceptedAnalysisIds = getAcceptedAnalysisIds();
    if (acceptedAnalysisIds.length === 0) {
      addNotification({
        kind: NotificationKinds.warning,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "validation.preview.noSelection",
          defaultMessage:
            "Select at least one result marked for validation before previewing.",
        }),
      });
      setNotificationVisible(true);
      return;
    }
    if (hasAcceptedRowsMissingValidationDate) {
      addNotification({
        kind: NotificationKinds.warning,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "validation.date.required.preview",
          defaultMessage:
            "Enter validation date for each selected test before previewing.",
        }),
      });
      setNotificationVisible(true);
      return;
    }

    const reportChoices = buildDownloadChoices(acceptedAnalysisIds);
    const sameChoicesAsCurrent = areSameChoiceSet(
      reportChoices,
      previewOptions,
    );

    setPreviewOptions(reportChoices);
    if (!sameChoicesAsCurrent) {
      setPreviewedOptionIds([]);
      setHasOpenedPreview(false);
      setPreviewConfirmed(false);
    }

    if (reportChoices.length <= 1) {
      const onlyChoice = reportChoices[0];
      if (!onlyChoice) {
        return;
      }
      setSelectedPreviewOption(onlyChoice.id);
      setPreviewSelectionModalOpen(false);
      await openPreviewByChoice(onlyChoice, reportChoices);
      return;
    }

    setSelectedPreviewOption(reportChoices[0].id);
    setPreviewSelectionModalOpen(true);
  };

  const confirmPreviewSelection = async () => {
    const selectedOption = previewOptions.find(
      (option) => option.id === selectedPreviewOption,
    );
    if (!selectedOption) {
      return;
    }
    setPreviewSelectionModalOpen(false);
    await openPreviewByChoice(selectedOption, previewOptions);
  };

  const buildChoiceSignatures = (choices) =>
    choices
      .map((choice) => {
        const ids = [...(choice.analysisIds || [])].sort().join(",");
        return `${choice.id}|${choice.preferredReport}|${ids}`;
      })
      .sort();

  const areSameChoiceSet = (leftChoices, rightChoices) => {
    const left = buildChoiceSignatures(leftChoices);
    const right = buildChoiceSignatures(rightChoices);
    if (left.length !== right.length) {
      return false;
    }
    return left.every((value, index) => value === right[index]);
  };

  const handleSave = (values) => {
    if (validationLocked) {
      if (!currentUserIsMedicalValidator) {
        addNotification({
          kind: NotificationKinds.warning,
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({
            id: "validation.medical.only",
            defaultMessage: "Only medical validators can validate results.",
          }),
        });
        setNotificationVisible(true);
      }
      return;
    }
    if (isSubmitting) {
      return;
    }
    const acceptedAnalysisIds = getAcceptedAnalysisIds();
    const rejectedAnalysisIds = getRejectedAnalysisIds();
    const hasAcceptedSelections = acceptedAnalysisIds.length > 0;
    const hasRejectedSelections = rejectedAnalysisIds.length > 0;
    if (!hasAcceptedSelections && !hasRejectedSelections) {
      addNotification({
        kind: NotificationKinds.warning,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "validation.preview.noSelection",
          defaultMessage:
            "Select at least one result marked for validation before previewing.",
        }),
      });
      setNotificationVisible(true);
      return;
    }
    if (hasAcceptedRowsMissingValidationDate) {
      addNotification({
        kind: NotificationKinds.warning,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "validation.date.required.save",
          defaultMessage:
            "Enter validation date for each selected test before saving.",
        }),
      });
      setNotificationVisible(true);
      return;
    }
    if (
      currentUserIsMedicalValidator &&
      hasAcceptedSelections &&
      (!hasOpenedPreview || !previewConfirmed)
    ) {
      addNotification({
        kind: NotificationKinds.warning,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "validation.preview.confirm.required",
          defaultMessage:
            "Preview the report and confirm it before saving validation.",
        }),
      });
      setNotificationVisible(true);
      return;
    }
    props.results.medicalValidationConfirmed =
      currentUserIsMedicalValidator &&
      hasAcceptedSelections &&
      hasOpenedPreview &&
      previewConfirmed;
    setIsSubmitting(true);
    postToOpenElisServer(
      "/rest/AccessionValidation",
      JSON.stringify(props.results),
      handleResponse,
      acceptedAnalysisIds,
    );
  };

  const handleDownloadValidatedReport = () => {
    const eligibleAnalysisIds = getEligibleAnalysisIdsForDownload();
    if (eligibleAnalysisIds.length === 0) {
      return;
    }

    const reportChoices = buildDownloadChoices(eligibleAnalysisIds);

    if (reportChoices.length <= 1) {
      const onlyChoice = reportChoices[0];
      openDownloadByAnalysisIds(
        onlyChoice?.analysisIds || eligibleAnalysisIds,
        onlyChoice?.preferredReport || validationReportName,
      );
      return;
    }

    setDownloadOptions(reportChoices);
    setSelectedDownloadOption(reportChoices[0].id);
    setDownloadModalOpen(true);
  };

  const getEligibleAnalysisIdsForDownload = () => {
    if (savedAnalysisIds.length > 0) {
      return [...new Set(savedAnalysisIds)];
    }

    if (!hasLiveResults) {
      return [
        ...new Set(
          liveResultList
            .filter((row) => row?.readOnly && row?.analysisId)
            .map((row) => row.analysisId),
        ),
      ];
    }

    return [];
  };

  const buildDownloadChoices = (eligibleAnalysisIds) => {
    const eligibleSet = new Set(eligibleAnalysisIds);
    const groupedByTest = new Map();

    liveResultList.forEach((row) => {
      if (!row?.analysisId || !eligibleSet.has(row.analysisId)) {
        return;
      }

      const testKey = row.testId || row.testName || row.analysisId;
      const existing = groupedByTest.get(testKey);
      if (!existing) {
        groupedByTest.set(testKey, {
          id: String(testKey),
          label: row.testName || row.testId || row.analysisId,
          preferredReport: (row.testName || "").toLowerCase().includes("dmpk")
            ? "patientDMPK"
            : validationReportName,
          analysisIds: [row.analysisId],
        });
      } else {
        existing.analysisIds.push(row.analysisId);
      }
    });

    return Array.from(groupedByTest.values()).map((option) => ({
      ...option,
      analysisIds: [...new Set(option.analysisIds)],
    }));
  };

  const openDownloadByAnalysisIds = (
    analysisIds,
    requestedReport = validationReportName,
  ) => {
    if (!analysisIds || analysisIds.length === 0) {
      return;
    }
    const reportUrl = getReportUrl(analysisIds, false, requestedReport);
    window.open(reportUrl, "_blank", "noopener,noreferrer");
  };

  const confirmDownloadSelection = () => {
    const selectedOption = downloadOptions.find(
      (option) => option.id === selectedDownloadOption,
    );
    if (!selectedOption) {
      return;
    }
    openDownloadByAnalysisIds(
      selectedOption.analysisIds,
      selectedOption.preferredReport || validationReportName,
    );
    setDownloadModalOpen(false);
  };
  const handleResponse = (status, savedIds) => {
    let message = intl.formatMessage({ id: "validation.save.error" });
    let kind = NotificationKinds.error;
    setIsSubmitting(false);
    if (status == 200) {
      message = intl.formatMessage({ id: "validation.save.success" });
      kind = NotificationKinds.success;
      const successfulSavedIds =
        currentUserIsMedicalValidator && Array.isArray(savedIds)
          ? savedIds
          : [];
      setSavedAnalysisIds(successfulSavedIds);
      const savedIdSet = new Set(successfulSavedIds);
      if (savedIdSet.size > 0) {
        liveResultList.forEach((row) => {
          const shouldLockMedicalRow =
            savedIdSet.has(row?.analysisId) && !row?.readOnly;
          if (shouldLockMedicalRow) {
            row.approvedByCurrentUser = true;
            row.isAccepted = true;
          }
        });
      }
      if (currentUserIsMedicalValidator) {
        setHasOpenedPreview(false);
        setPreviewConfirmed(false);
      }
    }
    addNotification({
      kind: kind,
      title: intl.formatMessage({ id: "notification.title" }),
      message: message,
    });
    setNotificationVisible(true);

    if (status == 200) {
      window.location.reload();
    }
  };

  const handlePageChange = (pageInfo) => {
    if (page != pageInfo.page) {
      setPage(pageInfo.page);
    }
    if (pageSize != pageInfo.pageSize) {
      setPageSize(pageInfo.pageSize);
    }
  };

  const handleChange = (e, rowId) => {
    const { name, id, value } = e.target;
    let form = props.results;
    var jp = require("jsonpath");
    jp.value(form, name, value);
  };

  const handleDatePickerChange = (date, rowId) => {
    console.debug("handleDatePickerChange:" + date);
    const d = new Date(date).toLocaleDateString("fr-FR");
    var form = props.results;
    var jp = require("jsonpath");
    jp.value(form, "resultList[" + rowId + "].sentDate_", d);
  };
  const handleCheckBox = (e, rowId) => {
    const { name, id, checked } = e.target;
    let form = props.results;
    var jp = require("jsonpath");
    jp.value(form, name, checked);
    resetPreviewGate();
  };

  const handleAutomatedCheck = (checked, name) => {
    let form = props.results;
    var jp = require("jsonpath");
    jp.value(form, name, checked);
    resetPreviewGate();
  };

  const getRowValidationDateValue = (row) => {
    const fromState = validationDatesByRowId[row?.id];
    if (fromState) {
      return fromState;
    }
    const fallback = row?.validationDate || row?.sentDate_ || "";
    return String(fallback || "")
      .trim()
      .slice(0, 10);
  };

  const handleValidationDateChange = (e, rowId) => {
    const { value } = e.target;
    let form = props.results;
    var jp = require("jsonpath");
    jp.value(form, "resultList[" + rowId + "].validationDate", value);
    setValidationDatesByRowId((previous) => ({
      ...previous,
      [rowId]: value,
    }));
    resetPreviewGate();
  };

  const validateResults = (e, rowId) => {
    handleChange(e, rowId);
  };

  const openResultAttachment = (file) => {
    const toBase64 = (value) => {
      if (!value) {
        return "";
      }
      if (typeof value === "string") {
        if (value.includes(";base64,")) {
          return value.split(";base64,", 2)[1];
        }
        return value;
      }

      try {
        if (Array.isArray(value)) {
          return btoa(
            new Uint8Array(value).reduce(
              (accumulator, byte) => accumulator + String.fromCharCode(byte),
              "",
            ),
          );
        }

        if (value instanceof Uint8Array) {
          return btoa(
            value.reduce(
              (accumulator, byte) => accumulator + String.fromCharCode(byte),
              "",
            ),
          );
        }

        if (value instanceof ArrayBuffer) {
          return btoa(
            new Uint8Array(value).reduce(
              (accumulator, byte) => accumulator + String.fromCharCode(byte),
              "",
            ),
          );
        }

        if (value?.buffer instanceof ArrayBuffer) {
          return btoa(
            new Uint8Array(value.buffer).reduce(
              (accumulator, byte) => accumulator + String.fromCharCode(byte),
              "",
            ),
          );
        }

        if (Array.isArray(value?.data)) {
          return btoa(
            new Uint8Array(value.data).reduce(
              (accumulator, byte) => accumulator + String.fromCharCode(byte),
              "",
            ),
          );
        }
      } catch (error) {
        return "";
      }

      return "";
    };

    const normalizeMimeSource = (fileTypeValue) => {
      if (!fileTypeValue || typeof fileTypeValue !== "string") {
        return null;
      }
      if (fileTypeValue.startsWith("data:")) {
        return fileTypeValue.includes(";base64,")
          ? fileTypeValue
          : `${fileTypeValue};base64,`;
      }
      if (fileTypeValue.includes(";base64,")) {
        return fileTypeValue;
      }
      return `data:${fileTypeValue};base64,`;
    };

    const encodedContent = toBase64(file?.content);
    const mimeSource = normalizeMimeSource(file?.fileType);

    if (!encodedContent || !mimeSource) {
      return;
    }

    const attachmentSource = mimeSource.includes(";base64,")
      ? `${mimeSource}${encodedContent}`
      : `${mimeSource};base64,${encodedContent}`;
    const previewWindow = window.open("", "_blank");
    if (!previewWindow) {
      return;
    }

    previewWindow.document.write(
      `<iframe src="${attachmentSource}" frameborder="0" style="border:0; top:0; left:0; bottom:0; right:0; width:100%; height:100%;" allowfullscreen></iframe>`,
    );
    previewWindow.document.close();
  };

  const renderExpandedRow = ({ data }) => {
    const primaryResultActive = isPrimaryResultActive(data);
    const primaryResultLabel =
      typeof data?.resultName === "string" && data.resultName.trim().length > 0
        ? data.resultName.trim()
        : intl.formatMessage({ id: "column.name.result" });
    const primaryResultValue = getDisplayPrimaryResult(data);
    const additionalDefinitions = Array.isArray(
      data?.additionalFieldDefinitions,
    )
      ? data.additionalFieldDefinitions.filter(
          (fieldDefinition) => fieldDefinition?.active !== false,
        )
      : [];
    const additionalValues = data?.additionalFieldValues || {};
    const attachedFile = data?.resultFile;
    const groupedBlocks = [];
    const groupedByName = new Map();
    additionalDefinitions.forEach((fieldDefinition) => {
      const { blockName, entryScope } = getFieldBlockAndScope(fieldDefinition);
      if (!groupedByName.has(blockName)) {
        const group = { blockName, entryScope, fields: [] };
        groupedByName.set(blockName, group);
        groupedBlocks.push(group);
      }
      groupedByName.get(blockName).fields.push(fieldDefinition);
    });
    const officialBlockName = intl.formatMessage({
      id: "results.block.official",
      defaultMessage: "Official",
    });
    const hasOfficialScopeBlock = groupedBlocks.some(
      (block) => normalizeResultEntryScope(block.entryScope) === "OFFICIAL",
    );
    if (!hasOfficialScopeBlock) {
      groupedBlocks.unshift({
        blockName: officialBlockName,
        entryScope: "OFFICIAL",
        fields: [],
      });
    }

    return (
      <div style={{ padding: "1rem 1.25rem" }}>
        <Grid>
          <Column lg={16} md={8} sm={4}>
            <h5 style={{ marginBottom: "0.5rem" }}>
              {intl.formatMessage({
                id: "validation.expand.results.title",
                defaultMessage: "Results",
              })}
            </h5>
          </Column>
          {(() => {
            let primaryRendered = false;
            return groupedBlocks.map((block, blockIndex) => {
              const shouldRenderPrimary =
                primaryResultActive &&
                !primaryRendered &&
                normalizeResultEntryScope(block.entryScope) === "OFFICIAL";
              if (shouldRenderPrimary) {
                primaryRendered = true;
              }
              const blockTitle = block.blockName || officialBlockName;
              return (
                <React.Fragment
                  key={`validation-block-${data?.analysisId || data?.id}-${blockIndex}-${blockTitle}`}
                >
                  <Column lg={16} md={8} sm={4}>
                    <h6
                      style={{ marginBottom: "0.5rem", marginTop: "0.25rem" }}
                    >
                      {blockTitle}
                    </h6>
                  </Column>
                  {shouldRenderPrimary && (
                    <Column lg={4} md={4} sm={4}>
                      <div style={{ marginBottom: "0.5rem" }}>
                        <div
                          style={{
                            fontSize: "0.75rem",
                            color: "#6f6f6f",
                            marginBottom: "0.2rem",
                          }}
                        >
                          {primaryResultLabel}
                        </div>
                        <div style={{ wordBreak: "break-word" }}>
                          {primaryResultValue || "-"}
                        </div>
                      </div>
                    </Column>
                  )}
                  {block.fields.map((fieldDefinition, index) => {
                    const key =
                      fieldDefinition?.fieldKey ||
                      `validation-extra-field-${data?.analysisId || data?.id}-${index}`;
                    const value =
                      additionalValues[fieldDefinition?.fieldKey] || "";
                    const fieldType = String(
                      fieldDefinition?.fieldType || "TEXT",
                    ).toUpperCase();
                    const documentValue =
                      fieldType === "DOCUMENT"
                        ? parseDocumentFieldValue(value)
                        : null;
                    return (
                      <Column lg={4} md={4} sm={4} key={key}>
                        <div style={{ marginBottom: "0.5rem" }}>
                          <div
                            style={{
                              fontSize: "0.75rem",
                              color: "#6f6f6f",
                              marginBottom: "0.2rem",
                            }}
                          >
                            {fieldDefinition?.displayName ||
                              fieldDefinition?.fieldKey}
                          </div>
                          <div style={{ wordBreak: "break-word" }}>
                            {documentValue?.fileName ? (
                              <Link
                                style={{ cursor: "pointer" }}
                                onClick={() =>
                                  openAdditionalFieldDocument(
                                    data?.analysisId || data?.id,
                                    fieldDefinition?.fieldKey,
                                    documentValue,
                                  )
                                }
                              >
                                {documentValue.fileName}
                              </Link>
                            ) : (
                              value || "-"
                            )}
                          </div>
                        </div>
                      </Column>
                    );
                  })}
                </React.Fragment>
              );
            });
          })()}
        </Grid>

        {showResultLevelFileUpload && (
          <Grid style={{ marginTop: "0.75rem" }}>
            <Column lg={16} md={8} sm={4}>
              <h5 style={{ marginBottom: "0.5rem" }}>
                {intl.formatMessage({
                  id: "validation.expand.file.title",
                  defaultMessage: "Attached File",
                })}
              </h5>
              {attachedFile?.fileName ? (
                <Link
                  onClick={() => openResultAttachment(attachedFile)}
                  style={{ cursor: "pointer" }}
                >
                  {attachedFile.fileName}
                </Link>
              ) : (
                <p style={{ margin: 0 }}>
                  {intl.formatMessage({
                    id: "validation.expand.file.empty",
                    defaultMessage: "No file attached.",
                  })}
                </p>
              )}
            </Column>
          </Grid>
        )}
      </div>
    );
  };

  const isValidatedDisplayRow = (row) => row?.readOnly === true;
  const isRowLockedForCurrentUser = (row) =>
    isValidatedDisplayRow(row) ||
    (Boolean(row?.approvedByCurrentUser) && !currentUserIsMedicalValidator);

  const getDisplayPrimaryResult = (row) => {
    switch (row?.resultType) {
      case "M":
      case "C":
      case "D":
        return row?.dictionaryResults?.find((result) => result.id == row.result)
          ?.value;
      default:
        return row?.result;
    }
  };

  const renderCell = (row, index, column, id) => {
    const fullTestName = row.testName;
    const splitIndex = fullTestName.lastIndexOf("(");
    const testName = fullTestName.substring(0, splitIndex);
    const sampleType = fullTestName.substring(splitIndex);
    switch (column.id) {
      case "sampleInfo": {
        const sampleCode = row.accessionNumber;
        return (
          <>
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
            <div className="sampleInfo" data-testid="LabNo">
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
          <div className="sampleInfo" data-testid="sampleInfo">
            <br></br>
            {testName}
            <br></br>
            {sampleType}
          </div>
        );

      case "save":
        return (
          <>
            <div data-testid="Checkbox">
              <Field name="isAccepted">
                {({ field }) => (
                  <Checkbox
                    id={"resultList" + row.id + ".isAccepted"}
                    name={"resultList[" + row.id + "].isAccepted"}
                    labelText=""
                    value={true}
                    checked={Boolean(row?.isAccepted)}
                    disabled={
                      validationLocked || isRowLockedForCurrentUser(row)
                    }
                    onChange={(e) => handleCheckBox(e, row.id)}
                  />
                )}
              </Field>
            </div>
          </>
        );
      case "validationDate":
        return (
          <input
            id={"resultList" + row.id + ".validationDate"}
            name={"resultList[" + row.id + "].validationDate"}
            type="date"
            value={getRowValidationDateValue(row)}
            disabled={validationLocked || isRowLockedForCurrentUser(row)}
            onChange={(e) => handleValidationDateChange(e, row.id)}
            className="bx--text-input"
          />
        );
      case "approvals":
        return (
          <span>
            {Number(row?.approvedCount || 0)}/
            {Number(row?.requiredApprovals || 1)}
          </span>
        );

      case "retest":
        return (
          <>
            <Field name="isRejected">
              {({ field }) => (
                <Checkbox
                  id={"resultList" + row.id + ".isRejected"}
                  name={"resultList[" + row.id + "].isRejected"}
                  labelText=""
                  value={true}
                  checked={Boolean(row?.isRejected)}
                  disabled={validationLocked || isRowLockedForCurrentUser(row)}
                  onChange={(e) => handleCheckBox(e, row.id)}
                />
              )}
            </Field>
          </>
        );

      case "notes":
        return (
          <>
            <div className="note">
              <TextArea
                id={"resultList" + row.id + ".note"}
                name={"resultList[" + row.id + "].note"}
                disabled={validationLocked || isRowLockedForCurrentUser(row)}
                type="text"
                labelText=""
                rows={2}
                onChange={(e) => handleChange(e, row.id)}
              ></TextArea>
            </div>
          </>
        );

      case "pastNotes":
        return (
          <>
            <div
              className="note"
              dangerouslySetInnerHTML={{ __html: row.pastNotes }}
            />
          </>
        );

      case "result":
        return getDisplayPrimaryResult(row);

      default:
    }
    return row.result;
  };

  const liveResultList = props?.results?.resultList || [];
  const pendingLiveResults = liveResultList.filter((row) => !row?.readOnly);
  const hasLiveResults = pendingLiveResults.length > 0;
  const hasValidatedRows = liveResultList.some((row) => row?.readOnly);
  const displayResultList = liveResultList;
  const validationLocked = !hasLiveResults || !currentUserIsMedicalValidator;
  const acceptedAnalysisCount = getAcceptedAnalysisIds().length;
  const acceptedRowsMissingValidationDate = liveResultList.filter(
    (row) =>
      row?.isAccepted &&
      !row?.readOnly &&
      !isRowLockedForCurrentUser(row) &&
      !getRowValidationDateValue(row),
  );
  const hasAcceptedRowsMissingValidationDate =
    acceptedRowsMissingValidationDate.length > 0;
  const rejectedAnalysisCount = getRejectedAnalysisIds().length;
  const requiresMedicalPreview =
    currentUserIsMedicalValidator && acceptedAnalysisCount > 0;
  const canSave =
    !validationLocked &&
    (acceptedAnalysisCount > 0 || rejectedAnalysisCount > 0) &&
    !hasAcceptedRowsMissingValidationDate &&
    (!requiresMedicalPreview || (hasOpenedPreview && previewConfirmed));
  const canDownload =
    savedAnalysisIds.length > 0 || (!hasLiveResults && hasValidatedRows);

  return (
    <>
      {hasLiveResults && (
        <Grid style={{ marginTop: "20px" }} className="gridBoundary">
          <Column lg={5} md={4} sm={4}>
            <Checkbox
              id={"saveallnormal"}
              name={"autochecks"}
              labelText={intl.formatMessage({ id: "validation.accept.normal" })}
              disabled={validationLocked}
              onChange={(e) => {
                if (validationLocked) {
                  return;
                }
                const nomalResults = liveResultList?.filter(
                  (result) =>
                    result.normal == true &&
                    !result.readOnly &&
                    !result.approvedByCurrentUser,
                );
                nomalResults.forEach((result) => {
                  const checkbox = document.getElementById(
                    "resultList" + result.id + ".isAccepted",
                  );
                  checkbox.checked = e.target.checked;
                  handleAutomatedCheck(e.target.checked, checkbox.name);
                });
              }}
            />
          </Column>
          <Column lg={5} md={4} sm={4}>
            <Checkbox
              id={"saveallresults"}
              name={"autochecks"}
              labelText={intl.formatMessage({ id: "validation.accept.all" })}
              disabled={validationLocked}
              onChange={(e) => {
                if (validationLocked) {
                  return;
                }
                const nomalResults = liveResultList.filter(
                  (result) => !result.readOnly && !result.approvedByCurrentUser,
                );
                nomalResults.forEach((result) => {
                  const checkbox = document.getElementById(
                    "resultList" + result.id + ".isAccepted",
                  );
                  checkbox.checked = e.target.checked;
                  handleAutomatedCheck(e.target.checked, checkbox.name);
                });
              }}
            />
          </Column>
          <Column lg={5} md={4} sm={4}>
            <Checkbox
              id={"retestalltests"}
              name={"autochecks"}
              labelText={intl.formatMessage({ id: "validation.reject.all" })}
              disabled={validationLocked}
              onChange={(e) => {
                if (validationLocked) {
                  return;
                }
                const nomalResults = liveResultList.filter(
                  (result) => !result.readOnly && !result.approvedByCurrentUser,
                );
                nomalResults.forEach((result) => {
                  const checkbox = document.getElementById(
                    "resultList" + result.id + ".isRejected",
                  );
                  checkbox.checked = e.target.checked;
                  handleAutomatedCheck(e.target.checked, checkbox.name);
                });
              }}
            />
          </Column>
        </Grid>
      )}
      <Formik
        initialValues={ValidationSearchFormValues}
        //validationSchema={}
        onSubmit
        onChange
      >
        {({ values, errors, touched, handleChange }) => (
          <Form onChange={handleChange}>
            <DataTable
              data={
                displayResultList
                  ? displayResultList.slice(
                      (page - 1) * pageSize,
                      page * pageSize,
                    )
                  : []
              }
              columns={visibleColumns}
              expandableRows
              expandableRowsComponent={renderExpandedRow}
              isSortable
            ></DataTable>
            <Pagination
              onChange={handlePageChange}
              page={page}
              pageSize={pageSize}
              pageSizes={[10, 20, 30, 50, 100]}
              totalItems={displayResultList ? displayResultList.length : 0}
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

            <Grid style={{ marginTop: "16px" }}>
              <Column lg={16} md={8} sm={4}>
                {currentUserIsMedicalValidator && (
                  <Button
                    type="button"
                    onClick={handlePreview}
                    kind="secondary"
                    style={{ marginRight: "0.75rem", marginBottom: "0.75rem" }}
                    disabled={
                      validationLocked ||
                      !hasLiveResults ||
                      hasAcceptedRowsMissingValidationDate ||
                      acceptedAnalysisCount === 0 ||
                      isSubmitting
                    }
                  >
                    <FormattedMessage
                      id="validation.preview.button"
                      defaultMessage="Preview Report"
                    />
                  </Button>
                )}

                <Button
                  type="button"
                  onClick={() => handleSave(values)}
                  id="submit"
                  style={{ marginRight: "0.75rem", marginBottom: "0.75rem" }}
                  data-testid="Save-btn"
                  disabled={!canSave || isSubmitting}
                >
                  <FormattedMessage id="label.button.save" />
                </Button>

                <Button
                  type="button"
                  onClick={handleDownloadValidatedReport}
                  kind="tertiary"
                  style={{ marginBottom: "0.75rem" }}
                  disabled={!canDownload || isSubmitting}
                >
                  <FormattedMessage
                    id="validation.download.button"
                    defaultMessage="Download Validated Report"
                  />
                </Button>
              </Column>

              {currentUserIsMedicalValidator && (
                <Column lg={16} md={8} sm={4}>
                  {previewOptions.length > 1 && (
                    <p style={{ marginBottom: "0.5rem" }}>
                      {intl.formatMessage(
                        {
                          id: "validation.preview.progress",
                          defaultMessage:
                            "Reviewed previews: {reviewed}/{total}.",
                        },
                        {
                          reviewed: previewedOptionIds.length,
                          total: previewOptions.length,
                        },
                      )}
                    </p>
                  )}
                  <Checkbox
                    id="preview-reviewed-confirmation"
                    labelText={intl.formatMessage({
                      id: "validation.preview.confirm.checkbox",
                      defaultMessage:
                        "I reviewed the preview and it is ready to validate.",
                    })}
                    checked={previewConfirmed}
                    disabled={
                      validationLocked || !hasOpenedPreview || isSubmitting
                    }
                    onChange={(e) => setPreviewConfirmed(e.target.checked)}
                  />
                </Column>
              )}
            </Grid>
          </Form>
        )}
      </Formik>
      <Modal
        open={previewSelectionModalOpen}
        modalHeading={intl.formatMessage({
          id: "validation.preview.selection.title",
          defaultMessage: "Choose Test Preview",
        })}
        primaryButtonText={intl.formatMessage({
          id: "validation.preview.selection.primary",
          defaultMessage: "Open Preview",
        })}
        secondaryButtonText={intl.formatMessage({
          id: "label.button.cancel",
          defaultMessage: "Cancel",
        })}
        onRequestClose={() => setPreviewSelectionModalOpen(false)}
        onRequestSubmit={confirmPreviewSelection}
        primaryButtonDisabled={!selectedPreviewOption}
      >
        <p style={{ marginBottom: "1rem" }}>
          <FormattedMessage
            id="validation.preview.selection.description"
            defaultMessage="Multiple tests are selected. Open and review each test preview before validating."
          />
        </p>
        <RadioButtonGroup
          legendText=""
          name="validated-preview-test-selector"
          valueSelected={selectedPreviewOption}
          onChange={(value) => setSelectedPreviewOption(value)}
        >
          {previewOptions.map((option) => (
            <RadioButton
              key={option.id}
              id={`preview-report-option-${option.id}`}
              labelText={`${previewedOptionIds.includes(option.id) ? "✓ " : ""}${option.label}`}
              value={option.id}
            />
          ))}
        </RadioButtonGroup>
      </Modal>
      <Modal
        open={previewModalOpen}
        modalHeading={intl.formatMessage({
          id: "validation.preview.modal.title",
          defaultMessage: "Validated Report Preview",
        })}
        passiveModal
        onRequestClose={closePreviewModal}
        size="lg"
      >
        <div
          style={{
            minHeight: "24rem",
            maxHeight: "70vh",
            overflowY: "auto",
            backgroundColor: "#f4f4f4",
            border: "1px solid #e0e0e0",
            padding: "0.25rem",
          }}
        >
          {previewLoading && (
            <p>
              <FormattedMessage
                id="validation.preview.loading"
                defaultMessage="Loading preview..."
              />
            </p>
          )}
          {!previewLoading && previewError && <p>{previewError}</p>}
          {!previewLoading && !previewError && !!previewPdfUrl && (
            <iframe
              title={intl.formatMessage({
                id: "validation.preview.iframe.title",
                defaultMessage: "Validated report preview",
              })}
              src={`${previewPdfUrl}#toolbar=0&navpanes=0&scrollbar=1`}
              style={{
                width: "100%",
                minHeight: "64vh",
                border: "none",
                backgroundColor: "#ffffff",
              }}
            />
          )}
        </div>
      </Modal>
      <Modal
        open={downloadModalOpen}
        modalHeading={intl.formatMessage({
          id: "validation.download.modal.title",
          defaultMessage: "Choose Test Report",
        })}
        primaryButtonText={intl.formatMessage({
          id: "validation.download.modal.primary",
          defaultMessage: "Download",
        })}
        secondaryButtonText={intl.formatMessage({
          id: "label.button.cancel",
          defaultMessage: "Cancel",
        })}
        onRequestClose={() => setDownloadModalOpen(false)}
        onRequestSubmit={confirmDownloadSelection}
        primaryButtonDisabled={!selectedDownloadOption}
      >
        <p style={{ marginBottom: "1rem" }}>
          <FormattedMessage
            id="validation.download.modal.description"
            defaultMessage="This order has validated results from multiple tests. Select which test report to download."
          />
        </p>
        <RadioButtonGroup
          legendText=""
          name="validated-report-test-selector"
          valueSelected={selectedDownloadOption}
          onChange={(value) => setSelectedDownloadOption(value)}
        >
          {downloadOptions.map((option) => (
            <RadioButton
              key={option.id}
              id={`download-report-option-${option.id}`}
              labelText={option.label}
              value={option.id}
            />
          ))}
        </RadioButtonGroup>
      </Modal>
    </>
  );
};

export default Validation;
