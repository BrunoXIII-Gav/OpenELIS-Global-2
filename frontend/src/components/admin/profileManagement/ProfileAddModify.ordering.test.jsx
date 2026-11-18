import React from "react";
import { fireEvent, render } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { MemoryRouter } from "react-router-dom";
import ProfileAddModify from "./ProfileAddModify";
import { NotificationContext } from "../../layout/Layout";
import {
  getFromOpenElisServer,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils";
import messages from "../../../languages/es.json";

jest.mock("../../layout/Layout", () => ({
  NotificationContext: require("react").createContext({}),
}));
jest.mock("../../common/PageBreadCrumb", () => () => null);
jest.mock("../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  putToOpenElisServerFullResponse: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
}));

test("profile fields can be moved without changing their keys or saved data", () => {
  getFromOpenElisServer.mockImplementation((_url, callback) =>
    callback({
      code: "DOCTOR",
      name: "Médico",
      fields: [
        {
          fieldKey: "CMP",
          displayName: "CMP",
          fieldType: "TEXT",
          showInOrderEntry: true,
          hasSavedValues: true,
        },
        {
          fieldKey: "RNE",
          displayName: "RNE",
          fieldType: "TEXT",
          showInOrderEntry: true,
          hasSavedValues: true,
        },
      ],
    }),
  );
  const view = render(
    <IntlProvider locale="es" messages={messages}>
      <MemoryRouter
        initialEntries={["/MasterListsPage/profileEdit?code=DOCTOR"]}
      >
        <NotificationContext.Provider
          value={{
            addNotification: jest.fn(),
            setNotificationVisible: jest.fn(),
          }}
        >
          <ProfileAddModify />
        </NotificationContext.Provider>
      </MemoryRouter>
    </IntlProvider>,
  );
  fireEvent.click(view.getByRole("button", { name: "Subir RNE" }));
  expect(view.container.querySelector("#field-display-name-0").value).toBe(
    "RNE",
  );
  fireEvent.click(view.getByText(messages["button.save"]));
  const payload = JSON.parse(putToOpenElisServerFullResponse.mock.calls[0][1]);
  expect(
    payload.fields.map(({ fieldKey, sortOrder }) => ({ fieldKey, sortOrder })),
  ).toEqual([
    { fieldKey: "RNE", sortOrder: 0 },
    { fieldKey: "CMP", sortOrder: 1 },
  ]);
});
