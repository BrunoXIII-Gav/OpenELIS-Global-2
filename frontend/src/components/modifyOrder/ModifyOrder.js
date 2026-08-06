import React, { useContext, useEffect, useState, useRef } from "react";
import { useParams } from "react-router-dom";
import {
  Button,
  ProgressIndicator,
  ProgressStep,
  Stack,
  Section,
  Tag,
  Grid,
  Column,
} from "@carbon/react";
import EditSample from "./EditSample";
import AddOrder from "../addOrder/AddOrder";
import "../addOrder/add-order.scss";
import { ModifyOrderFormValues } from "../formModel/innitialValues/OrderEntryFormValues";
import { NotificationContext, ConfigurationContext } from "../layout/Layout";
import { AlertDialog, NotificationKinds } from "../common/CustomNotification";
import {
  postToOpenElisServerFullResponse,
  getFromOpenElisServer,
} from "../utils/Utils";
import EditOrderEntryAdditionalQuestions from "./EditOrderEntryAdditionalQuestions";
import OrderSuccessMessage from "../addOrder/OrderSuccessMessage";
import { FormattedMessage, useIntl } from "react-intl";
import PatientHeader from "../common/PatientHeader";
import PageBreadCrumb from "../common/PageBreadCrumb";
import ModifyOrderEntryValidationSchema from "../formModel/validationSchema/ModifyOrderEntryValidationSchema";
let breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "sample.label.search.Order", link: "/SampleEdit" },
];

const ModifyOrder = () => {
  const componentMounted = useRef(false);

  const intl = useIntl();
  const { configurationProperties } = useContext(ConfigurationContext);

  const showProgramStep =
    configurationProperties.SHOW_ORDER_PROGRAM_ON_ORDER_ENTRY !== "false";
  const firstPageNumber = 0;
  const programPageNumber = showProgramStep ? firstPageNumber : -1;
  const samplePageNumber = showProgramStep
    ? firstPageNumber + 1
    : firstPageNumber;
  const orderPageNumber = showProgramStep
    ? firstPageNumber + 2
    : firstPageNumber + 1;
  const successMsgPageNumber = showProgramStep
    ? firstPageNumber + 3
    : firstPageNumber + 2;
  const lastPageNumber = successMsgPageNumber;

  const [page, setPage] = useState(firstPageNumber);
  const [orderFormValues, setOrderFormValues] = useState(ModifyOrderFormValues);
  const [samples, setSamples] = useState([]);
  const [errors, setErrors] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [patientId, setPatientId] = useState("");
  const [changed, setChanged] = useState({
    "sampleOrderItems.providerFirstName": false,
    "sampleOrderItems.providerLastName": false,
    "sampleOrderItems.labNo": false,
  });

  useEffect(() => {
    componentMounted.current = true;
    let patientIdParam = new URLSearchParams(window.location.search).get(
      "patientId",
    );
    let accessionNumber = new URLSearchParams(window.location.search).get(
      "accessionNumber",
    );
    accessionNumber = accessionNumber ? accessionNumber : "";
    patientIdParam = patientIdParam ? patientIdParam : "";

    // If searching by accession number and no patientId, fetch patient from accession number
    if (!patientIdParam && accessionNumber) {
      getFromOpenElisServer(
        "/rest/patientByLabNumer?accessionNumber=" + accessionNumber,
        (response) => {
          if (componentMounted.current && response && response.id) {
            setPatientId(response.id);
          }
        },
      );
    } else {
      setPatientId(patientIdParam);
    }

    getFromOpenElisServer(
      "/rest/SampleEdit?patientId=" +
        patientIdParam +
        "&accessionNumber=" +
        accessionNumber,
      loadOrderValues,
    );
    return () => {
      componentMounted.current = false;
    };
  }, []);

  useEffect(() => {
    ModifyOrderEntryValidationSchema.validate(orderFormValues, {
      abortEarly: false,
    })
      .then((validData) => {
        setErrors([]);
        console.debug("Valid Data:", validData);
      })
      .catch((errors) => {
        setErrors(errors);
        console.error("Validation Errors:", errors.errors);
      });
  }, [changed, orderFormValues]);

  const loadOrderValues = (data) => {
    if (componentMounted.current) {
      if (data.sampleOrderItems) {
        data.sampleOrderItems.referringSiteName = "";
        setOrderFormValues(data);
      }
    }
  };

  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const showAlertMessage = (msg, kind) => {
    setNotificationVisible(true);
    addNotification({
      kind: kind,
      title: intl.formatMessage({ id: "notification.title" }),
      message: msg,
    });
  };

  const sanitizeServerMessage = (message) => {
    const trimmedMessage = String(message || "").trim();
    if (
      !trimmedMessage ||
      trimmedMessage.startsWith("{") ||
      trimmedMessage.startsWith("<")
    ) {
      return "";
    }
    return trimmedMessage;
  };

  const handlePost = async (response) => {
    setIsSubmitting(false);
    if (response.status === 200) {
      showAlertMessage(
        <FormattedMessage id="save.order.success.msg" />,
        NotificationKinds.success,
      );
      window.location.assign("/SampleEdit");
    } else {
      if (response.status === 413) {
        showAlertMessage(
          intl.formatMessage({ id: "server.error.requestTooLarge" }),
          NotificationKinds.error,
        );
        return;
      }

      let detailedMessage = "";
      try {
        detailedMessage = await response.text();
      } catch (error) {
        detailedMessage = "";
      }
      detailedMessage = sanitizeServerMessage(detailedMessage);
      const genericMessage = intl.formatMessage({ id: "server.error.msg" });
      const fallbackWithStatus =
        response.status > 0
          ? `${genericMessage} (HTTP ${response.status})`
          : genericMessage;
      showAlertMessage(
        detailedMessage || fallbackWithStatus,
        NotificationKinds.error,
      );
    }
  };
  const handleSubmitOrderForm = (e) => {
    e.preventDefault();
    if (isSubmitting) {
      return;
    }
    setIsSubmitting(true);
    const payload = JSON.parse(JSON.stringify(orderFormValues));
    payload.sampleOrderItems.modified = true;
    //remove display Lists rom the form
    payload.sampleOrderItems.priorityList = [];
    payload.sampleOrderItems.programList = [];
    payload.sampleOrderItems.referringSiteList = [];
    payload.initialSampleConditionList = [];
    payload.testSectionList = [];
    payload.sampleOrderItems.providersList = [];
    payload.sampleOrderItems.paymentOptions = [];
    payload.sampleOrderItems.testLocationCodeList = [];
    console.log(JSON.stringify(payload));
    postToOpenElisServerFullResponse(
      "/rest/SampleEdit",
      JSON.stringify(payload),
      handlePost,
    );
  };

  const elementError = (path) => {
    if (errors?.errors?.length > 0) {
      let error = errors.inner?.find((e) => e.path === path);
      if (error) {
        return error.message;
      } else {
        return null;
      }
    }
  };
  useEffect(() => {
    if (page === samplePageNumber + 1) {
      attacheSamplesToFormValues();
    }
  }, [page]);

  const attacheSamplesToFormValues = () => {
    let sampleXmlString = "";
    let referralItems = [];
    if (samples.length > 0) {
      if (samples[0].tests.length > 0) {
        sampleXmlString = '<?xml version="1.0" encoding="utf-8"?>';
        sampleXmlString += "<samples>";
        let tests = null;
        samples.map((sampleItem) => {
          if (sampleItem.tests.length > 0) {
            tests = Object.keys(sampleItem.tests)
              .map(function (i) {
                return sampleItem.tests[i].id;
              })
              .join(",");

            // Extract storage location data if present
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
            const cugCode = sampleItem.sampleXML?.cug || "";
            const cugReservationToken =
              sampleItem.sampleXML?.cugReservationToken || "";
            const cugReservationContextId =
              sampleItem.sampleXML?.cugReservationContextId || "";

            sampleXmlString += `<sample sampleID='${sampleItem.sampleTypeId}' date='${sampleItem.sampleXML.collectionDate}' time='${sampleItem.sampleXML.collectionTime}' collector='${sampleItem.sampleXML.collector}' tests='${tests}' testSectionMap='' testSampleTypeMap='' panels='' rejected='${sampleItem.sampleXML.rejected}' rejectReasonId='${sampleItem.sampleXML.rejectionReason}' cug='${cugCode}' cugReservationToken='${cugReservationToken}' cugReservationContextId='${cugReservationContextId}' initialConditionIds='' storageLocationId='${storageLocationId}' storageLocationType='${storageLocationType}' storagePositionCoordinate='${storagePositionCoordinate}' />`;
          }
          if (sampleItem.referralItems.length > 0) {
            const referredInstitutes = Object.keys(sampleItem.referralItems)
              .map(function (i) {
                return sampleItem.referralItems[i].institute;
              })
              .join(",");

            const sentDates = Object.keys(sampleItem.referralItems)
              .map(function (i) {
                return sampleItem.referralItems[i].sentDate;
              })
              .join(",");

            const referralReasonIds = Object.keys(sampleItem.referralItems)
              .map(function (i) {
                return sampleItem.referralItems[i].reasonForReferral;
              })
              .join(",");

            const referrers = Object.keys(sampleItem.referralItems)
              .map(function (i) {
                return sampleItem.referralItems[i].referrer;
              })
              .join(",");
            referralItems.push({
              referrer: referrers,
              referredInstituteId: referredInstitutes,
              referredTestId: tests,
              referredSendDate: sentDates,
              referralReasonId: referralReasonIds,
            });
          }
        });
        sampleXmlString += "</samples>";
      }
    }
    setOrderFormValues({
      ...orderFormValues,
      // useReferral: true,
      sampleXML: sampleXmlString,
      // referralItems: referralItems,
    });
  };

  const navigateForward = () => {
    if (page <= lastPageNumber && page >= firstPageNumber) {
      setPage(page + 1);
    }
  };

  const navigateBackWards = () => {
    if (page > firstPageNumber) {
      setPage(page + -1);
    }
  };
  const handleTabClickHandler = (e) => {
    setPage(e);
  };

  return (
    <>
      <PageBreadCrumb breadcrumbs={breadcrumbs} />

      <PatientHeader
        id={patientId}
        patientName={orderFormValues?.patientName}
        gender={orderFormValues?.gender}
        dob={orderFormValues?.dob}
        nationalId={orderFormValues?.nationalId}
        accesionNumber={orderFormValues?.accessionNumber}
        className="patient-header3"
        isOrderPage={true}
      >
        {" "}
      </PatientHeader>
      <Grid>
        <Column lg={16} md={8} sm={4}>
          <Stack gap={10}>
            <div className="pageContent">
              {notificationVisible === true ? <AlertDialog /> : ""}
              {orderFormValues?.sampleOrderItems && (
                <div className="orderWorkFlowDiv">
                  <h2>
                    <FormattedMessage id="order.test.request.heading" />
                  </h2>
                  {page <= orderPageNumber && (
                    <>
                      {showProgramStep ? (
                        <ProgressIndicator
                          currentIndex={page}
                          className="ProgressIndicator"
                          spaceEqually={true}
                          onChange={(e) => handleTabClickHandler(e)}
                        >
                          <ProgressStep
                            disabled={
                              orderFormValues.sampleOrderItems.labNo == ""
                            }
                            label={intl.formatMessage({
                              id: "order.step.program.selection",
                            })}
                          />
                          <ProgressStep
                            disabled={
                              orderFormValues.sampleOrderItems.labNo == ""
                            }
                            label={intl.formatMessage({
                              id: "order.step.add.request",
                            })}
                          />
                          <ProgressStep
                            disabled={
                              orderFormValues.sampleOrderItems.labNo == ""
                            }
                            label={intl.formatMessage({
                              id: "order.label.add",
                            })}
                          />
                        </ProgressIndicator>
                      ) : (
                        <ProgressIndicator
                          currentIndex={page}
                          className="ProgressIndicator"
                          spaceEqually={true}
                          onChange={(e) => handleTabClickHandler(e)}
                        >
                          <ProgressStep
                            disabled={
                              orderFormValues.sampleOrderItems.labNo == ""
                            }
                            label={intl.formatMessage({
                              id: "order.step.add.request",
                            })}
                          />
                          <ProgressStep
                            disabled={
                              orderFormValues.sampleOrderItems.labNo == ""
                            }
                            label={intl.formatMessage({
                              id: "order.label.add",
                            })}
                          />
                        </ProgressIndicator>
                      )}
                    </>
                  )}
                  {page === programPageNumber && (
                    <EditOrderEntryAdditionalQuestions
                      orderFormValues={orderFormValues}
                      setOrderFormValues={setOrderFormValues}
                    />
                  )}
                  {page === samplePageNumber && (
                    <EditSample
                      orderFormValues={orderFormValues}
                      setOrderFormValues={setOrderFormValues}
                      setSamples={setSamples}
                      samples={samples}
                      error={elementError}
                      patientId={patientId}
                    />
                  )}
                  {page === orderPageNumber && (
                    <AddOrder
                      orderFormValues={orderFormValues}
                      setOrderFormValues={setOrderFormValues}
                      samples={samples}
                      error={elementError}
                      isModifyOrder={true}
                      changed={changed}
                      setChanged={setChanged}
                    />
                  )}

                  {page === successMsgPageNumber && (
                    <OrderSuccessMessage
                      orderFormValues={orderFormValues}
                      setOrderFormValues={setOrderFormValues}
                      setSamples={setSamples}
                      setPage={setPage}
                    />
                  )}
                  <div className="navigationButtonsLayout">
                    {page !== firstPageNumber && page <= orderPageNumber && (
                      <Button
                        kind="tertiary"
                        onClick={() => navigateBackWards()}
                      >
                        <FormattedMessage id="back.action.button" />
                      </Button>
                    )}

                    {page < orderPageNumber && (
                      <Button
                        data-cy="next-button"
                        kind="primary"
                        className="forwardButton"
                        onClick={() => navigateForward()}
                      >
                        <FormattedMessage id="next.action.button" />
                      </Button>
                    )}

                    {page <= orderPageNumber && (
                      <Button
                        data-cy="submit-order"
                        kind="primary"
                        className="forwardButton"
                        onClick={handleSubmitOrderForm}
                        disabled={
                          isSubmitting || errors?.errors?.length > 0
                            ? true
                            : false
                        }
                      >
                        <FormattedMessage id="label.button.submit" />
                      </Button>
                    )}
                  </div>
                </div>
              )}
            </div>
          </Stack>
        </Column>
      </Grid>
    </>
  );
};

export default ModifyOrder;
