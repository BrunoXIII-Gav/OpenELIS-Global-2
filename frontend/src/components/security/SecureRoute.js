import React, { useState, useContext, useEffect, useRef } from "react";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import { ConfigurationContext } from "../layout/Layout";
import { Route } from "react-router-dom";
import { useLocation } from "react-router-dom";
import { useIdleTimer } from "react-idle-timer";
import "react-confirm-alert/src/react-confirm-alert.css"; // Import css
import { Loading, Modal } from "@carbon/react/";
import config from "../../config.json";
import { Roles } from "../utils/Utils";
import { FormattedMessage, useIntl } from "react-intl";
import AccessDeniedPanel from "./AccessDeniedPanel";
import { emitAccessDeniedEvent } from "./accessDenied";

const idleTimeout = 1000 * 60 * 30; // milliseconds until idle warning will appear
const idleWarningTimeout = 1000 * 60; // milliseconds until logout is automatically processed from idle warning
const idleLogoutTimeout = idleTimeout + idleWarningTimeout;

const normalizeRoles = (roles) =>
  Array.isArray(roles) ? roles : Object.values(roles || {});

function SecureRoute(props) {
  const [permissionGranted, setPermissionGranted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [stillThereOpen, setStillThereOpen] = useState(false);
  const [accessDenied, setAccessDenied] = useState(false);
  const accessDeniedShownRef = useRef(false);

  const intl = useIntl();
  const location = useLocation();

  const {
    userSessionDetails,
    errorLoadingSessionDetails,
    isCheckingLogin,
    logout,
  } = useContext(UserSessionDetailsContext);

  const { configurationProperties } = useContext(ConfigurationContext);

  const showAccessDenied = () => {
    setPermissionGranted(false);
    setAccessDenied(true);

    if (accessDeniedShownRef.current) {
      return;
    }

    accessDeniedShownRef.current = true;
    emitAccessDeniedEvent({
      redirectToHome: true,
    });
  };

  const checkRouteAccess = async () => {
    try {
      const targetUrl = `${location.pathname}${location.search || ""}`;
      const response = await fetch(
        `${config.serverBaseUrl}/rest/module-access?url=${encodeURIComponent(targetUrl)}`,
        {
          credentials: "include",
          method: "GET",
        },
      );

      if (response.status === 401) {
        return false;
      }

      if (!response.ok) {
        return false;
      }

      const data = await response.json();
      return data?.allowed === true;
    } catch (_error) {
      return false;
    }
  };

  useEffect(() => {
    let cancelled = false;

    const evaluateAccess = async () => {
      setLoading(!errorLoadingSessionDetails && isCheckingLogin());
      setAccessDenied(false);
      accessDeniedShownRef.current = false;

      if (userSessionDetails.authenticated) {
        const roleAllowed = hasPermission(userSessionDetails);
        if (!roleAllowed) {
          if (!cancelled) {
            showAccessDenied();
          }
          return;
        }

        if (
          configurationProperties.REQUIRE_LAB_UNIT_AT_LOGIN === "true" &&
          !userSessionDetails.loginLabUnit &&
          !normalizeRoles(userSessionDetails.roles).includes(Roles.GLOBAL_ADMIN)
        ) {
          window.location.href = "/landing";
          return;
        }

        const routeAllowed = await checkRouteAccess();
        if (cancelled) {
          return;
        }
        if (!routeAllowed) {
          showAccessDenied();
          return;
        }

        setPermissionGranted(true);
        setAccessDenied(false);
      } else if ("authenticated" in userSessionDetails) {
        window.location.href = config.loginRedirect;
      }
    };

    evaluateAccess();
    return () => {
      cancelled = true;
    };
  }, [
    userSessionDetails,
    errorLoadingSessionDetails,
    location.pathname,
    location.search,
  ]);

  const hasPermission = (userDetails = userSessionDetails) => {
    var hasRole =
      !props.role ||
      []
        .concat(props.role)
        .some((role) => normalizeRoles(userDetails.roles).includes(role));
    var containsLabUnitRole = false;
    if (props.labUnitRole) {
      Object.keys(props.labUnitRole).forEach((labunit) => {
        if (userDetails.userLabRolesMap) {
          const userRoles = userDetails.userLabRolesMap["AllLabUnits"]
            ? userDetails.userLabRolesMap["AllLabUnits"]
            : userDetails.userLabRolesMap[labunit] || [];
          const roles = props.labUnitRole[labunit];
          roles.forEach((r) => {
            if (userRoles.includes(r)) {
              containsLabUnitRole = true;
            }
          });
        }
      });
    }
    var hasLabUnitRole = !props.labUnitRole || containsLabUnitRole;
    return hasRole && hasLabUnitRole;
  };

  const onIdle = () => {
    setStillThereOpen(false);
    console.debug("idleTimer now idle");
    logout();
  };

  const onActive = () => {
    setStillThereOpen(false);
    console.debug("idleTimer now active");
  };

  const onPrompt = () => {
    setStillThereOpen(true);
    console.debug("idleTimer now prompting");
  };

  const { activate } = useIdleTimer({
    onIdle,
    onActive,
    onPrompt,
    timeout: idleLogoutTimeout,
    promptBeforeIdle: idleWarningTimeout,
    crossTab: true,
    syncTimers: true,
  });

  const handleStillHere = () => {
    activate();
  };

  return (
    <>
      <Modal
        open={stillThereOpen}
        onRequestClose={() => {
          setStillThereOpen(false);
          handleStillHere();
        }}
        modalHeading={intl.formatMessage({ id: "stillThere.title" })}
        passiveModal
      >
        <FormattedMessage id="stillThere.message" />
      </Modal>
      {loading && <Loading />}
      {!loading &&
        !userSessionDetails.authenticated &&
        intl.formatMessage({ id: "notAuthenticated" })}
      {!loading && userSessionDetails.authenticated && accessDenied && <AccessDeniedPanel />}
      {!loading && userSessionDetails.authenticated && permissionGranted && (
        <>{!stillThereOpen && <Route {...props} />}</>
      )}
    </>
  );
}

export default SecureRoute;
