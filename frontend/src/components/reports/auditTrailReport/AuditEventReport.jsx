import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  Button,
  CodeSnippet,
  Column,
  ComboBox,
  Form,
  Grid,
  InlineLoading,
  InlineNotification,
  Pagination,
  Select,
  SelectItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from "@carbon/react";
import { useIntl } from "react-intl";
import { getFromOpenElisServer } from "../../utils/Utils";
import {
  auditModules,
  auditFieldLabel,
  auditRecordLabel,
  auditValue,
} from "./auditPresentation";

const emptyFilters = {
  accessionNumber: "",
  module: "",
  testId: "",
  actorId: "",
  field: "",
  action: "",
  from: "",
  to: "",
};

export const auditQuery = (filters, page, size) => {
  const parameters = new URLSearchParams({
    page: String(page),
    size: String(size),
  });
  Object.entries(filters).forEach(([key, value]) => {
    if (value.trim()) {
      parameters.set(
        key,
        key === "from" || key === "to"
          ? new Date(value).toISOString()
          : value.trim(),
      );
    }
  });
  return `/rest/audit-events?${parameters.toString()}`;
};

export default function AuditEventReport() {
  const intl = useIntl();
  const t = (key) => intl.formatMessage({ id: `audit.events.${key}` });
  const [filters, setFilters] = useState(emptyFilters);
  const [applied, setApplied] = useState(null);
  const [result, setResult] = useState({ items: [], total: 0 });
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(30);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const [users, setUsers] = useState([]);
  const [usersLoading, setUsersLoading] = useState(true);
  const [usersError, setUsersError] = useState(false);
  const [usersAttempt, setUsersAttempt] = useState(0);
  const [tests, setTests] = useState([]);
  const [testsLoading, setTestsLoading] = useState(true);
  const [testsError, setTestsError] = useState(false);
  const [testsAttempt, setTestsAttempt] = useState(0);
  const request = useRef(null);
  const sequence = useRef(0);

  useEffect(() => () => request.current?.abort(), []);

  useEffect(() => {
    const controller = new AbortController();
    setUsersLoading(true);
    setUsersError(false);
    getFromOpenElisServer(
      "/rest/users",
      (data) => {
        if (controller.signal.aborted) return;
        setUsersLoading(false);
        if (!Array.isArray(data)) {
          setUsersError(true);
          return;
        }
        setUsers(
          data
            .filter((user) => user?.id != null && user?.value)
            .map((user) => ({ ...user, id: String(user.id) })),
        );
      },
      controller.signal,
    );
    return () => controller.abort();
  }, [usersAttempt]);

  useEffect(() => {
    const controller = new AbortController();
    setTestsLoading(true);
    setTestsError(false);
    getFromOpenElisServer(
      "/rest/audit-events/tests",
      (data) => {
        if (controller.signal.aborted) return;
        setTestsLoading(false);
        if (!Array.isArray(data)) {
          setTestsError(true);
          return;
        }
        setTests(
          data
            .filter((test) => test?.id != null && test?.name)
            .map((test) => ({
              id: String(test.id),
              value: test.code ? `${test.code} - ${test.name}` : test.name,
            })),
        );
      },
      controller.signal,
    );
    return () => controller.abort();
  }, [testsAttempt]);

  const anyTest = t("anyTest");
  const testOptions = useMemo(
    () => [
      { id: "", value: anyTest },
      ...[...tests].sort((left, right) =>
        left.value.localeCompare(right.value, intl.locale),
      ),
    ],
    [tests, anyTest, intl.locale],
  );

  const allUsers = t("allUsers");
  const userOptions = useMemo(
    () => [
      { id: "", value: allUsers },
      ...[...users].sort((left, right) =>
        left.value.localeCompare(right.value, intl.locale),
      ),
    ],
    [users, allUsers, intl.locale],
  );

  const selectedUser =
    userOptions.find((item) => item.id === filters.actorId) || null;
  const selectedTest =
    testOptions.find((item) => item.id === filters.testId) || null;
  const filterSelectorItems =
    (selection) =>
    ({ item, inputValue }) =>
      item.id === "" ||
      inputValue === selection?.value ||
      item.value
        .toLocaleLowerCase(intl.locale)
        .includes((inputValue || "").toLocaleLowerCase(intl.locale));

  const search = (values, nextPage, nextSize) => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    const currentSequence = ++sequence.current;
    setLoading(true);
    setError(false);
    setResult({ items: [], total: 0 });
    getFromOpenElisServer(
      auditQuery(values, nextPage, nextSize),
      (data) => {
        if (controller.signal.aborted || currentSequence !== sequence.current)
          return;
        setLoading(false);
        if (!Array.isArray(data?.items)) {
          setError(true);
          return;
        }
        setResult(data);
        setPage(nextPage);
        setSize(nextSize);
      },
      controller.signal,
    );
  };
  const update = (key) => (event) =>
    setFilters({ ...filters, [key]: event.target.value });

  return (
    <>
      <Form
        onSubmit={(event) => {
          event.preventDefault();
          setApplied({ ...filters });
          search(filters, 0, size);
        }}
      >
        <Grid fullWidth>
          <Column lg={4} md={4} sm={4}>
            <TextInput
              id="audit-accessionNumber"
              labelText={t("accessionNumber")}
              value={filters.accessionNumber}
              onChange={update("accessionNumber")}
            />
          </Column>
          <Column lg={4} md={4} sm={4}>
            <ComboBox
              id="audit-actorId"
              titleText={t("actor")}
              items={userOptions}
              itemToString={(item) => item?.value || ""}
              selectedItem={selectedUser}
              placeholder={usersLoading ? t("usersLoading") : allUsers}
              disabled={usersLoading || usersError}
              invalid={usersError}
              invalidText={t("usersError")}
              shouldFilterItem={filterSelectorItems(selectedUser)}
              onChange={({ selectedItem }) =>
                setFilters((previous) => ({
                  ...previous,
                  actorId: selectedItem?.id || "",
                }))
              }
              translateWithId={(id) => t(`userSelector.${id}`)}
            />
            {usersError && (
              <Button
                type="button"
                kind="ghost"
                size="sm"
                onClick={() => setUsersAttempt((attempt) => attempt + 1)}
              >
                {t("usersRetry")}
              </Button>
            )}
          </Column>
          <Column lg={4} md={4} sm={4}>
            <ComboBox
              id="audit-testId"
              titleText={t("test")}
              items={testOptions}
              itemToString={(item) => item?.value || ""}
              selectedItem={selectedTest}
              placeholder={testsLoading ? t("testsLoading") : anyTest}
              disabled={testsLoading || testsError}
              invalid={testsError}
              invalidText={t("testsError")}
              shouldFilterItem={filterSelectorItems(selectedTest)}
              onChange={({ selectedItem }) =>
                setFilters((previous) => ({
                  ...previous,
                  testId: selectedItem?.id || "",
                }))
              }
              translateWithId={(id) => t(`testSelector.${id}`)}
            />
            {testsError && (
              <Button
                type="button"
                kind="ghost"
                size="sm"
                onClick={() => setTestsAttempt((attempt) => attempt + 1)}
              >
                {t("testsRetry")}
              </Button>
            )}
          </Column>
          <Column lg={4} md={4} sm={4}>
            <TextInput
              id="audit-field"
              labelText={t("field")}
              value={filters.field}
              onChange={update("field")}
            />
          </Column>
          <Column lg={4} md={4} sm={4}>
            <Select
              id="audit-module"
              labelText={t("module")}
              value={filters.module}
              onChange={update("module")}
            >
              <SelectItem value="" text={t("all")} />
              {auditModules.map((module) => (
                <SelectItem
                  key={module}
                  value={module}
                  text={t(`module.${module}`)}
                />
              ))}
            </Select>
          </Column>
          <Column lg={4} md={4} sm={4}>
            <Select
              id="audit-action"
              labelText={t("action")}
              value={filters.action}
              onChange={update("action")}
            >
              <SelectItem value="" text={t("all")} />
              {["I", "U", "D"].map((action) => (
                <SelectItem
                  key={action}
                  value={action}
                  text={t(`action.${action}`)}
                />
              ))}
            </Select>
          </Column>
          {["from", "to"].map((key) => (
            <Column lg={4} md={4} sm={4} key={key}>
              <TextInput
                id={`audit-${key}`}
                type="datetime-local"
                labelText={t(key)}
                value={filters[key]}
                onChange={update(key)}
                min={key === "to" ? filters.from : undefined}
                max={key === "from" ? filters.to : undefined}
              />
            </Column>
          ))}
          <Column lg={16} md={8} sm={4}>
            <Button type="submit" disabled={loading}>
              {t("search")}
            </Button>
          </Column>
        </Grid>
      </Form>
      {loading && <InlineLoading description={t("loading")} />}
      {error && (
        <InlineNotification
          kind="error"
          hideCloseButton
          title={t("errorTitle")}
          subtitle={t("error")}
        />
      )}
      {!loading && !error && applied && result.items.length === 0 && (
        <p role="status">{t("noEvents")}</p>
      )}
      {result.items.length > 0 && (
        <>
          <Table aria-label={t("title")}>
            <TableHead>
              <TableRow>
                {[
                  "time",
                  "module",
                  "record",
                  "field",
                  "action",
                  "actor",
                  "oldValue",
                  "newValue",
                ].map((key) => (
                  <TableHeader key={key}>{t(key)}</TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {result.items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    {intl.formatDate(item.occurredAt)}{" "}
                    {intl.formatTime(item.occurredAt, {
                      hour: "2-digit",
                      minute: "2-digit",
                      second: "2-digit",
                    })}
                  </TableCell>
                  <TableCell>{t(`module.${item.module}`)}</TableCell>
                  <TableCell>{auditRecordLabel(intl, item)}</TableCell>
                  <TableCell>{auditFieldLabel(intl, item)}</TableCell>
                  <TableCell>{t(`action.${item.action}`)}</TableCell>
                  <TableCell>
                    {item.actorName || t(`actor.${item.actorSource}`)}
                  </TableCell>
                  <TableCell
                    title={
                      item.oldDescriptionFromCurrentCatalog
                        ? t("currentCatalog")
                        : undefined
                    }
                  >
                    <CodeSnippet
                      type="multi"
                      hideCopyButton
                      wrapText
                      showMoreText={t("showMore")}
                      showLessText={t("showLess")}
                    >
                      {auditValue(
                        intl,
                        item,
                        item.oldDisplayValue,
                        item.oldValue,
                      )}
                    </CodeSnippet>
                  </TableCell>
                  <TableCell
                    title={
                      item.newDescriptionFromCurrentCatalog
                        ? t("currentCatalog")
                        : undefined
                    }
                  >
                    <CodeSnippet
                      type="multi"
                      hideCopyButton
                      wrapText
                      showMoreText={t("showMore")}
                      showLessText={t("showLess")}
                    >
                      {auditValue(
                        intl,
                        item,
                        item.newDisplayValue,
                        item.newValue,
                      )}
                    </CodeSnippet>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Pagination
            page={page + 1}
            pageSize={size}
            pageSizes={[10, 30, 50, 100]}
            totalItems={result.total}
            itemsPerPageText={t("itemsPerPage")}
            pageNumberText={t("pageNumber")}
            itemRangeText={(min, max, total) =>
              intl.formatMessage(
                { id: "audit.events.itemRange" },
                { min, max, total },
              )
            }
            pageRangeText={(current, total) =>
              intl.formatMessage(
                { id: "audit.events.pageRange" },
                { current, total },
              )
            }
            backwardText={t("previous")}
            forwardText={t("next")}
            onChange={({ page: nextPage, pageSize }) =>
              search(applied, nextPage - 1, pageSize)
            }
          />
        </>
      )}
    </>
  );
}
