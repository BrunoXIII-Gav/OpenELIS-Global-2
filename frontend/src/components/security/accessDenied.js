import { confirmAlert } from "react-confirm-alert";

export const ACCESS_DENIED_EVENT = "openelis:access-denied";

export const redirectToHome = () => {
  window.location.href = window.location.origin;
};

export const emitAccessDeniedEvent = (detail = {}) => {
  if (typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(
    new CustomEvent(ACCESS_DENIED_EVENT, {
      detail,
    }),
  );
};

export const showAccessDeniedDialog = (intl, options = {}) => {
  const { onAcknowledge, closeOnClickOutside = false, closeOnEscape = false } =
    options;

  confirmAlert({
    title: intl.formatMessage({ id: "accessDenied.title" }),
    message: intl.formatMessage({ id: "accessDenied.message" }),
    buttons: [
      {
        label: intl.formatMessage({ id: "accessDenied.okButton" }),
        onClick: onAcknowledge,
      },
    ],
    closeOnClickOutside,
    closeOnEscape,
  });
};
