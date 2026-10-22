import React from "react";
import { fireEvent, render, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import SampleResultsTable from "./SampleResultsTable";
import messages from "../../languages/es.json";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../utils/Utils";

jest.mock("../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerFullResponse: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
}));

const sample = {
  id: "54",
  externalId: "LNG78-1",
  cugCode: "5004.2",
  sampleTypeId: "1",
  sampleType: "Sangre",
  editable: true,
  canRead: true,
  canComplete: true,
  canUpdate: true,
  orderedTests: [
    { analysisId: "a-54", testName: "ADN", status: "Not Started" },
  ],
  additionalFields: [],
  additionalFieldValues: {},
};

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServer.mockImplementation((url, callback) => callback([]));
});

const show = (props = {}) =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <SampleResultsTable
        sampleItems={[sample]}
        currentTestsVisibleBySampleId={{ 54: true }}
        {...props}
      />
    </IntlProvider>,
  );

test("saves the complete CUG while changing only its suffix and translates a duplicate rejection", async () => {
  const onPersistResult = jest.fn();
  postToOpenElisServerJsonResponse.mockImplementation(
    (url, payload, callback) =>
      callback({
        error: "Invalid Request",
        message: "sample.cug.sample.duplicate",
      }),
  );
  const view = show({ onPersistResult });
  const suffix = view.getByLabelText(messages["sample.cug.suffix.label"]);
  fireEvent.change(suffix, { target: { value: "3" } });
  fireEvent.click(
    view.container.querySelector("tbody .cds--table-expand__button"),
  );
  fireEvent.click(
    view.getByRole("button", {
      name: messages["sample.management.action.save"],
    }),
  );
  await wait(() =>
    expect(postToOpenElisServerJsonResponse).toHaveBeenCalledTimes(1),
  );
  const [url, body] = postToOpenElisServerJsonResponse.mock.calls[0];
  expect(url).toBe("/rest/sample-management/save-changes");
  expect(JSON.parse(body).sampleUpdates[0].cugCode).toBe("5004.3");
  expect(onPersistResult).toHaveBeenCalledWith({
    success: false,
    message: messages["sample.cug.sample.duplicate"],
  });
  expect(suffix.value).toBe("3");
  expect(view.getByText("5004.")).toBeTruthy();
});

test.each([
  { isReadOnly: true },
  { sampleItems: [{ ...sample, editable: false }] },
])("keeps suffix read-only when sample editing is forbidden: %j", (props) => {
  const view = show(props);
  expect(
    view.getByLabelText(messages["sample.cug.suffix.label"]).readOnly,
  ).toBe(true);
});
