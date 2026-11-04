import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import TestSectionOriginConfig from "./TestSectionOriginConfig";
import messages from "../../../languages/es.json";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../../utils/Utils";

jest.mock("../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
}));

const mount = () =>
  render(
    <IntlProvider locale="es" messages={messages}>
      <TestSectionOriginConfig />
    </IntlProvider>,
  );
beforeEach(() => jest.clearAllMocks());

test.each([true, false])(
  "classifies selected legacy units with userCreated=%s and refreshes the list",
  (userCreated) => {
    let saved = false;
    getFromOpenElisServer.mockImplementation((url, callback) =>
      callback(
        url.endsWith("/availability")
          ? { available: true }
          : saved
            ? []
            : [{ id: "27", name: "Mi unidad" }],
      ),
    );
    postToOpenElisServerJsonResponse.mockImplementation(
      (url, body, callback) => {
        saved = true;
        callback({ classified: 1 });
      },
    );
    const { container } = mount();
    fireEvent.click(
      container.querySelector("#test-section-origin-selection button"),
    );
    fireEvent.click(screen.getByText("Mi unidad"));
    fireEvent.click(
      screen.getByText(
        messages[`testSection.origin.${userCreated ? "user" : "system"}`],
      ),
    );
    expect(postToOpenElisServerJsonResponse).toHaveBeenCalledWith(
      "/rest/test-section-origins",
      JSON.stringify({ sectionIds: ["27"], userCreated }),
      expect.any(Function),
    );
    expect(screen.getByText(messages["testSection.origin.saved"])).toBeTruthy();
    expect(
      container.querySelector("#test-section-origin-selection"),
    ).toBeNull();
  },
);

test("does not fetch origins when administration access is unavailable", () => {
  getFromOpenElisServer.mockImplementation((url, callback) =>
    callback({ available: false }),
  );
  const { container } = mount();
  expect(container.textContent).toBe("");
  expect(getFromOpenElisServer).toHaveBeenCalledTimes(1);
});

test("conflicting classification shows an error and preserves the selection", () => {
  getFromOpenElisServer.mockImplementation((url, callback) =>
    callback(
      url.endsWith("/availability")
        ? { available: true }
        : [{ id: "27", name: "Mi unidad" }],
    ),
  );
  postToOpenElisServerJsonResponse.mockImplementation((url, body, callback) =>
    callback({ error: "testPackage.issue.conflicts" }),
  );
  const { container } = mount();
  fireEvent.click(
    container.querySelector("#test-section-origin-selection button"),
  );
  fireEvent.click(screen.getByText("Mi unidad"));
  fireEvent.click(screen.getByText(messages["testSection.origin.user"]));
  expect(screen.getByText(messages["testSection.origin.error"])).toBeTruthy();
  expect(screen.queryByText(messages["testSection.origin.saved"])).toBeNull();
});
