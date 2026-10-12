import React from "react";
import {
  render,
  screen,
  fireEvent,
  act,
  cleanup,
} from "@testing-library/react";
import { IntlProvider } from "react-intl";
import SessionRecoveryModal from "./SessionRecoveryModal";
import {
  dismissSessionRecovery,
  requestSamlSessionRecovery,
} from "./sessionRecovery";
import messages from "../../languages/es.json";

describe("Session recovery prompt", () => {
  const originalLocation = window.location;
  beforeEach(() => {
    dismissSessionRecovery();
    sessionStorage.clear();
    sessionStorage.setItem("openelis.lastLoginMethod", "SAML");
    delete window.location;
    window.location = {
      origin: "https://openelis.example",
      pathname: "/MasterListsPage/TestModifyEntry",
      search: "",
      hash: "",
      replace: jest.fn(),
    };
  });
  afterEach(() => {
    cleanup();
    window.location = originalLocation;
    sessionStorage.clear();
    dismissSessionRecovery();
  });

  const showModal = () =>
    render(
      <IntlProvider locale="es" messages={messages}>
        <SessionRecoveryModal />
      </IntlProvider>,
    );

  it("asks before reloading and launches SSO only when the user chooses recovery", () => {
    showModal();
    act(() => {
      requestSamlSessionRecovery();
    });
    expect(screen.getByText(messages["session.recovery.unsaved"])).toBeTruthy();
    expect(window.location.replace).not.toHaveBeenCalled();
    fireEvent.click(screen.getByText("Recuperar sesión"));
    expect(window.location.replace).toHaveBeenCalledWith("/saml-launch.html");
    expect(sessionStorage.getItem("openelis.lastLoginMethod")).toBe("SAML");
  });

  it("handles expiry detected before the modal mounts", () => {
    act(() => {
      requestSamlSessionRecovery();
    });
    showModal();
    expect(screen.getByRole("dialog").getAttribute("aria-hidden")).not.toBe(
      "true",
    );
    expect(window.location.replace).not.toHaveBeenCalled();
  });
});
