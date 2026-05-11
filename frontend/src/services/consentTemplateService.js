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
