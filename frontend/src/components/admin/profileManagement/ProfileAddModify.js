import React, { useContext, useEffect, useMemo, useState } from "react";
import { Button, Column, Form, Grid, Heading, Loading, Section, TextInput } from "@carbon/react";
import { useHistory, useLocation } from "react-router-dom";
import { FormattedMessage, useIntl } from "react-intl";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import { NotificationContext } from "../../layout/Layout.js";
import { NotificationKinds } from "../../common/CustomNotification.js";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils.js";

const toCodeCandidate = (value = "") =>
  value
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .replace(/_+/g, "_");

function ProfileAddModify() {
  const history = useHistory();
  const location = useLocation();
  const intl = useIntl();
  const { addNotification, setNotificationVisible } =
    useContext(NotificationContext);
  const [loading, setLoading] = useState(true);
  const [formData, setFormData] = useState({ code: "", name: "" });

  const profileCode = useMemo(() => {
    const search = new URLSearchParams(location.search);
    return search.get("code") || "new";
  }, [location.search]);

  const isNew = profileCode === "new";

  const breadcrumbs = useMemo(
    () => [
      { label: "home.label", link: "/" },
      { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
      {
        label: "professionalProfile.page.title",
        link: "/MasterListsPage/profileManagement",
      },
      {
        label: isNew
          ? "professionalProfile.add.title"
          : "professionalProfile.edit.title",
        link: `/MasterListsPage/profileEdit?code=${profileCode}`,
      },
    ],
    [isNew, profileCode],
  );

  useEffect(() => {
    if (isNew) {
      setLoading(false);
      return;
    }

    getFromOpenElisServer(
      `/rest/professional-profiles/${encodeURIComponent(profileCode)}`,
      (response) => {
        setFormData({
          code: response?.code || profileCode,
          name: response?.name || "",
        });
        setLoading(false);
      },
    );
  }, [isNew, profileCode]);

  const showError = async (response) => {
    let message = intl.formatMessage({ id: "server.error.msg" });
    if (response && typeof response.json === "function") {
      try {
        const payload = await response.json();
        message = payload?.message || message;
      } catch (_error) {
        // ignore parse failure
      }
    }
    addNotification({
      kind: NotificationKinds.error,
      title: intl.formatMessage({ id: "notification.title" }),
      message,
    });
    setNotificationVisible(true);
    setLoading(false);
  };

  const handleSuccess = () => {
    addNotification({
      kind: NotificationKinds.success,
      title: intl.formatMessage({ id: "notification.title" }),
      message: intl.formatMessage({
        id: "professionalProfile.save.success",
        defaultMessage: "Professional profile saved successfully",
      }),
    });
    setNotificationVisible(true);
    history.push("/MasterListsPage/profileManagement");
  };

  const handleSave = () => {
    setLoading(true);
    const payload = JSON.stringify({
      code: formData.code,
      name: formData.name,
    });

    if (isNew) {
      postToOpenElisServerJsonResponse(
        "/rest/professional-profiles",
        payload,
        (response) => {
          if (response?.code) {
            handleSuccess();
            return;
          }
          showError();
        },
      );
      return;
    }

    putToOpenElisServerFullResponse(
      `/rest/professional-profiles/${encodeURIComponent(profileCode)}`,
      payload,
      async (response) => {
        if (response.ok) {
          handleSuccess();
          return;
        }
        showError(response);
      },
    );
  };

  if (loading) {
    return <Loading />;
  }

  return (
    <div className="adminPageContent">
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Grid fullWidth>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading>
              {isNew ? (
                <FormattedMessage
                  id="professionalProfile.add.title"
                  defaultMessage="Create Professional Profile"
                />
              ) : (
                <FormattedMessage
                  id="professionalProfile.edit.title"
                  defaultMessage="Edit Professional Profile"
                />
              )}
            </Heading>
          </Section>
          <br />
          <Form>
            <Grid fullWidth>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="professional-profile-name"
                  labelText={intl.formatMessage({
                    id: "professionalProfile.fields.name",
                    defaultMessage: "Profile name",
                  })}
                  value={formData.name}
                  onChange={(event) => {
                    const name = event.target.value;
                    setFormData((previousFormData) => ({
                      ...previousFormData,
                      name,
                      code:
                        isNew &&
                        (!previousFormData.code ||
                          previousFormData.code ===
                            toCodeCandidate(previousFormData.name))
                          ? toCodeCandidate(name)
                          : previousFormData.code,
                    }));
                  }}
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="professional-profile-code"
                  labelText={intl.formatMessage({
                    id: "professionalProfile.fields.code",
                    defaultMessage: "Profile code",
                  })}
                  value={formData.code}
                  onChange={(event) =>
                    setFormData((previousFormData) => ({
                      ...previousFormData,
                      code: toCodeCandidate(event.target.value),
                    }))
                  }
                  readOnly={!isNew}
                />
              </Column>
            </Grid>
            <br />
            <Button
              type="button"
              disabled={!formData.name.trim()}
              onClick={handleSave}
            >
              <FormattedMessage id="label.button.save" />
            </Button>
            <Button
              type="button"
              kind="tertiary"
              onClick={() => history.push("/MasterListsPage/profileManagement")}
            >
              <FormattedMessage id="label.button.exit" />
            </Button>
          </Form>
        </Column>
      </Grid>
    </div>
  );
}

export default ProfileAddModify;
