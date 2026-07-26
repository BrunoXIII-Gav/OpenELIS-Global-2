import React, { useEffect, useMemo, useState } from "react";
import {
  Button,
  Checkbox,
  InlineNotification,
  Loading,
  MultiSelect,
  Select,
  SelectItem,
  Tag,
  TextArea,
  TextInput,
  Tile,
} from "@carbon/react";
import { Add, Edit, Save } from "@carbon/icons-react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  getAllTests,
  getValidationFieldOptions,
  getValidationTemplateOverrides,
  parseValidationTemplateFile,
  saveValidationTemplateOverride,
  uploadValidationTemplateImage,
} from "../../services/reportTemplateOverrideService";
import "./ValidationTemplateOverrideConfig.scss";

const DYNAMIC_TEMPLATE_MODE = "jasper_dynamic";
const LEGACY_TEMPLATE_MODE = "legacy";
const DYNAMIC_TEMPLATE_REPORT = "dynamicJasperValidation";

const defaultImageOptions = [
  { id: "headerLeftImage", label: "Site Header Left Logo" },
  { id: "headerRightImage", label: "Site Header Right Logo" },
  { id: "labDirectorSignature", label: "Lab Director Signature" },
];

const emptyForm = {
  id: "",
  name: "",
  description: "",
  report: DYNAMIC_TEMPLATE_REPORT,
  isActive: true,
  testIds: [],
  testCodesText: "",
  mode: DYNAMIC_TEMPLATE_MODE,
  templateName: "",
  templateContent: "",
  parameterDefinitions: [],
  parameterMappings: {},
  warningMessages: [],
  fieldCount: 0,
  rawConfig: {},
};

const csvToArray = (raw) =>
  (raw || "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);

const arrayToCsv = (values) =>
  Array.isArray(values) && values.length > 0 ? values.join(", ") : "";

const normalizeOption = (option) => ({
  id: String(option?.id || ""),
  label: String(option?.label || option?.value || option?.id || ""),
});

const normalizeImageOptions = (imageOptions) => {
  const normalized = Array.isArray(imageOptions)
    ? imageOptions.map(normalizeOption).filter((option) => option.id)
    : [];
  return normalized.length > 0 ? normalized : defaultImageOptions;
};

const normalizeParameterDefinitions = (definitions) =>
  Array.isArray(definitions)
    ? definitions
        .map((definition) => ({
          name: String(definition?.name || "").trim(),
          className: String(
            definition?.className || definition?.class || "java.lang.String",
          ).trim(),
        }))
        .filter((definition) => definition.name)
    : [];

const normalizeMappings = (mappings) => {
  if (!mappings || typeof mappings !== "object") {
    return {};
  }

  return Object.entries(mappings).reduce((acc, [parameter, mapping]) => {
    if (!parameter) {
      return acc;
    }
    acc[parameter] = {
      type: String(mapping?.type || "source").trim(),
      value: String(mapping?.value || "").trim(),
    };
    return acc;
  }, {});
};

const normalizeToken = (value) =>
  String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]/g, "");

const defaultAliasMap = {
  patientname: "patientName",
  fullname: "patientName",
  pacientename: "patientName",
  patientdni: "dni",
  documentnumber: "dni",
  passport: "passportNumber",
  patientpassport: "passportNumber",
  foreignid: "foreignId",
  nationalid: "nationalId",
  hc: "subjectNumber",
  patienthc: "subjectNumber",
  subjectnumber: "subjectNumber",
  cug: "sampleCug",
  samplecug: "sampleCug",
  accession: "accessionNumber",
  accessionnumber: "accessionNumber",
  gender: "gender",
  sex: "gender",
  birthdate: "dob",
  patientbirthdate: "dob",
  dob: "dob",
  patientdob: "dob",
  contact: "patientSiteNumber",
  patientsitenumber: "patientSiteNumber",
  requestingphysician: "prescriber",
  medicosolicitante: "prescriber",
  physician: "prescriber",
  prescriber: "prescriber",
  requesterfirstname: "requesterFirstName",
  requesterlastname: "requesterLastName",
  requesterphone: "requesterPhone",
  requesteremail: "requesterEmail",
  requestercmp: "requesterCmp",
  requesterrne: "requesterRne",
  requesterspecialty: "requesterSpecialty",
  referringsite: "siteInfo",
  referencecenter: "siteInfo",
  centroreferencia: "siteInfo",
  siteinfo: "siteInfo",
  collectiondatetime: "collectionDateTime",
  collectiondate: "collectionDateTime",
  sampletype: "sampleType",
  samplesource: "sampleType",
  resultdate: "orderFinishDate",
  orderfinishdate: "orderFinishDate",
  orderdate: "orderDate",
  testdate: "testDate",
  analysisresult1: "analysisResult1",
  analysisresult2: "analysisResult2",
  result1: "analysisResult1",
  result2: "analysisResult2",
  headerleftimage: "headerLeftImage",
  leftlogo: "headerLeftImage",
  headerrightimage: "headerRightImage",
  rightlogo: "headerRightImage",
  signature: "labDirectorSignature",
  directorsignature: "labDirectorSignature",
  labdirectorsignature: "labDirectorSignature",
};

const buildOptionIndex = (options) =>
  (options || []).reduce((acc, option) => {
    if (!option?.id) {
      return acc;
    }

    const normalizedId = normalizeToken(option.id);
    const normalizedLabel = normalizeToken(option.label);

    if (normalizedId) {
      acc[normalizedId] = option.id;
    }
    if (normalizedLabel) {
      acc[normalizedLabel] = option.id;
    }

    return acc;
  }, {});

const inferMappingForParameter = (parameter, sourceOptions, imageOptions) => {
  const normalizedName = normalizeToken(parameter?.name);
  if (!normalizedName) {
    return null;
  }

  const isImageParameter = parameter?.className === "java.io.InputStream";
  const optionIndex = buildOptionIndex(
    isImageParameter ? imageOptions : sourceOptions,
  );

  const aliasTarget = defaultAliasMap[normalizedName];
  if (aliasTarget && optionIndex[normalizeToken(aliasTarget)]) {
    return {
      type: isImageParameter ? "image" : "source",
      value: optionIndex[normalizeToken(aliasTarget)],
    };
  }

  const candidateTokens = [
    normalizedName,
    normalizedName.replace(/^patient/, ""),
    normalizedName.replace(/^requester/, "requester"),
    normalizedName.replace(/^report/, ""),
  ].filter(Boolean);

  for (const token of candidateTokens) {
    if (optionIndex[token]) {
      return {
        type: isImageParameter ? "image" : "source",
        value: optionIndex[token],
      };
    }
  }

  if (isImageParameter) {
    return {
      type: "image",
      value: imageOptions[0]?.id || "",
    };
  }

  return {
    type: "source",
    value: "",
  };
};

const ensureMappingsForParameters = (
  parameterDefinitions,
  mappings,
  imageOptions,
  sourceOptions,
) =>
  parameterDefinitions.reduce((acc, parameter) => {
    const current = mappings?.[parameter.name];
    const suggested = inferMappingForParameter(
      parameter,
      sourceOptions,
      imageOptions,
    );

    if (current?.type && (current.type === "empty" || current.value)) {
      acc[parameter.name] = current;
      return acc;
    }

    acc[parameter.name] = {
      ...(suggested || {}),
      ...(current || {}),
      type: current?.type || suggested?.type || "source",
      value: current?.value || suggested?.value || "",
    };
    return acc;
  }, {});

const getTemplateMode = (override) => {
  const mode = override?.config?.mode;
  return mode === DYNAMIC_TEMPLATE_MODE ? DYNAMIC_TEMPLATE_MODE : LEGACY_TEMPLATE_MODE;
};

const toFormState = (override, imageOptions, sourceOptions = []) => {
  if (!override) {
    return {
      ...emptyForm,
      parameterMappings: ensureMappingsForParameters(
        [],
        {},
        imageOptions,
        sourceOptions,
      ),
    };
  }

  const parameterDefinitions = normalizeParameterDefinitions(
    override?.config?.parameterDefinitions,
  );
  const parameterMappings = ensureMappingsForParameters(
    parameterDefinitions,
    normalizeMappings(override?.config?.mappings),
    imageOptions,
    sourceOptions,
  );

  return {
    id: override?.id || "",
    name: override?.name || "",
    description: override?.description || "",
    report: override?.report || DYNAMIC_TEMPLATE_REPORT,
    isActive: override?.isActive !== false,
    testIds: Array.isArray(override?.testIds) ? override.testIds : [],
    testCodesText: arrayToCsv(override?.testCodes),
    mode: getTemplateMode(override),
    templateName: override?.config?.templateName || "",
    templateContent: override?.config?.templateContent || "",
    parameterDefinitions,
    parameterMappings,
    warningMessages: Array.isArray(override?.config?.warningMessages)
      ? override.config.warningMessages
      : [],
    fieldCount: Number(override?.config?.fieldCount || 0),
    rawConfig: override?.config || {},
  };
};

const ValidationTemplateOverrideConfig = () => {
  const intl = useIntl();
  const [tests, setTests] = useState([]);
  const [sourceOptions, setSourceOptions] = useState([]);
  const [imageOptions, setImageOptions] = useState(defaultImageOptions);
  const [overrides, setOverrides] = useState([]);
  const [form, setForm] = useState({
    ...emptyForm,
    parameterMappings: {},
  });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [parsingTemplate, setParsingTemplate] = useState(false);
  const [uploadingParameter, setUploadingParameter] = useState("");
  const [message, setMessage] = useState(null);

  const testItems = useMemo(
    () =>
      (tests || []).map((test) => ({
        id: String(test.id),
        label: String(test.value || test.name || test.id),
      })),
    [tests],
  );

  const selectedTests = useMemo(
    () => testItems.filter((item) => form.testIds.includes(item.id)),
    [form.testIds, testItems],
  );

  const templateCards = useMemo(
    () =>
      (overrides || []).map((override) => ({
        ...override,
        templateMode: getTemplateMode(override),
        parameterCount: normalizeParameterDefinitions(
          override?.config?.parameterDefinitions,
        ).length,
      })),
    [overrides],
  );

  const loadData = () => {
    setLoading(true);
    getAllTests((testData) => {
      setTests(Array.isArray(testData) ? testData : []);
      getValidationFieldOptions([], (fieldOptions) => {
        const nextSourceOptions = Array.isArray(fieldOptions?.sources)
          ? fieldOptions.sources.map(normalizeOption).filter((option) => option.id)
          : [];
        setSourceOptions(nextSourceOptions);
        const nextImageOptions = normalizeImageOptions(fieldOptions?.imageOptions);
        setImageOptions(nextImageOptions);
        getValidationTemplateOverrides((overrideData) => {
          const parsed = Array.isArray(overrideData) ? overrideData : [];
          setOverrides(parsed);
          setForm((prev) =>
            prev?.id
              ? toFormState(
                  parsed.find((item) => item.id === prev.id) || prev,
                  nextImageOptions,
                  nextSourceOptions,
                )
              : {
                  ...emptyForm,
                  parameterMappings: ensureMappingsForParameters(
                    [],
                    {},
                    nextImageOptions,
                    nextSourceOptions,
                  ),
                },
          );
          setLoading(false);
        });
      });
    });
  };

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    getValidationFieldOptions(form.testIds, (fieldOptions) => {
      const nextSourceOptions = Array.isArray(fieldOptions?.sources)
        ? fieldOptions.sources.map(normalizeOption).filter((option) => option.id)
        : [];
      const nextImageOptions = normalizeImageOptions(fieldOptions?.imageOptions);

      setSourceOptions(nextSourceOptions);
      setImageOptions(nextImageOptions);
      setForm((prev) => ({
        ...prev,
        parameterMappings: ensureMappingsForParameters(
          prev.parameterDefinitions,
          prev.parameterMappings,
          nextImageOptions,
          nextSourceOptions,
        ),
      }));
    });
  }, [form.testIds]);

  const onCreateNew = () => {
    setForm({
      ...emptyForm,
      parameterMappings: ensureMappingsForParameters(
        [],
        {},
        imageOptions,
        sourceOptions,
      ),
    });
    setMessage(null);
  };

  const onSelectOverride = (override) => {
    setForm(toFormState(override, imageOptions, sourceOptions));
    setMessage(null);
  };

  const updateForm = (field, value) => {
    setForm((prev) => ({ ...prev, [field]: value }));
  };

  const updateParameterMapping = (parameterName, patch) => {
    setForm((prev) => ({
      ...prev,
      parameterMappings: {
        ...(prev.parameterMappings || {}),
        [parameterName]: {
          ...(prev.parameterMappings?.[parameterName] || {}),
          ...patch,
        },
      },
    }));
  };

  const applyParsedTemplate = (parsedTemplate) => {
    const parameterDefinitions = normalizeParameterDefinitions(
      parsedTemplate?.parameterDefinitions,
    );
    const nextImageOptions = imageOptions.length > 0 ? imageOptions : defaultImageOptions;
    setForm((prev) => ({
      ...prev,
      mode: DYNAMIC_TEMPLATE_MODE,
      report: DYNAMIC_TEMPLATE_REPORT,
      templateName:
        String(parsedTemplate?.templateName || "").trim() || prev.templateName,
      templateContent: String(parsedTemplate?.templateContent || ""),
      parameterDefinitions,
      warningMessages: Array.isArray(parsedTemplate?.warningMessages)
        ? parsedTemplate.warningMessages
        : [],
      fieldCount: Number(parsedTemplate?.fieldCount || 0),
      parameterMappings: ensureMappingsForParameters(
        parameterDefinitions,
        prev.parameterMappings,
        nextImageOptions,
        sourceOptions,
      ),
      rawConfig: {
        ...(prev.rawConfig || {}),
        mode: DYNAMIC_TEMPLATE_MODE,
        templateName:
          String(parsedTemplate?.templateName || "").trim() || prev.templateName,
        templateContent: String(parsedTemplate?.templateContent || ""),
        parameterDefinitions,
        warningMessages: Array.isArray(parsedTemplate?.warningMessages)
          ? parsedTemplate.warningMessages
          : [],
        fieldCount: Number(parsedTemplate?.fieldCount || 0),
      },
      name:
        prev.name ||
        String(parsedTemplate?.templateName || "").trim() ||
        prev.name,
    }));
  };

  const onTemplateFileSelected = async (event) => {
    const file = event?.target?.files?.[0];
    if (!file) {
      return;
    }

    try {
      setParsingTemplate(true);
      const parsedTemplate = await parseValidationTemplateFile(file);
      applyParsedTemplate(parsedTemplate);
      setMessage({
        kind: "success",
        title: intl.formatMessage({
          id: "validation.template.override.template.parse.success.title",
          defaultMessage: "Template analyzed",
        }),
        subtitle: intl.formatMessage(
          {
            id: "validation.template.override.template.parse.success.subtitle",
            defaultMessage:
              "Detected {count} Jasper parameters from {name}.",
          },
          {
            count: normalizeParameterDefinitions(parsedTemplate?.parameterDefinitions)
              .length,
            name: parsedTemplate?.templateName || file.name,
          },
        ),
      });
    } catch (error) {
      setMessage({
        kind: "error",
        title: intl.formatMessage({
          id: "validation.template.override.template.parse.error.title",
          defaultMessage: "Unable to read template",
        }),
        subtitle:
          error?.message ||
          intl.formatMessage({
            id: "validation.template.override.template.parse.error.subtitle",
            defaultMessage: "The JRXML file could not be parsed.",
          }),
      });
    } finally {
      setParsingTemplate(false);
      event.target.value = "";
    }
  };

  const onUploadParameterImage = async (parameterName, file) => {
    if (!file) {
      return;
    }

    try {
      setUploadingParameter(parameterName);
      const uploaded = await uploadValidationTemplateImage(
        file,
        `${form.templateName || form.name || "jasper-template"}-${parameterName}`,
      );
      updateParameterMapping(parameterName, {
        type: "image",
        value: uploaded?.key || "",
      });
      setMessage({
        kind: "success",
        title: intl.formatMessage({
          id: "validation.template.override.logo.upload.success.title",
          defaultMessage: "Image Uploaded",
        }),
        subtitle: intl.formatMessage(
          {
            id: "validation.template.override.parameter.image.upload.success",
            defaultMessage: "Image saved and linked to {parameter}.",
          },
          { parameter: parameterName },
        ),
      });
    } catch (error) {
      setMessage({
        kind: "error",
        title: intl.formatMessage({
          id: "validation.template.override.template.image.error.title",
          defaultMessage: "Unable to upload image",
        }),
        subtitle:
          error?.message ||
          intl.formatMessage({
            id: "validation.template.override.template.image.error.subtitle",
            defaultMessage: "The image could not be uploaded.",
          }),
      });
    } finally {
      setUploadingParameter("");
    }
  };

  const buildDynamicConfig = () => ({
    mode: DYNAMIC_TEMPLATE_MODE,
    templateName: form.templateName?.trim(),
    templateContent: form.templateContent,
    parameterDefinitions: form.parameterDefinitions.map((parameter) => ({
      name: parameter.name,
      className: parameter.className,
    })),
    mappings: Object.entries(form.parameterMappings || {}).reduce(
      (acc, [parameter, mapping]) => {
        const type = String(mapping?.type || "").trim();
        const value = String(mapping?.value || "").trim();
        if (!type || (type !== "empty" && !value)) {
          return acc;
        }
        acc[parameter] = { type, value };
        return acc;
      },
      {},
    ),
    warningMessages: form.warningMessages || [],
    fieldCount: Number(form.fieldCount || 0),
  });

  const onSave = async () => {
    const testCodes = csvToArray(form.testCodesText);

    if ((form.testIds || []).length === 0 && testCodes.length === 0) {
      setMessage({
        kind: "error",
        title: intl.formatMessage({
          id: "validation.template.override.save.error.title",
          defaultMessage: "Validation Error",
        }),
        subtitle: intl.formatMessage({
          id: "validation.template.override.error.test.required",
          defaultMessage: "Select at least one test or enter test codes.",
        }),
      });
      return;
    }

    if (form.mode === DYNAMIC_TEMPLATE_MODE) {
      if (!form.templateContent) {
        setMessage({
          kind: "error",
          title: intl.formatMessage({
            id: "validation.template.override.template.required.title",
            defaultMessage: "Template required",
          }),
          subtitle: intl.formatMessage({
            id: "validation.template.override.template.required.subtitle",
            defaultMessage: "Upload and analyze a JRXML file before saving.",
          }),
        });
        return;
      }

      if ((form.parameterDefinitions || []).length === 0) {
        setMessage({
          kind: "error",
          title: intl.formatMessage({
            id: "validation.template.override.parameters.required.title",
            defaultMessage: "No parameters detected",
          }),
          subtitle: intl.formatMessage({
            id: "validation.template.override.parameters.required.subtitle",
            defaultMessage:
              "The uploaded JRXML must declare Jasper parameters to be mapped.",
          }),
        });
        return;
      }
    }

    const payload = {
      id: form.id || undefined,
      name: form.name?.trim(),
      description: form.description?.trim(),
      report:
        form.mode === DYNAMIC_TEMPLATE_MODE
          ? DYNAMIC_TEMPLATE_REPORT
          : form.report?.trim(),
      isActive: form.isActive,
      testIds: form.testIds,
      testCodes,
      config:
        form.mode === DYNAMIC_TEMPLATE_MODE ? buildDynamicConfig() : form.rawConfig || {},
    };

    try {
      setSaving(true);
      await saveValidationTemplateOverride(payload);
      setMessage({
        kind: "success",
        title: intl.formatMessage({
          id: "validation.template.override.save.success.title",
          defaultMessage: "Saved",
        }),
        subtitle: intl.formatMessage({
          id: "validation.template.override.save.success.subtitle",
          defaultMessage: "Template override saved successfully.",
        }),
      });
      loadData();
    } catch (error) {
      setMessage({
        kind: "error",
        title: intl.formatMessage({
          id: "validation.template.override.save.error.title",
          defaultMessage: "Validation Error",
        }),
        subtitle:
          error?.message ||
          intl.formatMessage({
            id: "validation.template.override.save.error.subtitle",
            defaultMessage: "Unable to save template override.",
          }),
      });
    } finally {
      setSaving(false);
    }
  };

  const renderMappingControl = (parameter) => {
    const mapping = form.parameterMappings?.[parameter.name] || {
      type: parameter.className === "java.io.InputStream" ? "image" : "source",
      value: "",
    };
    const isImageParameter = parameter.className === "java.io.InputStream";

    return (
      <div className="validation-template-override-parameter-controls">
        <Select
          id={`mapping-type-${parameter.name}`}
          labelText={intl.formatMessage({
            id: "validation.template.override.mapping.type",
            defaultMessage: "Mapping type",
          })}
          value={mapping.type}
          onChange={(event) =>
            updateParameterMapping(parameter.name, {
              type: event.target.value,
              value:
                event.target.value === "image"
                  ? imageOptions[0]?.id || ""
                  : "",
            })
          }
        >
          {!isImageParameter && (
            <SelectItem
              value="source"
              text={intl.formatMessage({
                id: "validation.template.override.mapping.type.source",
                defaultMessage: "System data",
              })}
            />
          )}
          {!isImageParameter && (
            <SelectItem
              value="constant"
              text={intl.formatMessage({
                id: "validation.template.override.mapping.type.constant",
                defaultMessage: "Fixed text",
              })}
            />
          )}
          {isImageParameter && (
            <SelectItem
              value="image"
              text={intl.formatMessage({
                id: "validation.template.override.mapping.type.image",
                defaultMessage: "Image",
              })}
            />
          )}
          <SelectItem
            value="empty"
            text={intl.formatMessage({
              id: "validation.template.override.mapping.type.empty",
              defaultMessage: "Leave empty",
            })}
          />
        </Select>

        {mapping.type === "source" && (
          <Select
            id={`mapping-source-${parameter.name}`}
            labelText={intl.formatMessage({
              id: "validation.template.override.mapping.source",
              defaultMessage: "OpenELIS source",
            })}
            value={mapping.value}
            onChange={(event) =>
              updateParameterMapping(parameter.name, {
                value: event.target.value,
              })
            }
          >
            <SelectItem
              value=""
              text={intl.formatMessage({
                id: "validation.template.override.mapping.source.placeholder",
                defaultMessage: "Select a source",
              })}
            />
            {sourceOptions.map((option) => (
              <SelectItem key={option.id} value={option.id} text={option.label} />
            ))}
          </Select>
        )}

        {mapping.type === "constant" && (
          <TextInput
            id={`mapping-constant-${parameter.name}`}
            labelText={intl.formatMessage({
              id: "validation.template.override.mapping.constant",
              defaultMessage: "Fixed value",
            })}
            value={mapping.value}
            onChange={(event) =>
              updateParameterMapping(parameter.name, {
                value: event.target.value,
              })
            }
          />
        )}

        {mapping.type === "image" && (
          <div className="validation-template-override-image-mapping">
            <Select
              id={`mapping-image-${parameter.name}`}
              labelText={intl.formatMessage({
                id: "validation.template.override.mapping.image",
                defaultMessage: "Image source",
              })}
              value={mapping.value}
              onChange={(event) =>
                updateParameterMapping(parameter.name, {
                  value: event.target.value,
                })
              }
            >
              <SelectItem
                value=""
                text={intl.formatMessage({
                  id: "validation.template.override.mapping.image.placeholder",
                  defaultMessage: "Select an image",
                })}
              />
              {imageOptions.map((option) => (
                <SelectItem key={option.id} value={option.id} text={option.label} />
              ))}
            </Select>
            <div className="validation-template-override-inline-upload">
              <label htmlFor={`parameter-image-${parameter.name}`}>
                <FormattedMessage
                  id="validation.template.override.mapping.image.upload"
                  defaultMessage="Upload custom image"
                />
              </label>
              <input
                id={`parameter-image-${parameter.name}`}
                type="file"
                accept=".png,.jpg,.jpeg,.gif"
                disabled={uploadingParameter === parameter.name}
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  onUploadParameterImage(parameter.name, file);
                  event.target.value = "";
                }}
              />
            </div>
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="adminPageContent validation-template-override-page">
      <header className="validation-template-override-page-header">
        <div>
          <h1>
            <FormattedMessage
              id="validation.template.override.title"
              defaultMessage="Jasper Validation Templates"
            />
          </h1>
          <p className="validation-template-override-subtitle">
            <FormattedMessage
              id="validation.template.override.subtitle"
              defaultMessage="Upload a JRXML template, detect its parameters automatically, map them to OpenELIS data, and assign the template to one or more tests."
            />
          </p>
        </div>
        <Button
          kind="secondary"
          renderIcon={Add}
          onClick={onCreateNew}
          size="md"
        >
          <FormattedMessage
            id="validation.template.override.new"
            defaultMessage="New Template"
          />
        </Button>
      </header>

      {message && (
        <InlineNotification
          className="validation-template-override-alert"
          kind={message.kind}
          title={message.title}
          subtitle={message.subtitle}
          hideCloseButton={false}
          onCloseButtonClick={() => setMessage(null)}
        />
      )}

      {loading && <Loading withOverlay={false} />}

      {!loading && (
        <div className="validation-template-override-shell">
          <aside className="validation-template-override-sidebar">
            <div className="validation-template-override-sidebar-header">
              <h2>
                <FormattedMessage
                  id="validation.template.override.list.title"
                  defaultMessage="Configured Templates"
                />
              </h2>
              <p>
                <FormattedMessage
                  id="validation.template.override.list.helper"
                  defaultMessage="Select an existing template to review or edit it."
                />
              </p>
            </div>
            <div className="validation-template-override-card-list">
              {templateCards.length === 0 && (
                <Tile className="validation-template-override-empty">
                  <FormattedMessage
                    id="validation.template.override.list.empty"
                    defaultMessage="No templates have been configured yet."
                  />
                </Tile>
              )}
              {templateCards.map((override) => (
                <button
                  key={override.id}
                  type="button"
                  className={`validation-template-override-card${
                    form.id === override.id ? " is-selected" : ""
                  }`}
                  onClick={() => onSelectOverride(override)}
                >
                  <div className="validation-template-override-card-top">
                    <strong>{override.name || override.config?.templateName || override.id}</strong>
                    <Tag type={override.isActive === false ? "gray" : "green"}>
                      {override.isActive === false
                        ? intl.formatMessage({
                            id: "validation.template.override.status.inactive",
                            defaultMessage: "Inactive",
                          })
                        : intl.formatMessage({
                            id: "validation.template.override.status.active",
                            defaultMessage: "Active",
                          })}
                    </Tag>
                  </div>
                  <div className="validation-template-override-card-meta">
                    <Tag type={override.templateMode === DYNAMIC_TEMPLATE_MODE ? "blue" : "purple"}>
                      {override.templateMode === DYNAMIC_TEMPLATE_MODE
                        ? intl.formatMessage({
                            id: "validation.template.override.mode.dynamic",
                            defaultMessage: "Uploaded JRXML",
                          })
                        : intl.formatMessage({
                            id: "validation.template.override.mode.legacy",
                            defaultMessage: "Legacy mapping",
                          })}
                    </Tag>
                    {override.parameterCount > 0 && (
                      <span>
                        {intl.formatMessage(
                          {
                            id: "validation.template.override.parameter.count",
                            defaultMessage: "{count} parameters",
                          },
                          { count: override.parameterCount },
                        )}
                      </span>
                    )}
                    {override.testIds?.length > 0 && (
                      <span>
                        {intl.formatMessage(
                          {
                            id: "validation.template.override.test.count",
                            defaultMessage: "{count} tests",
                          },
                          { count: override.testIds.length },
                        )}
                      </span>
                    )}
                  </div>
                  {override.description && (
                    <p className="validation-template-override-card-description">
                      {override.description}
                    </p>
                  )}
                </button>
              ))}
            </div>
          </aside>

          <main className="validation-template-override-main">
            {form.mode === LEGACY_TEMPLATE_MODE && (
              <InlineNotification
                className="validation-template-override-legacy-banner"
                kind="warning"
                lowContrast={true}
                title={intl.formatMessage({
                  id: "validation.template.override.legacy.title",
                  defaultMessage: "Legacy template configuration",
                })}
                subtitle={intl.formatMessage({
                  id: "validation.template.override.legacy.subtitle",
                  defaultMessage:
                    "This record still uses the older report-specific mapping format. Upload a JRXML below if you want to migrate it into the new Jasper parameter flow.",
                })}
                hideCloseButton={true}
              />
            )}

            <section className="validation-template-override-step">
              <div className="validation-template-override-step-header">
                <div>
                  <span className="validation-template-override-step-number">1</span>
                  <div>
                    <h2>
                      <FormattedMessage
                        id="validation.template.override.step.template.title"
                        defaultMessage="Template and assignment"
                      />
                    </h2>
                    <p>
                      <FormattedMessage
                        id="validation.template.override.step.template.subtitle"
                        defaultMessage="Name the template, assign it to tests, and upload the JRXML that should drive the PDF."
                      />
                    </p>
                  </div>
                </div>
                {parsingTemplate && (
                  <InlineNotification
                    kind="info"
                    lowContrast={true}
                    title={intl.formatMessage({
                      id: "validation.template.override.template.parsing",
                      defaultMessage: "Analyzing JRXML...",
                    })}
                    hideCloseButton={true}
                  />
                )}
              </div>

              <div className="validation-template-override-form-grid">
                <TextInput
                  id="template-display-name"
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.name",
                    defaultMessage: "Display Name",
                  })}
                  value={form.name}
                  onChange={(event) => updateForm("name", event.target.value)}
                />
                <div className="validation-template-override-upload-control">
                  <label htmlFor="jasper-template-upload">
                    <FormattedMessage
                      id="validation.template.override.template.upload"
                      defaultMessage="JRXML template"
                    />
                  </label>
                  <input
                    id="jasper-template-upload"
                    type="file"
                    accept=".jrxml"
                    onChange={onTemplateFileSelected}
                    disabled={parsingTemplate}
                  />
                </div>
                <div className="validation-template-override-full-width">
                  <MultiSelect
                    id="template-tests"
                    titleText={intl.formatMessage({
                      id: "validation.template.override.form.tests",
                      defaultMessage: "Tests (IDs)",
                    })}
                    label={intl.formatMessage({
                      id: "validation.template.override.form.tests.placeholder",
                      defaultMessage: "Select tests",
                    })}
                    items={testItems}
                    itemToString={(item) => item?.label || ""}
                    selectedItems={selectedTests}
                    onChange={({ selectedItems: nextSelectedItems }) =>
                      updateForm(
                        "testIds",
                        (nextSelectedItems || []).map((item) => item.id),
                      )
                    }
                  />
                </div>
                <TextInput
                  id="template-test-codes"
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.testCodes",
                    defaultMessage: "Test Codes (optional)",
                  })}
                  helperText={intl.formatMessage({
                    id: "validation.template.override.form.testCodes.helper",
                    defaultMessage: "Comma separated. Example: 81234, 81271",
                  })}
                  value={form.testCodesText}
                  onChange={(event) => updateForm("testCodesText", event.target.value)}
                />
                <div className="validation-template-override-template-summary">
                  <span>
                    <FormattedMessage
                      id="validation.template.override.template.detected"
                      defaultMessage="Detected template"
                    />
                  </span>
                  <strong>
                    {form.templateName ||
                      intl.formatMessage({
                        id: "validation.template.override.template.none",
                        defaultMessage: "No JRXML uploaded yet",
                      })}
                  </strong>
                </div>
                <div className="validation-template-override-full-width">
                  <TextArea
                    id="template-description"
                    labelText={intl.formatMessage({
                      id: "validation.template.override.form.description",
                      defaultMessage: "Description",
                    })}
                    value={form.description}
                    onChange={(event) => updateForm("description", event.target.value)}
                  />
                </div>
              </div>

              <div className="validation-template-override-chip-row">
                <Tag type="blue">
                  {intl.formatMessage(
                    {
                      id: "validation.template.override.summary.parameters",
                      defaultMessage: "{count} parameters detected",
                    },
                    { count: form.parameterDefinitions.length },
                  )}
                </Tag>
                <Tag type={form.fieldCount > 0 ? "red" : "green"}>
                  {form.fieldCount > 0
                    ? intl.formatMessage(
                        {
                          id: "validation.template.override.summary.fields",
                          defaultMessage: "{count} Jasper fields detected",
                        },
                        { count: form.fieldCount },
                      )
                    : intl.formatMessage({
                        id: "validation.template.override.summary.parametersOnly",
                        defaultMessage: "Parameter-based template",
                      })}
                </Tag>
              </div>

              {Array.isArray(form.warningMessages) &&
                form.warningMessages.length > 0 && (
                  <div className="validation-template-override-warning-list">
                    {form.warningMessages.map((warning, index) => (
                      <InlineNotification
                        key={`${warning}-${index}`}
                        kind="warning"
                        lowContrast={true}
                        title={warning}
                        hideCloseButton={true}
                      />
                    ))}
                  </div>
                )}
            </section>

            <section className="validation-template-override-step">
              <div className="validation-template-override-step-header">
                <div>
                  <span className="validation-template-override-step-number">2</span>
                  <div>
                    <h2>
                      <FormattedMessage
                        id="validation.template.override.step.mapping.title"
                        defaultMessage="Parameter mapping"
                      />
                    </h2>
                    <p>
                      <FormattedMessage
                        id="validation.template.override.step.mapping.subtitle"
                        defaultMessage="Each Jasper parameter becomes one row. Point it to OpenELIS data, a fixed value, or an image."
                      />
                    </p>
                  </div>
                </div>
              </div>

              {form.parameterDefinitions.length === 0 ? (
                <Tile className="validation-template-override-empty">
                  <FormattedMessage
                    id="validation.template.override.parameters.empty"
                    defaultMessage="Upload a JRXML file first. The detected Jasper parameters will appear here automatically."
                  />
                </Tile>
              ) : (
                <div className="validation-template-override-parameter-list">
                  {form.parameterDefinitions.map((parameter) => (
                    <div
                      key={parameter.name}
                      className="validation-template-override-parameter-card"
                    >
                      <div className="validation-template-override-parameter-header">
                        <div>
                          <strong>{parameter.name}</strong>
                          <p>{parameter.className}</p>
                        </div>
                        <Tag type="cool-gray">
                          {parameter.className === "java.io.InputStream"
                            ? intl.formatMessage({
                                id: "validation.template.override.parameter.kind.image",
                                defaultMessage: "Image parameter",
                              })
                            : intl.formatMessage({
                                id: "validation.template.override.parameter.kind.value",
                                defaultMessage: "Value parameter",
                              })}
                        </Tag>
                      </div>
                      {renderMappingControl(parameter)}
                      {uploadingParameter === parameter.name && (
                        <div className="validation-template-override-inline-status">
                          <FormattedMessage
                            id="validation.template.override.logo.uploading"
                            defaultMessage="Uploading..."
                          />
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="validation-template-override-step">
              <div className="validation-template-override-step-header">
                <div>
                  <span className="validation-template-override-step-number">3</span>
                  <div>
                    <h2>
                      <FormattedMessage
                        id="validation.template.override.step.review.title"
                        defaultMessage="Review and activate"
                      />
                    </h2>
                    <p>
                      <FormattedMessage
                        id="validation.template.override.step.review.subtitle"
                        defaultMessage="Save the assignment once the template, mappings, and active state look right."
                      />
                    </p>
                  </div>
                </div>
              </div>

              <div className="validation-template-override-review-grid">
                <div className="validation-template-override-review-item">
                  <span>
                    <FormattedMessage
                      id="validation.template.override.review.tests"
                      defaultMessage="Assigned tests"
                    />
                  </span>
                  <strong>{form.testIds.length + csvToArray(form.testCodesText).length}</strong>
                </div>
                <div className="validation-template-override-review-item">
                  <span>
                    <FormattedMessage
                      id="validation.template.override.review.parameters"
                      defaultMessage="Mapped parameters"
                    />
                  </span>
                  <strong>
                    {
                      Object.values(form.parameterMappings || {}).filter(
                        (mapping) =>
                          mapping?.type &&
                          (mapping.type === "empty" ||
                            String(mapping?.value || "").trim()),
                      ).length
                    }
                  </strong>
                </div>
                <div className="validation-template-override-review-item">
                  <span>
                    <FormattedMessage
                      id="validation.template.override.review.mode"
                      defaultMessage="Runtime mode"
                    />
                  </span>
                  <strong>
                    {form.mode === DYNAMIC_TEMPLATE_MODE
                      ? intl.formatMessage({
                          id: "validation.template.override.mode.dynamic",
                          defaultMessage: "Uploaded JRXML",
                        })
                      : intl.formatMessage({
                          id: "validation.template.override.mode.legacy",
                          defaultMessage: "Legacy mapping",
                        })}
                  </strong>
                </div>
              </div>

              <Checkbox
                id="template-active"
                labelText={intl.formatMessage({
                  id: "validation.template.override.form.active",
                  defaultMessage: "Active",
                })}
                checked={form.isActive}
                onChange={(_, { checked }) => updateForm("isActive", checked)}
              />

              <div className="validation-template-override-actions">
                <Button
                  kind="primary"
                  renderIcon={Save}
                  onClick={onSave}
                  disabled={saving}
                >
                  {saving
                    ? intl.formatMessage({
                        id: "validation.template.override.save.saving",
                        defaultMessage: "Saving...",
                      })
                    : intl.formatMessage({
                        id: "validation.template.override.save",
                        defaultMessage: "Save",
                      })}
                </Button>
                <Button
                  kind="ghost"
                  renderIcon={Edit}
                  onClick={onCreateNew}
                  disabled={saving}
                >
                  <FormattedMessage
                    id="validation.template.override.actions.reset"
                    defaultMessage="Start another template"
                  />
                </Button>
              </div>
            </section>
          </main>
        </div>
      )}
    </div>
  );
};

export default ValidationTemplateOverrideConfig;
