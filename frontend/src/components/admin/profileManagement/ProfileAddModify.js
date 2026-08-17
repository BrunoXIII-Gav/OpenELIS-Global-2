import React, { useContext, useEffect, useMemo, useState } from "react";
import {
  Button,
  Checkbox,
  Column,
  Form,
  Grid,
  Heading,
  Loading,
  Section,
  Select,
  SelectItem,
  Tag,
  TextInput,
} from "@carbon/react";
import { useHistory, useLocation } from "react-router-dom";
import { FormattedMessage, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import { NotificationContext } from "../../layout/Layout.js";
import { NotificationKinds } from "../../common/CustomNotification.js";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils.js";

const FIELD_TYPE_OPTIONS = [
  { value: "TEXT", labelId: "professionalProfile.fieldTypes.text", defaultMessage: "Text" },
  { value: "NUMBER", labelId: "professionalProfile.fieldTypes.number", defaultMessage: "Number" },
  { value: "DATE", labelId: "professionalProfile.fieldTypes.date", defaultMessage: "Date" },
  { value: "SELECT", labelId: "professionalProfile.fieldTypes.select", defaultMessage: "Select" },
  {
    value: "MULTISELECT",
    labelId: "professionalProfile.fieldTypes.multiselect",
    defaultMessage: "Multiple select",
  },
  { value: "BOOLEAN", labelId: "professionalProfile.fieldTypes.boolean", defaultMessage: "Checkbox" },
];

const toCodeCandidate = (value = "") =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .replace(/_+/g, "_");

const createEmptyField = (sortOrder = 0) => ({
  fieldKey: "",
  displayName: "",
  fieldType: "TEXT",
  required: false,
  active: true,
  sortOrder,
  optionsText: "",
  options: [],
  legacyBinding: "",
  systemField: false,
});

const optionsToText = (options = []) =>
  options
    .filter((option) => option?.optionKey || option?.optionLabel)
    .map((option) =>
      option.optionKey && option.optionLabel && option.optionKey !== option.optionLabel
        ? `${option.optionKey}|${option.optionLabel}`
        : option.optionLabel || option.optionKey,
    )
    .join(", ");

const textToOptions = (rawText = "") =>
  rawText
    .split(",")
    .map((token) => token.trim())
    .filter(Boolean)
    .map((token, index) => {
      const [optionKeyRaw, optionLabelRaw] = token.split("|");
      const optionKey = (optionKeyRaw || "").trim();
      const optionLabel = (optionLabelRaw || optionKeyRaw || "").trim();
      return {
        optionKey,
        optionLabel,
        active: true,
        sortOrder: index,
      };
    })
    .filter((option) => option.optionKey && option.optionLabel);

const normalizeFieldForUi = (field = {}, index = 0) => ({
  fieldKey: field.fieldKey || "",
  displayName: field.displayName || "",
  fieldType: field.fieldType || "TEXT",
  required: !!field.required,
  active: field.active !== false,
  sortOrder: field.sortOrder ?? index,
  optionsText: optionsToText(field.options || []),
  options: field.options || [],
  legacyBinding: field.legacyBinding || "",
  systemField: !!field.systemField,
});

function ProfileAddModify() {
  const history = useHistory();
  const location = useLocation();
  const intl = useIntl();
  const { addNotification, setNotificationVisible } = useContext(NotificationContext);
  const [loading, setLoading] = useState(true);
  const [newSpecialtyOptionKey, setNewSpecialtyOptionKey] = useState("");
  const [newSpecialtyOptionLabel, setNewSpecialtyOptionLabel] = useState("");
  const [formData, setFormData] = useState({
    code: "",
    name: "",
    specialtyOptionsText: "",
    fields: [],
  });

  const profileCode = useMemo(() => {
    const search = new URLSearchParams(location.search);
    return search.get("code") || "new";
  }, [location.search]);

  const isNew = profileCode === "new";

  const specialtyOptions = useMemo(
    () => textToOptions(formData.specialtyOptionsText),
    [formData.specialtyOptionsText],
  );

  const breadcrumbs = useMemo(
    () => [
      { label: "home.label", link: "/" },
      { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
      {
        label: "professionalProfile.page.title",
        link: "/MasterListsPage/profileManagement",
      },
      {
        label: isNew ? "professionalProfile.add.title" : "professionalProfile.edit.title",
        link: `/MasterListsPage/profileEdit?code=${profileCode}`,
      },
    ],
    [isNew, profileCode],
  );

  useEffect(() => {
    if (isNew) {
      setFormData({ code: "", name: "", specialtyOptionsText: "", fields: [] });
      setLoading(false);
      return;
    }

    getFromOpenElisServer(`/rest/professional-profiles/${encodeURIComponent(profileCode)}`, (response) => {
      setFormData({
        code: response?.code || profileCode,
        name: response?.name || "",
        specialtyOptionsText: optionsToText(response?.specialtyOptions || []),
        fields: Array.isArray(response?.fields)
          ? response.fields.map((field, index) => normalizeFieldForUi(field, index))
          : [],
      });
      setLoading(false);
    });
  }, [isNew, profileCode]);

  const showError = async (response) => {
    let message = intl.formatMessage({ id: "server.error.msg" });
    if (response?.message) {
      message = response.message;
    } else if (response && typeof response.json === "function") {
      try {
        const payload = await response.json();
        message = payload?.message || message;
      } catch (_error) {
        // ignore parse failure
      }
    }
    addNotification({
      kind: NotificationKinds.error,
      title: intl.formatMessage({ id: "notification.title" }),
      message,
    });
    setNotificationVisible(true);
    setLoading(false);
  };

  const handleSuccess = () => {
    addNotification({
      kind: NotificationKinds.success,
      title: intl.formatMessage({ id: "notification.title" }),
      message: intl.formatMessage({
        id: "professionalProfile.save.success",
        defaultMessage: "Professional profile saved successfully",
      }),
    });
    setNotificationVisible(true);
    history.push("/MasterListsPage/profileManagement");
  };

  const buildPayload = () =>
    JSON.stringify({
      code: formData.code,
      name: formData.name,
      specialtyOptions: textToOptions(formData.specialtyOptionsText),
      fields: formData.fields.map((field, index) => ({
        fieldKey: field.fieldKey,
        displayName: field.displayName,
        fieldType: field.fieldType,
        required: !!field.required,
        active: field.active !== false,
        sortOrder: index,
        legacyBinding: field.legacyBinding || "",
        systemField: !!field.systemField,
        options: textToOptions(field.optionsText),
      })),
    });

  const handleSave = () => {
    setLoading(true);
    const payload = buildPayload();

    if (isNew) {
      postToOpenElisServerJsonResponse("/rest/professional-profiles", payload, (response) => {
        if (response?.code) {
          handleSuccess();
          return;
        }
        showError(response);
      });
      return;
    }

    putToOpenElisServerFullResponse(
      `/rest/professional-profiles/${encodeURIComponent(profileCode)}`,
      payload,
      async (response) => {
        if (response.ok) {
          handleSuccess();
          return;
        }
        showError(response);
      },
    );
  };

  const updateField = (index, updater) => {
    setFormData((previousFormData) => ({
      ...previousFormData,
      fields: previousFormData.fields.map((field, fieldIndex) =>
        fieldIndex === index ? updater(field) : field,
      ),
    }));
  };

  const addField = () => {
    setFormData((previousFormData) => ({
      ...previousFormData,
      fields: [...previousFormData.fields, createEmptyField(previousFormData.fields.length)],
    }));
  };

  const removeField = (index) => {
    setFormData((previousFormData) => ({
      ...previousFormData,
      fields: previousFormData.fields.filter((_, fieldIndex) => fieldIndex !== index),
    }));
  };

  const addSpecialtyOption = () => {
    const optionKey = (newSpecialtyOptionKey || "").trim();
    const optionLabel = (newSpecialtyOptionLabel || "").trim();

    if (!optionKey || !optionLabel) {
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "professionalProfile.specialty.options.add.invalid",
          defaultMessage: "Both specialty option code and label are required.",
        }),
      });
      setNotificationVisible(true);
      return;
    }

    const normalizedKey = toCodeCandidate(optionKey);
    const alreadyExists = specialtyOptions.some(
      (option) => option.optionKey === normalizedKey,
    );

    if (alreadyExists) {
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "professionalProfile.specialty.options.add.duplicate",
          defaultMessage: "That specialty option code already exists.",
        }),
      });
      setNotificationVisible(true);
      return;
    }

    setFormData((previousFormData) => ({
      ...previousFormData,
      specialtyOptionsText: optionsToText([
        ...specialtyOptions,
        {
          optionKey: normalizedKey,
          optionLabel,
          active: true,
          sortOrder: specialtyOptions.length,
        },
      ]),
    }));
    setNewSpecialtyOptionKey("");
    setNewSpecialtyOptionLabel("");
  };

  const removeSpecialtyOption = (optionKeyToRemove) => {
    setFormData((previousFormData) => ({
      ...previousFormData,
      specialtyOptionsText: optionsToText(
        textToOptions(previousFormData.specialtyOptionsText).filter(
          (option) => option.optionKey !== optionKeyToRemove,
        ),
      ),
    }));
  };

  const shouldShowOptions = (fieldType) => fieldType === "SELECT" || fieldType === "MULTISELECT";

  if (loading) {
    return <Loading />;
  }

  return (
    <div className="adminPageContent">
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Grid fullWidth>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading>
              {isNew ? (
                <FormattedMessage
                  id="professionalProfile.add.title"
                  defaultMessage="Create Professional Profile"
                />
              ) : (
                <FormattedMessage
                  id="professionalProfile.edit.title"
                  defaultMessage="Edit Professional Profile"
                />
              )}
            </Heading>
          </Section>
          <br />
          <Form>
            <Grid fullWidth>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="professional-profile-name"
                  labelText={intl.formatMessage({
                    id: "professionalProfile.fields.name",
                    defaultMessage: "Profile name",
                  })}
                  value={formData.name}
                  onChange={(event) => {
                    const name = event.target.value;
                    setFormData((previousFormData) => ({
                      ...previousFormData,
                      name,
                      code:
                        isNew &&
                        (!previousFormData.code ||
                          previousFormData.code === toCodeCandidate(previousFormData.name))
                          ? toCodeCandidate(name)
                          : previousFormData.code,
                    }));
                  }}
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="professional-profile-code"
                  labelText={intl.formatMessage({
                    id: "professionalProfile.fields.code",
                    defaultMessage: "Profile code",
                  })}
                  value={formData.code}
                  onChange={(event) =>
                    setFormData((previousFormData) => ({
                      ...previousFormData,
                      code: toCodeCandidate(event.target.value),
                    }))
                  }
                  readOnly={!isNew}
                />
              </Column>
            </Grid>

            <br />
            <Section>
              <Heading>
                <FormattedMessage
                  id="professionalProfile.specialty.sectionTitle"
                  defaultMessage="Fixed specialty options"
                />
              </Heading>
            </Section>
            <br />

            <Grid fullWidth>
              <Column lg={8} md={8} sm={4}>
                <div
                  style={{
                    border: "1px solid #e0e0e0",
                    padding: "1rem",
                    minHeight: "100%",
                  }}
                >
                  <p style={{ marginTop: 0, marginBottom: "1rem", fontWeight: 600 }}>
                    {intl.formatMessage({
                      id: "professionalProfile.specialty.options.formTitle",
                      defaultMessage: "Add a new specialty option",
                    })}
                  </p>
                  <TextInput
                    id="professional-profile-specialty-option-key"
                    labelText={intl.formatMessage({
                      id: "professionalProfile.specialty.options.newKey",
                      defaultMessage: "New specialty code",
                    })}
                    placeholder={intl.formatMessage({
                      id: "professionalProfile.specialty.options.newKey.placeholder",
                      defaultMessage: "Example: GENETICA",
                    })}
                    value={newSpecialtyOptionKey}
                    onChange={(event) => setNewSpecialtyOptionKey(event.target.value)}
                  />
                  <br />
                  <TextInput
                    id="professional-profile-specialty-option-label"
                    labelText={intl.formatMessage({
                      id: "professionalProfile.specialty.options.newLabel",
                      defaultMessage: "New specialty label",
                    })}
                    placeholder={intl.formatMessage({
                      id: "professionalProfile.specialty.options.newLabel.placeholder",
                      defaultMessage: "Example: Genética",
                    })}
                    onChange={(event) => setNewSpecialtyOptionLabel(event.target.value)}
                    value={newSpecialtyOptionLabel}
                  />
                  <br />
                  <Button type="button" kind="secondary" onClick={addSpecialtyOption}>
                    <FormattedMessage
                      id="professionalProfile.specialty.options.add"
                      defaultMessage="Add specialty option"
                    />
                  </Button>
                </div>
              </Column>
              <Column lg={8} md={8} sm={4}>
                <div
                  style={{
                    border: "1px solid #e0e0e0",
                    minHeight: "10rem",
                    padding: "1rem",
                  }}
                >
                  <p style={{ marginTop: 0, marginBottom: "1rem", fontWeight: 600 }}>
                    {intl.formatMessage({
                      id: "professionalProfile.specialty.options.current",
                      defaultMessage: "Current specialty options",
                    })}
                  </p>
                  {specialtyOptions.length === 0 ? (
                    <p style={{ margin: 0 }}>
                      {intl.formatMessage({
                        id: "professionalProfile.specialty.options.empty",
                        defaultMessage: "No specialty options added yet.",
                      })}
                    </p>
                  ) : (
                    specialtyOptions.map((option) => (
                      <div
                        key={`specialty-option-${option.optionKey}`}
                        style={{
                          alignItems: "center",
                          backgroundColor: "#f4f4f4",
                          borderRadius: "0.25rem",
                          display: "flex",
                          justifyContent: "space-between",
                          marginBottom: "0.75rem",
                          gap: "1rem",
                          padding: "0.75rem",
                        }}
                      >
                        <div>
                          <div style={{ fontWeight: 600 }}>{option.optionLabel}</div>
                          <div style={{ marginTop: "0.35rem" }}>
                            <Tag type="gray">{option.optionKey}</Tag>
                          </div>
                        </div>
                        <Button
                          type="button"
                          kind="ghost"
                          size="sm"
                          onClick={() => removeSpecialtyOption(option.optionKey)}
                        >
                          <FormattedMessage
                            id="professionalProfile.specialty.options.remove"
                            defaultMessage="Remove"
                          />
                        </Button>
                      </div>
                    ))
                  )}
                </div>
                <p style={{ marginTop: "0.75rem", marginBottom: 0 }}>
                  {intl.formatMessage({
                    id: "professionalProfile.specialty.options.helper",
                    defaultMessage:
                      "These options are used only by the fixed Specialty field for this professional profile.",
                  })}
                </p>
              </Column>
            </Grid>

            <br />
            <Section>
              <Heading>
                <FormattedMessage
                  id="professionalProfile.fields.sectionTitle"
                  defaultMessage="Profile fields"
                />
              </Heading>
            </Section>
            <br />

            {formData.fields.map((field, index) => (
              <div
                key={`professional-profile-field-${index}`}
                style={{
                  marginBottom: "1rem",
                  padding: "1rem",
                  border: "1px solid #e0e0e0",
                }}
              >
                <Grid fullWidth>
                  <Column lg={4} md={4} sm={4}>
                    <TextInput
                      id={`field-display-name-${index}`}
                      labelText={intl.formatMessage({
                        id: "professionalProfile.fields.fieldDisplayName",
                        defaultMessage: "Field label",
                      })}
                      value={field.displayName}
                      onChange={(event) =>
                        updateField(index, (currentField) => ({
                          ...currentField,
                          displayName: event.target.value,
                          fieldKey:
                            !currentField.systemField &&
                            (!currentField.fieldKey ||
                              currentField.fieldKey === toCodeCandidate(currentField.displayName))
                              ? toCodeCandidate(event.target.value)
                              : currentField.fieldKey,
                        }))
                      }
                    />
                  </Column>
                  <Column lg={4} md={4} sm={4}>
                    <TextInput
                      id={`field-key-${index}`}
                      labelText={intl.formatMessage({
                        id: "professionalProfile.fields.fieldKey",
                        defaultMessage: "Field key",
                      })}
                      value={field.fieldKey}
                      readOnly={field.systemField}
                      onChange={(event) =>
                        updateField(index, (currentField) => ({
                          ...currentField,
                          fieldKey: toCodeCandidate(event.target.value),
                        }))
                      }
                    />
                  </Column>
                  <Column lg={4} md={4} sm={4}>
                    <Select
                      id={`field-type-${index}`}
                      labelText={intl.formatMessage({
                        id: "professionalProfile.fields.fieldType",
                        defaultMessage: "Field type",
                      })}
                      value={field.fieldType}
                      disabled={field.systemField}
                      onChange={(event) =>
                        updateField(index, (currentField) => ({
                          ...currentField,
                          fieldType: event.target.value,
                          optionsText:
                            event.target.value === "SELECT" || event.target.value === "MULTISELECT"
                              ? currentField.optionsText
                              : "",
                        }))
                      }
                    >
                      {FIELD_TYPE_OPTIONS.map((option) => (
                        <SelectItem
                          key={`field-type-option-${option.value}`}
                          value={option.value}
                          text={intl.formatMessage({
                            id: option.labelId,
                            defaultMessage: option.defaultMessage,
                          })}
                        />
                      ))}
                    </Select>
                  </Column>
                  <Column lg={4} md={4} sm={4}>
                    <TextInput
                      id={`field-options-${index}`}
                      labelText={intl.formatMessage({
                        id: "professionalProfile.fields.options",
                        defaultMessage: "Options",
                      })}
                      placeholder={intl.formatMessage({
                        id: "professionalProfile.fields.options.placeholder",
                        defaultMessage: "Example: A|Option A, B|Option B",
                      })}
                      value={field.optionsText}
                      disabled={!shouldShowOptions(field.fieldType)}
                      onChange={(event) =>
                        updateField(index, (currentField) => ({
                          ...currentField,
                          optionsText: event.target.value,
                        }))
                      }
                    />
                  </Column>
                </Grid>

                <br />
                <Grid fullWidth>
                  <Column lg={3} md={4} sm={4}>
                    <Checkbox
                      id={`field-required-${index}`}
                      labelText={intl.formatMessage({
                        id: "professionalProfile.fields.required",
                        defaultMessage: "Required",
                      })}
                      checked={!!field.required}
                      onChange={(_event, { checked }) =>
                        updateField(index, (currentField) => ({
                          ...currentField,
                          required: !!checked,
                        }))
                      }
                    />
                  </Column>
                  <Column lg={3} md={4} sm={4}>
                    <Checkbox
                      id={`field-active-${index}`}
                      labelText={intl.formatMessage({
                        id: "professionalProfile.fields.active",
                        defaultMessage: "Active",
                      })}
                      checked={field.active !== false}
                      onChange={(_event, { checked }) =>
                        updateField(index, (currentField) => ({
                          ...currentField,
                          active: !!checked,
                        }))
                      }
                    />
                  </Column>
                  <Column lg={5} md={4} sm={4}>
                    {!field.systemField ? (
                      <Button
                        type="button"
                        kind="danger--tertiary"
                        onClick={() => removeField(index)}
                      >
                        <FormattedMessage
                          id="professionalProfile.fields.remove"
                          defaultMessage="Remove field"
                        />
                      </Button>
                    ) : null}
                  </Column>
                </Grid>
              </div>
            ))}

            <Button type="button" kind="ghost" onClick={addField}>
              <FormattedMessage
                id="professionalProfile.fields.add"
                defaultMessage="Add field"
              />
            </Button>

            <br />
            <br />

            <Button type="button" disabled={!formData.name.trim()} onClick={handleSave}>
              <FormattedMessage id="label.button.save" />
            </Button>
            <Button
              type="button"
              kind="tertiary"
              onClick={() => history.push("/MasterListsPage/profileManagement")}
            >
              <FormattedMessage id="label.button.exit" />
            </Button>
          </Form>
        </Column>
      </Grid>
    </div>
  );
}

export default ProfileAddModify;
