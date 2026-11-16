import React, { useEffect, useMemo, useState } from "react";
import {
  Button,
  Checkbox,
  TextInput,
  FileUploaderButton,
  Stack,
  InlineNotification,
  Loading,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  parseConsentTemplate,
  downloadConsentTemplate,
  getConsentTemplateConfig,
  getConsentTemplateOptions,
  saveConsentTemplateConfig,
  getConsentTemplates,
  saveConsentTemplate,
} from "../../services/consentTemplateService";
import "./ConsentTemplateConfig.scss";

const defaultConfig = {
  fields: {},
};

const normalizeConfig = (config) => {
  if (!config) {
    return { ...defaultConfig };
  }
  const fields =
    config.fields && typeof config.fields === "object" ? config.fields : {};
  return { ...config, fields };
};

const ConsentTemplateConfig = () => {
  const intl = useIntl();
  const [config, setConfig] = useState(defaultConfig);
  const [templates, setTemplates] = useState([]);
  const [creating, setCreating] = useState(false);
  const requestVersion = React.useRef(0);
  const refreshTemplates = () =>
    getConsentTemplates((items) =>
      setTemplates(Array.isArray(items) ? items : []),
    );
  const [fieldOptions, setFieldOptions] = useState([]);
  const [sourceOptions, setSourceOptions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [parsing, setParsing] = useState(false);
  const [message, setMessage] = useState(null);

  useEffect(() => {
    let isMounted = true;
    let pending = 2;

    const complete = () => {
      pending -= 1;
      if (pending <= 0 && isMounted) {
        setLoading(false);
      }
    };

    getConsentTemplates((items) => {
      if (isMounted) setTemplates(Array.isArray(items) ? items : []);
    });

    getConsentTemplateOptions((options) => {
      if (!isMounted) {
        return;
      }
      setSourceOptions(Array.isArray(options?.sources) ? options.sources : []);
      complete();
    });

    getConsentTemplateConfig((data) => {
      if (!isMounted) {
        return;
      }
      setConfig(normalizeConfig(data));
      setFieldOptions(data?.parameters || []);
      complete();
    });

    return () => {
      isMounted = false;
    };
  }, []);

  useEffect(() => {
    const refresh = () =>
      getConsentTemplateOptions((options) => {
        setSourceOptions(
          Array.isArray(options?.sources) ? options.sources : [],
        );
      });
    window.addEventListener("focus", refresh);
    return () => window.removeEventListener("focus", refresh);
  }, []);

  const selectTemplate = (id) => {
    const version = ++requestVersion.current;
    setCreating(false);
    setLoading(true);
    setMessage(null);
    getConsentTemplateConfig((data) => {
      if (version !== requestVersion.current) return;
      if (data?.id) {
        setConfig(normalizeConfig(data));
        setFieldOptions(data.parameters || []);
      } else
        setMessage({
          kind: "error",
          title: intl.formatMessage({ id: "consent.template.error.notFound" }),
        });
      setLoading(false);
    }, id);
  };

  const createTemplate = () => {
    ++requestVersion.current;
    setCreating(true);
    setConfig({ fields: {}, name: "", isActive: true });
    setFieldOptions([]);
    setMessage(null);
  };

  const handleUpload = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    setParsing(true);
    setMessage(null);
    try {
      const template = await parseConsentTemplate(file);
      setConfig((previous) => ({
        ...previous,
        ...template,
        id: previous.id,
        isActive: previous.isActive,
        name: previous.name,
        fields: Object.fromEntries(
          (template.parameters || []).map((parameter) => [
            parameter.id,
            previous.fields?.[parameter.id] || "",
          ]),
        ),
      }));
      setFieldOptions(template.parameters || []);
    } catch (error) {
      setMessage({
        kind: "error",
        title: intl.formatMessage({
          id: intl.messages[error.message]
            ? error.message
            : "consent.template.error.invalid",
        }),
      });
    } finally {
      setParsing(false);
      event.target.value = "";
    }
  };

  const handleDownload = async () => {
    try {
      // Download the currently selected template, including an upload not yet saved.
      const blob = config.templateContent
        ? new Blob([config.templateContent], { type: "application/xml" })
        : await downloadConsentTemplate();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = config.templateFilename || "consent-template.jrxml";
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      setMessage({
        kind: "error",
        title: intl.formatMessage({ id: "consent.template.error.download" }),
      });
    }
  };

  const fieldRows = useMemo(() => fieldOptions || [], [fieldOptions]);

  const resolveOptionLabel = (option) => {
    if (!option) {
      return "";
    }
    if (option.labelKey) {
      if (option.label) {
        return intl.formatMessage(
          { id: option.labelKey },
          { name: option.label },
        );
      }
      return intl.formatMessage({ id: option.labelKey });
    }
    return option.label || option.name || "";
  };

  const handleSourceChange = (fieldId, value) => {
    setConfig((prev) => ({
      ...prev,
      fields: {
        ...(prev.fields || {}),
        [fieldId]: value,
      },
    }));
  };

  const handleSave = () => {
    setSaving(true);
    setMessage(null);
    if (creating && (!config.name?.trim() || !config.templateContent)) {
      setSaving(false);
      setMessage({
        kind: "error",
        title: intl.formatMessage({
          id: !config.name?.trim()
            ? "consent.template.error.name"
            : "consent.template.error.file",
        }),
      });
      return;
    }
    const save =
      !creating && (!config.id || config.id === "RPT-CONSENT-TEMPLATE")
        ? saveConsentTemplateConfig
        : saveConsentTemplate;
    save(config, (savedConfig, response) => {
      const ok = response?.ok;
      if (ok) {
        setConfig(normalizeConfig(savedConfig || config));
        setCreating(false);
        refreshTemplates();
        setMessage({
          kind: "success",
          title: intl.formatMessage({
            id: "consent.template.config.saveSuccess",
          }),
        });
      } else {
        setMessage({
          kind: "error",
          title: intl.formatMessage({
            id: "consent.template.config.saveError",
          }),
        });
      }
      setSaving(false);
    });
  };

  return (
    <div className="adminPageContent consent-template-config-page">
      <header className="consent-template-config-header">
        <h1>
          <FormattedMessage id="consent.template.config.title" />
        </h1>
        <p className="consent-template-config-subtitle">
          <FormattedMessage id="consent.template.config.subtitle" />
        </p>
      </header>

      {message && (
        <InlineNotification
          className="consent-template-config-alert"
          kind={message.kind}
          title={message.title}
          hideCloseButton={false}
          onCloseButtonClick={() => setMessage(null)}
        />
      )}

      {loading && <Loading withOverlay={false} />}

      {!loading && (
        <Stack gap={5}>
          <Select
            id="consent-template-selector"
            labelText={intl.formatMessage({ id: "consent.template.select" })}
            value={creating ? "" : config.id || "RPT-CONSENT-TEMPLATE"}
            disabled={saving || parsing}
            onChange={(event) => selectTemplate(event.target.value)}
          >
            {creating && (
              <SelectItem
                value=""
                text={intl.formatMessage({ id: "consent.template.new" })}
              />
            )}
            {templates.map((template) => (
              <SelectItem
                key={template.id}
                value={template.id}
                text={`${template.name}${template.isActive === false ? ` (${intl.formatMessage({ id: "label.inactive" })})` : ""}`}
              />
            ))}
          </Select>
          <Button
            kind="tertiary"
            disabled={saving || parsing}
            onClick={createTemplate}
          >
            <FormattedMessage id="consent.template.new" />
          </Button>
          <TextInput
            id="consent-template-name"
            maxLength={200}
            labelText={intl.formatMessage({ id: "consent.template.name" })}
            value={config.name || ""}
            disabled={saving || parsing}
            onChange={(event) => {
              const name = event.target.value;
              setConfig((previous) => ({ ...previous, name }));
            }}
          />
          <Checkbox
            id="consent-template-active"
            labelText={intl.formatMessage({ id: "label.active" })}
            checked={config.isActive !== false}
            disabled={saving || parsing}
            onChange={(_event, { checked }) =>
              setConfig((previous) => ({ ...previous, isActive: checked }))
            }
          />
          <p>
            <FormattedMessage id="consent.template.upload.help" />
          </p>
          <FileUploaderButton
            accept={[".jrxml"]}
            labelText={intl.formatMessage({ id: "consent.template.upload" })}
            onChange={handleUpload}
            disabled={parsing || saving}
            multiple={false}
          />
          <p>{config.templateFilename}</p>
          <Button
            kind="tertiary"
            onClick={handleDownload}
            disabled={
              parsing || saving || (creating && !config.templateContent)
            }
          >
            <FormattedMessage id="consent.template.download" />
          </Button>
          {parsing && (
            <Loading
              withOverlay={false}
              description={intl.formatMessage({
                id: "consent.template.parsing",
              })}
            />
          )}
          <Table size="sm" className="consent-template-config-table">
            <TableHead>
              <TableRow>
                <TableHeader>
                  <FormattedMessage id="consent.template.config.field" />
                </TableHeader>
                <TableHeader>
                  <FormattedMessage id="consent.template.config.source" />
                </TableHeader>
              </TableRow>
            </TableHead>
            <TableBody>
              {fieldRows.map((field) => (
                <TableRow key={field.id}>
                  <TableCell>{`${field.kind === "field" ? "$F" : "$P"}{${field.name}}`}</TableCell>
                  <TableCell>
                    <Select
                      id={`consent-source-${field.id}`}
                      labelText={intl.formatMessage({
                        id: "consent.template.config.source",
                      })}
                      hideLabel
                      disabled={parsing || saving}
                      value={config.fields?.[field.id] || ""}
                      onChange={(event) =>
                        handleSourceChange(field.id, event.target.value)
                      }
                    >
                      <SelectItem
                        value=""
                        text={intl.formatMessage({
                          id: "consent.template.config.selectSource",
                        })}
                      />
                      {sourceOptions
                        .filter(
                          (source) =>
                            (field.valueType === "image") ===
                            (source.valueType === "image"),
                        )
                        .map((source) => (
                          <SelectItem
                            key={source.id}
                            value={source.id}
                            text={resolveOptionLabel(source)}
                          />
                        ))}
                    </Select>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Stack>
      )}

      <div className="consent-template-config-actions">
        <Button
          kind="primary"
          onClick={handleSave}
          disabled={saving || loading || parsing}
        >
          {saving
            ? intl.formatMessage({ id: "consent.template.config.saving" })
            : intl.formatMessage({ id: "consent.template.config.save" })}
        </Button>
      </div>
    </div>
  );
};

export default ConsentTemplateConfig;
