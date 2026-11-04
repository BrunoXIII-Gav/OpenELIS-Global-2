import React, { useEffect, useState } from "react";
import { Modal } from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  beginSamlSessionRecovery,
  dismissSessionRecovery,
  isSessionRecoveryPending,
  SESSION_RECOVERY_EVENT,
} from "./sessionRecovery";

export default function SessionRecoveryModal() {
  const intl = useIntl();
  const [open, setOpen] = useState(isSessionRecoveryPending);

  useEffect(() => {
    const showRecovery = () => setOpen(true);
    window.addEventListener(SESSION_RECOVERY_EVENT, showRecovery);
    // A request can expire before the modal's effect subscribes.
    setOpen(isSessionRecoveryPending());
    return () =>
      window.removeEventListener(SESSION_RECOVERY_EVENT, showRecovery);
  }, []);

  return (
    <Modal
      open={open}
      modalHeading={intl.formatMessage({ id: "session.recovery.title" })}
      primaryButtonText={intl.formatMessage({ id: "session.recovery.action" })}
      onRequestSubmit={beginSamlSessionRecovery}
      preventCloseOnClickOutside
      onRequestClose={() => {
        dismissSessionRecovery();
        setOpen(false);
      }}
    >
      <p>
        <FormattedMessage id="session.recovery.message" />
      </p>
      <p>
        <FormattedMessage id="session.recovery.unsaved" />
      </p>
    </Modal>
  );
}
