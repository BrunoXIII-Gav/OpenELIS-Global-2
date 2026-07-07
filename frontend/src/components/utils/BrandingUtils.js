/**
 * Site branding utilities and API functions.
 */

import {
  getFromOpenElisServer,
  postToOpenElisServer,
  putToOpenElisServerFullResponse,
  deleteFromOpenElisServerFullResponse,
} from "./Utils";
import config from "../../config.json";

// =============================================================================
// API Functions
// =============================================================================

/**
 * Get current branding configuration
 * @param {Function} callback - Callback function to handle response
 */
export const getBranding = (callback) => {
  getFromOpenElisServer("/rest/site-branding", callback);
};

/**
 * Update branding configuration
 * @param {Object} formData - Branding configuration data
 * @param {Function} callback - Callback function to handle response (receives status, errorMessage, responseData)
 * @param {Object} extraParams - Additional parameters to pass to callback
 */
export const updateBranding = (formData, callback, extraParams) => {
  const payload = JSON.stringify(formData);
  putToOpenElisServerFullResponse(
    "/rest/site-branding",
    payload,
    async (response, extraParams) => {
      const status = response.status;
      let errorMessage = null;
      let responseData = null;

      if (status === 200 || status === 201) {
        try {
          responseData = await response.json();
        } catch (e) {
          // Response might be empty, that's okay
        }
      } else {
        try {
          const errorData = await response.json();
          console.error("Backend error response:", errorData);
          // Handle validation errors (from @Valid)
          if (errorData.errors && typeof errorData.errors === "object") {
            const validationErrors = Object.entries(errorData.errors)
              .map(([field, message]) => `${field}: ${message}`)
              .join("; ");
            errorMessage = validationErrors || "Validation error";
          } else {
            // Handle other error formats
            errorMessage =
              errorData.error ||
              errorData.message ||
              errorData.globalErrors?.join("; ") ||
              "Unknown error";
          }
        } catch (e) {
          console.error("Error parsing error response:", e);
          errorMessage = `Error ${status}: ${response.statusText}`;
        }
      }

      callback(status, errorMessage, responseData, extraParams);
    },
    extraParams,
  );
};

/**
 * Remove logo file
 * @param {String} type - Logo type (header, login, favicon)
 * @param {Function} callback - Callback function to handle response
 * @param {Object} extraParams - Additional parameters to pass to callback
 */
export const removeLogo = (type, callback, extraParams) => {
  deleteFromOpenElisServerFullResponse(
    `/rest/site-branding/logo/${type}`,
    callback,
    extraParams,
  );
};

const DEFAULT_HEADER_LOGO = "../images/openelis_logo.png";
const DEFAULT_LOGIN_LOGO = "images/openelis_logo_full.png";
const DEFAULT_FAVICON = "/images/favicon-16x16.png";

/**
 * Reset all branding to default values
 * @param {Function} callback - Callback function to handle response
 * @param {Object} extraParams - Additional parameters to pass to callback
 */
export const resetBranding = (callback, extraParams) => {
  const payload = JSON.stringify({});
  postToOpenElisServer(
    "/rest/site-branding/reset",
    payload,
    callback,
    extraParams,
  );
};

// =============================================================================
// DOM Utility Functions
// =============================================================================

const resolveRgbFromCssColor = (color) => {
  if (!color || typeof document === "undefined") return null;

  const probe = document.createElement("span");
  probe.style.color = color;
  probe.style.display = "none";
  document.body.appendChild(probe);
  const resolved = window.getComputedStyle(probe).color;
  document.body.removeChild(probe);

  const match = resolved.match(
    /rgba?\(\s*(\d{1,3})[\s,]+(\d{1,3})[\s,]+(\d{1,3})/i,
  );
  if (!match) return null;

  return {
    r: Number(match[1]),
    g: Number(match[2]),
    b: Number(match[3]),
  };
};

const isLightColor = (rgb) => {
  if (!rgb) return false;
  const { r, g, b } = rgb;
  const luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
  return luminance > 0.6;
};

const normalizeColor = (value) => (value || "").trim().toLowerCase();

const getEffectivePrimaryColor = (branding) => {
  const primary = normalizeColor(branding?.primaryColor);
  const header = normalizeColor(branding?.headerColor);
  const isDefaultPrimary = primary === "" || primary === "#0f62fe";
  const hasCustomHeader = header !== "" && header !== "#295785";
  return isDefaultPrimary && hasCustomHeader
    ? branding.headerColor
    : branding.primaryColor;
};

const applyThemeTokenOverrides = (primaryColor, secondaryColor) => {
  if (typeof document === "undefined") return;

  const themeContainers = document.querySelectorAll(
    "[data-carbon-theme], .cds--white",
  );

  themeContainers.forEach((el) => {
    if (primaryColor) {
      el.style.setProperty("--cds-button-primary", primaryColor);
      el.style.setProperty("--cds-button-primary-hover", primaryColor);
      el.style.setProperty("--cds-button-primary-active", primaryColor);
      el.style.setProperty("--cds-button-tertiary", primaryColor);
      el.style.setProperty("--cds-link-primary", primaryColor);
      el.style.setProperty("--cds-link-primary-hover", primaryColor);
      el.style.setProperty("--cds-focus", primaryColor);
      el.style.setProperty("--cds-border-interactive", primaryColor);
      el.style.setProperty("--cds-interactive-01", primaryColor);
      el.style.setProperty("--cds-interactive", primaryColor);
      el.style.setProperty("--cds-background-brand", primaryColor);
      el.style.setProperty("--cds-icon-interactive", primaryColor);
      el.style.setProperty("--cds-text-interactive", primaryColor);
    }

    if (secondaryColor) {
      el.style.setProperty("--cds-button-secondary", secondaryColor);
      el.style.setProperty("--cds-button-secondary-hover", secondaryColor);
      el.style.setProperty("--cds-button-secondary-active", secondaryColor);
      el.style.setProperty("--cds-interactive-02", secondaryColor);
    }
  });
};

/**
 * Apply branding colors to the document root element.
 * Sets CSS custom properties that Carbon components will use.
 * @param {Object} branding - Branding configuration object
 */
export const applyBrandingColors = (branding) => {
  if (!branding) return;

  const root = document.documentElement;
  const effectivePrimaryColor = getEffectivePrimaryColor(branding);
  // Safety: ensure global background tokens are not overridden by branding.
  // They control broad layout surfaces and can unintentionally paint the full app.
  root.style.removeProperty("--cds-background");
  root.style.removeProperty("--cds-layer-01");

  if (branding.headerColor) {
    root.style.setProperty("--site-branding-header", branding.headerColor);

    const headerRgb = resolveRgbFromCssColor(branding.headerColor);
    const lightHeader = isLightColor(headerRgb);
    root.style.setProperty(
      "--site-branding-header-text",
      lightHeader ? "#161616" : "#f4f4f4",
    );
    root.style.setProperty(
      "--site-branding-header-overlay",
      lightHeader ? "black" : "white",
    );
    root.style.setProperty(
      "--site-branding-sidenav-text",
      lightHeader ? "#161616" : "#f4f4f4",
    );
    root.style.setProperty(
      "--site-branding-sidenav-overlay",
      lightHeader ? "black" : "white",
    );
  }
  if (effectivePrimaryColor) {
    root.style.setProperty("--cds-button-primary", effectivePrimaryColor);
    root.style.setProperty("--cds-button-primary-hover", effectivePrimaryColor);
    root.style.setProperty(
      "--cds-button-primary-active",
      effectivePrimaryColor,
    );
    root.style.setProperty("--cds-button-tertiary", effectivePrimaryColor);
    root.style.setProperty("--cds-link-primary", effectivePrimaryColor);
    root.style.setProperty("--cds-link-primary-hover", effectivePrimaryColor);
    root.style.setProperty("--cds-focus", effectivePrimaryColor);
    root.style.setProperty("--cds-border-interactive", effectivePrimaryColor);
    root.style.setProperty("--cds-interactive-01", effectivePrimaryColor);
    root.style.setProperty("--cds-interactive", effectivePrimaryColor);
    root.style.setProperty("--cds-background-brand", effectivePrimaryColor);
    root.style.setProperty("--cds-icon-interactive", effectivePrimaryColor);
    root.style.setProperty("--cds-text-interactive", effectivePrimaryColor);
    root.style.setProperty("--site-branding-primary", effectivePrimaryColor);
  }
  if (branding.secondaryColor) {
    root.style.setProperty("--cds-button-secondary", branding.secondaryColor);
    root.style.setProperty(
      "--cds-button-secondary-hover",
      branding.secondaryColor,
    );
    root.style.setProperty(
      "--cds-button-secondary-active",
      branding.secondaryColor,
    );
    root.style.setProperty("--cds-interactive-02", branding.secondaryColor);
    root.style.setProperty(
      "--site-branding-secondary",
      branding.secondaryColor,
    );
  }

  applyThemeTokenOverrides(effectivePrimaryColor, branding.secondaryColor);
};

/**
 * Update the document favicon.
 * @param {String} faviconUrl - URL path to the favicon
 */
export const clearFavicons = () => {
  if (typeof document === "undefined") return;

  const existingLinks = document.querySelectorAll(
    'link[rel*="icon"], link[rel="apple-touch-icon"]',
  );
  existingLinks.forEach((link) => link.remove());
};

export const applyFaviconHref = (href, type = "image/x-icon", rel = "icon") => {
  if (!href || typeof document === "undefined") return;

  clearFavicons();

  const link = document.createElement("link");
  link.rel = rel;
  link.type = type;
  link.href = href;
  document.head.appendChild(link);
};

export const applyFavicon = (branding) => {
  if (!branding) return;

  if (branding.showFavicon === false) {
    clearFavicons();
    return;
  }

  if (branding.faviconUrl) {
    applyFaviconHref(`${config.serverBaseUrl}${branding.faviconUrl}`);
    return;
  }

  applyFaviconHref(DEFAULT_FAVICON, "image/png");
};

/**
 * Fetch branding configuration and apply it to the DOM.
 * Applies colors and favicon.
 * @param {Function} callback - Optional callback after branding is applied
 */
export const loadAndApplyBranding = (callback) => {
  getBranding((response) => {
    if (response) {
      applyBrandingColors(response);
      applyFavicon(response);
    }
    if (callback) {
      callback(response);
    }
  });
};

export const getHeaderLogoSrc = (branding, logoVersion = 0) => {
  if (branding?.showHeaderLogo === false) {
    return null;
  }

  if (branding?.headerLogoUrl) {
    return `${config.serverBaseUrl}${branding.headerLogoUrl}?v=${logoVersion}`;
  }

  return DEFAULT_HEADER_LOGO;
};

export const getLoginLogoSrc = (branding, logoVersion = 0) => {
  if (branding?.showLoginLogo === false) {
    return null;
  }

  const logoUrl =
    branding?.useHeaderLogoForLogin && branding?.headerLogoUrl
      ? branding.headerLogoUrl
      : branding?.loginLogoUrl;

  if (logoUrl) {
    return `${config.serverBaseUrl}${logoUrl}?v=${logoVersion}`;
  }

  return DEFAULT_LOGIN_LOGO;
};
