import React from "react";
import { render, wait, cleanup, screen } from "@testing-library/react";
import { IntlProvider } from "react-intl";
import { MemoryRouter } from "react-router-dom";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import { ConfigurationContext } from "../layout/Layout";
import SecureRoute from "./SecureRoute";
import { emitAccessDeniedEvent } from "./accessDenied";
import {
  dismissSessionRecovery,
  requestSamlSessionRecovery,
  SESSION_RECOVERY_EVENT,
} from "./sessionRecovery";
import { useIdleTimer } from "react-idle-timer";
import messages from "../../languages/en.json";

jest.mock("../layout/Layout", () => ({
  ConfigurationContext: require("react").createContext({}),
}));
jest.mock("./accessDenied", () => ({ emitAccessDeniedEvent: jest.fn() }));
jest.mock("react-idle-timer", () => ({
  useIdleTimer: jest.fn(() => ({ activate: jest.fn() })),
}));

describe("Protected routes after SAML session expiry", () => {
  const originalFetch = global.fetch;
  let listener;
  let logout;
  beforeEach(() => {
    sessionStorage.clear();
    sessionStorage.setItem("openelis.lastLoginMethod", "SAML");
    dismissSessionRecovery();
    global.fetch = jest.fn();
    logout = jest.fn();
    listener = jest.fn();
    window.addEventListener(SESSION_RECOVERY_EVENT, listener);
    emitAccessDeniedEvent.mockClear();
    useIdleTimer.mockReturnValue({ activate: jest.fn() });
  });
  afterEach(() => {
    cleanup();
    global.fetch = originalFetch;
    window.removeEventListener(SESSION_RECOVERY_EVENT, listener);
    sessionStorage.clear();
    dismissSessionRecovery();
  });
  const showRoute = () =>
    render(
      <IntlProvider locale="en" messages={messages}>
        <MemoryRouter initialEntries={["/Storage/samples"]}>
          <UserSessionDetailsContext.Provider
            value={{
              userSessionDetails: {
                authenticated: true,
                loginMethod: "SAML",
                roles: [],
              },
              isCheckingLogin: () => false,
              errorLoadingSessionDetails: false,
              logout,
            }}
          >
            <ConfigurationContext.Provider
              value={{ configurationProperties: {} }}
            >
              <SecureRoute
                path="/Storage/samples"
                component={() => <span>Protected screen</span>}
              />
            </ConfigurationContext.Provider>
          </UserSessionDetailsContext.Provider>
        </MemoryRouter>
      </IntlProvider>,
    );

  it.each([401, 200])(
    "offers recovery, not access denied, on an expired response with status %s",
    async (status) => {
      global.fetch.mockResolvedValue({
        status,
        ok: status === 200,
        url: "/api/OpenELIS-Global/LoginPage",
      });
      showRoute();
      await wait(() => expect(listener).toHaveBeenCalledTimes(1));
      expect(emitAccessDeniedEvent).not.toHaveBeenCalled();
      expect(logout).not.toHaveBeenCalled();
    },
  );

  it("keeps denied permissions denied rather than offering session recovery", async () => {
    global.fetch.mockResolvedValue({ status: 403, ok: false });
    showRoute();
    await wait(() => expect(emitAccessDeniedEvent).toHaveBeenCalledTimes(1));
    expect(listener).not.toHaveBeenCalled();
  });

  it("does not request logout while the session recovery prompt is pending", async () => {
    global.fetch.mockResolvedValue({
      status: 200,
      ok: true,
      json: async () => ({ allowed: true }),
    });
    showRoute();
    await wait(() => expect(screen.getByText("Protected screen")).toBeTruthy());
    requestSamlSessionRecovery();
    const timerOptions =
      useIdleTimer.mock.calls[useIdleTimer.mock.calls.length - 1][0];
    timerOptions.onIdle();
    expect(logout).not.toHaveBeenCalled();
  });
});
