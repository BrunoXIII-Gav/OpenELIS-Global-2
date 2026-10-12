import {
  beginSamlSessionRecovery,
  dismissSessionRecovery,
  finishSamlSessionRecovery,
  isSessionExpiredResponse,
  isSessionRecoveryPending,
  requestSamlSessionRecovery,
  SESSION_RECOVERY_EVENT,
} from "./sessionRecovery";

describe("OpenELIS SAML session recovery", () => {
  const originalLocation = window.location;
  let listener;

  beforeEach(() => {
    sessionStorage.clear();
    dismissSessionRecovery();
    delete window.location;
    window.location = {
      origin: "https://openelis.example",
      pathname: "/MasterListsPage/TestModifyEntry",
      search: "?sampleType=38",
      hash: "",
      replace: jest.fn(),
    };
    listener = jest.fn();
    window.addEventListener(SESSION_RECOVERY_EVENT, listener);
  });

  afterEach(() => {
    window.removeEventListener(SESSION_RECOVERY_EVENT, listener);
    window.location = originalLocation;
    sessionStorage.clear();
    dismissSessionRecovery();
  });

  it("offers one recovery prompt without navigating or clearing the SSO identity", () => {
    sessionStorage.setItem("openelis.lastLoginMethod", "SAML");
    expect(requestSamlSessionRecovery()).toBe(true);
    expect(requestSamlSessionRecovery()).toBe(true);
    expect(listener).toHaveBeenCalledTimes(1);
    expect(isSessionRecoveryPending()).toBe(true);
    expect(window.location.replace).not.toHaveBeenCalled();
    expect(sessionStorage.getItem("openelis.lastLoginMethod")).toBe("SAML");
  });

  it.each(["openelis.loggedOutToPortal", "openelis.portalSamlLaunch"])(
    "does not restart authentication when %s is set",
    (flag) => {
      sessionStorage.setItem("openelis.lastLoginMethod", "SAML");
      sessionStorage.setItem(flag, "1");
      expect(requestSamlSessionRecovery()).toBe(false);
      expect(listener).not.toHaveBeenCalled();
    },
  );

  it("keeps local password authentication unchanged", () => {
    sessionStorage.setItem("openelis.lastLoginMethod", "FORM");
    expect(requestSamlSessionRecovery()).toBe(false);
    expect(listener).not.toHaveBeenCalled();
  });

  it.each(["https://openelis.jellbru.lat", "https://openmo.jellbru.lat"])(
    "uses the existing launch on %s and returns to the original page",
    (origin) => {
      window.location.origin = origin;
      beginSamlSessionRecovery();
      expect(window.location.replace).toHaveBeenCalledWith("/saml-launch.html");
      expect(sessionStorage.getItem("openelis.portalSamlLaunch")).toBe("1");

      window.location.pathname = "/";
      window.location.search = "";
      window.location.replace.mockClear();
      finishSamlSessionRecovery();
      expect(window.location.replace).toHaveBeenCalledWith(
        "/MasterListsPage/TestModifyEntry?sampleType=38",
      );
      expect(
        sessionStorage.getItem("openelis.samlRecoveryReturnPath"),
      ).toBeNull();
      expect(isSessionRecoveryPending()).toBe(false);
    },
  );

  it.each([
    "https://other.example/",
    "//other.example/",
    "/\\other.example/",
    "/api/OpenELIS-Global/Logout",
    "/login",
    "/saml-launch.html",
  ])("rejects an unsafe recovery return path %s", (path) => {
    sessionStorage.setItem("openelis.samlRecoveryReturnPath", path);
    finishSamlSessionRecovery();
    expect(window.location.replace).not.toHaveBeenCalled();
    expect(
      sessionStorage.getItem("openelis.samlRecoveryReturnPath"),
    ).toBeNull();
  });

  it("does not reload when authentication already returned to the same page", () => {
    beginSamlSessionRecovery();
    window.location.replace.mockClear();
    finishSamlSessionRecovery();
    expect(window.location.replace).not.toHaveBeenCalled();
  });

  it("distinguishes login redirects and 401 from permissions and server errors", () => {
    expect(isSessionExpiredResponse({ status: 401 })).toBe(true);
    expect(
      isSessionExpiredResponse({
        status: 200,
        url: "/api/OpenELIS-Global/LoginPage?lang=es",
      }),
    ).toBe(true);
    expect(isSessionExpiredResponse({ status: 403 })).toBe(false);
    expect(isSessionExpiredResponse({ status: 500 })).toBe(false);
    expect(
      isSessionExpiredResponse(
        { status: 200, url: "/api/OpenELIS-Global/LoginPage" },
        "/LoginPage?lang=es",
      ),
    ).toBe(false);
  });
});
