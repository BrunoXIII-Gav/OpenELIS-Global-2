import React from "react";
import { fireEvent, render, screen, wait } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { MemoryRouter } from "react-router-dom";
import { NotificationContext } from "../../layout/Layout";
import messages from "../../../languages/en.json";
import SampleTypeAdditionalFields from "./SampleTypeAdditionalFields";
import {
  deleteFromOpenElisServer,
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
  putToOpenElisServer,
} from "../../utils/Utils";

jest.mock("../../utils/Utils", () => ({
  deleteFromOpenElisServer: jest.fn(),
  getFromOpenElisServer: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
  putToOpenElisServer: jest.fn(),
}));

const notificationContextValue = {
  notificationVisible: false,
  setNotificationVisible: jest.fn(),
  addNotification: jest.fn(),
};

const renderComponent = () =>
  render(
    <MemoryRouter>
      <IntlProvider locale="en" messages={messages}>
        <NotificationContext.Provider value={notificationContextValue}>
          <SampleTypeAdditionalFields />
        </NotificationContext.Provider>
      </IntlProvider>
    </MemoryRouter>,
  );

describe("SampleTypeAdditionalFields", () => {
  beforeEach(() => {
    jest.clearAllMocks();

    getFromOpenElisServer.mockImplementation((url, callback) => {
      if (url === "/rest/sample-type-additional-fields/sample-types") {
        callback([
          { id: "1", value: "Blood" },
          { id: "2", value: "Serum" },
        ]);
        return;
      }

      if (
        url ===
        "/rest/sample-type-additional-fields?sampleTypeId=1&includeInactive=true"
      ) {
        callback([]);
        return;
      }

      callback([]);
    });

    postToOpenElisServerJsonResponse.mockImplementation(
      (url, payload, callback) => {
        callback({ status: 201, id: 100 });
      },
    );

    deleteFromOpenElisServer.mockImplementation((url, callback) => {
      callback(204);
    });

    putToOpenElisServer.mockImplementation((url, payload, callback) => {
      callback(200);
    });
  });

  test("fetches fields when sample type is selected", async () => {
    const { container } = renderComponent();

    await wait(() => {
      expect(getFromOpenElisServer).toHaveBeenCalledWith(
        "/rest/sample-type-additional-fields/sample-types",
        expect.any(Function),
      );
    });

    const sampleTypeSelector = container.querySelector(
      "#sampleTypeAdditionalFieldsSelector",
    );
    fireEvent.change(sampleTypeSelector, { target: { value: "1" } });

    await wait(() => {
      expect(getFromOpenElisServer).toHaveBeenCalledWith(
        "/rest/sample-type-additional-fields?sampleTypeId=1&includeInactive=true",
        expect.any(Function),
      );
    });
  });

  test("does not post when sample type is not selected", async () => {
    const { container } = renderComponent();

    await wait(() => {
      expect(getFromOpenElisServer).toHaveBeenCalledWith(
        "/rest/sample-type-additional-fields/sample-types",
        expect.any(Function),
      );
    });

    fireEvent.change(
      container.querySelector("#sampleAdditionalFieldDisplayName"),
      {
        target: { value: "Batch Code" },
      },
    );

    const createButton = screen.getByText("Create Field");
    expect(createButton.disabled).toBe(true);
    fireEvent.click(createButton);

    expect(postToOpenElisServerJsonResponse).not.toHaveBeenCalled();
    expect(notificationContextValue.addNotification).not.toHaveBeenCalled();
  });

  test("reactivates inactive field", async () => {
    getFromOpenElisServer.mockImplementation((url, callback) => {
      if (url === "/rest/sample-type-additional-fields/sample-types") {
        callback([{ id: "1", value: "Blood" }]);
        return;
      }

      if (
        url ===
        "/rest/sample-type-additional-fields?sampleTypeId=1&includeInactive=true"
      ) {
        callback([
          {
            id: 12,
            displayName: "Comentario",
            fieldKey: "comentario",
            fieldType: "TEXT",
            required: false,
            active: false,
            options: [],
          },
        ]);
        return;
      }

      callback([]);
    });

    const { container } = renderComponent();

    await wait(() => {
      expect(getFromOpenElisServer).toHaveBeenCalledWith(
        "/rest/sample-type-additional-fields/sample-types",
        expect.any(Function),
      );
    });

    const sampleTypeSelector = container.querySelector(
      "#sampleTypeAdditionalFieldsSelector",
    );
    fireEvent.change(sampleTypeSelector, { target: { value: "1" } });

    await wait(() => {
      expect(screen.getByText("Activate")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("Activate"));

    await wait(() => {
      expect(putToOpenElisServer).toHaveBeenCalledWith(
        "/rest/sample-type-additional-fields/12",
        JSON.stringify({ active: true }),
        expect.any(Function),
      );
    });
  });
});
