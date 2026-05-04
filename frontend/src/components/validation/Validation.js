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
import {
  convertAlphaNumLabNumForDisplay,
  postToOpenElisServer,
} from "../utils/Utils";
import { NotificationContext } from "../layout/Layout";
import { ConfigurationContext } from "../layout/Layout";
import config from "../../config.json";

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

  const validationReportName =
    configurationProperties?.validationReportName || DEFAULT_VALIDATION_REPORT;
  const currentUserIsMedicalValidator = Boolean(
    props?.results?.currentUserIsMedicalValidator,
  );
  const currentUserIsBiologistValidator = Boolean(
    props?.results?.currentUserIsBiologistValidator,
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

  const isRowReadyForMedicalValidation = (row) =>
    Number(row?.approvedCount || 0) >= Number(row?.requiredApprovals || 1);

  const getAcceptedAnalysisIds = () => {
    const acceptedRows =
      props?.results?.resultList?.filter(
        (result) =>
          result?.isAccepted &&
          result?.analysisId &&
          !result?.readOnly &&
          (!currentUserIsMedicalValidator || isRowReadyForMedicalValidation(result)) &&
          (!result?.approvedByCurrentUser || currentUserIsMedicalValidator),
      ) || [];
    return [...new Set(acceptedRows.map((result) => result.analysisId))];
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
      width: "15rem",
    },
    {
      id: "normalRange",
      name: intl.formatMessage({ id: "column.name.normalRange" }),
      selector: (row) => row.normalRange,
      sortable: true,
      width: "8rem",
    },
    {
      id: "result",
      name: intl.formatMessage({ id: "column.name.result" }),
      cell: (row, index, column, id) => {
        return renderCell(row, index, column, id);
      },
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

  const openPreviewByChoice = async (choice, optionsSnapshot = previewOptions) => {
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

    const reportChoices = buildDownloadChoices(acceptedAnalysisIds);
    const sameChoicesAsCurrent = areSameChoiceSet(reportChoices, previewOptions);

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
      return;
    }
    if (isSubmitting) {
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
    if (
      currentUserIsMedicalValidator &&
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
    if (currentUserIsMedicalValidator) {
      const selectedRows = liveResultList.filter(
        (row) => row?.isAccepted && !row?.readOnly,
      );
      const hasRowsWithoutBiologistApprovals = selectedRows.some(
        (row) => !isRowReadyForMedicalValidation(row),
      );
      if (hasRowsWithoutBiologistApprovals) {
        addNotification({
          kind: NotificationKinds.warning,
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({
            id: "validation.medical.approvals.required",
            defaultMessage:
              "Some selected rows still need biologist approvals before medical validation.",
          }),
        });
        setNotificationVisible(true);
        return;
      }
    }
    props.results.medicalValidationConfirmed =
      currentUserIsMedicalValidator && hasOpenedPreview && previewConfirmed;
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
          preferredReport:
            (row.testName || "").toLowerCase().includes("dmpk")
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
        currentUserIsMedicalValidator && Array.isArray(savedIds) ? savedIds : [];
      setSavedAnalysisIds(successfulSavedIds);
      const savedIdSet = new Set(successfulSavedIds);
      if (savedIdSet.size > 0 || currentUserIsBiologistValidator) {
        liveResultList.forEach((row) => {
          const shouldLockBiologistRow =
            currentUserIsBiologistValidator &&
            row?.analysisId &&
            row?.isAccepted &&
            !row?.readOnly;
          const shouldLockMedicalRow =
            savedIdSet.has(row?.analysisId) && !row?.readOnly;
          if (shouldLockBiologistRow || shouldLockMedicalRow) {
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
  const validateResults = (e, rowId) => {
    handleChange(e, rowId);
  };

  const openResultAttachment = (file) => {
    if (!file?.content || !file?.fileType) {
      return;
    }

    let encodedContent = file.content;
    if (Array.isArray(file.content)) {
      try {
        encodedContent = btoa(
          new Uint8Array(file.content).reduce(
            (accumulator, byte) => accumulator + String.fromCharCode(byte),
            "",
          ),
        );
      } catch (error) {
        return;
      }
    }

    const popup = window.open("", "_blank", "noopener,noreferrer");
    if (!popup) {
      return;
    }
    popup.document.write(
      `<iframe src="${file.fileType};base64,${encodedContent}" frameborder="0" style="border:0;position:fixed;top:0;left:0;bottom:0;right:0;width:100%;height:100%;" allowfullscreen></iframe>`,
    );
    popup.document.close();
  };

  const renderExpandedRow = ({ data }) => {
    const additionalDefinitions = Array.isArray(data?.additionalFieldDefinitions)
      ? data.additionalFieldDefinitions.filter(
          (fieldDefinition) => fieldDefinition?.active !== false,
        )
      : [];
    const additionalValues = data?.additionalFieldValues || {};
    const attachedFile = data?.resultFile;

    return (
      <div style={{ padding: "1rem 1.25rem" }}>
        <Grid>
          <Column lg={16} md={8} sm={4}>
            <h5 style={{ marginBottom: "0.5rem" }}>
              {intl.formatMessage({
                id: "validation.expand.additionalFields.title",
                defaultMessage: "Additional Fields",
              })}
            </h5>
          </Column>
          {additionalDefinitions.length === 0 && (
            <Column lg={16} md={8} sm={4}>
              <p style={{ margin: 0 }}>
                {intl.formatMessage({
                  id: "validation.expand.additionalFields.empty",
                  defaultMessage: "No additional fields.",
                })}
              </p>
            </Column>
          )}
          {additionalDefinitions.map((fieldDefinition, index) => {
            const key =
              fieldDefinition?.fieldKey ||
              `validation-extra-field-${data?.analysisId || data?.id}-${index}`;
            const value = additionalValues[fieldDefinition?.fieldKey] || "";
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
                    {fieldDefinition?.displayName || fieldDefinition?.fieldKey}
                  </div>
                  <div style={{ wordBreak: "break-word" }}>{value || "-"}</div>
                </div>
              </Column>
            );
          })}
        </Grid>

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
      </div>
    );
  };

  const isValidatedDisplayRow = (row) => row?.readOnly === true;
  const isRowLockedForCurrentUser = (row) =>
    isValidatedDisplayRow(row) ||
    (Boolean(row?.approvedByCurrentUser) && !currentUserIsMedicalValidator);

  const renderCell = (row, index, column, id) => {
    let formatLabNum = configurationProperties.AccessionFormat === "ALPHANUM";
    const fullTestName = row.testName;
    const splitIndex = fullTestName.lastIndexOf("(");
    const testName = fullTestName.substring(0, splitIndex);
    const sampleType = fullTestName.substring(splitIndex);
    switch (column.id) {
      case "sampleInfo":
        return (
          <>
            <Button
              onClick={async () => {
                if ("clipboard" in navigator) {
                  return await navigator.clipboard.writeText(
                    row.accessionNumber,
                  );
                } else {
                  return document.execCommand(
                    "copy",
                    true,
                    row.accessionNumber,
                  );
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
              {formatLabNum
                ? convertAlphaNumLabNumForDisplay(row.accessionNumber)
                : row.accessionNumber}
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
                      validationLocked ||
                      isRowLockedForCurrentUser(row) ||
                      (currentUserIsMedicalValidator &&
                        !isRowReadyForMedicalValidation(row))
                    }
                    onChange={(e) => handleCheckBox(e, row.id)}
                  />
                )}
              </Field>
            </div>
          </>
        );
      case "approvals":
        return (
          <span>
            {Number(row?.approvedCount || 0)}/{Number(row?.requiredApprovals || 1)}
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
        switch (row.resultType) {
          case "M":
          case "C":
          case "D":
            return (
              <>
                {
                  row.dictionaryResults.find(
                    (result) => result.id == row.result,
                  )?.value
                }
              </>
            );
          default:
            return row.result;
        }

      default:
    }
    return row.result;
  };

  const liveResultList = props?.results?.resultList || [];
  const pendingLiveResults = liveResultList.filter((row) => !row?.readOnly);
  const hasLiveResults = pendingLiveResults.length > 0;
  const hasValidatedRows = liveResultList.some((row) => row?.readOnly);
  const displayResultList = liveResultList;
  const validationLocked = !hasLiveResults;
  const acceptedAnalysisCount = getAcceptedAnalysisIds().length;
  const requiresMedicalPreview = currentUserIsMedicalValidator;
  const canSave =
    !validationLocked &&
    acceptedAnalysisCount > 0 &&
    (!requiresMedicalPreview || (hasOpenedPreview && previewConfirmed));
  const canDownload =
    savedAnalysisIds.length > 0 || (!hasLiveResults && hasValidatedRows);

  return (
    <>
      {hasLiveResults && (
        <Grid style={{ marginTop: "20px" }} className="gridBoundary">
          <Column lg={7} md={8} sm={2}>
            <picture>
              <img
                src={config.serverBaseUrl + "/images/nonconforming.gif"}
                alt="nonconforming"
                width="25" // Set your desired width
                height="20" // Set your desired height
              />
            </picture>
            <b>
              {" "}
              <FormattedMessage id="validation.label.nonconform" />
            </b>
          </Column>
          <Column lg={3} md={2} sm={4}>
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
                    !result.approvedByCurrentUser &&
                    (!currentUserIsMedicalValidator ||
                      isRowReadyForMedicalValidation(result)),
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
          <Column lg={3} md={2} sm={4}>
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
                  (result) =>
                    !result.readOnly &&
                    !result.approvedByCurrentUser &&
                    (!currentUserIsMedicalValidator ||
                      isRowReadyForMedicalValidation(result)),
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
          <Column lg={3} md={2} sm={4}>
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
                  (result) =>
                    !result.readOnly &&
                    !result.approvedByCurrentUser &&
                    (!currentUserIsMedicalValidator ||
                      isRowReadyForMedicalValidation(result)),
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
              columns={columns}
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
          id: "validation.preview.selection.secondary",
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
          id: "validation.download.modal.secondary",
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
