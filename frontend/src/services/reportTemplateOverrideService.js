import config from "../config.json";
import { getFromOpenElisServer } from "../components/utils/Utils";

export const getValidationTemplateOverrides = (callback) => {
  getFromOpenElisServer(
    "/rest/reports/validation-template-overrides",
    callback,
  );
};

export const getValidationReportOptions = (callback) => {
  getFromOpenElisServer(
    "/rest/reports/validation-template-overrides/report-options",
    callback,
  );
};

export const getValidationFieldOptions = (testIds = [], callback) => {
  const params =
    Array.isArray(testIds) && testIds.length > 0
      ? `?${testIds.map((id) => `testIds=${encodeURIComponent(id)}`).join("&")}`
      : "";
  getFromOpenElisServer(
    `/rest/reports/validation-template-overrides/field-options${params}`,
    callback,
  );
};

export const getAllTests = (callback) => {
  getFromOpenElisServer("/rest/tests", callback);
};

export const saveValidationTemplateOverride = async (payload) => {
  const hasId = Boolean(payload?.id);
  const endpoint = hasId
    ? `/rest/reports/validation-template-overrides/${payload.id}`
    : "/rest/reports/validation-template-overrides";

  const response = await fetch(config.serverBaseUrl + endpoint, {
    credentials: "include",
    method: hasId ? "PUT" : "POST",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Token": localStorage.getItem("CSRF"),
    },
    body: JSON.stringify(payload),
  });

  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const errorMessage =
      typeof body === "string"
        ? body
        : body?.message || body?.error || "Failed to save template override";
    throw new Error(errorMessage);
  }

  return body;
};

export const parseValidationTemplateFile = async (file) => {
  const formData = new FormData();
  formData.append("file", file);

  const response = await fetch(
    config.serverBaseUrl + "/rest/reports/validation-template-overrides/parse-template",
    {
      credentials: "include",
      method: "POST",
      headers: {
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: formData,
    },
  );

  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const errorMessage =
      typeof body === "string"
        ? body
        : body?.message || body?.error || "Failed to parse Jasper template";
    throw new Error(errorMessage);
  }

  return body;
};

export const uploadValidationTemplateImage = async (file, name = "") => {
  const formData = new FormData();
  formData.append("file", file);
  if (name) {
    formData.append("name", name);
  }

  const response = await fetch(
    config.serverBaseUrl +
      "/rest/reports/validation-template-overrides/upload-image",
    {
      credentials: "include",
      method: "POST",
      headers: {
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: formData,
    },
  );

  const contentType = response.headers.get("content-type") || "";
  const body = contentType.includes("application/json")
    ? await response.json()
    : await response.text();

  if (!response.ok) {
    const errorMessage =
      typeof body === "string"
        ? body
        : body?.message || body?.error || "Failed to upload template image";
    throw new Error(errorMessage);
  }

  return body;
};

export const downloadValidationTemplateFile = async (id) => {
  const response = await fetch(
    config.serverBaseUrl +
      `/rest/reports/validation-template-overrides/${encodeURIComponent(id)}/download-template`,
    {
      credentials: "include",
      method: "GET",
      headers: {
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
    },
  );

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || "Failed to download Jasper template");
  }

  const blob = await response.blob();
  const contentDisposition = response.headers.get("Content-Disposition") || "";
  const filenameMatch =
    contentDisposition.match(/filename\*=UTF-8''([^;]+)/i) ||
    contentDisposition.match(/filename="?([^"]+)"?/i);
  const filename = filenameMatch?.[1]
    ? decodeURIComponent(filenameMatch[1])
    : "validation-template.jrxml";

  const objectUrl = window.URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = objectUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(objectUrl);
};
