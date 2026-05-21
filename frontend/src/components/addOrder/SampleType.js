import {
  Checkbox,
  FormGroup,
  Layer,
  Loading,
  Search,
  Select,
  SelectItem,
  Tag,
  TextArea,
  TextInput,
  Tile,
} from "@carbon/react";
import { useCallback, useContext, useEffect, useRef, useState } from "react";
import { FormattedMessage, useIntl } from "react-intl";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import OrderReferralRequest from "../addOrder/OrderReferralRequest";
import CustomCheckBox from "../common/CustomCheckBox";
import CustomDatePicker from "../common/CustomDatePicker";
import { NotificationKinds } from "../common/CustomNotification";
import CustomSelect from "../common/CustomSelect";
import CustomTextInput from "../common/CustomTextInput";
import CustomTimePicker from "../common/CustomTimePicker";
import { sampleTypeTestsStructure } from "../data/SampleEntryTestsForTypeProvider";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import StorageLocationSelector from "../storage/StorageLocationSelector";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../utils/Utils";
import GpsCoordinatesCapture from "./GpsCoordinatesCapture";

const createCugReservationContextId = () => {
  if (typeof crypto !== "undefined" && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return `cug-${Date.now()}-${Math.floor(Math.random() * 1000000)}`;
};

const parseCugParts = (value) => {
  const normalized = String(value || "").trim();
  const splitIndex = normalized.lastIndexOf(".");
  if (splitIndex <= 0 || splitIndex === normalized.length - 1) {
    return null;
  }
  const prefixRaw = normalized.substring(0, splitIndex).trim();
  const suffixRaw = normalized.substring(splitIndex + 1).trim();
  if (!/^\d+$/.test(prefixRaw) || !/^\d+$/.test(suffixRaw)) {
    return null;
  }
  return {
    prefix: Number(prefixRaw),
    suffix: Number(suffixRaw),
  };
};

const SampleType = (props) => {
  const { userSessionDetails } = useContext(UserSessionDetailsContext);
  const { configurationProperties } = useContext(ConfigurationContext);

  const intl = useIntl();

  const componentMounted = useRef(false);
  const sampleTypesRef = useRef(null);
  const cugGenerationInFlightRef = useRef(false);
  const cugGenerationKeyRef = useRef("");
  const previousPatientCugKeyRef = useRef(
    String(props.patientCugKey || "").trim() || null,
  );

  const { index, rejectSampleReasons, sample } = props;

  const [sampleTypes, setSampleTypes] = useState([]);
  const [selectedSampleType, setSelectedSampleType] = useState({
    id: sample?.sampleTypeId || null,
    name: "",
    element_index: 0,
  });
  const [sampleTypeTests, setSampleTypeTests] = useState(
    sampleTypeTestsStructure,
  );
  const [selectedTests, setSelectedTests] = useState([
    ...(sample?.tests || []),
  ]);
  const [searchBoxTests, setSearchBoxTests] = useState([]);
  const [requestTestReferral, setRequestTestReferral] = useState(false);
  const [referralReasons, setReferralReasons] = useState([]);
  const [referralOrganizations, setReferralOrganizations] = useState([]);
  const [testSearchTerm, setTestSearchTerm] = useState("");
  const [referralRequests, setReferralRequests] = useState([]);
  const { setNotificationVisible, addNotification } =
    useContext(NotificationContext);
  const [rejectionReasonsDisabled, setRejectionReasonsDisabled] =
    useState(true);
  const [selectedPanels, setSelectedPanels] = useState([
    ...(sample?.panels || []),
  ]);
  const [panelSearchTerm, setPanelSearchTerm] = useState("");
  const [searchBoxPanels, setSearchBoxPanels] = useState([]);
  const [uomList, setUomList] = useState([]);
  const [sampleXml, setSampleXml] = useState(() => {
    if (sample?.sampleXML != null) {
      return {
        ...sample.sampleXML,
        cug: sample.sampleXML.cug || "",
        cugAutoReserved: sample.sampleXML.cugAutoReserved || "",
        cugValidationMessage: "",
        cugReservationToken: sample.sampleXML.cugReservationToken || "",
        cugReservationContextId:
          sample.sampleXML.cugReservationContextId ||
          createCugReservationContextId(),
        additionalFieldValues: sample.sampleXML.additionalFieldValues || {},
      };
    }
    return {
      collectionDate:
        configurationProperties?.AUTOFILL_COLLECTION_DATE === "true"
          ? configurationProperties.currentDateAsText
          : "",
      collector: "",
      quantity: "",
      uom: "",
      rejected: false,
      rejectionReason: "",
      collectionTime:
        configurationProperties?.AUTOFILL_COLLECTION_DATE === "true"
          ? configurationProperties.currentTimeAsText
          : "",
      cug: "",
      cugAutoReserved: "",
      cugValidationMessage: "",
      cugReservationToken: "",
      cugReservationContextId: createCugReservationContextId(),
      additionalFieldValues: {},
    };
  });
  const [loading, setLoading] = useState(true);
  const [sampleFixedFieldConfigs, setSampleFixedFieldConfigs] = useState([]);
  const [waitingForSampleFixedFieldConfig, setWaitingForSampleFixedFieldConfig] =
    useState(true);

  function getSampleFixedFieldConfig(fieldKey) {
    return sampleFixedFieldConfigs.find(
      (c) => c?.fieldKey?.toLowerCase() === fieldKey?.toLowerCase(),
    ) || null;
  }

  function isSampleFieldVisible(fieldKey) {
    if (waitingForSampleFixedFieldConfig && !sampleFixedFieldConfigs.length) {
      return false;
    }
    const config = getSampleFixedFieldConfig(fieldKey);
    return config ? config.visible !== false : true;
  }

  function isSampleFieldRequired(fieldKey, fallback = false) {
    const config = getSampleFixedFieldConfig(fieldKey);
    if (!config) {
      return fallback;
    }
    if (config.visible === false) {
      return false;
    }
    return config.required != null ? !!config.required : fallback;
  }

  const additionalFieldsVisible = isSampleFieldVisible("additionalFields");
  const referralFieldVisible = isSampleFieldVisible("referral");

  function handleCollectionDate(date) {
    setSampleXml({
      ...sampleXml,
      collectionDate: date,
    });
  }

  function handleReasons(value) {
    setSampleXml({
      ...sampleXml,
      rejectionReason: value,
    });
    props.sampleTypeObject({
      rejectionReason: value,
      sampleObjectIndex: index,
    });
  }

  function handleCollectionTime(time) {
    setSampleXml({
      ...sampleXml,
      collectionTime: time,
    });
  }

  function handleCollector(value) {
    setSampleXml({
      ...sampleXml,
      collector: value,
    });
  }

  const generateCugPreview = useCallback(() => {
    if (!props.canGenerateCug) {
      return;
    }
    const patientId = String(props.patientId || "").trim();
    const patientCugKey = String(props.patientCugKey || "").trim();
    const sampleTypeId = String(selectedSampleType.id || "").trim();
    const currentCug = String(sampleXml.cug || "").trim();
    if (!sampleTypeId || currentCug) {
      return;
    }
    const generationKey = `${index}|${sampleTypeId}|${patientId}|${patientCugKey}`;
    if (
      cugGenerationInFlightRef.current ||
      cugGenerationKeyRef.current === generationKey
    ) {
      return;
    }
    cugGenerationKeyRef.current = generationKey;
    cugGenerationInFlightRef.current = true;
    let reservationContextId = sampleXml.cugReservationContextId;
    if (!reservationContextId) {
      reservationContextId = createCugReservationContextId();
      setSampleXml((previous) => ({
        ...previous,
        cugReservationContextId: reservationContextId,
      }));
    }

    const payload = {
      patientId,
      existingCugs: Array.isArray(props.existingCugs) ? props.existingCugs : [],
      reservationToken: sampleXml.cugReservationToken || "",
      reservationContextId: reservationContextId,
    };

    postToOpenElisServerJsonResponse(
      "/rest/sample-cug/preview",
      JSON.stringify(payload),
      (response) => {
        cugGenerationInFlightRef.current = false;
        if (response?.status && response.status >= 400) {
          setNotificationVisible(true);
          addNotification({
            kind: NotificationKinds.error,
            title: intl.formatMessage({ id: "notification.title" }),
            message:
              response?.message ||
              intl.formatMessage({ id: "sample.cug.generate.error" }),
          });
          return;
        }
        const generated = response?.cugCode;
        const reservationToken = response?.reservationToken;
        if (generated && reservationToken) {
          setSampleXml((previous) => ({
            ...previous,
            cug: generated,
            cugAutoReserved: generated,
            cugValidationMessage: "",
            cugReservationToken: reservationToken,
          }));
          return;
        }
        setNotificationVisible(true);
        addNotification({
          kind: NotificationKinds.error,
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({ id: "sample.cug.generate.error" }),
        });
      },
    );
  }, [
    addNotification,
    index,
    intl,
    props.canGenerateCug,
    props.existingCugs,
    props.patientId,
    props.patientCugKey,
    sampleXml.cug,
    sampleXml.cugReservationContextId,
    sampleXml.cugReservationToken,
    selectedSampleType.id,
    setNotificationVisible,
  ]);

  useEffect(() => {
    const currentPatientCugKey =
      String(props.patientCugKey || "").trim() || null;
    const previousPatientCugKey = previousPatientCugKeyRef.current;
    if (previousPatientCugKey === currentPatientCugKey) {
      return;
    }

    previousPatientCugKeyRef.current = currentPatientCugKey;
    cugGenerationKeyRef.current = "";
    setSampleXml((previous) => ({
      ...previous,
      cug: "",
      cugAutoReserved: "",
      cugValidationMessage: "",
      cugReservationToken: "",
      cugReservationContextId: createCugReservationContextId(),
    }));
  }, [props.patientCugKey]);

  useEffect(() => {
    if (props.canGenerateCug) {
      return;
    }
    cugGenerationKeyRef.current = "";
    setSampleXml((previous) => {
      const hasAnyCugData =
        String(previous.cug || "").trim() !== "" ||
        String(previous.cugAutoReserved || "").trim() !== "" ||
        String(previous.cugReservationToken || "").trim() !== "";
      if (!hasAnyCugData) {
        return previous;
      }
      return {
        ...previous,
        cug: "",
        cugAutoReserved: "",
        cugValidationMessage: "",
        cugReservationToken: "",
      };
    });
  }, [props.canGenerateCug]);

  useEffect(() => {
    if (!props.canGenerateCug) {
      return;
    }
    const hasSampleType =
      selectedSampleType.id !== "" && selectedSampleType.id != null;
    const hasCug = String(sampleXml.cug || "").trim() !== "";
    if (!hasSampleType || hasCug) {
      return;
    }
    generateCugPreview();
  }, [
    generateCugPreview,
    props.canGenerateCug,
    props.patientCugKey,
    sampleXml.cug,
    selectedSampleType.id,
  ]);

  const handleGpsCoordinatesChange = useCallback(
    (gpsData) => {
      const updatedSampleXml = {
        ...sampleXml,
        gpsLatitude: gpsData.gpsLatitude,
        gpsLongitude: gpsData.gpsLongitude,
        gpsAccuracy: gpsData.gpsAccuracy,
        gpsCaptureMethod: gpsData.gpsCaptureMethod,
      };
      setSampleXml(updatedSampleXml);
      props.sampleTypeObject({
        sampleXML: updatedSampleXml,
        sampleObjectIndex: index,
      });
    },
    [sampleXml, props.sampleTypeObject, index],
  );

  function handleStorageLocationChange(location, positionCoordinate) {
    const normalizedLocation = {
      ...(location || {}),
      id:
        location?.locationId ||
        location?.box?.id ||
        location?.id ||
        location?.rack?.id ||
        location?.shelf?.id ||
        location?.device?.id ||
        null,
      type:
        location?.locationType ||
        (location?.box?.id
          ? "box"
          : location?.type ||
            (location?.rack?.id
              ? "rack"
              : location?.shelf?.id
                ? "shelf"
                : location?.device?.id
                  ? "device"
                  : "")),
      positionCoordinate:
        positionCoordinate ||
        location?.positionCoordinate ||
        location?.position?.coordinate ||
        "",
    };

    setSampleXml({
      ...sampleXml,
      storageLocation: normalizedLocation,
      storagePositionId: location?.position?.id || null,
    });
  }

  function handleQuantity(value) {
    setSampleXml({
      ...sampleXml,
      quantity: value.target.value,
    });
  }

  function handleUom(value) {
    setSampleXml({
      ...sampleXml,
      uom: value,
    });
  }

  function handleCugChange(value) {
    const nextCug = value?.target?.value || "";
    setSampleXml((previous) => {
      const next = { ...previous, cug: nextCug };
      const hasReservationContext =
        String(previous.cugReservationToken || "").trim() !== "" &&
        String(previous.cugAutoReserved || "").trim() !== "";
      if (!hasReservationContext || String(nextCug).trim() === "") {
        return { ...next, cugValidationMessage: "" };
      }

      const reserved = parseCugParts(previous.cugAutoReserved);
      const manual = parseCugParts(nextCug);
      if (!reserved || !manual) {
        return {
          ...next,
          cugValidationMessage: intl.formatMessage({
            id: "sample.cug.manual.format.error",
          }),
        };
      }

      const maxPrefix = reserved.prefix + 5;
      const maxSuffix = reserved.suffix + 5;
      const prefixInRange =
        manual.prefix >= reserved.prefix && manual.prefix <= maxPrefix;
      const suffixInRange =
        manual.suffix >= reserved.suffix && manual.suffix <= maxSuffix;
      if (!prefixInRange || !suffixInRange) {
        return {
          ...next,
          cugValidationMessage: intl.formatMessage(
            { id: "sample.cug.manual.range.error" },
            {
              minPrefix: reserved.prefix,
              minSuffix: reserved.suffix,
              maxPrefix: maxPrefix,
              maxSuffix: maxSuffix,
            },
          ),
        };
      }

      return { ...next, cugValidationMessage: "" };
    });
  }

  function handleAdditionalFieldValueChange(fieldKey, value) {
    setSampleXml((previous) => ({
      ...previous,
      additionalFieldValues: {
        ...(previous.additionalFieldValues || {}),
        [fieldKey]: value,
      },
    }));
  }

  function handleAdditionalMultiSelectOption(fieldKey, optionKey, checked) {
    const currentValues = (sampleXml.additionalFieldValues?.[fieldKey] || "")
      .split(",")
      .map((value) => value.trim())
      .filter((value) => value !== "");
    const currentSet = new Set(currentValues);

    if (checked) {
      currentSet.add(optionKey);
    } else {
      currentSet.delete(optionKey);
    }

    handleAdditionalFieldValueChange(
      fieldKey,
      Array.from(currentSet).join(","),
    );
  }

  useEffect(() => {
    updateSampleXml(sampleXml, index);
  }, [sampleXml]);

  const handleReferralRequest = () => {
    setRequestTestReferral(!requestTestReferral);
    if (selectedTests.length > 0) {
      const defaultReferralRequest = [];
      selectedTests.map((test) => {
        defaultReferralRequest.push({
          reasonForReferral: referralReasons[0].id,
          referrer:
            userSessionDetails.firstName + " " + userSessionDetails.lastName,
          institute: referralOrganizations[0].id,
          sentDate: "",
          testId: test.id,
        });
      });
      setReferralRequests(defaultReferralRequest);
    }
  };

  const handleTestSearchChange = (event) => {
    const query = event.target.value;
    setTestSearchTerm(query);
    const results = sampleTypeTests.tests.filter((test) => {
      return test.name.toLowerCase().includes(query.toLowerCase());
    });
    setSearchBoxTests(results);
  };

  const handleRemoveSelectedTest = (test) => {
    removedTestFromSelectedTests(test);
  };

  const handleFilterSelectTest = (test) => {
    setTestSearchTerm("");
    addTestToSelectedTests(test);
  };

  const handleTestCheckbox = (e, test) => {
    if (e.currentTarget.checked) {
      addTestToSelectedTests(test);
    } else {
      removedTestFromSelectedTests(test);
    }
  };

  function findTestById(testId) {
    return sampleTypeTests.tests.find((test) => test.id === testId);
  }

  function findTestIndex(testId) {
    return sampleTypeTests.tests.findIndex((test) => test.id === testId);
  }

  const panelIsSelected = (panelId) => {
    for (let i in selectedPanels) {
      if (selectedPanels[i].id === panelId) {
        return true;
      }
    }
    return false;
  };

  const testIsSelected = (testId) => {
    for (let i in selectedTests) {
      if (selectedTests[i].id === testId) {
        return true;
      }
    }
    return false;
  };

  const triggerPanelCheckBoxChange = (isChecked, testIds) => {
    const testIdsList = testIds.split(",").map((id) => id.trim());
    testIdsList.map((testId) => {
      let testIndex = findTestIndex(testId);
      let test = findTestById(testId);
      if (testIndex !== -1) {
        if (isChecked) {
          if (!testIsSelected(test.id)) {
            setSelectedTests((prevState) => {
              return [...prevState, { id: test.id, name: test.name }];
            });
          }
        } else {
          removedTestFromSelectedTests(test);
        }
      }
    });
  };

  const removedTestFromSelectedTests = (test) => {
    setSelectedTests((previous) =>
      previous.filter((selectedTest) => selectedTest.id !== test.id),
    );
  };

  const handleFetchSampleTypeTests = (e, index) => {
    setSelectedTests([]);
    setSelectedPanels([]);
    setReferralRequests([]);
    const { value } = e.target;
    cugGenerationKeyRef.current = "";
    const selectedSampleTypeOption =
      sampleTypesRef.current.options[sampleTypesRef.current.selectedIndex].text;
    setSelectedSampleType({
      ...selectedSampleType,
      id: value,
      name: selectedSampleTypeOption,
      element_index: index,
    });
    setSampleXml((previous) => ({
      ...previous,
      cug: "",
      cugAutoReserved: "",
      cugValidationMessage: "",
      cugReservationToken: "",
      additionalFieldValues: {},
    }));
    props.sampleTypeObject({ sampleTypeId: value, sampleObjectIndex: index });
    props.sampleTypeObject({ additionalFields: [], sampleObjectIndex: index });
  };

  const updateSampleXml = (sampleXML, index) => {
    props.sampleTypeObject({ sampleXML: sampleXML, sampleObjectIndex: index });
  };

  const fetchSamplesTypes = (res) => {
    if (componentMounted.current) {
      const normalized = (Array.isArray(res) ? res : []).map((sampleType) => {
        const displayValue = [
          sampleType?.value,
          sampleType?.name,
          sampleType?.description,
          sampleType?.id,
        ].find((entry) => String(entry || "").trim() !== "");

        return {
          ...sampleType,
          value: displayValue || "",
        };
      });
      setSampleTypes(normalized);
      setLoading(false);
    }
  };

  const fetchSampleTypeTests = (res) => {
    if (componentMounted.current) {
      setSampleTypeTests({
        ...sampleTypeTestsStructure,
        ...(res || {}),
        additionalFields: res?.additionalFields || [],
      });
    }
  };

  useEffect(() => {
    if (props.sample.referralItems.length > 0 && referralReasons.length > 0) {
      setRequestTestReferral(props.sample.requestReferralEnabled);
      setReferralRequests(props.sample.referralItems);
    }
  }, [referralReasons]);

  useEffect(() => {
    props.sampleTypeObject({
      requestReferralEnabled: requestTestReferral,
      sampleObjectIndex: index,
    });
    if (!requestTestReferral) {
      setReferralRequests([]);
    }
  }, [requestTestReferral]);

  useEffect(() => {
    props.sampleTypeObject({
      referralItems: referralRequests,
      sampleObjectIndex: index,
    });
  }, [referralRequests]);

  useEffect(() => {
    if (referralFieldVisible) {
      return;
    }

    if (requestTestReferral) {
      setRequestTestReferral(false);
    }
    if (referralRequests.length > 0) {
      setReferralRequests([]);
    }
  }, [referralFieldVisible, referralRequests.length, requestTestReferral]);

  const displayReferralReasonsOptions = (res) => {
    if (componentMounted.current) {
      setReferralReasons(Array.isArray(res) ? res : []);
    }
  };
  const displayReferralOrgOptions = (res) => {
    if (componentMounted.current) {
      setReferralOrganizations(Array.isArray(res) ? res : []);
    }
  };

  function handleRejection(checked) {
    if (checked) {
      addNotification({
        kind: NotificationKinds.warning,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "reject.order.sample.notification" }),
      });
      setNotificationVisible(true);
    }
    setSampleXml({
      ...sampleXml,
      rejected: checked,
    });
    setRejectionReasonsDisabled(!rejectionReasonsDisabled);
  }

  const removedPanelFromSelectedPanels = (panel) => {
    triggerPanelCheckBoxChange(false, panel.testIds);
    setSelectedPanels((previous) =>
      previous.filter((selectedPanel) => selectedPanel.id !== panel.id),
    );
  };

  const handlePanelSearchChange = (event) => {
    const query = event.target.value;
    setPanelSearchTerm(query);
    const results = sampleTypeTests.panels.filter((panel) => {
      return panel.name.toLowerCase().includes(query.toLowerCase());
    });
    setSearchBoxPanels(results);
  };

  const handleFilterSelectPanel = (panel) => {
    setPanelSearchTerm("");
    addPanelToSelectedPanels(panel);
  };

  const handlePanelCheckbox = (panel) => {
    if (!panelIsSelected(panel.id)) {
      addPanelToSelectedPanels(panel);
    } else {
      removedPanelFromSelectedPanels(panel);
    }
  };

  const handleRemoveSelectedPanel = (panel) => {
    removedPanelFromSelectedPanels(panel);
  };

  function addTestToSelectedTests(test) {
    if (!testIsSelected(test.id)) {
      setSelectedTests([...selectedTests, { id: test.id, name: test.name }]);
    }
  }

  const addPanelToSelectedPanels = (panel) => {
    setSelectedPanels([
      ...selectedPanels,
      { id: panel.id, name: panel.name, testIds: panel.testIds },
    ]);
  };

  useEffect(() => {
    componentMounted.current = true;
    if (selectedSampleType.id !== "" && selectedSampleType.id != null) {
      getFromOpenElisServer(
        `/rest/sample-type-tests?sampleType=${selectedSampleType.id}`,
        fetchSampleTypeTests,
      );
    }
    return () => {
      componentMounted.current = false;
    };
  }, [selectedSampleType.id]);

  useEffect(() => {
    const additionalFields = additionalFieldsVisible
      ? sampleTypeTests?.additionalFields || []
      : [];
    props.sampleTypeObject({
      additionalFields: additionalFields,
      sampleObjectIndex: index,
    });

    if (additionalFields.length === 0) {
      return;
    }

    setSampleXml((previous) => {
      const existingValues = previous.additionalFieldValues || {};
      const updatedValues = { ...existingValues };
      let changed = previous.additionalFieldValues == null;

      additionalFields.forEach((field) => {
        const fieldKey = field.fieldKey;
        const defaultValue = field.defaultValue;
        const hasExistingValue =
          existingValues[fieldKey] !== undefined &&
          existingValues[fieldKey] !== null &&
          String(existingValues[fieldKey]).trim() !== "";
        if (
          !hasExistingValue &&
          defaultValue !== undefined &&
          defaultValue !== null
        ) {
          updatedValues[fieldKey] = defaultValue;
          changed = true;
        }
      });

      if (!changed) {
        return previous;
      }

      return {
        ...previous,
        additionalFieldValues: updatedValues,
      };
    });
  }, [sampleTypeTests.additionalFields, additionalFieldsVisible, index]);

  useEffect(() => {
    getFromOpenElisServer(`/rest/displayList/UNIT_OF_MEASURE`, fetchUomCreate);
  }, []);

  const fetchUomCreate = (res) => {
    if (componentMounted.current) {
      if (Array.isArray(res)) {
        setUomList(res);
        return;
      }
      setUomList(
        Array.isArray(res?.existingUomList) ? res.existingUomList : [],
      );
    }
  };

  useEffect(() => {
    props.sampleTypeObject({
      sampleRejected: rejectionReasonsDisabled,
      sampleObjectIndex: index,
    });
  }, [rejectionReasonsDisabled]);

  useEffect(() => {
    props.sampleTypeObject({
      selectedTests: [...selectedTests],
      sampleObjectIndex: index,
    });
  }, [selectedTests]);

  useEffect(() => {
    props.sampleTypeObject({
      selectedPanels: [...selectedPanels],
      sampleObjectIndex: index,
    });
    for (let i in selectedPanels) {
      triggerPanelCheckBoxChange(true, selectedPanels[i].testIds);
    }
  }, [selectedPanels, sampleTypeTests]);

  useEffect(() => {
    componentMounted.current = true;
    getFromOpenElisServer(
      "/rest/referral-reasons",
      displayReferralReasonsOptions,
    );
    getFromOpenElisServer(
      "/rest/referral-organizations",
      displayReferralOrgOptions,
    );
    getFromOpenElisServer("/rest/user-sample-types", fetchSamplesTypes);
    getFromOpenElisServer(
      "/rest/sample-additional-fields/fixed",
      (response) => {
        if (componentMounted.current) {
          setSampleFixedFieldConfigs(Array.isArray(response) ? response : []);
          setWaitingForSampleFixedFieldConfig(false);
        }
      },
    );
    return () => {
      componentMounted.current = false;
    };
  }, []);

  const renderAdditionalField = (field) => {
    const fieldType = (field.fieldType || "TEXT").toUpperCase();
    const fieldKey = field.fieldKey;
    const value = sampleXml.additionalFieldValues?.[fieldKey] || "";
    const options = field.options || [];
    const required = Boolean(field.required);
    const fieldLabel = field.displayName || field.fieldKey;

    if (fieldType === "BOOLEAN") {
      return (
        <Checkbox
          id={`additional_field_${index}_${fieldKey}`}
          labelText={fieldLabel}
          checked={value === "true"}
          onChange={(event) =>
            handleAdditionalFieldValueChange(
              fieldKey,
              event.target.checked ? "true" : "false",
            )
          }
        />
      );
    }

    if (fieldType === "SELECT" || fieldType === "RADIO") {
      return (
        <Select
          id={`additional_field_${index}_${fieldKey}`}
          labelText={fieldLabel}
          value={value}
          required={required}
          onChange={(event) =>
            handleAdditionalFieldValueChange(fieldKey, event.target.value)
          }
        >
          <SelectItem
            text={intl.formatMessage({ id: "label.select" })}
            value=""
          />
          {options.map((option, optionIndex) => (
            <SelectItem
              key={`additional_field_option_${fieldKey}_${optionIndex}`}
              text={option.optionLabel}
              value={option.optionKey}
            />
          ))}
        </Select>
      );
    }

    if (fieldType === "MULTISELECT") {
      const selectedValues = new Set(
        value
          .split(",")
          .map((entry) => entry.trim())
          .filter((entry) => entry !== ""),
      );

      return (
        <FormGroup legendText={fieldLabel}>
          {options.map((option, optionIndex) => (
            <Checkbox
              key={`additional_field_option_${fieldKey}_${optionIndex}`}
              id={`additional_field_${index}_${fieldKey}_${optionIndex}`}
              labelText={option.optionLabel}
              checked={selectedValues.has(option.optionKey)}
              onChange={(event) =>
                handleAdditionalMultiSelectOption(
                  fieldKey,
                  option.optionKey,
                  event.target.checked,
                )
              }
            />
          ))}
        </FormGroup>
      );
    }

    if (fieldType === "TEXTAREA") {
      return (
        <TextArea
          id={`additional_field_${index}_${fieldKey}`}
          labelText={fieldLabel}
          value={value}
          required={required}
          maxLength={field.maxLength || undefined}
          onChange={(event) =>
            handleAdditionalFieldValueChange(fieldKey, event.target.value)
          }
        />
      );
    }

    const htmlInputType =
      fieldType === "NUMBER"
        ? "number"
        : fieldType === "DATE"
          ? "date"
          : fieldType === "DATETIME"
            ? "datetime-local"
            : "text";

    return (
      <TextInput
        id={`additional_field_${index}_${fieldKey}`}
        labelText={fieldLabel}
        value={value}
        required={required}
        type={htmlInputType}
        maxLength={field.maxLength || undefined}
        onChange={(event) =>
          handleAdditionalFieldValueChange(fieldKey, event.target.value)
        }
      />
    );
  };

  return (
    <>
      {loading && <Loading />}
      <div className="sampleBody">
        <Select
          className="selectSampleType"
          id={"sampleId_" + index}
          ref={sampleTypesRef}
          value={
            props.sample.sampleTypeId === "" ? "" : props.sample.sampleTypeId
          }
          name="sampleId"
          labelText=""
          onChange={(e) => {
            handleFetchSampleTypeTests(e, index);
          }}
          required
        >
          <SelectItem
            text={intl.formatMessage({ id: "sample.type.select.placeholder" })}
            value=""
          />
          {sampleTypes?.map((sampleType, i) => (
            <SelectItem text={sampleType.value} value={sampleType.id} key={i} />
          ))}
        </Select>
        {isSampleFieldVisible("cug") && (
          <TextInput
            id={`sample_cug_${index}`}
            labelText={intl.formatMessage({ id: "sample.cug.label" })}
            value={sampleXml.cug || ""}
            required={isSampleFieldRequired("cug", true)}
            onChange={handleCugChange}
            invalid={Boolean(sampleXml.cugValidationMessage)}
            invalidText={sampleXml.cugValidationMessage || ""}
          />
        )}

        {isSampleFieldVisible("rejected") && (
          <CustomCheckBox
            id={"reject_" + index}
            onChange={(value) => handleRejection(value)}
            label={intl.formatMessage({ id: "sample.reject.label" })}
          />
        )}
        {sampleXml.rejected && (
          <CustomSelect
            id={"rejectedReasonId_" + index}
            options={rejectSampleReasons}
            disabled={rejectionReasonsDisabled}
            value={sampleXml.rejectionReason}
            onChange={(e) => handleReasons(e)}
          />
        )}
        {isSampleFieldVisible("quantity") && (
          <div className="inlineDiv" style={{ display: "flex", gap: "1rem" }}>
            <TextInput
              value={sampleXml.quantity}
              name="quantity"
              labelText={intl.formatMessage({
                id: "sample.quantity.label",
              })}
              id="quantity"
              type="number"
              min="0"
              onChange={(value) => handleQuantity(value)}
              placeholder={intl.formatMessage({
                id: "sample.quantity.label",
              })}
            />

            <CustomSelect
              id={"uomId_" + index}
              labelText={intl.formatMessage({ id: "sample.uom.label" })}
              options={uomList}
              disabled={false}
              value={sampleXml.uom}
              onChange={(value) => handleUom(value)}
            />
          </div>
        )}
        {isSampleFieldVisible("collectionDate") && (
          <div className="inlineDiv">
            <CustomDatePicker
              id={"collectionDate_" + index}
              autofillDate={
                configurationProperties?.AUTOFILL_COLLECTION_DATE === "true"
              }
              onChange={(date) => handleCollectionDate(date)}
              value={sampleXml.collectionDate}
              labelText={intl.formatMessage({ id: "sample.collection.date" })}
              className="inputText"
              disallowFutureDate={true}
            />

            <CustomTimePicker
              id={"collectionTime_" + index}
              autofillTime={
                configurationProperties?.AUTOFILL_COLLECTION_DATE === "true"
              }
              onChange={(time) => handleCollectionTime(time)}
              value={sampleXml.collectionTime}
              className="inputText"
              labelText={intl.formatMessage({ id: "sample.collection.time" })}
            />
          </div>
        )}
        {isSampleFieldVisible("collector") && (
          <div className="inlineDiv">
            <CustomTextInput
              id={"collector_" + index}
              onChange={(value) => handleCollector(value)}
              defaultValue={""}
              value={sampleXml.collector}
              labelText={intl.formatMessage({ id: "collector.label" })}
              className="inputText"
            />
          </div>
        )}

        {additionalFieldsVisible &&
          sampleTypeTests.additionalFields &&
          sampleTypeTests.additionalFields.length > 0 && (
            <div className="additionalFields">
              <h4>
                <FormattedMessage id="sample.additional.fields.heading" />
              </h4>
              <div className="inlineDiv">
                {sampleTypeTests.additionalFields.map((field, fieldIndex) => (
                  <div
                    key={`additional_field_${index}_${field.fieldKey}_${fieldIndex}`}
                    className="inputText"
                    style={{ width: "100%" }}
                  >
                    {renderAdditionalField(field)}
                  </div>
                ))}
              </div>
            </div>
          )}

        {configurationProperties.GPS_ENABLED === "true" && (
          <div className="gpsDiv">
            <GpsCoordinatesCapture
              index={index}
              sampleXml={sampleXml}
              onChange={handleGpsCoordinatesChange}
              disabled={sampleXml.rejected}
            />
          </div>
        )}

        {/* Storage Location Selector - INT-001: Integration point */}
        {/* NOTE: In order entry workflow, SampleItems are created after Sample is saved.
            Storage assignment operates at SampleItem level, so actual assignment happens
            after SampleItems are created. The location preference is stored here for
            later assignment to the first/default SampleItem. */}
        {isSampleFieldVisible("storageLocation") && (
          <div className="inlineDiv">
            <StorageLocationSelector
              workflow="orders"
              optional={true}
              sampleInfo={{
                // Note: sampleId here is temporary/placeholder - actual SampleItem ID will be available after SampleItems are created
                sampleId: sample?.id || sample?.sampleId || `TEMP-${index}`,
                type:
                  selectedSampleType?.name || sampleXml?.sampleTypeName || "",
                status: sampleXml?.rejected ? "Rejected" : "Active",
              }}
              initialLocation={sampleXml?.storageLocation || null}
              onLocationChange={(locationData) => {
                // locationData format: { sample, newLocation, reason?, conditionNotes?, positionCoordinate? }
                // Extract newLocation and positionCoordinate from locationData
                // Store location preference - will be assigned to SampleItem after SampleItems are created
                const location = locationData?.newLocation || locationData;
                const positionCoordinate =
                  locationData?.positionCoordinate || "";
                handleStorageLocationChange(location, positionCoordinate);
              }}
            />
          </div>
        )}
        {isSampleFieldVisible("panels") && (
          <div className="testPanels">
            <div className="cds--col">
              <h4>
                <FormattedMessage id="sample.label.orderpanel" />
              </h4>
              <div
                className={"searchTestText"}
                style={{ marginBottom: "1.188rem" }}
              >
                {selectedPanels && selectedPanels.length ? (
                  <>
                    {selectedPanels.map((panel, panel_index) => (
                      <Tag
                        filter
                        key={`panelTags_` + panel_index}
                        onClose={() => handleRemoveSelectedPanel(panel)}
                        style={{ marginRight: "0.5rem" }}
                        type={"green"}
                      >
                        {panel.name}
                      </Tag>
                    ))}
                  </>
                ) : (
                  <></>
                )}
              </div>
              <FormGroup
                legendText={
                  <FormattedMessage id="sample.search.panel.legend.text" />
                }
              >
                <Search
                  size="lg"
                  id={`panels_search_` + index}
                  labelText={
                    <FormattedMessage id="label.search.availablepanel" />
                  }
                  placeholder={intl.formatMessage({
                    id: "choose.availablepanel",
                  })}
                  onChange={handlePanelSearchChange}
                  value={(() => {
                    if (panelSearchTerm) {
                      return panelSearchTerm;
                    }
                    return "";
                  })()}
                />
                <div>
                  {(() => {
                    if (!panelSearchTerm) return null;
                    if (searchBoxPanels && searchBoxPanels.length) {
                      return (
                        <ul className={"searchTestsList"}>
                          {searchBoxPanels.map((panel, panel_index) => (
                            <li
                              role="menuitem"
                              className={"singleTest"}
                              key={`panelFilter_` + panel_index}
                              onClick={() => handleFilterSelectPanel(panel)}
                            >
                              {panel.name}
                            </li>
                          ))}
                        </ul>
                      );
                    }
                    return (
                      <>
                        <Layer>
                          <Tile className={"emptyFilterTests"}>
                            <span>
                              <FormattedMessage id="sample.panel.search.error.msg" />{" "}
                              <strong>
                                &quot;{panelSearchTerm}&quot;
                              </strong>{" "}
                            </span>
                          </Tile>
                        </Layer>
                      </>
                    );
                  })()}
                </div>
              </FormGroup>
              {sampleTypeTests.panels != null &&
                sampleTypeTests.panels.map((panel) => {
                  return panel.name === "" ? (
                    ""
                  ) : (
                    <Checkbox
                      onChange={() => handlePanelCheckbox(panel)}
                      labelText={panel.name}
                      id={`panel_` + index + "_" + panel.id}
                      key={index + panel.id}
                      checked={
                        selectedPanels.filter((item) => item.id === panel.id)
                          .length > 0
                      }
                    />
                  );
                })}
            </div>
          </div>
        )}
        {isSampleFieldVisible("tests") && (
          <div className="cds--col">
            {selectedTests && !selectedTests.length ? "" : <h4>Order Tests</h4>}
            <div
              className={"searchTestText"}
              style={{ marginBottom: "1.188rem" }}
            >
              {selectedTests && selectedTests.length ? (
                <>
                  {selectedTests.map((test, index) => (
                    <Tag
                      filter
                      key={`testTags_` + index}
                      onClose={() => handleRemoveSelectedTest(test)}
                      style={{ marginRight: "0.5rem" }}
                      type={"red"}
                    >
                      {test.name}
                    </Tag>
                  ))}
                </>
              ) : (
                <></>
              )}
            </div>
            <FormGroup
              legendText={intl.formatMessage({
                id: "legend.search.availabletests",
              })}
            >
              <Search
                size="lg"
                id={`tests_search_` + index}
                labelText={
                  <FormattedMessage id="label.search.available.targetest" />
                }
                placeholder={intl.formatMessage({
                  id: "holder.choose.availabletest",
                })}
                onChange={handleTestSearchChange}
                value={(() => {
                  if (testSearchTerm) {
                    return testSearchTerm;
                  }
                  return "";
                })()}
              />
              <div>
                {(() => {
                  if (!testSearchTerm) return null;
                  if (searchBoxTests && searchBoxTests.length) {
                    return (
                      <ul className={"searchTestsList"}>
                        {searchBoxTests.map((test, test_index) => (
                          <li
                            role="menuitem"
                            className={"singleTest"}
                            key={`filterTest_` + test_index}
                            onClick={() => handleFilterSelectTest(test)}
                          >
                            {test.name}
                          </li>
                        ))}
                      </ul>
                    );
                  }
                  return (
                    <>
                      <Layer>
                        <Tile className={"emptyFilterTests"}>
                          <span>
                            <FormattedMessage id="title.notestfoundmatching" />
                            <strong> &quot;{testSearchTerm}&quot;</strong>{" "}
                          </span>
                        </Tile>
                      </Layer>
                    </>
                  );
                })()}
              </div>
            </FormGroup>
            {sampleTypeTests.tests != null &&
              sampleTypeTests.tests.map((test) => {
                return test.name === "" ? (
                  ""
                ) : (
                  <Checkbox
                    onChange={(e) => handleTestCheckbox(e, test)}
                    labelText={test.name}
                    id={`test_` + index + "_" + test.id}
                    key={`test_checkBox_` + index + test.id}
                    checked={
                      selectedTests.filter((item) => item.id === test.id)
                        .length > 0
                    }
                  />
                );
              })}
          </div>
        )}

        {referralFieldVisible && (
          <div className="requestTestReferral">
            <Checkbox
              id={`useReferral_` + index}
              labelText={intl.formatMessage({
                id: "label.refertest.referencelab",
              })}
              onChange={handleReferralRequest}
            />
            {requestTestReferral === true && (
              <OrderReferralRequest
                index={index}
                selectedTests={selectedTests}
                referralReasons={referralReasons}
                referralOrganizations={referralOrganizations}
                referralRequests={referralRequests}
                setReferralRequests={setReferralRequests}
              />
            )}
          </div>
        )}
      </div>
    </>
  );
};

export default SampleType;
