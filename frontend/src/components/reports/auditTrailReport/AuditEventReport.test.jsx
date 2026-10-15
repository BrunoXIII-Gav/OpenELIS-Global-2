/* eslint-env jest */
import React from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import AuditEventReport, { auditQuery } from "./AuditEventReport";
import messages from "../../../languages/en.json";
import { getFromOpenElisServer } from "../../utils/Utils";

jest.mock("../../utils/Utils", () => ({ getFromOpenElisServer: jest.fn() }));

const event = (id, name, field, before, after) => ({
  id,
  occurredAt: "2026-10-14T15:00:00Z",
  module: "sampleitem",
  entityName: "SampleItem",
  entityId: "100",
  field: "fieldValue",
  fieldLabel: field,
  actorName: name,
  actorId: id,
  actorSource: "USER",
  action: "U",
  oldValue: before,
  newValue: after,
});
const mount = () =>
  render(
    <IntlProvider locale="en" messages={messages}>
      <AuditEventReport />
    </IntlProvider>,
  );

const users = [
  { id: "12", value: "Zapata, Luis" },
  { id: "7", value: "García, Ana" },
];
const tests = [
  { id: "9201", code: "MAN", name: "Manual DMPK" },
  { id: "9202", code: "MAN2", name: "Manual ATXN10" },
];
const mockHistory = (
  handler = (url, callback) => callback({ items: [], total: 0 }),
) =>
  getFromOpenElisServer.mockImplementation((url, callback, signal) => {
    if (url === "/rest/users") callback(users);
    else if (url === "/rest/audit-events/tests") callback(tests);
    else handler(url, callback, signal);
  });
const historyRequests = () =>
  getFromOpenElisServer.mock.calls.filter(([url]) =>
    url.startsWith("/rest/audit-events?"),
  );

beforeEach(() => {
  jest.resetAllMocks();
  mockHistory();
});

it("shows all test choices when opening the selector without typing or after a selection", () => {
  mount();
  fireEvent.click(screen.getByRole("button", { name: "Open test list" }));
  expect(screen.getByText("MAN - Manual DMPK")).toBeTruthy();
  expect(screen.getByText("MAN2 - Manual ATXN10")).toBeTruthy();
  fireEvent.click(screen.getByText("MAN - Manual DMPK"));
  fireEvent.click(screen.getByRole("button", { name: "Open test list" }));
  expect(screen.getByText("MAN2 - Manual ATXN10")).toBeTruthy();
});

it("shows all users when opening their selector without typing", () => {
  mount();
  fireEvent.click(screen.getByRole("button", { name: "Open user list" }));
  expect(screen.getByText("García, Ana")).toBeTruthy();
  expect(screen.getByText("Zapata, Luis")).toBeTruthy();
});

it("shows separate actors and before/after values for each sample field", () => {
  mockHistory((url, callback) =>
    callback({
      items: [
        event("1", "Ana", "Collection", null, "Collected"),
        event("2", "Luis", "Reception", "Pending", "Received"),
      ],
      total: 2,
    }),
  );
  mount();
  fireEvent.change(screen.getByLabelText("Lab number or sample CUG"), {
    target: { value: "CUG-7" },
  });
  fireEvent.click(screen.getByText("Search history"));
  expect(screen.getByText("Ana")).toBeTruthy();
  expect(screen.getByText("Luis")).toBeTruthy();
  expect(screen.getByText("Collection")).toBeTruthy();
  expect(screen.getByText("Reception")).toBeTruthy();
  expect(screen.getByText("Pending")).toBeTruthy();
  expect(screen.getByText("Received")).toBeTruthy();
  expect(historyRequests()[0][0]).toContain("accessionNumber=CUG-7");
});

it("clears previous data and reports a denied or failed request", () => {
  const responses = [
    { items: [event("1", "Ana", "Collection", null, "Collected")], total: 1 },
    undefined,
  ];
  mockHistory((url, callback) => callback(responses.shift()));
  mount();
  fireEvent.click(screen.getByText("Search history"));
  expect(screen.getByText("Ana")).toBeTruthy();
  fireEvent.click(screen.getByText("Search history"));
  expect(screen.queryByText("Ana")).toBeNull();
  expect(screen.getByText("Audit history unavailable")).toBeTruthy();
});

it("ignores responses from an aborted earlier search", () => {
  const callbacks = [];
  mockHistory((url, callback) => callbacks.push(callback));
  const view = mount();
  fireEvent.click(screen.getByText("Search history"));
  view.unmount();
  act(() =>
    callbacks[0]({
      items: [event("1", "Ana", "Collection", null, "Collected")],
      total: 1,
    }),
  );
});

it("encodes filters and sends local dates as UTC instants", () => {
  const query = auditQuery(
    {
      accessionNumber: "A&B / 1",
      from: "2026-10-14T10:00",
      to: "",
      module: "sampleitem",
    },
    2,
    30,
  );
  const parameters = new URLSearchParams(query.split("?")[1]);
  expect(parameters.get("accessionNumber")).toBe("A&B / 1");
  expect(parameters.get("from")).toBe(
    new Date("2026-10-14T10:00").toISOString(),
  );
  expect(parameters.get("page")).toBe("2");
});

it("offers only the agreed business modules and configuration groups", () => {
  mount();
  const select = screen.getByLabelText("Module");
  expect(Array.from(select.options).map((option) => option.text)).toEqual([
    "All",
    "Orders",
    "Samples",
    "Patients",
    "Results",
    "Validation",
    "Storage",
    "Professionals",
    "Users, roles and permissions",
    "Reports",
    "Report templates",
    "Sample form configuration",
    "Test configuration",
    "System configuration",
  ]);
});

it("shows descriptions and field labels without internal record or field IDs", () => {
  mockHistory((url, callback) =>
    callback({
      items: [
        {
          ...event("1", "Ana", "Procedure", "CODE_A", "CODE_B"),
          oldDisplayValue: "Procedure A",
          oldDescriptionFromCurrentCatalog: true,
          newDisplayValue: "Procedure B",
          fieldDefinitionId: "777",
          recordLabel: "CUG-7",
        },
      ],
      total: 1,
    }),
  );
  mount();
  fireEvent.click(screen.getByText("Search history"));
  expect(screen.getByText("Procedure A")).toBeTruthy();
  expect(screen.getByText("Procedure A").closest("td").title).toContain(
    "current catalog",
  );
  expect(screen.getByText("Procedure B")).toBeTruthy();
  expect(screen.getByText("Sample: CUG-7")).toBeTruthy();
  expect(screen.queryByText("CODE_A")).toBeNull();
  expect(screen.queryByText("SampleItem / 100")).toBeNull();
  expect(screen.queryByText("Procedure (777)")).toBeNull();
});

it("translates functional states instead of displaying persistence enum names", () => {
  mockHistory((url, callback) =>
    callback({
      items: [
        {
          ...event(
            "1",
            "Ana",
            "dataCompletionStatus",
            "PENDING_COMPLETION",
            "COMPLETED",
          ),
          field: "dataCompletionStatus",
        },
      ],
      total: 1,
    }),
  );
  mount();
  fireEvent.click(screen.getByText("Search history"));
  expect(screen.getByText("Sample data completion")).toBeTruthy();
  expect(screen.getByText("Pending completion")).toBeTruthy();
  expect(screen.getByText("Completed")).toBeTruthy();
  expect(screen.queryByText("PENDING_COMPLETION")).toBeNull();
});

it("lists users by name and filters history using the selected account", () => {
  mount();
  const input = screen.getByRole("combobox", { name: "Acting user" });
  expect(screen.queryByLabelText("Acting user ID")).toBeNull();
  fireEvent.change(input, { target: { value: "Ana" } });
  expect(screen.getByText("García, Ana")).toBeTruthy();
  expect(screen.queryByText("Zapata, Luis")).toBeNull();
  fireEvent.click(screen.getByText("García, Ana"));
  fireEvent.click(screen.getByText("Search history"));
  const parameters = new URLSearchParams(historyRequests()[0][0].split("?")[1]);
  expect(parameters.get("actorId")).toBe("7");
  expect(parameters.has("entityId")).toBe(false);
  fireEvent.click(screen.getByRole("button", { name: "Clear selected user" }));
  fireEvent.click(screen.getByText("Search history"));
  const cleared = new URLSearchParams(historyRequests()[1][0].split("?")[1]);
  expect(cleared.has("actorId")).toBe(false);
});

it("keeps the selected user when paging and changing the page size", () => {
  mockHistory((url, callback) =>
    callback({
      items: [event("1", "Ana", "Collection", null, "Collected")],
      total: 70,
    }),
  );
  mount();
  fireEvent.change(screen.getByRole("combobox", { name: "Acting user" }), {
    target: { value: "Luis" },
  });
  fireEvent.click(screen.getByText("Zapata, Luis"));
  fireEvent.click(screen.getByText("Search history"));
  fireEvent.click(screen.getByRole("button", { name: "Next page" }));
  let parameters = new URLSearchParams(historyRequests()[1][0].split("?")[1]);
  expect(parameters.get("actorId")).toBe("12");
  expect(parameters.get("page")).toBe("1");
  fireEvent.change(screen.getByLabelText("Events per page"), {
    target: { value: "10" },
  });
  parameters = new URLSearchParams(historyRequests()[2][0].split("?")[1]);
  expect(parameters.get("actorId")).toBe("12");
  expect(parameters.get("size")).toBe("10");
});

it("allows retrying user loading while other history filters remain usable", () => {
  let attempts = 0;
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url === "/rest/users") callback(++attempts === 1 ? undefined : users);
    else if (url === "/rest/audit-events/tests") callback(tests);
    else callback({ items: [], total: 0 });
  });
  mount();
  expect(screen.getByText("The user list could not be loaded.")).toBeTruthy();
  fireEvent.click(screen.getByText("Search history"));
  expect(historyRequests()).toHaveLength(1);
  fireEvent.click(screen.getByText("Retry loading users"));
  expect(screen.queryByText("The user list could not be loaded.")).toBeNull();
  fireEvent.change(screen.getByRole("combobox", { name: "Acting user" }), {
    target: { value: "Ana" },
  });
  expect(screen.getByText("García, Ana")).toBeTruthy();
});

it("aborts both selector requests when the report unmounts", () => {
  const requests = [];
  getFromOpenElisServer.mockImplementation((url, callback, requestSignal) => {
    requests.push({ url, callback, signal: requestSignal });
  });
  const view = mount();
  act(() => view.unmount());
  expect(requests).toHaveLength(2);
  expect(requests.every((request) => request.signal.aborted)).toBe(true);
  act(() => requests.forEach((request) => request.callback(undefined)));
});

it("replaces internal record IDs with test selection and retains it when paging", () => {
  mockHistory((url, callback) =>
    callback({
      items: [event("1", "Ana", "Measurement", null, "42")],
      total: 70,
    }),
  );
  mount();
  expect(screen.queryByLabelText("Record ID")).toBeNull();
  fireEvent.change(screen.getByRole("combobox", { name: "Test" }), {
    target: { value: "DMPK" },
  });
  fireEvent.click(screen.getByText("MAN - Manual DMPK"));
  fireEvent.change(screen.getByRole("combobox", { name: "Acting user" }), {
    target: { value: "Ana" },
  });
  fireEvent.click(screen.getByText("García, Ana"));
  fireEvent.change(screen.getByLabelText("Lab number or sample CUG"), {
    target: { value: "CUG-7" },
  });
  fireEvent.click(screen.getByText("Search history"));
  fireEvent.click(screen.getByRole("button", { name: "Next page" }));
  const parameters = new URLSearchParams(historyRequests()[1][0].split("?")[1]);
  expect(parameters.get("testId")).toBe("9201");
  expect(parameters.get("actorId")).toBe("7");
  expect(parameters.get("accessionNumber")).toBe("CUG-7");
  expect(parameters.get("page")).toBe("1");
  expect(parameters.has("entityId")).toBe(false);
  fireEvent.click(screen.getByRole("button", { name: "Clear selected test" }));
  fireEvent.click(screen.getByText("Search history"));
  expect(
    new URLSearchParams(historyRequests()[2][0].split("?")[1]).has("testId"),
  ).toBe(false);
});

it("retries test loading independently of the user and history requests", () => {
  let attempts = 0;
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url === "/rest/users") callback(users);
    else if (url === "/rest/audit-events/tests")
      callback(++attempts === 1 ? undefined : tests);
    else callback({ items: [], total: 0 });
  });
  mount();
  expect(screen.getByText("The test list could not be loaded.")).toBeTruthy();
  fireEvent.click(screen.getByText("Search history"));
  expect(historyRequests()).toHaveLength(1);
  fireEvent.click(screen.getByText("Retry loading tests"));
  expect(screen.queryByText("The test list could not be loaded.")).toBeNull();
  fireEvent.change(screen.getByRole("combobox", { name: "Test" }), {
    target: { value: "DMPK" },
  });
  expect(screen.getByText("MAN - Manual DMPK")).toBeTruthy();
});
