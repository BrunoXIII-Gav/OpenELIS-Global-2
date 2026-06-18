import React, { useContext, useEffect, useMemo, useState } from "react";
import {
  Grid,
  Column,
  Heading,
  Section,
  Button,
  Tile,
  DataTable,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  TableContainer,
  TextInput,
  TextArea,
  Select,
  SelectItem,
  Checkbox,
} from "@carbon/react";
import { Add, Edit, Save, TrashCan } from "@carbon/icons-react";
import { FormattedMessage, useIntl } from "react-intl";
import PageBreadCrumb from "../../../common/PageBreadCrumb";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../../../utils/Utils";
import { NotificationContext } from "../../../layout/Layout";
import {
  AlertDialog,
  NotificationKinds,
} from "../../../common/CustomNotification";

const emptyContact = () => ({
  id: null,
  personId: "",
  lastName: "",
  firstName: "",
  primaryPhone: "",
  email: "",
});

const emptyForm = () => ({
  id: null,
  active: true,
  name: "",
  description: "",
  programmedConnection: "smtp_server",
  authenticationType: "basic",
  uri: "",
  username: "",
  password: "",
  contacts: [emptyContact()],
});

const ExternalConnectionsConfig = () => {
  const intl = useIntl();
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [connections, setConnections] = useState([]);
  const [options, setOptions] = useState({
    programmedConnections: [],
    authenticationTypes: [],
  });
  const [formData, setFormData] = useState(emptyForm());
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const breadcrumbs = [
    { label: "home.label", link: "/" },
    { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
    {
      label: "sidenav.label.admin.formEntry.externalConnections",
      link: "/MasterListsPage/ExternalConnectionsConfig",
    },
  ];

  const headers = useMemo(
    () => [
      {
        key: "name",
        header: intl.formatMessage({ id: "externalConnections.table.name" }),
      },
      {
        key: "programmedConnectionLabel",
        header: intl.formatMessage({ id: "externalConnections.table.type" }),
      },
      {
        key: "authenticationTypeLabel",
        header: intl.formatMessage({ id: "externalConnections.table.auth" }),
      },
      {
        key: "uri",
        header: intl.formatMessage({ id: "externalConnections.table.endpoint" }),
      },
      {
        key: "lastUpdated",
        header: intl.formatMessage({ id: "externalConnections.table.lastUpdated" }),
      },
      {
        key: "actions",
        header: intl.formatMessage({ id: "externalConnections.table.actions" }),
      },
    ],
    [intl],
  );

  const loadData = () => {
    setLoading(true);
    getFromOpenElisServer("/rest/external-connections/options", (optionsRes) => {
      setOptions(
        optionsRes || { programmedConnections: [], authenticationTypes: [] },
      );
    });
    getFromOpenElisServer("/rest/external-connections", (response) => {
      setConnections(response || []);
      setLoading(false);
    });
  };

  useEffect(() => {
    loadData();
  }, []);

  const loadConnection = (id) => {
    getFromOpenElisServer(`/rest/external-connections/${id}`, (response) => {
      if (!response) {
        return;
      }
      setFormData({
        ...response,
        contacts:
          response.contacts && response.contacts.length > 0
            ? response.contacts
            : [emptyContact()],
      });
    });
  };

  const updateField = (field, value) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  };

  const updateContact = (index, field, value) => {
    setFormData((prev) => {
      const contacts = [...prev.contacts];
      contacts[index] = { ...contacts[index], [field]: value };
      return { ...prev, contacts };
    });
  };

  const addContact = () => {
    setFormData((prev) => ({
      ...prev,
      contacts: [...prev.contacts, emptyContact()],
    }));
  };

  const removeContact = (index) => {
    setFormData((prev) => {
      const contacts = prev.contacts.filter((_, idx) => idx !== index);
      return { ...prev, contacts: contacts.length ? contacts : [emptyContact()] };
    });
  };

  const handleNew = () => {
    setFormData(emptyForm());
  };

  const handleSave = () => {
    setSaving(true);
    postToOpenElisServerJsonResponse(
      "/rest/external-connections",
      JSON.stringify(formData),
      (response) => {
        setSaving(false);
        if (response?.error) {
          setNotificationVisible(true);
          addNotification({
            kind: NotificationKinds.error,
            title: intl.formatMessage({ id: "error.title" }),
            message: response.error,
          });
          return;
        }

        setNotificationVisible(true);
        addNotification({
          kind: NotificationKinds.success,
          title: intl.formatMessage({ id: "externalConnections.save.success" }),
          message: intl.formatMessage({
            id: "externalConnections.save.success.message",
          }),
        });
        setFormData({
          ...response,
          contacts:
            response.contacts && response.contacts.length > 0
              ? response.contacts
              : [emptyContact()],
        });
        loadData();
      },
    );
  };

  const isCertificateMode = formData.authenticationType === "certificate";

  return (
    <div className="adminPageContent">
      {notificationVisible ? <AlertDialog /> : null}
      <PageBreadCrumb breadcrumbs={breadcrumbs} />
      <Grid fullWidth>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading>
              <FormattedMessage id="sidenav.label.admin.formEntry.externalConnections" />
            </Heading>
          </Section>
          <br />

          <Tile style={{ padding: "1rem", marginBottom: "1rem" }}>
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                gap: "1rem",
                flexWrap: "wrap",
              }}
            >
              <Heading style={{ margin: 0 }}>
                <FormattedMessage id="externalConnections.table.title" />
              </Heading>
              <Button renderIcon={Add} onClick={handleNew}>
                <FormattedMessage id="externalConnections.button.new" />
              </Button>
            </div>
            <div style={{ marginTop: "1rem" }}>
              <DataTable rows={connections} headers={headers}>
                {({ rows, headers, getHeaderProps, getTableProps }) => (
                  <TableContainer>
                    <Table {...getTableProps()}>
                      <TableHead>
                        <TableRow>
                          {headers.map((header) => (
                            <TableHeader
                              key={header.key}
                              {...getHeaderProps({ header })}
                            >
                              {header.header}
                            </TableHeader>
                          ))}
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {rows.map((row) => (
                          <TableRow key={row.id}>
                            {row.cells.map((cell) => {
                              if (cell.info.header === "actions") {
                                return (
                                  <TableCell key={cell.id}>
                                    <Button
                                      kind="ghost"
                                      size="sm"
                                      renderIcon={Edit}
                                      iconDescription={intl.formatMessage({
                                        id: "externalConnections.button.edit",
                                      })}
                                      hasIconOnly
                                      onClick={() => loadConnection(row.id)}
                                    />
                                  </TableCell>
                                );
                              }
                              return (
                                <TableCell key={cell.id}>{cell.value}</TableCell>
                              );
                            })}
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              </DataTable>
            </div>
          </Tile>

          <Tile style={{ padding: "1rem" }}>
            <Heading style={{ marginBottom: "1rem" }}>
              {formData.id ? (
                <FormattedMessage id="externalConnections.form.edit" />
              ) : (
                <FormattedMessage id="externalConnections.form.new" />
              )}
            </Heading>

            {isCertificateMode ? (
              <InlineNotification
                lowContrast
                hideCloseButton
                kind="warning"
                title={intl.formatMessage({
                  id: "externalConnections.certificate.noteTitle",
                })}
                subtitle={intl.formatMessage({
                  id: "externalConnections.certificate.noteBody",
                })}
                style={{ marginBottom: "1rem" }}
              />
            ) : null}

            <Grid condensed fullWidth>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="external-connection-name"
                  labelText={intl.formatMessage({
                    id: "externalConnections.field.name",
                  })}
                  value={formData.name || ""}
                  onChange={(e) => updateField("name", e.target.value)}
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <Select
                  id="external-connection-type"
                  labelText={intl.formatMessage({
                    id: "externalConnections.field.programmedConnection",
                  })}
                  value={formData.programmedConnection || ""}
                  onChange={(e) =>
                    updateField("programmedConnection", e.target.value)
                  }
                >
                  {options.programmedConnections.map((option) => (
                    <SelectItem
                      key={option.value}
                      value={option.value}
                      text={option.label}
                    />
                  ))}
                </Select>
              </Column>
              <Column lg={16} md={8} sm={4}>
                <TextArea
                  id="external-connection-description"
                  labelText={intl.formatMessage({
                    id: "externalConnections.field.description",
                  })}
                  rows={4}
                  value={formData.description || ""}
                  onChange={(e) => updateField("description", e.target.value)}
                />
              </Column>
              <Column lg={8} md={4} sm={4}>
                <Select
                  id="external-connection-auth-type"
                  labelText={intl.formatMessage({
                    id: "externalConnections.field.authenticationType",
                  })}
                  value={formData.authenticationType || ""}
                  onChange={(e) =>
                    updateField("authenticationType", e.target.value)
                  }
                >
                  {options.authenticationTypes.map((option) => (
                    <SelectItem
                      key={option.value}
                      value={option.value}
                      text={option.label}
                    />
                  ))}
                </Select>
              </Column>
              <Column lg={8} md={4} sm={4}>
                <TextInput
                  id="external-connection-uri"
                  labelText={intl.formatMessage({
                    id: "externalConnections.field.uri",
                  })}
                  value={formData.uri || ""}
                  onChange={(e) => updateField("uri", e.target.value)}
                />
              </Column>
              {formData.authenticationType === "basic" ? (
                <>
                  <Column lg={8} md={4} sm={4}>
                    <TextInput
                      id="external-connection-username"
                      labelText={intl.formatMessage({
                        id: "externalConnections.field.username",
                      })}
                      value={formData.username || ""}
                      onChange={(e) =>
                        updateField("username", e.target.value)
                      }
                    />
                  </Column>
                  <Column lg={8} md={4} sm={4}>
                    <TextInput
                      type="password"
                      id="external-connection-password"
                      labelText={intl.formatMessage({
                        id: "externalConnections.field.password",
                      })}
                      value={formData.password || ""}
                      onChange={(e) =>
                        updateField("password", e.target.value)
                      }
                    />
                  </Column>
                </>
              ) : null}
              <Column lg={16} md={8} sm={4}>
                <Checkbox
                  id="external-connection-active"
                  labelText={intl.formatMessage({
                    id: "externalConnections.field.active",
                  })}
                  checked={!!formData.active}
                  onChange={(_, { checked }) => updateField("active", checked)}
                />
              </Column>
            </Grid>

            <div style={{ marginTop: "1.5rem", marginBottom: "0.75rem" }}>
              <Heading style={{ fontSize: "1.1rem" }}>
                <FormattedMessage id="externalConnections.contacts.title" />
              </Heading>
            </div>

            {formData.contacts.map((contact, index) => (
              <Tile
                key={`contact-${index}`}
                style={{ marginBottom: "1rem", padding: "1rem" }}
              >
                <Grid condensed fullWidth>
                  <Column lg={4} md={4} sm={4}>
                    <TextInput
                      id={`external-contact-last-${index}`}
                      labelText={intl.formatMessage({
                        id: "externalConnections.contacts.lastName",
                      })}
                      value={contact.lastName || ""}
                      onChange={(e) =>
                        updateContact(index, "lastName", e.target.value)
                      }
                    />
                  </Column>
                  <Column lg={4} md={4} sm={4}>
                    <TextInput
                      id={`external-contact-first-${index}`}
                      labelText={intl.formatMessage({
                        id: "externalConnections.contacts.firstName",
                      })}
                      value={contact.firstName || ""}
                      onChange={(e) =>
                        updateContact(index, "firstName", e.target.value)
                      }
                    />
                  </Column>
                  <Column lg={4} md={4} sm={4}>
                    <TextInput
                      id={`external-contact-phone-${index}`}
                      labelText={intl.formatMessage({
                        id: "externalConnections.contacts.phone",
                      })}
                      value={contact.primaryPhone || ""}
                      onChange={(e) =>
                        updateContact(index, "primaryPhone", e.target.value)
                      }
                    />
                  </Column>
                  <Column lg={4} md={4} sm={4}>
                    <TextInput
                      id={`external-contact-email-${index}`}
                      labelText={intl.formatMessage({
                        id: "externalConnections.contacts.email",
                      })}
                      value={contact.email || ""}
                      onChange={(e) =>
                        updateContact(index, "email", e.target.value)
                      }
                    />
                  </Column>
                </Grid>
                <div style={{ marginTop: "0.75rem" }}>
                  <Button
                    kind="danger--ghost"
                    size="sm"
                    renderIcon={TrashCan}
                    onClick={() => removeContact(index)}
                  >
                    <FormattedMessage id="externalConnections.contacts.remove" />
                  </Button>
                </div>
              </Tile>
            ))}

            <div
              style={{
                display: "flex",
                gap: "0.75rem",
                flexWrap: "wrap",
                marginTop: "1rem",
              }}
            >
              <Button kind="secondary" renderIcon={Add} onClick={addContact}>
                <FormattedMessage id="externalConnections.contacts.add" />
              </Button>
              <Button
                renderIcon={Save}
                onClick={handleSave}
                disabled={saving || isCertificateMode}
              >
                <FormattedMessage id="externalConnections.button.save" />
              </Button>
            </div>
          </Tile>
        </Column>
      </Grid>
    </div>
  );
};

export default ExternalConnectionsConfig;
