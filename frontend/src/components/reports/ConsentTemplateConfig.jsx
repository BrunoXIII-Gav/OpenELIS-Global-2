import React, { useEffect, useMemo, useState } from "react";
import {
  Button,
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
      setFieldOptions(Array.isArray(options?.fields) ? options.fields : []);
      setSourceOptions(Array.isArray(options?.sources) ? options.sources : []);
      complete();
    });

    getConsentTemplateConfig((data) => {
      if (!isMounted) {
        return;
      }
      setConfig(normalizeConfig(data));
      complete();
    });

    return () => {
      isMounted = false;
    };
  }, []);

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
    return option.label || option.id || "";
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
    <div className="consent-template-config-page">
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
                <TableCell>{resolveOptionLabel(field)}</TableCell>
                <TableCell>
                  <Select
                    id={`consent-source-${field.id}`}
                    labelText={intl.formatMessage({
                      id: "consent.template.config.source",
                    })}
                    hideLabel
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
      )}

      <div className="consent-template-config-actions">
        <Button kind="primary" onClick={handleSave} disabled={saving || loading}>
          {saving
            ? intl.formatMessage({ id: "consent.template.config.saving" })
            : intl.formatMessage({ id: "consent.template.config.save" })}
        </Button>
      </div>
    </div>
  );
};

export default ConsentTemplateConfig;
