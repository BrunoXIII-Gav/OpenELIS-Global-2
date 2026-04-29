import React, { useState, useContext, useEffect, useRef } from "react";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import { ConfigurationContext } from "../layout/Layout";
import { Route } from "react-router-dom";
import { useLocation } from "react-router-dom";
import { useIdleTimer } from "react-idle-timer";
import { confirmAlert } from "react-confirm-alert";
import "react-confirm-alert/src/react-confirm-alert.css"; // Import css
import { Loading, Modal } from "@carbon/react/";
import config from "../../config.json";
import { Roles } from "../utils/Utils";
import { FormattedMessage, useIntl } from "react-intl";

const idleTimeout = 1000 * 60 * 30; // milliseconds until idle warning will appear
const idleWarningTimeout = 1000 * 60; // milliseconds until logout is automatically processed from idle warning
const idleLogoutTimeout = idleTimeout + idleWarningTimeout;

function SecureRoute(props) {
  const [permissionGranted, setPermissionGranted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [stillThereOpen, setStillThereOpen] = useState(false);

  const intl = useIntl();
  const location = useLocation();

  const {
    userSessionDetails,
    errorLoadingSessionDetails,
    isCheckingLogin,
    logout,
  } = useContext(UserSessionDetailsContext);

  const { configurationProperties } = useContext(ConfigurationContext);

  const showAccessDeniedDialog = () => {
    const options = {
      title: intl.formatMessage({ id: "accessDenied.title" }),
      message: intl.formatMessage({ id: "accessDenied.message" }),
      buttons: [
        {
          label: intl.formatMessage({ id: "accessDenied.okButton" }),
          onClick: () => {
            window.location.href = window.location.origin;
          },
        },
      ],
      closeOnClickOutside: false,
      closeOnEscape: false,
    };
    confirmAlert(options);
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

      if (userSessionDetails.authenticated) {
        const roleAllowed = hasPermission(userSessionDetails);
        if (!roleAllowed) {
          showAccessDeniedDialog();
          if (!cancelled) {
            setPermissionGranted(false);
          }
          return;
        }

        if (
          configurationProperties.REQUIRE_LAB_UNIT_AT_LOGIN === "true" &&
          !userSessionDetails.loginLabUnit &&
          !userSessionDetails.roles.includes(Roles.GLOBAL_ADMIN)
        ) {
          window.location.href = "/landing";
          return;
        }

        const routeAllowed = await checkRouteAccess();
        if (cancelled) {
          return;
        }
        if (!routeAllowed) {
          showAccessDeniedDialog();
          setPermissionGranted(false);
          return;
        }

        setPermissionGranted(true);
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
        .some((role) => userDetails.roles && userDetails.roles.includes(role));
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
      {!loading && userSessionDetails.authenticated && permissionGranted && (
        <>{!stillThereOpen && <Route {...props} />}</>
      )}
    </>
  );
}

export default SecureRoute;
