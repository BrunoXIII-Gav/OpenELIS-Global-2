import React from "react";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { MemoryRouter } from "react-router-dom";
import UserAddModify from "./UserAddModify";
import messages from "../../../languages/en.json";
import { ConfigurationContext, NotificationContext } from "../../layout/Layout";
import {
  getFromOpenElisServer,
  getFromOpenElisServerV2,
  postToOpenElisServerJsonResponse,
} from "../../utils/Utils";

jest.mock("../../layout/Layout", () => {
  const React = require("react");
  return {
    ConfigurationContext: React.createContext({}),
    NotificationContext: React.createContext({}),
  };
});
jest.mock("../../utils/Utils", () => ({
  getFromOpenElisServer: jest.fn(),
  getFromOpenElisServerV2: jest.fn(),
  postToOpenElisServerJsonResponse: jest.fn(),
  toBase64: jest.fn(),
}));
jest.mock("../../common/PageBreadCrumb", () => () => null);
jest.mock("../../common/CustomNotification", () => ({
  AlertDialog: () => null,
  NotificationKinds: { info: "info", success: "success", error: "error" },
}));
jest.mock("../../common/CustomDatePicker", () => () => null);

const profiles = ["MEDICAL_DOCTOR", "BIOLOGIST"];
const account = (profile) => ({
  systemUserId: "113",
  loginUserId: "5",
  userLoginName: "ernesto",
  userFirstName: "Ernesto",
  userLastName: "Valverde",
  userPassword: "Example#1",
  confirmPassword: "Example#1",
  timeout: "480",
  expirationDate: "07/06/2036",
  accountActive: "Y",
  accountLocked: "N",
  accountDisabled: "N",
  selectedRoles: [],
  selectedCustomRoleIds: ["118"],
  selectedTestSectionLabUnits: {},
  testSections: [],
  globalRoles: [],
  labUnitRoles: [],
  customRoles: [
    { roleId: "118", elementID: "118", roleName: "Operational role" },
  ],
  professionalProfileCode: profile,
  linkedProviderPersonId: "22",
  professionalInfo: {
    providerId: "8",
    professionalProfileCode: profile,
    dni: "12345678",
    professionalInitials: "EV",
    telephone: "999999999",
    email: "example@example.com",
    fax: "111",
    specialty: "GENETICS",
    profileFieldValues: { CMP: "123", RNE: "456" },
  },
});

const show = (profile) => {
  getFromOpenElisServer.mockImplementation((url, callback) => {
    if (url.startsWith("/rest/UnifiedSystemUser?")) callback(account(profile));
    else if (url.startsWith("/rest/Provider/Person/"))
      callback({ firstName: "Ernesto", lastName: "Valverde" });
    else callback([]);
  });
  return render(
    <IntlProvider locale="en" messages={messages}>
      <MemoryRouter initialEntries={["/MasterListsPage/userEdit?ID=113-5"]}>
        <ConfigurationContext.Provider
          value={{
            configurationProperties: {
              professionalProfileOptions:
                "MEDICAL_DOCTOR|Doctor,BIOLOGIST|Biologist",
            },
          }}
        >
          <NotificationContext.Provider
            value={{
              notificationVisible: false,
              setNotificationVisible: jest.fn(),
              addNotification: jest.fn(),
            }}
          >
            <UserAddModify />
          </NotificationContext.Provider>
        </ConfigurationContext.Provider>
      </MemoryRouter>
    </IntlProvider>,
  );
};

beforeEach(() => {
  jest.clearAllMocks();
  getFromOpenElisServerV2.mockResolvedValue({ duplicate: false });
});

test.each(profiles)(
  "%s keeps only the professional profile and link in Users",
  async (profile) => {
    const { container } = show(profile);
    const link = await screen.findByRole("link", {
      name: "Professional Management",
    });
    expect(link).toHaveAttribute("href", "/MasterListsPage/providerMenu");
    expect(container.querySelector("#professional-profile-code")).toHaveValue(
      profile,
    );
    expect(
      container.querySelector("#linked-provider-person"),
    ).toBeInTheDocument();
    expect(container.querySelector('[id^="user-professional-"]')).toBeNull();
    expect(screen.queryByDisplayValue("12345678")).not.toBeInTheDocument();
    expect(
      screen.queryByText("Edit professional details here"),
    ).not.toBeInTheDocument();
  },
);

test.each(profiles)(
  "saving %s role changes preserves its link without submitting the professional record",
  async (profile) => {
    show(profile);
    await screen.findByRole("link", { name: "Professional Management" });
    fireEvent.click(screen.getByRole("checkbox", { name: "Operational role" }));
    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Save" }));
    });
    expect(postToOpenElisServerJsonResponse).toHaveBeenCalledTimes(1);
    const [url, body] = postToOpenElisServerJsonResponse.mock.calls[0];
    const payload = JSON.parse(body);
    expect(url).toBe("/rest/UnifiedSystemUser");
    expect(payload.professionalProfileCode).toBe(profile);
    expect(payload.linkedProviderPersonId).toBe("22");
    expect(payload.selectedCustomRoleIds).toEqual([]);
    expect(payload).not.toHaveProperty("professionalInfo");
    expect(
      getFromOpenElisServer.mock.calls.some(
        ([request]) =>
          request.startsWith("/rest/user-professional/") ||
          request.startsWith("/rest/providers/form-config"),
      ),
    ).toBe(false);
  },
);
