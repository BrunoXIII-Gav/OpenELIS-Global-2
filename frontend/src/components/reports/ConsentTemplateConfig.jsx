import React, { useEffect, useMemo, useState } from "react";
import {
  Button,
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
    saveConsentTemplateConfig(config, (savedConfig, response) => {
      const ok = response?.ok;
      if (ok) {
        setConfig(normalizeConfig(savedConfig || config));
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
          <Button kind="tertiary" onClick={handleDownload} disabled={parsing}>
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
                      {sourceOptions.map((source) => (
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
