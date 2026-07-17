import React, { useState, useEffect } from "react";
import { BrowserRouter as Router, Route, Switch } from "react-router-dom";
import { IntlProvider } from "react-intl";
import { confirmAlert } from "react-confirm-alert";
import Layout from "./components/layout/Layout";
import Home from "./components/Home";
import StorageDashboard from "./components/storage/StorageDashboard";
import Login from "./components/Login";
import LandingPage from "./components/home/LandingPage";
import AnalyzersPage from "./pages/AnalyzersPage";
import FieldMapping from "./components/analyzers/FieldMapping/FieldMapping";
import ErrorDashboardPage from "./pages/ErrorDashboardPage";
import CustomFieldTypeManagementPage from "./pages/CustomFieldTypeManagementPage";
import AnalyzerTypesPage from "./pages/AnalyzerTypesPage";
import ValidationTemplateOverrideConfigPage from "./pages/ValidationTemplateOverrideConfigPage";
import ConsentTemplateConfigPage from "./pages/ConsentTemplateConfigPage";
import QCDashboardPlaceholder from "./pages/analyzers/QCDashboardPlaceholder";
import QCAlertsPlaceholder from "./pages/analyzers/QCAlertsPlaceholder";
import CorrectiveActionsPlaceholder from "./pages/analyzers/CorrectiveActionsPlaceholder";
import { Admin } from "./components";
import ResultSearch from "./components/resultPage/ResultSearch";
import UserSessionDetailsContext from "./UserSessionDetailsContext";
import { getFromOpenElisServer } from "./components/utils/Utils";
import { loadAndApplyBranding } from "./components/utils/BrandingUtils";
import "./App.css";
import { languages } from "./languages";
import config from "./config.json";
import { SecureRoute } from "./components/security";
import "./index.scss";
import RedirectOldUI from "./RedirectOldUI";
import PatientManagement from "./components/patient/PatientManagement";
import PatientHistory from "./components/patient/PatientHistory";
import PatientMerge from "./components/patient/PatientMerge";
import Aliquot from "./components/sample/Aliquot";
import Workplan from "./components/workplan/Workplan";
import AddOrder from "./components/addOrder/Index";
import FindOrder from "./components/modifyOrder/Index";
import ModifyOrder from "./components/modifyOrder/ModifyOrder";
import RoutineReports from "./components/reports/Routine";
import StudyReports from "./components/reports/Study";
import StudyValidation from "./components/validation/Index";
import AnalyserResultIndex from "./components/analyserResults/Index";
import PathologyDashboard from "./components/pathology/PathologyDashboard";
import CytologyDashboard from "./components/cytology/CytologyDashBoard";
import NoteBookDashBoard from "./components/notebook/NoteBookDashBoard";
import NoteBookEntryForm from "./components/notebook/NoteBookEntryForm";
import CytologyCaseView from "./components/cytology/CytologyCaseView";
import PathologyCaseView from "./components/pathology/PathologyCaseView";
import ImmunohistochemistryDashboard from "./components/immunohistochemistry/ImmunohistochemistryDashboard";
import ImmunohistochemistryCaseView from "./components/immunohistochemistry/ImmunohistochemistryCaseView";
import RoutedResultsViewer from "./components/patient/resultsViewer/results-viewer.tsx";
import EOrderPage from "./components/eOrder/Index";
import RoutineIndex from "./components/reports/routine/Index.js";
import StudyIndex from "./components/reports/study/index.js";
import ReportIndex from "./components/reports/Index.js";
import PrintBarcode from "./components/printBarcode/Index";
import NonConformIndex from "./components/nonconform/index";
import SampleBatchEntrySetup from "./components/batchOrderEntry/SampleBatchEntrySetup.js";
import AuditTrailReportIndex from "./components/reports/auditTrailReport/Index.js";
import ReferredOutTests from "./components/resultPage/resultsReferredOut/ReferredOutTests.js";
import ChangePassword from "./components/ChangePassword.js";
import { Roles } from "./components/utils/Utils";
import NoteBookInstanceEntryForm from "./components/notebook/NoteBookInstanceEntryForm.js";
import NotebookSampleOrder from "./components/notebook/NotebookSampleOrder.js";
import FreezerMonitoringDashboard from "./components/coldStorage/FreezerMonitoringDashboard";
import ProgramDashboard from "./components/program/programDashboard.jsx";
import ProgramCaseView from "./components/program/programCaseView.jsx";
import SampleManagement from "./components/sampleManagement/SampleManagement";
import InventoryManagement from "./components/inventory/InventoryManagement";

export default function App() {
  const PORTAL_LOGOUT_FLAG = "openelis.loggedOutToPortal";
  const PORTAL_SAML_LAUNCH_FLAG = "openelis.portalSamlLaunch";
  const BACK_GUARD_FLAG = "openelis.samlBackGuardArmed";
  const BACK_GUARD_SOURCE = "openelis.samlBackGuard";

  const defaultLocale =
    localStorage.getItem("locale") || navigator.language.split(/[-_]/)[0];

  const initialLocale = languages[defaultLocale] ? defaultLocale : "en";

  const [locale, setLocale] = useState(initialLocale);
  const [messages, setMessages] = useState(languages[initialLocale].messages);

  const [userSessionDetails, setUserSessionDetails] = useState({});
  const [errorLoadingSessionDetails, setErrorLoadingSessionDetails] =
    useState(false);

  useEffect(() => {
    getUserSessionDetails();
  }, []);

  useEffect(() => {
    const handlePageShow = (event) => {
      if (event.persisted) {
        getUserSessionDetails();
      }
    };

    window.addEventListener("pageshow", handlePageShow);
    return () => window.removeEventListener("pageshow", handlePageShow);
  }, []);

  useEffect(() => {
    const handlePortalRestore = async () => {
      if (sessionStorage.getItem(PORTAL_SAML_LAUNCH_FLAG) === "1") {
        sessionStorage.removeItem(PORTAL_LOGOUT_FLAG);
        return;
      }

      if (sessionStorage.getItem(PORTAL_LOGOUT_FLAG) !== "1") {
        return;
      }

      const portalLogoutUrl =
        sessionStorage.getItem("openelis.portalLogoutUrl") || "";
      const targetUrl = buildPortalReturnUrl(
        portalLogoutUrl,
        "openelis",
        "logged_out",
      );

      if (targetUrl) {
        window.location.replace(targetUrl);
      } else {
        window.location.replace(config.loginRedirect);
      }
    };

    handlePortalRestore();
    window.addEventListener("pageshow", handlePortalRestore);
    window.addEventListener("popstate", handlePortalRestore);
    window.addEventListener("hashchange", handlePortalRestore);
    document.addEventListener("visibilitychange", handlePortalRestore);

    return () => {
      window.removeEventListener("pageshow", handlePortalRestore);
      window.removeEventListener("popstate", handlePortalRestore);
      window.removeEventListener("hashchange", handlePortalRestore);
      document.removeEventListener("visibilitychange", handlePortalRestore);
    };
  }, []);

  useEffect(() => {
    const isAuthenticated = Boolean(userSessionDetails?.authenticated);
    const loginMethod =
      userSessionDetails?.loginMethod ||
      sessionStorage.getItem("openelis.lastLoginMethod") ||
      "";
    const isSamlSession = isAuthenticated && loginMethod === "SAML";
    const loggedOutToPortal =
      sessionStorage.getItem(PORTAL_LOGOUT_FLAG) === "1";

    if (!isSamlSession || loggedOutToPortal) {
      sessionStorage.removeItem(BACK_GUARD_FLAG);
      return undefined;
    }

    const trapState = {
      ...(window.history.state || {}),
      [BACK_GUARD_SOURCE]: "trap",
    };
    const rootState = {
      ...(window.history.state || {}),
      [BACK_GUARD_SOURCE]: "root",
    };

    if (sessionStorage.getItem(BACK_GUARD_FLAG) !== "1") {
      window.history.replaceState(rootState, "", window.location.href);
      window.history.pushState(trapState, "", window.location.href);
      sessionStorage.setItem(BACK_GUARD_FLAG, "1");
    }

    const rearmGuard = () => {
      if (
        sessionStorage.getItem(PORTAL_LOGOUT_FLAG) === "1" ||
        sessionStorage.getItem("openelis.lastLoginMethod") !== "SAML"
      ) {
        sessionStorage.removeItem(BACK_GUARD_FLAG);
        return;
      }

      if (window.history.state?.[BACK_GUARD_SOURCE] !== "trap") {
        window.history.pushState(
          {
            ...(window.history.state || {}),
            [BACK_GUARD_SOURCE]: "trap",
          },
          "",
          window.location.href,
        );
      }
    };

    const blockBrowserBack = () => {
      if (
        sessionStorage.getItem(PORTAL_LOGOUT_FLAG) === "1" ||
        sessionStorage.getItem("openelis.lastLoginMethod") !== "SAML"
      ) {
        sessionStorage.removeItem(BACK_GUARD_FLAG);
        return;
      }

      window.history.go(1);
      window.setTimeout(rearmGuard, 0);
    };

    window.addEventListener("popstate", blockBrowserBack);
    window.addEventListener("pageshow", rearmGuard);

    return () => {
      window.removeEventListener("popstate", blockBrowserBack);
      window.removeEventListener("pageshow", rearmGuard);
    };
  }, [
    BACK_GUARD_FLAG,
    BACK_GUARD_SOURCE,
    PORTAL_LOGOUT_FLAG,
    userSessionDetails,
  ]);

  // Load and apply site branding (colors, favicon)
  useEffect(() => {
    loadAndApplyBranding();

    // Listen for branding updates from admin UI
    const handleBrandingUpdate = () => {
      loadAndApplyBranding();
    };
    window.addEventListener("branding-updated", handleBrandingUpdate);

    return () => {
      window.removeEventListener("branding-updated", handleBrandingUpdate);
    };
  }, []);

  const fetchPortalLogoutUrl = async (authenticated) => {
    const endpoint = authenticated
      ? "/rest/configuration-properties"
      : "/rest/open-configuration-properties";

    try {
      const response = await fetch(config.serverBaseUrl + endpoint, {
        credentials: "include",
      });
      if (!response.ok) {
        return "";
      }
      const portalConfigResponse = await response.json();
      const portalLogoutUrl = portalConfigResponse?.samlPortalLogoutUrl;
      const normalized =
        typeof portalLogoutUrl === "string" ? portalLogoutUrl.trim() : "";
      if (normalized) {
        sessionStorage.setItem("openelis.portalLogoutUrl", normalized);
      }
      return normalized;
    } catch (error) {
      console.error(error);
      return "";
    }
  };

  const buildPortalReturnUrl = (url, source, reason) => {
    try {
      const portalUrl = new URL(url);
      portalUrl.searchParams.set("source", source);
      if (reason) {
        portalUrl.searchParams.set(reason, "1");
      }
      return portalUrl.toString();
    } catch (_error) {
      return "";
    }
  };

  const redirectExpiredSamlSessionToPortal = async () => {
    if (sessionStorage.getItem(PORTAL_SAML_LAUNCH_FLAG) === "1") {
      return false;
    }

    const lastLoginMethod =
      userSessionDetails.loginMethod ||
      sessionStorage.getItem("openelis.lastLoginMethod") ||
      "";
    if (lastLoginMethod !== "SAML") {
      return false;
    }

    const portalLogoutUrl = await fetchPortalLogoutUrl(false);
    const targetUrl = buildPortalReturnUrl(
      portalLogoutUrl,
      "openelis",
      "session_expired",
    );
    if (!targetUrl) {
      return false;
    }

    sessionStorage.removeItem(PORTAL_LOGOUT_FLAG);
    sessionStorage.removeItem("openelis.lastLoginMethod");
    window.location.replace(targetUrl);
    return true;
  };

  const redirectLoggedOutSamlSessionToPortal = async () => {
    if (sessionStorage.getItem(PORTAL_SAML_LAUNCH_FLAG) === "1") {
      sessionStorage.removeItem(PORTAL_LOGOUT_FLAG);
      return false;
    }

    if (sessionStorage.getItem(PORTAL_LOGOUT_FLAG) !== "1") {
      return false;
    }

    const portalLogoutUrl =
      sessionStorage.getItem("openelis.portalLogoutUrl") ||
      (await fetchPortalLogoutUrl(false));
    const targetUrl = buildPortalReturnUrl(
      portalLogoutUrl,
      "openelis",
      "logged_out",
    );

    if (!targetUrl) {
      return false;
    }

    window.location.replace(targetUrl);
    return true;
  };

  const getUserSessionDetails = async () => {
    let counter = 0;
    while (counter < 10) {
      try {
        const response = await fetch(
          config.serverBaseUrl + `/session`,
          //includes the browser sessionId in the Header for Authentication on the backend server
          { credentials: "include" },
        );
        if (response.status === 200) {
          const jsonResp = await response.json();
          console.debug(JSON.stringify(jsonResp));
          if (jsonResp.authenticated) {
            localStorage.setItem("CSRF", jsonResp.csrf);
            sessionStorage.removeItem(PORTAL_LOGOUT_FLAG);
            sessionStorage.removeItem(PORTAL_SAML_LAUNCH_FLAG);
            if (jsonResp.loginMethod) {
              sessionStorage.setItem(
                "openelis.lastLoginMethod",
                jsonResp.loginMethod,
              );
            }
          } else {
            if (await redirectLoggedOutSamlSessionToPortal()) {
              return jsonResp;
            }
            if (await redirectExpiredSamlSessionToPortal()) {
              return jsonResp;
            }
            sessionStorage.removeItem(PORTAL_SAML_LAUNCH_FLAG);
            sessionStorage.removeItem("openelis.lastLoginMethod");
          }
          if (
            !Object.keys(jsonResp).every(
              (key) => jsonResp[key] === userSessionDetails[key],
            )
          ) {
            setUserSessionDetails(jsonResp);
          }
          setErrorLoadingSessionDetails(false);
          return jsonResp;
        } else {
          throw new Error(
            "Did not receive a successful response from the backend while retrieving user session details",
          );
        }
      } catch (error) {
        console.error(error);
        if (await redirectLoggedOutSamlSessionToPortal()) {
          return userSessionDetails;
        }
        if (await redirectExpiredSamlSessionToPortal()) {
          return userSessionDetails;
        }
        if (counter === 10) {
          const options = {
            title: "System Error",
            message: "Error : " + error.message,
            buttons: [
              {
                label: "OK",
                onClick: () => {
                  window.location.href = window.location.origin;
                },
              },
            ],
            closeOnClickOutside: false,
            closeOnEscape: false,
          };
          confirmAlert(options);
        }
      }
      ++counter;
    }
    setErrorLoadingSessionDetails(true);
    return userSessionDetails;
  };

  const logout = () => {
    const redirectToLogin = () => {
      sessionStorage.removeItem(PORTAL_LOGOUT_FLAG);
      getUserSessionDetails();
      window.location.replace(config.loginRedirect);
    };

    const addLogoutMarker = (url) => {
      return buildPortalReturnUrl(url, "openelis", "logged_out");
    };

    const probePortalAvailability = async (url) => {
      if (!url) {
        return false;
      }

      const targetUrl = addLogoutMarker(url);
      if (!targetUrl) {
        return false;
      }

      try {
        const parsedUrl = new URL(targetUrl);
        const isLocalhostTarget =
          parsedUrl.hostname === "localhost" ||
          parsedUrl.hostname === "127.0.0.1";
        const currentPageIsHttps = window.location.protocol === "https:";

        // Browsers block HTTPS -> HTTP fetch probes as mixed content even when
        // a top-level redirect to localhost would still be acceptable. For
        // local portal development we skip the probe and allow direct
        // navigation after local logout.
        if (
          isLocalhostTarget &&
          currentPageIsHttps &&
          parsedUrl.protocol === "http:"
        ) {
          return true;
        }
      } catch (_error) {
        return false;
      }

      try {
        const controller = new AbortController();
        const timeoutId = window.setTimeout(() => controller.abort(), 4000);
        await fetch(targetUrl, {
          method: "GET",
          mode: "no-cors",
          cache: "no-store",
          signal: controller.signal,
        });
        window.clearTimeout(timeoutId);
        return true;
      } catch (_error) {
        return false;
      }
    };

    const performLocalLogoutToPortal = async (url) => {
      const targetUrl = addLogoutMarker(url);
      if (!targetUrl) {
        throw new Error("Invalid portal logout URL");
      }

      await fetch(config.serverBaseUrl + "/Logout", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-Token": localStorage.getItem("CSRF"),
        },
      });

      sessionStorage.setItem(PORTAL_LOGOUT_FLAG, "1");
      sessionStorage.removeItem("openelis.lastLoginMethod");
      window.location.replace(targetUrl);
    };

    const performSamlSingleLogout = () => {
      fetch(config.serverBaseUrl + "/Logout?useSAML=true", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-Token": localStorage.getItem("CSRF"),
        },
      })
        .then((response) => response.text())
        .then((html) => {
          const parser = new DOMParser();
          const doc = parser.parseFromString(html, "text/html");
          const samlForm = doc.querySelector("form");

          if (samlForm) {
            const form = document.createElement("form");
            form.method = samlForm.method || "POST";
            form.action = samlForm.action;
            Array.from(samlForm.querySelectorAll("input")).forEach((input) => {
              const hidden = document.createElement("input");
              hidden.type = "hidden";
              hidden.name = input.name;
              hidden.value = input.value;
              form.appendChild(hidden);
            });
            document.body.appendChild(form);
            form.submit();
          } else {
            redirectToLogin();
          }
        })
        .catch((error) => {
          console.error(error);
          redirectToLogin();
        });
    };

    const performStandardLogout = () => {
      fetch(config.serverBaseUrl + "/Logout", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-CSRF-Token": localStorage.getItem("CSRF"),
        },
      })
        .then((response) => response.status)
        .then(() => {
          redirectToLogin();
        })
        .catch((error) => {
          console.error(error);
        });
    };

    if (userSessionDetails.loginMethod === "SAML") {
      fetchPortalLogoutUrl(userSessionDetails.authenticated)
        .then((portalLogoutUrl) => {
          if (!portalLogoutUrl) {
            performSamlSingleLogout();
            return null;
          }

          return probePortalAvailability(portalLogoutUrl)
            .then((isAvailable) => {
              if (isAvailable) {
                return performLocalLogoutToPortal(portalLogoutUrl);
              }

              performSamlSingleLogout();
              return null;
            })
            .catch((error) => {
              console.error(error);
              performSamlSingleLogout();
              return null;
            });
        })
        .catch((error) => {
          console.error(error);
          performSamlSingleLogout();
        });
    } else {
      performStandardLogout();
    }
  };

  const changeLanguageReact = (lang) => {
    if (!languages[lang]) {
      lang = "en";
    }
    setLocale(lang);
    setMessages(languages[lang].messages);
    localStorage.setItem("locale", lang);
  };

  const changeLanguageBackend = async (lang) => {
    if (userSessionDetails.authenticated) {
      getFromOpenElisServer("/Home?lang=" + lang, () => {
        // Language changed on backend
      });
    } else {
      getFromOpenElisServer("/LoginPage?lang=" + lang, () => {
        // Language changed on backend
      });
    }
  };

  const onChangeLanguage = (lang) => {
    changeLanguageReact(lang);
    changeLanguageBackend(lang);
    // Re-apply branding after locale switch because IntlProvider remounts
    // themed subtrees and Carbon theme tokens can revert to defaults.
    setTimeout(() => {
      loadAndApplyBranding();
    }, 0);
  };

  const refresh = async (callback) => {
    await getUserSessionDetails();
    if (typeof callback === "function") {
      callback();
    }
  };

  const isCheckingLogin = () => {
    return !("authenticated" in userSessionDetails);
  };

  return (
    <IntlProvider
      locale={locale}
      key={locale}
      defaultLocale="en"
      messages={messages}
    >
      <UserSessionDetailsContext.Provider
        value={{
          userSessionDetails,
          errorLoadingSessionDetails,
          isCheckingLogin,
          logout,
          refresh,
        }}
      >
        <>
          <Router>
            <Layout onChangeLanguage={onChangeLanguage}>
              <Switch>
                <Route path="/login" exact component={() => <Login />} />
                <Route
                  path="/ChangePasswordLogin"
                  exact
                  component={() => <ChangePassword />}
                />
                <Route
                  path="/landing"
                  exact
                  component={() => <LandingPage />}
                />
                <SecureRoute
                  path="/"
                  exact
                  component={() => <Home />}
                  role=""
                />
                <SecureRoute
                  path="/Dashboard"
                  exact
                  component={() => <Home />}
                  role=""
                />
                <SecureRoute
                  path="/admin"
                  exact
                  component={() => <Admin />}
                  role={Roles.ADMINISTRATION}
                />
                <SecureRoute
                  path="/MasterListsPage"
                  component={() => <Admin />}
                  role={Roles.ADMINISTRATION}
                />
                <SecureRoute
                  path="/PathologyDashboard"
                  exact
                  component={() => <PathologyDashboard />}
                  role=""
                  labUnitRole={{ Pathology: [Roles.RESULTS] }}
                />
                <SecureRoute
                  path="/PathologyCaseView/:pathologySampleId"
                  exact
                  component={() => <PathologyCaseView />}
                  role=""
                  labUnitRole={{ Pathology: [Roles.RESULTS] }}
                />
                <SecureRoute
                  path="/ImmunohistochemistryDashboard"
                  exact
                  component={() => <ImmunohistochemistryDashboard />}
                  role=""
                  labUnitRole={{ Immunohistochemistry: [Roles.RESULTS] }}
                />
                <SecureRoute
                  path="/ImmunohistochemistryCaseView/:immunohistochemistrySampleId"
                  exact
                  component={() => <ImmunohistochemistryCaseView />}
                  role=""
                  labUnitRole={{ Immunohistochemistry: [Roles.RESULTS] }}
                />
                <SecureRoute
                  path="/CytologyDashboard"
                  exact
                  component={() => <CytologyDashboard />}
                  role=""
                />
                <SecureRoute
                  path="/genericProgram"
                  exact
                  component={() => <ProgramDashboard />}
                  role={Roles.RECEPTION}
                />
                <SecureRoute
                  path="/programView/:programSampleId"
                  exact
                  component={() => <ProgramCaseView />}
                  role={Roles.RECEPTION}
                />
                <SecureRoute
                  path="/NoteBookDashboard"
                  exact
                  component={() => <NoteBookDashBoard />}
                  role={[Roles.RECEPTION, Roles.RESULTS, Roles.VALIDATION]}
                />
                <SecureRoute
                  path="/NoteBookEntryForm/:notebookid"
                  exact
                  component={() => <NoteBookEntryForm />}
                  role={Roles.GLOBAL_ADMIN}
                />
                <SecureRoute
                  path="/NoteBookEntryForm"
                  exact
                  component={() => <NoteBookEntryForm />}
                  role={Roles.GLOBAL_ADMIN}
                />
                <SecureRoute
                  path="/NoteBookInstanceEntryForm/:notebookid"
                  exact
                  component={() => <NoteBookInstanceEntryForm />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/NoteBookInstanceEditForm/:notebookentryid"
                  exact
                  component={() => <NoteBookInstanceEntryForm />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/NotebookSampleOrder/:notebookId/:notebookEntryId"
                  exact
                  component={() => <NotebookSampleOrder />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/NotebookSampleOrder/:notebookId"
                  exact
                  component={() => <NotebookSampleOrder />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/CytologyCaseView/:cytologySampleId"
                  exact
                  component={() => <CytologyCaseView />}
                  role=""
                  labUnitRole={{ Cytology: [Roles.RESULTS] }}
                />
                <SecureRoute
                  path="/GenericSample/Order"
                  exact
                  component={() => {
                    const GenericSampleOrder =
                      require("./components/genericSample/GenericSampleOrder").default;
                    return <GenericSampleOrder />;
                  }}
                  role={Roles.GENERIC_SAMPLE}
                />
                <SecureRoute
                  path="/GenericSample/Edit"
                  exact
                  component={() => {
                    const GenericSampleOrderEdit =
                      require("./components/genericSample/GenericSampleOrderEdit").default;
                    return <GenericSampleOrderEdit />;
                  }}
                  role={Roles.GENERIC_SAMPLE}
                />
                <SecureRoute
                  path="/GenericSample/Import"
                  exact
                  component={() => {
                    const GenericSampleOrderImport =
                      require("./components/genericSample/GenericSampleOrderImport").default;
                    return <GenericSampleOrderImport />;
                  }}
                  role={Roles.GENERIC_SAMPLE}
                />
                <SecureRoute
                  path="/FreezerMonitoring"
                  exact
                  component={() => <FreezerMonitoringDashboard />}
                  role={Roles.RECEPTION}
                />
                <SecureRoute
                  path="/SamplePatientEntry"
                  exact
                  component={() => <AddOrder />}
                  role={Roles.ORDER}
                />
                <SecureRoute
                  path="/ModifyOrder"
                  exact
                  component={() => <ModifyOrder />}
                  role={Roles.ORDER}
                />
                <SecureRoute
                  path="/SampleEdit"
                  exact
                  component={() => <FindOrder />}
                  role={Roles.ORDER}
                />
                <SecureRoute
                  path="/ReportNonConformingEvent"
                  exact
                  component={() => (
                    <NonConformIndex form="ReportNonConformingEvent" />
                  )}
                  role={[Roles.RECEPTION, Roles.VALIDATION]}
                />
                <SecureRoute
                  path="/ViewNonConformingEvent"
                  exact
                  component={() => (
                    <NonConformIndex form="ViewNonConformingEvent" />
                  )}
                  role={[Roles.RECEPTION, Roles.VALIDATION]}
                />

                <SecureRoute
                  path="/NCECorrectiveAction"
                  exact
                  component={() => (
                    <NonConformIndex form="NCECorrectiveAction" />
                  )}
                  role={[Roles.RECEPTION, Roles.VALIDATION]}
                />

                <SecureRoute
                  path="/SampleBatchEntrySetup"
                  exact
                  component={() => <SampleBatchEntrySetup />}
                  role={Roles.ORDER}
                />

                <SecureRoute
                  path="/ElectronicOrders"
                  exact
                  component={() => <EOrderPage />}
                  role={Roles.ORDER}
                />
                <SecureRoute
                  path="/PrintBarcode"
                  exact
                  component={() => <PrintBarcode />}
                  role={Roles.ORDER}
                />
                <SecureRoute
                  path="/PatientManagement"
                  exact
                  component={() => <PatientManagement />}
                  role={Roles.PATIENT}
                />
                <SecureRoute
                  path="/Storage"
                  exact
                  component={() => <StorageDashboard />}
                  role={[
                    Roles.STORAGE,
                    Roles.RECEPTION,
                    Roles.RESULTS,
                    Roles.GLOBAL_ADMIN,
                  ]}
                />
                <SecureRoute
                  path="/Storage/:tab"
                  component={() => <StorageDashboard />}
                  role={[
                    Roles.STORAGE,
                    Roles.RECEPTION,
                    Roles.RESULTS,
                    Roles.GLOBAL_ADMIN,
                  ]}
                />
                <SecureRoute
                  path="/inventory"
                  exact
                  component={() => <InventoryManagement />}
                  role={[Roles.RESULTS, Roles.GLOBAL_ADMIN]}
                />
                <SecureRoute
                  path="/SampleManagement"
                  exact
                  component={() => <SampleManagement />}
                  role={[Roles.RECEPTION, Roles.RESULTS]}
                />
                <SecureRoute
                  path="/analyzers"
                  exact
                  component={() => <AnalyzersPage />}
                  role={[Roles.ADMINISTRATION, Roles.GLOBAL_ADMIN]}
                />
                <SecureRoute
                  path="/analyzers/:id/mappings"
                  exact
                  component={FieldMapping}
                  role={[Roles.ADMINISTRATION, Roles.GLOBAL_ADMIN]}
                />
                <SecureRoute
                  path="/analyzers/errors"
                  exact
                  component={() => <ErrorDashboardPage />}
                  role={[Roles.ADMINISTRATION, Roles.GLOBAL_ADMIN]}
                />
                <SecureRoute
                  path="/analyzers/custom-field-types"
                  exact
                  component={() => <CustomFieldTypeManagementPage />}
                  role={[Roles.ADMINISTRATION, Roles.GLOBAL_ADMIN]}
                />
                <SecureRoute
                  path="/analyzers/types"
                  exact
                  component={() => <AnalyzerTypesPage />}
                  role={[Roles.ADMINISTRATION, Roles.GLOBAL_ADMIN]}
                />
                <SecureRoute
                  path="/validation-template-config"
                  exact
                  component={() => <ValidationTemplateOverrideConfigPage />}
                  role={Roles.GLOBAL_ADMIN}
                />
                <SecureRoute
                  path="/consent-template-config"
                  exact
                  component={() => <ConsentTemplateConfigPage />}
                  role={Roles.GLOBAL_ADMIN}
                />
                <SecureRoute
                  path="/analyzers/qc"
                  exact
                  component={() => <QCDashboardPlaceholder />}
                  role={[Roles.ADMINISTRATION, Roles.GLOBAL_ADMIN]}
                />
                <SecureRoute
                  path="/analyzers/qc/alerts"
                  exact
                  component={() => <QCAlertsPlaceholder />}
                  role={[Roles.ADMINISTRATION, Roles.GLOBAL_ADMIN]}
                />
                <SecureRoute
                  path="/analyzers/qc/corrective-actions"
                  exact
                  component={() => <CorrectiveActionsPlaceholder />}
                  role={[Roles.ADMINISTRATION, Roles.GLOBAL_ADMIN]}
                />
                <SecureRoute
                  path="/PatientHistory"
                  exact
                  component={() => <PatientHistory />}
                  role={Roles.PATIENT}
                />
                <SecureRoute
                  path="/PatientMerge"
                  exact
                  component={() => <PatientMerge />}
                  role={Roles.GLOBAL_ADMIN}
                />
                <SecureRoute
                  path="/GenericSample/Results"
                  exact
                  component={() => {
                    const GenericSampleResults =
                      require("./components/genericSample/GenericSampleResults").default;
                    return <GenericSampleResults />;
                  }}
                  role={Roles.GENERIC_SAMPLE}
                />
                <SecureRoute
                  path="/Aliquot"
                  exact
                  component={() => <Aliquot />}
                  role={[Roles.ALIQUOT, Roles.RECEPTION]}
                />

                <SecureRoute
                  path="/PatientResults/:patientId"
                  exact
                  component={() => <RoutedResultsViewer />}
                  role={Roles.PATIENT}
                />

                <SecureRoute
                  path="/WorkPlanByTestSection"
                  exact
                  component={() => <Workplan type="unit" />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/WorkplanByTest"
                  exact
                  component={() => <Workplan type="test" />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/WorkplanByPanel"
                  exact
                  component={() => <Workplan type="panel" />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/WorkplanByPriority"
                  exact
                  component={() => <Workplan type="priority" />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/result"
                  exact
                  component={() => <ResultSearch />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/LogbookResults"
                  exact
                  component={() => <ResultSearch />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/PatientResults"
                  exact
                  component={() => <ResultSearch />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/AccessionResults"
                  exact
                  component={() => <ResultSearch />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/StatusResults"
                  exact
                  component={() => <ResultSearch />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/RangeResults"
                  exact
                  component={() => <ResultSearch />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/ReferredOutTests"
                  exact
                  component={() => <ReferredOutTests />}
                  role={Roles.RESULTS}
                />
                <SecureRoute
                  path="/RoutineReports"
                  exact
                  component={() => <RoutineReports />}
                  role={Roles.REPORTS}
                />
                <SecureRoute
                  path="/RoutineReport"
                  exact
                  component={() => <RoutineIndex />}
                  role={Roles.REPORTS}
                />
                <SecureRoute
                  path="/StudyReports"
                  exact
                  component={() => <StudyReports />}
                  role={Roles.REPORTS}
                />
                <SecureRoute
                  path="/StudyReport"
                  exact
                  component={() => <StudyIndex />}
                  role={Roles.REPORTS}
                />
                <SecureRoute
                  path="/Report"
                  exact
                  component={() => <ReportIndex />}
                  role={Roles.REPORTS}
                />
                <SecureRoute
                  path="/AuditTrailReport"
                  exact
                  component={() => <AuditTrailReportIndex />}
                  role={Roles.REPORTS}
                />
                <SecureRoute
                  path="/validation"
                  exact
                  component={() => <StudyValidation />}
                  role={Roles.VALIDATION}
                />
                <SecureRoute
                  path="/ResultValidation"
                  exact
                  component={() => <StudyValidation />}
                  role={Roles.VALIDATION}
                />
                <SecureRoute
                  path="/AccessionValidation"
                  exact
                  component={() => <StudyValidation />}
                  role={Roles.VALIDATION}
                />
                <SecureRoute
                  path="/AccessionValidationRange"
                  exact
                  component={() => <StudyValidation />}
                  role={Roles.VALIDATION}
                />
                <SecureRoute
                  path="/ResultValidationByTestDate"
                  exact
                  component={() => <StudyValidation />}
                  role={Roles.VALIDATION}
                />
                <SecureRoute
                  path="/AnalyzerResults"
                  exact
                  component={() => <AnalyserResultIndex />}
                  role={Roles.ANALYSER_IMPORT}
                />
                <Route path="*" component={() => <RedirectOldUI />} />
              </Switch>
            </Layout>
          </Router>
        </>
      </UserSessionDetailsContext.Provider>
    </IntlProvider>
  );
}
