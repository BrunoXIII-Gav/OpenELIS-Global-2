import React from "react";
import { useIntl } from "react-intl";
import { redirectToHome } from "./accessDenied";

function AccessDeniedPanel() {
  const intl = useIntl();

  return (
    <div
      style={{
        minHeight: "60vh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: "2rem",
      }}
    >
      <div
        style={{
          maxWidth: "26rem",
          background: "#ffffff",
          padding: "2rem",
          borderRadius: "0.5rem",
          boxShadow: "0 8px 32px rgba(0, 0, 0, 0.16)",
        }}
      >
        <h2 style={{ marginTop: 0, marginBottom: "0.75rem" }}>
          {intl.formatMessage({ id: "accessDenied.title" })}
        </h2>
        <p style={{ marginBottom: "1rem" }}>
          {intl.formatMessage({ id: "accessDenied.message" })}
        </p>
        <button
          type="button"
          onClick={redirectToHome}
          style={{
            border: "none",
            borderRadius: "0.25rem",
            padding: "0.65rem 1rem",
            cursor: "pointer",
            background: "#393939",
            color: "#ffffff",
          }}
        >
          {intl.formatMessage({ id: "accessDenied.okButton" })}
        </button>
      </div>
    </div>
  );
}

export default AccessDeniedPanel;
