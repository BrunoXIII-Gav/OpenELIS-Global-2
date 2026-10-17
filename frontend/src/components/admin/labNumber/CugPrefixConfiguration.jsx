import React, { useCallback, useEffect, useState } from "react";
import {
  Button,
  Column,
  Form,
  Grid,
  Heading,
  InlineNotification,
  Loading,
  Section,
  Stack,
  TextInput,
} from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  getFromOpenElisServer,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils";

const endpoint = "/rest/sample-cug/prefix-settings";
const MAX_PREFIX = "9223372036854775806";
export const validCugStart = (value) => {
  if (!/^[0-9]+$/.test(value)) return false;
  const normalized = value.replace(/^0+/, "");
  return (
    normalized.length > 0 &&
    (normalized.length < MAX_PREFIX.length ||
      (normalized.length === MAX_PREFIX.length && normalized <= MAX_PREFIX))
  );
};

const CugPrefixConfiguration = () => {
  const intl = useIntl();
  const [settings, setSettings] = useState(null);
  const [start, setStart] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState(null);
  const load = useCallback(() => {
    setLoading(true);
    getFromOpenElisServer(endpoint, (body) => {
      if (body?.startPrefix) {
        setSettings(body);
        setStart(body.startPrefix);
        setMessage(null);
      } else setMessage({ kind: "error", key: "cug.prefix.error.load" });
      setLoading(false);
    });
  }, []);
  useEffect(() => {
    load();
  }, [load]);
  const save = (event) => {
    event.preventDefault();
    if (!validCugStart(start) || saving) return;
    setSaving(true);
    setMessage(null);
    putToOpenElisServerFullResponse(
      endpoint,
      JSON.stringify({ startPrefix: start }),
      async (response) => {
        let body;
        try {
          body = await response?.json();
        } catch (_) {
          /* Show a localized failure. */
        }
        if (response?.ok && body?.startPrefix) {
          setSettings(body);
          setStart(body.startPrefix);
          setMessage({ kind: "success", key: "cug.prefix.saved" });
        } else {
          setMessage({
            kind: "error",
            key: intl.messages[body?.error]
              ? body.error
              : "cug.prefix.error.save",
          });
        }
        setSaving(false);
      },
    );
  };
  return (
    <Section className="orderLegendBody">
      <Stack gap={5}>
        <Heading>
          <FormattedMessage id="cug.prefix.title" />
        </Heading>
        <p>
          <FormattedMessage id="cug.prefix.help" />
        </p>
        {message && (
          <InlineNotification
            kind={message.kind}
            title={intl.formatMessage({ id: message.key })}
            hideCloseButton
          />
        )}
        {loading && (
          <Loading
            withOverlay={false}
            description={intl.formatMessage({ id: "cug.prefix.loading" })}
          />
        )}
        {!loading && !settings && (
          <Button kind="tertiary" onClick={load}>
            <FormattedMessage id="cug.prefix.retry" />
          </Button>
        )}
        {settings && (
          <Form onSubmit={save}>
            <Grid fullWidth>
              <Column lg={16} md={8} sm={4}>
                <p>
                  <FormattedMessage id="cug.prefix.next" />:{" "}
                  <strong>
                    {settings.nextAvailablePrefix ||
                      intl.formatMessage({ id: "cug.prefix.exhausted" })}
                  </strong>
                </p>
              </Column>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="cug-prefix-start"
                  inputMode="numeric"
                  labelText={intl.formatMessage({ id: "cug.prefix.start" })}
                  helperText={intl.formatMessage({
                    id: "cug.prefix.start.help",
                  })}
                  value={start}
                  disabled={saving || loading}
                  onChange={(event) => setStart(event.target.value)}
                  invalid={start !== "" && !validCugStart(start)}
                  invalidText={intl.formatMessage({
                    id: "cug.prefix.error.invalid",
                  })}
                />
              </Column>
              <Column lg={16} md={8} sm={4}>
                <Button
                  type="submit"
                  disabled={saving || loading || !validCugStart(start)}
                >
                  <FormattedMessage id="cug.prefix.apply" />
                </Button>
                {saving && (
                  <Loading
                    small
                    withOverlay={false}
                    description={intl.formatMessage({
                      id: "cug.prefix.saving",
                    })}
                  />
                )}
              </Column>
            </Grid>
          </Form>
        )}
      </Stack>
    </Section>
  );
};
export default CugPrefixConfiguration;
