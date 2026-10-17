import config from "../config.json";
import {
  getFromOpenElisServer,
  putToOpenElisServerFullResponse,
} from "../components/utils/Utils";

export const getConsentTemplateConfig = (callback) => {
  getFromOpenElisServer("/rest/reports/consent-template", callback);
};

export const getConsentTemplateOptions = (callback) => {
  getFromOpenElisServer("/rest/reports/consent-template/options", callback);
};

export const saveConsentTemplateConfig = (payload, callback) => {
  putToOpenElisServerFullResponse(
    "/rest/reports/consent-template",
    JSON.stringify(payload),
    (response) => {
      if (!response) {
        callback(null, response);
        return;
      }
      response
        .json()
        .then((json) => callback(json, response))
        .catch(() => callback(null, response));
    },
  );
};

export const parseConsentTemplate = async (file) => {
  const body = new FormData();
  body.append("file", file);
  const response = await fetch(
    config.serverBaseUrl + "/rest/reports/consent-template/parse-template",
    {
      method: "POST",
      credentials: "include",
      headers: { "X-CSRF-Token": localStorage.getItem("CSRF") },
      body,
    },
  );
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result?.error || "consent.template.error.invalid");
  }
  return result;
};

export const downloadConsentTemplate = async () => {
  const response = await fetch(
    config.serverBaseUrl + "/rest/reports/consent-template/download-template",
    { credentials: "include" },
  );
  if (!response.ok) throw new Error("consent.template.error.download");
  return response.blob();
};
