import React, { useContext, useEffect, useMemo, useState } from "react";
import {
  Button,
  Checkbox,
  Column,
  DataTable,
  Grid,
  Heading,
  MultiSelect,
  Section,
  Select,
  SelectItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  Tag,
  TextArea,
  TextInput,
} from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  deleteFromOpenElisServer,
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils";
import PageBreadCrumb from "../../common/PageBreadCrumb";
import { NotificationContext } from "../../layout/Layout";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification";

const FIELD_TYPE_OPTIONS = [
  "TEXT",
  "TEXTAREA",
  "NUMBER",
  "DATE",
  "TIME",
  "DATETIME",
  "BOOLEAN",
  "SELECT",
  "MULTISELECT",
  "RADIO",
  "DOCUMENT",
  "USER",
];

const LEGACY_USER_FIELD_TYPE = "SYSTEM_USER_BIOLOGIST_SELECT";
const GENERIC_USER_FIELD_TYPE = "USER";
const USER_DISPLAY_MODE_INITIALS = "INITIALS";
const USER_DISPLAY_MODE_NAME = "NAME";
const USER_DISPLAY_MODE_BOTH = "BOTH";

const CONDITION_LOGIC_OPTIONS = ["ALL", "ANY"];

const CONDITION_OPERATOR_OPTIONS = [
  "equals",
  "notequals",
  "in",
  "notin",
  "hasvalue",
  "istrue",
  "isfalse",
];

const VALUE_OPTIONAL_OPERATORS = new Set(["hasvalue", "istrue", "isfalse"]);

const MULTI_VALUE_OPERATORS = new Set(["in", "notin"]);

const createEmptyCondition = () => ({
  fieldKey: "",
  operator: "equals",
  value: "",
});

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "master.lists.page.test.management",
    link: "/MasterListsPage/testManagementConfigMenu",
  },
  {
    label: "order.additional.fields.menu",
    link: "/MasterListsPage/OrderAdditionalFields",
  },
];

const defaultNewField = {
  displayName: "",
  fieldKey: "",
  fieldType: "TEXT",
  required: false,
  searchable: false,
  searchUnique: false,
  active: true,
  defaultValue: "",
  maxLength: "",
  sortOrder: "",
  optionLines: "",
  rulesLogic: "ALL",
  visibleWhen: [],
  requiredWhen: [],
  documentAccept: "application/pdf",
  documentMaxSizeMb: "5",
  showInSampleReception: false,
  storageAssignmentRequired: false,
  userProfileCodes: [],
  userDisplayMode: USER_DISPLAY_MODE_BOTH,
};

const normalizeFieldType = (fieldType) =>
  String(fieldType || "").toUpperCase() === LEGACY_USER_FIELD_TYPE
    ? GENERIC_USER_FIELD_TYPE
    : fieldType || "TEXT";

const resolveUserProfileCodes = (fieldType, metadata) => {
  if (Array.isArray(metadata?.userProfileCodes)) {
    return metadata.userProfileCodes;
  }
  return [];
};

const resolveUserDisplayMode = (metadata) => {
  const mode = String(metadata?.userDisplayMode || "").toUpperCase();
  if (mode === USER_DISPLAY_MODE_INITIALS || mode === USER_DISPLAY_MODE_NAME) {
    return mode;
  }
  return USER_DISPLAY_MODE_BOTH;
};

const OrderAdditionalFieldsManagement = () => {
  const intl = useIntl();
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [fields, setFields] = useState([]);
  const [fixedConfigs, setFixedConfigs] = useState([]);
  const [newField, setNewField] = useState(defaultNewField);
  const [editingFieldId, setEditingFieldId] = useState(null);
  const [savingField, setSavingField] = useState(false);
  const [savingFixed, setSavingFixed] = useState(false);
  const [savingSortFieldId, setSavingSortFieldId] = useState(null);
  const [sampleFixedConfigs, setSampleFixedConfigs] = useState([]);
  const [savingSampleFixed, setSavingSampleFixed] = useState(false);
  const [professionalProfileOptions, setProfessionalProfileOptions] = useState([]);

  const loadFields = () => {
    getFromOpenElisServer(
      "/rest/order-additional-fields?includeInactive=true",
      (response) => {
        setFields(response || []);
      },
    );
  };

  const loadSampleFixedConfigs = () => {
    getFromOpenElisServer("/rest/sample-additional-fields/fixed", (data) =>
      setSampleFixedConfigs(Array.isArray(data) ? data : []),
    );
  };

  const loadFixedConfigs = () => {
    getFromOpenElisServer("/rest/order-additional-fields/fixed", (response) => {
      setFixedConfigs(response || []);
    });
  };

  useEffect(() => {
    loadFields();
    loadFixedConfigs();
    loadSampleFixedConfigs();
    getFromOpenElisServer("/rest/professional-profiles/catalog", (response) => {
      setProfessionalProfileOptions(
        Array.isArray(response?.profiles)
          ? response.profiles.map((profile) => ({
              id: profile.code,
              label: profile.label || profile.name || profile.code,
            }))
          : [],
      );
    });
  }, []);

  const sampleFixedRows = useMemo(
    () =>
      [...sampleFixedConfigs].sort(
        (a, b) => (a?.sortOrder ?? 0) - (b?.sortOrder ?? 0),
      ),
    [sampleFixedConfigs],
  );
  const fixedRows = useMemo(
    () =>
      [...fixedConfigs].sort((left, right) => {
        const leftSort = left?.sortOrder ?? 0;
        const rightSort = right?.sortOrder ?? 0;
        return leftSort - rightSort;
      }),
    [fixedConfigs],
  );

  const conditionFieldOptions = useMemo(() => {
    const map = new Map();

    fixedRows.forEach((config) => {
      if (!config?.fieldKey) {
        return;
      }
      map.set(config.fieldKey, {
        value: config.fieldKey,
        text: config.fieldKey,
      });
    });

    (fields || []).forEach((field) => {
      if (!field?.fieldKey) {
        return;
      }
      map.set(field.fieldKey, {
        value: field.fieldKey,
        text: `${field.displayName || field.fieldKey} (${field.fieldKey})`,
      });
    });

    return Array.from(map.values()).sort((left, right) =>
      left.text.localeCompare(right.text),
    );
  }, [fields, fixedRows]);

  const showNotification = (kind, message) => {
    setNotificationVisible(true);
    addNotification({
      kind,
      title: intl.formatMessage({ id: "notification.title" }),
      message,
    });
  };

  const resetFieldForm = () => {
    setNewField(defaultNewField);
    setEditingFieldId(null);
  };

  const parseOptions = (optionLines) => {
    if (!optionLines || !optionLines.trim()) {
      return [];
    }

    return optionLines
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line, index) => {
        const splitLine = line.split("|");
        if (splitLine.length >= 2) {
          return {
            optionKey: splitLine[0].trim(),
            optionLabel: splitLine.slice(1).join("|").trim(),
            active: true,
            sortOrder: index + 1,
          };
        }
        return {
          optionKey: splitLine[0].trim().toLowerCase().replace(/\s+/g, "_"),
          optionLabel: splitLine[0].trim(),
          active: true,
          sortOrder: index + 1,
        };
      })
      .filter((option) => option.optionLabel);
  };

  const parseCsvValues = (rawValue) =>
    String(rawValue || "")
      .split(",")
      .map((value) => value.trim())
      .filter(Boolean);

  const extractConditionValue = (condition) => {
    if (!condition) {
      return "";
    }
    if (Array.isArray(condition.values)) {
      return condition.values.join(", ");
    }
    return String(condition.value || "");
  };

  const mapFieldToForm = (field) => {
    let metadata = {};
    if (field?.metadataJson) {
      try {
        metadata = JSON.parse(field.metadataJson);
      } catch (_error) {
        metadata = {};
      }
    }

    const rules = metadata?.rules || {};
    const visibleWhen = Array.isArray(rules.visibleWhen)
      ? rules.visibleWhen.map((condition) => ({
          fieldKey: condition?.fieldKey || "",
          operator: condition?.operator || "equals",
          value: extractConditionValue(condition),
        }))
      : [];
    const requiredWhen = Array.isArray(rules.requiredWhen)
      ? rules.requiredWhen.map((condition) => ({
          fieldKey: condition?.fieldKey || "",
          operator: condition?.operator || "equals",
          value: extractConditionValue(condition),
        }))
      : [];

    const documentMetadata = metadata?.document || {};
    const sampleReceptionMetadata = metadata?.sampleReception || {};
    const optionLines = (field?.options || [])
      .filter((option) => option?.active !== false)
      .sort((left, right) => (left?.sortOrder ?? 0) - (right?.sortOrder ?? 0))
      .map((option) =>
        option?.optionKey
          ? `${option.optionKey}|${option.optionLabel || option.optionKey}`
          : option?.optionLabel || "",
      )
      .filter(Boolean)
      .join("\n");

    return {
      displayName: field?.displayName || "",
      fieldKey: field?.fieldKey || "",
      fieldType: normalizeFieldType(field?.fieldType),
      required: !!field?.required,
      searchable: !!field?.searchable,
      searchUnique: !!field?.searchUnique,
      active: field?.active !== false,
      defaultValue: field?.defaultValue || "",
      maxLength:
        field?.maxLength !== null && field?.maxLength !== undefined
          ? String(field.maxLength)
          : "",
      sortOrder:
        field?.sortOrder !== null && field?.sortOrder !== undefined
          ? String(field.sortOrder)
          : "",
      optionLines,
      rulesLogic: rules.logic || "ALL",
      visibleWhen,
      requiredWhen,
      documentAccept: Array.isArray(documentMetadata.accept)
        ? documentMetadata.accept.join(", ")
        : "application/pdf",
      documentMaxSizeMb: String(documentMetadata.maxSizeMb ?? 5),
      showInSampleReception: Boolean(
        sampleReceptionMetadata.showInSampleReception ??
          metadata?.showInSampleReception,
      ),
      storageAssignmentRequired: Boolean(
        metadata?.storage?.requireCheckedForStorageAssignment,
      ),
      userProfileCodes: resolveUserProfileCodes(field?.fieldType, metadata),
      userDisplayMode: resolveUserDisplayMode(metadata),
    };
  };

  const updateCondition = (groupKey, index, property, value) => {
    setNewField((previous) => ({
      ...previous,
      [groupKey]: (previous[groupKey] || []).map((condition, conditionIndex) =>
        conditionIndex === index
          ? {
              ...condition,
              [property]: value,
            }
          : condition,
      ),
    }));
  };

  const addCondition = (groupKey) => {
    setNewField((previous) => ({
      ...previous,
      [groupKey]: [...(previous[groupKey] || []), createEmptyCondition()],
    }));
  };

  const removeCondition = (groupKey, index) => {
    setNewField((previous) => ({
      ...previous,
      [groupKey]: (previous[groupKey] || []).filter(
        (_condition, conditionIndex) => conditionIndex !== index,
      ),
    }));
  };

  const normalizeCondition = (condition) => {
    const fieldKey = String(condition?.fieldKey || "").trim();
    const operator = String(condition?.operator || "equals").trim();
    const rawValue = String(condition?.value || "").trim();
    if (!fieldKey || !operator) {
      return null;
    }

    if (VALUE_OPTIONAL_OPERATORS.has(operator)) {
      return {
        fieldKey,
        operator,
      };
    }

    if (MULTI_VALUE_OPERATORS.has(operator)) {
      const values = parseCsvValues(rawValue);
      if (!values.length) {
        return null;
      }
      return {
        fieldKey,
        operator,
        values,
      };
    }

    if (!rawValue) {
      return null;
    }

    return {
      fieldKey,
      operator,
      value: rawValue,
    };
  };

  const isConditionValid = (condition) =>
    normalizeCondition(condition) !== null;

  const buildMetadataFromForm = (formValue) => {
    const hasInvalidVisibleCondition = (formValue.visibleWhen || []).some(
      (condition) => !isConditionValid(condition),
    );
    const hasInvalidRequiredCondition = (formValue.requiredWhen || []).some(
      (condition) => !isConditionValid(condition),
    );

    if (hasInvalidVisibleCondition || hasInvalidRequiredCondition) {
      return {
        errorMessageId: "order.additional.fields.rules.invalid",
        metadataJson: null,
      };
    }

    const visibleWhen = (formValue.visibleWhen || [])
      .map(normalizeCondition)
      .filter(Boolean);
    const requiredWhen = (formValue.requiredWhen || [])
      .map(normalizeCondition)
      .filter(Boolean);

    const metadata = {};

    if (visibleWhen.length > 0 || requiredWhen.length > 0) {
      metadata.rules = {
        logic: formValue.rulesLogic || "ALL",
      };
      if (visibleWhen.length > 0) {
        metadata.rules.visibleWhen = visibleWhen;
      }
      if (requiredWhen.length > 0) {
        metadata.rules.requiredWhen = requiredWhen;
      }
    }

    if ((formValue.fieldType || "").toUpperCase() === "DOCUMENT") {
      const acceptedMimeTypes = parseCsvValues(formValue.documentAccept);
      const maxSizeMb = Number.parseInt(formValue.documentMaxSizeMb, 10);
      if (acceptedMimeTypes.length > 0 || Number.isFinite(maxSizeMb)) {
        metadata.document = {};
        if (acceptedMimeTypes.length > 0) {
          metadata.document.accept = acceptedMimeTypes;
        }
        if (Number.isFinite(maxSizeMb) && maxSizeMb > 0) {
          metadata.document.maxSizeMb = maxSizeMb;
        }
      }
    }

    if (formValue.showInSampleReception) {
      metadata.sampleReception = {
        ...(metadata.sampleReception || {}),
        showInSampleReception: true,
      };
    }

    if (
      String(formValue.fieldType || "").toUpperCase() === "BOOLEAN" &&
      formValue.storageAssignmentRequired
    ) {
      metadata.storage = {
        ...(metadata.storage || {}),
        requireCheckedForStorageAssignment: true,
      };
    }

    if (String(formValue.fieldType || "").toUpperCase() === "USER") {
      metadata.userProfileCodes = Array.isArray(formValue.userProfileCodes)
        ? formValue.userProfileCodes.filter(Boolean)
        : [];
      metadata.userDisplayMode =
        formValue.userDisplayMode || USER_DISPLAY_MODE_BOTH;
    } else if (metadata.userProfileCodes) {
      delete metadata.userProfileCodes;
      delete metadata.userDisplayMode;
    } else if (metadata.userDisplayMode) {
      delete metadata.userDisplayMode;
    }

    return {
      metadataJson:
        Object.keys(metadata).length > 0 ? JSON.stringify(metadata) : null,
      errorMessageId: null,
    };
  };

  const normalizeSortOrder = (sortOrder) => {
    if (sortOrder === null || sortOrder === undefined || sortOrder === "") {
      return null;
    }
    const parsed = Number.parseInt(sortOrder, 10);
    return Number.isNaN(parsed) ? null : parsed;
  };

  const saveField = (event) => {
    event.preventDefault();
    setSavingField(true);

    const metadataBuildResult = buildMetadataFromForm(newField);
    if (metadataBuildResult.errorMessageId) {
      setSavingField(false);
      showNotification(
        NotificationKinds.error,
        intl.formatMessage({
          id: metadataBuildResult.errorMessageId,
        }),
      );
      return;
    }

    const sourceField = fields.find((field) => field.id === editingFieldId);
    const payload = {
      displayName: newField.displayName,
      fieldKey: newField.fieldKey,
      fieldType: newField.fieldType,
      required: newField.required,
      searchable: newField.searchable,
      searchUnique: newField.searchUnique,
      active: sourceField ? sourceField.active : true,
      defaultValue: newField.defaultValue || null,
      maxLength: newField.maxLength
        ? Number.parseInt(newField.maxLength, 10)
        : null,
      sortOrder: normalizeSortOrder(newField.sortOrder),
      metadataJson: metadataBuildResult.metadataJson,
      options: parseOptions(newField.optionLines),
    };

    if (
      sourceField &&
      (newField.fieldType === "SELECT" ||
        newField.fieldType === "MULTISELECT" ||
        newField.fieldType === "RADIO")
    ) {
      const existingOptionsByKey = new Map(
        (sourceField.options || []).map((option) => [option.optionKey, option]),
      );
      payload.options = payload.options.map((option) => {
        const existing = existingOptionsByKey.get(option.optionKey);
        return existing ? { ...option, id: existing.id } : option;
      });
    }

    const onSaveSuccess = () => {
      resetFieldForm();
      showNotification(
        NotificationKinds.success,
        intl.formatMessage({ id: "order.additional.fields.saved" }),
      );
      loadFields();
    };

    if (!editingFieldId) {
      postToOpenElisServerJsonResponse(
        "/rest/order-additional-fields",
        JSON.stringify(payload),
        (response) => {
          setSavingField(false);
          if (response?.id) {
            onSaveSuccess();
            return;
          }

          showNotification(
            NotificationKinds.error,
            response?.message || intl.formatMessage({ id: "server.error.msg" }),
          );
        },
      );
      return;
    }

    putToOpenElisServerFullResponse(
      `/rest/order-additional-fields/${editingFieldId}`,
      JSON.stringify(payload),
      (response) => {
        setSavingField(false);
        if (response.status >= 200 && response.status < 300) {
          onSaveSuccess();
          return;
        }

        showNotification(
          NotificationKinds.error,
          response?.message || intl.formatMessage({ id: "server.error.msg" }),
        );
      },
    );
  };

  const startEditingField = (field) => {
    if (!field?.id) {
      return;
    }
    setEditingFieldId(field.id);
    setNewField(mapFieldToForm(field));
  };

  const toggleFieldStatus = (field) => {
    if (!field?.id) {
      return;
    }

    if (field.active) {
      deleteFromOpenElisServer(
        `/rest/order-additional-fields/${field.id}`,
        (status) => {
          if (status === 204) {
            loadFields();
            showNotification(
              NotificationKinds.success,
              intl.formatMessage({ id: "order.additional.fields.updated" }),
            );
            return;
          }
          showNotification(
            NotificationKinds.error,
            intl.formatMessage({ id: "server.error.msg" }),
          );
        },
      );
      return;
    }

    const payload = {
      active: true,
      displayName: field.displayName,
      fieldType: field.fieldType,
      required: field.required,
      searchable: field.searchable,
      searchUnique: field.searchUnique,
      defaultValue: field.defaultValue,
      maxLength: field.maxLength,
      sortOrder: normalizeSortOrder(field.sortOrder),
      metadataJson: field.metadataJson,
      options: field.options || [],
    };

    putToOpenElisServerFullResponse(
      `/rest/order-additional-fields/${field.id}`,
      JSON.stringify(payload),
      (response) => {
        if (response.status >= 200 && response.status < 300) {
          loadFields();
          showNotification(
            NotificationKinds.success,
            intl.formatMessage({ id: "order.additional.fields.updated" }),
          );
          return;
        }
        showNotification(
          NotificationKinds.error,
          intl.formatMessage({ id: "server.error.msg" }),
        );
      },
    );
  };

  const saveFieldSortOrder = (field) => {
    if (!field?.id) {
      return;
    }

    setSavingSortFieldId(field.id);
    const payload = {
      displayName: field.displayName,
      fieldType: field.fieldType,
      required: field.required,
      searchable: field.searchable,
      searchUnique: field.searchUnique,
      active: field.active,
      defaultValue: field.defaultValue,
      maxLength: field.maxLength,
      sortOrder: normalizeSortOrder(field.sortOrder),
      metadataJson: field.metadataJson,
      options: field.options || [],
    };

    putToOpenElisServerFullResponse(
      `/rest/order-additional-fields/${field.id}`,
      JSON.stringify(payload),
      (response) => {
        setSavingSortFieldId(null);
        if (response.status >= 200 && response.status < 300) {
          loadFields();
          showNotification(
            NotificationKinds.success,
            intl.formatMessage({ id: "order.additional.fields.saved" }),
          );
          return;
        }
        showNotification(
          NotificationKinds.error,
          intl.formatMessage({ id: "server.error.msg" }),
        );
      },
    );
  };

  const updateSampleFixedConfig = (fieldKey, property, rawValue) => {
    setSampleFixedConfigs((previous) =>
      previous.map((config) =>
        config.fieldKey !== fieldKey
          ? config
          : { ...config, [property]: rawValue },
      ),
    );
  };

  const saveSampleFixedConfigs = () => {
    setSavingSampleFixed(true);
    putToOpenElisServerFullResponse(
      "/rest/sample-additional-fields/fixed",
      JSON.stringify(sampleFixedConfigs),
      (response) => {
        setSavingSampleFixed(false);
        if (response.status >= 200 && response.status < 300) {
          addNotification({
            kind: NotificationKinds.success,
            title: intl.formatMessage({ id: "notification.title" }),
            message: intl.formatMessage({ id: "save.success.msg" }),
          });
          setNotificationVisible(true);
          loadSampleFixedConfigs();
          return;
        }
        addNotification({
          kind: NotificationKinds.error,
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({ id: "server.error.msg" }),
        });
        setNotificationVisible(true);
      },
    );
  };

  const updateFixedConfig = (fieldKey, property, rawValue) => {
    setFixedConfigs((previous) =>
      previous.map((config) => {
        if (config.fieldKey !== fieldKey) {
          return config;
        }
        return {
          ...config,
          [property]: rawValue,
        };
      }),
    );
  };

  const saveFixedConfigs = () => {
    setSavingFixed(true);
    putToOpenElisServerFullResponse(
      "/rest/order-additional-fields/fixed",
      JSON.stringify(fixedConfigs),
      (response) => {
        setSavingFixed(false);
        if (response.status >= 200 && response.status < 300) {
          showNotification(
            NotificationKinds.success,
            intl.formatMessage({ id: "order.additional.fields.saved" }),
          );
          loadFixedConfigs();
          return;
        }
        showNotification(
          NotificationKinds.error,
          intl.formatMessage({ id: "server.error.msg" }),
        );
      },
    );
  };

  const renderConditionGroup = (groupKey, titleMessageId) => {
    const conditions = newField[groupKey] || [];

    return (
      <Stack gap={4}>
        <Heading>
          <FormattedMessage id={titleMessageId} />
        </Heading>
        {conditions.length === 0 ? (
          <p>
            <FormattedMessage id="order.additional.fields.rules.empty" />
          </p>
        ) : null}
        {conditions.map((condition, index) => {
          const selectedOperator = condition.operator || "equals";
          const showValueInput =
            !VALUE_OPTIONAL_OPERATORS.has(selectedOperator);
          return (
            <Grid fullWidth key={`${groupKey}-${index}`}>
              <Column lg={5} md={4} sm={4}>
                <Select
                  id={`${groupKey}-field-${index}`}
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.rules.field",
                  })}
                  value={condition.fieldKey}
                  onChange={(event) =>
                    updateCondition(
                      groupKey,
                      index,
                      "fieldKey",
                      event.target.value,
                    )
                  }
                >
                  <SelectItem value="" text="" />
                  {conditionFieldOptions.map((option) => (
                    <SelectItem
                      key={`${groupKey}-field-option-${option.value}`}
                      value={option.value}
                      text={option.text}
                    />
                  ))}
                </Select>
              </Column>
              <Column lg={5} md={4} sm={4}>
                <Select
                  id={`${groupKey}-operator-${index}`}
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.rules.operator",
                  })}
                  value={selectedOperator}
                  onChange={(event) =>
                    updateCondition(
                      groupKey,
                      index,
                      "operator",
                      event.target.value,
                    )
                  }
                >
                  {CONDITION_OPERATOR_OPTIONS.map((operator) => (
                    <SelectItem
                      key={`${groupKey}-operator-option-${operator}`}
                      value={operator}
                      text={intl.formatMessage({
                        id: `order.additional.fields.rules.operator.${operator}`,
                      })}
                    />
                  ))}
                </Select>
              </Column>
              <Column lg={4} md={4} sm={4}>
                {showValueInput ? (
                  <TextInput
                    id={`${groupKey}-value-${index}`}
                    labelText={intl.formatMessage({
                      id: MULTI_VALUE_OPERATORS.has(selectedOperator)
                        ? "order.additional.fields.rules.values"
                        : "order.additional.fields.rules.value",
                    })}
                    value={condition.value || ""}
                    onChange={(event) =>
                      updateCondition(
                        groupKey,
                        index,
                        "value",
                        event.target.value,
                      )
                    }
                  />
                ) : null}
              </Column>
              <Column lg={2} md={4} sm={4}>
                <Button
                  kind="ghost"
                  size="sm"
                  onClick={() => removeCondition(groupKey, index)}
                >
                  <FormattedMessage id="order.additional.fields.rules.remove" />
                </Button>
              </Column>
            </Grid>
          );
        })}
        <Button kind="ghost" size="sm" onClick={() => addCondition(groupKey)}>
          <FormattedMessage id="order.additional.fields.rules.add" />
        </Button>
      </Stack>
    );
  };

  return (
    <>
      {notificationVisible ? <AlertDialog /> : null}
      <div className="adminPageContent">
        <PageBreadCrumb breadcrumbs={breadcrumbs} />
        <Grid fullWidth>
          <Column lg={16} md={8} sm={4}>
            <Section>
              <Heading>
                <FormattedMessage id="order.additional.fields.title" />
              </Heading>
            </Section>
          </Column>
        </Grid>
        <div className="orderLegendBody">
          <Stack gap={6}>
            <Heading>
              <FormattedMessage id="order.additional.fields.fixed.title" />
            </Heading>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>
                      <FormattedMessage id="order.additional.fields.fieldKey" />
                    </TableHeader>
                    <TableHeader>
                      <FormattedMessage id="order.additional.fields.visible" />
                    </TableHeader>
                    <TableHeader>
                      <FormattedMessage id="order.additional.fields.required" />
                    </TableHeader>
                    <TableHeader>
                      <FormattedMessage id="order.additional.fields.readonly" />
                    </TableHeader>
                    <TableHeader>
                      <FormattedMessage id="order.additional.fields.showInSampleReception" />
                    </TableHeader>
                    <TableHeader>
                      <FormattedMessage id="order.additional.fields.sortOrder" />
                    </TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {fixedRows.map((config) => (
                    <TableRow key={config.fieldKey}>
                      <TableCell>{config.fieldKey}</TableCell>
                      <TableCell>
                        <Checkbox
                          id={`fixed-visible-${config.fieldKey}`}
                          labelText=""
                          checked={config.visible !== false}
                          onChange={(_event, { checked }) =>
                            updateFixedConfig(
                              config.fieldKey,
                              "visible",
                              checked,
                            )
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Checkbox
                          id={`fixed-required-${config.fieldKey}`}
                          labelText=""
                          checked={!!config.required}
                          onChange={(_event, { checked }) =>
                            updateFixedConfig(
                              config.fieldKey,
                              "required",
                              checked,
                            )
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Checkbox
                          id={`fixed-readonly-${config.fieldKey}`}
                          labelText=""
                          checked={!!config.readonly}
                          onChange={(_event, { checked }) =>
                            updateFixedConfig(
                              config.fieldKey,
                              "readonly",
                              checked,
                            )
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Checkbox
                          id={`fixed-sample-reception-${config.fieldKey}`}
                          labelText=""
                          checked={!!config.showInSampleReception}
                          onChange={(_event, { checked }) =>
                            updateFixedConfig(
                              config.fieldKey,
                              "showInSampleReception",
                              checked,
                            )
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <TextInput
                          id={`fixed-sort-${config.fieldKey}`}
                          labelText=""
                          type="number"
                          value={String(config.sortOrder ?? 0)}
                          onChange={(event) =>
                            updateFixedConfig(
                              config.fieldKey,
                              "sortOrder",
                              Number.parseInt(event.target.value || "0", 10),
                            )
                          }
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <Button
              onClick={saveFixedConfigs}
              disabled={savingFixed}
              data-cy="save-fixed-order-fields"
            >
              <FormattedMessage id="order.additional.fields.saveFixed" />
            </Button>
          </Stack>
        </div>

        <br />

        <div className="orderLegendBody">
          <Stack gap={6}>
            <Heading>
              <FormattedMessage id="order.additional.fields.custom.title" />
            </Heading>
            <Grid fullWidth>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="order-additional-display-name"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.displayName",
                  })}
                  value={newField.displayName}
                  onChange={(event) =>
                    setNewField((previous) => ({
                      ...previous,
                      displayName: event.target.value,
                    }))
                  }
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="order-additional-field-key"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.fieldKey",
                  })}
                  value={newField.fieldKey}
                  disabled={editingFieldId !== null}
                  onChange={(event) =>
                    setNewField((previous) => ({
                      ...previous,
                      fieldKey: event.target.value,
                    }))
                  }
                />
              </Column>
            </Grid>
            <Grid fullWidth>
              <Column lg={8} md={4} sm={4}>
                <Select
                  id="order-additional-field-type"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.fieldType",
                  })}
                  value={newField.fieldType}
                  onChange={(event) =>
                    setNewField((previous) => ({
                      ...previous,
                      fieldType: event.target.value,
                      storageAssignmentRequired:
                        event.target.value === "BOOLEAN"
                          ? previous.storageAssignmentRequired
                          : false,
                    }))
                  }
                >
                  {FIELD_TYPE_OPTIONS.map((fieldType) => (
                    <SelectItem
                      key={fieldType}
                      value={fieldType}
                      text={fieldType}
                    />
                  ))}
                </Select>
              </Column>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="order-additional-default-value"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.defaultValue",
                  })}
                  value={newField.defaultValue}
                  onChange={(event) =>
                    setNewField((previous) => ({
                      ...previous,
                      defaultValue: event.target.value,
                    }))
                  }
                />
              </Column>
            </Grid>
            <Grid fullWidth>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="order-additional-max-length"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.maxLength",
                  })}
                  type="number"
                  value={newField.maxLength}
                  onChange={(event) =>
                    setNewField((previous) => ({
                      ...previous,
                      maxLength: event.target.value,
                    }))
                  }
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="order-additional-sort-order"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.sortOrder",
                  })}
                  type="number"
                  value={newField.sortOrder}
                  onChange={(event) =>
                    setNewField((previous) => ({
                      ...previous,
                      sortOrder: event.target.value,
                    }))
                  }
                />
              </Column>
            </Grid>
            <Grid fullWidth>
              <Column lg={8} md={4} sm={4}>
                <Checkbox
                  id="order-additional-required"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.required",
                  })}
                  checked={newField.required}
                  onChange={(_event, { checked }) =>
                    setNewField((previous) => ({
                      ...previous,
                      required: checked,
                    }))
                  }
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <Checkbox
                  id="order-additional-searchable"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.searchable",
                  })}
                  checked={newField.searchable}
                  onChange={(_event, { checked }) =>
                    setNewField((previous) => ({
                      ...previous,
                      searchable: checked,
                      searchUnique: checked ? previous.searchUnique : false,
                    }))
                  }
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <Checkbox
                  id="order-additional-search-unique"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.searchUnique",
                  })}
                  checked={newField.searchUnique}
                  disabled={!newField.searchable}
                  onChange={(_event, { checked }) =>
                    setNewField((previous) => ({
                      ...previous,
                      searchUnique: checked,
                    }))
                  }
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <Checkbox
                  id="order-additional-show-in-sample-reception"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.showInSampleReception",
                  })}
                  checked={newField.showInSampleReception}
                  onChange={(_event, { checked }) =>
                    setNewField((previous) => ({
                      ...previous,
                      showInSampleReception: checked,
                    }))
                  }
                />
              </Column>
              {newField.fieldType === "BOOLEAN" ? (
                <Column lg={8} md={4} sm={4}>
                  <Checkbox
                    id="order-additional-storage-assignment-required"
                    labelText={intl.formatMessage({
                      id: "order.additional.fields.storageAssignmentRequired",
                    })}
                    checked={newField.storageAssignmentRequired}
                    onChange={(_event, { checked }) =>
                      setNewField((previous) => ({
                        ...previous,
                        storageAssignmentRequired: checked,
                      }))
                    }
                  />
                </Column>
              ) : null}
            </Grid>

            {(newField.fieldType === "SELECT" ||
              newField.fieldType === "MULTISELECT" ||
              newField.fieldType === "RADIO") && (
              <TextArea
                id="order-additional-options"
                labelText={intl.formatMessage({
                  id: "order.additional.fields.options",
                })}
                helperText={intl.formatMessage({
                  id: "order.additional.fields.options.helper",
                })}
                value={newField.optionLines}
                onChange={(event) =>
                  setNewField((previous) => ({
                    ...previous,
                    optionLines: event.target.value,
                  }))
                }
              />
            )}
            {newField.fieldType === "USER" ? (
              <Grid fullWidth condensed>
                <Column lg={8} md={4} sm={4}>
                  <div style={{ maxWidth: "32rem" }}>
                    <label
                      htmlFor="order-additional-user-profiles"
                      style={{ display: "block", marginBottom: "0.5rem" }}
                    >
                      {intl.formatMessage({
                        id: "order.additional.fields.userProfiles",
                        defaultMessage: "Allowed professional profiles",
                      })}
                    </label>
                    <MultiSelect
                      id="order-additional-user-profiles"
                      items={professionalProfileOptions}
                      itemToString={(item) => item?.label || ""}
                      selectedItems={professionalProfileOptions.filter((item) =>
                        (newField.userProfileCodes || []).includes(item.id),
                      )}
                      onChange={({ selectedItems }) =>
                        setNewField((previous) => ({
                          ...previous,
                          userProfileCodes: selectedItems.map(
                            (item) => item.id,
                          ),
                        }))
                      }
                      label=""
                      titleText=""
                      selectionFeedback="top-after-reopen"
                    />
                  </div>
                </Column>
                <Column lg={4} md={4} sm={4}>
                  <Select
                    id="orderAdditionalUserDisplayMode"
                    labelText={intl.formatMessage({
                      id: "user.field.display.mode.label",
                      defaultMessage: "Display user as",
                    })}
                    value={newField.userDisplayMode || USER_DISPLAY_MODE_BOTH}
                    onChange={(event) =>
                      setNewField((previous) => ({
                        ...previous,
                        userDisplayMode: event.target.value,
                      }))
                    }
                  >
                    <SelectItem
                      value={USER_DISPLAY_MODE_INITIALS}
                      text={intl.formatMessage({
                        id: "user.field.display.mode.initials",
                        defaultMessage: "Initials only",
                      })}
                    />
                    <SelectItem
                      value={USER_DISPLAY_MODE_NAME}
                      text={intl.formatMessage({
                        id: "user.field.display.mode.name",
                        defaultMessage: "Name only",
                      })}
                    />
                    <SelectItem
                      value={USER_DISPLAY_MODE_BOTH}
                      text={intl.formatMessage({
                        id: "user.field.display.mode.both",
                        defaultMessage: "Initials and name",
                      })}
                    />
                  </Select>
                </Column>
              </Grid>
            ) : null}
            <Stack gap={5}>
              <Heading>
                <FormattedMessage id="order.additional.fields.rules.title" />
              </Heading>
              <Grid fullWidth>
                <Column lg={6} md={4} sm={4}>
                  <Select
                    id="order-additional-rules-logic"
                    labelText={intl.formatMessage({
                      id: "order.additional.fields.rules.logic",
                    })}
                    value={newField.rulesLogic}
                    onChange={(event) =>
                      setNewField((previous) => ({
                        ...previous,
                        rulesLogic: event.target.value,
                      }))
                    }
                  >
                    {CONDITION_LOGIC_OPTIONS.map((logic) => (
                      <SelectItem
                        key={`logic-${logic}`}
                        value={logic}
                        text={intl.formatMessage({
                          id: `order.additional.fields.rules.logic.${logic.toLowerCase()}`,
                        })}
                      />
                    ))}
                  </Select>
                </Column>
              </Grid>

              {renderConditionGroup(
                "visibleWhen",
                "order.additional.fields.rules.visibleWhen",
              )}
              {renderConditionGroup(
                "requiredWhen",
                "order.additional.fields.rules.requiredWhen",
              )}
            </Stack>

            {newField.fieldType === "DOCUMENT" && (
              <Stack gap={4}>
                <Heading>
                  <FormattedMessage id="order.additional.fields.document.title" />
                </Heading>
                <Grid fullWidth>
                  <Column lg={8} md={4} sm={4}>
                    <TextInput
                      id="order-additional-document-accept"
                      labelText={intl.formatMessage({
                        id: "order.additional.fields.document.accept",
                      })}
                      helperText={intl.formatMessage({
                        id: "order.additional.fields.document.accept.helper",
                      })}
                      value={newField.documentAccept}
                      onChange={(event) =>
                        setNewField((previous) => ({
                          ...previous,
                          documentAccept: event.target.value,
                        }))
                      }
                    />
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <TextInput
                      id="order-additional-document-max-size"
                      labelText={intl.formatMessage({
                        id: "order.additional.fields.document.maxSizeMb",
                      })}
                      type="number"
                      min="1"
                      value={newField.documentMaxSizeMb}
                      onChange={(event) =>
                        setNewField((previous) => ({
                          ...previous,
                          documentMaxSizeMb: event.target.value,
                        }))
                      }
                    />
                  </Column>
                </Grid>
              </Stack>
            )}

            <Stack orientation="horizontal" gap={4}>
              <Button
                onClick={saveField}
                disabled={savingField}
                data-cy="create-order-additional-field"
              >
                {editingFieldId ? (
                  <FormattedMessage id="button.save" />
                ) : (
                  <FormattedMessage id="order.additional.fields.create" />
                )}
              </Button>
              {editingFieldId ? (
                <Button kind="ghost" onClick={resetFieldForm}>
                  <FormattedMessage id="button.cancel" />
                </Button>
              ) : null}
            </Stack>

            <DataTable
              rows={(fields || []).map((field) => ({
                id: String(field.id),
                displayName: field.displayName,
                fieldKey: field.fieldKey,
                fieldType: field.fieldType,
                sortOrder: field.sortOrder,
                required: field.required,
                searchable: field.searchable,
                searchUnique: field.searchUnique,
                active: field.active,
                showInSampleReception: Boolean(
                  (() => {
                    if (!field?.metadataJson) {
                      return false;
                    }
                    try {
                      const parsed = JSON.parse(field.metadataJson);
                      return Boolean(
                        parsed?.sampleReception?.showInSampleReception ??
                          parsed?.showInSampleReception,
                      );
                    } catch (_error) {
                      return false;
                    }
                  })(),
                ),
                options: field.options || [],
              }))}
              headers={[
                {
                  key: "displayName",
                  header: intl.formatMessage({
                    id: "order.additional.fields.displayName",
                  }),
                },
                {
                  key: "fieldKey",
                  header: intl.formatMessage({
                    id: "order.additional.fields.fieldKey",
                  }),
                },
                {
                  key: "fieldType",
                  header: intl.formatMessage({
                    id: "order.additional.fields.fieldType",
                  }),
                },
                {
                  key: "sortOrder",
                  header: intl.formatMessage({
                    id: "order.additional.fields.sortOrder",
                  }),
                },
                {
                  key: "required",
                  header: intl.formatMessage({
                    id: "order.additional.fields.required",
                  }),
                },
                {
                  key: "searchable",
                  header: intl.formatMessage({
                    id: "order.additional.fields.searchable",
                  }),
                },
                {
                  key: "searchUnique",
                  header: intl.formatMessage({
                    id: "order.additional.fields.searchUnique",
                  }),
                },
                {
                  key: "active",
                  header: intl.formatMessage({
                    id: "order.additional.fields.active",
                  }),
                },
                {
                  key: "showInSampleReception",
                  header: intl.formatMessage({
                    id: "order.additional.fields.showInSampleReception",
                  }),
                },
                {
                  key: "actions",
                  header: intl.formatMessage({
                    id: "order.additional.fields.actions",
                  }),
                },
              ]}
            >
              {({ rows, headers, getHeaderProps, getTableProps }) => (
                <TableContainer>
                  <Table {...getTableProps()}>
                    <TableHead>
                      <TableRow>
                        {headers.map((header) => (
                          <TableHeader
                            key={header.key}
                            {...getHeaderProps({ header })}
                          >
                            {header.header}
                          </TableHeader>
                        ))}
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {rows.map((row) => {
                        const sourceField = fields.find(
                          (field) => String(field.id) === row.id,
                        );
                        return (
                          <TableRow key={row.id}>
                            <TableCell>{row.cells[0].value}</TableCell>
                            <TableCell>{row.cells[1].value}</TableCell>
                            <TableCell>{row.cells[2].value}</TableCell>
                            <TableCell>
                              <TextInput
                                id={`custom-field-sort-order-${row.id}`}
                                labelText=""
                                type="number"
                                value={String(row.cells[3].value ?? "")}
                                onChange={(event) => {
                                  const nextSortOrder = event.target.value;
                                  setFields((previous) =>
                                    previous.map((field) => {
                                      if (String(field.id) !== row.id) {
                                        return field;
                                      }
                                      return {
                                        ...field,
                                        sortOrder: nextSortOrder,
                                      };
                                    }),
                                  );
                                }}
                              />
                            </TableCell>
                            <TableCell>
                              {row.cells[4].value ? (
                                <Tag type="green">
                                  <FormattedMessage id="yes.option" />
                                </Tag>
                              ) : (
                                <Tag type="gray">
                                  <FormattedMessage id="no.option" />
                                </Tag>
                              )}
                            </TableCell>
                            <TableCell>
                              <Checkbox
                                id={`custom-field-searchable-${row.id}`}
                                labelText=""
                                checked={!!sourceField?.searchable}
                                onChange={(_event, { checked }) => {
                                  setFields((previous) =>
                                    previous.map((field) => {
                                      if (String(field.id) !== row.id) {
                                        return field;
                                      }
                                      return {
                                        ...field,
                                        searchable: checked,
                                        searchUnique: checked
                                          ? field.searchUnique
                                          : false,
                                      };
                                    }),
                                  );
                                }}
                              />
                            </TableCell>
                            <TableCell>
                              <Checkbox
                                id={`custom-field-search-unique-${row.id}`}
                                labelText=""
                                checked={!!sourceField?.searchUnique}
                                disabled={!sourceField?.searchable}
                                onChange={(_event, { checked }) => {
                                  setFields((previous) =>
                                    previous.map((field) => {
                                      if (String(field.id) !== row.id) {
                                        return field;
                                      }
                                      return {
                                        ...field,
                                        searchUnique: checked,
                                      };
                                    }),
                                  );
                                }}
                              />
                            </TableCell>
                            <TableCell>
                              {row.cells[7].value ? (
                                <Tag type="green">
                                  <FormattedMessage id="status.active" />
                                </Tag>
                              ) : (
                                <Tag type="red">
                                  <FormattedMessage id="status.inactive" />
                                </Tag>
                              )}
                            </TableCell>
                            <TableCell>
                              {row.cells[8].value ? (
                                <Tag type="blue">
                                  <FormattedMessage id="yes.option" />
                                </Tag>
                              ) : (
                                <Tag type="gray">
                                  <FormattedMessage id="no.option" />
                                </Tag>
                              )}
                            </TableCell>
                            <TableCell>
                              <Button
                                kind="ghost"
                                size="sm"
                                onClick={() => startEditingField(sourceField)}
                              >
                                <FormattedMessage id="button.edit" />
                              </Button>
                              {"  "}
                              <Button
                                kind="tertiary"
                                size="sm"
                                disabled={savingSortFieldId === sourceField?.id}
                                onClick={() => saveFieldSortOrder(sourceField)}
                              >
                                <FormattedMessage id="button.save" />
                              </Button>
                              {"  "}
                              <Button
                                kind={
                                  row.cells[7].value ? "danger" : "secondary"
                                }
                                size="sm"
                                onClick={() => toggleFieldStatus(sourceField)}
                              >
                                {row.cells[7].value ? (
                                  <FormattedMessage id="order.additional.fields.disable" />
                                ) : (
                                  <FormattedMessage id="order.additional.fields.enable" />
                                )}
                              </Button>
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </DataTable>
          </Stack>
        </div>

        <br />

        <div className="orderLegendBody">
          <Stack gap={6}>
            <Heading>
              <FormattedMessage id="sample.fixed.fields.title" />
            </Heading>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableHeader>Field</TableHeader>
                    <TableHeader>Visible</TableHeader>
                    <TableHeader>Required</TableHeader>
                    <TableHeader>Readonly</TableHeader>
                    <TableHeader>Sort Order</TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {sampleFixedRows.map((config) => (
                    <TableRow key={config.fieldKey}>
                      <TableCell>{config.fieldKey}</TableCell>
                      <TableCell>
                        <Checkbox
                          id={`sample-fixed-visible-${config.fieldKey}`}
                          labelText=""
                          checked={config.visible !== false}
                          onChange={(_event, { checked }) =>
                            updateSampleFixedConfig(
                              config.fieldKey,
                              "visible",
                              checked,
                            )
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Checkbox
                          id={`sample-fixed-required-${config.fieldKey}`}
                          labelText=""
                          checked={!!config.required}
                          onChange={(_event, { checked }) =>
                            updateSampleFixedConfig(
                              config.fieldKey,
                              "required",
                              checked,
                            )
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <Checkbox
                          id={`sample-fixed-readonly-${config.fieldKey}`}
                          labelText=""
                          checked={!!config.readonly}
                          onChange={(_event, { checked }) =>
                            updateSampleFixedConfig(
                              config.fieldKey,
                              "readonly",
                              checked,
                            )
                          }
                        />
                      </TableCell>
                      <TableCell>
                        <TextInput
                          id={`sample-fixed-sort-${config.fieldKey}`}
                          labelText=""
                          type="number"
                          value={String(config.sortOrder ?? 0)}
                          onChange={(event) =>
                            updateSampleFixedConfig(
                              config.fieldKey,
                              "sortOrder",
                              Number.parseInt(event.target.value || "0", 10),
                            )
                          }
                        />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <Button
              onClick={saveSampleFixedConfigs}
              disabled={savingSampleFixed}
              data-cy="save-fixed-sample-fields"
            >
              <FormattedMessage id="sample.fixed.fields.save" />
            </Button>
          </Stack>
        </div>
      </div>
    </>
  );
};

export default OrderAdditionalFieldsManagement;
