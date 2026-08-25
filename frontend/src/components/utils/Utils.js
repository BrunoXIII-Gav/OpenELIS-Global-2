import config from "../../config.json";
import { emitAccessDeniedEvent } from "../security/accessDenied";

const FRONTEND_HIDDEN_SAMPLE_TYPE_NAMES = new Set([
  "DBS",
  "Fluid",
  "Genital Specimen",
  "Histopathology specimen",
  "Immunohistochemistry specimen",
  "Plasma",
  "Respiratory Swab",
  "Serum",
  "Skin",
  "Sputum",
  "Tissue",
  "Tissue antemortem",
  "Tissue post mortem",
  "Urine",
  "Urines",
  "Vaginal Fluid",
  "Whole Blood",
]);

const filterFrontendHiddenSampleTypes = (endPoint, payload) => {
  if (
    !String(endPoint || "").startsWith("/rest/user-sample-types") ||
    !Array.isArray(payload)
  ) {
    return payload;
  }

  return payload.filter((item) => {
    const sampleTypeName = String(item?.value || "").trim();
    return !FRONTEND_HIDDEN_SAMPLE_TYPE_NAMES.has(sampleTypeName);
  });
};

const notifyIfAccessDenied = (response, options = {}) => {
  if (response?.status === 403 && !options?.suppressAccessDeniedDialog) {
    emitAccessDeniedEvent();
  }
};

export const redirectToPortalOnSamlExpiry = () => {
  const portalLogoutUrl =
    sessionStorage.getItem("openelis.portalLogoutUrl") || "";
  const lastLoginMethod =
    sessionStorage.getItem("openelis.lastLoginMethod") || "";
  if (lastLoginMethod !== "SAML" || !portalLogoutUrl) {
    return false;
  }

  try {
    const portalUrl = new URL(portalLogoutUrl, window.location.origin);
    portalUrl.searchParams.set("source", "openelis");
    portalUrl.searchParams.set("session_expired", "1");
    sessionStorage.removeItem("openelis.lastLoginMethod");
    window.location.replace(portalUrl.toString());
    return true;
  } catch (error) {
    console.error(error);
    return false;
  }
};

export const getFromOpenElisServer = (endPoint, callback, signal = null) => {
  fetch(
    config.serverBaseUrl + endPoint,

    {
      //includes the browser sessionId in the Header for Authentication on the backend server
      credentials: "include",
      method: "GET",
      signal: signal,
    },
  )
    .then((response) => {
      console.debug("checking response");
      // if (response.url.includes("LoginPage")) {
      //     throw "No Login Session";
      // }
      if (response.status === 401 && redirectToPortalOnSamlExpiry()) {
        return;
      }
      if (!response.ok) {
        notifyIfAccessDenied(response);
        callback(undefined);
        return;
      }
      const contentType = response.headers.get("content-type");
      if (contentType && contentType.indexOf("application/json") !== -1) {
        return response.json().then((jsonResp) => {
          callback(filterFrontendHiddenSampleTypes(endPoint, jsonResp));
        });
      } else {
        callback();
      }
    })
    .catch((error) => {
      // Don't log AbortError - it's expected when component unmounts
      if (error.name !== "AbortError") {
        console.error(error);
      }
      // Ensure callback is always called, even on error, to avoid hanging promises
      callback(undefined);
    });
};

export const postToOpenElisServer = (
  endPoint,
  payLoad,
  callback,
  extraParams,
  options = {},
) => {
  fetch(
    config.serverBaseUrl + endPoint,

    {
      //includes the browser sessionId in the Header for Authentication on the backend server
      credentials: "include",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: payLoad,
    },
  )
    .then((response) => {
      notifyIfAccessDenied(response, options);
      return response.status;
    })
    .then((status) => {
      callback(status, extraParams);
    })
    .catch((error) => {
      console.error(error);
      callback(0, extraParams);
    });
};

export const postToOpenElisServerFullResponse = (
  endPoint,
  payLoad,
  callback,
  extraParams,
) => {
  fetch(
    config.serverBaseUrl + endPoint,

    {
      //includes the browser sessionId in the Header for Authentication on the backend server
      credentials: "include",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: payLoad,
    },
  )
    .then((response) => {
      notifyIfAccessDenied(response);
      callback(response, extraParams);
    })
    .catch((error) => {
      console.error(error);
      callback(
        {
          status: 0,
          headers: new Headers(),
          text: async () => "",
        },
        extraParams,
      );
    });
};

export const postToOpenElisServerFormData = (
  endPoint,
  formData,
  callback,
  extraParams,
) => {
  fetch(
    config.serverBaseUrl + endPoint,

    {
      credentials: "include",
      method: "POST",
      headers: {
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: formData,
    },
  )
    .then((response) => {
      notifyIfAccessDenied(response);
      return response.status;
    })
    .then((status) => {
      callback(status, extraParams);
    })
    .catch((error) => {
      console.error(error);
    });
};

export const postToOpenElisServerFormDataJsonResponse = (
  endPoint,
  formData,
  callback,
  extraParams,
) => {
  fetch(
    config.serverBaseUrl + endPoint,

    {
      credentials: "include",
      method: "POST",
      headers: {
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: formData,
    },
  )
    .then(async (response) => {
      notifyIfAccessDenied(response);
      const contentType = response.headers.get("content-type") || "";
      let payload = null;

      if (contentType.includes("application/json")) {
        try {
          payload = await response.json();
        } catch (error) {
          payload = null;
        }
      }

      callback(
        {
          ok: response.ok,
          status: response.status,
          body: payload,
        },
        extraParams,
      );
    })
    .catch((error) => {
      console.error(error);
      callback(
        {
          ok: false,
          status: 0,
          body: null,
        },
        extraParams,
      );
    });
};

export const postToOpenElisServerJsonResponse = (
  endPoint,
  payLoad,
  callback,
  extraParams,
) => {
  fetch(
    config.serverBaseUrl + endPoint,

    {
      //includes the browser sessionId in the Header for Authentication on the backend server
      credentials: "include",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: payLoad,
    },
  )
    .then((response) => {
      notifyIfAccessDenied(response);
      // Check if response is ok (status 200-299)
      if (!response.ok) {
        // For error responses, try to parse JSON error message
        return response.json().then((errorJson) => {
          // Include status code in error response for better error handling
          return {
            ...errorJson,
            status: response.status,
            statusCode: response.status,
            statusText: response.statusText,
          };
        });
      }
      // For successful responses, parse JSON normally
      return response.json();
    })
    .then((json) => {
      callback(json, extraParams);
    })
    .catch((error) => {
      console.error("postToOpenElisServerJsonResponse error:", error);
      // Pass error to callback so calling code can handle it
      callback(
        {
          error: error.message || "Network error",
          message: error.message || "Network error",
          status: 0,
        },
        extraParams,
      );
    });
};

//provides Synchronous calls to the api
export const getFromOpenElisServerSync = (endPoint, callback) => {
  const request = new XMLHttpRequest();
  request.open("GET", config.serverBaseUrl + endPoint, false);
  request.setRequestHeader("credentials", "include");
  request.send();
  // if (request.response.url.includes("LoginPage")) {
  //     throw "No Login Session";
  // }
  return callback(JSON.parse(request.response));
};

export const postToOpenElisServerForBlob = (
  endPoint,
  payLoad,
  callback,
  errorCallback,
) => {
  fetch(
    config.serverBaseUrl + endPoint,

    {
      //includes the browser sessionId in the Header for Authentication on the backend server
      credentials: "include",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: payLoad,
    },
  )
    .then((response) => {
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return response.blob().then((blob) => ({ blob, response }));
    })
    .then(({ blob, response }) => {
      callback(blob, response);
    })
    .catch((error) => {
      console.error(error);
      if (errorCallback) {
        errorCallback(error);
      }
    });
};

export const postToOpenElisServerForPDF = (endPoint, payLoad, callback) => {
  fetch(
    config.serverBaseUrl + endPoint,

    {
      //includes the browser sessionId in the Header for Authentication on the backend server
      credentials: "include",
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: payLoad,
    },
  )
    .then((response) => response.blob())
    .then((blob) => {
      callback(true, blob);
      let link = document.createElement("a");
      link.href = window.URL.createObjectURL(blob, { type: "application/pdf" });
      link.target = "_blank";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
    })
    .catch((error) => {
      callback(false);
      console.error(error);
    });
};

export const putToOpenElisServer = (endPoint, payLoad, callback) => {
  // Build the request options
  let options = {
    // includes the browser sessionId in the Header for Authentication on the backend server
    credentials: "include",
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Token": localStorage.getItem("CSRF"),
    },
  };

  // Include the body only if payLoad is provided
  if (payLoad) {
    options.body = payLoad;
  }

  fetch(config.serverBaseUrl + endPoint, options)
    .then((response) => {
      notifyIfAccessDenied(response);
      return response.status;
    })
    .then((status) => {
      callback(status);
    })
    .catch((error) => {
      console.error(error);
    });
};

export const putToOpenElisServerFullResponse = (
  endPoint,
  payLoad,
  callback,
  extraParams,
) => {
  fetch(config.serverBaseUrl + endPoint, {
    //includes the browser sessionId in the Header for Authentication on the backend server
    credentials: "include",
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Token": localStorage.getItem("CSRF"),
    },
    body: payLoad,
  })
    .then((response) => {
      notifyIfAccessDenied(response);
      callback(response, extraParams);
    })
    .catch((error) => {
      console.error(error);
    });
};

export const deleteFromOpenElisServer = (endPoint, callback) => {
  fetch(config.serverBaseUrl + endPoint, {
    // includes the browser sessionId in the Header for Authentication on the backend server
    credentials: "include",
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Token": localStorage.getItem("CSRF"),
    },
  })
    .then((response) => {
      notifyIfAccessDenied(response);
      return response.status;
    })
    .then((status) => {
      callback(status);
    })
    .catch((error) => {
      console.error(error);
    });
};

export const deleteFromOpenElisServerFullResponse = (
  endPoint,
  callback,
  extraParams,
) => {
  fetch(config.serverBaseUrl + endPoint, {
    // includes the browser sessionId in the Header for Authentication on the backend server
    credentials: "include",
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
      "X-CSRF-Token": localStorage.getItem("CSRF"),
    },
  })
    .then((response) => {
      notifyIfAccessDenied(response);
      callback(response, extraParams);
    })
    .catch((error) => {
      console.error(error);
    });
};

export const hasRole = (userSessionDetails, role) => {
  if (!userSessionDetails || !userSessionDetails.roles) {
    return false;
  }
  const roles = Array.isArray(userSessionDetails.roles)
    ? userSessionDetails.roles
    : Object.values(userSessionDetails.roles);
  return roles.includes(role);
};

// this is complicated to enable it to format "smartly" as a person types
// possible rework could allow it to only format completed numbers

export const getFromOpenElisServerV2 = (url) => {
  return new Promise((resolve, reject) => {
    // Simulating the original callback-based function
    getFromOpenElisServer(url, (res) => {
      if (res) {
        resolve(res);
      } else {
        reject("Failed to fetch Subscription data");
      }
    });
  });
};

export const patchToOpenElisServerJsonResponse = (
  endPoint,
  payLoad,
  callback,
  extraParams,
) => {
  fetch(
    config.serverBaseUrl + endPoint,

    {
      //includes the browser sessionId in the Header for Authentication on the backend server
      credentials: "include",
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
        "X-CSRF-Token": localStorage.getItem("CSRF"),
      },
      body: payLoad,
    },
  )
    .then((response) => {
      notifyIfAccessDenied(response);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      return response.json();
    })
    .then((json) => {
      callback(json, extraParams);
    })
    .catch((error) => {
      console.error(error);
    });
};

export const convertAlphaNumLabNumForDisplay = (labNumber) => {
  if (!labNumber) {
    return labNumber;
  }
  if (labNumber.length > 15) {
    console.warn("labNumber is not alphanumeric (too long), ignoring format");
    return labNumber;
  }
  //if dash made it into value, then it's part of the analysis number, not the base lab number
  let labNumberParts = labNumber.split("-");
  let isAnalysisLabNumber = labNumberParts.length > 1;
  let labNumberForDisplay = labNumberParts[0];
  //incomplete lab number
  if (labNumberParts[0].length < 8) {
    labNumberForDisplay = labNumberParts[0].slice(0, 2);
    if (labNumberParts[0].length > 2) {
      labNumberForDisplay = labNumberForDisplay + "-";
      labNumberForDisplay = labNumberForDisplay + labNumberParts[0].slice(2);
    }
  } else {
    //possibly complete lab number
    labNumberForDisplay = labNumberParts[0].slice(0, 2) + "-";
    if (labNumberParts[0].length > 8) {
      // lab number contains prefix
      labNumberForDisplay =
        labNumberForDisplay +
        labNumberParts[0].slice(2, labNumberParts[0].length - 6) +
        "-";
    }
    labNumberForDisplay =
      labNumberForDisplay +
      labNumberParts[0].slice(
        labNumberParts[0].length - 6,
        labNumberParts[0].length - 3,
      ) +
      "-";

    labNumberForDisplay =
      labNumberForDisplay +
      labNumberParts[0].slice(labNumberParts[0].length - 3);
  }
  //re-add dash
  if (isAnalysisLabNumber) {
    labNumberForDisplay = labNumberForDisplay + "-" + labNumberParts[1];
  }
  return labNumberForDisplay.toUpperCase();
};

export function encodeDate(dateString) {
  if (typeof dateString === "string" && dateString.trim() !== "") {
    return dateString.split("/").map(encodeURIComponent).join("%2F");
  } else {
    return "";
  }
}

export function getDifferenceInDays(date1, date2) {
  console.log("secondDate", date2);

  // Function to parse dates in DD/MM/YYYY format
  function parseDate(dateStr) {
    const [day, month, year] = dateStr.split("/").map(Number);
    return new Date(year, month - 1, day); // Months are 0-based in JavaScript Date
  }

  function correctDate(firstDate) {
    // "08/05/2024" the error is 08 is not day it is month and 05 is day
    let dateParts = firstDate.split("/");
    if (dateParts[0].length === 4) {
      return dateParts[1] + "/" + dateParts[0] + "/" + dateParts[2];
    }
    return firstDate;
  }

  // Convert the date strings to Date objects
  const firstDate = parseDate(correctDate(date1));
  const secondDate = parseDate(correctDate(date2));

  // Calculate the difference in time (milliseconds)
  const timeDifference = secondDate - firstDate;

  // Convert the time difference from milliseconds to days
  const millisecondsPerDay = 1000 * 60 * 60 * 24;
  const dayDifference = timeDifference / millisecondsPerDay;

  // Return the rounded difference in days
  return dayDifference;
}

export function formatTimestamp(timestamp) {
  // Convert the timestamp to milliseconds and create a Date object
  const date = new Date(timestamp * 1000);

  // Extract and format components
  const hours = date.getUTCHours();
  const minutes = date.getUTCMinutes();
  const day = date.getUTCDate();
  const month = date.getUTCMonth() + 1; // Months are zero-based
  const year = date.getUTCFullYear();

  // Determine AM or PM and format hours
  const ampm = hours >= 12 ? "PM" : "AM";
  const formattedHours = (hours % 12 || 12).toString().padStart(2, "0");
  const formattedMinutes = minutes.toString().padStart(2, "0");

  // Format day and month
  const formattedDay = day.toString().padStart(2, "0");
  const formattedMonth = month.toString().padStart(2, "0");

  // Combine and return the formatted string
  return `${formattedHours}:${formattedMinutes} ${ampm}; ${formattedDay}/${formattedMonth}/${year}`;
}

// Helper function to convert a URL-safe base64 string to a Uint8Array
export function urlBase64ToUint8Array(base64String) {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

export const Roles = {
  ADMINISTRATION: "Administration",
  GLOBAL_ADMIN: "Global Administrator",
  USER_ACCOUNT_ADMIN: "User Account Administrator",
  AUDIT_TRAIL: "Audit Trail",
  ANALYSER_IMPORT: "Analyser Import",
  CYTOPATHOLOGIST: "Cytopathologist",
  PATHOLOGIST: "Pathologist",
  RECEPTION: "Reception",
  GENERIC_SAMPLE: "Generic Sample",
  SAMPLE_MANAGEMENT: "Sample Management",
  ORDER: "Order",
  ORDER_ADD: "Order Add",
  ORDER_EDIT: "Order Edit",
  PATIENT: "Patient",
  PATIENT_MANAGEMENT: "Patient Management",
  PATIENT_HISTORY: "Patient History",
  RESULTS: "Results",
  RESULTS_BY_UNIT: "Results By Unit",
  RESULTS_BY_PATIENT: "Results By Patient",
  RESULTS_BY_ORDER: "Results By Order",
  VALIDATION: "Validation",
  VALIDATION_ROUTINE: "Validation Routine",
  VALIDATION_BY_ORDER: "Validation By Order",
  REPORTS: "Reports",
  ALIQUOT: "Aliquot",
  STORAGE: "Storage",
  STORAGE_MANAGEMENT: "Storage Management",
};

export const toBase64 = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
  });
