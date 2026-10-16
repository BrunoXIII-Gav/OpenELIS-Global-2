import React, { useEffect, useRef, useState } from "react";
import {
  Button,
  ButtonSet,
  InlineNotification,
  MultiSelect,
  Stack,
  Tile,
} from "@carbon/react";
import { useIntl } from "react-intl";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../../utils/Utils";

const BASE = "/rest/test-section-origins";

export default function TestSectionOriginConfig() {
  const intl = useIntl();
  const mounted = useRef(false);
  const [sections, setSections] = useState([]);
  const [selected, setSelected] = useState([]);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState(null);
  const msg = (key) => intl.formatMessage({ id: `testSection.origin.${key}` });
  const load = (signal) =>
    getFromOpenElisServer(
      BASE,
      (data) => {
        if (mounted.current && Array.isArray(data)) {
          setSections(data);
          setSelected((items) =>
            items.filter((item) =>
              data.some((section) => section.id === item.id),
            ),
          );
        }
      },
      signal,
    );

  useEffect(() => {
    mounted.current = true;
    const abort = new AbortController();
    getFromOpenElisServer(
      `${BASE}/availability`,
      (data) => {
        if (!abort.signal.aborted && data?.available) load(abort.signal);
      },
      abort.signal,
    );
    return () => {
      mounted.current = false;
      abort.abort();
    };
  }, []);

  const classify = (userCreated) => {
    setBusy(true);
    setNotice(null);
    postToOpenElisServerJsonResponse(
      BASE,
      JSON.stringify({
        sectionIds: selected.map((section) => section.id),
        userCreated,
      }),
      (data) => {
        if (!mounted.current) return;
        setBusy(false);
        if (
          !data ||
          data.error ||
          data.status >= 400 ||
          data.classified !== selected.length
        ) {
          setNotice({ kind: "error", text: msg("error") });
          load();
          return;
        }
        setSelected([]);
        setNotice({ kind: "success", text: msg("saved") });
        load();
      },
    );
  };

  if (!sections.length && !notice) return null;
  return (
    <Tile>
      <Stack gap={4}>
        {notice && (
          <InlineNotification
            kind={notice.kind}
            title={notice.text}
            hideCloseButton
          />
        )}
        {sections.length > 0 && (
          <>
            <p>{msg("help")}</p>
            <MultiSelect
              key={sections.map((section) => section.id).join(",")}
              id="test-section-origin-selection"
              titleText={msg("title")}
              label={msg("select")}
              items={sections}
              itemToString={(item) => item?.name ?? ""}
              initialSelectedItems={[]}
              disabled={busy}
              onChange={({ selectedItems }) => setSelected(selectedItems)}
            />
            <ButtonSet>
              {[true, false].map((userCreated) => (
                <Button
                  key={String(userCreated)}
                  kind={userCreated ? "primary" : "secondary"}
                  disabled={busy || !selected.length || selected.length > 200}
                  onClick={() => classify(userCreated)}
                >
                  {msg(userCreated ? "user" : "system")}
                </Button>
              ))}
            </ButtonSet>
          </>
        )}
      </Stack>
    </Tile>
  );
}
