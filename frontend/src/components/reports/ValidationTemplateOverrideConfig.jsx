import React, { useEffect, useMemo, useState } from "react";
import {
  Select,
  SelectItem,
  Button,
  Checkbox,
  InlineNotification,
  MultiSelect,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TextArea,
  TextInput,
} from "@carbon/react";
import { Add, Edit, Save, Subtract } from "@carbon/icons-react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  getAllTests,
  getValidationFieldOptions,
  getValidationReportOptions,
  getValidationTemplateOverrides,
  saveValidationTemplateOverride,
  uploadValidationTemplateImage,
} from "../../services/reportTemplateOverrideService";
import "./ValidationTemplateOverrideConfig.scss";

const emptyRow = () => ({ key: "", value: "" });
const emptySlotRow = () => ({ key: "", value: "", additionalKey: "" });
const emptySectionFieldRow = () => ({
  key: "",
  section: "SAMPLE",
  label: "",
  source: "",
  additionalKey: "",
  order: 10,
  enabled: true,
});

const defaultForm = {
  id: "",
  name: "",
  description: "",
  report: "patientDMPK",
  isActive: true,
  testIds: [],
  testCodesText: "",
  slotRows: [emptySlotRow()],
  constantRows: [emptyRow()],
  sectionFieldRows: [],
};

const rowsToObject = (rows) =>
  rows.reduce((acc, row) => {
    const key = (row.key || "").trim();
    const value = (row.value || "").trim();
    if (key && value) {
      acc[key] = value;
    }
    return acc;
  }, {});

const objectToRows = (obj) => {
  if (!obj || typeof obj !== "object") {
    return [emptyRow()];
  }
  const rows = Object.entries(obj).map(([key, value]) => ({
    key: String(key || ""),
    value: String(value || ""),
  }));
  return rows.length > 0 ? rows : [emptyRow()];
};

const normalizeSlotRows = (rows) =>
  (rows || []).map((row) => {
    const source = (row?.value || "").trim();
    const additionalPrefix = additionalSourcePlaceholders.find((placeholder) =>
      source.startsWith(placeholder.replace("<field_key>", "")),
    );
    if (additionalPrefix && source !== additionalPrefix) {
      const prefix = additionalPrefix.replace("<field_key>", "");
      const extractedKey = source.substring(prefix.length);
      return {
        ...row,
        value: additionalPrefix,
        additionalKey: extractedKey,
      };
    }
    return {
      ...row,
      additionalKey: row?.additionalKey || "",
    };
  });

const csvToArray = (raw) =>
  (raw || "")
    .split(",")
    .map((value) => value.trim())
    .filter(Boolean);

const arrayToCsv = (arr) => (arr && arr.length > 0 ? arr.join(", ") : "");
const getTestCodeFromLabel = (label) => {
  const raw = String(label || "").trim();
  if (!raw) return "";
  const match = raw.match(/^(\d+)\s*[-–]/);
  if (match?.[1]) return match[1];
  return raw;
};

const toFormState = (override, defaultSectionFields = []) => ({
  id: override?.id || "",
  name: override?.name || "",
  description: override?.description || "",
  report: override?.report || "patientDMPK",
  isActive: override?.isActive !== false,
  testIds: override?.testIds || [],
  testCodesText: arrayToCsv(override?.testCodes || []),
  slotRows: normalizeSlotRows(objectToRows(override?.config?.slots)),
  constantRows: objectToRows(override?.config?.constants),
  sectionFieldRows: normalizeSectionFieldRows(
    override?.config?.sectionFields?.length > 0
      ? override.config.sectionFields
      : defaultSectionFields,
    defaultSectionFields,
  ),
});

const ADDITIONAL_TEST_SOURCE_PLACEHOLDER = "additional.<field_key>";
const ADDITIONAL_ORDER_SOURCE_PLACEHOLDER = "orderAdditional.<field_key>";
const ADDITIONAL_SAMPLE_SOURCE_PLACEHOLDER = "sampleAdditional.<field_key>";

const additionalSourcePlaceholders = [
  ADDITIONAL_TEST_SOURCE_PLACEHOLDER,
  ADDITIONAL_ORDER_SOURCE_PLACEHOLDER,
  ADDITIONAL_SAMPLE_SOURCE_PLACEHOLDER,
];

const normalizeSectionFieldRows = (rows, fallbackRows = []) => {
  const fallbackByKey = (fallbackRows || []).reduce((acc, field) => {
    const key = String(field?.key || "").trim();
    if (key) {
      acc[key] = field;
    }
    return acc;
  }, {});

  return (rows || []).map((row) => {
    const key = String(row?.key || "").trim();
    const fallback = fallbackByKey[key] || {};
    const source = (row?.source || "").trim();
    const additionalPrefix = additionalSourcePlaceholders.find((placeholder) =>
      source.startsWith(placeholder.replace("<field_key>", "")),
    );
    if (additionalPrefix && source !== additionalPrefix) {
      const prefix = additionalPrefix.replace("<field_key>", "");
      const extractedKey = source.substring(prefix.length);
      const forceOrderAdditionalKey =
        (extractedKey || "").toLowerCase() === "dmpk_alelo_1" ||
        (extractedKey || "").toLowerCase() === "dmpk_alelo_2";
      return {
        ...row,
        section: String(row?.section || fallback?.section || "SAMPLE").trim(),
        label: String(row?.label || fallback?.label || "").trim(),
        source: forceOrderAdditionalKey
          ? ADDITIONAL_ORDER_SOURCE_PLACEHOLDER
          : additionalPrefix,
        additionalKey: extractedKey,
      };
    }
    return {
      ...row,
      section: String(row?.section || fallback?.section || "SAMPLE").trim(),
      label: String(row?.label || fallback?.label || "").trim(),
      source: String(row?.source || fallback?.source || "").trim(),
      additionalKey: row?.additionalKey || "",
    };
  });
};

const ValidationTemplateOverrideConfig = () => {
  const intl = useIntl();
  const [tests, setTests] = useState([]);
  const [overrides, setOverrides] = useState([]);
  const [reportOptions, setReportOptions] = useState([]);
  const [sectionOptions, setSectionOptions] = useState([]);
  const [sourceOptions, setSourceOptions] = useState([]);
  const [defaultSectionFields, setDefaultSectionFields] = useState([]);
  const [form, setForm] = useState(defaultForm);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [uploadingLeftLogo, setUploadingLeftLogo] = useState(false);
  const [uploadingRightLogo, setUploadingRightLogo] = useState(false);
  const [leftLogoFile, setLeftLogoFile] = useState(null);
  const [rightLogoFile, setRightLogoFile] = useState(null);
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
  const effectiveTestIds = useMemo(() => {
    if (form.testIds && form.testIds.length > 0) {
      return form.testIds;
    }
    const wantedCodes = csvToArray(form.testCodesText);
    if (wantedCodes.length === 0 || testItems.length === 0) {
      return [];
    }
    const wantedSet = new Set(wantedCodes.map((code) => String(code).trim()));
    return testItems
      .filter((item) => wantedSet.has(getTestCodeFromLabel(item.label)))
      .map((item) => item.id);
  }, [form.testIds, form.testCodesText, testItems]);
  const additionalSourceOptions = useMemo(
    () =>
      sourceOptions.filter(
        (source) => {
          const sourceId = source?.id || "";
          const isAdditional = sourceId.startsWith("additional.");
          const isOrderAdditional = sourceId.startsWith("orderAdditional.");
          const isSampleAdditional = sourceId.startsWith("sampleAdditional.");
          return (
            (isAdditional &&
              sourceId !== ADDITIONAL_TEST_SOURCE_PLACEHOLDER) ||
            (isOrderAdditional &&
              sourceId !== ADDITIONAL_ORDER_SOURCE_PLACEHOLDER) ||
            (isSampleAdditional &&
              sourceId !== ADDITIONAL_SAMPLE_SOURCE_PLACEHOLDER)
          );
        },
      ),
    [sourceOptions],
  );

  const loadFieldOptions = (selectedTestIds = [], preserveExisting = false) => {
    getValidationFieldOptions(selectedTestIds, (fieldOptions) => {
      const sections = Array.isArray(fieldOptions?.sections) ? fieldOptions.sections : [];
      const sources = Array.isArray(fieldOptions?.sources) ? fieldOptions.sources : [];
      const defaults = Array.isArray(fieldOptions?.defaultSectionFields)
        ? normalizeSectionFieldRows(
            fieldOptions.defaultSectionFields,
            fieldOptions.defaultSectionFields,
          )
        : [];
      setSectionOptions(sections);
      setSourceOptions(sources);
      setDefaultSectionFields(defaults);
      if (!preserveExisting) {
        setForm((prev) => ({
          ...prev,
          sectionFieldRows:
            prev.sectionFieldRows && prev.sectionFieldRows.length > 0
              ? prev.sectionFieldRows
              : defaults,
        }));
      }
    });
  };

  const loadData = () => {
    setLoading(true);
    getValidationReportOptions((reportData) => {
      setReportOptions(Array.isArray(reportData) ? reportData : []);
      getAllTests((testData) => {
        setTests(Array.isArray(testData) ? testData : []);
        getValidationTemplateOverrides((overrideData) => {
          const parsed = Array.isArray(overrideData) ? overrideData : [];
          setOverrides(parsed);
          setLoading(false);
        });
      });
    });
  };

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    loadFieldOptions(effectiveTestIds, true);
  }, [effectiveTestIds]);

  useEffect(() => {
    if (
      !form.id &&
      (form.sectionFieldRows || []).length === 0 &&
      defaultSectionFields.length > 0
    ) {
      setForm((prev) => ({ ...prev, sectionFieldRows: defaultSectionFields }));
    }
  }, [defaultSectionFields, form.id, form.sectionFieldRows]);

  const updateRow = (listName, index, field, value) => {
    setForm((prev) => {
      const nextRows = [...prev[listName]];
      nextRows[index] = { ...nextRows[index], [field]: value };
      return { ...prev, [listName]: nextRows };
    });
  };

  const addRow = (listName) => {
    const factory = listName === "slotRows" ? emptySlotRow : emptyRow;
    setForm((prev) => ({ ...prev, [listName]: [...prev[listName], factory()] }));
  };

  const removeRow = (listName, index) => {
    setForm((prev) => {
      const rows = prev[listName].filter((_, rowIndex) => rowIndex !== index);
      const factory = listName === "slotRows" ? emptySlotRow : emptyRow;
      return { ...prev, [listName]: rows.length > 0 ? rows : [factory()] };
    });
  };

  const onSelectOverride = (override) => {
    setForm(toFormState(override, defaultSectionFields));
    setMessage(null);
  };

  const onCreateNew = () => {
    setForm({
      ...defaultForm,
      sectionFieldRows:
        defaultSectionFields.length > 0
          ? defaultSectionFields
          : [emptySectionFieldRow()],
    });
    setMessage(null);
  };

  const onSave = async () => {
    if (!form.report?.trim()) {
      setMessage({
        kind: "error",
        title: intl.formatMessage({
          id: "validation.template.override.save.error.title",
          defaultMessage: "Validation Error",
        }),
        subtitle: intl.formatMessage({
          id: "validation.template.override.error.report.required",
          defaultMessage: "Report key is required.",
        }),
      });
      return;
    }
    if ((form.testIds || []).length === 0 && csvToArray(form.testCodesText).length === 0) {
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

    const slots = (form.slotRows || []).reduce((acc, row) => {
      const key = (row.key || "").trim();
      if (!key) {
        return acc;
      }
      const sourceValue = additionalSourcePlaceholders.includes(row.value)
        ? `${row.value.replace("<field_key>", "")}${(row.additionalKey || "").trim()}`
        : (row.value || "").trim();
      if (sourceValue) {
        acc[key] = sourceValue;
      }
      return acc;
    }, {});
    const constants = rowsToObject(form.constantRows);
    const config = {};
    if (Object.keys(slots).length > 0) {
      config.slots = slots;
    }
    if (Object.keys(constants).length > 0) {
      config.constants = constants;
    }
    const sectionFields = (form.sectionFieldRows || [])
      .map((row) => {
        const sourceValue = additionalSourcePlaceholders.includes(row.source)
          ? `${row.source.replace("<field_key>", "")}${(row.additionalKey || "").trim()}`
          : (row.source || "").trim();
        return {
        key: (row.key || "").trim(),
        section: (row.section || "").trim(),
        label: (row.label || "").trim(),
        source: sourceValue,
        order: Number(row.order || 0),
        enabled: row.enabled !== false,
      };
      })
      .filter(
        (row) => row.key && row.section && row.label && row.source && row.order > 0,
      )
      .sort((a, b) => a.order - b.order);
    if (sectionFields.length > 0) {
      config.sectionFields = sectionFields;
    }

    const payload = {
      id: form.id || undefined,
      name: form.name?.trim(),
      description: form.description?.trim(),
      report: form.report?.trim(),
      isActive: form.isActive,
      testIds: form.testIds,
      testCodes: csvToArray(form.testCodesText),
      config,
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

  const ensureConstantRow = (rows, key, defaultValue = "") => {
    const normalizedKey = String(key || "").trim();
    if (!normalizedKey) {
      return rows;
    }
    const existingIndex = rows.findIndex(
      (row) => String(row?.key || "").trim() === normalizedKey,
    );
    if (existingIndex >= 0) {
      return rows;
    }
    return [...rows, { key: normalizedKey, value: defaultValue }];
  };

  const setConstantValue = (key, value) => {
    setForm((prev) => {
      const rows = [...(prev.constantRows || [])];
      const idx = rows.findIndex((row) => String(row?.key || "").trim() === key);
      if (idx >= 0) {
        rows[idx] = { ...rows[idx], key, value };
      } else {
        rows.push({ key, value });
      }
      return { ...prev, constantRows: rows.length > 0 ? rows : [emptyRow()] };
    });
  };

  const applyLogoConstantPreset = () => {
    setForm((prev) => {
      let nextRows = [...(prev.constantRows || [])];
      nextRows = ensureConstantRow(nextRows, "leftHeaderImageName", "headerLeftImage");
      nextRows = ensureConstantRow(nextRows, "rightHeaderImageName", "headerRightImage");
      return { ...prev, constantRows: nextRows.length > 0 ? nextRows : [emptyRow()] };
    });
  };

  const onUploadLogo = async (side) => {
    const isLeft = side === "left";
    const file = isLeft ? leftLogoFile : rightLogoFile;
    if (!file) {
      setMessage({
        kind: "error",
        title: intl.formatMessage({
          id: "validation.template.override.save.error.title",
          defaultMessage: "Validation Error",
        }),
        subtitle: intl.formatMessage({
          id: "validation.template.override.logo.file.required",
          defaultMessage: "Choose an image file before upload.",
        }),
      });
      return;
    }
    try {
      if (isLeft) setUploadingLeftLogo(true);
      else setUploadingRightLogo(true);
      const uploaded = await uploadValidationTemplateImage(
        file,
        `${form.report || "report"}-${side}-logo`,
      );
      const constantKey = isLeft ? "leftHeaderImageName" : "rightHeaderImageName";
      const resolvedImageKey =
        String(uploaded?.key || "").trim() ||
        (uploaded?.id ? `imageId:${uploaded.id}` : "");
      if (!resolvedImageKey) {
        throw new Error(
          intl.formatMessage({
            id: "validation.template.override.logo.upload.key.missing",
            defaultMessage:
              "Upload succeeded but no image key was returned. Please try again.",
          }),
        );
      }
      setConstantValue(constantKey, resolvedImageKey);
      setMessage({
        kind: "success",
        title: intl.formatMessage({
          id: "validation.template.override.logo.upload.success.title",
          defaultMessage: "Image Uploaded",
        }),
        subtitle: `${intl.formatMessage({
          id: "validation.template.override.logo.upload.success.body",
          defaultMessage:
            "Logo uploaded and linked to constants. Save the override to persist this association.",
        })} (${constantKey}=${resolvedImageKey})`,
      });
    } catch (error) {
      setMessage({
        kind: "error",
        title: intl.formatMessage({
          id: "validation.template.override.save.error.title",
          defaultMessage: "Validation Error",
        }),
        subtitle: error?.message || "Failed to upload image.",
      });
    } finally {
      if (isLeft) setUploadingLeftLogo(false);
      else setUploadingRightLogo(false);
    }
  };

  return (
    <div className="adminPageContent validation-template-override-page">
      <h1>
        <FormattedMessage
          id="validation.template.override.title"
          defaultMessage="Validation Template Overrides"
        />
      </h1>
      <p className="validation-template-override-subtitle">
        <FormattedMessage
          id="validation.template.override.subtitle"
          defaultMessage="Configure report template mapping by test without editing JSON manually."
        />
      </p>

      {message && (
        <InlineNotification
          className="validation-template-override-alert"
          kind={message.kind}
          title={message.title}
          subtitle={message.subtitle}
          lowContrast
          onCloseButtonClick={() => setMessage(null)}
        />
      )}

      <div className="validation-template-override-grid">
        <section className="validation-template-override-list">
          <div className="validation-template-override-section-header">
            <h2>
              <FormattedMessage
                id="validation.template.override.list.title"
                defaultMessage="Existing Overrides"
              />
            </h2>
            <Button
              size="sm"
              kind="tertiary"
              renderIcon={Add}
              onClick={onCreateNew}
            >
              <FormattedMessage
                id="validation.template.override.new"
                defaultMessage="New"
              />
            </Button>
          </div>
          <Table size="sm">
            <TableHead>
              <TableRow>
                <TableHeader>
                  <FormattedMessage
                    id="validation.template.override.list.name"
                    defaultMessage="Name"
                  />
                </TableHeader>
                <TableHeader>
                  <FormattedMessage
                    id="validation.template.override.list.report"
                    defaultMessage="Report"
                  />
                </TableHeader>
                <TableHeader>
                  <FormattedMessage
                    id="validation.template.override.list.active"
                    defaultMessage="Active"
                  />
                </TableHeader>
                <TableHeader>
                  <FormattedMessage
                    id="validation.template.override.list.action"
                    defaultMessage="Action"
                  />
                </TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {!loading &&
                overrides.map((override) => (
                  <TableRow key={override.id}>
                    <TableCell>{override.name || override.id}</TableCell>
                    <TableCell>{override.report}</TableCell>
                    <TableCell>
                      {override.isActive
                        ? intl.formatMessage({
                            id: "common.yes",
                            defaultMessage: "Yes",
                          })
                        : intl.formatMessage({
                            id: "common.no",
                            defaultMessage: "No",
                          })}
                    </TableCell>
                    <TableCell>
                      <Button
                        kind="ghost"
                        size="sm"
                        renderIcon={Edit}
                        onClick={() => onSelectOverride(override)}
                      >
                        <FormattedMessage
                          id="validation.template.override.edit"
                          defaultMessage="Edit"
                        />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
            </TableBody>
          </Table>
        </section>

        <section className="validation-template-override-form">
          <h2>
            <FormattedMessage
              id="validation.template.override.form.title"
              defaultMessage="Override Detail"
            />
          </h2>
          <div className="validation-template-override-form-fields">
            <TextInput
              id="override-name"
              labelText={intl.formatMessage({
                id: "validation.template.override.form.name",
                defaultMessage: "Display Name",
              })}
              value={form.name}
              onChange={(e) => setForm((prev) => ({ ...prev, name: e.target.value }))}
            />
            <Select
              id="override-report-select"
              labelText={intl.formatMessage({
                id: "validation.template.override.form.report",
                defaultMessage: "Report Key",
              })}
              value={form.report}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, report: e.target.value }))
              }
            >
              <SelectItem
                value=""
                text={intl.formatMessage({
                  id: "validation.template.override.form.report.placeholder",
                  defaultMessage: "Select a report",
                })}
              />
              {reportOptions.map((option) => (
                <SelectItem
                  key={option.id}
                  value={option.id}
                  text={option.value || option.id}
                />
              ))}
              {form.report &&
                !reportOptions.some((option) => option.id === form.report) && (
                  <SelectItem value={form.report} text={form.report} />
                )}
            </Select>
            <MultiSelect
              id="override-test-ids"
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
              onChange={({ selectedItems }) => {
                setForm((prev) => ({
                  ...prev,
                  testIds: selectedItems.map((item) => item.id),
                }));
              }}
            />
            <TextInput
              id="override-test-codes"
              labelText={intl.formatMessage({
                id: "validation.template.override.form.testCodes",
                defaultMessage: "Test Codes (optional)",
              })}
              helperText={intl.formatMessage({
                id: "validation.template.override.form.testCodes.helper",
                defaultMessage: "Comma separated. Example: 81234, 81271",
              })}
              value={form.testCodesText}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, testCodesText: e.target.value }))
              }
            />
            <TextArea
              id="override-description"
              labelText={intl.formatMessage({
                id: "validation.template.override.form.description",
                defaultMessage: "Description",
              })}
              value={form.description}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, description: e.target.value }))
              }
            />
            <Checkbox
              id="override-active"
              labelText={intl.formatMessage({
                id: "validation.template.override.form.active",
                defaultMessage: "Active",
              })}
              checked={Boolean(form.isActive)}
              onChange={(checked) => setForm((prev) => ({ ...prev, isActive: checked }))}
            />
          </div>

          <div className="validation-template-override-mapping-section">
            <div className="validation-template-override-mapping-header">
              <h3>
                <FormattedMessage
                  id="validation.template.override.form.section.fields"
                  defaultMessage="Section Fields (ordered)"
                />
              </h3>
              <Button
                size="sm"
                kind="ghost"
                renderIcon={Add}
                onClick={() =>
                  setForm((prev) => ({
                    ...prev,
                    sectionFieldRows: [
                      ...(prev.sectionFieldRows || []),
                      emptySectionFieldRow(),
                    ],
                  }))
                }
              >
                <FormattedMessage
                  id="validation.template.override.add.row"
                  defaultMessage="Add row"
                />
              </Button>
            </div>
            {(form.sectionFieldRows || []).map((row, index) => (
              <div className="section-field-row" key={`section-field-${index}`}>
                <TextInput
                  id={`section-field-key-${index}`}
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.section.key",
                    defaultMessage: "Field key",
                  })}
                  value={row.key || ""}
                  onChange={(e) =>
                    setForm((prev) => {
                      const rows = [...(prev.sectionFieldRows || [])];
                      rows[index] = { ...rows[index], key: e.target.value };
                      return { ...prev, sectionFieldRows: rows };
                    })
                  }
                />
                <Select
                  id={`section-field-section-${index}`}
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.section",
                    defaultMessage: "Section",
                  })}
                  value={row.section || ""}
                  onChange={(e) =>
                    setForm((prev) => {
                      const rows = [...(prev.sectionFieldRows || [])];
                      rows[index] = { ...rows[index], section: e.target.value };
                      return { ...prev, sectionFieldRows: rows };
                    })
                  }
                >
                  <SelectItem value="" text="" />
                  {sectionOptions.map((section) => (
                    <SelectItem key={section} value={section} text={section} />
                  ))}
                </Select>
                <TextInput
                  id={`section-field-label-${index}`}
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.section.label",
                    defaultMessage: "Label",
                  })}
                  value={row.label || ""}
                  onChange={(e) =>
                    setForm((prev) => {
                      const rows = [...(prev.sectionFieldRows || [])];
                      rows[index] = { ...rows[index], label: e.target.value };
                      return { ...prev, sectionFieldRows: rows };
                    })
                  }
                />
                <Select
                  id={`section-field-source-${index}`}
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.section.source",
                    defaultMessage: "Source",
                  })}
                  value={row.source || ""}
                  onChange={(e) =>
                    setForm((prev) => {
                      const rows = [...(prev.sectionFieldRows || [])];
                      rows[index] = { ...rows[index], source: e.target.value };
                      return { ...prev, sectionFieldRows: rows };
                    })
                  }
                >
                  <SelectItem value="" text="" />
                  {sourceOptions.map((source) => (
                    <SelectItem
                      key={source.id}
                      value={source.id}
                      text={source.value || source.id}
                    />
                  ))}
                  {row.source &&
                    !sourceOptions.some((source) => source.id === row.source) && (
                      <SelectItem value={row.source} text={row.source} />
                    )}
                </Select>
                {additionalSourcePlaceholders.includes(row.source) && (
                  <>
                    {additionalSourceOptions.filter((source) =>
                      row.source === ADDITIONAL_TEST_SOURCE_PLACEHOLDER
                        ? source.id.startsWith("additional.")
                        : row.source === ADDITIONAL_ORDER_SOURCE_PLACEHOLDER
                          ? source.id.startsWith("orderAdditional.")
                          : source.id.startsWith("sampleAdditional."),
                    ).length > 0 ? (
                      <Select
                        id={`section-field-additional-key-select-${index}`}
                        labelText={intl.formatMessage({
                          id: "validation.template.override.form.section.additional.key",
                          defaultMessage: "Additional key",
                        })}
                        value={row.additionalKey || ""}
                        onChange={(e) =>
                          setForm((prev) => {
                            const rows = [...(prev.sectionFieldRows || [])];
                            rows[index] = { ...rows[index], additionalKey: e.target.value };
                            return { ...prev, sectionFieldRows: rows };
                          })
                        }
                      >
                        <SelectItem value="" text="" />
                        {additionalSourceOptions
                          .filter((source) =>
                            row.source === ADDITIONAL_TEST_SOURCE_PLACEHOLDER
                              ? source.id.startsWith("additional.")
                              : row.source === ADDITIONAL_ORDER_SOURCE_PLACEHOLDER
                                ? source.id.startsWith("orderAdditional.")
                                : source.id.startsWith("sampleAdditional."),
                          )
                          .map((source) => {
                          const prefix = row.source.replace("<field_key>", "");
                          const key = source.id.substring(prefix.length);
                          return (
                            <SelectItem
                              key={source.id}
                              value={key}
                              text={source.value || key}
                            />
                          );
                          })}
                      </Select>
                    ) : (
                      <TextInput
                        id={`section-field-additional-key-${index}`}
                        labelText={intl.formatMessage({
                          id: "validation.template.override.form.section.additional.key",
                          defaultMessage: "Additional key",
                        })}
                        helperText={intl.formatMessage({
                          id: "validation.template.override.form.section.additional.key.helper",
                          defaultMessage:
                            "No selectable fields loaded for this source yet. Verify selected test IDs/codes, or use Order Additional Field source if your fields come from order entry.",
                        })}
                        placeholder={intl.formatMessage({
                          id: "validation.template.override.form.section.additional.key.placeholder",
                          defaultMessage: "example: dmpk_alelo_1",
                        })}
                        value={row.additionalKey || ""}
                        onChange={(e) =>
                          setForm((prev) => {
                            const rows = [...(prev.sectionFieldRows || [])];
                            rows[index] = { ...rows[index], additionalKey: e.target.value };
                            return { ...prev, sectionFieldRows: rows };
                          })
                        }
                      />
                    )}
                  </>
                )}
                <TextInput
                  id={`section-field-order-${index}`}
                  type="number"
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.section.order",
                    defaultMessage: "Order",
                  })}
                  value={row.order || 0}
                  onChange={(e) =>
                    setForm((prev) => {
                      const rows = [...(prev.sectionFieldRows || [])];
                      rows[index] = { ...rows[index], order: Number(e.target.value || 0) };
                      return { ...prev, sectionFieldRows: rows };
                    })
                  }
                />
                <Checkbox
                  id={`section-field-enabled-${index}`}
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.section.enabled",
                    defaultMessage: "Enabled",
                  })}
                  checked={row.enabled !== false}
                  onChange={(checked) =>
                    setForm((prev) => {
                      const rows = [...(prev.sectionFieldRows || [])];
                      rows[index] = { ...rows[index], enabled: checked };
                      return { ...prev, sectionFieldRows: rows };
                    })
                  }
                />
                <Button
                  kind="ghost"
                  size="sm"
                  renderIcon={Subtract}
                  onClick={() =>
                    setForm((prev) => {
                      const rows = (prev.sectionFieldRows || []).filter(
                        (_, rowIndex) => rowIndex !== index,
                      );
                      return {
                        ...prev,
                        sectionFieldRows:
                          rows.length > 0 ? rows : [emptySectionFieldRow()],
                      };
                    })
                  }
                />
              </div>
            ))}
          </div>

          <div className="validation-template-override-mapping-section">
            <div className="validation-template-override-mapping-header">
              <h3>
                <FormattedMessage
                  id="validation.template.override.form.slots"
                  defaultMessage="Slot Mapping"
                />
              </h3>
              <Button size="sm" kind="ghost" renderIcon={Add} onClick={() => addRow("slotRows")}>
                <FormattedMessage id="validation.template.override.add.row" defaultMessage="Add row" />
              </Button>
            </div>
            {form.slotRows.map((row, index) => (
              <div className="mapping-row" key={`slot-${index}`}>
                <TextInput
                  id={`slot-key-${index}`}
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.mapping.key",
                    defaultMessage: "Slot key",
                  })}
                  value={row.key}
                  onChange={(e) => updateRow("slotRows", index, "key", e.target.value)}
                />
                <Select
                  id={`slot-source-${index}`}
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.mapping.value",
                    defaultMessage: "Source field",
                  })}
                  value={row.value || ""}
                  onChange={(e) => {
                    updateRow("slotRows", index, "value", e.target.value);
                    if (!additionalSourcePlaceholders.includes(e.target.value)) {
                      updateRow("slotRows", index, "additionalKey", "");
                    }
                  }}
                >
                  <SelectItem value="" text="" />
                  {sourceOptions.map((source) => (
                    <SelectItem
                      key={source.id}
                      value={source.id}
                      text={source.value || source.id}
                    />
                  ))}
                  {row.value &&
                    !sourceOptions.some((source) => source.id === row.value) && (
                      <SelectItem value={row.value} text={row.value} />
                    )}
                </Select>
                {additionalSourcePlaceholders.includes(row.value) &&
                  (additionalSourceOptions.filter((source) =>
                    row.value === ADDITIONAL_TEST_SOURCE_PLACEHOLDER
                      ? source.id.startsWith("additional.")
                      : row.value === ADDITIONAL_ORDER_SOURCE_PLACEHOLDER
                        ? source.id.startsWith("orderAdditional.")
                        : source.id.startsWith("sampleAdditional."),
                  ).length > 0 ? (
                    <Select
                      id={`slot-additional-key-select-${index}`}
                      labelText={intl.formatMessage({
                        id: "validation.template.override.form.section.additional.key",
                        defaultMessage: "Additional key",
                      })}
                      value={row.additionalKey || ""}
                      onChange={(e) => updateRow("slotRows", index, "additionalKey", e.target.value)}
                    >
                      <SelectItem value="" text="" />
                      {additionalSourceOptions
                        .filter((source) =>
                          row.value === ADDITIONAL_TEST_SOURCE_PLACEHOLDER
                            ? source.id.startsWith("additional.")
                            : row.value === ADDITIONAL_ORDER_SOURCE_PLACEHOLDER
                              ? source.id.startsWith("orderAdditional.")
                              : source.id.startsWith("sampleAdditional."),
                        )
                        .map((source) => {
                          const prefix = row.value.replace("<field_key>", "");
                          const key = source.id.substring(prefix.length);
                          return (
                            <SelectItem
                              key={source.id}
                              value={key}
                              text={source.value || key}
                            />
                          );
                        })}
                    </Select>
                  ) : (
                    <TextInput
                      id={`slot-additional-key-${index}`}
                      labelText={intl.formatMessage({
                        id: "validation.template.override.form.section.additional.key",
                        defaultMessage: "Additional key",
                      })}
                      helperText={intl.formatMessage({
                        id: "validation.template.override.form.section.additional.key.helper",
                        defaultMessage:
                          "No selectable fields loaded for this source yet. Verify selected test IDs/codes, or use Order Additional Field source if your fields come from order entry.",
                      })}
                      placeholder={intl.formatMessage({
                        id: "validation.template.override.form.section.additional.key.placeholder",
                        defaultMessage: "example: dmpk_alelo_1",
                      })}
                      value={row.additionalKey || ""}
                      onChange={(e) => updateRow("slotRows", index, "additionalKey", e.target.value)}
                    />
                  ))}
                <Button
                  kind="ghost"
                  size="sm"
                  renderIcon={Subtract}
                  onClick={() => removeRow("slotRows", index)}
                />
              </div>
            ))}
          </div>

          <div className="validation-template-override-mapping-section">
            <div className="validation-template-override-mapping-header">
              <h3>
                <FormattedMessage
                  id="validation.template.override.form.constants"
                  defaultMessage="Constants"
                />
              </h3>
              <div className="validation-template-override-header-actions">
                <Button size="sm" kind="ghost" onClick={applyLogoConstantPreset}>
                  <FormattedMessage
                    id="validation.template.override.constants.logo.preset"
                    defaultMessage="Add Logo Keys"
                  />
                </Button>
                <Button
                  size="sm"
                  kind="ghost"
                  renderIcon={Add}
                  onClick={() => addRow("constantRows")}
                >
                  <FormattedMessage
                    id="validation.template.override.add.row"
                    defaultMessage="Add row"
                  />
                </Button>
              </div>
            </div>
            <p className="validation-template-override-helper">
              <FormattedMessage
                id="validation.template.override.constants.logo.helper"
                defaultMessage="Use constants leftHeaderImageName / rightHeaderImageName with DB image names (example: headerLeftImage, headerRightImage) to override logos for this report only."
              />
            </p>
            <div className="validation-template-override-logo-upload-grid">
              <div className="validation-template-override-logo-upload-item">
                <label htmlFor="validation-template-override-left-logo-file">
                  {intl.formatMessage({
                    id: "validation.template.override.logo.left",
                    defaultMessage: "Left Logo Image",
                  })}
                </label>
                <input
                  id="validation-template-override-left-logo-file"
                  type="file"
                  accept=".png,.jpg,.jpeg,.gif,image/png,image/jpeg,image/gif"
                  onChange={(e) => setLeftLogoFile(e?.target?.files?.[0] || null)}
                />
                <Button
                  size="sm"
                  kind="tertiary"
                  disabled={uploadingLeftLogo}
                  onClick={() => onUploadLogo("left")}
                >
                  {uploadingLeftLogo
                    ? intl.formatMessage({
                        id: "validation.template.override.logo.uploading",
                        defaultMessage: "Uploading...",
                      })
                    : intl.formatMessage({
                        id: "validation.template.override.logo.upload.left",
                        defaultMessage: "Upload Left Logo",
                      })}
                </Button>
              </div>
              <div className="validation-template-override-logo-upload-item">
                <label htmlFor="validation-template-override-right-logo-file">
                  {intl.formatMessage({
                    id: "validation.template.override.logo.right",
                    defaultMessage: "Right Logo Image",
                  })}
                </label>
                <input
                  id="validation-template-override-right-logo-file"
                  type="file"
                  accept=".png,.jpg,.jpeg,.gif,image/png,image/jpeg,image/gif"
                  onChange={(e) => setRightLogoFile(e?.target?.files?.[0] || null)}
                />
                <Button
                  size="sm"
                  kind="tertiary"
                  disabled={uploadingRightLogo}
                  onClick={() => onUploadLogo("right")}
                >
                  {uploadingRightLogo
                    ? intl.formatMessage({
                        id: "validation.template.override.logo.uploading",
                        defaultMessage: "Uploading...",
                      })
                    : intl.formatMessage({
                        id: "validation.template.override.logo.upload.right",
                        defaultMessage: "Upload Right Logo",
                      })}
                </Button>
              </div>
            </div>
            {form.constantRows.map((row, index) => (
              <div className="mapping-row" key={`constant-${index}`}>
                <TextInput
                  id={`constant-key-${index}`}
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.mapping.key",
                    defaultMessage: "Slot key",
                  })}
                  value={row.key}
                  onChange={(e) => updateRow("constantRows", index, "key", e.target.value)}
                />
                <TextInput
                  id={`constant-value-${index}`}
                  labelText={intl.formatMessage({
                    id: "validation.template.override.form.mapping.value",
                    defaultMessage: "Source field",
                  })}
                  value={row.value}
                  onChange={(e) => updateRow("constantRows", index, "value", e.target.value)}
                />
                <Button
                  kind="ghost"
                  size="sm"
                  renderIcon={Subtract}
                  onClick={() => removeRow("constantRows", index)}
                />
              </div>
            ))}
          </div>

          <div className="validation-template-override-actions">
            <Button
              kind="secondary"
              renderIcon={Save}
              onClick={onSave}
              disabled={saving}
            >
              <FormattedMessage id="validation.template.override.save" defaultMessage="Save" />
            </Button>
          </div>
        </section>
      </div>
    </div>
  );
};

export default ValidationTemplateOverrideConfig;
