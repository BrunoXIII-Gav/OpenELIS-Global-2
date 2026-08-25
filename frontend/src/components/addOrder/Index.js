import React, { useContext, useEffect, useState } from "react";
import { Button, ProgressIndicator, ProgressStep, Stack } from "@carbon/react";
import PatientInfo from "./PatientInfo";
import AddSample from "./AddSample";
import AddOrder from "./AddOrder";
import "./add-order.scss";
import { createSampleOrderFormValues } from "../formModel/innitialValues/OrderEntryFormValues";
import { NotificationContext, ConfigurationContext } from "../layout/Layout";
import { AlertDialog, NotificationKinds } from "../common/CustomNotification";
import {
  getFromOpenElisServer,
  postToOpenElisServerFullResponse,
  postToOpenElisServerForBlob,
} from "../utils/Utils";
import { isAlternateOrderFlowSelected } from "../../utils/alternateOrderFlow";
import OrderEntryAdditionalQuestions from "./OrderEntryAdditionalQuestions";
import OrderSuccessMessage from "./OrderSuccessMessage";
import { FormattedMessage, useIntl } from "react-intl";
import OrderEntryValidationSchema from "../formModel/validationSchema/OrderEntryValidationSchema";
import config from "../../config.json";
import PageBreadCrumb from "../common/PageBreadCrumb";
let breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "sidenav.label.addorder", link: "/SamplePatientEntry" },
];

export const createSampleObject = () => ({
  index: 0,
  sampleRejected: false,
  rejectionReason: "",
  sampleTypeId: "",
  sampleXML: null,
  panels: [],
  tests: [],
  additionalFields: [],
  requestReferralEnabled: false,
  referralItems: [],
});
export const sampleObject = createSampleObject();
const Index = () => {
  const intl = useIntl();

  const firstPageNumber = 0;
  const patientInfoPageNumber = firstPageNumber;
  const { configurationProperties } = useContext(ConfigurationContext);
  const showProgramStep =
    configurationProperties.SHOW_ORDER_PROGRAM_ON_ORDER_ENTRY !== "false";
  const programPageNumber = showProgramStep ? firstPageNumber + 1 : -1;
  const samplePageNumber = showProgramStep
    ? firstPageNumber + 2
    : firstPageNumber + 1;
  const orderPageNumber = showProgramStep
    ? firstPageNumber + 3
    : firstPageNumber + 2;
  const successMsgPageNumber = showProgramStep
    ? firstPageNumber + 4
    : firstPageNumber + 3;
  const lastPageNumber = successMsgPageNumber;
  const [changed, setChanged] = useState({
    "sampleOrderItems.providerFirstName": false,
    "sampleOrderItems.providerLastName": false,
    "sampleOrderItems.labNo": false,
  });
  const [page, setPage] = useState(firstPageNumber);
  const [orderFormValues, setOrderFormValues] = useState(
    createSampleOrderFormValues,
  );
  const [samples, setSamples] = useState([createSampleObject()]);
  const [errors, setErrors] = useState([]);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isConsentDownloading, setIsConsentDownloading] = useState(false);
  const [hasOrderPermission, setHasOrderPermission] = useState(true);
  const [permissionsLoaded, setPermissionsLoaded] = useState(false);
  const [phoneValidation, setPhoneValidation] = useState({
    primaryPhone: { body: "", status: true },
    contactPhone: { body: "", status: true },
  });

  let SampleTypes = [];
  let sampleTypeMap = {};
  let CrossPanels = [];
  let CrossTests = [];
  let sampleTypeOrder;
  let crossSampleTypeMap = {};
  let crossSampleTypeOrderMap = {};

  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const isOrderReadOnly = !permissionsLoaded || !hasOrderPermission;

  useEffect(() => {
    if (configurationProperties.ACCEPT_EXTERNAL_ORDERS === "true") {
      const urlParams = new URLSearchParams(window.location.search);
      const externalId = urlParams.get("ID");
      checkOrderReferral(externalId);
    } else {
      setOrderFormValues((previous) => ({
        ...previous,
        sampleOrderItems: {
          ...previous.sampleOrderItems,
          externalOrderNumber: "",
        },
      }));
    }
  }, [configurationProperties.ACCEPT_EXTERNAL_ORDERS]);

  useEffect(() => {
    checkOrderReferral(orderFormValues.sampleOrderItems.externalOrderNumber);
  }, [orderFormValues.sampleOrderItems.externalOrderNumber]);

  useEffect(() => {
    getFromOpenElisServer("/rest/professional-profile-permissions", (response) => {
      if (response) {
        setHasOrderPermission(response.hasOrderPermission !== false);
      }
      setPermissionsLoaded(true);
    });
  }, []);

  useEffect(() => {
    if (!permissionsLoaded || hasOrderPermission) {
      return;
    }
    if (page === samplePageNumber) {
      setPage(orderPageNumber);
    }
  }, [
    hasOrderPermission,
    orderPageNumber,
    page,
    permissionsLoaded,
    samplePageNumber,
  ]);

  const checkOrderReferral = (externalOrderNumber) => {
    if (externalOrderNumber) {
      getLabOrder(externalOrderNumber, processLabOrderSuccess);
    }
  };

  const getLabOrder = (orderNumber, success, failure) => {
    if (!failure) {
      failure = () => {
        // Default failure handler - no-op
      };
    }

    fetch(
      config.serverBaseUrl +
        "/ajaxQueryXML?asJSON=true&provider=LabOrderSearchProvider&orderNumber=" +
        orderNumber,
      {
        method: "get",
        //indicator: 'throbbing',
        headers: {
          "X-CSRF-Token": localStorage.getItem("CSRF"),
        },
      },
    )
      .then((response) => response.json())
      .then((jsonResponse) => {
        success(jsonResponse);
      })
      .catch((error) => {
        console.error(error);
        if (error instanceof SyntaxError) {
          addNotification({
            title: intl.formatMessage({
              id: "notification.title",
            }),
            message: intl.formatMessage({
              id: "notification.response.syntax.error",
            }),
            kind: NotificationKinds.error,
          });
          setNotificationVisible(true);
        }
        failure();
      });
  };

  const processLabOrderSuccess = (labOrder) => {
    // clearOrderData();
    let message = labOrder.fieldmessage.message;
    let formField = labOrder.fieldmessage.formfield;
    let order = formField.order;

    let newOrderFormValues = { ...orderFormValues };

    SampleTypes = [];
    CrossPanels = [];
    CrossTests = [];
    sampleTypeMap = {};

    //TODO all these actions mimic other areas of the code. Possible rework could centralize these calls into a context
    if (message === "valid") {
      // PATIENT
      if (order.patient) {
        parsePatient(newOrderFormValues, order.patient);
      }

      // REQUESTER
      if (order.requester) {
        parseRequester(newOrderFormValues, order.requester);
      }

      if (order.requestingOrg) {
        parseRequestingOrg(newOrderFormValues, order.requestingOrg);
      }
      if (order.location && !order.requestingOrg.id) {
        parseLocation(newOrderFormValues, order.location);
      }

      if (order.user_alert) {
        alert(order.user_alert);
      }

      // initialize objects and globals
      sampleTypeOrder = -1;
      crossSampleTypeMap = {};
      crossSampleTypeOrderMap = {};

      if (order.sampleTypes != "") {
        parseSampletypes(
          newOrderFormValues,
          order.sampleTypes instanceof Array
            ? order.sampleTypes
            : [{ sampleType: order.sampleTypes.sampleType }],
          SampleTypes,
        );
      }

      const urlParams = new URLSearchParams(window.location.search);
      const externalId = urlParams.get("ID");
      const labNumber = urlParams.get("labNumber");

      newOrderFormValues = {
        ...newOrderFormValues,
        sampleOrderItems: {
          ...newOrderFormValues.sampleOrderItems,
          externalOrderNumber: externalId,
          labNo: labNumber,
        },
      };
      setOrderFormValues(newOrderFormValues);
      setSamples(SampleTypes);

      //TODO not translated over for 3.0 Unsure if needed
      // parseCrossPanels(
      //   order.crosspanel,
      //   crossSampleTypeMap,
      //   crossSampleTypeOrderMap,
      // );
      // parseCrossTests(
      //   order.crosstest,
      //   crossSampleTypeMap,
      //   crossSampleTypeOrderMap,
      // );
      // populateCrossPanelsAndTests(CrossPanels, CrossTests, '${entryDate}');
      // displaySampleTypes('${entryDate}');

      // if (SampleTypes.length > 0) sampleClicked(1);
    } else {
      alert(message);
    }

    // if (attemptAutoSave) {
    // let validToSave =  patientFormValid() && sampleEntryTopValid();
    // if (validToSave) {
    //   savePage();
    // }
    // }
  };

  const parsePatient = (newOrderFormValues, patient) => {
    newOrderFormValues.patientProperties = {
      ...newOrderFormValues.patientProperties,
      guid: patient.guid,
    };
  };

  const parseRequester = (newOrderFormValues, requester) => {
    const providerId = requester.personId;
    if (providerId) {
      newOrderFormValues.sampleOrderItems = {
        ...newOrderFormValues.sampleOrderItems,
        providerId: providerId,
        providerProfessionalProfileCode: "",
        providerProfileFields: [],
        providerProfileFieldValues: {},
      };
      getFromOpenElisServer(
        "/rest/practitioner?providerId=" + providerId,
        (data) => {
          const person = data?.person || {};
          setOrderFormValues((previous) => ({
            ...(previous || {}),
            sampleOrderItems: {
              ...((previous || {}).sampleOrderItems || {}),
              providerId: data?.id || "",
              providerPersonId: person.id || "",
              providerProfessionalProfileCode:
                data?.professionalProfileCode || "",
              providerFirstName: person.firstName || "",
              providerLastName: person.lastName || "",
              providerWorkPhone: person.workPhone || "",
              providerEmail: person.email || "",
              providerFax: person.fax || "",
              providerCmp: data?.npi || "",
              providerRne: data?.externalId || "",
              providerDni: data?.dni || "",
              providerSpecialty: data?.specialty || "",
            },
          }));
        },
      );
    } else {
      newOrderFormValues.sampleOrderItems = {
        ...newOrderFormValues.sampleOrderItems,
        providerFirstName: requester.firstName,
        providerLastName: requester.lastName,
        providerWorkPhone: requester.phone,
        providerEmail: requester.email,
        providerFax: requester.fax,
        providerCmp: requester.cmp || "",
        providerRne: requester.rne || "",
        providerDni: requester.dni || "",
        providerSpecialty: requester.specialty || "",
        providerProfessionalProfileCode: "",
        providerProfileFields: [],
        providerProfileFieldValues: {},
      };
    }
  };

  const parseRequestingOrg = (newOrderFormValues, requestingOrg) => {
    newOrderFormValues.sampleOrderItems = {
      ...newOrderFormValues.sampleOrderItems,
      referringSiteId: requestingOrg.id,
    };
    getFromOpenElisServer(
      "/rest/departments-for-site?refferingSiteId=" + requestingOrg.id,
      () => {
        // Departments loaded - handled elsewhere
      },
    );
  };

  const parseLocation = (newOrderFormValues, location) => {
    newOrderFormValues.sampleOrderItems = {
      ...newOrderFormValues.sampleOrderItems,
      referringSiteId: location.id,
    };
    getFromOpenElisServer(
      "/rest/departments-for-site?refferingSiteId=" + location.id,
      () => {
        // Departments loaded - handled elsewhere
      },
    );
  };

  const parseSampletypes = (newOrderFormValues, sampletypes, SampleTypes) => {
    let index = 0;
    for (let i = 0; i < sampletypes.length; i++) {
      index = parseSampletype(index, sampletypes[i].sampleType, SampleTypes);
    }
  };

  const parseSampletype = (index, sampleType, SampleTypes) => {
    let sampleTypeName = sampleType.name;
    let sampleTypeId = sampleType.id;
    let panels = sampleType.panels;
    let tests = sampleType.tests;
    let collection = sampleType.collection;
    let sampleTypeInList = sampleTypeMap[sampleTypeId];
    if (!sampleTypeInList) {
      index++;
      SampleTypes[index - 1] = newSampleType(
        sampleTypeId,
        sampleTypeName,
        index,
      );
      sampleTypeMap[sampleTypeId] = SampleTypes[index - 1];
      SampleTypes[index - 1].rowid = index;
      sampleTypeInList = SampleTypes[index - 1];
    }
    let panelnodes = getNodeNamesByTagName(panels, "panel");
    let testnodes = getNodeNamesByTagName(tests, "test");
    let collectionDate = collection.date;
    let collectionTime = collection.time;

    addPanelsToSampleType(sampleTypeInList, panelnodes);
    addTestsToSampleType(sampleTypeInList, testnodes);
    if (collectionDate) {
      sampleTypeInList.sampleXML.collectionDate = collectionDate;
    } else {
      sampleTypeInList.sampleXML.collectionDate =
        configurationProperties?.AUTOFILL_COLLECTION_DATE === "true"
          ? configurationProperties.currentDateAsText
          : "";
    }
    if (collectionTime) {
      sampleTypeInList.sampleXML.collectionTime = collectionTime;
    } else {
      sampleTypeInList.sampleXML.collectionTime =
        configurationProperties?.AUTOFILL_COLLECTION_DATE === "true"
          ? configurationProperties.currentTimeAsText
          : "";
    }
    return index;
  };

  // const parseCrossPanels = (
  //   crosspanels,
  //   crossSampleTypeMap,
  //   crossSampleTypeOrderMap,
  // ) => {
  //   for (let i = 0; i < crosspanels.length; i++) {
  //     var crossPanelName = crosspanels[i].name;
  //     var crossPanelId = crosspanels[i].id;
  //     var crossSampleTypes = crosspanels[i].crosssampletypes;

  //     CrossPanels[i] = newCrossPanel(crossPanelId, crossPanelName);
  //     CrossPanels[i].sampleTypes = getNodeNamesByTagName(
  //       crossSampleTypes,
  //       "crosssampletype",
  //     );
  //     CrossPanels[i].typeMap = [CrossPanels[i].sampleTypes.length];

  //     for (let j = 0; j < CrossPanels[i].sampleTypes.length; j = j + 1) {
  //       CrossPanels[i].typeMap[CrossPanels[i].sampleTypes[j].name] = "t";
  //       var sampleType = crossSampleTypeMap[CrossPanels[i].sampleTypes[j].id];

  //       if (sampleType === undefined) {
  //         crossSampleTypeMap[CrossPanels[i].sampleTypes[j].id] =
  //           CrossPanels[i].sampleTypes[j];
  //         sampleTypeOrder = sampleTypeOrder + 1;
  //         crossSampleTypeOrderMap[sampleTypeOrder] =
  //           CrossPanels[i].sampleTypes[j].id;
  //       }
  //     }
  //   }
  // };

  // const parseCrossTests = (
  //   crosstests,
  //   crossSampleTypeMap,
  //   crossSampleTypeOrderMap,
  // ) => {
  //   for (let x = 0; x < crosstests.length; x = x + 1) {
  //     var crossTestName = crosstests[x].name;
  //     var crossSampleTypes = crosstests[x].crosssampletypes;

  //     CrossTests[x] = newCrossTest(crossTestName);
  //     CrossTests[x].sampleTypes = getNodeNamesByTagName(
  //       crossSampleTypes,
  //       "crosssampletype",
  //     );
  //     CrossTests[x].typeMap = [CrossTests[x].sampleTypes.length];
  //     var sTypes = [];
  //     for (var y = 0; y < CrossTests[x].sampleTypes.length; y++) {
  //       //alert(crossTestName + " " + CrossTests[x].sampleTypes[y].id + " testid=" + CrossTests[x].sampleTypes[y].testId);
  //       sTypes[y] = CrossTests[x].sampleTypes[y];
  //       CrossTests[x].typeMap[CrossTests[x].sampleTypes[y].name] = "t";
  //       var sType = crossSampleTypeMap[CrossTests[x].sampleTypes[y].id];

  //       if (sType === undefined) {
  //         crossSampleTypeMap[CrossTests[x].sampleTypes[y].id] =
  //           CrossTests[x].sampleTypes[y];
  //         sampleTypeOrder++;
  //         crossSampleTypeOrderMap[sampleTypeOrder] =
  //           CrossTests[x].sampleTypes[y].id;
  //       }
  //     }
  //     crossTestSampleTypeTestIdMap[crossTestName] = sTypes;
  //   }
  // };

  function addPanelsToSampleType(sampleType, panelNodes) {
    for (let i = 0; i < panelNodes.length; i++) {
      sampleType.panels[sampleType.panels.length] = panelNodes[i];
    }
  }
  function addTestsToSampleType(sampleType, testNodes) {
    for (let i = 0; i < testNodes.length; i++) {
      sampleType.tests[sampleType.tests.length] = newTest(
        testNodes[i].id,
        testNodes[i].name,
      );
    }
  }

  function getNodeNamesByTagName(elements, tag) {
    //initialize helper objects
    let allTestsMap = {};
    let panelTestsMap = {};

    if (elements[tag] === undefined) {
      return [];
    }
    let nodes =
      elements[tag] instanceof Array ? elements[tag] : [elements[tag]];
    let objList = [];

    for (let j = 0; j < nodes.length; j++) {
      let name = nodes[j].name;
      let id = nodes[j].id;
      if (tag == "panel") {
        objList[j] = newPanel(id, name);
        let testNodes = nodes[j].panelTests;
        if (testNodes.length === undefined) {
          testNodes = [testNodes];
        }
        for (let x = 0; x < testNodes.length; x++) {
          let ptNodes = testNodes[x].test;
          for (let y = 0; y < ptNodes.length; y++) {
            let pName = ptNodes[y].name;
            let pId = ptNodes[y].id;
            if (objList[j].tests.length == 0) {
              objList[j].tests = pName;
              objList[j].testIds = pId;
            } else {
              objList[j].tests = objList[j].tests + "," + pName;
              objList[j].testIds = objList[j].testIds + "," + pId;
            }
          }
        }
      } else if (tag == "test") {
        objList[j] = newTest(id, name);
        allTestsMap[id] = name;
      } else if (tag == "crosssampletype") {
        let testtag = nodes[j].testid;
        if (testtag) {
          objList[j] = newCrossSampleType(id, name, testtag);
        } else objList[j] = newCrossSampleType(id, name);
      }
    }

    return objList;
  }

  const newSampleType = (id, name, index) => {
    return {
      index: index,
      sampleRejected: true,
      rejectionReason: "",
      requestReferralEnabled: false,
      referralItems: [],
      sampleTypeId: "" + id,
      sampleXML: {
        collectionDate: "",
        collector: "",
        quantity: "",
        uom: "",
        rejected: false,
        rejectionReason: "",
        collectionTime: "",
      },
      id: "" + id,
      name: name,
      panels: [],
      tests: [],
      // setCrossPanels: "false",
      // setCrossTests: "false",
      // crossPanels: [],
      // crossTests: [],
    };
  };

  const newPanel = (id, name) => {
    return {
      id: "" + id,
      name: name,
      tests: "",
      testIds: "",
    };
  };
  const newTest = (id, name) => {
    return { id: "" + id, name: name };
  };
  const newCrossSampleType = (id, name, testId) => {
    return {
      id: "" + id,
      name: name,
      testId: testId,
    };
  };
  const newCrossPanel = (id, name) => {
    return {
      id: "" + id,
      name: name,
      sampleTypes: [],
      typeMap: [],
    };
  };
  const newCrossTest = (name) => {
    return {
      name: name,
      sampleTypes: [],
      typeMap: [],
    };
  };

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
    return trimmedMessage.replace(/\s+@\s+[\w.$]+:\d+\s*$/, "").trim();
  };

  const formatOrderSubmissionError = (message) => {
    const normalizedMessage = String(message || "").trim();
    if (!normalizedMessage) {
      return normalizedMessage;
    }

    const duplicateExistingMatch = normalizedMessage.match(
      /Duplicate CUG value already exists:\s*([0-9]+\.[0-9]+)/i,
    );
    if (duplicateExistingMatch) {
      return intl.formatMessage(
        { id: "sample.cug.duplicate.existing.error" },
        { cugCode: duplicateExistingMatch[1] },
      );
    }

    const duplicateInRequestMatch = normalizedMessage.match(
      /Duplicate CUG value in request:\s*([0-9]+\.[0-9]+)/i,
    );
    if (duplicateInRequestMatch) {
      return intl.formatMessage(
        { id: "sample.cug.duplicate.request.error" },
        { cugCode: duplicateInRequestMatch[1] },
      );
    }

    if (/Invalid CUG format/i.test(normalizedMessage)) {
      return intl.formatMessage({ id: "sample.cug.manual.format.error" });
    }

    return normalizedMessage;
  };

  const handlePost = async (response) => {
    setIsSubmitting(false);
    if (response.status === 200) {
      showAlertMessage(
        <FormattedMessage id="save.order.success.msg" />,
        NotificationKinds.success,
      );
      setPage(page + 1);
    } else {
      if (response.status === 413) {
        showAlertMessage(
          intl.formatMessage({ id: "server.error.requestTooLarge" }),
          NotificationKinds.error,
        );
        return;
      }

      let detailedMessage =
        response.headers.get("X-OpenELIS-Error-Message") || "";
      if (!detailedMessage) {
        try {
          detailedMessage = await response.text();
        } catch (e) {
          detailedMessage = "";
        }
      }
      detailedMessage = sanitizeServerMessage(detailedMessage);
      detailedMessage = formatOrderSubmissionError(detailedMessage);
      const genericMessage = intl.formatMessage({ id: "server.error.msg" });
      const fallbackWithStatus = `${genericMessage} (HTTP ${response.status})`;
      showAlertMessage(
        detailedMessage || fallbackWithStatus,
        NotificationKinds.error,
      );
    }
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

  const handleSubmitOrderForm = (e) => {
    e.preventDefault();
    // Prevent multiple submissions.
    if (isSubmitting) {
      return;
    }
    const invalidCugSample = (samples || []).find(
      (sampleItem) =>
        String(sampleItem?.sampleXML?.cugValidationMessage || "").trim() !== "",
    );
    if (invalidCugSample) {
      showAlertMessage(
        invalidCugSample.sampleXML.cugValidationMessage,
        NotificationKinds.error,
      );
      return;
    }
    const missingCugSample = (samples || []).find((sampleItem) => {
      const cugValue = String(sampleItem?.sampleXML?.cug || "").trim();
      return cugValue === "";
    });
    if (missingCugSample) {
      showAlertMessage(
        intl.formatMessage({ id: "sample.cug.required" }),
        NotificationKinds.error,
      );
      return;
    }
    setIsSubmitting(true);
    const attachedSamplePayload = buildAttachedSamplesPayload();
    const payload = JSON.parse(
      JSON.stringify({
        ...orderFormValues,
        ...attachedSamplePayload,
      }),
    );
    payload.patientProperties = payload.patientProperties || {};
    payload.patientProperties.patientUpdateStatus =
      payload.patientProperties.patientUpdateStatus ||
      payload.patientUpdateStatus ||
      "ADD";

    if ("years" in payload.patientProperties) {
      delete payload.patientProperties.years;
    }
    if ("months" in payload.patientProperties) {
      delete payload.patientProperties.months;
    }
    if ("days" in payload.patientProperties) {
      delete payload.patientProperties.days;
    }
    if ("questionnaire" in payload.sampleOrderItems) {
      delete payload.sampleOrderItems.questionnaire;
    }
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
      "/rest/SamplePatientEntry",
      JSON.stringify(payload),
      handlePost,
    );
  };

  useEffect(() => {
    if (page === samplePageNumber + 1) {
      attacheSamplesToFormValues();
    }
  }, [page]);

  useEffect(() => {
    console.log(changed);
    OrderEntryValidationSchema.validate(orderFormValues, { abortEarly: false })
      .then((validData) => {
        setErrors([]);
        console.debug("Valid Data:", validData);
      })
      .catch((errors) => {
        setErrors(errors);
        console.error("Validation Errors:", errors.errors);
      });
  }, [changed, orderFormValues]);

  useEffect(() => {
    const labNumber = new URLSearchParams(window.location.search).get(
      "labNumber",
    );
    setOrderFormValues((previous) => ({
      ...previous,
      sampleOrderItems: {
        ...previous.sampleOrderItems,
        labNo: labNumber ? labNumber : "",
      },
    }));
  }, []);

  const buildAttachedSamplesPayload = () => {
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

    const alternateOrderFlowActive = isAlternateOrderFlowSelected(
      orderFormValues?.sampleOrderItems,
    );
    let sampleXmlString = "";
    let referralItems = [];
    const samplesToPersist = (samples || []).filter((sampleItem) => {
      if (!sampleItem || !sampleItem.sampleXML || !sampleItem.sampleTypeId) {
        return false;
      }
      return alternateOrderFlowActive || (sampleItem.tests || []).length > 0;
    });

    if (samplesToPersist.length > 0) {
      sampleXmlString = '<?xml version="1.0" encoding="utf-8"?>';
      sampleXmlString += "<samples>";
      samplesToPersist.forEach((sampleItem) => {
        const tests = Object.keys(sampleItem.tests || {})
          .map(function (i) {
            return sampleItem.tests[i].id;
          })
          .join(",");

        const panels = Object.keys(sampleItem.panels || {})
          .map(function (i) {
            return sampleItem.panels[i].id;
          })
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

        sampleXmlString += `<sample sampleID='${escapeXmlAttribute(sampleItem.sampleTypeId)}' date='${escapeXmlAttribute(sampleItem.sampleXML.collectionDate)}' time='${escapeXmlAttribute(sampleItem.sampleXML.collectionTime)}' collector='${escapeXmlAttribute(sampleItem.sampleXML.collector)}' quantity='${escapeXmlAttribute(sampleItem.sampleXML.quantity)}' uom='${escapeXmlAttribute(sampleItem.sampleXML.uom)}' tests='${escapeXmlAttribute(tests)}' testSectionMap='' testSampleTypeMap='' panels='${escapeXmlAttribute(panels)}' rejected='${escapeXmlAttribute(sampleItem.sampleXML.rejected)}' rejectReasonId='${escapeXmlAttribute(sampleItem.sampleXML.rejectionReason)}' cug='${escapeXmlAttribute(cugCode)}' cugReservationToken='${escapeXmlAttribute(cugReservationToken)}' cugReservationContextId='${escapeXmlAttribute(cugReservationContextId)}' initialConditionIds='' storageLocationId='${escapeXmlAttribute(storageLocationId)}' storageLocationType='${escapeXmlAttribute(storageLocationType)}' storagePositionCoordinate='${escapeXmlAttribute(storagePositionCoordinate)}' gpsLatitude='${escapeXmlAttribute(gpsLatitude)}' gpsLongitude='${escapeXmlAttribute(gpsLongitude)}' gpsAccuracy='${escapeXmlAttribute(gpsAccuracy)}' gpsCaptureMethod='${escapeXmlAttribute(gpsCaptureMethod)}'>`;
        if (additionalFieldEntries !== "") {
          sampleXmlString += `<additionalFields>${additionalFieldEntries}</additionalFields>`;
        }
        sampleXmlString += `</sample>`;

        if ((sampleItem.referralItems || []).length > 0) {
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

    return {
      useReferral: true,
      sampleXML: sampleXmlString,
      referralItems: referralItems,
    };
  };

  const attacheSamplesToFormValues = () => {
    setOrderFormValues({
      ...orderFormValues,
      ...buildAttachedSamplesPayload(),
    });
  };

  const navigateForward = () => {
    if (page < lastPageNumber && page >= firstPageNumber) {
      let nextPage = page + 1;
      if (!hasOrderPermission && nextPage === samplePageNumber) {
        nextPage = orderPageNumber;
      }
      setPage(nextPage);
    }
  };

  const navigateBackWards = () => {
    if (page > firstPageNumber) {
      setPage(page + -1);
    }
  };
  const handleTabClickHandler = (e) => {
    if (!hasOrderPermission && e === samplePageNumber) {
      return;
    }
    setPage(e);
  };

  const formatFullName = (firstName, lastName, fullName) => {
    const safeFullName = (fullName || "").trim();
    if (safeFullName) {
      return safeFullName;
    }
    const safeFirst = (firstName || "").trim();
    const safeLast = (lastName || "").trim();
    if (safeLast && safeFirst) {
      return `${safeLast}, ${safeFirst}`;
    }
    return safeLast || safeFirst;
  };

  const collectSelectedTests = () => {
    const names = [];
    (samples || []).forEach((sample) => {
      (sample?.tests || []).forEach((test) => {
        if (test?.name) {
          names.push(String(test.name).trim());
        }
      });
    });
    return Array.from(new Set(names.filter(Boolean)));
  };

  const buildConsentPayload = () => {
    const patient = orderFormValues?.patientProperties || {};
    const orderItems = orderFormValues?.sampleOrderItems || {};
    const providerFirst = orderItems.providerFirstName || "";
    const providerLast = orderItems.providerLastName || "";
    const providerDni = orderItems.providerDni || "";
    const firstSampleWithCug = (samples || []).find(
      (sample) => (sample?.sampleXML?.cug || "").trim() !== "",
    );
    return {
      patient: {
        firstName: patient.firstName || "",
        lastName: patient.lastName || "",
        fullName: formatFullName(
          patient.firstName,
          patient.lastName,
          patient.fullName,
        ),
        nationalId: patient.nationalId || "",
      },
      provider: {
        firstName: providerFirst,
        lastName: providerLast,
        fullName: formatFullName(providerFirst, providerLast),
        dni: providerDni,
      },
      selectedTests: collectSelectedTests(),
      orderAdditionalFieldValues: orderItems.additionalFieldValues || {},
      orderDate:
        orderItems.requestDate || configurationProperties?.currentDateAsText,
      cug: firstSampleWithCug?.sampleXML?.cug || "",
    };
  };

  const handleDownloadConsent = () => {
    if (isConsentDownloading) {
      return;
    }
    const payload = buildConsentPayload();
    if (!payload.selectedTests || payload.selectedTests.length === 0) {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "consent.download.noTests" }),
        kind: NotificationKinds.error,
      });
      setNotificationVisible(true);
      return;
    }
    setIsConsentDownloading(true);
    postToOpenElisServerForBlob(
      "/rest/reports/consent-template/preview",
      JSON.stringify(payload),
      (blob) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.target = "_blank";
        link.download = "consent-template.pdf";
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(url);
        setIsConsentDownloading(false);
      },
      () => {
        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({ id: "consent.download.error" }),
          kind: NotificationKinds.error,
        });
        setNotificationVisible(true);
        setIsConsentDownloading(false);
      },
    );
  };

  return (
    <>
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Stack gap={10}>
        <div className="pageContent">
          {notificationVisible === true ? <AlertDialog /> : ""}
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
                      complete
                      label={intl.formatMessage({
                        id: "order.step.patient.info",
                      })}
                    />
                    <ProgressStep
                      label={intl.formatMessage({
                        id: "order.step.program.selection",
                      })}
                    />
                    <ProgressStep
                      label={intl.formatMessage({
                        id: "order.step.add.request",
                      })}
                      disabled={isOrderReadOnly}
                    />
                    <ProgressStep
                      label={intl.formatMessage({ id: "order.label.add" })}
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
                      complete
                      label={intl.formatMessage({
                        id: "order.step.patient.info",
                      })}
                    />
                    <ProgressStep
                      label={intl.formatMessage({
                        id: "order.step.add.request",
                      })}
                      disabled={isOrderReadOnly}
                    />
                    <ProgressStep
                      label={intl.formatMessage({ id: "order.label.add" })}
                    />
                  </ProgressIndicator>
                )}
              </>
            )}

            {page === patientInfoPageNumber && (
              <PatientInfo
                orderFormValues={orderFormValues}
                setOrderFormValues={setOrderFormValues}
                error={elementError}
                setPhoneValidation={setPhoneValidation}
              />
            )}
            {page === programPageNumber && (
              <OrderEntryAdditionalQuestions
                orderFormValues={orderFormValues}
                setOrderFormValues={setOrderFormValues}
              />
            )}
            {page === samplePageNumber && (
              <AddSample
                error={elementError}
                setSamples={setSamples}
                samples={samples}
                orderFormValues={orderFormValues}
              />
            )}
            {page === orderPageNumber && (
              <AddOrder
                orderFormValues={orderFormValues}
                setOrderFormValues={setOrderFormValues}
                samples={samples}
                error={elementError}
                isModifyOrder={false}
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
                <Button kind="tertiary" onClick={() => navigateBackWards()}>
                  <FormattedMessage id="back.action.button" />
                </Button>
              )}

              {page < orderPageNumber && (
                <Button
                  kind="primary"
                  className="forwardButton"
                  onClick={() => navigateForward()}
                >
                  <FormattedMessage id="next.action.button" />
                </Button>
              )}

              {page === orderPageNumber && (
                <div className="orderPageActionButtons">
                  <Button
                    kind="secondary"
                    onClick={handleDownloadConsent}
                    disabled={isConsentDownloading}
                  >
                    <FormattedMessage id="consent.download.button" />
                  </Button>
                  <Button
                    kind="primary"
                    className="forwardButton"
                    disabled={
                      isOrderReadOnly ||
                      isSubmitting ||
                      Object.values(phoneValidation).some(
                        (item) => item.status === false,
                      ) ||
                      errors?.errors?.length > 0
                        ? true
                        : false
                    }
                    onClick={handleSubmitOrderForm}
                  >
                    <FormattedMessage id="label.button.submit" />
                  </Button>
                </div>
              )}
            </div>
          </div>
        </div>
      </Stack>
    </>
  );
};

export default Index;
