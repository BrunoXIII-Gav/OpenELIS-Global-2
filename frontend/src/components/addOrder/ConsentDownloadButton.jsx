import React, { useContext, useRef, useState } from "react";
import { Button } from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import { ConfigurationContext, NotificationContext } from "../layout/Layout";
import { NotificationKinds } from "../common/CustomNotification";
import { getConsentTemplateOptions } from "../../services/consentTemplateService";
import {
  getFromOpenElisServer,
  postToOpenElisServerForBlob,
} from "../utils/Utils";
import { buildConsentPayload } from "./consentPayload";

const ConsentDownloadButton = ({
  orderFormValues,
  samples,
  patientId,
  disabled = false,
}) => {
  const intl = useIntl();
  const { configurationProperties } = useContext(ConfigurationContext);
  const { addNotification, setNotificationVisible } =
    useContext(NotificationContext);
  const [downloading, setDownloading] = useState(false);
  const inFlight = useRef(false);

  const download = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    setDownloading(true);
    let errorKey = "consent.download.error";
    try {
      const options = await new Promise((resolve) =>
        getConsentTemplateOptions(resolve),
      );
      if (!Array.isArray(options?.sources))
        throw new Error("Consent sources unavailable");
      let form = orderFormValues;
      if (patientId) {
        const patient = await new Promise((resolve) =>
          getFromOpenElisServer(
            `/rest/patient-details?patientID=${encodeURIComponent(patientId)}`,
            resolve,
          ),
        );
        if (
          !patient?.patientPK ||
          String(patient.patientPK) !== String(patientId)
        )
          throw new Error("Patient details unavailable");
        form = { ...form, patientProperties: patient };
      }
      const payload = buildConsentPayload(
        options.sources,
        form,
        samples,
        intl,
        configurationProperties,
      );
      if (!payload.selectedTests.length) {
        errorKey = "consent.download.noTests";
        throw new Error("No selected tests");
      }
      const blob = await new Promise((resolve, reject) =>
        postToOpenElisServerForBlob(
          "/rest/reports/consent-template/preview",
          JSON.stringify(payload),
          resolve,
          reject,
        ),
      );
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      try {
        link.href = url;
        link.download = "consent-template.pdf";
        document.body.appendChild(link);
        link.click();
      } finally {
        link.remove();
        window.URL.revokeObjectURL(url);
      }
    } catch (_) {
      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: errorKey }),
        kind: NotificationKinds.error,
      });
      setNotificationVisible(true);
    } finally {
      inFlight.current = false;
      setDownloading(false);
    }
  };

  return (
    <Button
      type="button"
      kind="secondary"
      onClick={download}
      disabled={disabled || downloading}
    >
      <FormattedMessage id="consent.download.button" />
    </Button>
  );
};

export default ConsentDownloadButton;
