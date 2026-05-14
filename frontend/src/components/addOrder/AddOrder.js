import React, { useContext, useEffect, useRef, useState } from "react";
import {
  Checkbox,
  FileUploader,
  Link,
  RadioButton,
  RadioButtonGroup,
  Select,
  SelectItem,
  Stack,
  TextArea,
  TextInput,
  Column,
  Grid,
} from "@carbon/react";
import CustomLabNumberInput from "../common/CustomLabNumberInput";
import CustomDatePicker from "../common/CustomDatePicker";
import { getFromOpenElisServer, toBase64 } from "../utils/Utils";
import CustomTimePicker from "../common/CustomTimePicker";
import { NotificationContext } from "../layout/Layout";
import { NotificationKinds } from "../common/CustomNotification";
import AutoComplete from "../common/AutoComplete";
import OrderResultReporting from "./OrderResultReporting";
import { FormattedMessage, useIntl } from "react-intl";
import { ConfigurationContext } from "../layout/Layout";
import config from "../../config.json";

const DEFAULT_FIXED_FIELD_ORDER = [
  { fieldKey: "priority", sortOrder: 10 },
  { fieldKey: "requestDate", sortOrder: 20 },
  { fieldKey: "receivedDateForDisplay", sortOrder: 30 },
  { fieldKey: "receivedTime", sortOrder: 40 },
  { fieldKey: "nextVisitDate", sortOrder: 50 },
  { fieldKey: "referringSiteName", sortOrder: 60 },
  { fieldKey: "referringSiteDepartmentId", sortOrder: 70 },
  { fieldKey: "provisionalClinicalDiagnosis", sortOrder: 80 },
  { fieldKey: "providerFirstName", sortOrder: 90 },
  { fieldKey: "providerLastName", sortOrder: 100 },
  { fieldKey: "providerCmp", sortOrder: 110 },
  { fieldKey: "providerRne", sortOrder: 120 },
  { fieldKey: "providerDni", sortOrder: 130 },
  { fieldKey: "providerSpecialty", sortOrder: 140 },
  { fieldKey: "providerWorkPhone", sortOrder: 150 },
  { fieldKey: "providerFax", sortOrder: 160 },
  { fieldKey: "providerEmail", sortOrder: 170 },
  { fieldKey: "paymentOptionSelection", sortOrder: 180 },
  { fieldKey: "testLocationCode", sortOrder: 190 },
  { fieldKey: "otherLocationCode", sortOrder: 200 },
  { fieldKey: "rememberSiteAndRequester", sortOrder: 210 },
];

const DEFAULT_CONDITION_OPERATOR = "equals";
const DEFAULT_CONDITION_LOGIC = "ALL";
const DEFAULT_ORDER_PRIORITIES = [
  { id: "ROUTINE", value: "ROUTINE" },
  { id: "ASAP", value: "ASAP" },
  { id: "STAT", value: "STAT" },
  { id: "TIMED", value: "TIMED" },
  { id: "FUTURE_STAT", value: "FUTURE STAT" },
];

const parseProviderSpecialtyOptions = (rawValue) => {
  if (!rawValue) {
    return [];
  }

  return rawValue
    .split(/[\n,]+/)
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const parts = entry.split("|");
      const id = (parts[0] || "").trim();
      const value = (parts[1] || parts[0] || "").trim();
      return id ? { id, value } : null;
    })
    .filter(Boolean);
};

const AddOrder = (props) => {
  const { setNotificationVisible, addNotification } =
    useContext(NotificationContext);
  const { configurationProperties } = useContext(ConfigurationContext);

  const intl = useIntl();
  const providerSpecialtyOptions = parseProviderSpecialtyOptions(
    configurationProperties?.providerSpecialtyOptions,
  );

  const componentMounted = useRef(false);

  const {
    orderFormValues,
    setOrderFormValues,
    samples,
    error,
    isModifyOrder,
    changed,
    setChanged,
  } = props;
  const [otherSamplingVisible, setOtherSamplingVisible] = useState(false);
  const [providers, setProviders] = useState([]);
  const [paymentOptions, setPaymentOptions] = useState([]);
  const [samplingPerformed, setSamplingPerformed] = useState([]);
  const [priorityOptions, setPriorityOptions] = useState(
    DEFAULT_ORDER_PRIORITIES,
  );
  const [siteNames, setSiteNames] = useState([]);
  const [innitialized, setInnitialized] = useState(false);
  const [departments, setDepartments] = useState([]);
  const [waitingForFixedFieldConfig, setWaitingForFixedFieldConfig] =
    useState(true);

  const hasLoadedFixedFieldConfig =
    Array.isArray(orderFormValues?.sampleOrderItems?.fixedFieldConfigs) &&
    orderFormValues.sampleOrderItems.fixedFieldConfigs.length > 0;

  const getFixedFieldConfig = (fieldKey) => {
    const configs = orderFormValues?.sampleOrderItems?.fixedFieldConfigs || [];
    return (
      configs.find(
        (config) => config?.fieldKey?.toLowerCase() === fieldKey?.toLowerCase(),
      ) || null
    );
  };

  const isFieldVisible = (fieldKey) => {
    if (waitingForFixedFieldConfig && !hasLoadedFixedFieldConfig) {
      return false;
    }
    const config = getFixedFieldConfig(fieldKey);
    return config ? config.visible !== false : true;
  };

  const isFieldRequired = (fieldKey, fallback = false) => {
    const config = getFixedFieldConfig(fieldKey);
    return config && config.required != null ? !!config.required : fallback;
  };

  const isFieldReadonly = (fieldKey) => {
    const config = getFixedFieldConfig(fieldKey);
    return config ? config.readonly === true : false;
  };

  const buildRequesterDisplayValue = () => {
    const sampleOrderItems = orderFormValues?.sampleOrderItems || {};
    const firstName = (sampleOrderItems.providerFirstName || "").trim();
    const lastName = (sampleOrderItems.providerLastName || "").trim();
    const providerIdentifier = (
      sampleOrderItems.providerPersonId ||
      sampleOrderItems.providerId ||
      ""
    ).trim();

    if (lastName && firstName) {
      return `${lastName}, ${firstName}`;
    }
    if (lastName) {
      return lastName;
    }
    if (firstName) {
      return firstName;
    }
    return providerIdentifier;
  };

  const parseFieldMetadata = (field) => {
    if (!field?.metadataJson) {
      return {};
    }
    try {
      return JSON.parse(field.metadataJson);
    } catch (_error) {
      return {};
    }
  };

  const getDynamicConditionContext = () => {
    const sampleOrderItems = orderFormValues?.sampleOrderItems || {};
    return {
      ...(sampleOrderItems.additionalFieldValues || {}),
      priority: sampleOrderItems.priority,
      requestDate: sampleOrderItems.requestDate,
      receivedDateForDisplay: sampleOrderItems.receivedDateForDisplay,
      receivedTime: sampleOrderItems.receivedTime,
      nextVisitDate: sampleOrderItems.nextVisitDate,
      referringSiteName: sampleOrderItems.referringSiteName,
      referringSiteDepartmentId: sampleOrderItems.referringSiteDepartmentId,
      provisionalClinicalDiagnosis:
        sampleOrderItems.provisionalClinicalDiagnosis,
      providerFirstName: sampleOrderItems.providerFirstName,
      providerLastName: sampleOrderItems.providerLastName,
      providerCmp: sampleOrderItems.providerCmp,
      providerRne: sampleOrderItems.providerRne,
      providerDni: sampleOrderItems.providerDni,
      providerWorkPhone: sampleOrderItems.providerWorkPhone,
      providerFax: sampleOrderItems.providerFax,
      providerEmail: sampleOrderItems.providerEmail,
      paymentOptionSelection: sampleOrderItems.paymentOptionSelection,
      testLocationCode: sampleOrderItems.testLocationCode,
      otherLocationCode: sampleOrderItems.otherLocationCode,
      rememberSiteAndRequester: orderFormValues?.rememberSiteAndRequester
        ? "true"
        : "false",
    };
  };

  const evaluateSingleCondition = (condition, context) => {
    if (!condition || !condition.fieldKey) {
      return false;
    }

    const operator = (
      condition.operator || DEFAULT_CONDITION_OPERATOR
    ).toLowerCase();
    const leftValue = String(context?.[condition.fieldKey] ?? "").trim();
    const rightValue = String(condition.value ?? "").trim();
    const rightValues = Array.isArray(condition.values)
      ? condition.values.map((value) => String(value ?? "").trim())
      : rightValue
        ? [rightValue]
        : [];

    switch (operator) {
      case "equals":
        return leftValue.toLowerCase() === rightValue.toLowerCase();
      case "notequals":
        return leftValue.toLowerCase() !== rightValue.toLowerCase();
      case "in":
        return rightValues.some(
          (value) => leftValue.toLowerCase() === value.toLowerCase(),
        );
      case "notin":
        return rightValues.every(
          (value) => leftValue.toLowerCase() !== value.toLowerCase(),
        );
      case "hasvalue":
        return !!leftValue;
      case "istrue":
        return ["true", "yes", "1"].includes(leftValue.toLowerCase());
      case "isfalse":
        return ["false", "no", "0"].includes(leftValue.toLowerCase());
      default:
        return false;
    }
  };

  const evaluateConditions = (conditions, logic, context) => {
    if (!Array.isArray(conditions) || conditions.length === 0) {
      return true;
    }
    const useAny =
      String(logic || DEFAULT_CONDITION_LOGIC).toUpperCase() === "ANY";
    if (useAny) {
      return conditions.some((condition) =>
        evaluateSingleCondition(condition, context),
      );
    }
    return conditions.every((condition) =>
      evaluateSingleCondition(condition, context),
    );
  };

  const isDynamicFieldVisible = (field) => {
    if (!field || !field.fieldKey || field.active === false) {
      return false;
    }
    const metadata = parseFieldMetadata(field);
    const rules = metadata?.rules || {};
    return evaluateConditions(
      rules.visibleWhen,
      rules.logic || DEFAULT_CONDITION_LOGIC,
      getDynamicConditionContext(),
    );
  };

  const isDynamicFieldRequired = (field) => {
    if (field?.required) {
      return true;
    }
    const metadata = parseFieldMetadata(field);
    const rules = metadata?.rules || {};
    if (!Array.isArray(rules.requiredWhen) || rules.requiredWhen.length === 0) {
      return false;
    }
    return evaluateConditions(
      rules.requiredWhen,
      rules.logic || DEFAULT_CONDITION_LOGIC,
      getDynamicConditionContext(),
    );
  };

  const handleAdditionalFieldValueChange = (fieldKey, value) => {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        additionalFieldValues: {
          ...(orderFormValues.sampleOrderItems.additionalFieldValues || {}),
          [fieldKey]: value,
        },
      },
    });
  };

  const handleAdditionalFieldFileChange = (fieldKey, filePayload) => {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        additionalFieldFiles: {
          ...(orderFormValues.sampleOrderItems.additionalFieldFiles || {}),
          [fieldKey]: filePayload,
        },
      },
    });
  };

  const removeAdditionalFieldFile = (fieldKey) => {
    const existingFiles = orderFormValues.sampleOrderItems.additionalFieldFiles;
    const existingFile = existingFiles?.[fieldKey];
    if (existingFile?.fileName) {
      handleAdditionalFieldFileChange(fieldKey, { deleteFile: true });
      return;
    }
    if (!existingFiles) {
      return;
    }
    const nextFiles = { ...existingFiles };
    delete nextFiles[fieldKey];
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        additionalFieldFiles: nextFiles,
      },
    });
  };

  const handleAdditionalFileUpload = async (field, event) => {
    const file = event?.target?.files?.[0];
    if (!file || !field?.fieldKey) {
      return;
    }

    try {
      const base64Content = await toBase64(file);
      handleAdditionalFieldFileChange(field.fieldKey, {
        fileName: file.name,
        fileType: file.type,
        fileSize: file.size,
        base64Content,
        deleteFile: false,
      });
    } catch (_error) {
      setNotificationVisible(true);
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "server.error.msg" }),
      });
    }
  };

  const buildAdditionalFieldFileUrl = (fieldKey, download = false) => {
    const sampleId = orderFormValues?.sampleOrderItems?.sampleId;
    if (!sampleId || !fieldKey) {
      return null;
    }
    return `${config.serverBaseUrl}/rest/order-additional-fields/files/${sampleId}/${fieldKey}?download=${download}`;
  };

  const openBase64FilePreview = (fileType, base64Content) => {
    if (!base64Content) {
      return;
    }

    const normalizedBase64 = base64Content.includes(";base64,")
      ? base64Content.split(";base64,", 2)[1]
      : base64Content;

    try {
      const binaryString = window.atob(normalizedBase64);
      const byteArray = Uint8Array.from(binaryString, (character) =>
        character.charCodeAt(0),
      );
      const blob = new Blob([byteArray], {
        type: fileType || "application/octet-stream",
      });
      const objectUrl = URL.createObjectURL(blob);
      window.open(objectUrl, "_blank", "noopener,noreferrer");
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch (_error) {
      setNotificationVisible(true);
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "server.error.msg" }),
      });
    }
  };

  const openAdditionalFieldFilePreview = (fieldKey, currentFile) => {
    if (currentFile?.base64Content) {
      openBase64FilePreview(currentFile.fileType, currentFile.base64Content);
      return;
    }

    const previewUrl = buildAdditionalFieldFileUrl(fieldKey, false);
    if (!previewUrl) {
      return;
    }
    window.open(previewUrl, "_blank", "noopener,noreferrer");
  };

  const downloadAdditionalFieldFile = (fieldKey) => {
    const downloadUrl = buildAdditionalFieldFileUrl(fieldKey, true);
    if (!downloadUrl) {
      return;
    }
    window.open(downloadUrl, "_blank", "noopener,noreferrer");
  };

  const handleAdditionalMultiSelectOption = (fieldKey, optionKey, checked) => {
    const currentValuesRaw =
      orderFormValues.sampleOrderItems.additionalFieldValues?.[fieldKey] || "";
    const selectedValues = new Set(
      currentValuesRaw
        .split(",")
        .map((value) => value.trim())
        .filter(Boolean),
    );

    if (checked) {
      selectedValues.add(optionKey);
    } else {
      selectedValues.delete(optionKey);
    }

    handleAdditionalFieldValueChange(
      fieldKey,
      Array.from(selectedValues).join(","),
    );
  };

  const renderDynamicField = (field) => {
    if (!isDynamicFieldVisible(field)) {
      return null;
    }

    const fieldType = (field.fieldType || "TEXT").toUpperCase();
    const currentValue =
      orderFormValues.sampleOrderItems.additionalFieldValues?.[field.fieldKey];
    const hasValue = currentValue !== undefined && currentValue !== null;
    const value = hasValue ? currentValue : field.defaultValue || "";
    const options = (field.options || []).filter((option) => option.active);
    const required = isDynamicFieldRequired(field);
    const readonly = isFieldReadonly(field.fieldKey);
    const fieldMetadata = parseFieldMetadata(field);
    const documentConfig = fieldMetadata?.document || {};
    const acceptedMimeTypes = Array.isArray(documentConfig.accept)
      ? documentConfig.accept
      : ["application/pdf"];
    const currentFile =
      orderFormValues.sampleOrderItems.additionalFieldFiles?.[field.fieldKey];
    const label = (
      <>
        {field.displayName}
        {required ? <span className="requiredlabel">*</span> : null}
      </>
    );

    switch (fieldType) {
      case "TEXTAREA":
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <TextArea
              id={`order-dynamic-${field.fieldKey}`}
              labelText={label}
              value={value}
              onChange={(event) =>
                handleAdditionalFieldValueChange(
                  field.fieldKey,
                  event.target.value,
                )
              }
              maxLength={field.maxLength || undefined}
              readOnly={readonly}
            />
          </Column>
        );
      case "NUMBER":
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              id={`order-dynamic-${field.fieldKey}`}
              labelText={label}
              type="number"
              value={value}
              onChange={(event) =>
                handleAdditionalFieldValueChange(
                  field.fieldKey,
                  event.target.value,
                )
              }
              readOnly={readonly}
            />
          </Column>
        );
      case "DATE":
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              id={`order-dynamic-${field.fieldKey}`}
              labelText={label}
              type="date"
              value={value}
              onChange={(event) =>
                handleAdditionalFieldValueChange(
                  field.fieldKey,
                  event.target.value,
                )
              }
              readOnly={readonly}
            />
          </Column>
        );
      case "DATETIME":
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              id={`order-dynamic-${field.fieldKey}`}
              labelText={label}
              type="datetime-local"
              value={value}
              onChange={(event) =>
                handleAdditionalFieldValueChange(
                  field.fieldKey,
                  event.target.value,
                )
              }
              readOnly={readonly}
            />
          </Column>
        );
      case "BOOLEAN":
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <Checkbox
              id={`order-dynamic-${field.fieldKey}`}
              labelText={field.displayName}
              checked={String(value).toLowerCase() === "true"}
              onChange={(_event, { checked }) =>
                handleAdditionalFieldValueChange(
                  field.fieldKey,
                  checked ? "true" : "false",
                )
              }
              disabled={readonly}
            />
          </Column>
        );
      case "SELECT":
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <Select
              id={`order-dynamic-${field.fieldKey}`}
              labelText={label}
              value={value}
              onChange={(event) =>
                handleAdditionalFieldValueChange(
                  field.fieldKey,
                  event.target.value,
                )
              }
              disabled={readonly}
            >
              <SelectItem value="" text="" />
              {options.map((option) => (
                <SelectItem
                  key={`${field.fieldKey}-${option.optionKey}`}
                  value={option.optionKey}
                  text={option.optionLabel}
                />
              ))}
            </Select>
          </Column>
        );
      case "RADIO":
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <RadioButtonGroup
              legendText={label}
              name={`order-dynamic-radio-${field.fieldKey}`}
              valueSelected={value}
              onChange={(selectedValue) =>
                handleAdditionalFieldValueChange(field.fieldKey, selectedValue)
              }
            >
              {options.map((option) => (
                <RadioButton
                  key={`${field.fieldKey}-${option.optionKey}`}
                  id={`order-dynamic-${field.fieldKey}-${option.optionKey}`}
                  labelText={option.optionLabel}
                  value={option.optionKey}
                  disabled={readonly}
                />
              ))}
            </RadioButtonGroup>
          </Column>
        );
      case "MULTISELECT": {
        const selectedValues = new Set(
          String(value)
            .split(",")
            .map((entry) => entry.trim())
            .filter(Boolean),
        );
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <label htmlFor={`order-dynamic-${field.fieldKey}`}>{label}</label>
            <div id={`order-dynamic-${field.fieldKey}`}>
              {options.map((option) => (
                <Checkbox
                  key={`${field.fieldKey}-${option.optionKey}`}
                  id={`order-dynamic-${field.fieldKey}-${option.optionKey}`}
                  labelText={option.optionLabel}
                  checked={selectedValues.has(option.optionKey)}
                  onChange={(_event, { checked }) =>
                    handleAdditionalMultiSelectOption(
                      field.fieldKey,
                      option.optionKey,
                      checked,
                    )
                  }
                  disabled={readonly}
                />
              ))}
            </div>
          </Column>
        );
      }
      case "DOCUMENT":
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <div style={{ marginBottom: "0.5rem" }}>{label}</div>
            <FileUploader
              buttonLabel={<FormattedMessage id="label.button.uploadfile" />}
              filenameStatus={currentFile?.fileName ? "complete" : ""}
              accept={acceptedMimeTypes}
              multiple={false}
              onChange={(event) => handleAdditionalFileUpload(field, event)}
              filename={currentFile?.fileName}
              disabled={readonly}
            />
            {currentFile?.fileName ? (
              <div style={{ marginTop: "0.5rem" }}>
                <Link
                  onClick={() =>
                    openAdditionalFieldFilePreview(field.fieldKey, currentFile)
                  }
                >
                  {currentFile.fileName}
                </Link>
                {orderFormValues?.sampleOrderItems?.sampleId ? (
                  <>
                    {"  "}
                    <Link
                      onClick={() =>
                        downloadAdditionalFieldFile(field.fieldKey)
                      }
                    >
                      <FormattedMessage id="order.additional.fields.document.download" />
                    </Link>
                  </>
                ) : null}
                {!readonly ? (
                  <>
                    {"  "}
                    <Link
                      onClick={() => removeAdditionalFieldFile(field.fieldKey)}
                    >
                      <FormattedMessage id="label.button.remove" />
                    </Link>
                  </>
                ) : null}
              </div>
            ) : null}
          </Column>
        );
      case "TEXT":
      default:
        return (
          <Column key={field.fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              id={`order-dynamic-${field.fieldKey}`}
              labelText={label}
              value={value}
              onChange={(event) =>
                handleAdditionalFieldValueChange(
                  field.fieldKey,
                  event.target.value,
                )
              }
              maxLength={field.maxLength || undefined}
              readOnly={readonly}
            />
          </Column>
        );
    }
  };

  const getEffectiveFixedFieldConfigs = () => {
    const configs = orderFormValues?.sampleOrderItems?.fixedFieldConfigs;
    if (Array.isArray(configs) && configs.length > 0) {
      return configs;
    }
    return DEFAULT_FIXED_FIELD_ORDER;
  };

  const getOrderedOrderFieldDescriptors = () => {
    const fixedFields = getEffectiveFixedFieldConfigs()
      .map((config) => ({
        type: "fixed",
        fieldKey: config.fieldKey,
        sortOrder: Number(config?.sortOrder ?? 0),
      }))
      .filter((field) => isFieldVisible(field.fieldKey));

    const customFields = (
      orderFormValues?.sampleOrderItems?.additionalFields || []
    )
      .filter(
        (field) =>
          field &&
          field.active !== false &&
          field.fieldKey &&
          isDynamicFieldVisible(field),
      )
      .map((field) => ({
        type: "custom",
        field,
        sortOrder: Number(field?.sortOrder ?? 0),
      }));

    const staticFields = [
      { type: "static", fieldKey: "requesterSearch", sortOrder: 75 },
    ];

    const typeRank = {
      fixed: 0,
      static: 1,
      custom: 2,
    };

    return [...fixedFields, ...staticFields, ...customFields].sort(
      (left, right) => {
        if (left.sortOrder !== right.sortOrder) {
          return left.sortOrder - right.sortOrder;
        }

        const leftRank = typeRank[left.type] ?? 99;
        const rightRank = typeRank[right.type] ?? 99;
        if (leftRank !== rightRank) {
          return leftRank - rightRank;
        }

        const leftKey =
          left.type === "custom" ? left.field.fieldKey : left.fieldKey;
        const rightKey =
          right.type === "custom" ? right.field.fieldKey : right.fieldKey;
        return String(leftKey || "").localeCompare(String(rightKey || ""));
      },
    );
  };

  const renderRequesterSearchField = () => (
    <Column key="requesterSearch" lg={8} md={4} sm={4}>
      <AutoComplete
        name="requesterId"
        id="requesterId"
        allowFreeText={
          !(configurationProperties.restrictFreeTextProviderEntry === "true")
        }
        value={buildRequesterDisplayValue()}
        onSelect={handleProviderSelectOptions}
        onChange={clearProviderId}
        label={
          <>
            <FormattedMessage id="order.search.requester.label" />{" "}
            <span className="requiredlabel">*</span>
          </>
        }
        style={{ width: "!important 100%" }}
        invalidText={
          <FormattedMessage id="order.invalid.requester.name.label" />
        }
        suggestions={providers.length > 0 ? providers : []}
        required
      />
    </Column>
  );

  const renderFixedOrderField = (fieldKey) => {
    switch (fieldKey) {
      case "priority":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <Select
              id="priorityId"
              name="priority"
              labelText={intl.formatMessage({
                id: "workplan.priority.list",
              })}
              value={orderFormValues.sampleOrderItems.priority}
              onChange={handlePriority}
              disabled={isFieldReadonly("priority")}
            >
              {priorityOptions.map((priority) => {
                return (
                  <SelectItem
                    key={priority.id}
                    text={priority.value}
                    value={priority.id}
                  />
                );
              })}
            </Select>
          </Column>
        );
      case "requestDate":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <CustomDatePicker
              id={"order_requestDate"}
              labelText={intl.formatMessage({ id: "sample.requestDate" })}
              autofillDate={true}
              value={
                orderFormValues.sampleOrderItems.requestDate
                  ? orderFormValues.sampleOrderItems.requestDate
                  : configurationProperties.currentDateAsText
              }
              disallowFutureDate={true}
              onChange={(date) => handleDatePickerChange("requestDate", date)}
            />
          </Column>
        );
      case "receivedDateForDisplay":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <CustomDatePicker
              id={"order_receivedDate"}
              labelText={intl.formatMessage({ id: "sample.receivedDate" })}
              autofillDate={true}
              value={
                orderFormValues.sampleOrderItems.receivedDateForDisplay
                  ? orderFormValues.sampleOrderItems.receivedDateForDisplay
                  : configurationProperties.currentDateAsText
              }
              disallowFutureDate={true}
              onChange={(date) => handleDatePickerChange("receivedDate", date)}
            />
          </Column>
        );
      case "receivedTime":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <CustomTimePicker
              id="order_receivedTime"
              labelText={intl.formatMessage({ id: "order.reception.time" })}
              onChange={handleReceivedTime}
              value={
                orderFormValues.sampleOrderItems.receivedTime
                  ? orderFormValues.sampleOrderItems.receivedTime
                  : configurationProperties.currentTimeAsText
              }
              disabled={isFieldReadonly("receivedTime")}
            />
          </Column>
        );
      case "nextVisitDate":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <CustomDatePicker
              id={"order_nextVisitDate"}
              labelText={intl.formatMessage({
                id: "sample.entry.nextVisit.date",
              })}
              value={orderFormValues.sampleOrderItems.nextVisitDate}
              autofillDate={false}
              disallowPastDate={true}
              onChange={(date) => handleDatePickerChange("nextVisitDate", date)}
            />
          </Column>
        );
      case "referringSiteName":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <AutoComplete
              name="siteName"
              id="siteName"
              allowFreeText={
                !(
                  configurationProperties.restrictFreeTextRefSiteEntry ===
                  "true"
                )
              }
              value={
                orderFormValues.sampleOrderItems.referringSiteId != ""
                  ? orderFormValues.sampleOrderItems.referringSiteId
                  : orderFormValues.sampleOrderItems.referringSiteName
              }
              onChange={handleSiteName}
              onSelect={handleAutoCompleteSiteName}
              label={
                <>
                  <FormattedMessage id="order.search.site.name" />{" "}
                  {isFieldRequired("referringSiteName", true) ? (
                    <span className="requiredlabel">*</span>
                  ) : null}
                </>
              }
              style={{ width: "!important 100%" }}
              suggestions={siteNames.length > 0 ? siteNames : []}
              required={isFieldRequired("referringSiteName", true)}
            />
          </Column>
        );
      case "referringSiteDepartmentId":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <Select
              id="requesterDepartmentId"
              name="requesterDepartmentId"
              labelText={intl.formatMessage({
                id: "order.department.label",
              })}
              onChange={handleRequesterDept}
              value={orderFormValues.sampleOrderItems.referringSiteDepartmentId}
              disabled={isFieldReadonly("referringSiteDepartmentId")}
            >
              <SelectItem value="" text="" />
              {departments.map((department, index) => (
                <SelectItem
                  key={index}
                  text={department.value}
                  value={department.id}
                />
              ))}
            </Select>
          </Column>
        );
      case "provisionalClinicalDiagnosis":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              name="provisionalDiagnosis"
              placeholder={intl.formatMessage({
                id: "input.placeholder.provisionalClinicalDiagnosis",
              })}
              onChange={handleProvisionalClinicalDiagnosisChange}
              value={
                orderFormValues.sampleOrderItems.provisionalClinicalDiagnosis
              }
              labelText={intl.formatMessage({
                id: "order.requester.provisionalDiagnosis.label",
              })}
              id="provisionalDiagnosisId"
              readOnly={isFieldReadonly("provisionalClinicalDiagnosis")}
            />
          </Column>
        );
      case "providerFirstName":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              name="requesterFirstName"
              placeholder={intl.formatMessage({
                id: "input.placeholder.requesterFirstName",
              })}
              labelText={
                <>
                  <FormattedMessage id="order.requester.firstName.label" />
                  {isFieldRequired("providerFirstName", true) ? (
                    <span className="requiredlabel">*</span>
                  ) : null}
                </>
              }
              disabled={
                configurationProperties.restrictFreeTextProviderEntry ===
                  "true" || isFieldReadonly("providerFirstName")
              }
              onChange={handleRequesterFirstName}
              onClick={() => handleChange("sampleOrderItems.providerFirstName")}
              value={orderFormValues.sampleOrderItems.providerFirstName}
              invalid={
                changed["sampleOrderItems.providerFirstName"] &&
                error("sampleOrderItems.providerFirstName")
                  ? true
                  : false
              }
              invalidText={error("sampleOrderItems.providerFirstName")}
              id="requesterFirstName"
            />
          </Column>
        );
      case "providerLastName":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              name="requesterLastName"
              placeholder={intl.formatMessage({
                id: "input.placeholder.requesterLastName",
              })}
              labelText={
                <>
                  <FormattedMessage id="order.requester.lastName.label" />
                  {isFieldRequired("providerLastName", true) ? (
                    <span className="requiredlabel">*</span>
                  ) : null}
                </>
              }
              disabled={
                configurationProperties.restrictFreeTextProviderEntry ===
                  "true" || isFieldReadonly("providerLastName")
              }
              value={orderFormValues.sampleOrderItems.providerLastName}
              onClick={() => handleChange("sampleOrderItems.providerLastName")}
              onChange={handleRequesterLastName}
              id="requesterLastName"
              invalid={
                changed["sampleOrderItems.providerLastName"] &&
                error("sampleOrderItems.providerLastName")
                  ? true
                  : false
              }
              invalidText={error("sampleOrderItems.providerLastName")}
            />
          </Column>
        );
      case "providerWorkPhone":
        return (
          <Column key={fieldKey} lg={8} sm={4}>
            <TextInput
              name="providerWorkPhone"
              placeholder={intl.formatMessage({
                id: "input.placeholder.providerWorkPhone",
              })}
              disabled={
                configurationProperties.restrictFreeTextProviderEntry ===
                  "true" || isFieldReadonly("providerWorkPhone")
              }
              onChange={handleRequesterWorkPhone}
              value={orderFormValues.sampleOrderItems.providerWorkPhone}
              onMouseLeave={handlePhoneNoValidation}
              labelText={intl.formatMessage({
                id: "order.requester.phone.label",
              })}
              id="providerWorkPhoneId"
            />
          </Column>
        );
      case "providerCmp":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              name="providerCmp"
              labelText="CMP"
              disabled={isFieldReadonly("providerCmp")}
              onChange={handleRequesterCmp}
              value={orderFormValues.sampleOrderItems.providerCmp || ""}
              id="providerCmpId"
            />
          </Column>
        );
      case "providerRne":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              name="providerRne"
              labelText="RNE"
              disabled={isFieldReadonly("providerRne")}
              onChange={handleRequesterRne}
              value={orderFormValues.sampleOrderItems.providerRne || ""}
              id="providerRneId"
            />
          </Column>
        );
      case "providerDni":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              name="providerDni"
              labelText="DNI"
              disabled={isFieldReadonly("providerDni")}
              onChange={handleRequesterDni}
              value={orderFormValues.sampleOrderItems.providerDni || ""}
              id="providerDniId"
            />
          </Column>
        );
      case "providerSpecialty":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            {providerSpecialtyOptions.length > 0 ? (
              <Select
                id="providerSpecialtyId"
                name="providerSpecialty"
                labelText={intl.formatMessage({
                  id: "provider.specialty.label",
                  defaultMessage: "Specialty",
                })}
                disabled={isFieldReadonly("providerSpecialty")}
                onChange={handleRequesterSpecialty}
                value={orderFormValues.sampleOrderItems.providerSpecialty || ""}
              >
                <SelectItem value="" text="" />
                {providerSpecialtyOptions.map((option) => (
                  <SelectItem
                    key={`provider-specialty-option-${option.id}`}
                    value={option.id}
                    text={option.value}
                  />
                ))}
              </Select>
            ) : (
              <TextInput
                name="providerSpecialty"
                labelText={intl.formatMessage({
                  id: "provider.specialty.label",
                  defaultMessage: "Specialty",
                })}
                disabled={isFieldReadonly("providerSpecialty")}
                onChange={handleRequesterSpecialty}
                value={orderFormValues.sampleOrderItems.providerSpecialty || ""}
                id="providerSpecialtyId"
              />
            )}
          </Column>
        );
      case "providerFax":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              name="providerFax"
              placeholder={intl.formatMessage({
                id: "input.placeholder.providerFax",
              })}
              labelText={intl.formatMessage({
                id: "order.requester.fax.label",
              })}
              disabled={
                configurationProperties.restrictFreeTextProviderEntry ===
                  "true" || isFieldReadonly("providerFax")
              }
              onChange={handleRequesterFax}
              value={orderFormValues.sampleOrderItems.providerFax}
              id="providerFaxId"
            />
          </Column>
        );
      case "providerEmail":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              name="providerEmail"
              placeholder={intl.formatMessage({
                id: "input.placeholder.providerEmail",
              })}
              labelText={intl.formatMessage({
                id: "order.requester.email.label",
              })}
              disabled={
                configurationProperties.restrictFreeTextProviderEntry ===
                  "true" || isFieldReadonly("providerEmail")
              }
              onChange={handleRequesterEmail}
              value={orderFormValues.sampleOrderItems.providerEmail}
              id="providerEmailId"
              invalid={error("sampleOrderItems.providerEmail") ? true : false}
              invalidText={intl.formatMessage({
                id: "error.invalid.email",
              })}
            />
          </Column>
        );
      case "paymentOptionSelection":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <Select
              id="paymentOptionSelectionId"
              name="paymentOptionSelections"
              value={orderFormValues.sampleOrderItems.paymentOptionSelection}
              labelText={intl.formatMessage({
                id: "order.payment.status.label",
              })}
              onChange={handlePaymentStatus}
              disabled={isFieldReadonly("paymentOptionSelection")}
            >
              <SelectItem value="" text="" />
              {paymentOptions &&
                paymentOptions.map((option) => {
                  return (
                    <SelectItem
                      key={option.id}
                      value={option.id}
                      text={option.value}
                    />
                  );
                })}
            </Select>
          </Column>
        );
      case "testLocationCode":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <Select
              id="testLocationCodeId"
              name="testLocationCode"
              value={orderFormValues.sampleOrderItems.testLocationCode}
              labelText={
                <FormattedMessage id="order.sampling.performed.label" />
              }
              onChange={(e) => handleSamplingPerformed(e)}
              disabled={isFieldReadonly("testLocationCode")}
            >
              <SelectItem value="" text="" />
              {samplingPerformed.map((option) => {
                return (
                  <SelectItem
                    key={option.id}
                    value={option.id}
                    text={option.value}
                  />
                );
              })}
            </Select>
          </Column>
        );
      case "otherLocationCode":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <TextInput
              name="testLocationCodeOther"
              labelText={intl.formatMessage({ id: "order.if.other.label" })}
              onChange={handleOtherLocationCode}
              value={orderFormValues.sampleOrderItems.otherLocationCode}
              disabled={
                !otherSamplingVisible || isFieldReadonly("otherLocationCode")
              }
              id="testLocationCodeOtherId"
            />
          </Column>
        );
      case "rememberSiteAndRequester":
        return (
          <Column key={fieldKey} lg={8} md={4} sm={4}>
            <Checkbox
              labelText={
                <FormattedMessage id="order.remember.site.and.requester.label" />
              }
              id="rememberSiteAndRequester"
              onChange={handleRememberCheckBox}
              checked={!!orderFormValues.rememberSiteAndRequester}
              disabled={isFieldReadonly("rememberSiteAndRequester")}
            />
          </Column>
        );
      default:
        return null;
    }
  };

  const renderOrderedOrderField = (descriptor) => {
    if (descriptor.type === "custom") {
      return renderDynamicField(descriptor.field);
    }
    if (descriptor.type === "static") {
      return renderRequesterSearchField();
    }
    return renderFixedOrderField(descriptor.fieldKey);
  };

  const loadPriorityOptions = (response) => {
    if (!componentMounted.current) {
      return;
    }
    if (!Array.isArray(response) || response.length === 0) {
      setPriorityOptions(DEFAULT_ORDER_PRIORITIES);
      return;
    }
    setPriorityOptions(
      response.map((priority) => ({
        id: priority.id,
        value: priority.value,
      })),
    );
  };

  useEffect(() => {
    componentMounted.current = true;
    getFromOpenElisServer("/rest/SamplePatientEntry", getSampleEntryPreform);
    getFromOpenElisServer("/rest/priorities", loadPriorityOptions);
    window.scrollTo(0, 0);
    return () => {
      componentMounted.current = false;
    };
  }, []);

  useEffect(() => {
    const selectedLocationCode =
      orderFormValues?.sampleOrderItems?.testLocationCode;
    setOtherSamplingVisible(selectedLocationCode === "1310");
  }, [orderFormValues?.sampleOrderItems?.testLocationCode]);

  const handleDatePickerChange = (datePicker, date) => {
    let obj = null;
    switch (datePicker) {
      case "requestDate":
        obj = { ...orderFormValues.sampleOrderItems, requestDate: date };
        break;
      case "receivedDate":
        obj = {
          ...orderFormValues.sampleOrderItems,
          receivedDateForDisplay: date,
        };
        break;
      case "nextVisitDate":
        obj = { ...orderFormValues.sampleOrderItems, nextVisitDate: date };
        break;
      default:
    }
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: obj,
    });
  };

  function handlePaymentStatus(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        paymentOptionSelection: e.target.value,
      },
    });
  }

  function handleRequesterFax(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerFax: e.target.value,
      },
    });
  }

  function handleRequesterEmail(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerEmail: e.target.value,
      },
    });
  }

  function handleProvisionalClinicalDiagnosisChange(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        provisionalClinicalDiagnosis: e.target.value,
      },
    });
    setNotificationVisible(false);
  }

  function handleRequesterWorkPhone(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerWorkPhone: e.target.value,
      },
    });
    setNotificationVisible(false);
  }

  function handleRequesterCmp(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerCmp: e.target.value,
      },
    });
  }

  function handleRequesterRne(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerRne: e.target.value,
      },
    });
  }

  function handleRequesterDni(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerDni: e.target.value,
      },
    });
  }

  function handleRequesterSpecialty(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerSpecialty: e.target.value,
      },
    });
  }

  function handleRequesterFirstName(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerFirstName: e.target.value,
      },
    });
  }
  function handleChange(path) {
    console.log([path]);
    setChanged({
      ...changed,
      [path]: true,
    });
  }

  function handleRequesterLastName(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerLastName: e.target.value,
      },
    });
  }

  const handleSamplingPerformed = (e) => {
    const { value } = e.target;
    if (value === "1310") {
      setOtherSamplingVisible(!otherSamplingVisible);
    } else {
      setOtherSamplingVisible(false);
    }
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        testLocationCode: value,
      },
    });
  };

  function handleOtherLocationCode(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        otherLocationCode: e.target.value,
      },
    });
  }

  function handleReceivedTime(valueOrEvent) {
    const receivedTime =
      typeof valueOrEvent === "string"
        ? valueOrEvent
        : valueOrEvent?.target?.value || "";
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        receivedTime,
      },
    });
  }

  const handleLabNoGeneration = (e) => {
    if (e) {
      e.preventDefault();
    }
    getFromOpenElisServer(
      "/rest/SampleEntryGenerateScanProvider",
      fetchGeneratedAccessionNo,
    );
  };

  function accessionNumberValidationResults(res) {
    if (res.status === false) {
      setNotificationVisible(true);
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: res.body,
      });
    }
  }

  function handleProviderSelectOptions(providerId) {
    handleChange("sampleOrderItems.providerId");
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerPersonId: providerId,
      },
    });

    getFromOpenElisServer(
      "/rest/practitioner?providerId=" + providerId,
      fetchPractitioner,
    );
  }

  function fetchPractitioner(data) {
    const person = data?.person || {};
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerFirstName: person.firstName || "",
        providerLastName: person.lastName || "",
        providerWorkPhone: person.workPhone || "",
        providerEmail: person.email || "",
        providerFax: person.fax || "",
        providerCmp: data?.npi || "",
        providerRne: data?.externalId || "",
        providerDni: data?.dni || "",
        providerSpecialty: data?.specialty || "",
        providerId: data?.id || "",
        providerPersonId: person.id || "",
        referringSiteName: "",
      },
    });
  }

  function handleRequesterDept(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        referringSiteDepartmentId: e.target.value,
        referringSiteName: "",
      },
    });
  }

  function handleSiteName(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        referringSiteName: e.target.value,
        referringSiteId: "",
        referringSiteDepartmentId: "",
      },
    });
  }

  function clearProviderId(e) {
    handleChange("sampleOrderItems.providerId");
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        providerId: "",
        providerPersonId: "",
        providerCmp: "",
        providerRne: "",
        providerDni: "",
        providerSpecialty: "",
      },
    });
  }

  function handleAutoCompleteSiteName(siteId) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        referringSiteId: siteId,
        referringSiteName: "",
        referringSiteDepartmentId: "",
      },
    });
  }
  const loadDepartments = (data) => {
    setDepartments(data);
  };

  function handleLabNo(e, rawVal) {
    if (isModifyOrder) {
      setOrderFormValues({
        ...orderFormValues,
        newAccessionNumber: e?.target?.value,
      });
    } else {
      setOrderFormValues({
        ...orderFormValues,
        sampleOrderItems: {
          ...orderFormValues.sampleOrderItems,
          labNo: rawVal ? rawVal : e?.target?.value,
        },
      });
    }
    handleLabNoValidationOnChange(e?.target?.value);
    setNotificationVisible(false);
  }

  const handleLabNoValidationOnChange = (value) => {
    if (value) {
      getFromOpenElisServer(
        "/rest/SampleEntryAccessionNumberValidation?ignoreYear=false&ignoreUsage=false&field=labNo&accessionNumber=" +
          value,
        accessionNumberValidationResults,
      );
    }
  };

  function fetchPhoneNoValidation(res) {
    if (res.status === false) {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: res.body,
        kind: NotificationKinds.error,
      });
      setNotificationVisible(true);
    }
  }

  const handlePhoneNoValidation = () => {
    if (orderFormValues.sampleOrderItems.providerWorkPhone) {
      const providerPhoneNo =
        orderFormValues.sampleOrderItems.providerWorkPhone.replace(
          /\+/g,
          "%2B",
        );
      getFromOpenElisServer(
        "/rest/PhoneNumberValidationProvider?fieldId=providerWorkPhoneID&value=" +
          providerPhoneNo,
        fetchPhoneNoValidation,
      );
    }
  };

  function handleRememberCheckBox(e) {
    let checked = false;
    if (e.currentTarget.checked) {
      checked = true;
    }
    setOrderFormValues({
      ...orderFormValues,
      rememberSiteAndRequester: checked,
    });
  }

  useEffect(() => {
    if (!innitialized) {
      setOrderFormValues({
        ...orderFormValues,
        sampleOrderItems: {
          ...orderFormValues.sampleOrderItems,
          requestDate: configurationProperties.currentDateAsText,
          receivedDateForDisplay: configurationProperties.currentDateAsText,
          nextVisitDate: configurationProperties.currentDateAsText,
          receivedTime: configurationProperties.currentTimeAsText,
        },
      });
    }
    if (orderFormValues.sampleOrderItems.requestDate != "") {
      setInnitialized(true);
    }
  }, [orderFormValues]);

  useEffect(() => {
    getFromOpenElisServer(
      "/rest/departments-for-site?refferingSiteId=" +
        (orderFormValues.sampleOrderItems.referringSiteId || ""),
      loadDepartments,
    );
  }, [orderFormValues.sampleOrderItems.referringSiteId]);

  function handlePriority(e) {
    setOrderFormValues({
      ...orderFormValues,
      sampleOrderItems: {
        ...orderFormValues.sampleOrderItems,
        priority: e.target.value,
      },
    });
  }

  function fetchGeneratedAccessionNo(res) {
    if (res.status) {
      if (isModifyOrder) {
        setOrderFormValues({
          ...orderFormValues,
          newAccessionNumber: res.body,
        });
      } else {
        setOrderFormValues({
          ...orderFormValues,
          sampleOrderItems: {
            ...orderFormValues.sampleOrderItems,
            labNo: res.body,
          },
        });
      }

      setNotificationVisible(false);
    }
  }

  const reportingNotifications = (object) => {
    setOrderFormValues({
      ...orderFormValues,
      customNotificationLogic: true,
      patientSMSNotificationTestIds: object.patientSMSNotificationTestIds,
      patientEmailNotificationTestIds: object.patientEmailNotificationTestIds,
      providerSMSNotificationTestIds: object.providerSMSNotificationTestIds,
      providerEmailNotificationTestIds: object.providerEmailNotificationTestIds,
    });
  };

  const getSampleEntryPreform = (response) => {
    if (componentMounted.current && response?.sampleOrderItems) {
      setSiteNames(response.sampleOrderItems.referringSiteList || []);
      setPaymentOptions(response.sampleOrderItems.paymentOptions || []);
      setSamplingPerformed(
        response.sampleOrderItems.testLocationCodeList || [],
      );
      setProviders(response.sampleOrderItems.providersList || []);

      const responseAdditionalFields =
        response?.sampleOrderItems?.additionalFields || [];
      const responseFixedFieldConfigs =
        response?.sampleOrderItems?.fixedFieldConfigs || [];
      const responseAdditionalFieldFiles =
        response?.sampleOrderItems?.additionalFieldFiles || {};

      setOrderFormValues((previous) => {
        if (!previous?.sampleOrderItems) {
          return previous;
        }

        const shouldAdoptAdditionalFields =
          !isModifyOrder ||
          !(previous.sampleOrderItems.additionalFields || []).length;
        const shouldAdoptFixedConfigs = !(
          previous.sampleOrderItems.fixedFieldConfigs || []
        ).length;
        const shouldAdoptAdditionalFiles = !(
          previous.sampleOrderItems.additionalFieldFiles &&
          Object.keys(previous.sampleOrderItems.additionalFieldFiles).length
        );

        if (
          !shouldAdoptAdditionalFields &&
          !shouldAdoptFixedConfigs &&
          !shouldAdoptAdditionalFiles
        ) {
          return previous;
        }

        return {
          ...previous,
          sampleOrderItems: {
            ...previous.sampleOrderItems,
            additionalFields: shouldAdoptAdditionalFields
              ? responseAdditionalFields
              : previous.sampleOrderItems.additionalFields,
            fixedFieldConfigs: shouldAdoptFixedConfigs
              ? responseFixedFieldConfigs
              : previous.sampleOrderItems.fixedFieldConfigs,
            additionalFieldValues:
              previous.sampleOrderItems.additionalFieldValues || {},
            additionalFieldFiles: shouldAdoptAdditionalFiles
              ? responseAdditionalFieldFiles
              : previous.sampleOrderItems.additionalFieldFiles,
          },
        };
      });
      setWaitingForFixedFieldConfig(false);
      return;
    }

    if (componentMounted.current) {
      setWaitingForFixedFieldConfig(false);
    }
  };

  const handleKeyPress = (event) => {
    if (event.key === "Enter") {
      handleLabNoGeneration(event);
    }
  };

  return (
    <>
      <Stack gap={10}>
        <div className="orderLegendBody">
          <Grid>
            <Column lg={16} md={8} sm={4}>
              <h3>
                <FormattedMessage id="order.title" />
              </h3>
            </Column>
            {configurationProperties.ACCEPT_EXTERNAL_ORDERS === "true" && (
              <Column lg={16} md={8} sm={4}>
                <input
                  type="hidden"
                  name="externalOrderNumber"
                  id="externalOrderNumber"
                  value={orderFormValues.sampleOrderItems.externalOrderNumber}
                />
              </Column>
            )}
            {isModifyOrder && (
              <Column lg={16} md={8} sm={4}>
                <h5>
                  {" "}
                  <FormattedMessage id="sample.label.labnumber" />:{" "}
                  {orderFormValues.accessionNumber}
                </h5>
              </Column>
            )}

            <Column lg={8} md={4} sm={4}>
              <div>
                <CustomLabNumberInput
                  name="labNo"
                  placeholder={intl.formatMessage({
                    id: "input.placeholder.labNo",
                  })}
                  value={
                    isModifyOrder
                      ? orderFormValues.newAccessionNumber
                      : orderFormValues.sampleOrderItems.labNo
                  }
                  //onMouseLeave={handleLabNoValidation}
                  onClick={() => handleChange("sampleOrderItems.labNo")}
                  onChange={handleLabNo}
                  onKeyPress={handleKeyPress}
                  labelText={
                    <>
                      <FormattedMessage id="sample.label.labnumber" />{" "}
                      <span className="requiredlabel">*</span>
                    </>
                  }
                  id="labNo"
                  invalid={
                    changed["sampleOrderItems.labNo"] &&
                    error("sampleOrderItems.labNo")
                      ? true
                      : false
                  }
                  invalidText={error("sampleOrderItems.labNo")}
                />
                <div>
                  <FormattedMessage id="label.order.scan.text" />{" "}
                  <Link
                    data-cy="generate-labNumber"
                    href="#"
                    onClick={(e) => handleLabNoGeneration(e)}
                  >
                    <FormattedMessage id="sample.label.labnumber.generate" />
                  </Link>
                </div>
              </div>
            </Column>
            {getOrderedOrderFieldDescriptors().map((descriptor) =>
              renderOrderedOrderField(descriptor),
            )}
          </Grid>
        </div>
        <div className="orderLegendBody">
          <h3>
            <FormattedMessage id="order.result.reporting.heading" />
          </h3>
          {samples.map((sample, index) => {
            if (sample.tests.length > 0) {
              return (
                <div key={index}>
                  <h4>
                    {" "}
                    <FormattedMessage id="label.button.sample" /> {index + 1}
                  </h4>
                  <OrderResultReporting
                    selectedTests={sample.tests}
                    reportingNotifications={reportingNotifications}
                  />
                </div>
              );
            }
          })}
        </div>
      </Stack>
    </>
  );
};

export default AddOrder;
