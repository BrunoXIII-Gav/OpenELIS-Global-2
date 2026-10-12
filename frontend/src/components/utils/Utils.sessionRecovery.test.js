import { wait } from "@testing-library/react";
import {
  getFromOpenElisServer,
  postToOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "./Utils";
import {
  dismissSessionRecovery,
  SESSION_RECOVERY_EVENT,
} from "../security/sessionRecovery";
import { emitAccessDeniedEvent } from "../security/accessDenied";

jest.mock("../security/accessDenied", () => ({
  emitAccessDeniedEvent: jest.fn(),
}));

describe("Shared API calls with an expired SAML session", () => {
  const originalFetch = global.fetch;
  let listener;
  let callback;
  const response = (status, url = "/api/OpenELIS-Global/rest/TestAdd") => ({
    status,
    ok: status >= 200 && status < 300,
    url,
    headers: new Headers({ "Content-Type": "application/json" }),
    json: jest.fn().mockResolvedValue({ labUnitList: [] }),
  });

  beforeEach(() => {
    dismissSessionRecovery();
    sessionStorage.clear();
    sessionStorage.setItem("openelis.lastLoginMethod", "SAML");
    global.fetch = jest.fn();
    callback = jest.fn();
    listener = jest.fn();
    window.addEventListener(SESSION_RECOVERY_EVENT, listener);
    emitAccessDeniedEvent.mockClear();
  });
  afterEach(() => {
    global.fetch = originalFetch;
    window.removeEventListener(SESSION_RECOVERY_EVENT, listener);
    sessionStorage.clear();
    dismissSessionRecovery();
  });

  it.each([401, 200])(
    "offers recovery for status %s without giving login HTML to the form",
    async (status) => {
      const login = response(status, "/api/OpenELIS-Global/LoginPage");
      login.headers = new Headers({ "Content-Type": "text/html" });
      global.fetch.mockResolvedValue(login);
      getFromOpenElisServer("/rest/TestAdd", callback);
      await wait(() => expect(listener).toHaveBeenCalledTimes(1));
      expect(callback).not.toHaveBeenCalled();
      expect(login.json).not.toHaveBeenCalled();
    },
  );

  it.each([403, 500])(
    "continues normal error handling for status %s",
    async (status) => {
      global.fetch.mockResolvedValue(response(status));
      getFromOpenElisServer("/rest/TestAdd", callback);
      await wait(() => expect(callback).toHaveBeenCalledWith(undefined));
      expect(listener).not.toHaveBeenCalled();
      expect(emitAccessDeniedEvent).toHaveBeenCalledTimes(
        status === 403 ? 1 : 0,
      );
    },
  );

  it.each([postToOpenElisServer, postToOpenElisServerJsonResponse])(
    "does not report a successful save or automatically repeat an expired POST",
    async (post) => {
      global.fetch.mockResolvedValue(
        response(200, "/api/OpenELIS-Global/LoginPage"),
      );
      post("/rest/TestModifyEntry", "{}", callback);
      await wait(() => expect(listener).toHaveBeenCalledTimes(1));
      expect(callback).not.toHaveBeenCalled();
      expect(global.fetch).toHaveBeenCalledTimes(1);
      expect(global.fetch).toHaveBeenCalledWith(
        "/api/OpenELIS-Global/rest/TestModifyEntry",
        expect.objectContaining({ method: "POST" }),
      );
    },
  );

  it("still delivers valid JSON", async () => {
    global.fetch.mockResolvedValue(response(200));
    getFromOpenElisServer("/rest/TestAdd", callback);
    await wait(() =>
      expect(callback).toHaveBeenCalledWith({ labUnitList: [] }),
    );
    expect(listener).not.toHaveBeenCalled();
  });

  it("does not force SSO for a local password login", async () => {
    sessionStorage.setItem("openelis.lastLoginMethod", "FORM");
    global.fetch.mockResolvedValue(response(401));
    getFromOpenElisServer("/rest/TestAdd", callback);
    await wait(() => expect(callback).toHaveBeenCalledWith(undefined));
    expect(listener).not.toHaveBeenCalled();
  });
});
