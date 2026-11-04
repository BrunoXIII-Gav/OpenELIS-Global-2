/* eslint-env jest */
import React from "react";
import { fireEvent, render, screen, act } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { TextEncoder } from "util";
import TestConfigurationPackages, {
  parseConfigurationFile,
} from "./TestConfigurationPackages";
import messages from "../../../languages/en.json";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../../utils/Utils";

jest.mock("../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
}));
global.TextEncoder = TextEncoder;
const bundle = {
  format: "openelis-test-configuration",
  version: 1,
  roots: ["source-test"],
  nodes: [
    {
      key: "source-test",
      kind: "Test",
      catalog: false,
      values: { description: "DNA", localCode: "DNA" },
      references: {},
    },
  ],
};
const preview = {
  token: "review-token",
  ready: true,
  alreadyImported: false,
  catalogs: [],
  issues: [],
  tests: [{ key: "source-test", name: "DNA", code: "DNA" }],
  differences: [],
};
const mount = () =>
  render(
    <IntlProvider locale="en" messages={messages}>
      <TestConfigurationPackages />
    </IntlProvider>,
  );
const upload = async (container, data = bundle) => {
  const file = new File([JSON.stringify(data)], "tests.json", {
    type: "application/json",
  });
  file.text = () => Promise.resolve(JSON.stringify(data));
  await act(async () =>
    fireEvent.change(container.querySelector('input[type="file"]'), {
      target: { files: [file] },
    }),
  );
};
beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((url, cb) =>
    cb(url.endsWith("/availability") ? { available: true } : []),
  );
  postToOpenElisServerJsonResponse.mockImplementation((url, payload, cb) => {
    if (url.endsWith("/prepare")) cb(JSON.parse(payload).bundle);
    else if (url.endsWith("/preview")) cb(preview);
    else
      cb({
        revisionId: "revision",
        alreadyImported: false,
        testIds: { "source-test": "500" },
      });
  });
});
test("accepts supported portable format and rejects unsupported versions", () => {
  expect(parseConfigurationFile(JSON.stringify(bundle))).toEqual(bundle);
  expect(() =>
    parseConfigurationFile(JSON.stringify({ ...bundle, version: 2 })),
  ).toThrow("testPackage.issue.format");
  expect(() => parseConfigurationFile("not json")).toThrow();
});

test("classifies selected legacy definitions once and reloads the eligible selector", () => {
  const legacy = [
    { id: "73", name: "My existing test", code: "OWN" },
    { id: "84", name: "Old initialization test", code: "INIT" },
  ];
  let saved = false;
  getFromOpenElisServer.mockImplementation((url, cb) => {
    if (url.endsWith("/availability")) cb({ available: true });
    else if (url.endsWith("/unclassified-tests"))
      cb(saved ? [legacy[1]] : legacy);
    else if (url.endsWith("/tests")) cb(saved ? [legacy[0]] : []);
    else cb([]);
  });
  postToOpenElisServerJsonResponse.mockImplementation((url, body, cb) => {
    saved = true;
    cb({ classified: 1 });
  });
  const { container } = mount();
  const selection = container.querySelector("#test-package-selection");
  expect(selection.options.length).toBe(1);
  fireEvent.click(screen.getByText("Identify the origin of legacy tests"));
  fireEvent.click(
    container.querySelector("#test-package-origin-selection button"),
  );
  fireEvent.click(screen.getByText("My existing test"));
  fireEvent.click(screen.getByText("Mark as user-created"));
  expect(postToOpenElisServerJsonResponse).toHaveBeenCalledWith(
    expect.stringMatching(/\/classify-origin$/),
    JSON.stringify({ testIds: ["73"], userCreated: true }),
    expect.any(Function),
  );
  expect(Array.from(selection.options).map((option) => option.value)).toEqual([
    "",
    "73",
  ]);
});
test("limits the actual UTF-8 byte size", () => {
  expect(() =>
    parseConfigurationFile(
      JSON.stringify({ ...bundle, padding: "é".repeat(2_500_000) }),
    ),
  ).toThrow("testPackage.issue.size");
});
test("does not load protected data when configuration management is unavailable", () => {
  getFromOpenElisServer.mockImplementation((url, cb) =>
    cb({ available: false }),
  );
  mount();
  expect(screen.queryByText("Copy, export and import tests")).toBeNull();
  expect(getFromOpenElisServer).toHaveBeenCalledTimes(1);
});
test("requires review and confirmation before import and sends the reviewed token", async () => {
  const { container } = mount();
  await upload(container);
  expect(screen.queryByText("Import reviewed configuration")).toBeNull();
  fireEvent.click(screen.getByText("Check conflicts and references"));
  expect(
    screen.getByText("Import reviewed configuration").closest("button")
      .disabled,
  ).toBe(true);
  fireEvent.click(container.querySelector("#test-package-confirm"));
  fireEvent.click(screen.getByText("Import reviewed configuration"));
  expect(postToOpenElisServerJsonResponse).toHaveBeenLastCalledWith(
    expect.stringMatching(/\/import$/),
    expect.stringContaining('"previewToken":"review-token"'),
    expect.any(Function),
  );
  expect(
    screen.getByText("1 test definitions imported successfully."),
  ).toBeTruthy();
});
test("changing the name invalidates the previous review", async () => {
  const { container } = mount();
  await upload(container);
  fireEvent.click(screen.getByText("Check conflicts and references"));
  fireEvent.change(screen.getByLabelText("New test name"), {
    target: { value: "DNA copy" },
  });
  expect(screen.queryByText("Import reviewed configuration")).toBeNull();
  fireEvent.click(screen.getByText("Check conflicts and references"));
  expect(postToOpenElisServerJsonResponse.mock.calls.at(-1)[1]).toContain(
    '"description":"DNA copy"',
  );
});
test("conflicts prevent confirmation and import", async () => {
  postToOpenElisServerJsonResponse.mockImplementation((url, payload, cb) =>
    cb(
      url.endsWith("/prepare")
        ? JSON.parse(payload).bundle
        : {
            ...preview,
            ready: false,
            issues: [
              { code: "name", key: "source-test", property: "description" },
            ],
          },
    ),
  );
  const { container } = mount();
  await upload(container);
  fireEvent.click(screen.getByText("Check conflicts and references"));
  expect(
    screen.getByText("This test name already exists. Choose a new name."),
  ).toBeTruthy();
  expect(container.querySelector("#test-package-confirm").disabled).toBe(true);
  expect(
    screen.getByText("Import reviewed configuration").closest("button")
      .disabled,
  ).toBe(true);
});
test("preserves explicit catalog mappings in the next review", async () => {
  postToOpenElisServerJsonResponse.mockImplementation((url, payload, cb) =>
    cb(
      url.endsWith("/prepare")
        ? JSON.parse(payload).bundle
        : {
            ...preview,
            catalogs: [
              {
                key: "unit",
                kind: "TestSection",
                label: "Genetics",
                suggestedId: "88",
                canCreate: false,
                candidates: [
                  { id: "88", label: "Genetics" },
                  { id: "99", label: "Other unit" },
                ],
              },
            ],
          },
    ),
  );
  const { container } = mount();
  await upload(container);
  fireEvent.click(screen.getByText("Check conflicts and references"));
  fireEvent.change(container.querySelector("#package-catalog-unit"), {
    target: { value: "99" },
  });
  expect(postToOpenElisServerJsonResponse.mock.calls.at(-1)[1]).toContain(
    '"catalogs":{"unit":"99"}',
  );
});

test("copies only the single test selected in the source dropdown", () => {
  getFromOpenElisServer.mockImplementation((url, cb) => {
    if (url.endsWith("/availability")) cb({ available: true });
    else if (url.endsWith("/tests"))
      cb([
        { id: "11", name: "DNA", code: "DNA" },
        { id: "12", name: "Other", code: "OTHER" },
      ]);
    else cb([]);
  });
  postToOpenElisServerJsonResponse.mockImplementation((url, payload, cb) =>
    cb(bundle),
  );
  mount();
  fireEvent.change(screen.getByLabelText("Test to export or copy"), {
    target: { value: "11" },
  });
  fireEvent.click(screen.getByText("Copy selected test"));
  expect(postToOpenElisServerJsonResponse).toHaveBeenCalledWith(
    expect.stringMatching(/\/export$/),
    '["11"]',
    expect.any(Function),
  );
  expect(screen.getAllByLabelText("New test name")).toHaveLength(1);
});

test("an older multi-test file requires choosing one test and imports only the prepared selection", async () => {
  const other = {
    ...bundle.nodes[0],
    key: "other-test",
    values: { description: "Other test", localCode: "OTHER" },
  };
  const legacy = {
    ...bundle,
    roots: [...bundle.roots, other.key],
    nodes: [...bundle.nodes, other],
  };
  const prepared = { ...bundle, roots: [other.key], nodes: [other] };
  postToOpenElisServerJsonResponse.mockImplementation((url, payload, cb) => {
    if (url.endsWith("/prepare")) cb(prepared);
    else if (url.endsWith("/preview"))
      cb({
        ...preview,
        tests: [{ key: "other-test", name: "Other test", code: "OTHER" }],
      });
    else
      cb({
        revisionId: "revision",
        alreadyImported: false,
        testIds: { "other-test": "600" },
      });
  });
  const { container } = mount();
  await upload(container, legacy);
  expect(screen.queryByLabelText("New test name")).toBeNull();
  expect(postToOpenElisServerJsonResponse).not.toHaveBeenCalled();
  expect(
    screen.getByText("Prepare selected test").closest("button").disabled,
  ).toBe(true);
  fireEvent.change(screen.getByLabelText("Test from the file to import"), {
    target: { value: "other-test" },
  });
  fireEvent.click(screen.getByText("Prepare selected test"));
  expect(
    JSON.parse(postToOpenElisServerJsonResponse.mock.calls.at(-1)[1]).testKey,
  ).toBe("other-test");
  expect(screen.getAllByLabelText("New test name")).toHaveLength(1);
  expect(screen.getByLabelText("New test name").value).toBe("Other test");
  fireEvent.click(screen.getByText("Check conflicts and references"));
  fireEvent.click(container.querySelector("#test-package-confirm"));
  fireEvent.click(screen.getByText("Import reviewed configuration"));
  const imported = JSON.parse(
    postToOpenElisServerJsonResponse.mock.calls.at(-1)[1],
  );
  expect(imported.bundle.roots).toEqual(["other-test"]);
  expect(imported.bundle.nodes).toHaveLength(1);
});

test("preparation errors do not expose an importable configuration", async () => {
  postToOpenElisServerJsonResponse.mockImplementation((url, payload, cb) =>
    cb({ error: "testPackage.issue.selection" }),
  );
  const { container } = mount();
  await upload(container);
  expect(
    screen.getByText(
      "Select a single test. Configure relationships between tests manually afterwards.",
    ),
  ).toBeTruthy();
  expect(screen.queryByLabelText("New test name")).toBeNull();
  expect(screen.queryByText("Import reviewed configuration")).toBeNull();
});
