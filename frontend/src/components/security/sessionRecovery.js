export const SESSION_RECOVERY_EVENT = "openelis:session-recovery";
const RETURN_PATH_KEY = "openelis.samlRecoveryReturnPath";
let recoveryPending = false;

export const isSessionRecoveryPending = () => recoveryPending;

export const dismissSessionRecovery = () => {
  recoveryPending = false;
};

export const requestSamlSessionRecovery = (loginMethod) => {
  if (
    (loginMethod || sessionStorage.getItem("openelis.lastLoginMethod")) !==
      "SAML" ||
    sessionStorage.getItem("openelis.loggedOutToPortal") === "1" ||
    sessionStorage.getItem("openelis.portalSamlLaunch") === "1"
  ) {
    return false;
  }
  if (!recoveryPending) {
    recoveryPending = true;
    window.dispatchEvent(new CustomEvent(SESSION_RECOVERY_EVENT));
  }
  return true;
};

const safeReturnPath = (path) => {
  if (!path || !path.startsWith("/") || path.startsWith("//")) {
    return null;
  }
  try {
    const url = new URL(path, window.location.origin);
    if (
      url.origin !== window.location.origin ||
      /^\/(?:api(?:\/|$)|login(?:\/|$)|saml-launch\.html(?:\/|$))/.test(
        url.pathname,
      )
    ) {
      return null;
    }
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return null;
  }
};

export const beginSamlSessionRecovery = () => {
  const path = safeReturnPath(
    `${window.location.pathname}${window.location.search}${window.location.hash}`,
  );
  if (path) {
    sessionStorage.setItem(RETURN_PATH_KEY, path);
  } else {
    sessionStorage.removeItem(RETURN_PATH_KEY);
  }
  // Reuse the normal launch: the backend resolves the configured SAML provider.
  sessionStorage.setItem("openelis.portalSamlLaunch", "1");
  window.location.replace("/saml-launch.html");
};

export const finishSamlSessionRecovery = () => {
  recoveryPending = false;
  const path = safeReturnPath(sessionStorage.getItem(RETURN_PATH_KEY));
  sessionStorage.removeItem(RETURN_PATH_KEY);
  if (
    path &&
    path !==
      `${window.location.pathname}${window.location.search}${window.location.hash}`
  ) {
    window.location.replace(path);
  }
};

export const isSessionExpiredResponse = (response, endPoint = "") => {
  const isLoginPage = (url) => {
    try {
      return /\/LoginPage\/?$/.test(
        new URL(url, window.location.origin).pathname,
      );
    } catch {
      return false;
    }
  };
  return (
    !isLoginPage(endPoint) &&
    (response.status === 401 || isLoginPage(response.url))
  );
};

export class SamlSessionRecoveryRequired extends Error {}
