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
  TextInput,
} from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb";
import {
  deleteFromOpenElisServer,
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils";
import { NotificationContext } from "../../layout/Layout";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification";
import AdditionalFieldOptionsEditor from "./customComponents/AdditionalFieldOptionsEditor";
import {
  getPatientFixedFieldLabel,
  normalizePatientFixedFieldConfigs,
  PATIENT_FIXED_FIELD_DEFINITION_MAP,
} from "../../patient/patientFixedFieldConfig";
import {
  createEmptyOption,
  FIELD_TYPE_OPTIONS,
  getAdditionalFieldTypeLabel,
  normalizeOptionForUi,
  normalizeOptionsForPayload,
  OPTION_FIELD_TYPES,
  toCodeCandidate,
} from "./additionalFieldOptionUtils";

const LEGACY_USER_FIELD_TYPE = "SYSTEM_USER_BIOLOGIST_SELECT";
const GENERIC_USER_FIELD_TYPE = "USER";
const USER_DISPLAY_MODE_INITIALS = "INITIALS";
const USER_DISPLAY_MODE_NAME = "NAME";
const USER_DISPLAY_MODE_BOTH = "BOTH";

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "master.lists.page.test.management",
    link: "/MasterListsPage/testManagementConfigMenu",
  },
  {
    label: "patient.additional.fields.menu",
    link: "/MasterListsPage/PatientAdditionalFields",
  },
];

const defaultNewField = {
  displayName: "",
  fieldKey: "",
  fieldType: "TEXT",
  hasSavedValues: false,
  required: false,
  active: true,
  defaultValue: "",
  maxLength: "",
  sortOrder: "",
  options: [],
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

const PatientAdditionalFieldsManagement = () => {
  const intl = useIntl();
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [fields, setFields] = useState([]);
  const [fixedConfigs, setFixedConfigs] = useState([]);
  const [professionalProfileOptions, setProfessionalProfileOptions] = useState([]);
  const [newField, setNewField] = useState(defaultNewField);
  const [editingFieldId, setEditingFieldId] = useState(null);
  const [savingField, setSavingField] = useState(false);
  const [savingFixed, setSavingFixed] = useState(false);
  const [savingSortFieldId, setSavingSortFieldId] = useState(null);
  const structureLocked = editingFieldId !== null && newField.hasSavedValues;

  const loadFields = () => {
    getFromOpenElisServer(
      "/rest/patient-additional-fields?includeInactive=true",
      (response) => {
        setFields(Array.isArray(response) ? response : []);
      },
    );
  };

  const loadFixedConfigs = () => {
    getFromOpenElisServer("/rest/patient-additional-fields/fixed", (response) => {
      setFixedConfigs(normalizePatientFixedFieldConfigs(response));
    });
  };

  useEffect(() => {
    loadFields();
    loadFixedConfigs();
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

  const parseFieldMetadata = (metadataJson) => {
    if (!metadataJson || typeof metadataJson !== "string") {
      return {};
    }

    try {
      const parsed = JSON.parse(metadataJson);
      return parsed && typeof parsed === "object" ? parsed : {};
    } catch (_error) {
      return {};
    }
  };

  const showNotification = (kind, message) => {
    setNotificationVisible(true);
    addNotification({
      kind,
      title: intl.formatMessage({ id: "notification.title" }),
      message,
    });
  };

  const mapFieldToForm = (field) => {
    return {
      displayName: field?.displayName || "",
      fieldKey: field?.fieldKey || "",
      fieldType: normalizeFieldType(field?.fieldType),
      hasSavedValues: field?.hasSavedValues === true,
      required: !!field?.required,
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
      options: Array.isArray(field?.options)
        ? field.options
            .filter((option) => option?.active !== false)
            .map((option, index) => normalizeOptionForUi(option, index))
        : [],
      userProfileCodes: resolveUserProfileCodes(
        field?.fieldType,
        parseFieldMetadata(field?.metadataJson),
      ),
      userDisplayMode: resolveUserDisplayMode(
        parseFieldMetadata(field?.metadataJson),
      ),
    };
  };

  const resetFieldForm = () => {
    setNewField(defaultNewField);
    setEditingFieldId(null);
  };

  const normalizeSortOrder = (sortOrder) => {
    if (sortOrder === null || sortOrder === undefined || sortOrder === "") {
      return null;
    }
    const parsed = Number.parseInt(sortOrder, 10);
    return Number.isNaN(parsed) ? null : parsed;
  };

  const addOption = () => {
    setNewField((previous) => ({
      ...previous,
      options: [
        ...(previous.options || []),
        createEmptyOption((previous.options || []).length + 1),
      ],
    }));
  };

  const updateFixedConfig = (fieldKey, property, rawValue) => {
    setFixedConfigs((previous) =>
      previous.map((config) =>
        config.fieldKey !== fieldKey
          ? config
          : {
              ...config,
              [property]: rawValue,
            },
      ),
    );
  };

  const saveFixedConfigs = () => {
    setSavingFixed(true);
    putToOpenElisServerFullResponse(
      "/rest/patient-additional-fields/fixed",
      JSON.stringify(fixedConfigs),
      (response) => {
        setSavingFixed(false);
        if (response.status >= 200 && response.status < 300) {
          showNotification(
            NotificationKinds.success,
            intl.formatMessage({ id: "patient.additional.fields.saved" }),
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

  const updateOptionLabel = (optionIndex, nextLabel) => {
    setNewField((previous) => ({
      ...previous,
      options: (previous.options || []).map((option, index) => {
        if (index !== optionIndex) {
          return option;
        }

        const nextGeneratedKey = toCodeCandidate(nextLabel);
        return {
          ...option,
          optionLabel: nextLabel,
          optionKey: option.isAutoGeneratedKey
            ? nextGeneratedKey
            : option.optionKey,
        };
      }),
    }));
  };

  const removeOption = (optionIndex) => {
    setNewField((previous) => ({
      ...previous,
      options: (previous.options || [])
        .filter((_, index) => index !== optionIndex)
        .map((option, index) => ({
          ...option,
          sortOrder: index + 1,
        })),
    }));
  };

  const saveField = (event) => {
    event.preventDefault();
    setSavingField(true);

    const payload = {
      displayName: newField.displayName,
      fieldKey: newField.fieldKey,
      fieldType: newField.fieldType,
      required: newField.required,
      active: true,
      defaultValue: newField.defaultValue || null,
      maxLength: newField.maxLength
        ? Number.parseInt(newField.maxLength, 10)
        : null,
      sortOrder: normalizeSortOrder(newField.sortOrder),
      metadataJson:
        newField.fieldType === "USER"
          ? JSON.stringify({
              userProfileCodes: newField.userProfileCodes || [],
              userDisplayMode:
                newField.userDisplayMode || USER_DISPLAY_MODE_BOTH,
            })
          : null,
      options: OPTION_FIELD_TYPES.has(newField.fieldType)
        ? normalizeOptionsForPayload(newField.options)
        : [],
    };

    const onSaveSuccess = () => {
      resetFieldForm();
      showNotification(
        NotificationKinds.success,
        intl.formatMessage({ id: "patient.additional.fields.saved" }),
      );
      loadFields();
    };

    if (!editingFieldId) {
      postToOpenElisServerJsonResponse(
        "/rest/patient-additional-fields",
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
      `/rest/patient-additional-fields/${editingFieldId}`,
      JSON.stringify(payload),
      async (response) => {
        setSavingField(false);
        if (response.status >= 200 && response.status < 300) {
          onSaveSuccess();
          return;
        }

        let errorMessage = intl.formatMessage({ id: "server.error.msg" });
        try {
          const errorBody = await response.json();
          errorMessage = errorBody?.message || errorMessage;
        } catch (_error) {
          // keep generic message
        }

        showNotification(NotificationKinds.error, errorMessage);
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
        `/rest/patient-additional-fields/${field.id}`,
        (status) => {
          if (status === 204) {
            loadFields();
            showNotification(
              NotificationKinds.success,
              intl.formatMessage({ id: "patient.additional.fields.updated" }),
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
      defaultValue: field.defaultValue,
      maxLength: field.maxLength,
      sortOrder: normalizeSortOrder(field.sortOrder),
      metadataJson: field.metadataJson,
      options: field.options || [],
    };

    putToOpenElisServerFullResponse(
      `/rest/patient-additional-fields/${field.id}`,
      JSON.stringify(payload),
      (response) => {
        if (response.status >= 200 && response.status < 300) {
          loadFields();
          showNotification(
            NotificationKinds.success,
            intl.formatMessage({ id: "patient.additional.fields.updated" }),
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
      active: field.active,
      defaultValue: field.defaultValue,
      maxLength: field.maxLength,
      sortOrder: normalizeSortOrder(field.sortOrder),
      metadataJson: field.metadataJson,
      options: field.options || [],
    };

    putToOpenElisServerFullResponse(
      `/rest/patient-additional-fields/${field.id}`,
      JSON.stringify(payload),
      (response) => {
        setSavingSortFieldId(null);
        if (response.status >= 200 && response.status < 300) {
          loadFields();
          showNotification(
            NotificationKinds.success,
            intl.formatMessage({ id: "patient.additional.fields.saved" }),
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

  const rows = useMemo(
    () =>
      (fields || []).map((field) => ({
        id: String(field.id),
        displayName: field.displayName,
        fieldKey: field.fieldKey,
        fieldType: getAdditionalFieldTypeLabel(intl, field.fieldType),
        sortOrder: field.sortOrder,
        required: field.required,
        active: field.active,
      })),
    [fields, intl],
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

  return (
    <>
      {notificationVisible ? <AlertDialog /> : null}
      <div className="adminPageContent">
        <PageBreadCrumb breadcrumbs={breadcrumbs} />
        <Grid fullWidth>
          <Column lg={16} md={8} sm={4}>
            <Section>
              <Heading>
                <FormattedMessage id="patient.additional.fields.title" />
              </Heading>
            </Section>
          </Column>
        </Grid>

        <div className="orderLegendBody">
          <Stack gap={6}>
            <Heading>
              <FormattedMessage id="patient.fixed.fields.title" />
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
                      <FormattedMessage id="order.additional.fields.sortOrder" />
                    </TableHeader>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {fixedRows.map((config) => {
                    const definition =
                      PATIENT_FIXED_FIELD_DEFINITION_MAP[config.fieldKey] || {};
                    return (
                      <TableRow key={config.fieldKey}>
                        <TableCell>
                          {getPatientFixedFieldLabel(intl, config.fieldKey)}
                        </TableCell>
                        <TableCell>
                          <Checkbox
                            id={`patient-fixed-visible-${config.fieldKey}`}
                            labelText=""
                            checked={config.visible !== false}
                            onChange={(_event, { checked }) =>
                              updateFixedConfig(config.fieldKey, "visible", checked)
                            }
                          />
                        </TableCell>
                        <TableCell>
                          <Checkbox
                            id={`patient-fixed-required-${config.fieldKey}`}
                            labelText=""
                            checked={!!config.required}
                            disabled={!definition.supportsRequired}
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
                            id={`patient-fixed-readonly-${config.fieldKey}`}
                            labelText=""
                            checked={!!config.readonly}
                            disabled={!definition.supportsReadonly}
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
                          <TextInput
                            id={`patient-fixed-sort-order-${config.fieldKey}`}
                            type="number"
                            labelText=""
                            hideLabel
                            value={String(config.sortOrder ?? "")}
                            onChange={(event) =>
                              updateFixedConfig(
                                config.fieldKey,
                                "sortOrder",
                                event.target.value === ""
                                  ? ""
                                  : Number.parseInt(event.target.value, 10),
                              )
                            }
                          />
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
            <Button onClick={saveFixedConfigs} disabled={savingFixed}>
              <FormattedMessage id="button.save" />
            </Button>

            <Heading>
              <FormattedMessage id="patient.additional.fields.custom.title" />
            </Heading>
            <Grid fullWidth>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="patient-additional-display-name"
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
                  id="patient-additional-field-key"
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
                  id="patient-additional-field-type"
                  labelText={intl.formatMessage({
                    id: "order.additional.fields.fieldType",
                  })}
                  value={newField.fieldType}
                  disabled={structureLocked}
                  onChange={(event) =>
                    setNewField((previous) => ({
                      ...previous,
                      fieldType: event.target.value,
                      userDisplayMode:
                        event.target.value === "USER"
                          ? previous.userDisplayMode ||
                            USER_DISPLAY_MODE_BOTH
                          : USER_DISPLAY_MODE_BOTH,
                    }))
                  }
                >
                  {FIELD_TYPE_OPTIONS.filter(
                    (fieldType) => fieldType.value !== "DOCUMENT",
                  ).map((fieldType) => (
                    <SelectItem
                      key={fieldType.value}
                      value={fieldType.value}
                      text={intl.formatMessage({
                        id: fieldType.labelId,
                        defaultMessage: fieldType.defaultMessage,
                      })}
                    />
                  ))}
                </Select>
              </Column>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="patient-additional-default-value"
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
              {newField.fieldType === "USER" ? (
                <>
                  <Column lg={8} md={4} sm={4}>
                    <div style={{ marginTop: "1rem" }}>
                      <label
                        htmlFor="patient-additional-user-profiles"
                        style={{ display: "block", marginBottom: "0.5rem" }}
                      >
                        {intl.formatMessage({
                          id: "patient.additional.fields.userProfiles",
                          defaultMessage: "Allowed professional profiles",
                        })}
                      </label>
                      <MultiSelect
                        id="patient-additional-user-profiles"
                        items={professionalProfileOptions}
                        itemToString={(item) => item?.label || ""}
                        selectedItems={professionalProfileOptions.filter(
                          (item) =>
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
                  <Column lg={8} md={4} sm={4}>
                    <Select
                      id="patientAdditionalUserDisplayMode"
                      labelText={intl.formatMessage({
                        id: "user.field.display.mode.label",
                        defaultMessage: "Display user as",
                      })}
                      value={
                        newField.userDisplayMode || USER_DISPLAY_MODE_BOTH
                      }
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
                </>
              ) : null}
            </Grid>
            <Grid fullWidth>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="patient-additional-max-length"
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
                  id="patient-additional-sort-order"
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
                  id="patient-additional-required"
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
            </Grid>

            {OPTION_FIELD_TYPES.has(newField.fieldType) ? (
              <AdditionalFieldOptionsEditor
                idPrefix="patient-additional"
                options={newField.options || []}
                locked={structureLocked}
                onAddOption={addOption}
                onOptionLabelChange={updateOptionLabel}
                onRemoveOption={removeOption}
              />
            ) : null}

            <Stack orientation="horizontal" gap={4}>
              <Button
                onClick={saveField}
                disabled={savingField}
                data-cy="create-patient-additional-field"
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
              rows={rows}
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
                  key: "active",
                  header: intl.formatMessage({
                    id: "order.additional.fields.active",
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
                                id={`patient-custom-field-sort-order-${row.id}`}
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
                              {row.cells[5].value ? (
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
                                  row.cells[5].value ? "danger" : "secondary"
                                }
                                size="sm"
                                onClick={() => toggleFieldStatus(sourceField)}
                              >
                                {row.cells[5].value ? (
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
      </div>
    </>
  );
};

export default PatientAdditionalFieldsManagement;
