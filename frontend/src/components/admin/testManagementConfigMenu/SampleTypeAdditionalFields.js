import React, { useContext, useEffect, useRef, useState } from "react";
import {
  Button,
  Column,
  Grid,
  Heading,
  Loading,
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
  TextInput,
  TextArea,
  Tag,
} from "@carbon/react";
import {
  deleteFromOpenElisServer,
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
  putToOpenElisServer,
} from "../../utils/Utils";
import { FormattedMessage, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb";
import { useLocation } from "react-router-dom";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification";
import { NotificationContext } from "../../layout/Layout";

const buildBreadcrumbs = (fromSampleEntryConfig) => [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  fromSampleEntryConfig
    ? {
        label: "sidenav.label.admin.formEntry.sampleEntryconfig",
        link: "/MasterListsPage/SampleEntryConfigurationMenu",
      }
    : {
        label: "master.lists.page.test.management",
        link: "/MasterListsPage/testManagementConfigMenu",
      },
  {
    label: "configuration.sampleType.additional.fields",
    link: "/MasterListsPage/SampleTypeAdditionalFields",
  },
];

const OPTION_BASED_TYPES = new Set(["SELECT", "RADIO", "MULTISELECT"]);
const LEGACY_USER_FIELD_TYPE = "SYSTEM_USER_BIOLOGIST_SELECT";
const GENERIC_USER_FIELD_TYPE = "USER";
const USER_DISPLAY_MODE_INITIALS = "INITIALS";
const USER_DISPLAY_MODE_NAME = "NAME";
const USER_DISPLAY_MODE_BOTH = "BOTH";

const initialFormState = {
  fieldKey: "",
  displayName: "",
  fieldType: "TEXT",
  required: false,
  displaySection: "RECEPTION",
  sortOrder: "",
  defaultValue: "",
  maxLength: "",
  optionLines: "",
  userProfileCodes: [],
  userDisplayMode: USER_DISPLAY_MODE_BOTH,
};

const SampleTypeAdditionalFields = () => {
  const intl = useIntl();
  const location = useLocation();
  const componentMounted = useRef(false);
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [loading, setLoading] = useState(true);
  const [sampleTypes, setSampleTypes] = useState([]);
  const [professionalProfileOptions, setProfessionalProfileOptions] = useState([]);
  const [selectedSampleTypeId, setSelectedSampleTypeId] = useState("");
  const [fields, setFields] = useState([]);
  const [formState, setFormState] = useState(initialFormState);
  const [editingFieldId, setEditingFieldId] = useState(null);
  const fromSampleEntryConfig =
    new URLSearchParams(location.search).get("source") === "sampleEntryConfig";
  const breadcrumbs = buildBreadcrumbs(fromSampleEntryConfig);

  const optionsRequired = OPTION_BASED_TYPES.has(formState.fieldType);

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
    if (
      mode === USER_DISPLAY_MODE_INITIALS ||
      mode === USER_DISPLAY_MODE_NAME
    ) {
      return mode;
    }
    return USER_DISPLAY_MODE_BOTH;
  };

  const showNotification = (kind, message) => {
    addNotification({
      kind,
      title: intl.formatMessage({ id: "notification.title" }),
      message,
    });
    setNotificationVisible(true);
  };

  const fetchFields = (sampleTypeId) => {
    if (!sampleTypeId) {
      setFields([]);
      return;
    }

    getFromOpenElisServer(
      `/rest/sample-type-additional-fields?sampleTypeId=${sampleTypeId}&includeInactive=true`,
      (response) => {
        if (!componentMounted.current) {
          return;
        }
        if (Array.isArray(response)) {
          setFields(response);
          return;
        }
        setFields([]);
      },
    );
  };

  const parseOptionLines = (optionLines) => {
    return optionLines
      .split("\n")
      .map((line) => line.trim())
      .filter((line) => line !== "")
      .map((line, index) => {
        const [optionKeyPart, ...labelParts] = line.split("|");
        const hasExplicitKey = labelParts.length > 0;
        const optionLabel = hasExplicitKey
          ? labelParts.join("|").trim()
          : optionKeyPart.trim();
        const optionKey = hasExplicitKey ? optionKeyPart.trim() : "";

        return {
          optionKey,
          optionLabel,
          sortOrder: index + 1,
          active: true,
        };
      })
      .filter((option) => option.optionLabel !== "");
  };

  const toOptionLines = (options) =>
    (Array.isArray(options) ? options : [])
      .filter((option) => option?.active !== false)
      .sort((left, right) => (left?.sortOrder ?? 0) - (right?.sortOrder ?? 0))
      .map((option) =>
        option?.optionKey
          ? `${option.optionKey}|${option.optionLabel || option.optionKey}`
          : option?.optionLabel || "",
      )
      .filter(Boolean)
      .join("\n");

  const startEditingField = (field) => {
    if (!field?.id) {
      return;
    }

    setEditingFieldId(field.id);
    const metadata = parseFieldMetadata(field?.metadataJson);
    setFormState({
      fieldKey: field.fieldKey || "",
      displayName: field.displayName || "",
      fieldType: normalizeFieldType(field.fieldType),
      required: !!field.required,
      displaySection: field.displaySection || "RECEPTION",
      sortOrder:
        field.sortOrder !== null && field.sortOrder !== undefined
          ? String(field.sortOrder)
          : "",
      defaultValue: field.defaultValue || "",
      maxLength:
        field.maxLength !== null && field.maxLength !== undefined
          ? String(field.maxLength)
          : "",
      optionLines: toOptionLines(field.options),
      userProfileCodes: resolveUserProfileCodes(field.fieldType, metadata),
      userDisplayMode: resolveUserDisplayMode(metadata),
    });
  };

  const resetForm = () => {
    setEditingFieldId(null);
    setFormState(initialFormState);
  };

  const handleSaveField = (event) => {
    event.preventDefault();

    if (!selectedSampleTypeId) {
      showNotification(
        NotificationKinds.error,
        intl.formatMessage({
          id: "sample.additional.fields.sample.type.required",
        }),
      );
      return;
    }

    if (!formState.displayName.trim()) {
      showNotification(
        NotificationKinds.error,
        intl.formatMessage({
          id: "sample.additional.fields.display.name.required",
        }),
      );
      return;
    }

    const options = optionsRequired
      ? parseOptionLines(formState.optionLines)
      : [];

    if (optionsRequired && options.length === 0) {
      showNotification(
        NotificationKinds.error,
        intl.formatMessage({ id: "sample.additional.fields.options.required" }),
      );
      return;
    }

    const sourceField = fields.find((field) => field.id === editingFieldId);

    const payload = {
      sampleTypeId: selectedSampleTypeId,
      fieldKey: formState.fieldKey,
      displayName: formState.displayName,
      fieldType: formState.fieldType,
      required: formState.required,
      displaySection: formState.displaySection,
      active: sourceField ? sourceField.active : true,
      sortOrder:
        formState.sortOrder && formState.sortOrder !== ""
          ? Number(formState.sortOrder)
          : null,
      defaultValue: formState.defaultValue,
      maxLength:
        formState.maxLength && formState.maxLength !== ""
          ? Number(formState.maxLength)
          : null,
      metadataJson:
        formState.fieldType === "USER"
          ? JSON.stringify({
              userProfileCodes: formState.userProfileCodes || [],
              userDisplayMode:
                formState.userDisplayMode || USER_DISPLAY_MODE_BOTH,
            })
          : null,
      options,
    };

    if (sourceField && optionsRequired) {
      const existingOptionsByKey = new Map(
        (sourceField.options || []).map((option) => [option.optionKey, option]),
      );
      payload.options = payload.options.map((option) => {
        const existing = existingOptionsByKey.get(option.optionKey);
        return existing ? { ...option, id: existing.id } : option;
      });
    }

    const onSaveSuccess = () => {
      showNotification(
        NotificationKinds.success,
        intl.formatMessage({
          id: "sample.additional.fields.create.success",
        }),
      );
      resetForm();
      fetchFields(selectedSampleTypeId);
    };

    if (editingFieldId) {
      putToOpenElisServer(
        `/rest/sample-type-additional-fields/${editingFieldId}`,
        JSON.stringify(payload),
        (status) => {
          if (status >= 200 && status < 300) {
            onSaveSuccess();
            return;
          }

          showNotification(
            NotificationKinds.error,
            intl.formatMessage({
              id: "sample.additional.fields.create.error",
            }),
          );
        },
      );
      return;
    }

    postToOpenElisServerJsonResponse(
      "/rest/sample-type-additional-fields",
      JSON.stringify(payload),
      (response) => {
        if (response?.status && response.status >= 400) {
          showNotification(
            NotificationKinds.error,
            response.message ||
              intl.formatMessage({
                id: "sample.additional.fields.create.error",
              }),
          );
          return;
        }

        onSaveSuccess();
      },
    );
  };

  const handleDeactivateField = (fieldId) => {
    deleteFromOpenElisServer(
      `/rest/sample-type-additional-fields/${fieldId}`,
      (status) => {
        if (status >= 200 && status < 300) {
          showNotification(
            NotificationKinds.success,
            intl.formatMessage({
              id: "sample.additional.fields.deactivate.success",
            }),
          );
          fetchFields(selectedSampleTypeId);
          return;
        }

        showNotification(
          NotificationKinds.error,
          intl.formatMessage({
            id: "sample.additional.fields.deactivate.error",
          }),
        );
      },
    );
  };

  const reactivateFieldOptions = (options, onComplete) => {
    const inactiveOptions = (Array.isArray(options) ? options : []).filter(
      (option) => option?.id && !option.active,
    );

    if (inactiveOptions.length === 0) {
      onComplete(true);
      return;
    }

    let processed = 0;
    let allSucceeded = true;

    inactiveOptions.forEach((option) => {
      putToOpenElisServer(
        `/rest/sample-type-additional-fields/options/${option.id}`,
        JSON.stringify({ active: true }),
        (status) => {
          if (!(status >= 200 && status < 300)) {
            allSucceeded = false;
          }

          processed += 1;
          if (processed === inactiveOptions.length) {
            onComplete(allSucceeded);
          }
        },
      );
    });
  };

  const handleReactivateField = (field) => {
    putToOpenElisServer(
      `/rest/sample-type-additional-fields/${field.id}`,
      JSON.stringify({ active: true }),
      (status) => {
        if (!(status >= 200 && status < 300)) {
          showNotification(
            NotificationKinds.error,
            intl.formatMessage({ id: "error.save.msg" }),
          );
          return;
        }

        reactivateFieldOptions(field.options, (optionsReactivated) => {
          if (!optionsReactivated) {
            showNotification(
              NotificationKinds.error,
              intl.formatMessage({ id: "error.save.msg" }),
            );
            return;
          }

          showNotification(
            NotificationKinds.success,
            intl.formatMessage({ id: "catalog.item.activate.success" }),
          );
          fetchFields(selectedSampleTypeId);
        });
      },
    );
  };

  useEffect(() => {
    componentMounted.current = true;
    getFromOpenElisServer(
      "/rest/sample-type-additional-fields/sample-types",
      (response) => {
        if (!componentMounted.current) {
          return;
        }
        const fetchedSampleTypes = Array.isArray(response) ? response : [];
        setSampleTypes(fetchedSampleTypes);
        setLoading(false);
      },
    );
    getFromOpenElisServer("/rest/professional-profiles/catalog", (response) => {
      if (!componentMounted.current) {
        return;
      }
      setProfessionalProfileOptions(
        Array.isArray(response?.profiles)
          ? response.profiles.map((profile) => ({
              id: profile.code,
              label: profile.label || profile.name || profile.code,
            }))
          : [],
      );
    });

    return () => {
      componentMounted.current = false;
    };
  }, []);

  useEffect(() => {
    fetchFields(selectedSampleTypeId);
  }, [selectedSampleTypeId]);

  return (
    <>
      {notificationVisible ? <AlertDialog /> : ""}
      {loading ? (
        <Loading />
      ) : (
        <div className="adminPageContent">
          <PageBreadCrumb breadcrumbs={breadcrumbs} />
          <div className="orderLegendBody">
            <Grid fullWidth>
              <Column lg={16} md={8} sm={4}>
                <Section>
                  <Heading>
                    <FormattedMessage id="configuration.sampleType.additional.fields" />
                  </Heading>
                </Section>
              </Column>
            </Grid>

            <br />
            <Grid fullWidth>
              <Column lg={8} md={8} sm={4}>
                <Select
                  id="sampleTypeAdditionalFieldsSelector"
                  labelText={intl.formatMessage({ id: "sample.type.label" })}
                  value={selectedSampleTypeId}
                  disabled={editingFieldId !== null}
                  onChange={(event) =>
                    setSelectedSampleTypeId(event.target.value)
                  }
                >
                  <SelectItem
                    text={intl.formatMessage({
                      id: "sample.type.select.placeholder",
                    })}
                    value=""
                  />
                  {(Array.isArray(sampleTypes) ? sampleTypes : []).map(
                    (sampleType, index) => (
                      <SelectItem
                        key={`sample_type_option_${index}`}
                        value={sampleType.id}
                        text={sampleType.value}
                      />
                    ),
                  )}
                </Select>
              </Column>
            </Grid>

            <br />
            <hr />
            <br />

            <Grid fullWidth>
              <Column lg={16} md={8} sm={4}>
                <Stack gap={5}>
                  <Heading>
                    <FormattedMessage id="sample.additional.fields.new.field.title" />
                  </Heading>
                  <Grid fullWidth>
                    <Column lg={4} md={4} sm={4}>
                      <TextInput
                        id="sampleAdditionalFieldDisplayName"
                        labelText={intl.formatMessage({
                          id: "sample.additional.fields.display.name",
                        })}
                        value={formState.displayName}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            displayName: event.target.value,
                          }))
                        }
                      />
                    </Column>
                    <Column lg={4} md={4} sm={4}>
                      <TextInput
                        id="sampleAdditionalFieldKey"
                        labelText={intl.formatMessage({
                          id: "sample.additional.fields.field.key",
                        })}
                        value={formState.fieldKey}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            fieldKey: event.target.value,
                          }))
                        }
                      />
                    </Column>
                    <Column lg={4} md={4} sm={4}>
                      <Select
                        id="sampleAdditionalFieldType"
                        labelText={intl.formatMessage({
                          id: "sample.additional.fields.field.type",
                        })}
                        value={formState.fieldType}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            fieldType: event.target.value,
                            optionLines: OPTION_BASED_TYPES.has(
                              event.target.value,
                            )
                              ? previous.optionLines
                              : "",
                            userDisplayMode:
                              event.target.value === "USER"
                                ? previous.userDisplayMode ||
                                  USER_DISPLAY_MODE_BOTH
                                : USER_DISPLAY_MODE_BOTH,
                          }))
                        }
                      >
                        <SelectItem value="TEXT" text="TEXT" />
                        <SelectItem value="TEXTAREA" text="TEXTAREA" />
                        <SelectItem value="NUMBER" text="NUMBER" />
                        <SelectItem value="DATE" text="DATE" />
                        <SelectItem value="TIME" text="TIME" />
                        <SelectItem value="DATETIME" text="DATETIME" />
                        <SelectItem value="BOOLEAN" text="BOOLEAN" />
                        <SelectItem value="SELECT" text="SELECT" />
                        <SelectItem value="MULTISELECT" text="MULTISELECT" />
                        <SelectItem value="RADIO" text="RADIO" />
                        <SelectItem value="USER" text="USER" />
                      </Select>
                    </Column>
                    {formState.fieldType === "USER" ? (
                      <>
                        <Column lg={8} md={4} sm={4}>
                          <div style={{ marginTop: "1rem" }}>
                            <label
                              htmlFor="sample-additional-user-profiles"
                              style={{
                                display: "block",
                                marginBottom: "0.5rem",
                              }}
                            >
                              {intl.formatMessage({
                                id: "sample.additional.fields.userProfiles",
                                defaultMessage: "Allowed professional profiles",
                              })}
                            </label>
                            <MultiSelect
                              id="sample-additional-user-profiles"
                              items={professionalProfileOptions}
                              itemToString={(item) => item?.label || ""}
                              selectedItems={professionalProfileOptions.filter(
                                (item) =>
                                  (formState.userProfileCodes || []).includes(
                                    item.id,
                                  ),
                              )}
                              onChange={({ selectedItems }) =>
                                setFormState((previous) => ({
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
                            id="sampleAdditionalUserDisplayMode"
                            labelText={intl.formatMessage({
                              id: "user.field.display.mode.label",
                              defaultMessage: "Display user as",
                            })}
                            value={
                              formState.userDisplayMode ||
                              USER_DISPLAY_MODE_BOTH
                            }
                            onChange={(event) =>
                              setFormState((previous) => ({
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
                    <Column lg={4} md={4} sm={4}>
                      <Select
                        id="sampleAdditionalFieldRequired"
                        labelText={intl.formatMessage({
                          id: "sample.additional.fields.required",
                        })}
                        value={formState.required ? "true" : "false"}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            required: event.target.value === "true",
                          }))
                        }
                      >
                        <SelectItem
                          value="false"
                          text={intl.formatMessage({ id: "label.no" })}
                        />
                        <SelectItem
                          value="true"
                          text={intl.formatMessage({ id: "label.yes" })}
                        />
                      </Select>
                    </Column>
                    <Column lg={4} md={4} sm={4}>
                      <Select
                        id="sampleAdditionalFieldDisplaySection"
                        labelText={intl.formatMessage({
                          id: "sample.additional.fields.display.section",
                          defaultMessage: "Mostrar en",
                        })}
                        value={formState.displaySection}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            displaySection: event.target.value,
                          }))
                        }
                      >
                        <SelectItem
                          value="COLLECTION"
                          text={intl.formatMessage({
                            id: "sample.additional.fields.display.section.collection",
                            defaultMessage:
                              "Datos de recolección de la muestra",
                          })}
                        />
                        <SelectItem
                          value="RECEPTION"
                          text={intl.formatMessage({
                            id: "sample.additional.fields.display.section.reception",
                            defaultMessage: "Recepción de la muestra",
                          })}
                        />
                      </Select>
                    </Column>
                  </Grid>

                  <Grid fullWidth>
                    <Column lg={4} md={4} sm={4}>
                      <TextInput
                        id="sampleAdditionalFieldSortOrder"
                        type="number"
                        min="0"
                        labelText={intl.formatMessage({
                          id: "order.additional.fields.sortOrder",
                        })}
                        value={formState.sortOrder}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            sortOrder: event.target.value,
                          }))
                        }
                      />
                    </Column>
                    <Column lg={4} md={4} sm={4}>
                      <TextInput
                        id="sampleAdditionalFieldDefaultValue"
                        labelText={intl.formatMessage({
                          id: "sample.additional.fields.default.value",
                        })}
                        value={formState.defaultValue}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            defaultValue: event.target.value,
                          }))
                        }
                      />
                    </Column>
                    <Column lg={4} md={4} sm={4}>
                      <TextInput
                        id="sampleAdditionalFieldMaxLength"
                        type="number"
                        min="0"
                        labelText={intl.formatMessage({
                          id: "sample.additional.fields.max.length",
                        })}
                        value={formState.maxLength}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            maxLength: event.target.value,
                          }))
                        }
                      />
                    </Column>
                    <Column lg={8} md={8} sm={4}>
                      <TextArea
                        id="sampleAdditionalFieldOptions"
                        rows={4}
                        labelText={intl.formatMessage({
                          id: "sample.additional.fields.options",
                        })}
                        helperText={intl.formatMessage({
                          id: "sample.additional.fields.options.helper",
                        })}
                        disabled={!optionsRequired}
                        value={formState.optionLines}
                        onChange={(event) =>
                          setFormState((previous) => ({
                            ...previous,
                            optionLines: event.target.value,
                          }))
                        }
                      />
                    </Column>
                  </Grid>

                  <div>
                    <Button
                      onClick={handleSaveField}
                      disabled={!selectedSampleTypeId}
                    >
                      {editingFieldId ? (
                        <FormattedMessage id="button.save" />
                      ) : (
                        <FormattedMessage id="sample.additional.fields.create.action" />
                      )}
                    </Button>
                    {editingFieldId ? (
                      <Button kind="ghost" onClick={resetForm}>
                        <FormattedMessage id="button.cancel" />
                      </Button>
                    ) : null}
                  </div>
                </Stack>
              </Column>
            </Grid>

            <br />
            <hr />
            <br />

            <Grid fullWidth>
              <Column lg={16} md={8} sm={4}>
                <TableContainer
                  title={intl.formatMessage({
                    id: "sample.additional.fields.list.title",
                  })}
                >
                  <Table>
                    <TableHead>
                      <TableRow>
                        <TableHeader>
                          <FormattedMessage id="sample.additional.fields.display.name" />
                        </TableHeader>
                        <TableHeader>
                          <FormattedMessage id="sample.additional.fields.field.key" />
                        </TableHeader>
                        <TableHeader>
                          <FormattedMessage id="sample.additional.fields.field.type" />
                        </TableHeader>
                        <TableHeader>
                          <FormattedMessage id="sample.additional.fields.required" />
                        </TableHeader>
                        <TableHeader>
                          <FormattedMessage
                            id="sample.additional.fields.display.section"
                            defaultMessage="Mostrar en"
                          />
                        </TableHeader>
                        <TableHeader>
                          <FormattedMessage id="order.additional.fields.sortOrder" />
                        </TableHeader>
                        <TableHeader>
                          <FormattedMessage id="label.status" />
                        </TableHeader>
                        <TableHeader>
                          <FormattedMessage id="sample.additional.fields.options" />
                        </TableHeader>
                        <TableHeader>
                          <FormattedMessage id="label.action" />
                        </TableHeader>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {fields.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={9}>
                            <FormattedMessage id="sample.additional.fields.list.empty" />
                          </TableCell>
                        </TableRow>
                      ) : (
                        (Array.isArray(fields) ? fields : []).map((field) => (
                          <TableRow key={`sample_additional_field_${field.id}`}>
                            <TableCell>{field.displayName}</TableCell>
                            <TableCell>{field.fieldKey}</TableCell>
                            <TableCell>{field.fieldType}</TableCell>
                            <TableCell>
                              {field.required ? (
                                <FormattedMessage id="label.yes" />
                              ) : (
                                <FormattedMessage id="label.no" />
                              )}
                            </TableCell>
                            <TableCell>
                              {field.displaySection === "COLLECTION" ? (
                                <FormattedMessage
                                  id="sample.additional.fields.display.section.collection"
                                  defaultMessage="Datos de recolección de la muestra"
                                />
                              ) : (
                                <FormattedMessage
                                  id="sample.additional.fields.display.section.reception"
                                  defaultMessage="Recepción de la muestra"
                                />
                              )}
                            </TableCell>
                            <TableCell>{field.sortOrder ?? 0}</TableCell>
                            <TableCell>
                              <Tag type={field.active ? "green" : "gray"}>
                                {field.active
                                  ? intl.formatMessage({ id: "label.active" })
                                  : intl.formatMessage({
                                      id: "label.inactive",
                                    })}
                              </Tag>
                            </TableCell>
                            <TableCell>
                              {(field.options || []).map(
                                (option, optionIndex) => (
                                  <Tag
                                    key={`sample_additional_field_option_${field.id}_${optionIndex}`}
                                    type="cool-gray"
                                    style={{ marginRight: "0.25rem" }}
                                  >
                                    {option.optionLabel}
                                  </Tag>
                                ),
                              )}
                            </TableCell>
                            <TableCell>
                              <Button
                                kind="ghost"
                                size="sm"
                                onClick={() => startEditingField(field)}
                              >
                                <FormattedMessage id="button.edit" />
                              </Button>
                              {"  "}
                              {field.active ? (
                                <Button
                                  kind="danger--tertiary"
                                  size="sm"
                                  onClick={() =>
                                    handleDeactivateField(field.id)
                                  }
                                >
                                  <FormattedMessage id="label.disable" />
                                </Button>
                              ) : (
                                <Button
                                  kind="tertiary"
                                  size="sm"
                                  onClick={() => handleReactivateField(field)}
                                >
                                  <FormattedMessage id="button.activate" />
                                </Button>
                              )}
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </TableContainer>
              </Column>
            </Grid>
          </div>
        </div>
      )}
    </>
  );
};

export default SampleTypeAdditionalFields;
