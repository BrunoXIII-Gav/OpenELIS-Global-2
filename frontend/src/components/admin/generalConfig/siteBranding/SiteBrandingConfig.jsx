/**
 * Site Branding Configuration Component
 *
 * Allows administrators to configure site branding including logos and colors
 *
 * Task Reference: T021
 */

import React, { useState, useEffect, useContext, useRef } from "react";
import {
  Grid,
  Column,
  Section,
  Heading,
  Button,
  Loading,
  Modal,
  InlineLoading,
  Checkbox,
} from "@carbon/react";
import {
  getBranding,
  updateBranding,
  resetBranding,
  applyFavicon,
  applyFaviconHref,
  clearFavicons,
} from "../../../utils/BrandingUtils";
import { NotificationContext } from "../../../layout/Layout";
import {
  AlertDialog,
  NotificationKinds,
} from "../../../common/CustomNotification";
import { FormattedMessage, useIntl } from "react-intl";
import { useHistory } from "react-router-dom";
import PageBreadCrumb from "../../../common/PageBreadCrumb";
import LogoUploadSection from "./LogoUploadSection";
import ColorPickerSection from "./ColorPickerSection";

function SiteBrandingConfig() {
  const intl = useIntl();
  const history = useHistory();
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [branding, setBranding] = useState(null);
  const [savedBranding, setSavedBranding] = useState(null); // Track saved state
  const [showResetConfirm, setShowResetConfirm] = useState(false);
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState(false);
  const [hasPendingFiles, setHasPendingFiles] = useState(false);
  const initialBrandingRef = useRef(null);

  // Refs for LogoUploadSection components to trigger uploads
  const headerLogoRef = useRef(null);
  const loginLogoRef = useRef(null);
  const faviconRef = useRef(null);

  const breadcrumbs = [
    { label: "home.label", link: "/" },
    { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
    {
      label: "sidenav.label.admin.formEntry.siteInfoconfig",
      link: "/MasterListsPage#SiteInformationMenu",
    },
    { label: "site.branding.title", link: "#SiteBrandingMenu" },
  ];

  useEffect(() => {
    loadBranding();
  }, []);

  const loadBranding = () => {
    setIsLoading(true);
    getBranding((response) => {
      console.debug("loadBranding response:", response);
      console.debug("Logo URLs in response:", {
        headerLogoUrl: response?.headerLogoUrl,
        loginLogoUrl: response?.loginLogoUrl,
        faviconUrl: response?.faviconUrl,
      });
      if (response) {
        setBranding(response);
        setSavedBranding(JSON.parse(JSON.stringify(response))); // Deep copy for comparison
        initialBrandingRef.current = JSON.parse(JSON.stringify(response));
        // Apply colors immediately
        applyBrandingColors(response);
        applyFavicon(response);
      } else {
        // Handle error - use default values
        const defaultBranding = {
          headerColor: "#295785",
          primaryColor: "#0f62fe",
          secondaryColor: "#393939",
          colorMode: "light",
          showHeaderLogo: true,
          showLoginLogo: true,
          useHeaderLogoForLogin: false,
          showFavicon: true,
          showLoginNotice: true,
          showHeaderBannerText: true,
          showHeaderVersion: true,
          showHeaderSearchIcon: true,
          showHeaderNotificationIcon: true,
          showHeaderHelpIcon: true,
        };
        setBranding(defaultBranding);
        setSavedBranding(JSON.parse(JSON.stringify(defaultBranding)));
        initialBrandingRef.current = JSON.parse(
          JSON.stringify(defaultBranding),
        );
        // Apply default colors
        applyBrandingColors(defaultBranding);
        applyFavicon(defaultBranding);
      }
      setIsLoading(false);
      setHasUnsavedChanges(false);
    });
  };

  // Task Reference: T073 - Detect form state changes
  useEffect(() => {
    if (!branding) {
      // No branding loaded yet, no changes
      setHasUnsavedChanges(false);
      return;
    }

    if (!savedBranding) {
      // Branding loaded but no saved state yet - wait for it
      // However, if branding exists, there might be unsaved changes
      // Set to false initially, will be recalculated when savedBranding loads
      setHasUnsavedChanges(false);
      return;
    }

    // Compare only the fields that can be changed via the save button
    // Exclude logo URLs as they are managed separately via upload endpoints
    const compareFields = (obj) => {
      if (!obj) return {};
      return {
        id: obj.id || null,
        headerColor: (obj.headerColor || "").trim().toLowerCase(),
        primaryColor: (obj.primaryColor || "").trim().toLowerCase(),
        secondaryColor: (obj.secondaryColor || "").trim().toLowerCase(),
        colorMode: (obj.colorMode || "").trim().toLowerCase(),
        useHeaderLogoForLogin: Boolean(obj.useHeaderLogoForLogin),
        showHeaderLogo: obj.showHeaderLogo !== false,
        showLoginLogo: obj.showLoginLogo !== false,
        showFavicon: obj.showFavicon !== false,
        showLoginNotice: obj.showLoginNotice !== false,
        showHeaderBannerText: obj.showHeaderBannerText !== false,
        showHeaderVersion: obj.showHeaderVersion !== false,
        showHeaderSearchIcon: obj.showHeaderSearchIcon !== false,
        showHeaderNotificationIcon: obj.showHeaderNotificationIcon !== false,
        showHeaderHelpIcon: obj.showHeaderHelpIcon !== false,
      };
    };

    const currentFields = compareFields(branding);
    const savedFields = compareFields(savedBranding);
    const hasChanges =
      JSON.stringify(currentFields) !== JSON.stringify(savedFields);

    // Debug logging (remove in production)
    if (hasChanges) {
      console.debug("Unsaved changes detected:", {
        current: currentFields,
        saved: savedFields,
      });
    }

    setHasUnsavedChanges(hasChanges);
  }, [branding, savedBranding]);

  // Task Reference: T073 - Warn user when navigating away with unsaved changes
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      if (hasUnsavedChanges) {
        e.preventDefault();
        e.returnValue = intl.formatMessage({
          id: "site.branding.unsaved.changes",
        });
        return e.returnValue;
      }
    };

    window.addEventListener("beforeunload", handleBeforeUnload);

    return () => {
      window.removeEventListener("beforeunload", handleBeforeUnload);
    };
  }, [hasUnsavedChanges, intl]);

  // Task Reference: T046 - Update favicon in document head
  // Apply branding colors to the DOM immediately
  const resolveRgbFromCssColor = (color) => {
    if (!color) return null;

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

  const getEffectivePrimaryColor = (brandingData) => {
    const primary = normalizeColor(brandingData?.primaryColor);
    const header = normalizeColor(brandingData?.headerColor);
    const isDefaultPrimary = primary === "" || primary === "#0f62fe";
    const hasCustomHeader = header !== "" && header !== "#295785";
    return isDefaultPrimary && hasCustomHeader
      ? brandingData.headerColor
      : brandingData.primaryColor;
  };

  const applyThemeTokenOverrides = (primaryColor, secondaryColor) => {
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

  const applyBrandingColors = (brandingData) => {
    if (!brandingData) {
      console.warn("applyBrandingColors called with null/undefined data");
      return;
    }

    const root = document.documentElement;
    const effectivePrimaryColor = getEffectivePrimaryColor(brandingData);
    // Safety: never tie global background tokens to branding values.
    root.style.removeProperty("--cds-background");
    root.style.removeProperty("--cds-layer-01");

    console.debug("Applying branding colors to DOM:", {
      header: brandingData.headerColor,
      primary: brandingData.primaryColor,
      secondary: brandingData.secondaryColor,
    });

    if (brandingData.headerColor) {
      root.style.setProperty(
        "--site-branding-header",
        brandingData.headerColor,
      );

      const headerRgb = resolveRgbFromCssColor(brandingData.headerColor);
      const lightHeader = isLightColor(headerRgb);
      root.style.setProperty(
        "--site-branding-sidenav-text",
        lightHeader ? "#161616" : "#f4f4f4",
      );
      root.style.setProperty(
        "--site-branding-sidenav-overlay",
        lightHeader ? "black" : "white",
      );
      console.debug("Set --site-branding-header to:", brandingData.headerColor);
    }
    if (effectivePrimaryColor) {
      root.style.setProperty("--cds-button-primary", effectivePrimaryColor);
      root.style.setProperty(
        "--cds-button-primary-hover",
        effectivePrimaryColor,
      );
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
      console.debug(
        "Set --cds-interactive-01 and --site-branding-primary to:",
        effectivePrimaryColor,
      );
    }
    if (brandingData.secondaryColor) {
      root.style.setProperty(
        "--cds-button-secondary",
        brandingData.secondaryColor,
      );
      root.style.setProperty(
        "--cds-button-secondary-hover",
        brandingData.secondaryColor,
      );
      root.style.setProperty(
        "--cds-button-secondary-active",
        brandingData.secondaryColor,
      );
      root.style.setProperty(
        "--cds-interactive-02",
        brandingData.secondaryColor,
      );
      root.style.setProperty(
        "--site-branding-secondary",
        brandingData.secondaryColor,
      );
      console.debug(
        "Set --cds-interactive-02 and --site-branding-secondary to:",
        brandingData.secondaryColor,
      );
    }

    applyThemeTokenOverrides(
      effectivePrimaryColor,
      brandingData.secondaryColor,
    );

    // Verify the properties were set
    const computedPrimary = getComputedStyle(root)
      .getPropertyValue("--cds-button-primary")
      .trim();
    console.debug(
      "Verified CSS property --cds-button-primary is now:",
      computedPrimary,
    );
  };

  const resetFavicon = () => {
    applyFaviconHref("/images/favicon-16x16.png", "image/png");
  };

  // Handler for when a file is selected in LogoUploadSection
  const handleFileSelected = (file, type) => {
    setHasPendingFiles(true);
  };

  const handleSave = async () => {
    if (!branding || isSaving) return;

    // Task Reference: T097 - Disable form during save operation
    setIsSaving(true);

    // Prepare data for sending - ensure colors are always valid
    // Exclude logo URLs as they are managed separately via upload endpoints
    // Colors must be provided as database requires NOT NULL
    const dataToSend = {
      id: branding.id,
      headerColor: branding.headerColor?.trim() || "#295785",
      primaryColor: branding.primaryColor?.trim() || "#0f62fe",
      secondaryColor: branding.secondaryColor?.trim() || "#393939",
      colorMode: branding.colorMode?.trim() || "light",
      showHeaderLogo: branding.showHeaderLogo !== false,
      showLoginLogo: branding.showLoginLogo !== false,
      useHeaderLogoForLogin: branding.useHeaderLogoForLogin || false,
      showFavicon: branding.showFavicon !== false,
      showLoginNotice: branding.showLoginNotice !== false,
      showHeaderBannerText: branding.showHeaderBannerText !== false,
      showHeaderVersion: branding.showHeaderVersion !== false,
      showHeaderSearchIcon: branding.showHeaderSearchIcon !== false,
      showHeaderNotificationIcon: branding.showHeaderNotificationIcon !== false,
      showHeaderHelpIcon: branding.showHeaderHelpIcon !== false,
      // Do not include headerLogoUrl, loginLogoUrl, or faviconUrl
      // These are managed via separate logo upload endpoints
    };

    // Save branding configuration FIRST (including useHeaderLogoForLogin flag)
    // This must happen before logo uploads so the backend has correct state
    updateBranding(dataToSend, async (status, errorMessage, responseData) => {
      if (status !== 200 && status !== 201) {
        setIsSaving(false);
        console.error("Save failed:", { status, errorMessage, dataToSend });
        const errorText = errorMessage
          ? `${intl.formatMessage({ id: "site.branding.save.error" })}: ${errorMessage}`
          : intl.formatMessage({ id: "site.branding.save.error" });
        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: errorText,
          kind: NotificationKinds.error,
        });
        setNotificationVisible(true);
        return;
      }

      // Now upload any pending logo files AFTER branding config is saved
      // Upload sequentially to avoid race conditions with DB updates
      try {
        if (headerLogoRef.current?.hasPendingFile()) {
          console.debug("Uploading header logo...");
          await headerLogoRef.current.uploadFile();
          console.debug("Header logo uploaded successfully");
        }
        if (loginLogoRef.current?.hasPendingFile()) {
          console.debug("Uploading login logo...");
          await loginLogoRef.current.uploadFile();
          console.debug("Login logo uploaded successfully");
        }
        if (faviconRef.current?.hasPendingFile()) {
          console.debug("Uploading favicon...");
          await faviconRef.current.uploadFile();
          console.debug("Favicon uploaded successfully");
        }

        // Reset pending files state
        setHasPendingFiles(false);
      } catch (error) {
        console.error("Error uploading logos:", error);
        // Don't return early - continue to loadBranding to show any successful uploads
        // The individual upload errors are already shown in their respective components
      }

      // All saves complete - finalize
      setIsSaving(false);

      // Task Reference: T074 - Re-fetch branding config after save to ensure consistency
      // Apply colors immediately from the data we sent
      console.debug(
        "Save successful. Applying colors immediately from sent data:",
        dataToSend,
      );
      applyBrandingColors(dataToSend);

        if (branding.showFavicon === false) {
          clearFavicons();
        } else if (branding.faviconUrl) {
          applyFavicon({
            ...branding,
            faviconUrl: branding.faviconUrl,
          });
        } else {
          resetFavicon();
        }

        // Reload from server to get complete state including logo URLs
        loadBranding();

      // Dispatch event to notify Header and other components to reload branding
      window.dispatchEvent(new CustomEvent("branding-updated"));

      addNotification({
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({ id: "site.branding.save.success" }),
        kind: NotificationKinds.success,
      });
      setNotificationVisible(true);
    });
  };

  const handleCancel = () => {
    if (hasUnsavedChanges) {
      // Show confirmation if there are unsaved changes
      if (
        window.confirm(
          intl.formatMessage({ id: "site.branding.unsaved.changes" }),
        )
      ) {
        // Discard changes - reload from saved state
        if (savedBranding) {
          setBranding(JSON.parse(JSON.stringify(savedBranding)));
          setHasUnsavedChanges(false);
        } else {
          loadBranding();
        }
      }
    } else {
      // No changes, just reload
      loadBranding();
    }
  };

  const handleReset = () => {
    setShowResetConfirm(true);
  };

  const confirmReset = () => {
    setShowResetConfirm(false);
    setIsLoading(true);

    resetBranding((status) => {
      setIsLoading(false);
      if (status === 200 || status === 201) {
        // Reload branding after reset
        loadBranding();

        // Reset CSS custom properties to defaults
        document.documentElement.style.setProperty(
          "--site-branding-header",
          "#295785",
        );
        document.documentElement.style.setProperty(
          "--site-branding-sidenav-text",
          "#f4f4f4",
        );
        document.documentElement.style.setProperty(
          "--site-branding-sidenav-overlay",
          "white",
        );
        document.documentElement.style.removeProperty("--cds-background");
        document.documentElement.style.removeProperty("--cds-layer-01");
        document.documentElement.style.setProperty(
          "--cds-button-primary",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-button-primary-hover",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-button-primary-active",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-button-tertiary",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-link-primary",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-link-primary-hover",
          "#0f62fe",
        );
        document.documentElement.style.setProperty("--cds-focus", "#0f62fe");
        document.documentElement.style.setProperty(
          "--cds-border-interactive",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-interactive-01",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-interactive",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-background-brand",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-icon-interactive",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-text-interactive",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--cds-button-secondary",
          "#393939",
        );
        document.documentElement.style.setProperty(
          "--cds-button-secondary-hover",
          "#393939",
        );
        document.documentElement.style.setProperty(
          "--cds-button-secondary-active",
          "#393939",
        );
        document.documentElement.style.setProperty(
          "--cds-interactive-02",
          "#393939",
        );
        document.documentElement.style.setProperty(
          "--site-branding-primary",
          "#0f62fe",
        );
        document.documentElement.style.setProperty(
          "--site-branding-secondary",
          "#393939",
        );

        // Reset favicon
        resetFavicon();

        window.dispatchEvent(new CustomEvent("branding-updated"));

        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({ id: "site.branding.reset.success" }),
          kind: NotificationKinds.success,
        });
        setNotificationVisible(true);
      } else {
        addNotification({
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({ id: "site.branding.reset.error" }),
          kind: NotificationKinds.error,
        });
        setNotificationVisible(true);
      }
    });
  };

  const cancelReset = () => {
    setShowResetConfirm(false);
  };

  if (isLoading) {
    return (
      <div className="adminPageContent">
        <Loading
          description={intl.formatMessage({ id: "loading.description" })}
        />
      </div>
    );
  }

  return (
    <div className="adminPageContent">
      {notificationVisible === true ? <AlertDialog /> : ""}
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading>
              <FormattedMessage id="site.branding.title" />
            </Heading>
            <p>
              <FormattedMessage id="site.branding.description" />
            </p>
          </Section>
        </Column>
      </Grid>

      {/* Logo Upload Sections */}
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <LogoUploadSection
            ref={headerLogoRef}
            type="header"
            currentLogoUrl={branding?.headerLogoUrl}
            isVisible={branding?.showHeaderLogo !== false}
            onFileSelected={handleFileSelected}
            onLogoUploaded={(url) => {
              // Don't call loadBranding() here - handleSave calls it once after all uploads complete
              // Just dispatch event to notify Header to reload branding
              window.dispatchEvent(new CustomEvent("branding-updated"));
            }}
            onLogoRemoved={() => {
              // Logo removal is saved immediately, so reload from server to sync state
              loadBranding();
              // Dispatch event to notify Header to reload branding
              window.dispatchEvent(new CustomEvent("branding-updated"));
            }}
            onVisibilityChange={(showLogo) => {
              setBranding((prev) => ({
                ...prev,
                showHeaderLogo: showLogo,
              }));
            }}
          />
        </Column>
      </Grid>

      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <LogoUploadSection
            ref={loginLogoRef}
            type="login"
            currentLogoUrl={branding?.loginLogoUrl}
            isVisible={branding?.showLoginLogo !== false}
            useHeaderLogoForLogin={branding?.useHeaderLogoForLogin || false}
            onFileSelected={handleFileSelected}
            onLogoUploaded={(url) => {
              // Don't call loadBranding() here - handleSave calls it once after all uploads complete
              // Just dispatch event to notify Header to reload branding
              window.dispatchEvent(new CustomEvent("branding-updated"));
            }}
            onLogoRemoved={() => {
              // Logo removal is saved immediately, so reload from server to sync state
              loadBranding();
              // Dispatch event to notify Header to reload branding
              window.dispatchEvent(new CustomEvent("branding-updated"));
            }}
            onUseHeaderLogoChange={(useHeader) => {
              setBranding((prev) => ({
                ...prev,
                useHeaderLogoForLogin: useHeader,
              }));
            }}
            onVisibilityChange={(showLogo) => {
              setBranding((prev) => ({
                ...prev,
                showLoginLogo: showLogo,
              }));
            }}
          />
        </Column>
      </Grid>

      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <LogoUploadSection
            ref={faviconRef}
            type="favicon"
            currentLogoUrl={branding?.faviconUrl}
            isVisible={branding?.showFavicon !== false}
            onFileSelected={handleFileSelected}
            onLogoUploaded={(url) => {
              applyFavicon({
                ...branding,
                faviconUrl: url,
                showFavicon: branding?.showFavicon !== false,
              });
              // Don't call loadBranding() here - handleSave calls it once after all uploads complete
              // Just dispatch event to notify Header to reload branding
              window.dispatchEvent(new CustomEvent("branding-updated"));
            }}
            onLogoRemoved={() => {
              // Logo removal is saved immediately, so reload from server to sync state
              // Reset to default favicon
              if (branding?.showFavicon === false) {
                clearFavicons();
              } else {
                resetFavicon();
              }
              loadBranding();
              // Dispatch event to notify Header to reload branding
              window.dispatchEvent(new CustomEvent("branding-updated"));
            }}
            onVisibilityChange={(showLogo) => {
              setBranding((prev) => ({
                ...prev,
                showFavicon: showLogo,
              }));
            }}
          />
        </Column>
      </Grid>

      {/* Color Configuration Sections */}
      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <ColorPickerSection
            label={intl.formatMessage({ id: "site.branding.header.color" })}
            description={intl.formatMessage({
              id: "site.branding.header.color.description",
            })}
            value={branding?.headerColor || "#295785"}
            onChange={(color) => {
              setBranding((prev) => ({ ...prev, headerColor: color }));
              // Apply color immediately for preview
              document.documentElement.style.setProperty(
                "--site-branding-header",
                color,
              );
              const headerRgb = resolveRgbFromCssColor(color);
              const lightHeader = isLightColor(headerRgb);
              document.documentElement.style.setProperty(
                "--site-branding-sidenav-text",
                lightHeader ? "#161616" : "#f4f4f4",
              );
              document.documentElement.style.setProperty(
                "--site-branding-sidenav-overlay",
                lightHeader ? "black" : "white",
              );
              document.documentElement.style.removeProperty("--cds-background");
              document.documentElement.style.removeProperty("--cds-layer-01");
            }}
          />
        </Column>
      </Grid>

      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <ColorPickerSection
            label={intl.formatMessage({ id: "site.branding.primary.color" })}
            description={intl.formatMessage({
              id: "site.branding.primary.color.description",
            })}
            value={branding?.primaryColor || "#0f62fe"}
            onChange={(color) => {
              setBranding((prev) => ({ ...prev, primaryColor: color }));
              // Apply color immediately for preview
              document.documentElement.style.setProperty(
                "--cds-button-primary",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-button-primary-hover",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-button-primary-active",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-button-tertiary",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-link-primary",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-link-primary-hover",
                color,
              );
              document.documentElement.style.setProperty("--cds-focus", color);
              document.documentElement.style.setProperty(
                "--cds-border-interactive",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-interactive-01",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-interactive",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-background-brand",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-icon-interactive",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-text-interactive",
                color,
              );
              document.documentElement.style.setProperty(
                "--site-branding-primary",
                color,
              );
            }}
          />
        </Column>
      </Grid>

      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <ColorPickerSection
            label={intl.formatMessage({ id: "site.branding.secondary.color" })}
            description={intl.formatMessage({
              id: "site.branding.secondary.color.description",
            })}
            value={branding?.secondaryColor || "#393939"}
            onChange={(color) => {
              setBranding((prev) => ({ ...prev, secondaryColor: color }));
              // Apply color immediately for preview
              document.documentElement.style.setProperty(
                "--cds-button-secondary",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-button-secondary-hover",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-button-secondary-active",
                color,
              );
              document.documentElement.style.setProperty(
                "--cds-interactive-02",
                color,
              );
              document.documentElement.style.setProperty(
                "--site-branding-secondary",
                color,
              );
            }}
          />
        </Column>
      </Grid>

      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <h4>
              <FormattedMessage
                id="site.branding.visibility.section"
                defaultMessage="Visibility Options"
              />
            </h4>
            <p>
              <FormattedMessage
                id="site.branding.visibility.description"
                defaultMessage="Control what is shown in login and header UI."
              />
            </p>
            <div style={{ marginTop: "1rem" }}>
              <Checkbox
                id="branding-show-login-notice"
                labelText={intl.formatMessage({
                  id: "site.branding.visibility.login.notice",
                  defaultMessage: "Show login notice text",
                })}
                checked={branding?.showLoginNotice !== false}
                onChange={(event) =>
                  setBranding((prev) => ({
                    ...prev,
                    showLoginNotice: event.target.checked,
                  }))
                }
              />
            </div>
            <div style={{ marginTop: "0.75rem" }}>
              <Checkbox
                id="branding-show-header-banner-text"
                labelText={intl.formatMessage({
                  id: "site.branding.visibility.header.banner",
                  defaultMessage: "Show header facility name",
                })}
                checked={branding?.showHeaderBannerText !== false}
                onChange={(event) =>
                  setBranding((prev) => ({
                    ...prev,
                    showHeaderBannerText: event.target.checked,
                  }))
                }
              />
            </div>
            <div style={{ marginTop: "0.75rem" }}>
              <Checkbox
                id="branding-show-header-version"
                labelText={intl.formatMessage({
                  id: "site.branding.visibility.header.version",
                  defaultMessage: "Show header version number",
                })}
                checked={branding?.showHeaderVersion !== false}
                onChange={(event) =>
                  setBranding((prev) => ({
                    ...prev,
                    showHeaderVersion: event.target.checked,
                  }))
                }
              />
            </div>
            <div style={{ marginTop: "0.75rem" }}>
              <Checkbox
                id="branding-show-header-search"
                labelText={intl.formatMessage({
                  id: "site.branding.visibility.header.search",
                  defaultMessage: "Show header search icon",
                })}
                checked={branding?.showHeaderSearchIcon !== false}
                onChange={(event) =>
                  setBranding((prev) => ({
                    ...prev,
                    showHeaderSearchIcon: event.target.checked,
                  }))
                }
              />
            </div>
            <div style={{ marginTop: "0.75rem" }}>
              <Checkbox
                id="branding-show-header-notification"
                labelText={intl.formatMessage({
                  id: "site.branding.visibility.header.notification",
                  defaultMessage: "Show header notification icon",
                })}
                checked={branding?.showHeaderNotificationIcon !== false}
                onChange={(event) =>
                  setBranding((prev) => ({
                    ...prev,
                    showHeaderNotificationIcon: event.target.checked,
                  }))
                }
              />
            </div>
            <div style={{ marginTop: "0.75rem" }}>
              <Checkbox
                id="branding-show-header-help"
                labelText={intl.formatMessage({
                  id: "site.branding.visibility.header.help",
                  defaultMessage: "Show header help icon",
                })}
                checked={branding?.showHeaderHelpIcon !== false}
                onChange={(event) =>
                  setBranding((prev) => ({
                    ...prev,
                    showHeaderHelpIcon: event.target.checked,
                  }))
                }
              />
            </div>
          </Section>
        </Column>
      </Grid>

      <Grid fullWidth={true}>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Button
              onClick={handleSave}
              disabled={(!hasUnsavedChanges && !hasPendingFiles) || isSaving}
              style={{ marginRight: "1rem" }}
            >
              {isSaving ? (
                <InlineLoading
                  description={intl.formatMessage({
                    id: "loading.description",
                  })}
                />
              ) : (
                <FormattedMessage id="site.branding.save" />
              )}
            </Button>
            <Button
              data-testid="branding-cancel-button"
              onClick={handleCancel}
              kind="secondary"
              style={{ marginRight: "1rem" }}
            >
              <FormattedMessage id="site.branding.cancel" />
            </Button>
            <Button
              data-testid="branding-reset-button"
              kind="danger"
              onClick={handleReset}
            >
              <FormattedMessage id="site.branding.reset.to.defaults" />
            </Button>
            {(hasUnsavedChanges || hasPendingFiles) && (
              <p
                style={{
                  marginTop: "1rem",
                  fontStyle: "italic",
                  color: "#da1e28",
                }}
              >
                <FormattedMessage id="site.branding.unsaved.changes.warning" />
              </p>
            )}
          </Section>
        </Column>
      </Grid>

      {/* Reset Confirmation Modal */}
      <Modal
        open={showResetConfirm}
        modalHeading={intl.formatMessage({
          id: "site.branding.reset.to.defaults",
        })}
        primaryButtonText={intl.formatMessage({ id: "label.button.reset" })}
        secondaryButtonText={intl.formatMessage({ id: "label.button.cancel" })}
        onRequestClose={cancelReset}
        onRequestSubmit={confirmReset}
        danger
      >
        <p>{intl.formatMessage({ id: "site.branding.reset.confirmation" })}</p>
      </Modal>
    </div>
  );
}

export default SiteBrandingConfig;
