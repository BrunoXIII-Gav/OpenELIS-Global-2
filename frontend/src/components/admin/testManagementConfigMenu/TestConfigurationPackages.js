import React, { useCallback, useEffect, useRef, useState } from "react";
import { useIntl } from "react-intl";
import {
  Button,
  ButtonSet,
  Checkbox,
  Column,
  FileUploaderButton,
  Heading,
  Grid,
  InlineLoading,
  InlineNotification,
  MultiSelect,
  Select,
  SelectItem,
  Stack,
  TextInput,
  Tile,
} from "@carbon/react";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../../utils/Utils";
import "./TestConfigurationPackages.scss";

const BASE = "/rest/test-configuration-packages";
export const MAX_PACKAGE_BYTES = 5_000_000;
export function parseConfigurationFile(text) {
  if (new TextEncoder().encode(text).length > MAX_PACKAGE_BYTES)
    throw new Error("testPackage.issue.size");
  const bundle = JSON.parse(text);
  if (
    bundle?.format !== "openelis-test-configuration" ||
    bundle?.version !== 1 ||
    !Array.isArray(bundle.nodes) ||
    !Array.isArray(bundle.roots) ||
    !bundle.nodes.length ||
    bundle.nodes.length > 5000 ||
    !bundle.roots.length ||
    bundle.roots.length > 200
  )
    throw new Error("testPackage.issue.format");
  const keys = new Set();
  const object = (value) =>
    value !== null && typeof value === "object" && !Array.isArray(value);
  for (const node of bundle.nodes) {
    if (
      !object(node) ||
      typeof node.key !== "string" ||
      !/^[A-Za-z0-9_-]{1,100}$/.test(node.key) ||
      keys.has(node.key) ||
      typeof node.kind !== "string" ||
      typeof node.catalog !== "boolean" ||
      !object(node.values) ||
      !object(node.references) ||
      Object.values(node.values).some(
        (v) =>
          v !== null && !["string", "number", "boolean"].includes(typeof v),
      ) ||
      Object.values(node.references).some((v) => typeof v !== "string")
    )
      throw new Error("testPackage.issue.format");
    keys.add(node.key);
    if (
      node.kind === "Test" &&
      (typeof node.values.description !== "string" ||
        (node.values.localCode != null &&
          typeof node.values.localCode !== "string"))
    )
      throw new Error("testPackage.issue.format");
  }
  if (
    bundle.roots.some(
      (key) =>
        !keys.has(key) ||
        bundle.nodes.find((node) => node.key === key).kind !== "Test",
    ) ||
    new Set(bundle.roots).size !== bundle.roots.length
  )
    throw new Error("testPackage.issue.format");
  return bundle;
}

export default function TestConfigurationPackages() {
  const intl = useIntl();
  const msg = (id, values) =>
    intl.formatMessage({ id: `testPackage.${id}` }, values);
  const [tests, setTests] = useState([]);
  const [selected, setSelected] = useState("");
  const [incoming, setIncoming] = useState(null);
  const [incomingTest, setIncomingTest] = useState("");
  const [request, setRequest] = useState(null);
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);
  const [history, setHistory] = useState([]);
  const [unclassified, setUnclassified] = useState([]);
  const [originSelection, setOriginSelection] = useState([]);
  const [confirmed, setConfirmed] = useState(false);
  const [available, setAvailable] = useState(false);
  const generation = useRef(0);
  const load = useCallback((signal = null) => {
    getFromOpenElisServer(
      `${BASE}/unclassified-tests`,
      (data) => {
        if (!signal?.aborted) setUnclassified(Array.isArray(data) ? data : []);
      },
      signal,
    );
    getFromOpenElisServer(
      `${BASE}/tests`,
      (data) => {
        if (!signal?.aborted) setTests(Array.isArray(data) ? data : []);
      },
      signal,
    );
    getFromOpenElisServer(
      `${BASE}/history`,
      (data) => {
        if (!signal?.aborted) setHistory(Array.isArray(data) ? data : []);
      },
      signal,
    );
  }, []);
  useEffect(() => {
    const abort = new AbortController();
    getFromOpenElisServer(
      `${BASE}/availability`,
      (data) => {
        if (abort.signal.aborted) return;
        setAvailable(data?.available === true);
        if (data?.available === true) load(abort.signal);
      },
      abort.signal,
    );
    return () => {
      abort.abort();
      generation.current++;
    };
  }, [load]);

  const errorMessage = (code) =>
    intl.formatMessage({
      id: code?.startsWith("testPackage.issue.")
        ? code
        : "testPackage.issue.format",
    });
  const post = (path, body, success) => {
    setBusy(true);
    setNotice(null);
    const sequence = ++generation.current;
    postToOpenElisServerJsonResponse(
      `${BASE}/${path}`,
      JSON.stringify(body),
      (data) => {
        if (sequence !== generation.current) return;
        setBusy(false);
        if (!data || data.error || data.status >= 400) {
          setNotice({ kind: "error", text: errorMessage(data?.error) });
          return;
        }
        success(data);
      },
    );
  };
  const change = (next) => {
    generation.current++;
    setRequest(next);
    setPreview(null);
    setConfirmed(false);
    setNotice(null);
  };
  const begin = (bundle, copy = false) => {
    setIncoming(null);
    setIncomingTest("");
    change({ bundle, copy, catalogs: {}, overrides: {} });
  };
  const prepare = (bundle, testKey) =>
    post("prepare", { bundle, testKey }, (standalone) => begin(standalone));
  const readFile = async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;
    try {
      if (file.size > MAX_PACKAGE_BYTES)
        throw new Error("testPackage.issue.size");
      const bundle = parseConfigurationFile(await file.text());
      change(null);
      setIncoming(null);
      setIncomingTest("");
      if (bundle.roots.length === 1) prepare(bundle, bundle.roots[0]);
      else setIncoming(bundle);
    } catch (error) {
      setNotice({ kind: "error", text: errorMessage(error.message) });
    }
    event.target.value = "";
  };
  const exportSelected = (copy) =>
    post("export", [selected], (bundle) => {
      if (copy) {
        begin(bundle, true);
        return;
      }
      const url = URL.createObjectURL(
        new Blob([JSON.stringify(bundle, null, 2)], {
          type: "application/json",
        }),
      );
      const link = document.createElement("a");
      link.href = url;
      link.download = "openelis-tests.json";
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    });
  const review = () => post("preview", request, setPreview);
  const save = () =>
    post("import", { ...request, previewToken: preview.token }, (result) => {
      setNotice({
        kind: "success",
        text: msg(result.alreadyImported ? "alreadyImported" : "success", {
          count: Object.keys(result.testIds || {}).length,
        }),
      });
      setRequest(null);
      setPreview(null);
      setConfirmed(false);
      load();
    });
  const editIdentity = (key, property, value) =>
    change({
      ...request,
      overrides: {
        ...request.overrides,
        [key]: { ...request.overrides[key], [property]: value },
      },
    });

  if (!available) return null;
  return (
    <Tile className="test-configuration-packages">
      <Stack gap={5} className="test-configuration-packages__content">
        <Heading className="test-configuration-packages__title">
          {msg("title")}
        </Heading>
        <p className="test-configuration-packages__description">
          {msg("help")}
        </p>
        {notice && (
          <InlineNotification
            kind={notice.kind}
            title={notice.text}
            hideCloseButton
          />
        )}
        {unclassified.length > 0 && (
          <details className="test-configuration-packages__history">
            <summary>{msg("originTitle")}</summary>
            <Stack gap={4} className="test-configuration-packages__content">
              <p>{msg("originHelp")}</p>
              <MultiSelect
                key={`origin-${unclassified.length}`}
                id="test-package-origin-selection"
                titleText={msg("originSelection")}
                label={msg("originSelection")}
                items={unclassified}
                itemToString={(item) => item?.name ?? ""}
                initialSelectedItems={[]}
                disabled={busy}
                onChange={({ selectedItems }) =>
                  setOriginSelection(selectedItems)
                }
              />
              <ButtonSet className="test-configuration-packages__actions">
                {[true, false].map((userCreated) => (
                  <Button
                    key={String(userCreated)}
                    size="md"
                    className="test-configuration-packages__action"
                    kind={userCreated ? "primary" : "secondary"}
                    disabled={
                      busy ||
                      !originSelection.length ||
                      originSelection.length > 200
                    }
                    onClick={() =>
                      post(
                        "classify-origin",
                        {
                          testIds: originSelection.map((item) => item.id),
                          userCreated,
                        },
                        () => {
                          setOriginSelection([]);
                          setNotice({
                            kind: "success",
                            text: msg("originSaved"),
                          });
                          load();
                        },
                      )
                    }
                  >
                    {msg(userCreated ? "originUser" : "originSystem")}
                  </Button>
                ))}
              </ButtonSet>
            </Stack>
          </details>
        )}
        <Select
          id="test-package-selection"
          labelText={msg("selectTests")}
          value={selected}
          onChange={(event) => setSelected(event.target.value)}
          disabled={busy}
        >
          <SelectItem value="" text={msg("selectTests")} />
          {tests.map((test) => (
            <SelectItem
              key={test.id}
              value={test.id}
              text={`${test.name}${test.code ? ` (${test.code})` : ""}`}
            />
          ))}
        </Select>
        <ButtonSet className="test-configuration-packages__actions">
          <Button
            size="md"
            className="test-configuration-packages__action"
            disabled={busy || !selected}
            onClick={() => exportSelected(false)}
          >
            {msg("export")}
          </Button>
          <Button
            size="md"
            className="test-configuration-packages__action"
            kind="secondary"
            disabled={busy || !selected}
            onClick={() => exportSelected(true)}
          >
            {msg("copy")}
          </Button>
          <FileUploaderButton
            size="md"
            className="test-configuration-packages__action"
            labelText={msg("upload")}
            accept={[".json", "application/json"]}
            multiple={false}
            disableLabelChanges
            disabled={busy}
            onChange={readFile}
          />
        </ButtonSet>
        {busy && <InlineLoading description={msg("working")} />}
        {incoming && (
          <Stack gap={4} className="test-configuration-packages__content">
            <p>{msg("fileSelectionHelp")}</p>
            <Select
              id="test-package-file-selection"
              labelText={msg("fileSelection")}
              value={incomingTest}
              disabled={busy}
              onChange={(event) => setIncomingTest(event.target.value)}
            >
              <SelectItem value="" text={msg("fileSelection")} />
              {incoming.roots.map((key) => {
                const test = incoming.nodes.find((node) => node.key === key);
                return (
                  <SelectItem
                    key={key}
                    value={key}
                    text={`${test.values.description}${test.values.localCode ? ` (${test.values.localCode})` : ""}`}
                  />
                );
              })}
            </Select>
            <ButtonSet className="test-configuration-packages__actions">
              <Button
                size="md"
                className="test-configuration-packages__action"
                disabled={busy || !incomingTest}
                onClick={() => prepare(incoming, incomingTest)}
              >
                {msg("prepareSelected")}
              </Button>
              <Button
                size="md"
                className="test-configuration-packages__action"
                kind="ghost"
                disabled={busy}
                onClick={() => setIncoming(null)}
              >
                {msg("cancel")}
              </Button>
            </ButtonSet>
          </Stack>
        )}
        {request && (
          <Stack gap={4} className="test-configuration-packages__review">
            <Heading className="test-configuration-packages__subtitle">
              {msg("reviewTitle")}
            </Heading>
            <p>{msg("historyProtection")}</p>
            <p>{msg("closureHelp")}</p>
            {request.bundle.nodes
              .filter((node) => node.kind === "Test")
              .map((node) => (
                <Grid
                  condensed
                  fullWidth
                  className="test-configuration-packages__grid"
                  key={node.key}
                >
                  <Column lg={12} md={4} sm={4}>
                    <TextInput
                      id={`package-name-${node.key}`}
                      labelText={msg("name")}
                      value={
                        request.overrides[node.key]?.description ??
                        node.values.description ??
                        ""
                      }
                      disabled={busy}
                      onChange={(e) =>
                        editIdentity(node.key, "description", e.target.value)
                      }
                      maxLength={60}
                    />
                  </Column>
                  <Column lg={4} md={4} sm={4}>
                    <TextInput
                      id={`package-code-${node.key}`}
                      labelText={msg("code")}
                      value={
                        request.overrides[node.key]?.localCode ??
                        node.values.localCode ??
                        ""
                      }
                      disabled={busy}
                      onChange={(e) =>
                        editIdentity(node.key, "localCode", e.target.value)
                      }
                      maxLength={10}
                    />
                  </Column>
                </Grid>
              ))}
            {preview?.catalogs.length > 0 && (
              <Grid
                condensed
                fullWidth
                className="test-configuration-packages__grid"
              >
                {preview.catalogs.map((catalog) => (
                  <Column lg={8} md={4} sm={4} key={catalog.key}>
                    <Select
                      id={`package-catalog-${catalog.key}`}
                      labelText={`${msg(`kind.${catalog.kind}`)}: ${catalog.label}`}
                      value={
                        request.catalogs[catalog.key] ?? catalog.suggestedId
                      }
                      disabled={busy}
                      onChange={(e) => {
                        const next = {
                          ...request,
                          catalogs: {
                            ...request.catalogs,
                            [catalog.key]: e.target.value,
                          },
                        };
                        change(next);
                        post("preview", next, setPreview);
                      }}
                    >
                      <SelectItem value="" text={msg("chooseCatalog")} />
                      {catalog.candidates.map((candidate) => (
                        <SelectItem
                          key={candidate.id}
                          value={candidate.id}
                          text={candidate.label}
                        />
                      ))}
                      {catalog.canCreate && (
                        <SelectItem
                          value="CREATE"
                          text={msg("createCatalog")}
                        />
                      )}
                    </Select>
                  </Column>
                ))}
              </Grid>
            )}
            <Button
              size="md"
              className="test-configuration-packages__action"
              kind="secondary"
              disabled={busy}
              onClick={review}
            >
              {msg("review")}
            </Button>
            {preview && (
              <>
                {preview.alreadyImported && (
                  <InlineNotification
                    kind="info"
                    title={msg("alreadyImported")}
                    hideCloseButton
                  />
                )}
                {preview.issues.map((issue, index) => (
                  <InlineNotification
                    key={`${issue.key}-${index}`}
                    kind="error"
                    title={msg(`issue.${issue.code}`)}
                    subtitle={
                      preview.tests.find((test) => test.key === issue.key)
                        ?.name ||
                      preview.catalogs.find(
                        (catalog) => catalog.key === issue.key,
                      )?.label
                    }
                    hideCloseButton
                  />
                ))}
                <p>
                  {msg("summary", {
                    tests: preview.tests.length,
                    changes: preview.differences.length,
                  })}
                </p>
                {preview.differences.length > 0 && (
                  <details>
                    <summary>{msg("differences")}</summary>
                    {preview.differences.map((diff, index) => (
                      <Tile key={`${diff.key}-${index}`}>
                        <strong>{msg(`kind.${diff.kind}`)}</strong>
                        <pre>
                          {JSON.stringify(
                            {
                              before: diff.before?.values,
                              after: diff.after?.values,
                            },
                            null,
                            2,
                          )}
                        </pre>
                      </Tile>
                    ))}
                  </details>
                )}
                <Checkbox
                  id="test-package-confirm"
                  labelText={msg("confirm")}
                  checked={confirmed}
                  disabled={busy || !preview.ready}
                  onChange={(_, { checked }) => setConfirmed(checked)}
                />
                <Button
                  size="md"
                  className="test-configuration-packages__action"
                  disabled={busy || !preview.ready || !confirmed}
                  onClick={save}
                >
                  {msg("import")}
                </Button>
              </>
            )}
            <Button
              size="md"
              className="test-configuration-packages__action"
              kind="ghost"
              disabled={busy}
              onClick={() => change(null)}
            >
              {msg("cancel")}
            </Button>
          </Stack>
        )}
        {history.length > 0 && (
          <details className="test-configuration-packages__history">
            <summary>{msg("history")}</summary>
            {history.map((revision) => (
              <p key={revision.id}>
                {intl.formatDate(revision.createdAt)}{" "}
                {intl.formatTime(revision.createdAt)} —{" "}
                {msg("historyRow", {
                  actor: revision.actor,
                  count: Object.keys(revision.mapping?.tests || {}).length,
                })}
              </p>
            ))}
          </details>
        )}
      </Stack>
    </Tile>
  );
}
