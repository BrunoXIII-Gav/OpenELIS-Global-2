import React, { useContext, useEffect, useMemo, useState } from "react";
import {
  Button,
  Column,
  DataTable,
  Grid,
  Heading,
  Loading,
  MultiSelect,
  Section,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from "@carbon/react";
import { useHistory } from "react-router-dom";
import { FormattedMessage, useIntl } from "react-intl";
import { confirmAlert } from "react-confirm-alert";
import PageBreadCrumb from "../../common/PageBreadCrumb.js";
import { NotificationContext } from "../../layout/Layout.js";
import { NotificationKinds } from "../../common/CustomNotification.js";
import {
  deleteFromOpenElisServerFullResponse,
  getFromOpenElisServer,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils.js";

const headers = [
  { key: "code", header: "professionalProfile.fields.code" },
  { key: "name", header: "professionalProfile.fields.name" },
];

function ProfileManagement() {
  const history = useHistory();
  const intl = useIntl();
  const { addNotification, setNotificationVisible } =
    useContext(NotificationContext);
  const [loading, setLoading] = useState(true);
  const [profiles, setProfiles] = useState([]);
  const [settings, setSettings] = useState({
    orderProviderProfessionalProfileCodes: [],
    sampleCollectorProfessionalProfileCodes: [],
    patientEntryProfessionalProfileCodes: [],
    resultEntryProfessionalProfileCodes: [],
    validationInterpreterProfessionalProfileCodes: [],
  });

  const breadcrumbs = useMemo(
    () => [
      { label: "home.label", link: "/" },
      { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
      {
        label: "professionalProfile.page.title",
        link: "/MasterListsPage/profileManagement",
      },
    ],
    [],
  );

  const loadCatalog = () => {
    setLoading(true);
    getFromOpenElisServer("/rest/professional-profiles/catalog", (response) => {
      setProfiles(
        Array.isArray(response?.profiles)
          ? response.profiles.map((profile) => ({
              ...profile,
              id: profile.code,
            }))
          : [],
      );
      setSettings({
        orderProviderProfessionalProfileCodes:
          response?.settings?.orderProviderProfessionalProfileCodes || [],
        sampleCollectorProfessionalProfileCodes:
          response?.settings?.sampleCollectorProfessionalProfileCodes || [],
        patientEntryProfessionalProfileCodes:
          response?.settings?.patientEntryProfessionalProfileCodes || [],
        resultEntryProfessionalProfileCodes:
          response?.settings?.resultEntryProfessionalProfileCodes || [],
        validationInterpreterProfessionalProfileCodes:
          response?.settings?.validationInterpreterProfessionalProfileCodes ||
          [],
      });
      setLoading(false);
    });
  };

  useEffect(() => {
    loadCatalog();
  }, []);

  const showNotification = (kind, message) => {
    addNotification({
      kind,
      title: intl.formatMessage({ id: "notification.title" }),
      message,
    });
    setNotificationVisible(true);
  };

  const saveSettings = () => {
    setLoading(true);
    putToOpenElisServerFullResponse(
      "/rest/professional-profiles/settings",
      JSON.stringify(settings),
      async (response) => {
        if (response.ok) {
          showNotification(
            NotificationKinds.success,
            intl.formatMessage({
              id: "professionalProfile.settings.save.success",
              defaultMessage:
                "Professional profile configuration saved successfully",
            }),
          );
          loadCatalog();
          return;
        }

        let message = intl.formatMessage({ id: "server.error.msg" });
        try {
          const payload = await response.json();
          message = payload?.message || message;
        } catch (_error) {
          // ignore parse failure
        }
        showNotification(NotificationKinds.error, message);
        setLoading(false);
      },
    );
  };

  const handleDelete = (code) => {
    confirmAlert({
      title: intl.formatMessage({
        id: "professionalProfile.delete.confirm.title",
        defaultMessage: "Delete professional profile",
      }),
      message: intl.formatMessage({
        id: "professionalProfile.delete.confirm.body",
        defaultMessage:
          "This professional profile will no longer be available for users and professional records.",
      }),
      buttons: [
        {
          label: intl.formatMessage({
            id: "professionalProfile.delete.confirm.ok",
            defaultMessage: "Delete",
          }),
          onClick: () => {
            deleteFromOpenElisServerFullResponse(
              `/rest/professional-profiles/${code}`,
              async (response) => {
                if (response.ok) {
                  showNotification(
                    NotificationKinds.success,
                    intl.formatMessage({
                      id: "professionalProfile.delete.success",
                      defaultMessage:
                        "Professional profile deleted successfully",
                    }),
                  );
                  loadCatalog();
                  return;
                }

                let message = intl.formatMessage({ id: "server.error.msg" });
                try {
                  const payload = await response.json();
                  message = payload?.message || message;
                } catch (_error) {
                  // ignore parse failure
                }
                showNotification(NotificationKinds.error, message);
              },
            );
          },
        },
        {
          label: intl.formatMessage({
            id: "professionalProfile.delete.confirm.cancel",
            defaultMessage: "Cancel",
          }),
        },
      ],
    });
  };

  if (loading) {
    return <Loading />;
  }

  const getSelectedProfiles = (selectedCodes) =>
    profiles.filter((profile) => selectedCodes.includes(profile.code));

  return (
    <div className="adminPageContent">
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Grid fullWidth>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading>
              <FormattedMessage
                id="professionalProfile.page.title"
                defaultMessage="Professional Profile Management"
              />
            </Heading>
          </Section>
          <br />
          <Grid fullWidth>
            <Column lg={5} md={4} sm={4}>
              <MultiSelect
                id="order-provider-profile"
                titleText={intl.formatMessage({
                  id: "professionalProfile.settings.orderProvider",
                  defaultMessage: "Order requester profile",
                })}
                labelText={intl.formatMessage({
                  id: "professionalProfile.settings.orderProvider",
                  defaultMessage: "Order requester profile",
                })}
                items={profiles}
                itemToString={(item) => item?.name || ""}
                selectedItems={getSelectedProfiles(
                  settings.orderProviderProfessionalProfileCodes,
                )}
                selectionFeedback="top-after-reopen"
                onChange={({ selectedItems }) =>
                  setSettings((previousSettings) => ({
                    ...previousSettings,
                    orderProviderProfessionalProfileCodes: selectedItems.map(
                      (item) => item.code,
                    ),
                  }))
                }
              />
            </Column>
            <Column lg={5} md={4} sm={4}>
              <MultiSelect
                id="sample-collector-profile"
                titleText={intl.formatMessage({
                  id: "professionalProfile.settings.sampleCollector",
                  defaultMessage: "Sample collector profile",
                })}
                labelText={intl.formatMessage({
                  id: "professionalProfile.settings.sampleCollector",
                  defaultMessage: "Sample collector profile",
                })}
                items={profiles}
                itemToString={(item) => item?.name || ""}
                selectedItems={getSelectedProfiles(
                  settings.sampleCollectorProfessionalProfileCodes,
                )}
                selectionFeedback="top-after-reopen"
                onChange={({ selectedItems }) =>
                  setSettings((previousSettings) => ({
                    ...previousSettings,
                    sampleCollectorProfessionalProfileCodes:
                      selectedItems.map((item) => item.code),
                  }))
                }
              />
            </Column>
            <Column lg={5} md={4} sm={4}>
              <MultiSelect
                id="patient-entry-profile"
                titleText={intl.formatMessage({
                  id: "professionalProfile.settings.patientEntry",
                  defaultMessage: "Patient manager profile",
                })}
                labelText={intl.formatMessage({
                  id: "professionalProfile.settings.patientEntry",
                  defaultMessage: "Patient manager profile",
                })}
                items={profiles}
                itemToString={(item) => item?.name || ""}
                selectedItems={getSelectedProfiles(
                  settings.patientEntryProfessionalProfileCodes,
                )}
                selectionFeedback="top-after-reopen"
                onChange={({ selectedItems }) =>
                  setSettings((previousSettings) => ({
                    ...previousSettings,
                    patientEntryProfessionalProfileCodes:
                      selectedItems.map((item) => item.code),
                  }))
                }
              />
            </Column>
            <Column lg={5} md={4} sm={4}>
              <MultiSelect
                id="result-entry-profile"
                titleText={intl.formatMessage({
                  id: "professionalProfile.settings.resultEntry",
                  defaultMessage: "Result entry profile",
                })}
                labelText={intl.formatMessage({
                  id: "professionalProfile.settings.resultEntry",
                  defaultMessage: "Result entry profile",
                })}
                items={profiles}
                itemToString={(item) => item?.name || ""}
                selectedItems={getSelectedProfiles(
                  settings.resultEntryProfessionalProfileCodes,
                )}
                selectionFeedback="top-after-reopen"
                onChange={({ selectedItems }) =>
                  setSettings((previousSettings) => ({
                    ...previousSettings,
                    resultEntryProfessionalProfileCodes:
                      selectedItems.map((item) => item.code),
                  }))
                }
              />
            </Column>
            <Column lg={6} md={8} sm={4}>
              <MultiSelect
                id="validation-interpreter-profile"
                titleText={intl.formatMessage({
                  id: "professionalProfile.settings.validationInterpreter",
                  defaultMessage: "Validation interpreter profile",
                })}
                labelText={intl.formatMessage({
                  id: "professionalProfile.settings.validationInterpreter",
                  defaultMessage: "Validation interpreter profile",
                })}
                items={profiles}
                itemToString={(item) => item?.name || ""}
                selectedItems={getSelectedProfiles(
                  settings.validationInterpreterProfessionalProfileCodes,
                )}
                selectionFeedback="top-after-reopen"
                onChange={({ selectedItems }) =>
                  setSettings((previousSettings) => ({
                    ...previousSettings,
                    validationInterpreterProfessionalProfileCodes:
                      selectedItems.map((item) => item.code),
                  }))
                }
              />
            </Column>
          </Grid>
          <br />
          <Button type="button" onClick={saveSettings}>
            <FormattedMessage id="label.button.save" />
          </Button>
          <br />
          <br />
          <Button
            type="button"
            onClick={() => history.push("/MasterListsPage/profileEdit?code=new")}
          >
            <FormattedMessage
              id="professionalProfile.actions.add"
              defaultMessage="Create professional profile"
            />
          </Button>
          <br />
          <br />
          <DataTable rows={profiles} headers={headers}>
            {({ rows, headers, getHeaderProps, getRowProps }) => (
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      {headers.map((header) => (
                        <TableHeader
                          key={header.key}
                          {...getHeaderProps({ header })}
                        >
                          <FormattedMessage
                            id={header.header}
                            defaultMessage={header.key}
                          />
                        </TableHeader>
                      ))}
                      <TableHeader>
                        <FormattedMessage
                          id="professionalProfile.actions.title"
                          defaultMessage="Actions"
                        />
                      </TableHeader>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rows.map((row) => (
                      <TableRow key={row.id} {...getRowProps({ row })}>
                        {row.cells.map((cell) => (
                          <TableCell key={cell.id}>{cell.value}</TableCell>
                        ))}
                        <TableCell>
                          <Button
                            kind="ghost"
                            size="sm"
                            onClick={() =>
                              history.push(
                                `/MasterListsPage/profileEdit?code=${row.id}`,
                              )
                            }
                          >
                            <FormattedMessage
                              id="professionalProfile.actions.edit"
                              defaultMessage="Edit"
                            />
                          </Button>
                          <Button
                            kind="danger--ghost"
                            size="sm"
                            onClick={() => handleDelete(row.id)}
                          >
                            <FormattedMessage
                              id="professionalProfile.actions.delete"
                              defaultMessage="Delete"
                            />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </DataTable>
        </Column>
      </Grid>
    </div>
  );
}

export default ProfileManagement;
